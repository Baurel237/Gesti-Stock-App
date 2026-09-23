package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseResponse {

    private Long id;
    private String name;
    private String location;
    private boolean active;
    private Long companyId;
    private int totalQuantity;
    private LocalDateTime createdAt;
}