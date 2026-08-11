package com.mxis.server.care.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진단 알고리즘 버전.
 * active_flag (Virtual Generated Column, DB에서만 존재)로 "is_active=true인 행 전역 1개" 제약을
 * 강제한다 - 엔티티에는 매핑하지 않는다. 활성 버전 교체가 필요해지면 기존 행을 먼저 비활성화하고
 * flush한 뒤 새 행을 활성화해야 한다.
 */
@Getter
@Entity
@Table(name = "care_algorithms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareAlgorithm extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rule_config", columnDefinition = "JSON")
    private String ruleConfig;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;
}
