package com.mxis.server.user.repository;

import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE u.provider = :provider AND u.providerUid = :providerUid AND u.deletedAt IS NULL")
    Optional<User> findActiveByProviderAndProviderUid(
            @Param("provider") AuthProvider provider, @Param("providerUid") String providerUid);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
