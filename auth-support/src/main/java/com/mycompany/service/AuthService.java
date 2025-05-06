package com.mycompany.service;

import com.mycompany.entity.Support;
import com.mycompany.repository.SupportRepository;
import com.mycompany.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
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

    public List<Support> findAllSupports() {
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

    public Long getAvailableSupportId(Long categoryId) {
        List<Support> supports = supportRepository.findByCategoryIdAndActiveTrueOrderByWorkloadAsc(categoryId);
        if (supports.isEmpty()) {
            throw new IllegalArgumentException("No available support users for category ID: " + categoryId);
        }
        Support selectedSupport = supports.get(0);
        return selectedSupport.getId();
    }

    public void updateSupportWorkload(Long supportTeamId, Long activeTickets) {
        Support support = supportRepository.findById(supportTeamId)
                .orElseThrow(() -> new IllegalArgumentException("Support team not found with ID: " + supportTeamId));
        support.setWorkload(activeTickets.intValue());
        supportRepository.save(support);
    }
}