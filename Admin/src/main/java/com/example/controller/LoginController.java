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

    // ─────────────────────────────────────────────────────────
    //  Form fields
    // ─────────────────────────────────────────────────────────
    private String email;
    private String password;
    private String errorMessage;

    // ─────────────────────────────────────────────────────────
    //  Login action
    // ─────────────────────────────────────────────────────────
    public String login() {
        LOGGER.info("Attempting ADMIN login for email: [" + email + "]");

        if (email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            errorMessage = "Email and password are required";
            LOGGER.warning("Empty credentials supplied");
            return null;
        }

        try {
            /* JSON payload */
            JSONObject payload = new JSONObject()
                    .put("email", email)
                    .put("password", password);

            /* HTTP request to **admin** service */
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://admin-api.prjsdr.xyz/api/auth/login"))          // ③ new URI
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = HttpClient
                    .newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                JSONObject responseJson = new JSONObject(response.body());
                String jwt    = responseJson.optString("jwt", null);
                String status = responseJson.optString("status", "ERROR");

                if ("SUCCESS".equals(status) && jwt != null) {
                    LOGGER.info("Admin login succeeded – redirecting to dashboard");

                    ExternalContext ext = FacesContext.getCurrentInstance().getExternalContext();
                    /* You can adjust the landing route below to your SPA / JSF page */
                    String js = "localStorage.setItem('jwt','" + jwt + "');"
                              + "window.location.href='https://app.prjsdr.xyz/admin?token=" + jwt + "';";

                    ext.addResponseHeader("Content-Type", "text/html; charset=UTF-8");
                    ext.getResponseOutputWriter().write("<script>" + js + "</script>");
                    FacesContext.getCurrentInstance().responseComplete();
                    return null;   // JSF already handled
                }
                errorMessage = "Login failed, please try again";

            } else if (statusCode == 403) {
                errorMessage = "Invalid email or password";
            } else {
                errorMessage = "Server error (" + statusCode + ")";
            }

        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Admin login request failed: " + ex.getMessage());
            errorMessage = "Login error, please try again";
        }
        return null;
    }

    /* ───────── Getters / Setters ───────── */
    public String getEmail()            { return email; }
    public void   setEmail(String email){ this.email = email; }
    public String getPassword()         { return password; }
    public void   setPassword(String p) { this.password = p; }
    public String getErrorMessage()     { return errorMessage; }
}
