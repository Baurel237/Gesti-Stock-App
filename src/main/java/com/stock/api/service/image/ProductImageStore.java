package com.stock.api.service.image;

import com.stock.api.service.ProductImageStorageService.StoredImage;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backend de stockage d'images produits.
 *
 * Deux implémentations existent, sélectionnées par la propriété
 * {@code stock.image-storage.mode} :
 * <ul>
 *   <li>{@code local} (défaut) — {@link LocalStorageProductImageStore} ;</li>
 *   <li>{@code s3} — {@link MinioProductImageStore} (MinIO ou tout endpoint compatible S3).</li>
 * </ul>
 * La clé retournée par {@link #store} est opaque pour l'appelant (nom de fichier
 * local ou clé d'objet S3) et suffit à {@link #read}/{@link #delete}.
 */
public interface ProductImageStore {

    /** Valide et stocke l'image, retourne clé + type MIME détecté. */
    StoredImage store(MultipartFile file);

    /**
     * Charge l'image stockée. La ressource est entièrement en mémoire
     * (les images sont plafonnées à 5 Mo).
     */
    Resource read(String key, String contentType);

    /** Supprime l'image (no-op sûre si clé null/vide ou objet absent). */
    void delete(String key);
}
