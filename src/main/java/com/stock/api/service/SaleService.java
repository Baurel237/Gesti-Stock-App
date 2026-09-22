package com.stock.api.service;

import com.stock.api.dto.*;
import com.stock.api.entity.Product;
import com.stock.api.entity.Role;
import com.stock.api.entity.Sale;
import com.stock.api.entity.Sale.SaleStatus;
import com.stock.api.entity.SaleEditRequest;
import com.stock.api.entity.SaleEditRequest.SaleEditRequestStatus;
import com.stock.api.entity.SaleEditRequestItem;
import com.stock.api.entity.SaleItem;
import com.stock.api.entity.StockMovement;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SaleEditRequestRepository;
import com.stock.api.repository.SaleRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleEditRequestRepository saleEditRequestRepository;
    private final AuditService auditService;

    /** Rôles pouvant consulter les ventes de tous les vendeurs. */
    private static final Set<Role> SUPERVISOR_ROLES = Set.of(Role.SUPER_ADMIN, Role.ADMIN, Role.MANAGER);

    /** Rôles autorisés à annuler / modifier n'importe quelle vente (administration). */
    private static final Set<Role> CANCELLER_ROLES = Set.of(Role.SUPER_ADMIN, Role.ADMIN);

    /** Délai laissé au vendeur pour annuler / modifier lui-même sa vente (RG). */
    private static final java.time.Duration SELF_SERVICE_WINDOW = java.time.Duration.ofHours(4);

    public SaleResponse create(SaleRequest request, String sellerEmail) {
        // Validation des entrées
        validateSaleRequest(request);

        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessRuleException("Vendeur non trouvé : " + sellerEmail));
        Sale sale = Sale.builder()
                .reference("VTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .seller(seller)
                .status(SaleStatus.COMPLETED)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : Sale.PaymentMethod.CASH)
                .buyerName(trimOrNull(request.getBuyerName()))
                .buyerPhone(trimOrNull(request.getBuyerPhone()))
                .notes(request.getNotes())
                .build();

        for (SaleItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessRuleException("Produit non trouvé (ID : " + itemRequest.getProductId() + ")"));

            if (product.isDeleted()) {
                throw new BusinessRuleException(
                    "Produit supprimé : " + product.getName() + " (réf. " + product.getReference() + ").");
            }

            if (!product.canRemoveQuantity(itemRequest.getQuantity())) {
                throw new BusinessRuleException(
                    String.format("Stock insuffisant pour « %s » (réf. %s). "
                        + "Disponible : %d unités, demandé : %d unités.",
                        product.getName(), product.getReference(),
                        product.getQuantity(), itemRequest.getQuantity()));
            }

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(itemRequest.getQuantity() > 0
                            ? product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                            : BigDecimal.ZERO)
                    .build();

            sale.addItem(item);

            // Décrémenter le stock
            product.removeQuantity(itemRequest.getQuantity());
            productRepository.save(product);

            // Tracer la sortie de stock pour cette vente
            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.EXIT)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .reason("Vente " + sale.getReference())
                    .performedBy(seller)
                    .build());
        }

        Sale saved = saleRepository.save(sale);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<SaleResponse> findAll(Long sellerId, SaleStatus status, Pageable pageable, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        boolean supervisor = isSupervisor(requester);

        // Un vendeur ne peut consulter que ses propres ventes
        if (!supervisor && sellerId != null && !sellerId.equals(requester.getId())) {
            throw new AccessDeniedException("Vous ne pouvez consulter que vos propres ventes");
        }
        Long effectiveSellerId = supervisor ? sellerId : requester.getId();

        Page<Sale> sales;
        if (effectiveSellerId != null && status != null) {
            sales = saleRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(effectiveSellerId, status, pageable);
        } else if (effectiveSellerId != null) {
            sales = saleRepository.findBySellerIdOrderByCreatedAtDesc(effectiveSellerId, pageable);
        } else {
            sales = saleRepository.findAll(pageable);
        }
        return sales.map(this::toResponse);
    }

    /**
     * Ventes de l'utilisateur authentifié — scoping automatique, sans
     * vérification de rôle (utilisé par /api/sales/me).
     */
    @Transactional(readOnly = true)
    public Page<SaleResponse> findMySales(SaleStatus status, Pageable pageable, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        Page<Sale> sales;
        if (status != null) {
            sales = saleRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(requester.getId(), status, pageable);
        } else {
            sales = saleRepository.findBySellerIdOrderByCreatedAtDesc(requester.getId(), pageable);
        }
        return sales.map(this::toResponse);
    }

    /**
     * Annulation d'une vente :
     *  - le vendeur peut annuler lui-même sa vente pendant 4 heures après
     *    l'enregistrement (auto-annulation) ;
     *  - passé ce délai, il doit passer par une demande d'annulation
     *    (requestCancellation) traitée par l'administration ;
     *  - SUPER_ADMIN / ADMIN peuvent annuler n'importe quelle vente COMPLETED.
     *
     * L'annulation remet les quantités en stock et trace un mouvement ENTRY
     * « Annulation vente ... ».
     */
    public SaleResponse cancel(Long id, String requesterEmail) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vente non trouvée avec l'id : " + id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé : " + requesterEmail));

        boolean supervisor = isSupervisor(requester);
        boolean canceller = isCanceller(requester);

        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException("Seule une vente effectuée peut être annulée");
        }

        if (!canceller) {
            if (!sale.getSeller().getId().equals(requester.getId())) {
                throw new AccessDeniedException("Vous ne pouvez annuler que vos propres ventes");
            }
            if (sale.getCreatedAt().isBefore(LocalDateTime.now().minus(SELF_SERVICE_WINDOW))) {
                throw new BusinessRuleException(
                        "Le délai d'auto-annulation (" + SELF_SERVICE_WINDOW.toHours()
                                + " heures) est dépassé. Demandez l'annulation à l'administration.");
            }
        }

        restockSale(sale);
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancellationRequested(false);
        Sale saved = saleRepository.save(sale);

        auditService.record("CANCEL", "Sale", saved.getId(), saved.getReference(),
                String.format("Annulation par %s — total=%s", requesterEmail, saved.getTotalAmount()));

        return toResponse(saved);
    }

    /**
     * Demande d'annulation émise par le vendeur après le délai de 4 heures.
     * L'administration la voit dans la liste des ventes (badge) et peut
     * l'approuver via cancel().
     */
    public SaleResponse requestCancellation(Long id, String requesterEmail, String reason) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vente non trouvée avec l'id : " + id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé : " + requesterEmail));

        if (!sale.getSeller().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Vous ne pouvez demander l'annulation que de vos propres ventes");
        }
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException("Seule une vente effectuée peut faire l'objet d'une demande d'annulation");
        }
        if (sale.isCancellationRequested()) {
            throw new BusinessRuleException("Une demande d'annulation est déjà en attente pour cette vente");
        }
        // Faisabilité : pendant les 4 premières heures le vendeur s'annule lui-même
        if (sale.getCreatedAt().isAfter(LocalDateTime.now().minus(SELF_SERVICE_WINDOW))) {
            throw new BusinessRuleException(
                    "La vente est encore dans le délai d'auto-annulation (" + SELF_SERVICE_WINDOW.toHours()
                            + " h) : annulez-la directement.");
        }

        sale.setCancellationRequested(true);
        sale.setCancellationReason(trimOrNull(reason));
        sale.setCancellationRequestedAt(LocalDateTime.now());
        Sale saved = saleRepository.save(sale);

        auditService.record("REQUEST_CANCEL", "Sale", saved.getId(), saved.getReference(),
                String.format("Demande d'annulation par %s — motif : %s",
                        requesterEmail, saved.getCancellationReason() != null ? saved.getCancellationReason() : "non précisé"));

        return toResponse(saved);
    }

    /**
     * Ventes avec demande d'annulation en attente — pour l'administration.
     */
    @Transactional(readOnly = true)
    public List<SaleResponse> getPendingCancellations() {
        return saleRepository.findByCancellationRequestedTrueOrderByCancellationRequestedAtAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Modification d'une vente :
     *  - le vendeur peut modifier lui-même sa vente pendant 4 heures après
     *    l'enregistrement (auto-modification) ;
     *  - passé ce délai, il doit passer par une demande de modification
     *    (requestSaleEdit) traitée par l'administration ;
     *  - SUPER_ADMIN / ADMIN peuvent modifier n'importe quelle vente COMPLETED.
     *
     * La modification remet en stock les anciens articles, sort de stock les
     * nouveaux (mouvements ENTRY / EXIT « Modification vente ... ») et recalcule
     * le total.
     */
    public SaleResponse editSale(Long id, SaleRequest request, String requesterEmail) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vente non trouvée avec l'id : " + id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé : " + requesterEmail));

        boolean canceller = isCanceller(requester);

        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException("Seule une vente effectuée peut être modifiée");
        }

        if (!canceller) {
            if (!sale.getSeller().getId().equals(requester.getId())) {
                throw new AccessDeniedException("Vous ne pouvez modifier que vos propres ventes");
            }
            if (sale.getCreatedAt().isBefore(LocalDateTime.now().minus(SELF_SERVICE_WINDOW))) {
                throw new BusinessRuleException(
                        "Le délai de modification (" + SELF_SERVICE_WINDOW.toHours()
                                + " heures) est dépassé. Demandez la modification à l'administration.");
            }
        }

        validateSaleRequest(request);
        applySaleEdit(sale, request, requester);

        auditService.record("EDIT", "Sale", sale.getId(), sale.getReference(),
                String.format("Modification par %s — nouveau total=%s", requesterEmail, sale.getTotalAmount()));

        return toResponse(sale);
    }

    /**
     * Demande de modification émise par le vendeur après le délai de 4 heures.
     * L'administration la voit dans la page « Demandes » et peut l'approuver
     * (approveEditRequest) ou la refuser (rejectEditRequest).
     */
    public SaleEditRequestResponse requestSaleEdit(Long id, SaleEditRequestData data, String requesterEmail) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vente non trouvée avec l'id : " + id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé : " + requesterEmail));

        if (!sale.getSeller().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Vous ne pouvez demander la modification que de vos propres ventes");
        }
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException("Seule une vente effectuée peut faire l'objet d'une demande de modification");
        }
        if (sale.isCancellationRequested()) {
            throw new BusinessRuleException("Une demande d'annulation est déjà en attente pour cette vente");
        }
        if (saleEditRequestRepository.existsBySaleIdAndStatus(id, SaleEditRequestStatus.PENDING)) {
            throw new BusinessRuleException("Une demande de modification est déjà en attente pour cette vente");
        }
        // Faisabilité : pendant les 4 premières heures le vendeur modifie lui-même
        if (sale.getCreatedAt().isAfter(LocalDateTime.now().minus(SELF_SERVICE_WINDOW))) {
            throw new BusinessRuleException(
                    "La vente est encore dans le délai de modification (" + SELF_SERVICE_WINDOW.toHours()
                            + " h) : modifiez-la directement.");
        }
        if (data == null) {
            throw new BusinessRuleException("La requête de modification est nulle");
        }
        if (data.getItems() == null || data.getItems().isEmpty()) {
            throw new BusinessRuleException("La modification doit contenir au moins un article");
        }
        if (data.getItems().size() > 100) {
            throw new BusinessRuleException("La modification ne peut pas contenir plus de 100 articles");
        }

        SaleEditRequest request = SaleEditRequest.builder()
                .sale(sale)
                .status(SaleEditRequestStatus.PENDING)
                .reason(trimOrNull(data.getReason()))
                .paymentMethod(data.getPaymentMethod())
                .buyerName(trimOrNull(data.getBuyerName()))
                .buyerPhone(trimOrNull(data.getBuyerPhone()))
                .notes(data.getNotes())
                .requestedBy(requester)
                .requestedAt(LocalDateTime.now())
                .build();

        for (SaleItemRequest itemRequest : data.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessRuleException("Produit non trouvé (ID : " + itemRequest.getProductId() + ")"));
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new BusinessRuleException("La quantité doit être supérieure à 0");
            }
            request.addItem(SaleEditRequestItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .build());
        }

        SaleEditRequest saved = saleEditRequestRepository.save(request);
        auditService.record("REQUEST_EDIT", "Sale", sale.getId(), sale.getReference(),
                String.format("Demande de modification par %s — motif : %s",
                        requesterEmail, saved.getReason() != null ? saved.getReason() : "non précisé"));

        return toEditRequestResponse(saved);
    }

    /**
     * Demandes de modification en attente — pour l'administration.
     */
    @Transactional(readOnly = true)
    public List<SaleEditRequestResponse> getPendingEditRequests() {
        return saleEditRequestRepository.findByStatusOrderByRequestedAtAsc(SaleEditRequestStatus.PENDING)
                .stream()
                .map(this::toEditRequestResponse)
                .toList();
    }

    /**
     * Approbation d'une demande de modification par l'administration :
     * applique la modification (stock + lignes + métadonnées) et marque la
     * demande APPROVED.
     */
    public SaleResponse approveEditRequest(Long requestId, String reviewerEmail) {
        SaleEditRequest request = saleEditRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException("Demande non trouvée"));
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        if (request.getStatus() != SaleEditRequestStatus.PENDING) {
            throw new BusinessRuleException("Cette demande a déjà été traitée");
        }
        Sale sale = request.getSale();
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessRuleException("La vente n'est plus modifiable (elle est annulée)");
        }

        SaleRequest saleRequest = SaleRequest.builder()
                .items(request.getItems().stream()
                        .map(i -> SaleItemRequest.builder()
                                .productId(i.getProduct().getId())
                                .quantity(i.getQuantity())
                                .build())
                        .toList())
                .notes(request.getNotes())
                .paymentMethod(request.getPaymentMethod())
                .buyerName(request.getBuyerName())
                .buyerPhone(request.getBuyerPhone())
                .build();

        applySaleEdit(sale, saleRequest, reviewer);
        sale.setCancellationRequested(false);

        request.setStatus(SaleEditRequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        saleEditRequestRepository.save(request);

        auditService.record("APPROVE_EDIT_REQUEST", "Sale", sale.getId(), sale.getReference(),
                String.format("Demande de modification approuvée par %s", reviewerEmail));

        return toResponse(sale);
    }

    /**
     * Refus d'une demande de modification par l'administration.
     */
    public SaleEditRequestResponse rejectEditRequest(Long requestId, String reviewerEmail) {
        SaleEditRequest request = saleEditRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException("Demande non trouvée"));
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        if (request.getStatus() != SaleEditRequestStatus.PENDING) {
            throw new BusinessRuleException("Cette demande a déjà été traitée");
        }

        request.setStatus(SaleEditRequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        SaleEditRequest saved = saleEditRequestRepository.save(request);

        auditService.record("REJECT_EDIT_REQUEST", "Sale", saved.getSale().getId(), saved.getSale().getReference(),
                String.format("Demande de modification refusée par %s — motif : %s",
                        reviewerEmail, saved.getReason() != null ? saved.getReason() : "non précisé"));

        return toEditRequestResponse(saved);
    }

    /**
     * Refus d'une demande d'annulation par l'administration : la demande est
     * levée et la vente reste effectuée.
     */
    public void rejectCancellation(Long saleId, String reviewerEmail) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new BusinessRuleException("Vente non trouvée"));

        if (!sale.isCancellationRequested()) {
            throw new BusinessRuleException("Aucune demande d'annulation en attente pour cette vente");
        }

        sale.setCancellationRequested(false);
        saleRepository.save(sale);

        auditService.record("REJECT_CANCEL", "Sale", sale.getId(), sale.getReference(),
                String.format("Demande d'annulation refusée par %s — motif : %s",
                        reviewerEmail, sale.getCancellationReason() != null ? sale.getCancellationReason() : "non précisé"));
    }

    /** Remet en stock les quantités vendues et trace les mouvements ENTRY. */
    private void restockSale(Sale sale) {
        User performer = sale.getSeller();
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.addQuantity(item.getQuantity());
            productRepository.save(product);
            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.ENTRY)
                    .product(product)
                    .quantity(item.getQuantity())
                    .reason("Annulation vente " + sale.getReference())
                    .performedBy(performer)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public SaleResponse findById(Long id, String requesterEmail) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vente non trouvée avec l'id : " + id));
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé : " + requesterEmail));

        if (!isSupervisor(requester) && !sale.getSeller().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Vous ne pouvez consulter que vos propres ventes");
        }
        return toResponse(sale);
    }

    @Transactional(readOnly = true)
    public SellerDashboardStats getSellerStats(Long sellerId, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        if (!isSupervisor(requester) && !sellerId.equals(requester.getId())) {
            throw new AccessDeniedException("Vous ne pouvez consulter que vos propres statistiques");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = now.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime monthEnd = YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX);

        Integer todayCount = saleRepository.countBySellerIdAndDateRange(sellerId, todayStart, todayEnd);
        BigDecimal todayTotal = saleRepository.sumTotalBySellerIdAndDateRange(sellerId, todayStart, todayEnd);
        Integer monthCount = saleRepository.countBySellerIdAndDateRange(sellerId, monthStart, monthEnd);
        BigDecimal monthTotal = saleRepository.sumTotalBySellerIdAndDateRange(sellerId, monthStart, monthEnd);

        List<Sale> recentSales = saleRepository.findTop10BySellerIdAndStatusOrderByCreatedAtDesc(sellerId, SaleStatus.COMPLETED);

        // Build last 7 days data
        List<SellerDashboardStats.DailySalesData> last7Days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.atTime(LocalTime.MAX);
            Integer dayCount = saleRepository.countBySellerIdAndDateRange(sellerId, dayStart, dayEnd);
            BigDecimal dayTotal = saleRepository.sumTotalBySellerIdAndDateRange(sellerId, dayStart, dayEnd);
            last7Days.add(SellerDashboardStats.DailySalesData.builder()
                    .date(day.format(DateTimeFormatter.ofPattern("dd/MM")))
                    .count(dayCount)
                    .total(dayTotal)
                    .build());
        }

        return SellerDashboardStats.builder()
                .todaySalesCount(todayCount)
                .todaySalesTotal(todayTotal)
                .monthSalesCount(monthCount)
                .monthSalesTotal(monthTotal)
                .last7Days(last7Days)
                .recentSales(recentSales.stream().map(this::toResponse).toList())
                .build();
    }

    /**
     * Ligne temporelle en direct de l'activité des ventes — utilisée par
     * l'admin pour suivre les ventes en temps réel.
     */
    @Transactional(readOnly = true)
    public List<SaleResponse> getLiveSalesFeed(int limit, String requesterEmail) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));

        if (!isSupervisor(requester)) {
            throw new AccessDeniedException("Réservé aux administrateurs");
        }

        return saleRepository.findTopNByStatusOrderByCreatedAtDesc(SaleStatus.COMPLETED, Pageable.ofSize(limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerStatsResponse> getAdminSellerStats(LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = saleRepository.findSellerStatsByDateRange(start, end);
        return results.stream().map(row -> SellerStatsResponse.builder()
                .sellerId((Long) row[0])
                .sellerEmail((String) row[1])
                .sellerFirstName((String) row[2])
                .sellerLastName((String) row[3])
                .totalProductsSold(((Number) row[4]).intValue())
                .totalSalesAmount((BigDecimal) row[5])
                .totalSalesCount(((Number) row[6]).intValue())
                .build()
        ).toList();
    }

    /**
     * Liste des vendeurs (users actifs ayant le rôle SELLER) avec leurs
     * statistiques sur la période donnée — y compris les vendeurs sans vente.
     */
    @Transactional(readOnly = true)
    public List<SellerStatsResponse> getSellersWithStats(LocalDateTime start, LocalDateTime end) {
        List<User> sellers = userRepository.findByActiveTrueAndRolesContaining(Role.SELLER);
        List<SellerStatsResponse> stats = getAdminSellerStats(start, end);

        Map<Long, SellerStatsResponse> statsById = stats.stream()
                .collect(Collectors.toMap(SellerStatsResponse::getSellerId, s -> s));

        List<SellerStatsResponse> result = new ArrayList<>();
        for (User seller : sellers) {
            SellerStatsResponse s = statsById.get(seller.getId());
            if (s == null) {
                s = SellerStatsResponse.builder()
                        .sellerId(seller.getId())
                        .sellerEmail(seller.getEmail())
                        .sellerFirstName(seller.getFirstName())
                        .sellerLastName(seller.getLastName())
                        .totalProductsSold(0)
                        .totalSalesAmount(BigDecimal.ZERO)
                        .totalSalesCount(0)
                        .build();
            }
            result.add(s);
        }
        // Trier par chiffre d'affaires décroissant
        result.sort((a, b) -> b.getTotalSalesAmount().compareTo(a.getTotalSalesAmount()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<SellerDashboardStats.DailySalesData> getDailySalesAggregation(LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = saleRepository.findDailySalesAggregation(start, end);
        return results.stream().map(row -> SellerDashboardStats.DailySalesData.builder()
                .date(row[0] != null ? row[0].toString() : "")
                .count(((Number) row[1]).intValue())
                .total((BigDecimal) row[2])
                .build()
        ).toList();
    }

    private boolean isSupervisor(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(SUPERVISOR_ROLES::contains);
    }

    private boolean isCanceller(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(CANCELLER_ROLES::contains);
    }

    /** Normalise un champ facultatif : blanc → null. */
    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Valide une requête de vente (articles non vides, identifiants et
     * quantités corrects). Utilisé par create() et editSale().
     */
    private void validateSaleRequest(SaleRequest request) {
        if (request == null) {
            throw new BusinessRuleException("La requête de vente est nulle");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La vente doit contenir au moins un article");
        }
        if (request.getItems().size() > 100) {
            throw new BusinessRuleException("La vente ne peut pas contenir plus de 100 articles");
        }
        for (SaleItemRequest itemRequest : request.getItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getProductId() <= 0) {
                throw new BusinessRuleException("L'ID du produit est invalide");
            }
            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new BusinessRuleException("La quantité doit être supérieure à 0");
            }
        }
    }

    /**
     * Applique une modification sur une vente effectuée :
     *  1. contrôle du stock pour tous les nouveaux articles (AVANT toute mutation) ;
     *  2. remise en stock des anciens articles (mouvements ENTRY) ;
     *  3. suppression des anciennes lignes ;
     *  4. décrémentation des nouveaux articles (mouvements EXIT) ;
     *  5. mise à jour des métadonnées (paiement, acheteur, notes).
     */
    private void applySaleEdit(Sale sale, SaleRequest request, User performer) {
        // 1. Vérification de disponibilité du stock AVANT toute mutation
        for (SaleItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessRuleException("Produit non trouvé (ID : " + itemRequest.getProductId() + ")"));

            if (product.isDeleted()) {
                throw new BusinessRuleException(
                    "Produit supprimé : " + product.getName() + " (réf. " + product.getReference() + ").");
            }

            if (!product.canRemoveQuantity(itemRequest.getQuantity())) {
                throw new BusinessRuleException(
                    String.format("Stock insuffisant pour « %s » (réf. %s). "
                        + "Disponible : %d unités, demandé : %d unités.",
                        product.getName(), product.getReference(),
                        product.getQuantity(), itemRequest.getQuantity()));
            }
        }

        // 2. Remise en stock des anciens articles
        for (SaleItem oldItem : sale.getItems()) {
            Product product = oldItem.getProduct();
            product.addQuantity(oldItem.getQuantity());
            productRepository.save(product);
            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.ENTRY)
                    .product(product)
                    .quantity(oldItem.getQuantity())
                    .reason("Modification vente " + sale.getReference())
                    .performedBy(performer)
                    .build());
        }

        // 3. Suppression des anciennes lignes (orphanRemoval)
        sale.getItems().clear();

        // 4. Application des nouveaux articles
        for (SaleItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new BusinessRuleException("Produit non trouvé (ID : " + itemRequest.getProductId() + ")"));

            SaleItem item = SaleItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())))
                    .build();

            sale.addItem(item);

            product.removeQuantity(itemRequest.getQuantity());
            productRepository.save(product);

            stockMovementRepository.save(StockMovement.builder()
                    .type(StockMovement.MovementType.EXIT)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .reason("Modification vente " + sale.getReference())
                    .performedBy(performer)
                    .build());
        }

        // 5. Métadonnées
        sale.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : Sale.PaymentMethod.CASH);
        sale.setBuyerName(trimOrNull(request.getBuyerName()));
        sale.setBuyerPhone(trimOrNull(request.getBuyerPhone()));
        sale.setNotes(request.getNotes());
        saleRepository.save(sale);
    }

    private SaleEditRequestResponse toEditRequestResponse(SaleEditRequest request) {
        List<SaleEditRequestItemResponse> items = request.getItems().stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal unitPrice = product.getPrice();
                    return SaleEditRequestItemResponse.builder()
                            .id(item.getId())
                            .productId(product.getId())
                            .productName(product.getName())
                            .productReference(product.getReference())
                            .quantity(item.getQuantity())
                            .unitPrice(unitPrice)
                            .subtotal(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
                            .build();
                })
                .toList();

        BigDecimal total = items.stream()
                .map(SaleEditRequestItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SaleEditRequestResponse.builder()
                .id(request.getId())
                .saleId(request.getSale().getId())
                .saleReference(request.getSale().getReference())
                .status(request.getStatus())
                .reason(request.getReason())
                .paymentMethod(request.getPaymentMethod())
                .buyerName(request.getBuyerName())
                .buyerPhone(request.getBuyerPhone())
                .notes(request.getNotes())
                .items(items)
                .totalAmount(total)
                .requestedById(request.getRequestedBy().getId())
                .requestedByName(request.getRequestedBy().getFirstName() + " " + request.getRequestedBy().getLastName())
                .requestedAt(request.getRequestedAt())
                .reviewedById(request.getReviewedBy() != null ? request.getReviewedBy().getId() : null)
                .reviewedByName(request.getReviewedBy() != null
                        ? request.getReviewedBy().getFirstName() + " " + request.getReviewedBy().getLastName()
                        : null)
                .reviewedAt(request.getReviewedAt())
                .build();
    }

    private SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> items = sale.getItems().stream()
                .map(item -> SaleItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productReference(item.getProduct().getReference())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build()
                ).toList();

        return SaleResponse.builder()
                .id(sale.getId())
                .reference(sale.getReference())
                .status(sale.getStatus())
                .paymentMethod(sale.getPaymentMethod())
                .totalAmount(sale.getTotalAmount())
                .totalItems(sale.getTotalItems())
                .items(items)
                .sellerId(sale.getSeller().getId())
                .sellerEmail(sale.getSeller().getEmail())
                .sellerName(sale.getSeller().getFirstName() + " " + sale.getSeller().getLastName())
                .buyerName(sale.getBuyerName())
                .buyerPhone(sale.getBuyerPhone())
                .notes(sale.getNotes())
                .cancellationRequested(sale.isCancellationRequested())
                .cancellationReason(sale.getCancellationReason())
                .cancellationRequestedAt(sale.getCancellationRequestedAt())
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}
