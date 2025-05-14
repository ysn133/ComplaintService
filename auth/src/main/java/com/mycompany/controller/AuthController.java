package com.mycompany.controller;

import com.mycompany.entity.Client;
import com.mycompany.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new HashMap<>();
        String jwt = authService.authenticate(request.getEmail(), request.getPassword());
        if (jwt != null) {
            response.put("status", "SUCCESS");
            response.put("jwt", jwt);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @GetMapping("/clients/me")
    public ResponseEntity<Map<String, Object>> getCurrentClient() {
        Map<String, Object> response = new HashMap<>();
        Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Client client = authService.findClientById(id);
        if (client != null) {
            response.put("status", "SUCCESS");
            response.put("client", client);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "ERROR");
            response.put("message", "Client not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping("/clients")
    public ResponseEntity<Map<String, Object>> createClient(@Valid @RequestBody Client client) {
        Map<String, Object> response = new HashMap<>();
        try {
            Client createdClient = authService.createClient(client);
            response.put("status", "SUCCESS");
            response.put("client", createdClient);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/clients/me")
    public ResponseEntity<Map<String, Object>> updateClient(@Valid @RequestBody Client updatedClient) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long id = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Client client = authService.updateClient(id, updatedClient);
            response.put("status", "SUCCESS");
            response.put("client", client);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}