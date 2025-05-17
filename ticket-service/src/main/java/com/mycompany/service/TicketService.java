package com.mycompany.service;

import com.mycompany.entity.Ticket;
import com.mycompany.entity.TicketDTO;
import com.mycompany.repository.TicketRepository;
import com.mycompany.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;

@Service
public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    private String supportServiceUrl = "http://support-api.prjsdr.xyz/api/support/available";

    private String activeTicketsUrl = "http://support-api.prjsdr.xyz/api/support/activeTickets";

    public Ticket createTicket(TicketDTO ticketDTO, Long clientId) {
        logger.debug("Creating ticket for client ID: {}", clientId);

        // Validate categoryId
        if (ticketDTO.getCategoryId() == null) {
            logger.error("Category ID is null in TicketDTO");
            throw new IllegalArgumentException("Category ID is required");
        }
        logger.debug("Category ID: {}", ticketDTO.getCategoryId());

        // Map TicketDTO to Ticket
        Ticket ticket = new Ticket();
        ticket.setClientId(clientId);
        ticket.setCategoryId(ticketDTO.getCategoryId());
        ticket.setTitle(ticketDTO.getTitle());
        ticket.setDescription(ticketDTO.getDescription());
        ticket.setPriority(ticketDTO.getPriority());

        // Generate admin JWT
        String adminJwt = jwtUtil.generateToken(1L, "ADMIN");

        // Fetch supportTeamId from internal API
        String url = supportServiceUrl + "/" + ticket.getCategoryId();
        logger.debug("Calling support service at: {}", url);
        Long supportTeamId = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + adminJwt);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            logger.debug("Support service response: Status={}, Body={}", response.getStatusCode(), response.getBody());
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().containsKey("supportTeamId")) {
                supportTeamId = ((Number) response.getBody().get("supportTeamId")).longValue();
                logger.debug("Retrieved supportTeamId: {}", supportTeamId);
            } else {
                logger.error("Failed to fetch support team ID. Response: {}", response.getBody());
                throw new IllegalStateException("Failed to fetch support team ID");
            }
        } catch (Exception e) {
            logger.error("Error fetching support team ID: {}", e.getMessage(), e);
            throw new IllegalStateException("Unable to assign support team: " + e.getMessage());
        }

        // Set supportTeamId on ticket
        ticket.setSupportTeamId(supportTeamId);

        // Save ticket to ensure supportTeamId is persisted
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setStatus("OPEN");
        Ticket savedTicket = ticketRepository.save(ticket);
        logger.debug("Saved ticket with ID: {} and supportTeamId: {}", savedTicket.getId(), savedTicket.getSupportTeamId());

        // Count non-closed/resolved tickets for the support team
        List<String> closedStatuses = Arrays.asList("CLOSED", "RESOLVED");
        long activeTicketCount = ticketRepository.countBySupportTeamIdAndStatusNotIn(supportTeamId, closedStatuses);
        logger.debug("Active ticket count for support team {}: {}", supportTeamId, activeTicketCount);

        // Update support team workload
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + adminJwt);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Long> payload = Map.of(
                "supportTeamId", supportTeamId,
                "activeTickets", activeTicketCount
            );
            logger.debug("Sending activeTickets payload: {}", payload);
            HttpEntity<Map<String, Long>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Void> response = restTemplate.exchange(activeTicketsUrl, HttpMethod.POST, entity, Void.class);
            logger.debug("Active tickets update response: Status={}", response.getStatusCode());
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("Failed to update active tickets for support team {}", supportTeamId);
            }
        } catch (Exception e) {
            logger.error("Error updating active tickets: {}", e.getMessage(), e);
            // Log error but don't fail ticket creation
        }

        return savedTicket;
    }

    public List<Ticket> getTicketsByClientId(Long clientId) {
        logger.debug("Fetching tickets for client ID: {}", clientId);
        return ticketRepository.findByClientId(clientId);
    }

    public List<Ticket> getTicketsBySupportTeamId(Long supportTeamId) {
        logger.debug("Fetching tickets for support team ID: {}", supportTeamId);
        return ticketRepository.findBySupportTeamId(supportTeamId);
    }

    public Ticket getTicketById(Long id) {
        logger.debug("Fetching ticket with ID: {}", id);
        return ticketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found with ID: " + id));
    }
}