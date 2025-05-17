package com.example.controller;

import com.example.service.AuthService;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import org.json.JSONObject;

@Named
@RequestScoped
public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @Inject
    private AuthService authService;

    private String email;
    private String password;
    private String errorMessage;

    public String login() {
        LOGGER.info("Attempting login for email: [" + email + "]");
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            LOGGER.warning("Email or password is null or empty");
            errorMessage = "Email and password are required";
            return null;
        }

        try {
            // Prepare JSON payload
            JSONObject payload = new JSONObject();
            payload.put("email", email);
            payload.put("password", password);

            // Create HTTP client
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8090/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            // Send request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode == 200) {
                // Parse response
                JSONObject responseJson = new JSONObject(response.body());
                String jwt = responseJson.getString("jwt");
                String status = responseJson.getString("status");

                if ("SUCCESS".equals(status) && jwt != null) {
                    LOGGER.info("Login successful, storing JWT and redirecting");
                    // Store JWT in localStorage and redirect with token as GET parameter
                    ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                    String script = "localStorage.setItem('jwt', '" + jwt + "');" +
                                   "window.location.href = 'https://app.prjsdr.xyz?token=" + jwt + "';";
                    externalContext.addResponseHeader("Content-Type", "text/html");
                    externalContext.responseFlushBuffer();
                    externalContext.getResponseOutputWriter().write("<script>" + script + "</script>");
                    FacesContext.getCurrentInstance().responseComplete();
                    return null;
                } else {
                    LOGGER.warning("Login failed: Invalid response status");
                    errorMessage = "Login failed, please try again";
                    return null;
                }
            } else if (statusCode == 403) {
                LOGGER.warning("Login failed: Invalid credentials");
                errorMessage = "Invalid email or password";
                return null;
            } else {
                LOGGER.warning("Login failed: Unexpected status code " + statusCode);
                errorMessage = "Login error, please try again";
                return null;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.severe("Login request failed: " + e.getMessage());
            errorMessage = "Login error, please try again";
            return null;
        }
    }

    // Getters and setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}