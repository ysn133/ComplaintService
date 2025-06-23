package com.example.model;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

@Named("admin")
@RequestScoped
public class AdminForm {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String username;

    /* ───────── Getters & Setters ───────── */

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

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
