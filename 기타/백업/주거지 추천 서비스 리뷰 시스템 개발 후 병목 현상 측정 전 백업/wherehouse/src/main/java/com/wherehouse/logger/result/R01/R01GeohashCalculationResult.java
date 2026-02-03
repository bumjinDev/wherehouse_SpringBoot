package com.wherehouse.logger.result.R01;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class R01GeohashCalculationResult {

    private double latitude;
    private double longitude;
    private int precision;

    private String centerHash;

    private List<String> adjacentHashes;

    private boolean isSuccess;
    private String errorMessage;
}