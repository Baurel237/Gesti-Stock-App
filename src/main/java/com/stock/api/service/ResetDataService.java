package com.stock.api.service;

import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.SaleRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetDataService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleRepository saleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    static final String SUPERADMIN_EMAIL = "superadmin@mail.com";
    static final String SUPERADMIN_PASSWORD = "admin1232";

    @Transactional
    public ResetResult reset(boolean keepUsers, boolean recreateSuperadmin) {
        if (!keepUsers) {
            deleteAllUsers();
        }

        if (recreateSuperadmin) {
            createOrUpdateSuperadmin();
        }

        return new ResetResult(
                userRepository.count(),
                categoryRepository.count(),
                productRepository.count(),
                stockMovementRepository.count(),
                saleRepository.count(),
                userRepository.findByEmail(SUPERADMIN_EMAIL).isPresent()
        );
    }

    private void deleteAllUsers() {
        // Suppression explicite pour éviter de laisser des références orphelines
        // si d'autres tables ont des contraintes FK vers users.
        userRepository.findAll().forEach(userRepository::delete);
        log.info("Utilisateurs supprimés {}", userRepository.count());
    }

    private void createOrUpdateSuperadmin() {
        String encoded = passwordEncoder.encode(SUPERADMIN_PASSWORD);

        User superadmin = userRepository.findByEmail(SUPERADMIN_EMAIL)
                .orElseGet(() -> {
                    User created = User.builder()
                            .email(SUPERADMIN_EMAIL)
                            .password(encoded)
                            .firstName("Super")
                            .lastName("Admin")
                            .roles(new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN)))
                            .active(true)
                            .build();
                    userRepository.save(created);
                    auditService.record("CREATE", "User", created.getId(), created.getEmail(),
                            "Compte superadmin recréé par réinitialisation système");
                    log.info("Superadmin créé : {}", SUPERADMIN_EMAIL);
                    return created;
                });

        boolean passwordChanged = true;
        try {
            if (passwordEncoder.matches(SUPERADMIN_PASSWORD, superadmin.getPassword())) {
                passwordChanged = false;
            }
        } catch (Exception e) {
            passwordChanged = true;
        }

        superadmin.setPassword(encoded);
        superadmin.setRoles(new HashSet<>(Set.of(Role.SUPER_ADMIN, Role.ADMIN)));
        superadmin.setActive(true);
        userRepository.save(superadmin);

        if (passwordChanged) {
            auditService.record("UPDATE", "User", superadmin.getId(), superadmin.getEmail(),
                    "Mot de passe réinitialisé par réinitialisation système");
        }
    }

    public record ResetResult(long users, long categories, long products,
                              long movements, long sales, boolean superadminReady) {}
}
