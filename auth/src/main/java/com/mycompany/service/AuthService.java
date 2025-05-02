package com.mycompany.service;

import com.mycompany.entity.Client;
import com.mycompany.repository.ClientRepository;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public String authenticate(String email, String password) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (passwordEncoder.matches(password, client.getPassword())) {
            // Update last_login on successful login
            client.setLastLogin(LocalDateTime.now());
            clientRepository.save(client);
            return JwtUtil.generateToken(client.getId(), "CLIENT");
        }
        throw new IllegalArgumentException("Invalid email or password");
    }

    public Client findClientById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
    }

    public Client createClient(Client client) {
        if (clientRepository.existsByEmail(client.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        // Hash password before saving
        client.setPassword(passwordEncoder.encode(client.getPassword()));
        return clientRepository.save(client);
    }

    public Client updateClient(Long id, Client updatedClient) {
        Client existingClient = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        // Check if email is changed and already exists
        if (!existingClient.getEmail().equals(updatedClient.getEmail()) &&
                clientRepository.existsByEmail(updatedClient.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Update fields
        existingClient.setEmail(updatedClient.getEmail());
        if (updatedClient.getPassword() != null && !updatedClient.getPassword().isBlank()) {
            existingClient.setPassword(passwordEncoder.encode(updatedClient.getPassword()));
        }
        existingClient.setPhone(updatedClient.getPhone());
        existingClient.setStatus(updatedClient.getStatus());

        return clientRepository.save(existingClient);
    }
}