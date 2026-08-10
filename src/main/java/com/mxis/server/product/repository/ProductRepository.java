package com.mxis.server.product.repository;

import com.mxis.server.product.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Product> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.user.id = :userId AND p.deletedAt IS NULL ORDER BY p.registeredAt DESC")
    List<Product> findAllActiveByUserId(@Param("userId") Long userId);
}
