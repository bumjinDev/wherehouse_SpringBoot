package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopPropertyDto {
    private String propertyId;
    private String propertyName;
    private String address;
    private Integer price;
    private String leaseType;
    private Double area;
    private Integer floor;
    private Integer buildYear;
    private Double finalScore;
}