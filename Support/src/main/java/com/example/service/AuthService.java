
package com.example.service;

import com.example.model.Client;
import com.example.util.JwtUtil;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private EntityManager em;

    public AuthService() {
        LOGGER.info("AuthService instantiated def567");
        try {
            LOGGER.info("Attempting to create EntityManagerFactory for UsersPU");
            EntityManagerFactory emf = Persistence.createEntityManagerFactory("UsersPU");
            LOGGER.info("EntityManagerFactory created successfully def567");
            em = emf.createEntityManager();
            LOGGER.info("EntityManager created successfully def567");
        } catch (Exception e) {
            LOGGER.severe("Failed to create EntityManagerFactory: " + e.getMessage());
            if (e.getCause() != null) {
                LOGGER.severe("Cause: " + e.getCause().getMessage());
                for (StackTraceElement ste : e.getCause().getStackTrace()) {
                    LOGGER.severe("Cause stack trace: " + ste);
                }
            }
            for (StackTraceElement ste : e.getStackTrace()) {
                LOGGER.severe("Stack trace: " + ste);
            }
        }
    }

    public String authenticate(String email, String password) {
        LOGGER.info("Authenticating user with email: [" + email + "] def567");
        LOGGER.info("Password provided: [" + (password != null ? "****" : "null") + "] def567");
        if (em == null) {
            LOGGER.severe("EntityManager is null! Check persistence.xml and dependencies def567");
            return null;
        }
        if (email == null || email.trim().isEmpty()) {
            LOGGER.warning("Email is null or empty def567");
            return null;
        }
        try {
            LOGGER.info("Testing database connection with SELECT 1");
            em.createNativeQuery("SELECT 1").getSingleResult();
            LOGGER.info("Database connection successful");
            LOGGER.info("Executing query for email: [" + email + "]");
            Client client = em.createNamedQuery("Client.findByEmail", Client.class)
                             .setParameter("email", email.trim())
                             .getSingleResult();
            LOGGER.info("Found client: email=[" + client.getEmail() + "], stored password=[" + client.getPassword() + "]");
            if (client != null && password != null && password.trim().equals(client.getPassword())) {
                client.setLastLogin(LocalDateTime.now());
                em.getTransaction().begin();
                em.merge(client);
                em.getTransaction().commit();
                LOGGER.info("Authentication successful for: [" + email + "]");
                String jwt = JwtUtil.generateToken(2L, "USER");
                LOGGER.info("Generated JWT for user: [" + email + "]");
                return jwt;
            } else {
                LOGGER.warning("Password mismatch for email=[" + email + "], input=[" + password + "]");
            }
        } catch (NoResultException e) {
            LOGGER.warning("No client found for email: [" + email + "]");
        } catch (Exception e) {
            LOGGER.severe("Authentication failed: " + e.getMessage());
            e.printStackTrace();
        }
        LOGGER.warning("Authentication failed for email: [" + email + "]");
        return null;
    }

    public List<Client> getAllClients() {
        LOGGER.info("Fetching all clients");
        if (em == null) {
            LOGGER.severe("EntityManager is null! Check persistence.xml and dependencies def567");
            return new ArrayList<>();
        }
        try {
            LOGGER.info("Testing database connection with SELECT 1");
            Object result = em.createNativeQuery("SELECT 1").getSingleResult();
            LOGGER.info("Database connection successful: SELECT 1 returned " + result);
            LOGGER.info("Checking if client table exists in users_db");
            List<Object[]> tableCheck = em.createNativeQuery("SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA = 'users_db' AND TABLE_NAME = 'client'").getResultList();
            LOGGER.info("Client table exists: " + (!tableCheck.isEmpty() ? "Yes" : "No"));
            if (!tableCheck.isEmpty()) {
                LOGGER.info("Counting rows in client table");
                Long count = (Long) em.createQuery("SELECT COUNT(c) FROM Client c").getSingleResult();
                LOGGER.info("Client table has " + count + " rows");
                LOGGER.info("Listing all emails in client table via native query");
                List<String> emails = em.createNativeQuery("SELECT email FROM client").getResultList();
                if (emails.isEmpty()) {
                    LOGGER.warning("No emails found in client table");
                } else {
                    emails.forEach(email -> LOGGER.info("Found email: [" + email + "]"));
                }
            } else {
                LOGGER.severe("Client table does not exist in users_db");
            }
            LOGGER.info("Executing JPA query: SELECT c FROM Client c");
            List<Client> clients = em.createQuery("SELECT c FROM Client c", Client.class).getResultList();
            if (clients.isEmpty()) {
                LOGGER.warning("No clients found in JPA query");
            } else {
                clients.forEach(c -> LOGGER.info("JPA Client: email=[" + c.getEmail() + "], password=[" + c.getPassword() + "]"));
            }
            LOGGER.info("Retrieved " + clients.size() + " clients");
            return clients;
        } catch (Exception e) {
            LOGGER.severe("Failed to fetch clients: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
