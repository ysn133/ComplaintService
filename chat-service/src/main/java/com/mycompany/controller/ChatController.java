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
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    // Maps for tracking UIDs
    private final Map<Long, String> clientUidMap = new ConcurrentHashMap<>();
    private final Map<Long, String> supportUidMap = new ConcurrentHashMap<>();

    // Mock storage for ticket assignments (ticketId -> supportId)
    private final Map<Long, Long> ticketAssignments = new ConcurrentHashMap<>();

    // Mock storage for active calls (callId -> ticketId)
    private final Map<String, Long> activeCalls = new HashMap<>();

    // Storage for call JWT tokens (callId -> jwtToken)
    private final Map<String, String> callJwtTokens = new HashMap<>();

    // Executor for delayed tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Generate unique UID
    private String generateUniqueUid() {
        String uid;
        int attempts = 0;
        do {
            uid = UUID.randomUUID().toString();
            attempts++;
            if (attempts > 10) {
                logger.severe("Failed to generate unique UID after " + attempts + " attempts");
                throw new IllegalStateException("Unable to generate unique UID");
            }
        } while (clientUidMap.containsValue(uid) || supportUidMap.containsValue(uid));
        logger.info("Generated unique UID: " + uid + " after " + attempts + " attempts");
        return uid;
    }

    // Handle WebSocket connection to assign and send UID
    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        logger.info("WebSocket connection established: " + event);

        // Extract headers from the connection event
        Map<String, List<String>> headers = event.getMessage().getHeaders().get("nativeHeaders", Map.class);
        if (headers == null) {
            logger.severe("No nativeHeaders found in WebSocket connection event");
            return;
        }
        logger.info("Native headers: " + headers);

        if (!headers.containsKey("Authorization")) {
            logger.severe("No Authorization header found in WebSocket connection");
            return;
        }

        List<String> authHeaders = headers.get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            logger.severe("Authorization header is empty");
            return;
        }

        String authHeader = authHeaders.get(0);
        logger.info("Authorization header: " + authHeader);
        if (!authHeader.startsWith("Bearer ")) {
            logger.severe("Invalid Authorization header: " + authHeader);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Long userId = JwtUtil.getUserIdFromToken(token);
            String role = JwtUtil.getRoleFromToken(token);
            logger.info("WebSocket connected for userId=" + userId + ", role=" + role);

            // Generate and store unique UID
            String uid = generateUniqueUid();
            if (role.equalsIgnoreCase("CLIENT")) {
                clientUidMap.put(userId, uid);
                supportUidMap.remove(userId); // Ensure userId is not in support map
                logger.info("Assigned UID=" + uid + " to client userId=" + userId + ", clientUidMap size=" + clientUidMap.size());
            } else if (role.equalsIgnoreCase("SUPPORT")) {
                supportUidMap.put(userId, uid);
                clientUidMap.remove(userId); // Ensure userId is not in client map
                logger.info("Assigned UID=" + uid + " to support userId=" + userId + ", supportUidMap size=" + supportUidMap.size());
            } else {
                logger.severe("Unknown role: " + role);
                return;
            }

            // Delay sending UID to ensure client subscription is active
            scheduler.schedule(() -> {
                Map<String, String> uidMessage = new HashMap<>();
                uidMessage.put("uid", uid);
                messagingTemplate.convertAndSend("/user/" + userId + "/uid", uidMessage);
                logger.info("Sent UID=" + uid + " to /user/" + userId + "/uid");
            }, 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.severe("Error processing WebSocket connection: " + e.getMessage());
        }
    }

    // Handle WebSocket disconnection to clean up UIDs
    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        logger.info("WebSocket disconnected for session: " + sessionId);

        // Remove userId from maps if sessionId matches UID
        clientUidMap.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(sessionId)) {
                logger.info("Removed client userId=" + entry.getKey() + " from clientUidMap");
                return true;
            }
            return false;
        });
        supportUidMap.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(sessionId)) {
                logger.info("Removed support userId=" + entry.getKey() + " from supportUidMap");
                return true;
            }
            return false;
        });
    }

    @PostMapping("/tickets/assign")
    public void assignTicket(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("supportId") Long supportId,
            HttpServletRequest request) {
        logger.info("Assigning ticket " + ticketId + " to supportId " + supportId);
        ticketAssignments.put(ticketId, supportId);
        logger.info("Updated ticketAssignments: " + ticketAssignments);
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

        ReceiverInfo receiverInfo = getReceiverInfo(ticketId, type);
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

    @MessageMapping("/messages/{uid}")
    public void sendWebSocketMessage(@DestinationVariable String uid, ChatMessageDTO messageDTO) {
        logger.info("Received WebSocket message for uid " + uid + ": " + messageDTO.getMessage());
        
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
            
            // Verify UID matches user and role
            String expectedUid = role.equals("CLIENT") ? clientUidMap.get(userId) : supportUidMap.get(userId);
            if (expectedUid == null) {
                logger.warning("No UID found for userId=" + userId + ", role=" + role + ". Attempting to reassign UID.");
                expectedUid = generateUniqueUid();
                if (role.equals("CLIENT")) {
                    clientUidMap.put(userId, expectedUid);
                    supportUidMap.remove(userId);
                    logger.info("Reassigned UID=" + expectedUid + " to client userId=" + userId);
                } else {
                    supportUidMap.put(userId, expectedUid);
                    clientUidMap.remove(userId);
                    logger.info("Reassigned UID=" + expectedUid + " to support userId=" + userId);
                }
                // Send new UID to user
                Map<String, String> uidMessage = new HashMap<>();
                uidMessage.put("uid", expectedUid);
                messagingTemplate.convertAndSend("/user/" + userId + "/uid", uidMessage);
                logger.info("Sent reassigned UID=" + expectedUid + " to /user/" + userId + "/uid");
            }
            if (!uid.equals(expectedUid)) {
                logger.severe("UID mismatch: received=" + uid + ", expected=" + expectedUid);
                return;
            }
            // Ensure userId is not in the other role's map
            if (role.equals("CLIENT") && supportUidMap.containsKey(userId)) {
                logger.severe("UserId=" + userId + " found in supportUidMap but role is CLIENT");
                return;
            } else if (role.equals("SUPPORT") && clientUidMap.containsKey(userId)) {
                logger.severe("UserId=" + userId + " found in clientUidMap but role is SUPPORT");
                return;
            }

            Long ticketId = messageDTO.getTicketId();
            if (ticketId == null) {
                logger.severe("Missing ticketId in message");
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
            Long supportTeamId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            logger.info("Ticket service response: ticketId=" + ticketId + ", supportTeamId=" + supportTeamId + ", clientId=" + clientId);
            
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
            
            // Send to sender
            String senderUid = role.equals("CLIENT") ? clientUidMap.get(userId) : supportUidMap.get(userId);
            if (senderUid != null) {
                messagingTemplate.convertAndSend("/user/" + senderUid + "/messages", message);
                logger.info("Sent message to sender: userId=" + userId + ", uid=" + senderUid);
            } else {
                logger.warning("Sender UID not found for userId=" + userId);
            }

            // Send to receiver
            String receiverUid = null;
            Long receiverId = null;
            if (role.equals("CLIENT")) {
                receiverId = supportTeamId;
                Long assignedSupportId = ticketAssignments.get(ticketId);
                logger.info("Checking support receiver: ticketId=" + ticketId + ", supportTeamId=" + supportTeamId + ", assignedSupportId=" + assignedSupportId + ", ticketAssignments=" + ticketAssignments);
                if (assignedSupportId == null) {
                    logger.warning("No assigned support for ticketId=" + ticketId + ". Using supportTeamId=" + supportTeamId + " from ticket service.");
                    assignedSupportId = supportTeamId;
                    ticketAssignments.put(ticketId, assignedSupportId); // Cache for future messages
                    logger.info("Updated ticketAssignments: " + ticketAssignments);
                }
                if (assignedSupportId.equals(supportTeamId)) {
                    receiverUid = supportUidMap.get(supportTeamId);
                }
            } else {
                receiverId = clientId;
                if (mockCheckTicketOwnership(clientId, ticketId)) {
                    receiverUid = clientUidMap.get(clientId);
                }
            }

            if (receiverUid != null) {
                messagingTemplate.convertAndSend("/user/" + receiverUid + "/messages", message);
                messagingTemplate.convertAndSendToUser(
                    receiverId.toString(),
                    "/queue/notifications",
                    Map.of(
                        "type", "NEW_MESSAGE",
                        "ticketId", ticketId,
                        "message", messageDTO.getMessage(),
                        "senderId", userId,
                        "senderType", role
                    )
                );
                logger.info("Sent message to receiver: receiverId=" + receiverId + ", uid=" + receiverUid);
            } else {
                logger.warning("No valid receiver UID found for receiverId=" + receiverId + " for ticketId=" + ticketId + ", supportUidMap=" + supportUidMap + ", ticketAssignments=" + ticketAssignments);
            }
        } catch (Exception e) {
            logger.severe("Error processing message: " + e.getMessage());
        }
    }

    private void notifySupportTicketAssigned(Long ticketId, Long supportId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("ticketId", ticketId);
        notification.put("message", "You have been assigned a new ticket: " + ticketId);
        String supportUid = supportUidMap.get(supportId);
        if (supportUid != null) {
            messagingTemplate.convertAndSend("/user/" + supportUid + "/tickets", notification);
            logger.info("Notified supportId " + supportId + " of new ticket assignment: ticketId=" + ticketId);
        } else {
            logger.warning("No UID found for supportId=" + supportId);
        }
    }

    private void notifySupportNewMessage(Long ticketId, TicketMessage message) {
        Long supportId = ticketAssignments.get(ticketId);
        if (supportId == null) {
            logger.warning("No support assigned to ticketId=" + ticketId + " in ticketAssignments");
            return;
        }
        String supportUid = supportUidMap.get(supportId);
        if (supportUid != null) {
            messagingTemplate.convertAndSend("/user/" + supportUid + "/messages", message);
            logger.info("Notified supportId " + supportId + " of new message in ticketId=" + ticketId);
        } else {
            logger.warning("No UID found for supportId=" + supportId);
        }
    }

    private boolean mockCheckTicketOwnership(Long userId, Long ticketId) {
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdAndUserId(ticketId, userId);
        return !messages.isEmpty();
    }

    private ReceiverInfo getReceiverInfo(Long ticketId, SenderType senderType) {
        Long assignedSupportId = ticketAssignments.get(ticketId);
        if (assignedSupportId == null) {
            logger.warning("No support assigned to ticketId=" + ticketId + " in ticketAssignments");
            return null;
        }
        if (senderType == SenderType.CLIENT) {
            return new ReceiverInfo(assignedSupportId, SenderType.SUPPORT);
        } else if (senderType == SenderType.SUPPORT) {
            // Assume clientId=1 for mock purposes; ideally fetch from ticket service
            return new ReceiverInfo(1L, SenderType.CLIENT);
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

    // Call logic remains unchanged
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

            String supportUid = supportUidMap.get(supportId);
            if (supportUid != null) {
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/ticket/" + ticketId + "/call/incoming",
                        callNotification
                );
                logger.info("Notified supportId " + supportId + " of incoming call: callId=" + callId);
            } else {
                logger.warning("No UID found for supportId=" + supportId);
            }
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
                String clientUid = clientUidMap.get(clientId);
                String supportUid = supportUidMap.get(supportId);
                if (clientUid != null) {
                    messagingTemplate.convertAndSendToUser(
                            clientId.toString(),
                            "/ticket/" + ticketId + "/call/response",
                            callResponse
                    );
                }
                if (supportUid != null) {
                    messagingTemplate.convertAndSendToUser(
                            supportId.toString(),
                            "/ticket/" + ticketId + "/call/response",
                            callResponse
                    );
                }
                logger.info("Sent call acceptance to clientId " + clientId + " and supportId " + supportId);
            } else {
                // Notify both client and support of rejection
                CallNotificationDTO notification = new CallNotificationDTO();
                notification.setCallId(callId);
                notification.setJwtToken(null); // Remove JWT token
                String clientUid = clientUidMap.get(clientId);
                String supportUid = supportUidMap.get(supportId);
                if (clientUid != null) {
                    messagingTemplate.convertAndSendToUser(
                            clientId.toString(),
                            "/ticket/" + ticketId + "/call/end",
                            notification
                    );
                }
                if (supportUid != null) {
                    messagingTemplate.convertAndSendToUser(
                            supportId.toString(),
                            "/ticket/" + ticketId + "/call/end",
                            notification
                    );
                }
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
        String toUserUid = clientUidMap.get(toUserId) != null ? clientUidMap.get(toUserId) : supportUidMap.get(toUserId);
        if (toUserUid != null) {
            messagingTemplate.convertAndSendToUser(
                    toUserId.toString(),
                    "/ticket/" + ticketId + "/call/signal",
                    signal
            );
            logger.info("Sent WebRTC signal to userId " + toUserId);
        } else {
            logger.warning("No UID found for toUserId=" + toUserId);
        }
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
            String supportUid = supportUidMap.get(supportId);
            String clientUid = clientUidMap.get(clientId);
            if (supportUid != null) {
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/ticket/" + ticketId + "/call/end",
                        notification
                );
            }
            if (clientUid != null) {
                messagingTemplate.convertAndSendToUser(
                        clientId.toString(),
                        "/ticket/" + ticketId + "/call/end",
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