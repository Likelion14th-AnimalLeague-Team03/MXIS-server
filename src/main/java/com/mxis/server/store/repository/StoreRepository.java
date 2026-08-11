package com.mxis.server.store.repository;

import com.mxis.server.store.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByIsActiveTrueOrderByIdAsc();

    Optional<Store> findByIdAndIsActiveTrue(Long id);
}
