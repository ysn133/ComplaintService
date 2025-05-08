package com.mycompany.controller;

import com.mycompany.dto.CallNotificationDTO;
import com.mycompany.dto.CallResponseDTO;
import com.mycompany.dto.ChatMessageDTO;
import com.mycompany.dto.WebRTCSignalDTO;
import com.mycompany.model.TicketMessage;
import com.mycompany.model.TicketMessage.SenderType;
import com.mycompany.repository.TicketMessageRepository;
import com.mycompany.service.ChatService;
import com.mycompany.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "https://192.168.0.102:3000", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}, allowedHeaders = "*")
public class ChatController {

    private static final Logger logger = Logger.getLogger(ChatController.class.getName());

    @Autowired
    private ChatService chatService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock storage for ticket assignments (ticketId -> supportId)
    private final Map<Long, Long> ticketAssignments = new HashMap<>();

    // Mock storage for active calls (callId -> ticketId)
    private final Map<String, Long> activeCalls = new HashMap<>();

    // Storage for call JWT tokens (callId -> jwtToken)
    private final Map<String, String> callJwtTokens = new HashMap<>();

    @PostMapping("/tickets/assign")
    public void assignTicket(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("supportId") Long supportId,
            HttpServletRequest request) {
        logger.info("Assigning ticket " + ticketId + " to supportId " + supportId);
        ticketAssignments.put(ticketId, supportId);
        notifySupportTicketAssigned(ticketId, supportId);
    }

    @PostMapping("/chat/message")
    public TicketMessage sendMessage(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("senderId") Long senderId,
            @RequestParam("senderType") String senderType,
            @RequestParam("message") String message,
            HttpServletRequest request) {
        Long userIdFromToken = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");

        if (!userIdFromToken.equals(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only send messages as yourself");
        }

        SenderType type;
        try {
            type = SenderType.valueOf(senderType.toUpperCase());
            if (!role.equalsIgnoreCase(senderType)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sender type does not match your role");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid senderType. Must be 'CLIENT' or 'SUPPORT'.");
        }

        boolean userOwnsTicket = mockCheckTicketOwnership(userIdFromToken, ticketId);
        if (!userOwnsTicket) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this ticket");
        }

        ReceiverInfo receiverInfo = mockGetReceiverInfo(ticketId, type);
        if (receiverInfo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine receiver for this ticket");
        }

        logger.info("Received POST request: ticketId=" + ticketId + ", senderId=" + senderId + ", senderType=" + senderType + ", message=" + message);
        TicketMessage savedMessage = chatService.saveMessage(ticketId, senderId, type, receiverInfo.getReceiverId(), receiverInfo.getReceiverType(), message);
        notifySupportNewMessage(ticketId, savedMessage);
        return savedMessage;
    }

    @GetMapping("/chat/messages/{ticketId}")
    public List<TicketMessage> getMessages(
            @PathVariable("ticketId") Long ticketId,
            HttpServletRequest request) {
        Long userIdFromToken = (Long) request.getAttribute("userId");

        boolean userOwnsTicket = mockCheckTicketOwnership(userIdFromToken, ticketId);
        if (!userOwnsTicket) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this ticket");
        }

