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
public class CompanyResponse {

    private Long id;
    private String name;
    private String slug;
    private boolean active;
    private boolean warehouseEnabled;
    private long userCount;
    private LocalDateTime createdAt;
}