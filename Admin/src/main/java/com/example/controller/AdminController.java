package com.example.controller;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;
import org.json.JSONObject;

@Named
@SessionScoped
public class AdminController implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(AdminController.class.getName());

    /* ───────── Profile fields ───────── */
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String username;

    /* ───────── “Create-Admin” form fields (separate) ───────── */
    private String newEmail;
    private String newPassword;
    private String newFirstName;
    private String newLastName;
    private String newUsername;

    /* ───────── Add-category field ───────── */
    private String categoryName;

    private String jwt;
    private String errorMessage;
    private String successMessage;

    /* =========================================================
       1.  LOAD / UPDATE PROFILE  (unchanged except dynamic JSON)
       ========================================================= */

    public void loadProfile() {
        LOGGER.info("Loading admin profile");
        if (jwt == null || jwt.isBlank()) {
            errorMessage = "Authentication token missing";
            LOGGER.warning(errorMessage);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://admin-api.prjsdr.xyz/api/admin/me"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            int code = res.statusCode();
            String body = res.body();
            LOGGER.info("loadProfile → status=" + code + " body=" + body);

            if (code == 200) {
                JSONObject json = new JSONObject(body);
                if ("SUCCESS".equals(json.optString("status"))) {
                    JSONObject admin = json.getJSONObject("admin");
                    email     = admin.optString("email");
                    firstName = admin.optString("firstname",  "");
                    lastName  = admin.optString("lastname",   "");
                    username  = admin.optString("username",   "");
                    errorMessage = null;
                    LOGGER.info("Profile loaded OK: " + email);
                } else {
                    errorMessage = json.optString("message", "Unknown error");
                }
            } else if (code == 404) {
                errorMessage = "Admin not found";
            } else {
                errorMessage = "Error loading profile (status " + code + ")";
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("loadProfile failed: " + ex.getMessage());
            errorMessage = "Error loading profile";
        }
    }

    public String updateProfile() {
        LOGGER.info("Updating admin profile [" + email + "]");
        if (jwtBlank() || blank(email)) { errorMessage = "Required data missing"; return null; }

        try {
            JSONObject p = new JSONObject();
            p.put("email", email);
            if (!blank(firstName))  p.put("firstname", firstName);
            if (!blank(lastName))   p.put("lastname",  lastName);
            if (!blank(username))   p.put("username",  username);
            if (!blank(password))   p.put("password",  password);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://admin-api.prjsdr.xyz/api/admin/me"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(p.toString()))
                    .build();

            HttpResponse<String> res = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200 && "SUCCESS".equals(new JSONObject(res.body()).optString("status"))) {
                successMessage = "Profile updated successfully";
                errorMessage   = null;
                password = null;     // clear optional field
            } else {
                errorMessage   = new JSONObject(res.body()).optString("message", "Update failed");
                successMessage = null;
            }
        } catch (Exception ex) {
            LOGGER.severe("updateProfile error: " + ex.getMessage());
            errorMessage = "Server error";
            successMessage = null;
        }
        return null;
    }

    /* =========================================================
       2.  CREATE NEW ADMIN  (uses new* fields)
       ========================================================= */
    public void createAdmin() {
        if (jwtBlank() ||
            blank(newEmail) || blank(newPassword) || blank(newUsername) ||
            blank(newFirstName) || blank(newLastName)) {
            errorMessage = "All new-admin fields are required";
            successMessage = null;
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("email",     newEmail.trim())
                    .put("password",  newPassword.trim())
                    .put("username",  newUsername.trim())
                    .put("firstname", newFirstName.trim())
                    .put("lastname",  newLastName.trim());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://admin-api.prjsdr.xyz/api/admin"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> res = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 201 || res.statusCode() == 200) {
                successMessage = "Admin created successfully";
                errorMessage   = null;
                /* clear form fields */
                newEmail = newPassword = newUsername = newFirstName = newLastName = "";
            } else {
                errorMessage   = new JSONObject(res.body()).optString("message", "Failed to create admin");
                successMessage = null;
            }
        } catch (Exception ex) {
            LOGGER.severe("createAdmin error: " + ex.getMessage());
            errorMessage = "Server error";
            successMessage = null;
        }
    }

    /* =========================================================
       3.  CREATE CATEGORY  (unchanged except blank check)
       ========================================================= */
    public void createCategory() {
        if (jwtBlank() || blank(categoryName)) { errorMessage = "Category name required"; return; }

        try {
            JSONObject body = new JSONObject().put("name", categoryName.trim());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://tickets-api.prjsdr.xyz/api/category"))
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> res = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 201 || res.statusCode() == 200) {
                successMessage = "Category added successfully";
                errorMessage   = null;
                categoryName   = "";
            } else {
                errorMessage   = new JSONObject(res.body()).optString("message", "Failed to add category");
                successMessage = null;
            }
        } catch (Exception ex) {
            LOGGER.severe("createCategory error: " + ex.getMessage());
            errorMessage = "Server error";
            successMessage = null;
        }
    }

    /* ─── helpers ─── */
    private boolean jwtBlank()         { return jwt == null || jwt.isBlank(); }
    private static boolean blank(String s){ return s==null||s.isBlank(); }

    /* ─── getters & setters (only new fields added) ─── */
    public String getEmail()              { return email; }  public void setEmail(String v){ email=v; }
    public String getPassword()           { return password; }public void setPassword(String v){ password=v; }
    public String getFirstName()          { return firstName; }public void setFirstName(String v){ firstName=v; }
    public String getLastName()           { return lastName; } public void setLastName(String v){ lastName=v; }
    public String getUsername()           { return username; } public void setUsername(String v){ username=v; }

    public String getNewEmail()           { return newEmail; }  public void setNewEmail(String v){ newEmail=v; }
    public String getNewPassword()        { return newPassword; } public void setNewPassword(String v){ newPassword=v; }
    public String getNewFirstName()       { return newFirstName; } public void setNewFirstName(String v){ newFirstName=v; }
    public String getNewLastName()        { return newLastName; }  public void setNewLastName(String v){ newLastName=v; }
    public String getNewUsername()        { return newUsername; }  public void setNewUsername(String v){ newUsername=v; }

    public String getCategoryName()       { return categoryName; }  public void setCategoryName(String v){ categoryName=v; }
    public String getJwt()                { return jwt; }          public void setJwt(String v){ jwt=v; }
    public String getErrorMessage()       { return errorMessage; } public void setErrorMessage(String v){ errorMessage=v; }
    public String getSuccessMessage()     { return successMessage; }public void setSuccessMessage(String v){ successMessage=v; }
}