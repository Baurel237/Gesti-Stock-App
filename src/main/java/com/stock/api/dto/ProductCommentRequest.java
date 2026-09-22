package com.stock.api.dto;

import com.stock.api.entity.ProductComment.CommentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCommentRequest {

    @NotBlank(message = "Le commentaire est obligatoire")
    @Size(max = 500, message = "Le commentaire ne doit pas dépasser 500 caractères")
    private String content;

    @NotNull(message = "La catégorie d'alerte est obligatoire")
    private CommentCategory category;
}
