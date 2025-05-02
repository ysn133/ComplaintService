package com.mycompany.service;

import com.mycompany.entity.Support;
import com.mycompany.repository.SupportRepository;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List; // Add this import
import java.util.Optional;

@Service
public class AuthService {
    @Autowired
    private SupportRepository supportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public String authenticate(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return null;
        }
        Optional<Support> supportOpt = supportRepository.findByEmail(email);
        if (supportOpt.isPresent() && passwordEncoder.matches(password, supportOpt.get().getPassword())) {
            Support support = supportOpt.get();
            return jwtUtil.generateToken(support.getId(), "SUPPORT");
        }
        return null;
    }

    public Support findSupportById(Long id) {
        return supportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Support user not found"));
    }

    public List<Support> findAllSupports() { // New method for retrieving all supports
        return supportRepository.findAll();
    }

    public Support createSupport(Support support) {
        if (supportRepository.existsByEmail(support.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        support.setPassword(passwordEncoder.encode(support.getPassword()));
        return supportRepository.save(support);
    }

    public Support updateSupport(Long id, Support updatedSupport) {
        Support existingSupport = supportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Support user not found"));

        // Only validate email if it's different
        if (!existingSupport.getEmail().equals(updatedSupport.getEmail())) {
            if (supportRepository.existsByEmail(updatedSupport.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
            }
            existingSupport.setEmail(updatedSupport.getEmail());
        }

        if (updatedSupport.getPassword() != null && !updatedSupport.getPassword().isBlank()) {
            existingSupport.setPassword(passwordEncoder.encode(updatedSupport.getPassword()));
        }
        existingSupport.setActive(updatedSupport.isActive());
        existingSupport.setCategoryId(updatedSupport.getCategoryId());
        existingSupport.setHireDate(updatedSupport.getHireDate());
        existingSupport.setWorkload(updatedSupport.getWorkload());

        return supportRepository.save(existingSupport);
    }

    public void deleteSupport(Long id) {
        if (!supportRepository.existsById(id)) {
            throw new IllegalArgumentException("Support user not found");
        }
        supportRepository.deleteById(id);
    }
}