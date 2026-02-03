package com.WhereHouse.AnalysisData.hospital.processor;

import com.WhereHouse.AnalysisData.hospital.entity.AnalysisHospitalData;
import com.WhereHouse.AnalysisData.hospital.repository.AnalysisHospitalDataRepository;
import com.WhereHouse.AnalysisData.hospital.service.KakaoCoordinateService;
import com.WhereHouse.AnalysisStaticData.HospitalSave.entity.HospitalData;
import com.WhereHouse.AnalysisStaticData.HospitalSave.repository.HospitalDataRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalDataProcessor {

    private final HospitalDataRepository hospitalDataRepository;
    private final AnalysisHospitalDataRepository analysisHospitalDataRepository;
    private final KakaoCoordinateService kakaoCoordinateService;
    private final EntityManager entityManager;

    private static final int BATCH_SIZE = 1000;

    /**
     * 병원 데이터 분석용 테이블 생성 메인 프로세스
     */
    @Transactional
    public void processHospitalDataForAnalysis() {
        log.info("=== 병원 데이터 분석용 테이블 생성 작업 시작 ===");
        LocalDateTime startTime = LocalDateTime.now();

        // 기존 분석용 데이터 삭제 (선택사항)
        log.info("기존 분석용 데이터 삭제 중...");
        analysisHospitalDataRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();

        // 전체 데이터 개수 조회
        long totalCount = hospitalDataRepository.count();
        log.info("처리 대상 원천 데이터: {} 개", totalCount);

        // 통계 변수 초기화
        ProcessingStats stats = new ProcessingStats();
        stats.totalSourceData = (int) totalCount;

        // 배치 처리
        int totalPages = (int) Math.ceil((double) totalCount / BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            processBatch(page, stats);

            // 메모리 관리
            entityManager.flush();
            entityManager.clear();

            // 진행률 로깅
            int processed = (page + 1) * BATCH_SIZE;
            int actualProcessed = Math.min(processed, (int) totalCount);
            log.info("진행률: {}/{} ({:.1f}%)",
                    actualProcessed, totalCount,
                    (actualProcessed * 100.0 / totalCount));
        }

        // 최종 결과 출력
        LocalDateTime endTime = LocalDateTime.now();
        printFinalResults(stats, startTime, endTime);
    }

    /**
     * 배치 단위 데이터 처리
     */
    private void processBatch(int page, ProcessingStats stats) {
        PageRequest pageRequest = PageRequest.of(page, BATCH_SIZE);
        Page<HospitalData> hospitalDataPage = hospitalDataRepository.findAll(pageRequest);

        List<AnalysisHospitalData> batchList = new ArrayList<>();

        for (HospitalData sourceData : hospitalDataPage.getContent()) {
            try {
                AnalysisHospitalData analysisData = convertToAnalysisData(sourceData, stats);
                if (analysisData != null) {
                    batchList.add(analysisData);
                    stats.successfulConversions++;
                }
            } catch (Exception e) {
                stats.failedConversions++;
                log.error("데이터 변환 실패 - ID: {}, 사업장명: {}, 오류: {}",
                        sourceData.getId(), sourceData.getBusinessName(), e.getMessage());
            }
        }

        // 배치 저장
        if (!batchList.isEmpty()) {
            analysisHospitalDataRepository.saveAll(batchList);
        }
    }

    /**
     * 원천 데이터를 분석용 데이터로 변환
     */
    private AnalysisHospitalData convertToAnalysisData(HospitalData sourceData, ProcessingStats stats) {
        AnalysisHospitalData.AnalysisHospitalDataBuilder builder = AnalysisHospitalData.builder();

        // 기본 정보 변환 (NULL 처리 적용)
        builder.businessName(processStringField(sourceData.getBusinessName()))
                .businessTypeName(processStringField(sourceData.getBusinessTypeName()))
                .detailedStatusName(processStringField(sourceData.getDetailedStatusName()))
                .phoneNumber(processStringField(sourceData.getPhoneNumber()))
                .lotAddress(processStringField(sourceData.getLotAddress()))
                .roadAddress(processStringField(sourceData.getRoadAddress()));

        // 상세영업상태별 통계 업데이트
        String statusName = sourceData.getDetailedStatusName();
        stats.statusDistribution.merge(statusName != null ? statusName : "데이터없음", 1, Integer::sum);

        // 업종별 통계 업데이트
        String businessType = sourceData.getBusinessTypeName();
        stats.businessTypeDistribution.merge(businessType != null ? businessType : "데이터없음", 1, Integer::sum);

        // 좌표 계산 (기존 좌표는 무시하고 Kakao API로만 계산)
        BigDecimal[] coordinates = kakaoCoordinateService.getCoordinates(
                sourceData.getRoadAddress(),
                sourceData.getLotAddress(),
                sourceData.getBusinessName()
        );

        if (coordinates != null) {
            builder.latitude(coordinates[0])
                    .longitude(coordinates[1]);
            stats.successfulCoordinates++;
        } else {
            // 좌표 계산 실패 시 NULL로 저장
            builder.latitude(null)
                    .longitude(null);
            stats.failedCoordinates++;
        }

        return builder.build();
    }

    /**
     * 문자열 필드 NULL 처리
     */
    private String processStringField(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
    }

    /**
     * 최종 결과 출력
     */
    private void printFinalResults(ProcessingStats stats, LocalDateTime startTime, LocalDateTime endTime) {
        long processingTimeSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        long minutes = processingTimeSeconds / 60;
        long seconds = processingTimeSeconds % 60;

        log.info("\n=== 병원 데이터 분석용 테이블 생성 작업 완료 ===");
        log.info("- 원천 데이터: {} 개", stats.totalSourceData);
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개",
                stats.successfulConversions, stats.failedConversions);

        if (stats.successfulConversions > 0) {
            double coordinateSuccessRate = (stats.successfulCoordinates * 100.0) / stats.successfulConversions;
            log.info("- 좌표 계산: 성공 {} 개 ({:.1f}%), 실패 {} 개 ({:.1f}%)",
                    stats.successfulCoordinates, coordinateSuccessRate,
                    stats.failedCoordinates, (100.0 - coordinateSuccessRate));
        }

        // 업종별 분포 (상위 10개)
        log.info("- 업종별 분포:");
        stats.businessTypeDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> log.info("  {} : {} 개", entry.getKey(), entry.getValue()));

        // 상세영업상태별 분포
        log.info("- 상세영업상태별 분포:");
        stats.statusDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> log.info("  {} : {} 개", entry.getKey(), entry.getValue()));

        log.info("- 처리 시간: {}분 {}초", minutes, seconds);
        log.info("- 완료 시각: {}", endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("===========================================\n");
    }

    /**
     * 데이터 품질 검증 메서드
     */
    public void validateDataQuality() {
        log.info("\n=== 데이터 품질 검증 ===");

        Long totalCount = analysisHospitalDataRepository.getTotalCount();
        Long coordinateCount = analysisHospitalDataRepository.getCoordinateCount();

        if (totalCount != null && coordinateCount != null) {
            double completionRate = (coordinateCount * 100.0) / totalCount;
            log.info("전체 데이터: {} 개", totalCount);
            log.info("좌표 보유: {} 개 ({:.1f}%)", coordinateCount, completionRate);
        }

        // 업종별 분포 확인
        List<Object[]> businessTypeStats = analysisHospitalDataRepository.countByBusinessType();
        log.info("업종별 분포:");
        businessTypeStats.forEach(stat ->
                log.info("  {} : {} 개", stat[0], stat[1]));

        // 상세영업상태별 분포 확인
        List<Object[]> statusStats = analysisHospitalDataRepository.countByDetailedStatus();
        log.info("상세영업상태별 분포:");
        statusStats.forEach(stat ->
                log.info("  {} : {} 개", stat[0], stat[1]));

        log.info("========================\n");
    }

    /**
     * 처리 통계 클래스
     */
    private static class ProcessingStats {
        int totalSourceData = 0;
        int successfulConversions = 0;
        int failedConversions = 0;
        int successfulCoordinates = 0;
        int failedCoordinates = 0;
        Map<String, Integer> businessTypeDistribution = new HashMap<>();
        Map<String, Integer> statusDistribution = new HashMap<>();
    }
}