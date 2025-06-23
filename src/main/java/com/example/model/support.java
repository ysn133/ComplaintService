/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.example.model;

/**
 *
 * @author HP
 */
public class support {
    private int id;
    private String email;
    private String password;
    private boolean active;
    private int categoryId;
    private String hireDate;
    private int workload;

    public void setId(int id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public void setHireDate(String hireDate) {
        this.hireDate = hireDate;
    }

    public void setWorkload(int workload) {
        this.workload = workload;
    }
    

    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public boolean isActive() {
        return active;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public String getHireDate() {
        return hireDate;
    }

    public int getWorkload() {
        return workload;
    }

}
