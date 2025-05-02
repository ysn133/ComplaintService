package com.mycompany.config;

import com.mycompany.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = Logger.getLogger(JwtFilter.class.getName());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        // Skip filtering for token generation and WebSocket endpoints
        String requestURI = request.getRequestURI();
        if (requestURI.equals("/api/token") ||
            requestURI.startsWith("/ws") ||
            requestURI.contains("/sockjs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove "Bearer " prefix
            try {
                // Validate token and extract userId and role
                Long userId = JwtUtil.getUserIdFromToken(token);
                String role = JwtUtil.getRoleFromToken(token);

                // Store userId and role in request attributes for use in controller
                request.setAttribute("userId", userId);
                request.setAttribute("role", role);

                // Create an Authentication object and set it in SecurityContext
                UserDetails userDetails = new User(
                    userId.toString(), // Username (userId as string)
                    "", // Password (not needed for JWT)
                    Collections.singletonList(() -> role) // Role as a GrantedAuthority
                );

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null, // Credentials (not needed for JWT)
                    userDetails.getAuthorities()
                );

                // Set the Authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Proceed with the filter chain
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    // Clear the SecurityContext after the request to avoid potential issues
                    SecurityContextHolder.clearContext();
                }

            } catch (JwtException e) {
                logger.severe("JWT Validation Failed: " + e.getMessage());
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT: " + e.getMessage());
                return;
            } catch (Exception e) {
                logger.severe("Unexpected error during JWT validation: " + e.getMessage());
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
                return;
            }
        } else {
            logger.warning("Missing or invalid Authorization header for URI: " + requestURI);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }
    }

    // Helper method to send a structured JSON error response
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}