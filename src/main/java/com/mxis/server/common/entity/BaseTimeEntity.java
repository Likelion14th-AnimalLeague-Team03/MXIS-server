package com.mxis.server.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Mutable Entity 공통 필드 (created_at, updated_at).
 *
 * ERD 원안에서는 updated_at을 PostgreSQL DB Trigger로 자동 갱신하도록 설계했으나,
 * MariaDB + 애플리케이션 계층 이식성을 고려해 Spring Data JPA Auditing으로 대체한다.
 * (JpaAuditingConfig 에서 @EnableJpaAuditing 활성화)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
