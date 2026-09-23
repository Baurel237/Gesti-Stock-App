package com.stock.api.repository;

import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(String email);

    /** Vendeurs actifs (rôle SELLER) — pour la liste des vendeurs côté admin. */
    List<User> findByActiveTrueAndRolesContaining(Role role);

    /** Utilisateurs d'une entreprise (isolation V2). */
    Page<User> findByCompanyId(Long companyId, Pageable pageable);

    List<User> findByCompanyId(Long companyId);
}
