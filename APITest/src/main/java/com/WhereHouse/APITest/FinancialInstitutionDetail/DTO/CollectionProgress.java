package com.WhereHouse.APITest.FinancialInstitutionDetail.DTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class CollectionProgress {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AtomicInteger totalProcessed = new AtomicInteger(0);
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger errorCount = new AtomicInteger(0);
    private AtomicInteger skipCount = new AtomicInteger(0);
    private List<ErrorDetail> errors = new ArrayList<>();
    private String currentTask = "";
    private String currentDistrict = "";
    private int currentPage = 0;
    private int totalDistricts = 25;
    private int completedDistricts = 0;

    @Data
    public static class ErrorDetail {
        private LocalDateTime timestamp;
        private String errorType;
        private String errorMessage;
        private String context;
        private String placeName;
        private String placeId;
        private String district;
        private String stackTrace;

        public ErrorDetail(String errorType, String errorMessage, String context) {
            this.timestamp = LocalDateTime.now();
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.context = context;
        }

        public ErrorDetail(String errorType, String errorMessage, String context,
                           String placeName, String placeId, String district) {
            this(errorType, errorMessage, context);
            this.placeName = placeName;
            this.placeId = placeId;
            this.district = district;
        }
    }

    public void addError(ErrorDetail error) {
        this.errors.add(error);
        this.errorCount.incrementAndGet();
    }

    public void incrementSuccess() {
        this.successCount.incrementAndGet();
        this.totalProcessed.incrementAndGet();
    }

    public void incrementSkip() {
        this.skipCount.incrementAndGet();
        this.totalProcessed.incrementAndGet();
    }

    public double getProgressPercentage() {
        if (totalDistricts == 0) return 0.0;
        return (double) completedDistricts / totalDistricts * 100.0;
    }

    public String getProgressStatus() {
        return String.format("[%d/%d 구 완료] 성공: %d, 오류: %d, 스킵: %d",
                completedDistricts, totalDistricts,
                successCount.get(), errorCount.get(), skipCount.get());
    }
}