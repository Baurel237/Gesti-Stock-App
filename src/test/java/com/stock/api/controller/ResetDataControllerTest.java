package com.stock.api.controller;

import com.stock.api.dto.ResetDataRequest;
import com.stock.api.service.ResetDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du garde-fou d'environnement de {@link ResetDataController}.
 *
 * L'endpoint /api/admin/reset/db est destructeur (purge des utilisateurs +
 * recréation d'un compte connu) : il doit répondre 404 sauf si un profil
 * « dev » ou « test » est explicitement actif.
 */
@ExtendWith(MockitoExtension.class)
class ResetDataControllerTest {

    @Mock
    private ResetDataService resetDataService;

    @Mock
    private Environment environment;

    private ResetDataController controller;

    @BeforeEach
    void setUp() {
        controller = new ResetDataController(resetDataService, environment);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void givenActiveProfiles(String... profiles) {
        when(environment.getActiveProfiles()).thenReturn(profiles);
    }

    @Nested
    @DisplayName("Garde-fou : endpoint neutralisé hors dev/test")
    class GuardTests {

        @Test
        @DisplayName("Profil postgresql seul (production) → 404, service jamais appelé")
        void reset_productionProfile_returns404() {
            givenActiveProfiles("postgresql");

            ResponseEntity<ResetDataService.ResetResult> response =
                    controller.reset(new ResetDataRequest("RESET-NOW"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(resetDataService, never()).reset(anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Aucun profil actif (défaut) → 404, service jamais appelé")
        void reset_noProfile_returns404() {
            givenActiveProfiles();

            ResponseEntity<ResetDataService.ResetResult> response =
                    controller.reset(new ResetDataRequest("RESET-NOW"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(resetDataService, never()).reset(anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Profil prod-like avec confirmation correcte → 404 quand même")
        void reset_confirmationCorrectButProdProfile_returns404() {
            givenActiveProfiles("prod");

            ResponseEntity<ResetDataService.ResetResult> response =
                    controller.reset(new ResetDataRequest("RESET-NOW"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(resetDataService, never()).reset(anyBoolean(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Profils autorisés : l'endpoint fonctionne")
    class AllowedProfileTests {

        @BeforeEach
        void authenticateAsSuperadmin() {
            // Le contrôleur journalise le nom de l'appelant : en conditions
            // réelles l'endpoint n'est atteignable qu'authentifié (SUPER_ADMIN).
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "superadmin@mail.com", "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        }

        @Test
        @DisplayName("Profil dev + confirmation correcte → 200 et service appelé")
        void reset_devProfile_returns200() {
            givenActiveProfiles("dev");

            ResponseEntity<ResetDataService.ResetResult> response =
                    controller.reset(new ResetDataRequest("RESET-NOW"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(resetDataService).reset(false, true);
        }

        @Test
        @DisplayName("Profil test + confirmation correcte → 200")
        void reset_testProfile_returns200() {
            givenActiveProfiles("test");

            ResponseEntity<ResetDataService.ResetResult> response =
                    controller.reset(new ResetDataRequest("RESET-NOW"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(resetDataService).reset(false, true);
        }

        @Test
        @DisplayName("Profil autorisé + confirmation incorrecte → IllegalArgumentException")
        void reset_devProfileBadConfirmation_throws() {
            givenActiveProfiles("dev");

            assertThatThrownBy(() -> controller.reset(new ResetDataRequest("WRONG")))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(resetDataService, never()).reset(anyBoolean(), anyBoolean());
        }
    }
}
