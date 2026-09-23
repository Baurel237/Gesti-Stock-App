package com.stock.api.security;

import com.stock.api.entity.User;
import com.stock.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service de chargement des utilisateurs pour Spring Security.
 * Charge les utilisateurs depuis la base de données avec leurs rôles.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur non trouvé avec l'email: " + email));

        return new TenantUserDetails(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toSet()),
                user.getCompany() != null ? user.getCompany().getId() : null
        );
    }
}
