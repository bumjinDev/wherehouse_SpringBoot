// PoliceOfficeResponseDTO.java
package com.wherehouse.information.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliceOfficeResponseDTO {
    private String address;
    private Double latitude;
    private Double longitude;
}