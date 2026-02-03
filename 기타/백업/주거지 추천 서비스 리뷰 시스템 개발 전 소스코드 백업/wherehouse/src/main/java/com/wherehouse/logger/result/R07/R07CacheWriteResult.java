package com.wherehouse.logger.result.R07;

import lombok.Builder;
import lombok.Data;

// R-07 서브: L1 캐시 쓰기
@Data
@Builder
public class R07CacheWriteResult {
    private String cacheKey;
    private int dataSize;
    private long ttlSeconds;  // 300 (5분)
    private boolean isSuccess;
    private String errorMessage;
}