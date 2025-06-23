/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.admintest;

import java.io.IOException;
import java.util.Date;

/**
 *
 * @author HP
 */
public class AdmintEst {

    public static void main(String[] args) {
                    throws ServletException, IOException {
        
        // 1. Get parameters
        String clientId = request.getParameter("clientId");
        
        // 2. Save to database
        EntityManagerFactory emf = (EntityManagerFactory)
            getServletContext().getAttribute("emf");
        EntityManager em = emf.createEntityManager();
        
        try {
            em.getTransaction().begin();
            
            BonLivraison bl = new BonLivraison();
            bl.setDateCommande(new Date());
            bl.setClientId(clientId);  // Directly use String
            em.persist(bl);
            
            em.getTransaction().commit();
            
            // 3. Show result
            response.setContentType("text/html");
            response.getWriter().println(
                "<h1>Delivery Note Created! ID: " + bl.getId() + "</h1>" +
                "<p>Client ID: " + bl.getClientId() + "</p>" +
                "<p>Date: " + bl.getDateCommande() + "</p>");
        } finally {
            em.close();
        }
    }
}}
