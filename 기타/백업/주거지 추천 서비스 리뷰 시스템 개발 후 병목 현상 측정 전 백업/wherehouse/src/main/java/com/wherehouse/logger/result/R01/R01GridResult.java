// R-01: 9-Block 그리드 계산 결과
package com.wherehouse.logger.result.R01;

import lombok.Data;
import lombok.Builder;
import java.util.List;

// R-01 메인: calculate9BlockGrid
@Data
@Builder
public class R01GridResult {
    // 입력 파라미터
    private double requestLatitude;
    private double requestLongitude;
    private Integer requestRadius;

    // 출력 결과
    private String centerGeohashId;
    private List<String> nineBlockGeohashes;
    private int totalGridCount;

    // 실행 상태
    private boolean isSuccess;
    private String errorMessage;
}