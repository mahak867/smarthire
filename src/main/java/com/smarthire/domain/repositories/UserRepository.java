// ── SmartHire · src/main/java/com/smarthire/domain/repositories/UserRepository.java ──
package com.smarthire.domain.repositories;

import com.smarthire.domain.entities.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.active = false WHERE u.id = :id")
    void deactivate(@Param("id") UUID id);
    @org.springframework.data.jpa.repository.Query("""
        SELECT u FROM User u
        WHERE (:role IS NULL OR CAST(u.role AS string) = :role)
          AND (:search IS NULL
               OR LOWER(u.email) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(u.firstName) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(u.lastName) LIKE LOWER(CONCAT('%',:search,'%')))
        """)
    org.springframework.data.domain.Page<User> findFiltered(
        @org.springframework.data.repository.query.Param("role") String role,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);

}