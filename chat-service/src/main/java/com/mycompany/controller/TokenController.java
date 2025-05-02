package com.mycompany.controller;

import com.mycompany.util.JwtUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TokenController {

    @GetMapping("/api/token")
    public String getToken() {
        // Generate a token with dummy values for testing
        Long userId = 2L;
        String role = "SUPPORT";
        return JwtUtil.generateToken(userId, role);
    }
}