package com.stock.api.service;

import com.stock.api.dto.CategoryRequest;
import com.stock.api.dto.CategoryResponse;
import com.stock.api.entity.Category;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour CategoryService.
 * RG-04 : suppression logique des catégories.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final long COMPANY_ID = 10L;

    private Category category;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(COMPANY_ID);
        category = Category.builder()
                .id(1L)
                .name("Électronique")
                .description("Appareils électroniques")
                .companyId(COMPANY_ID)
                .deleted(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ═══════════════════════════════════════════════════════
    // RG-04 : Suppression logique
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("RG-04 — Suppression logique")
    class SoftDeleteTests {

        @Test
        @DisplayName("Suppression → flag deleted = true (pas de suppression physique)")
        void delete_setsDeletedFlag() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.findByCategory_IdAndDeletedFalse(eq(1L), any(Pageable.class)))
                    .thenReturn(Page.empty());
            when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            categoryService.delete(1L);

            assertTrue(category.isDeleted(),
                    "RG-04 : la catégorie doit être marquée comme supprimée (soft delete)");
            verify(categoryRepository).save(category);
        }

        @Test
        @DisplayName("Suppression avec produits rattachés → IllegalStateException")
        void delete_withProducts_throwsException() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

            // Simuler qu'il y a des produits actifs rattachés
            var productPage = new PageImpl<>(Collections.singletonList(
                    com.stock.api.entity.Product.builder().id(1L).build()));
            when(productRepository.findByCategory_IdAndDeletedFalse(eq(1L), any(Pageable.class)))
                    .thenReturn(productPage);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> categoryService.delete(1L));

            assertTrue(exception.getMessage().contains("produits"));
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Suppression d'une catégorie déjà supprimée → IllegalArgumentException")
        void delete_alreadyDeleted_throwsException() {
            category.setDeleted(true);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

            assertThrows(IllegalArgumentException.class,
                    () -> categoryService.delete(1L));

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Catégorie introuvable → IllegalArgumentException")
        void delete_notFound_throwsException() {
            when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> categoryService.delete(999L));
        }
    }

    // ═══════════════════════════════════════════════════════
    // RG-04 : Lecture exclut les catégories supprimées
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("RG-04 — Lecture exclut les catégories supprimées")
    class ReadExcludesDeletedTests {

        @Test
        @DisplayName("findAll ne retourne que les catégories non supprimées")
        void findAll_excludesDeleted() {
            Page<Category> page = new PageImpl<>(Collections.singletonList(category));
            when(categoryRepository.findByFilters(eq(COMPANY_ID), any(Pageable.class))).thenReturn(page);

            Page<CategoryResponse> result = categoryService.findAll(Pageable.unpaged());

            assertEquals(1, result.getContent().size());
            assertFalse(result.getContent().get(0).isDeleted());
            verify(categoryRepository).findByFilters(eq(COMPANY_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("findById sur catégorie supprimée → exception")
        void findById_deleted_throwsException() {
            category.setDeleted(true);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

            assertThrows(IllegalArgumentException.class,
                    () -> categoryService.findById(1L));
        }
    }

    // ═══════════════════════════════════════════════════════
    // Création / Mise à jour
    // ═══════════════════════════════════════════════════════
    @Nested
    @DisplayName("Création et mise à jour")
    class CreateUpdateTests {

        @Test
        @DisplayName("Création avec nom unique → succès")
        void create_uniqueName() {
            when(categoryRepository.existsByCompanyIdAndNameAndDeletedFalse(COMPANY_ID, "Nouvelle"))
                    .thenReturn(false);
            when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryRequest request = CategoryRequest.builder()
                    .name("Nouvelle")
                    .description("Description")
                    .build();

            CategoryResponse response = categoryService.create(request);

            assertNotNull(response);
            assertEquals("Nouvelle", response.getName());
            verify(categoryRepository).save(any());
        }

        @Test
        @DisplayName("Création avec nom existant → IllegalStateException")
        void create_duplicateName_throwsException() {
            when(categoryRepository.existsByCompanyIdAndNameAndDeletedFalse(COMPANY_ID, "Électronique"))
                    .thenReturn(true);

            CategoryRequest request = CategoryRequest.builder()
                    .name("Électronique")
                    .build();

            assertThrows(IllegalStateException.class,
                    () -> categoryService.create(request));
        }

        @Test
        @DisplayName("Mise à jour → succès")
        void update_success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(categoryRepository.existsByCompanyIdAndNameAndDeletedFalse(COMPANY_ID, "Modifié"))
                    .thenReturn(false);
            when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryRequest request = CategoryRequest.builder()
                    .name("Modifié")
                    .description("Nouvelle description")
                    .build();

            CategoryResponse response = categoryService.update(1L, request);

            assertEquals("Modifié", response.getName());
        }
    }
}
