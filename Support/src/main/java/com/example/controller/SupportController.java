package com.example.controller;

import javax.enterprise.context.RequestScoped;
import javax.faces.context.FacesContext;
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
public class SupportController {
    private static final Logger LOGGER = Logger.getLogger(SupportController.class.getName());

    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String errorMessage;
    private String successMessage;
    private String jwt;

    public void loadProfile() {
        LOGGER.info("Loading support user profile");
        if (jwt == null || jwt.trim().isEmpty()) {
            LOGGER.warning("No JWT provided");
            errorMessage = "Authentication token missing";
            return;
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://support-api.prjsdr.xyz/api/supports/me"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            LOGGER.info("Load profile response [status=" + statusCode + "]: " + responseBody);

            if (statusCode == 200) {
                JSONObject responseJson = new JSONObject(responseBody);
                if ("SUCCESS".equals(responseJson.getString("status"))) {
                    JSONObject support = responseJson.getJSONObject("support");
                    this.email = support.getString("email");
                    this.firstName = support.getString("firstname");
                    this.lastName = support.getString("lastname");
                    LOGGER.info("Profile loaded successfully: email=" + email + ", firstName=" + firstName + ", lastName=" + lastName);
                    errorMessage = null;
                } else {
                    String message = responseJson.has("message") ? responseJson.getString("message") : "Unknown error occurred";
                    LOGGER.warning("Failed to load profile: " + message);
                    errorMessage = message;
                }
            } else if (statusCode == 404) {
                LOGGER.warning("Support user not found");
                errorMessage = "Support user not found";
            } else {
                LOGGER.warning("Unexpected status code: " + statusCode);
                errorMessage = "Error loading profile (status: " + statusCode + ")";
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.severe("Profile load request failed: " + e.getMessage());
            errorMessage = "Error loading profile";
        } catch (Exception e) {
            LOGGER.severe("Error parsing response: " + e.getMessage());
            errorMessage = "Error processing server response";
        }
    }

    public String updateProfile() {
        LOGGER.info("Updating support user profile for email: [" + email + "]");
        if (jwt == null || jwt.trim().isEmpty()) {
            LOGGER.warning("No JWT provided");
            errorMessage = "Authentication token missing";
            successMessage = null;
            return null;
        }

        if (email == null || email.trim().isEmpty()) {
            LOGGER.warning("Email is null or empty");
            errorMessage = "Email is required";
            successMessage = null;
            return null;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("email", email);
            if (password != null && !password.trim().isEmpty()) {
                payload.put("password", password);
            }
            payload.put("firstname", firstName);
            payload.put("lastname", lastName);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://support-api.prjsdr.xyz/api/supports/me"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            LOGGER.info("Update profile response [status=" + statusCode + "]: " + responseBody);

            if (statusCode == 200) {
                JSONObject responseJson = new JSONObject(responseBody);
                if ("SUCCESS".equals(responseJson.getString("status"))) {
                    JSONObject support = responseJson.getJSONObject("support");
                    this.email = support.getString("email");
                    this.firstName = support.getString("firstname");
                    this.lastName = support.getString("lastname");
                    this.password = null; // Clear password
                    LOGGER.info("Profile updated successfully");
                    successMessage = "Profile updated successfully";
                    errorMessage = null;
                    return null;
                } else {
                    String message = responseJson.has("message") ? responseJson.getString("message") : 
                                     responseJson.has("error") ? responseJson.getString("error") : "Unknown error occurred";
                    LOGGER.warning("Failed to update profile: " + message);
                    errorMessage = message;
                    successMessage = null;
                    return null;
                }
            } else if (statusCode == 400) {
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    LOGGER.warning("Failed to update profile: Empty response body");
                    errorMessage = "Invalid response from server";
                    successMessage = null;
                    return null;
                }
                try {
                    JSONObject responseJson = new JSONObject(responseBody);
                    String message = responseJson.has("message") ? responseJson.getString("message") : 
                                     responseJson.has("error") ? responseJson.getString("error") : "Invalid input data";
                    LOGGER.warning("Failed to update profile: " + message);
                    errorMessage = message;
                    successMessage = null;
                    return null;
                } catch (Exception e) {
                    LOGGER.severe("Failed to parse response: " + e.getMessage());
                    errorMessage = "Error processing server response";
                    successMessage = null;
                    return null;
                }
            } else {
                LOGGER.warning("Unexpected status code: " + statusCode);
                errorMessage = "Error updating profile (status: " + statusCode + ")";
                successMessage = null;
                return null;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.severe("Profile update request failed: " + e.getMessage());
            errorMessage = "Error updating profile";
            successMessage = null;
            return null;
        } catch (Exception e) {
            LOGGER.severe("Error parsing response: " + e.getMessage());
            errorMessage = "Error processing server response";
            successMessage = null;
            return null;
        }
    }

    // Getters and setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSuccessMessage() { return successMessage; }
    public void setSuccessMessage(String successMessage) { this.successMessage = successMessage; }
    public String getJwt() { return jwt; }
    public void setJwt(String jwt) { this.jwt = jwt; }
}