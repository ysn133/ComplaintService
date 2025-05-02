package com.mycompany.service;

import com.mycompany.entity.Admin;
import com.mycompany.repository.AdminRepository;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public String authenticate(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return null;
        }
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
        if (adminOpt.isPresent() && passwordEncoder.matches(password, adminOpt.get().getPassword())) {
            Admin admin = adminOpt.get();
            return jwtUtil.generateToken(admin.getId(), "ADMIN");
        }
        return null;
    }

    public Admin findAdminById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
    }

    public Admin createAdmin(Admin admin) {
        if (adminRepository.existsByEmail(admin.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        return adminRepository.save(admin);
    }

    public Admin updateAdmin(Long id, Admin updatedAdmin) {
        Admin existingAdmin = adminRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        if (!existingAdmin.getEmail().equals(updatedAdmin.getEmail()) &&
                adminRepository.existsByEmail(updatedAdmin.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        existingAdmin.setEmail(updatedAdmin.getEmail());
        if (updatedAdmin.getPassword() != null && !updatedAdmin.getPassword().isBlank()) {
            existingAdmin.setPassword(passwordEncoder.encode(updatedAdmin.getPassword()));
        }

        return adminRepository.save(existingAdmin);
    }
}