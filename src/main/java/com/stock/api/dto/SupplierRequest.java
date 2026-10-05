package com.stock.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierRequest {

    @NotBlank(message = "Le nom du fournisseur est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String name;

    @Email(message = "L'adresse email n'est pas valide")
    @Size(max = 100, message = "L'email ne doit pas dépasser 100 caractères")
    private String email;

    @Size(max = 30, message = "Le téléphone ne doit pas dépasser 30 caractères")
    private String phone;

    @Size(max = 200, message = "L'adresse ne doit pas dépasser 200 caractères")
    private String address;
}
