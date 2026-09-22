package com.stock.api.dto;

import com.stock.api.entity.ProductComment.CommentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCommentResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productReference;
    private Long authorId;
    private String authorName;
    private String content;
    private CommentCategory category;
    private LocalDateTime createdAt;
}
