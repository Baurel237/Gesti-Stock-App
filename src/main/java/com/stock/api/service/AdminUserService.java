package com.stock.api.service;

import com.stock.api.dto.CreateUserRequest;
import com.stock.api.dto.ResetPasswordResponse;
import com.stock.api.dto.UpdateUserRequest;
import com.stock.api.dto.UserResponse;
import com.stock.api.entity.Role;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.ProductCommentRepository;
import com.stock.api.repository.SaleEditRequestRepository;
import com.stock.api.repository.SaleRepository;
import com.stock.api.repository.StockMovementRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.service.AuditService;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SaleRepository saleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SaleEditRequestRepository saleEditRequestRepository;
    private final ProductCommentRepository productCommentRepository;
    private final com.stock.api.repository.CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        // V2 : un ADMIN ne gère que les comptes de son entreprise ;
        // le SUPER_ADMIN (scope plateforme) voit tous les comptes.
        Long companyId = TenantContext.getCompanyId();
        Page<User> users = companyId == null
                ? userRepository.findAll(pageable)
                : userRepository.findByCompanyId(companyId, pageable);
        return users.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec l'id: " + id));
        TenantGuard.assertSameCompany(user.getCompany() != null ? user.getCompany().getId() : null);
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        Set<Role> roles = parseRoles(request.getRoles());
        if (roles.isEmpty()) {
            roles = new HashSet<>(Set.of(Role.VIEWER));
        }
        assertCanAssignRoles(roles);

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException(
                    "Un utilisateur avec l'email « " + request.getEmail() + " » existe déjà. "
                            + "Choisissez un autre email ou réactivez le compte existant.");
        }

        // V2 : le compte créé est rattaché à l'entreprise de l'admin (sauf si
        // le SUPER_ADMIN crée explicitement un compte plateforme sans entreprise).
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(roles)
                .active(true)
                .build();
        if (CurrentUser.isSuperAdmin() && request.getCompanyId() != null) {
            user.setCompany(companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Entreprise non trouvée avec l'id: " + request.getCompanyId())));
        } else if (!CurrentUser.isPlatformScope()) {
            user.setCompany(companyRepository.findById(TenantGuard.requireCompanyId())
                    .orElseThrow(() -> new IllegalStateException("Entreprise introuvable")));
        }

        user = userRepository.save(user);
        auditService.record("CREATE", "User", user.getId(), user.getEmail(),
                "Compte créé avec rôles : " + roleNames(roles));
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec l'id: " + id));

        // Interdire à un utilisateur de désactiver son propre compte
        // (il se couperait l'accès immédiatement et sans retour simple).
        if (Boolean.FALSE.equals(request.getActive()) && isSelf(user)) {
            throw new BusinessRuleException(
                    "Vous ne pouvez pas désactiver votre propre compte. "
                            + "Demandez à un autre administrateur de le faire.");
        }

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getActive() != null) user.setActive(request.getActive());

        String rolesChange = null;
        if (request.getRoles() != null) {
            Set<Role> roles = parseRoles(request.getRoles());
            if (roles.isEmpty()) {
                throw new BusinessRuleException(
                        "Un utilisateur doit avoir au moins un rôle. Rôles valides : "
                                + validRoleNames());
            }
            assertCanAssignRoles(roles);
            assertCanModifyTarget(user);
            rolesChange = roleNames(roles);
            user.setRoles(roles);
        }

        if (request.getActive() != null && !request.getActive()) {
            assertCanModifyTarget(user);
        }

        user = userRepository.save(user);
        auditService.record("UPDATE", "User", user.getId(), user.getEmail(),
                rolesChange != null ? "Rôles modifiés : " + rolesChange : "Informations mises à jour");
        return toResponse(user);
    }

    /**
     * Réinitialise le mot de passe d'un utilisateur (SUPER_ADMIN / ADMIN).
     * Si newPassword est null ou blanc, un mot de passe temporaire lisible est
     * généré (12 caractères, sans caractères ambigus). Le mot de passe est
     * renvoyé en clair une seule fois pour être communiqué à l'utilisateur.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec l'id: " + id));

        assertCanModifyTarget(user);

        boolean generated = newPassword == null || newPassword.isBlank();
        String password = generated ? generateTemporaryPassword() : newPassword.trim();

        user.setPassword(passwordEncoder.encode(password));
        user = userRepository.save(user);

        auditService.record("RESET_PASSWORD", "User", user.getId(), user.getEmail(),
                "Mot de passe réinitialisé par l'administration"
                        + (generated ? " (temporaire généré)" : ""));

        return ResetPasswordResponse.builder()
                .email(user.getEmail())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .temporaryPassword(password)
                .generated(generated)
                .build();
    }

    /**
     * Génère un mot de passe temporaire lisible : groupes de 4 caractères
     * (majuscules + chiffres), préfixés de "Tmp-" pour inciter au changement.
     */
    private String generateTemporaryPassword() {
        // Caractères non ambigus (sans O/0, I/l/1, S/5...)
        String upper = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        String all = upper + digits;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder("Tmp-");
        for (int i = 0; i < 12; i++) {
            String pool = (i < 4) ? upper : (i % 2 == 0 ? digits : all);
            sb.append(pool.charAt(random.nextInt(pool.length())));
        }
        return sb.toString();
    }

    /**
     * Suppression d'un utilisateur (typiquement un vendeur) :
     *  1. purge de ses demandes de modification de vente ;
     *  2. purge de ses commentaires produits ;
     *  3. purge de ses mouvements de stock ;
     *  4. purge de ses ventes (lignes et demandes d'annulation incluses via

     *     cascade) ;
     *  5. suppression du compte.
     *
     * Les statistiques agrégées (CA jour/mois) repartent donc de zéro pour ce
     * vendeur — c'est le comportement attendu d'une suppression définitive.
     */
    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé avec l'id: " + id));

        // Interdire la suppression de son propre compte.
        if (isSelf(user)) {
            throw new BusinessRuleException(
                    "Vous ne pouvez pas supprimer votre propre compte. "
                            + "Demandez à un autre administrateur de le faire.");
        }

        assertCanModifyTarget(user);

        // Purge des données transactionnelles liées (contraintes FK)
        saleEditRequestRepository.deleteByRequestedBy(user);
        productCommentRepository.deleteByAuthor(user);
        stockMovementRepository.deleteByPerformedBy(user);
        saleRepository.deleteBySeller(user);

        userRepository.delete(user);
        auditService.record("DELETE", "User", user.getId(), user.getEmail(),
                "Compte supprimé avec ses ventes et mouvements de stock");
    }

    /**
     * Seul un SUPER_ADMIN peut attribuer les rôles ADMIN ou SUPER_ADMIN.
     */
    private void assertCanAssignRoles(Set<Role> roles) {
        if ((roles.contains(Role.ADMIN) || roles.contains(Role.SUPER_ADMIN))
                && !CurrentUser.isSuperAdmin()) {
            throw new AccessDeniedException(
                    "Seul un super administrateur peut attribuer les rôles ADMIN ou SUPER_ADMIN");
        }
    }

    /**
     * Seul un SUPER_ADMIN peut désactiver/supprimer un autre SUPER_ADMIN.
     */
    private void assertCanModifyTarget(User target) {
        if (target.hasRole(Role.SUPER_ADMIN) && !CurrentUser.isSuperAdmin()
                && !isSelf(target)) {
            throw new AccessDeniedException(
                    "Seul un super administrateur peut modifier un autre super administrateur");
        }
    }

    private boolean isSelf(User target) {
        return target.getEmail() != null
                && target.getEmail().equals(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private Set<Role> parseRoles(Set<String> roleStrings) {
        Set<Role> roles = new HashSet<>();
        if (roleStrings != null) {
            for (String r : roleStrings) {
                try {
                    roles.add(Role.valueOf(r.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BusinessRuleException(
                            "Rôle inconnu : « " + r + " ». Rôles valides : "
                                    + validRoleNames());
                }
            }
        }
        return roles;
    }

    private String validRoleNames() {
        return java.util.Arrays.stream(Role.values())
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String roleNames(Set<Role> roles) {
        return roles.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream().map(Role::name).collect(java.util.stream.Collectors.toSet()))
                .active(user.isActive())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getName() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /** Helper d'accès au contexte de sécurité. */
    private static final class CurrentUser {
        static boolean isSuperAdmin() {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null
                    && auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        }

        /** Portée plateforme = SUPER_ADMIN non rattaché à une entreprise. */
        static boolean isPlatformScope() {
            return TenantContext.getCompanyId() == null;
        }
    }
}
