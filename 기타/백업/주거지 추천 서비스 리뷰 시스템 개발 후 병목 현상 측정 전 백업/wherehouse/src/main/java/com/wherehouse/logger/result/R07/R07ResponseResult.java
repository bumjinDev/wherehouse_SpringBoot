package com.wherehouse.logger.result.R07;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// R-07 메인: buildFinalResponse
@Data
@Builder
public class R07ResponseResult {
    // 응답 구성
    private String analysisStatus;
    private boolean hasAddress;
    private boolean hasSafetyScore;
    private boolean hasConvenienceScore;

    // 추천/경고
    private List<String> recommendations;
    private List<String> warnings;

    // L1 캐시 저장
    private R07CacheWriteResult cacheWrite;

    // 응답 크기
    private int responseSizeBytes;

    // 실행 상태
    private boolean isSuccess;
    private String errorMessage;
}