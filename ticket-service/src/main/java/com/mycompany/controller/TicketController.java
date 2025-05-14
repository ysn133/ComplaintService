package com.mycompany.controller;

import com.mycompany.entity.Ticket;
import com.mycompany.entity.TicketDTO;
import com.mycompany.service.TicketService;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "https://192.168.0.102:3000", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}, allowedHeaders = "*")
@Validated
public class TicketController {
    private static final Logger logger = LoggerFactory.getLogger(TicketController.class);

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestBody @Valid TicketDTO ticketDTO,
            @RequestHeader("Authorization") String authorizationHeader) {
        logger.debug("Processing createTicket request");
        Map<String, Object> response = new HashMap<>();
        try {
            Long clientId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Ticket ticket = ticketService.createTicket(ticketDTO, clientId);
            ticketDTO.setId(ticket.getId());
            ticketDTO.setClientId(clientId);
            ticketDTO.setSupportTeamId(ticket.getSupportTeamId());
            ticketDTO.setStatus(ticket.getStatus());
            response.put("status", "SUCCESS");
            response.put("ticketId", ticket.getId());
            response.put("ticket", ticket);
            logger.info("Created ticket with id: {} for client id: {}", ticket.getId(), clientId);

            // Notify chat service via WebSocket
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("ticket", ticketDTO);
                message.put("jwtToken", authorizationHeader);
                messagingTemplate.convertAndSend("/topic/tickets/created", message);
                logger.info("Notified chat service via WebSocket for ticket id: {}", ticket.getId());
            } catch (Exception e) {
                logger.error("Failed to notify chat service via WebSocket for ticket id: {}: {}", ticket.getId(), e.getMessage());
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to create ticket: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> getTickets(@RequestHeader("Authorization") String authorizationHeader) {
        logger.debug("Processing getTickets request");
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String jwt = authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : authorizationHeader;
        String role = jwtUtil.getRoleFromToken(jwt);

        List<Ticket> tickets;
        if ("CLIENT".equals(role)) {
            tickets = ticketService.getTicketsByClientId(userId);
            logger.info("Retrieved {} tickets for client id: {}", tickets.size(), userId);
        } else if ("SUPPORT".equals(role) || "ADMIN".equals(role)) {
            tickets = ticketService.getTicketsBySupportTeamId(userId);
            logger.info("Retrieved {} tickets for support team id: {}", tickets.size(), userId);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Invalid role");
            logger.warn("Invalid role {} for user id: {}", role, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        response.put("status", "SUCCESS");
        response.put("tickets", tickets);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ticket/{id}")
    public ResponseEntity<Map<String, Object>> getTicketById(@PathVariable Long id, @RequestHeader("Authorization") String authorizationHeader) {
        logger.debug("Processing getTicketById request for id: {}", id);
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String jwt = authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : authorizationHeader;
        String role = jwtUtil.getRoleFromToken(jwt);

        try {
            Ticket ticket = ticketService.getTicketById(id);
            if ("CLIENT".equals(role)) {
                if (!ticket.getClientId().equals(userId)) {
                    response.put("status", "ERROR");
                    response.put("message", "Unauthorized access to ticket");
                    logger.warn("Unauthorized access to ticket id: {} by client id: {}", id, userId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            } else if ("SUPPORT".equals(role) || "ADMIN".equals(role)) {
                if (!ticket.getSupportTeamId().equals(userId)) {
                    response.put("status", "ERROR");
                    response.put("message", "Unauthorized access to ticket");
                    logger.warn("Unauthorized access to ticket id: {} by support team id: {}", id, userId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                }
            } else {
                response.put("status", "ERROR");
                response.put("message", "Invalid role");
                logger.warn("Invalid role {} for user id: {}", role, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            response.put("status", "SUCCESS");
            response.put("ticket", ticket);
            logger.info("Retrieved ticket with id: {} for user id: {} (role: {})", id, userId, role);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to retrieve ticket with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}