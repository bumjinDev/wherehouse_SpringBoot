package com.wherehouse.logger;

import lombok.Data;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Data
public class PerformanceLogger {

    private static final Logger log = LoggerFactory.getLogger("PERFORMANCE");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final long startNanoTime;
    private final String step;
    private final String action;
    private final String layer;
    private final String className;
    private final String methodName;

    // 결과 데이터를 설정하는 메서드
    // 각 단계별 결과 객체(DTO)를 담을 수 있는 단일 필드
    @Setter
    private Object resultData;

    private PerformanceLogger(String step, String action, String layer, String className, String methodName) {
        this.startNanoTime = System.nanoTime();
        this.step = step;
        this.action = action;
        this.layer = layer;
        this.className = className;
        this.methodName = methodName;
        logStart();
    }



    public static PerformanceLogger start(String step, String action, String layer, String className, String methodName) {
        return new PerformanceLogger(step, action, layer, className, methodName);
    }

    private void logStart() {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();

            logEntry.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER));
            logEntry.put("nanoTime", startNanoTime);
            logEntry.put("traceId", MDC.get("traceId"));
            logEntry.put("thread", Thread.currentThread().getName());
            logEntry.put("eventType", "PERFORMANCE");
            logEntry.put("step", step);
            logEntry.put("layer", layer);
            logEntry.put("class", className);
            logEntry.put("method", methodName);
            logEntry.put("action", action);
            logEntry.put("status", "START");

            log.info(objectMapper.writeValueAsString(logEntry));

        } catch (Exception e) {
            log.error("Failed to log performance start", e);
        }
    }

    public void end() {

        long endNanoTime = System.nanoTime();
        long durationNs = endNanoTime - startNanoTime;
        long durationMs = durationNs / 1_000_000;

        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER));
            logEntry.put("nanoTime", endNanoTime);
            logEntry.put("traceId", MDC.get("traceId"));
            logEntry.put("thread", Thread.currentThread().getName());
            logEntry.put("eventType", "PERFORMANCE");
            logEntry.put("step", step);
            logEntry.put("layer", layer);
            logEntry.put("class", className);
            logEntry.put("method", methodName);
            logEntry.put("action", action);
            logEntry.put("status", "END");
            logEntry.put("duration_ns", durationNs);
            logEntry.put("duration_ms", durationMs);

            // resultData가 있으면 추가
            if (resultData != null) {
                logEntry.set("resultData", objectMapper.valueToTree(resultData));
            }


            log.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            log.error("Failed to log performance end", e);
        }
    }
}
