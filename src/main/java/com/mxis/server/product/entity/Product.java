package com.mxis.server.product.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "dpp_code", unique = true, length = 100)
    private String dppCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "model_code", length = 50)
    private String modelCode;

    @Column(name = "material_id", nullable = false, length = 50)
    private String materialId;

    @Column(name = "material_display_name", length = 100)
    private String materialDisplayName;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "material_subtypes", columnDefinition = "json")
    private List<String> materialSubtypes;

    @Column(length = 30)
    private String color;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Product(User user, String dppCode, String productName, String modelCode,
                    String materialId, String materialDisplayName, List<String> materialSubtypes, String color,
                    String productImageUrl, LocalDate purchasedAt) {
        this.user = user;
        this.dppCode = dppCode;
        this.productName = productName;
        this.modelCode = modelCode;
        this.materialId = materialId;
        this.materialDisplayName = materialDisplayName;
        this.materialSubtypes = materialSubtypes == null ? List.of() : List.copyOf(materialSubtypes);
        this.color = color;
        this.productImageUrl = productImageUrl;
        this.purchasedAt = purchasedAt;
        this.registeredAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
