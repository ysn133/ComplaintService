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

    @PostMapping("/tickets/assign")
    public void assignTicket(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("supportId") Long supportId,
            HttpServletRequest request) {
        // In production, this would be handled by ticket-service
        // For now, mock the assignment by storing in a Map
        logger.info("Assigning ticket " + ticketId + " to supportId " + supportId);
        ticketAssignments.put(ticketId, supportId);

        // Notify the assigned support member via WebSocket
        notifySupportTicketAssigned(ticketId, supportId);
    }

    @PostMapping("/chat/message")
    public TicketMessage sendMessage(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("senderId") Long senderId,
            @RequestParam("senderType") String senderType,
            @RequestParam("message") String message,
            HttpServletRequest request) {
        // Get userId from JWT (set by JwtFilter)
        Long userIdFromToken = (Long) request.getAttribute("userId");
        String role = (String) request.getAttribute("role");

        // Validate: senderId must match the userId from JWT
        if (!userIdFromToken.equals(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only send messages as yourself");
        }

        // Validate senderType matches role from JWT
        SenderType type;
        try {
            type = SenderType.valueOf(senderType.toUpperCase());
            if (!role.equalsIgnoreCase(senderType)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sender type does not match your role");
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid senderType. Must be 'CLIENT' or 'SUPPORT'.");
        }

        // Check if the user is a participant in the ticket
        boolean userOwnsTicket = mockCheckTicketOwnership(userIdFromToken, ticketId);
        if (!userOwnsTicket) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this ticket");
        }

        // Determine the receiver based on the ticket
        ReceiverInfo receiverInfo = mockGetReceiverInfo(ticketId, type);
        if (receiverInfo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not determine receiver for this ticket");
        }

        logger.info("Received POST request: ticketId=" + ticketId + ", senderId=" + senderId + ", senderType=" + senderType + ", message=" + message);
        TicketMessage savedMessage = chatService.saveMessage(ticketId, senderId, type, receiverInfo.getReceiverId(), receiverInfo.getReceiverType(), message);

        // Notify the assigned support member of the new message
        notifySupportNewMessage(ticketId, savedMessage);

        return savedMessage;
    }

    @GetMapping("/chat/messages/{ticketId}")
    public List<TicketMessage> getMessages(
            @PathVariable("ticketId") Long ticketId,
            HttpServletRequest request) {
        // Get userId from JWT
        Long userIdFromToken = (Long) request.getAttribute("userId");

        // Check if the user is a participant in the ticket
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
        
        // Extract JWT token and validate
        String token = messageDTO.getJwtToken();
        if (token == null || !token.startsWith("Bearer ")) {
            logger.severe("Missing or invalid JWT token in message");
            return;
        }
        token = token.substring(7); // Remove "Bearer " prefix
        
        try {
            // Extract user info from JWT
            Long userId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);
            logger.info("Extracted userId=" + userId + ", role=" + role + " from JWT");
            
            // Get ticket info from ticket service
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
            
            // Create message entity
            TicketMessage message = new TicketMessage();
            message.setTicketId(ticketId);
            message.setSenderId(userId);
            message.setSenderType(SenderType.valueOf(role));
            
            // Set receiver based on sender type
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
            
            // Save message to database
            chatService.saveMessage(message);
            
            // Send message to ticket topic
            messagingTemplate.convertAndSend("/topic/ticket/" + ticketId, message);
            
            // Send notification to the receiver
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

    // Notify the assigned support member when a ticket is assigned
    private void notifySupportTicketAssigned(Long ticketId, Long supportId) {
        // Create a simple notification payload (in production, this could be a Ticket DTO)
        Map<String, Object> notification = new HashMap<>();
        notification.put("ticketId", ticketId);
        notification.put("message", "You have been assigned a new ticket: " + ticketId);

        // Send the notification to the specific support user
        messagingTemplate.convertAndSendToUser(
                supportId.toString(),
                "/tickets",
                notification
        );
        logger.info("Notified supportId " + supportId + " of new ticket assignment: ticketId=" + ticketId);
    }

    // Notify the assigned support member when a new message is added to the ticket
    private void notifySupportNewMessage(Long ticketId, TicketMessage message) {
        Long supportId = ticketAssignments.get(ticketId);
        if (supportId != null) {
            // Send the new message to the specific support user
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

    // Mock method to simulate ticket ownership check
    private boolean mockCheckTicketOwnership(Long userId, Long ticketId) {
        // Check if the user is either the sender or receiver of any message in the ticket
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdAndUserId(ticketId, userId);
        return !messages.isEmpty();
    }

    // Mock method to determine the receiver based on the ticket and sender
    private ReceiverInfo mockGetReceiverInfo(Long ticketId, SenderType senderType) {
        // In production, call ticket-service API to get the ticket's participants
        // For now, assume ticketId=1 has client userId=1 and support userId=2
        if (ticketId.equals(1L)) {
            if (senderType == SenderType.CLIENT) {
                return new ReceiverInfo(2L, SenderType.SUPPORT); // Client sends to Support
            } else if (senderType == SenderType.SUPPORT) {
                return new ReceiverInfo(1L, SenderType.CLIENT); // Support sends to Client
            }
        }
        return null;
    }

    // Helper class to hold receiver info
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
    
    
    // call logic : 






    // Mock storage for active calls (callId -> ticketId)
    private final Map<String, Long> activeCalls = new HashMap<>();


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
            // Extract JWT token and validate
            String token = callNotification.getJwtToken();
            if (token == null || !token.startsWith("Bearer ")) {
                logger.severe("Missing or invalid JWT token in call notification");
                return;
            }
            token = token.substring(7); // Remove "Bearer " prefix

            // Get ticket info from ticket service
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
            
            if (supportId == null || supportId == 0) {
                logger.severe("No support agent assigned to ticket " + ticketId);
                return;
            }

            String callId = UUID.randomUUID().toString();
            callNotification.setCallId(callId);
            activeCalls.put(callId, ticketId);

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

    Long clientId = mockGetClientIdForTicket(ticketId);
    if (clientId == null) {
        logger.severe("No client found for ticket " + ticketId);
        return;
    }

    // Ensure callId is set in the response
    callResponse.setCallId(callId);

    // Add timestamp when call is accepted
    if (callResponse.isAccepted()) {
        callResponse.setTimestamp(LocalDateTime.now().toString());
    }

    // Send response to client
    messagingTemplate.convertAndSendToUser(
            clientId.toString(), // Should be "1" for client
            "/call/response",
            callResponse
    );
    logger.info("Sent call response to clientId " + clientId + ": accepted=" + callResponse.isAccepted());

    if (!callResponse.isAccepted()) {
        activeCalls.remove(callId);
        logger.info("Call " + callId + " rejected and removed");
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

        Long supportId = 2L; // Hardcoded for consistency
        Long clientId = mockGetClientIdForTicket(ticketId);
        if (supportId != null) {
            messagingTemplate.convertAndSendToUser(
                    supportId.toString(),
                    "/call/end",
                    new CallNotificationDTO() {{ setCallId(callId); }}
            );
        }
        if (clientId != null) {
            messagingTemplate.convertAndSendToUser(
                    clientId.toString(),
                    "/call/end",
                    new CallNotificationDTO() {{ setCallId(callId); }}
            );
        }

        activeCalls.remove(callId);
        logger.info("Call " + callId + " ended and removed");
    }



    private Long mockGetClientIdForTicket(Long ticketId) {
        if (ticketId.equals(1L)) {
            return 1L;
        }
        return null;
    }
    

}