package com.example.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin")
@NamedQueries({
    @NamedQuery(
        name  = "Admin.findByEmail",
        query = "SELECT a FROM Admin a WHERE a.email = :email"
    ),
    @NamedQuery(
        name  = "Admin.findByUsername",
        query = "SELECT a FROM Admin a WHERE a.username = :username"
    )
})
public class Admin {

    /* ───────────────────────────────────────────
       Primary key
       ─────────────────────────────────────────── */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ───────────────────────────────────────────
       Core login & identity info
       ─────────────────────────────────────────── */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    /* NEW FIELDS (recently added to API / DB) */
    @Column(nullable = false, length = 40)
    private String firstname;

    @Column(nullable = false, length = 40)
    private String lastname;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    /* ───────────────────────────────────────────
       Auditing
       ─────────────────────────────────────────── */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /* =====================================================
       Getters & Setters
       ===================================================== */
    public Long getId()            { return id; }
    public void setId(Long id)     { this.id = id; }

    public String getEmail()       { return email; }
    public void setEmail(String e) { this.email = e; }

    public String getPassword()    { return password; }
    public void setPassword(String p) { this.password = p; }

    public String getFirstname()   { return firstname; }
    public void setFirstname(String fn) { this.firstname = fn; }

    public String getLastname()    { return lastname; }
    public void setLastname(String ln) { this.lastname = ln; }

    public String getUsername()    { return username; }
    public void setUsername(String un) { this.username = un; }

    public LocalDateTime getLastLogin()          { return lastLogin; }
    public void setLastLogin(LocalDateTime date) { this.lastLogin = date; }
}
