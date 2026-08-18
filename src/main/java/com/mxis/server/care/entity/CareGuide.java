package com.mxis.server.care.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.product.entity.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "care_guides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareGuide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false, length = 50)
    private String materialId;

    @Column(name = "material_subtype", length = 50)
    private String materialSubtype;

    @Column(name = "care_type", length = 50)
    private String careType;

    @Column(name = "guide_image_url", length = 500)
    private String guideImageUrl;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "json")
    private List<String> steps = List.of();

    @Column(columnDefinition = "TEXT")
    private String tip;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
