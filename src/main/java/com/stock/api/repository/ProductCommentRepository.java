package com.stock.api.repository;

import com.stock.api.entity.ProductComment;
import com.stock.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCommentRepository extends JpaRepository<ProductComment, Long> {

    /** Derniers commentaires d'un produit (affichage sous la carte produit). */
    List<ProductComment> findTop5ByProductIdOrderByCreatedAtDesc(Long productId);

    /** Journal complet pour l'admin. */
    Page<ProductComment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Purge des commentaires d'un utilisateur (suppression de son compte). */
    void deleteByAuthor(User author);
}
