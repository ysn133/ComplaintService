package com.example.controller;

import com.example.model.Client;
import com.example.service.AuthService;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.logging.Logger;

@Named
@RequestScoped
public class UserListController {
    private static final Logger LOGGER = Logger.getLogger(UserListController.class.getName());

    @Inject
    private AuthService authService;

    public List<Client> getAllUsers() {
        LOGGER.info("Fetching all users");
        List<Client> users = authService.getAllClients();
        LOGGER.info("Retrieved vuw012 " + users.size() + " users");
        return users;
    }
}