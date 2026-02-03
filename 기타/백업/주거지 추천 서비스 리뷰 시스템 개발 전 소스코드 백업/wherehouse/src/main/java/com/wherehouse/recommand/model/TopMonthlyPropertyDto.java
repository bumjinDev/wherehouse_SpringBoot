package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopMonthlyPropertyDto {
    private String propertyId;
    private String propertyName;
    private String address;
    private Integer price; // 보증금
    private Integer monthlyRent; // 월세금
    private String leaseType; // "월세"
    private Double area;
    private Integer floor;
    private Integer buildYear;
    private Double finalScore;
}