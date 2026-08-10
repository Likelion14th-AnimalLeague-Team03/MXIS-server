package com.mxis.server.user.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_uid", length = 255)
    private String providerUid;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String email, String passwordHash, AuthProvider provider, String providerUid, String name, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = provider;
        this.providerUid = providerUid;
        this.name = name;
        this.phone = phone;
    }

    public static User createLocal(String email, String encodedPassword, String name, String phone) {
        return new User(email, encodedPassword, AuthProvider.LOCAL, null, name, phone);
    }

    public static User createSocial(String email, AuthProvider provider, String providerUid, String name) {
        return new User(email, null, provider, providerUid, name, null);
    }

    public void updateProfile(String name, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
