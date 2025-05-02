package com.mycompany.controller;

import com.mycompany.entity.Admin;
import com.mycompany.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
@Validated
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

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

    @GetMapping("/admin/me")
    public ResponseEntity<Map<String, Object>> getCurrentAdmin() {
        logger.debug("Processing getCurrentAdmin request");
        Map<String, Object> response = new HashMap<>();
        Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Admin admin = authService.findAdminById(id);
        if (admin != null) {
            response.put("status", "SUCCESS");
            response.put("admin", admin);
            logger.info("Retrieved admin with id: {}", id);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Admin user not found");
            logger.warn("Admin not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> createAdmin(@Valid @RequestBody Admin admin) {
        logger.debug("Processing createAdmin request for email: {}", admin.getEmail());
        Map<String, Object> response = new HashMap<>();
        try {
            Admin createdAdmin = authService.createAdmin(admin);
            response.put("status", "SUCCESS");
            response.put("admin", createdAdmin);
            logger.info("Created admin with email: {}", admin.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to create admin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/admin/me")
    public ResponseEntity<Map<String, Object>> updateAdmin(@Valid @RequestBody Admin updatedAdmin) {
        logger.debug("Processing updateAdmin request");
        Map<String, Object> response = new HashMap<>();
        try {
            Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Admin admin = authService.updateAdmin(id, updatedAdmin);
            response.put("status", "SUCCESS");
            response.put("admin", admin);
            logger.info("Updated admin with id: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to update admin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
}