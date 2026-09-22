package com.stock.api.repository;

import com.stock.api.entity.SaleEditRequest;
import com.stock.api.entity.SaleEditRequest.SaleEditRequestStatus;
import com.stock.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleEditRequestRepository extends JpaRepository<SaleEditRequest, Long> {

    boolean existsBySaleIdAndStatus(Long saleId, SaleEditRequestStatus status);

    List<SaleEditRequest> findByStatusOrderByRequestedAtAsc(SaleEditRequestStatus status);

    /** Purge des demandes émises par un utilisateur (suppression de son compte). */
    void deleteByRequestedBy(User requestedBy);
}