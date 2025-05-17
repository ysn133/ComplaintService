package com.mycompany.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.dto.CallNotificationDTO;
import com.mycompany.dto.CallResponseDTO;
import com.mycompany.dto.ChatMessageDTO;
import com.mycompany.dto.WebRTCSignalDTO;
import com.mycompany.entity.TicketDTO;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "https://app.prjsdr.xyz", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}, allowedHeaders = "*")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

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

    @Autowired
    private JwtUtil jwtUtil;

    private final Map<Long, String> clientUidMap = new ConcurrentHashMap<>();
    private final Map<Long, String> supportUidMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> ticketAssignments = new ConcurrentHashMap<>();
    private final Map<String, Long> activeCalls = new HashMap<>();
    private final Map<String, String> callJwtTokens = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        logger.info("ChatController loaded - Version 2025-05-14-1720");
    }

    private String generateUniqueUid() {
        String uid;
        int attempts = 0;
        do {
            uid = UUID.randomUUID().toString();
            attempts++;
            if (attempts > 10) {
                logger.error("Failed to generate unique UID after {} attempts", attempts);
                throw new IllegalStateException("Unable to generate unique UID");
            }
        } while (clientUidMap.containsValue(uid) || supportUidMap.containsValue(uid));
        logger.info("Generated unique UID: {} after {} attempts", uid, attempts);
        return uid;
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        logger.info("WebSocket connection established: {}", event);
        Map<String, List<String>> headers = event.getMessage().getHeaders().get("nativeHeaders", Map.class);
        if (headers == null) {
            logger.error("No nativeHeaders found in WebSocket connection event");
            return;
        }
        logger.info("Native headers: {}", headers);
        if (!headers.containsKey("Authorization")) {
            logger.error("No Authorization header found in WebSocket connection");
            return;
        }
        List<String> authHeaders = headers.get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            logger.error("Authorization header is empty");
            return;
        }
        String authHeader = authHeaders.get(0);
        logger.info("Authorization header: {}", authHeader);
        if (!authHeader.startsWith("Bearer ")) {
            logger.error("Invalid Authorization header: {}", authHeader);
            return;
        }
        String token = authHeader.substring(7);
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);
            logger.info("WebSocket connected for userId={}, role={}", userId, role);
            String uid = generateUniqueUid();
            if (role.equalsIgnoreCase("CLIENT")) {
                clientUidMap.put(userId, uid);
                supportUidMap.remove(userId);
                logger.info("Assigned UID={} to client userId={}, clientUidMap size={}", uid, userId, clientUidMap.size());
            } else if (role.equalsIgnoreCase("SUPPORT")) {
                supportUidMap.put(userId, uid);
                clientUidMap.remove(userId);
                logger.info("Assigned UID={} to support userId={}, supportUidMap size={}", uid, userId, supportUidMap.size());
            } else {
                logger.error("Unknown role: {}", role);
                return;
            }
            scheduler.schedule(() -> {
                Map<String, String> uidMessage = new HashMap<>();
                uidMessage.put("uid", uid);
                messagingTemplate.convertAndSend("/user/" + userId + "/uid", uidMessage);
                logger.info("Sent UID={} to /user/{}/uid", uid, userId);
            }, 500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("Error processing WebSocket connection: {}", e.getMessage());
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        logger.info("WebSocket disconnected for session: {}", sessionId);
        clientUidMap.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(sessionId)) {
                logger.info("Removed client userId={} from clientUidMap", entry.getKey());
                return true;
            }
            return false;
        });
        supportUidMap.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(sessionId)) {
                logger.info("Removed support userId={} from supportUidMap", entry.getKey());
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
        logger.info("Assigning ticket {} to supportId {}", ticketId, supportId);
        ticketAssignments.put(ticketId, supportId);
        logger.info("Updated ticketAssignments: {}", ticketAssignments);
        notifySupportTicketAssigned(ticketId, supportId);
    }

    @MessageMapping("/tickets/created")
    public void handleTicketCreated(Map<String, Object> message) {
        logger.info("Received WebSocket ticket creation notification on /app/tickets/created: {}", message);
        processTicket(message);
    }

    private void processTicket(Map<String, Object> message) {
        String jwtToken = (String) message.get("jwtToken");
        logger.debug("Extracted JWT token: {}", jwtToken);
        if (jwtToken == null || !jwtToken.startsWith("Bearer ")) {
            logger.error("Missing or invalid JWT token in ticket notification");
            return;
        }
        String token = jwtToken.substring(7);
        String role;
        try {
            role = jwtUtil.getRoleFromToken(token);
            logger.debug("JWT role extracted: {}", role);
            if (!role.equals("ADMIN") && !role.equals("SUPPORT") && !role.equals("CLIENT")) {
                logger.error("Unauthorized role for ticket notification: {}", role);
                return;
            }
        } catch (Exception e) {
            logger.error("Error validating JWT token: {}", e.getMessage(), e);
            return;
        }
        logger.debug("Attempting to extract ticket from message: {}", message.get("ticket"));
        TicketDTO ticketDTO;
        try {
            ticketDTO = objectMapper.convertValue(message.get("ticket"), TicketDTO.class);
            logger.info("Ticket extracted successfully: id={}, clientId={}, supportTeamId={}, title='{}', description='{}', priority={}, categoryId={}, status={}",
                    ticketDTO.getId(), ticketDTO.getClientId(), ticketDTO.getSupportTeamId(), ticketDTO.getTitle(),
                    ticketDTO.getDescription(), ticketDTO.getPriority(), ticketDTO.getCategoryId(), ticketDTO.getStatus());
        } catch (Exception e) {
            logger.error("Error extracting ticket from message: {}", e.getMessage(), e);
            return;
        }
        Long supportTeamId = ticketDTO.getSupportTeamId();
        logger.debug("Processing supportTeamId: {}", supportTeamId);
        if (supportTeamId != null && supportTeamId != 0) {
            String supportUid = supportUidMap.get(supportTeamId);
            logger.info("Current supportUidMap: {}", supportUidMap);
            logger.info("Retrieved supportUid={} for supportTeamId={}", supportUid, supportTeamId);
            if (supportUid != null) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("id", ticketDTO.getId());
                notification.put("clientId", ticketDTO.getClientId());
                notification.put("supportTeamId", ticketDTO.getSupportTeamId());
                notification.put("categoryId", ticketDTO.getCategoryId());
                notification.put("subject", ticketDTO.getTitle());
                notification.put("description", ticketDTO.getDescription());
                notification.put("priority", ticketDTO.getPriority());
                notification.put("status", ticketDTO.getStatus());
                notification.put("category", ticketDTO.getCategoryId() == 1 ? "Technical" : ticketDTO.getCategoryId() == 2 ? "Billing" : "General");
                notification.put("unreadCount", 0);
                notification.put("lastMessageTime", null);
                String destination = "/user/" + supportUid + "/new-tickets";
                logger.debug("Preparing to send notification to destination: {}, payload: {}", destination, notification);
                try {
                    messagingTemplate.convertAndSend(destination, notification);
                    logger.info("Successfully sent notification to {} for ticketId={}", destination, ticketDTO.getId());
                } catch (Exception e) {
                    logger.error("Failed to send notification to {} for ticketId={}: {}", destination, ticketDTO.getId(), e.getMessage(), e);
                }
            } else {
                logger.warn("No UID found for supportTeamId={} in supportUidMap", supportTeamId);
            }
        } else {
            logger.warn("No supportTeamId assigned to ticketId={}", ticketDTO.getId());
        }
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
        logger.info("Received POST request: ticketId={}, senderId={}, senderType={}, message={}", ticketId, senderId, senderType, message);
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
        logger.info("Received GET request: ticketId={}", ticketId);
        return chatService.getMessagesByTicketId(ticketId, userIdFromToken);
    }

    @MessageMapping("/messages/{uid}")
    public void sendWebSocketMessage(@DestinationVariable String uid, ChatMessageDTO messageDTO) {
        logger.info("Received WebSocket message for uid {}: {}", uid, messageDTO.getMessage());
        String token = messageDTO.getJwtToken();
        if (token == null || !token.startsWith("Bearer ")) {
            logger.error("Missing or invalid JWT token in message");
            return;
        }
        token = token.substring(7);
        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);
            logger.info("Extracted userId={}, role={} from JWT", userId, role);
            String expectedUid = role.equals("CLIENT") ? clientUidMap.get(userId) : supportUidMap.get(userId);
            if (expectedUid == null) {
                logger.warn("No UID found for userId={}, role={}. Attempting to reassign UID.", userId, role);
                expectedUid = generateUniqueUid();
                if (role.equals("CLIENT")) {
                    clientUidMap.put(userId, expectedUid);
                    supportUidMap.remove(userId);
                    logger.info("Reassigned UID={} to client userId={}", expectedUid, userId);
                } else {
                    supportUidMap.put(userId, expectedUid);
                    clientUidMap.remove(userId);
                    logger.info("Reassigned UID={} to support userId={}", expectedUid, userId);
                }
                Map<String, String> uidMessage = new HashMap<>();
                uidMessage.put("uid", expectedUid);
                messagingTemplate.convertAndSend("/user/" + userId + "/uid", uidMessage);
                logger.info("Sent reassigned UID={} to /user/{}/uid", expectedUid, userId);
            }
            if (!uid.equals(expectedUid)) {
                logger.error("UID mismatch: received={}, expected={}", uid, expectedUid);
                return;
            }
            if (role.equals("CLIENT") && supportUidMap.containsKey(userId)) {
                logger.error("UserId={} found in supportUidMap but role is CLIENT", userId);
                return;
            } else if (role.equals("SUPPORT") && clientUidMap.containsKey(userId)) {
                logger.error("UserId={} found in clientUidMap but role is SUPPORT", userId);
                return;
            }
            Long ticketId = messageDTO.getTicketId();
            if (ticketId == null) {
                logger.error("Missing ticketId in message");
                return;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            logger.info("Making request to ticket service with headers: {}", headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://tickets.prjsdr.xyz/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: {}", ticketResponse);
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportTeamId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            logger.info("Ticket service response: ticketId={}, supportTeamId={}, clientId={}", ticketId, supportTeamId, clientId);
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
            String senderUid = role.equals("CLIENT") ? clientUidMap.get(userId) : supportUidMap.get(userId);
            if (senderUid != null) {
                messagingTemplate.convertAndSend("/user/" + senderUid + "/messages", message);
                logger.info("Sent message to sender: userId={}, uid={}", userId, senderUid);
            } else {
                logger.warn("Sender UID not found for userId={}", userId);
            }
            String receiverUid = null;
            Long receiverId = null;
            if (role.equals("CLIENT")) {
                receiverId = supportTeamId;
                Long assignedSupportId = ticketAssignments.get(ticketId);
                logger.info("Checking support receiver: ticketId={}, supportTeamId={}, assignedSupportId={}, ticketAssignments={}", ticketId, supportTeamId, assignedSupportId, ticketAssignments);
                if (assignedSupportId == null) {
                    logger.warn("No assigned support for ticketId={}. Using supportTeamId={} from ticket service.", ticketId, supportTeamId);
                    assignedSupportId = supportTeamId;
                    ticketAssignments.put(ticketId, assignedSupportId);
                    logger.info("Updated ticketAssignments: {}", ticketAssignments);
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
                logger.info("Sent message to receiver: receiverId={}, uid={}", receiverId, receiverUid);
            } else {
                logger.warn("No valid receiver UID found for receiverId={} for ticketId={}, supportUidMap={}, ticketAssignments={}", receiverId, ticketId, supportUidMap, ticketAssignments);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
        }
    }

    private void notifySupportTicketAssigned(Long ticketId, Long supportId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("ticketId", ticketId);
        notification.put("message", "You have been assigned a new ticket: " + ticketId);
        String supportUid = supportUidMap.get(supportId);
        if (supportUid != null) {
            messagingTemplate.convertAndSend("/user/" + supportUid + "/tickets", notification);
            logger.info("Notified supportId {} of new ticket assignment: ticketId={}", supportId, ticketId);
        } else {
            logger.warn("No UID found for supportId={}", supportId);
        }
    }

    private void notifySupportNewMessage(Long ticketId, TicketMessage message) {
        Long supportId = ticketAssignments.get(ticketId);
        if (supportId == null) {
            logger.warn("No support assigned to ticketId={} in ticketAssignments", ticketId);
            return;
        }
        String supportUid = supportUidMap.get(supportId);
        if (supportUid != null) {
            messagingTemplate.convertAndSend("/user/" + supportUid + "/messages", message);
            logger.info("Notified supportId {} of new message in ticketId={}", supportId, ticketId);
        } else {
            logger.warn("No UID found for supportId={}", supportId);
        }
    }

    private boolean mockCheckTicketOwnership(Long userId, Long ticketId) {
        List<TicketMessage> messages = ticketMessageRepository.findByTicketIdAndUserId(ticketId, userId);
        return !messages.isEmpty();
    }

    private ReceiverInfo getReceiverInfo(Long ticketId, SenderType senderType) {
        Long assignedSupportId = ticketAssignments.get(ticketId);
        if (assignedSupportId == null) {
            logger.warn("No support assigned to ticketId={} in ticketAssignments", ticketId);
            return null;
        }
        if (senderType == SenderType.CLIENT) {
            return new ReceiverInfo(assignedSupportId, SenderType.SUPPORT);
        } else if (senderType == SenderType.SUPPORT) {
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
        public Long getReceiverId() { return receiverId; }
        public SenderType getReceiverType() { return receiverType; }
    }

    @MessageMapping("/ticket/{ticketId}/initiateCall")
    public void initiateCall(
            @DestinationVariable Long ticketId,
            CallNotificationDTO callNotification) {
        logger.info("Initiating call for ticket {} from callerId {}", ticketId, callNotification.getCallerId());
        if (!callNotification.getCallerType().equalsIgnoreCase("CLIENT")) {
            logger.error("Only clients can initiate calls");
            return;
        }
        try {
            String token = callNotification.getJwtToken();
            if (token == null || !token.startsWith("Bearer ")) {
                logger.error("Missing or invalid JWT token in call notification");
                return;
            }
            token = token.substring(7);
            Long callerId = jwtUtil.getUserIdFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);
            logger.info("JWT extracted: callerId={}, role={}, notification: callerId={}, callerType={}",
                    callerId, role, callNotification.getCallerId(), callNotification.getCallerType());
            if (!callerId.equals(callNotification.getCallerId()) || !role.equalsIgnoreCase(callNotification.getCallerType())) {
                logger.error("JWT user info does not match call notification: JWT callerId={}, notification callerId={}, JWT role={}, notification callerType={}",
                        callerId, callNotification.getCallerId(), role, callNotification.getCallerType());
                return;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            logger.info("Making request to ticket service with headers: {}", headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://tickets.prjsdr.xyz/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: {}", ticketResponse);
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            if (supportId == null || supportId == 0) {
                logger.error("No support agent assigned to ticket {}", ticketId);
                return;
            }
            if (!clientId.equals(callerId)) {
                logger.error("Caller is not the client associated with ticket {}", ticketId);
                return;
            }
            String callId = UUID.randomUUID().toString();
            callNotification.setCallId(callId);
            callNotification.setJwtToken(null);
            activeCalls.put(callId, ticketId);
            callJwtTokens.put(callId, "Bearer " + token);
            String supportUid = supportUidMap.get(supportId);
            if (supportUid != null) {
                messagingTemplate.convertAndSendToUser(
                        supportId.toString(),
                        "/ticket/" + ticketId + "/call/incoming",
                        callNotification
                );
                logger.info("Notified supportId {} of incoming call: callId={}", supportId, callId);
            } else {
                
            }
        } catch (Exception e) {
            logger.error("Error processing call initiation: {}", e.getMessage());
        }
    }

    @MessageMapping("/call/{callId}/respond")
    public void respondToCall(
            @DestinationVariable String callId,
            CallResponseDTO callResponse) {
        logger.info("Received call response for callId {}: accepted={}", callId, callResponse.isAccepted());
        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.error("No active call found for callId {}", callId);
            return;
        }
        try {
            String jwtToken = callResponse.getJwtToken() != null ? callResponse.getJwtToken() : callJwtTokens.get(callId);
            if (jwtToken == null) {
                logger.error("No JWT token available for callId {}", callId);
                return;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", jwtToken);
            logger.info("Making request to ticket service with headers: {}", headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://tickets.prjsdr.xyz/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: {}", ticketResponse);
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long clientId = root.path("ticket").path("clientId").asLong();
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            if (clientId == null || clientId == 0) {
                logger.error("No client found for ticket {}", ticketId);
                return;
            }
            callResponse.setCallId(callId);
            callResponse.setJwtToken(null);
            if (callResponse.isAccepted()) {
                callResponse.setTimestamp(LocalDateTime.now().toString());
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
                logger.info("Sent call acceptance to clientId {} and supportId {}", clientId, supportId);
            } else {
                CallNotificationDTO notification = new CallNotificationDTO();
                notification.setCallId(callId);
                notification.setJwtToken(null);
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
                logger.info("Call {} rejected and removed", callId);
            }
        } catch (Exception e) {
            logger.error("Error processing call response: {}", e.getMessage());
        }
    }

    @MessageMapping("/call/{callId}/signal")
    public void handleWebRTCSignal(
            @DestinationVariable String callId,
            WebRTCSignalDTO signal) {
        logger.info("Received WebRTC signal for callId {}: type={}", callId, signal.getType());
        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.error("No active call found for callId {}", callId);
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
            logger.info("Sent WebRTC signal to userId {}", toUserId);
        } else {
            logger.warn("No UID found for toUserId={}", toUserId);
        }
    }

    @MessageMapping("/call/{callId}/end")
    public void endCall(
            @DestinationVariable String callId) {
        logger.info("Ending call for callId {}", callId);
        Long ticketId = activeCalls.get(callId);
        if (ticketId == null) {
            logger.error("No active call found for callId {}", callId);
            return;
        }
        try {
            String jwtToken = callJwtTokens.get(callId);
            if (jwtToken == null) {
                logger.error("No JWT token available for callId {}", callId);
                return;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", jwtToken);
            logger.info("Making request to ticket service with headers: {}", headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                "https://tickets.prjsdr.xyz/api/ticket/" + ticketId,
                HttpMethod.GET,
                entity,
                String.class
            );
            String ticketResponse = response.getBody();
            logger.info("Received response from ticket service: {}", ticketResponse);
            JsonNode root = objectMapper.readTree(ticketResponse);
            Long supportId = root.path("ticket").path("supportTeamId").asLong();
            Long clientId = root.path("ticket").path("clientId").asLong();
            CallNotificationDTO notification = new CallNotificationDTO();
            notification.setCallId(callId);
            notification.setJwtToken(null);
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
            logger.info("Call {} ended and removed", callId);
        } catch (Exception e) {
            logger.error("Error processing call end: {}", e.getMessage());
        }
    }
}