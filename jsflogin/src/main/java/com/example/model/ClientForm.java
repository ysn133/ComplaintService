package com.example.model;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

@Named("client")
@RequestScoped
public class ClientForm {
    private String email;
    private String password;

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
}