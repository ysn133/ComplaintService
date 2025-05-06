package com.mycompany.controller;

import com.mycompany.entity.Support;
import com.mycompany.service.AuthService;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody @Valid LoginRequest request) {
        logger.debug("Processing login request for email: {}", request.getEmail());
        Map<String, Object> response = new HashMap<>();
        String jwt = authService.authenticate(request.getEmail(), request.getPassword());
        if (jwt != null) {
            response.put("status", "SUCCESS");
            response.put("jwt", jwt);
            logger.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Invalid email or password");
            logger.warn("Login failed for email: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @GetMapping("/supports/me")
    public ResponseEntity<Map<String, Object>> getCurrentSupport() {
        logger.debug("Processing getCurrentSupport request");
        Map<String, Object> response = new HashMap<>();
        Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Support support = authService.findSupportById(id);
        if (support != null) {
            response.put("status", "SUCCESS");
            response.put("support", support);
            logger.info("Retrieved support with id: {}", id);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Support user not found");
            logger.warn("Support not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping("/supports")
    public ResponseEntity<Map<String, Object>> createSupport(@Valid @RequestBody Support support) {
        logger.debug("Processing createSupport request for email: {}", support.getEmail());
        Map<String, Object> response = new HashMap<>();
        try {
            Support createdSupport = authService.createSupport(support);
            response.put("status", "SUCCESS");
            response.put("support", createdSupport);
            logger.info("Created support with email: {}", support.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to create support: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/supports")
    public ResponseEntity<Map<String, Object>> getAllSupports() {
        logger.debug("Processing getAllSupports request");
        Map<String, Object> response = new HashMap<>();
        try {
            List<Support> supports = authService.findAllSupports();
            response.put("status", "SUCCESS");
            response.put("supports", supports);
            logger.info("Retrieved {} support users", supports.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Failed to retrieve support users");
            logger.warn("Failed to retrieve support users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/supports/{id}")
    public ResponseEntity<Map<String, Object>> getSupportById(@PathVariable Long id) {
        logger.debug("Processing getSupportById request for id: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            Support support = authService.findSupportById(id);
            response.put("status", "SUCCESS");
            response.put("support", support);
            logger.info("Retrieved support with id: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to retrieve support with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @DeleteMapping("/supports/{id}")
    public ResponseEntity<Map<String, Object>> deleteSupport(@PathVariable Long id) {
        logger.debug("Processing deleteSupport request for id: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            authService.deleteSupport(id);
            response.put("status", "SUCCESS");
            response.put("message", "Support account deleted successfully");
            logger.info("Deleted support with id: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to delete support with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PutMapping("/supports/me")
    public ResponseEntity<Map<String, Object>> updateSupport(@Valid @RequestBody Support updatedSupport) {
        logger.debug("Processing updateSupport request");
        Map<String, Object> response = new HashMap<>();
        try {
            Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Support support = authService.updateSupport(id, updatedSupport);
            response.put("status", "SUCCESS");
            response.put("support", support);
            logger.info("Updated support with id: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to update support: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/supports/{id}")
    public ResponseEntity<Map<String, Object>> updateSupportById(@PathVariable Long id, @Valid @RequestBody Support updatedSupport) {
        logger.debug("Processing updateSupportById request for id: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            Support support = authService.updateSupport(id, updatedSupport);
            response.put("status", "SUCCESS");
            response.put("support", support);
            logger.info("Updated support with id: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to update support with id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/support/available/{categoryId}")
    public ResponseEntity<Map<String, Object>> getAvailableSupport(@PathVariable Long categoryId, @RequestHeader("Authorization") String authorizationHeader) {
        logger.debug("Processing getAvailableSupport request for category ID: {}", categoryId);
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                response.put("status", "ERROR");
                response.put("message", "Invalid authentication details");
                logger.warn("Invalid authentication details for getAvailableSupport");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String jwt = authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : authorizationHeader;
            String role = jwtUtil.getRoleFromToken(jwt);
            if (!"ADMIN".equals(role)) {
                response.put("status", "ERROR");
                response.put("message", "Only admins can access this endpoint");
                logger.warn("Non-admin role {} attempted to access getAvailableSupport", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            Long supportTeamId = authService.getAvailableSupportId(categoryId);
            response.put("status", "SUCCESS");
            response.put("supportTeamId", supportTeamId);
            logger.info("Retrieved support ID: {} for category ID: {}", supportTeamId, categoryId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to retrieve support: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Invalid JWT token");
            logger.warn("Invalid JWT token for getAvailableSupport: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/support/activeTickets")
    public ResponseEntity<Map<String, Object>> updateActiveTickets(@RequestBody @Valid ActiveTicketsRequest request, @RequestHeader("Authorization") String authorizationHeader) {
        logger.debug("Processing updateActiveTickets request for supportTeamId: {}", request.getSupportTeamId());
        Map<String, Object> response = new HashMap<>();
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getPrincipal() == null) {
                response.put("status", "ERROR");
                response.put("message", "Invalid authentication details");
                logger.warn("Invalid authentication details for updateActiveTickets");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String jwt = authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : authorizationHeader;
            String role = jwtUtil.getRoleFromToken(jwt);
            if (!"ADMIN".equals(role)) {
                response.put("status", "ERROR");
                response.put("message", "Only admins can access this endpoint");
                logger.warn("Non-admin role {} attempted to access updateActiveTickets", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            authService.updateSupportWorkload(request.getSupportTeamId(), request.getActiveTickets());
            response.put("status", "SUCCESS");
            response.put("message", "Workload updated successfully for support team ID: " + request.getSupportTeamId());
            logger.info("Updated workload for support team ID: {} with active tickets: {}", request.getSupportTeamId(), request.getActiveTickets());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to update workload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Invalid JWT token");
            logger.warn("Invalid JWT token for updateActiveTickets: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
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

    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ActiveTicketsRequest {
        @NotNull(message = "Support team ID is required")
        private Long supportTeamId;

        @NotNull(message = "Active tickets count is required")
        private Long activeTickets;

        public Long getSupportTeamId() { return supportTeamId; }
        public void setSupportTeamId(Long supportTeamId) { this.supportTeamId = supportTeamId; }
        public Long getActiveTickets() { return activeTickets; }
        public void setActiveTickets(Long activeTickets) { this.activeTickets = activeTickets; }
    }
}