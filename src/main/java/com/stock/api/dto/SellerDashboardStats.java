package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerDashboardStats {
    private Integer todaySalesCount;
    private BigDecimal todaySalesTotal;
    private Integer monthSalesCount;
    private BigDecimal monthSalesTotal;
    private List<DailySalesData> last7Days;
    private List<SaleResponse> recentSales;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySalesData {
        private String date;
        private Integer count;
        private BigDecimal total;
    }
}
