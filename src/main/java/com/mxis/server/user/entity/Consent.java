package com.mxis.server.user.entity;

import com.mxis.server.common.entity.BaseCreatedAtEntity;
import com.mxis.server.common.enums.ConsentAction;
import com.mxis.server.common.enums.ConsentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관 동의 Event Log. Immutable Entity - INSERT 후 수정하지 않는다.
 */
@Getter
@Entity
@Table(name = "consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(name = "terms_version", nullable = false, length = 30)
    private String termsVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsentAction action;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public Consent(User user, ConsentType consentType, String termsVersion, ConsentAction action) {
        this.user = user;
        this.consentType = consentType;
        this.termsVersion = termsVersion;
        this.action = action;
        this.occurredAt = LocalDateTime.now();
    }
}
