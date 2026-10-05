package com.stock.api.repository;

import com.stock.api.entity.WarehouseStock;
import com.stock.api.entity.Warehouse;
import com.stock.api.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {

    Optional<WarehouseStock> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<WarehouseStock> findByWarehouseIdOrderByProductId(Long warehouseId);

    /**
     * Lignes de stock d'un entrepôt hors produits soft-supprimés (RG-04) :
     * leurs lignes peuvent subsister en base après la suppression logique
     * du produit — elles ne doivent ni apparaître dans le contenu, ni
     * compter dans le total, ni empêcher la suppression d'un entrepôt vide.
     */
    List<WarehouseStock> findByWarehouseIdAndProduct_DeletedFalseOrderByProductId(Long warehouseId);

    List<WarehouseStock> findByProductId(Long productId);

    void deleteByWarehouseId(Long warehouseId);
}
