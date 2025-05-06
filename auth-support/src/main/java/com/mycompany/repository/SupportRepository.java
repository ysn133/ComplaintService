package com.mycompany.repository;

import com.mycompany.entity.Support;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportRepository extends JpaRepository<Support, Long> {
    Optional<Support> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Support> findByCategoryIdAndActiveTrueOrderByWorkloadAsc(Long categoryId);
}