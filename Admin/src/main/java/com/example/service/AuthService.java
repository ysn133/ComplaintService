package com.example.service;

import com.example.model.Admin;
import com.example.util.JwtUtil;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private final EntityManager em;

    public AuthService() {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("UsersPU");
        em = emf.createEntityManager();
    }

    public String authenticate(String email, String password) {
        LOGGER.info("Authenticating admin: " + email);
        if (email == null || email.isBlank() || password == null) {
            return null;
        }
        try {
            Admin admin = em
                .createNamedQuery("Admin.findByEmail", Admin.class)
                .setParameter("email", email.trim())
                .getSingleResult();

            if (admin != null && password.equals(admin.getPassword())) {
                /* generate ADMIN token */
                return JwtUtil.generateToken(admin.getId(), "ADMIN");
            }
        } catch (NoResultException e) {
            LOGGER.warning("Admin not found for email: " + email);
        } catch (Exception e) {
            LOGGER.severe("Auth error: " + e.getMessage());
        }
        return null;
    }

    public List<Admin> getAllAdmins() {
        try {
            return em.createQuery("SELECT a FROM Admin a", Admin.class).getResultList();
        } catch (Exception e) {
            LOGGER.severe("getAllAdmins failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