        logger.info("Received GET request: ticketId=" + ticketId);
        return chatService.getMessagesByTicketId(ticketId, userIdFromToken);
    }

    @MessageMapping("/ticket/{ticketId}/sendMessage")
    public void sendWebSocketMessage(@DestinationVariable Long ticketId, ChatMessageDTO messageDTO) {
        logger.info("Received WebSocket message for ticket " + ticketId + ": " + messageDTO.getMessage());
        
        String token = messageDTO.getJwtToken();
        if (token == null || !token.startsWith("Bearer ")) {
            logger.severe("Missing or invalid JWT token in message");
            return;
        }
        token = token.substring(7);
        
        try {
            Long userId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);
            logger.info("Extracted userId=" + userId + ", role=" + role + " from JWT");
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            logger.info("Making request to ticket service with headers: " + headers);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://192.168.0.102:8093/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: " + ticketResponse);
            
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportTeamId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            
            TicketMessage message = new TicketMessage();
            message.setTicketId(ticketId);
            message.setSenderId(userId);
            message.setSenderType(SenderType.valueOf(role));
            
            if (role.equals("CLIENT")) {
                message.setReceiverId(supportTeamId);
                message.setReceiverType(SenderType.SUPPORT);
            } else {
                message.setReceiverId(clientId);
                message.setReceiverType(SenderType.CLIENT);
            }
            
            message.setMessage(messageDTO.getMessage());
            message.setCreatedAt(LocalDateTime.now());
            message.setIsRead(false);
            
            chatService.saveMessage(message);
            
            messagingTemplate.convertAndSend("/topic/ticket/" + ticketId, message);
            
            messagingTemplate.convertAndSendToUser(
                message.getReceiverId().toString(),
                "/queue/notifications",
                Map.of(
                    "type", "NEW_MESSAGE",
                    "ticketId", ticketId,
                    "message", messageDTO.getMessage(),
                    "senderId", userId,
                    "senderType", role
                )
            );
        } catch (Exception e) {
            logger.severe("Error processing message: " + e.getMessage());
        }
    }

    private void notifySupportTicketAssigned(Long ticketId, Long supportId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("ticketId", ticketId);
        notification.put("message", "You have been assigned a new ticket: " + ticketId);
        messagingTemplate.convertAndSendToUser(
                supportId.toString(),
                "/tickets",
                notification
        );
        logger.info("Notified supportId " + supportId + " of new ticket assignment: ticketId=" + ticketId);
    }

    private void notifySupportNewMessage(Long ticketId, TicketMessage message) {
        Long supportId = ticketAssignments.get(ticketId);
        if (supportId != null) {
            messagingTemplate.convertAndSendToUser(
                    supportId.toString(),
                    "/tickets/updates",
                    message
            );
            logger.info("Notified supportId " + supportId + " of new message in ticketId=" + ticketId);
        } else {
            logger.warning("No support member assigned to ticketId=" + ticketId);
        }
    }

    private boolean mockCheckTicketOwnership(Long userId, Long ticketId) {
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdAndUserId(ticketId, userId);
        return !messages.isEmpty();
    }

    private ReceiverInfo mockGetReceiverInfo(Long ticketId, SenderType senderType) {
        if (ticketId.equals(1L)) {
            if (senderType == SenderType.CLIENT) {
                return new ReceiverInfo(2L, SenderType.SUPPORT);
            } else if (senderType == SenderType.SUPPORT) {
                return new ReceiverInfo(1L, SenderType.CLIENT);
            }
        }
        return null;
    }

    private static class ReceiverInfo {
        private final Long receiverId;
        private final SenderType receiverType;

        public ReceiverInfo(Long receiverId, SenderType receiverType) {
            this.receiverId = receiverId;
            this.receiverType = receiverType;
        }

        public Long getReceiverId() {
            return receiverId;
        }

        public SenderType getReceiverType() {
            return receiverType;
        }
    }

    @MessageMapping("/ticket/{ticketId}/initiateCall")
    public void initiateCall(
            @DestinationVariable Long ticketId,
            CallNotificationDTO callNotification) {
        logger.info("Initiating call for ticket " + ticketId + " from callerId " + callNotification.getCallerId());

        if (!callNotification.getCallerType().equalsIgnoreCase("CLIENT")) {
            logger.severe("Only clients can initiate calls");
            return;
        }

        try {
            String token = callNotification.getJwtToken();
            if (token == null || !token.startsWith("Bearer ")) {
                logger.severe("Missing or invalid JWT token in call notification");
                return;
            }
            token = token.substring(7);

            Long callerId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);
            logger.info("JWT extracted: callerId=" + callerId + ", role=" + role + 
                       ", notification: callerId=" + callNotification.getCallerId() + 
                       ", callerType=" + callNotification.getCallerType());

            if (!callerId.equals(callNotification.getCallerId()) || !role.equalsIgnoreCase(callNotification.getCallerType())) {
                logger.severe("JWT user info does not match call notification: " +
                             "JWT callerId=" + callerId + ", notification callerId=" + callNotification.getCallerId() +
                             ", JWT role=" + role + ", notification callerType=" + callNotification.getCallerType());
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            logger.info("Making request to ticket service with headers: " + headers);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://192.168.0.102:8093/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: " + ticketResponse);
            
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            
            if (supportId == null || supportId == 0) {
                logger.severe("No support agent assigned to ticket " + ticketId);
                return;
            }

            if (!clientId.equals(callerId)) {
                logger.severe("Caller is not the client associated with ticket " + ticketId);
                return;
            }

            String callId = UUID.randomUUID().toString();
            callNotification.setCallId(callId);
            callNotification.setJwtToken(null); // Remove JWT token before sending to client
            activeCalls.put(callId, ticketId);
            callJwtTokens.put(callId, "Bearer " + token); // Store JWT token

            messagingTemplate.convertAndSendToUser(
                    supportId.toString(),
                    "/call/incoming",
                    callNotification
            );
            logger.info("Notified supportId " + supportId + " of incoming call: callId=" + callId);
        } catch (Exception e) {
            logger.severe("Error processing call initiation: " + e.getMessage());
        }
    }

    @MessageMapping("/call/{callId}/respond")
    public void respondToCall(
            @DestinationVariable String callId,
            CallResponseDTO callResponse) {
        logger.info("Received call response for callId " + callId + ": accepted=" + callResponse.isAccepted());

        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.severe("No active call found for callId " + callId);
            return;
        }

        try {
            String jwtToken = callResponse.getJwtToken() != null ? callResponse.getJwtToken() : callJwtTokens.get(callId);
            if (jwtToken == null) {
                logger.severe("No JWT token available for callId " + callId);
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", jwtToken);
            logger.info("Making request to ticket service with headers: " + headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://192.168.0.102:8093/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );

            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: " + ticketResponse);
            
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long clientId = root.path("ticket").path("clientId").asLong();
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            
            if (clientId == null || clientId == 0) {
                logger.severe("No client found for ticket " + ticketId);
                return;
            }

            callResponse.setCallId(callId);
            callResponse.setJwtToken(null); // Remove JWT token before sending to client

            if (callResponse.isAccepted()) {
                callResponse.setTimestamp(LocalDateTime.now().toString());
                // Notify both client and support of acceptance
                messagingTemplate.convertAndSendToUser(
                        clientId.toString(),
                        "/call/response",
                        callResponse
                );
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/call/response",
                        callResponse
                );
                logger.info("Sent call acceptance to clientId " + clientId + " and supportId " + supportId);
            } else {
                // Notify both client and support of rejection
                CallNotificationDTO notification = new CallNotificationDTO();
                notification.setCallId(callId);
                notification.setJwtToken(null); // Remove JWT token
                messagingTemplate.convertAndSendToUser(
                        clientId.toString(),
                        "/call/end",
                        notification
                );
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/call/end",
                        notification
                );
                activeCalls.remove(callId);
                callJwtTokens.remove(callId);
                logger.info("Call " + callId + " rejected and removed");
            }
        } catch (Exception e) {
            logger.severe("Error processing call response: " + e.getMessage());
        }
    }

    @MessageMapping("/call/{callId}/signal")
    public void handleWebRTCSignal(
            @DestinationVariable String callId,
            WebRTCSignalDTO signal) {
        logger.info("Received WebRTC signal for callId " + callId + ": type=" + signal.getType());

        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.severe("No active call found for callId " + callId);
            return;
        }

        Long toUserId = signal.getToUserId();
        messagingTemplate.convertAndSendToUser(
                toUserId.toString(),
                "/call/signal",
                signal
        );
        logger.info("Sent WebRTC signal to userId " + toUserId);
    }

    @MessageMapping("/call/{callId}/end")
    public void endCall(
            @DestinationVariable String callId) {
        logger.info("Ending call for callId " + callId);

        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.severe("No active call found for callId " + callId);
            return;
        }

        try {
            String jwtToken = callJwtTokens.get(callId);
            if (jwtToken == null) {
                logger.severe("No JWT token available for callId " + callId);
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", jwtToken);
            logger.info("Making request to ticket service with headers: " + headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://192.168.0.102:8093/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );

            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: " + ticketResponse);
            
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();

            CallNotificationDTO notification = new CallNotificationDTO();
            notification.setCallId(callId);
            notification.setJwtToken(null); // Remove JWT token
            if (supportId != null && supportId != 0) {
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/call/end",
                        notification
                );
            }
            if (clientId != null && clientId != 0) {
                messagingTemplate.convertAndSendToUser(
                        clientId.toString(),
                        "/call/end",
                        notification
                );
            }

            activeCalls.remove(callId);
            callJwtTokens.remove(callId);
            logger.info("Call " + callId + " ended and removed");
        } catch (Exception e) {
            logger.severe("Error processing call end: " + e.getMessage());
        }
    }
}