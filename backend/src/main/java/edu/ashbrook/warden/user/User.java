package edu.ashbrook.warden.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    private String email;

    private String passwordHash;

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_code")
    private Department department;

    private String rollNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id")
    private User mentor;

    private boolean active = true;

    private Instant createdAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Role role, Department department,
                String rollNo, User mentor, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.department = department;
        this.rollNo = rollNo;
        this.mentor = mentor;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public Department getDepartment() {
        return department;
    }

    public String getDepartmentCode() {
        return department == null ? null : department.getCode();
    }

    public String getRollNo() {
        return rollNo;
    }

    public User getMentor() {
        return mentor;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        this.active = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isHodOf(String departmentCode) {
        return role == Role.HOD && active && departmentCode != null && departmentCode.equals(getDepartmentCode());
    }
}
