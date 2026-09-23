package com.stock.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps de la requête POST /api/auth/refresh.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenRequest {

    @NotBlank(message = "Le refresh token est obligatoire")
    private String refreshToken;
}
