package com.mycompany.controller;

import com.mycompany.entity.Category;
import com.mycompany.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/category")
@Validated
public class CategoryController {
    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    @Autowired
    private CategoryService categoryService;

    private String getRoleFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            logger.warn("Authentication or details are null");
            return null;
        }
        Object details = authentication.getDetails();
        if (details instanceof String) {
            return (String) details;
        } else {
            logger.warn("Authentication details are not a String: {}", details.getClass().getName());
            return null;
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCategory(@Valid @RequestBody Category category) {
        logger.debug("Processing createCategory request");
        Map<String, Object> response = new HashMap<>();
        try {
            String role = getRoleFromAuthentication();
            if (!"ADMIN".equals(role)) {
                response.put("status", "ERROR");
                response.put("message", "Only admins can create categories");
                logger.warn("Non-admin role {} attempted to create category", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            Category createdCategory = categoryService.createCategory(category);
            response.put("status", "SUCCESS");
            response.put("category", createdCategory);
            logger.info("Created category with ID: {}", createdCategory.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to create category: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(@PathVariable Long id, @Valid @RequestBody Category updatedCategory) {
        logger.debug("Processing updateCategory request for ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            String role = getRoleFromAuthentication();
            if (!"ADMIN".equals(role)) {
                response.put("status", "ERROR");
                response.put("message", "Only admins can update categories");
                logger.warn("Non-admin role {} attempted to update category", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            Category category = categoryService.updateCategory(id, updatedCategory);
            response.put("status", "SUCCESS");
            response.put("category", category);
            logger.info("Updated category with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to update category: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long id) {
        logger.debug("Processing deleteCategory request for ID: {}", id);
        Map<String, Object> response = new HashMap<>();
        try {
            String role = getRoleFromAuthentication();
            if (!"ADMIN".equals(role)) {
                response.put("status", "ERROR");
                response.put("message", "Only admins can delete categories");
                logger.warn("Non-admin role {} attempted to delete category", role);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            categoryService.deleteCategory(id);
            response.put("status", "SUCCESS");
            response.put("message", "Category deleted successfully");
            logger.info("Deleted category with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            logger.warn("Failed to delete category: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listCategories() {
        logger.debug("Processing listCategories request");
        Map<String, Object> response = new HashMap<>();
        String role = getRoleFromAuthentication();

        if (role == null) {
            response.put("status", "ERROR");
            response.put("message", "Invalid authentication details");
            logger.warn("Invalid authentication details for listCategories");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (!"ADMIN".equals(role) && !"SUPPORT".equals(role) && !"CLIENT".equals(role)) {
            response.put("status", "ERROR");
            response.put("message", "Invalid role");
            logger.warn("Invalid role {} attempted to list categories", role);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        List<Category> categories = categoryService.getAllCategories();
        response.put("status", "SUCCESS");
        response.put("categories", categories);
        logger.info("Retrieved {} categories for role: {}", categories.size(), role);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}