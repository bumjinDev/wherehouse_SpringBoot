package com.WhereHouse.AnalysisData.convenience.processor;

import com.WhereHouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreData;
import com.WhereHouse.AnalysisData.convenience.repository.AnalysisConvenienceStoreRepository;
import com.WhereHouse.AnalysisData.convenience.service.KakaoConvenienceCoordinateService;
import com.WhereHouse.AnalysisStaticData.ConvenienceStore.reposiotry.ConvenienceStoreDataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 편의점 데이터 변환 및 처리를 위한 메인 Processor
 * AnalysisDataProcessor에서 호출되어 편의점 데이터를 분석용 테이블로 변환
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreCoordinateProcessor {

    private final ConvenienceStoreDataRepository sourceRepository;
    private final AnalysisConvenienceStoreRepository analysisRepository;
    private final KakaoConvenienceCoordinateService coordinateService;

    @PersistenceContext
    private EntityManager entityManager;

    // 배치 처리 설정
    private static final int BATCH_SIZE = 1000;

    // 처리 통계
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong successfulProcessed = new AtomicLong(0);
    private final AtomicLong failedProcessed = new AtomicLong(0);
    private final AtomicLong coordinateSuccess = new AtomicLong(0);
    private final AtomicLong coordinateFailed = new AtomicLong(0);

    /**
     * 편의점 데이터 전체 처리 실행 - 메인 진입점
     * AnalysisDataProcessor에서 호출되는 메인 메서드
     */
    @Transactional
    public void processConvenienceStoreData() {
        log.info("편의점 데이터 분석용 테이블 생성 작업 시작");

        long startTime = System.currentTimeMillis();
        resetStatistics();

        try {
            // 원천 데이터 총 개수 확인
            long totalSourceCount = sourceRepository.getTotalSourceCount();
            log.info("원천 데이터 총 개수: {} 건", totalSourceCount);

            if (totalSourceCount == 0) {
                log.warn("처리할 원천 데이터가 없습니다.");
                return;
            }

            // 기존 분석용 데이터 삭제
            clearExistingAnalysisData();

            // 배치 처리 실행
            processBatchData(totalSourceCount);

            // 처리 결과 출력
            printFinalResults(totalSourceCount, startTime);

        } catch (Exception e) {
            log.error("편의점 데이터 처리 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        }

        log.info("편의점 데이터 분석용 테이블 생성 작업 완료");
    }

    /**
     * 기존 분석용 데이터 삭제
     */
    private void clearExistingAnalysisData() {
        long existingCount = analysisRepository.count();
        if (existingCount > 0) {
            log.info("기존 분석용 데이터 {} 건 삭제 중...", existingCount);
            analysisRepository.deleteAll();
            entityManager.flush();
            entityManager.clear();
            log.info("기존 분석용 데이터 삭제 완료");
        }
    }

    /**
     * 배치 데이터 처리 실행
     */
    private void processBatchData(long totalSourceCount) {
        int totalPages = (int) Math.ceil((double) totalSourceCount / BATCH_SIZE);
        log.info("배치 처리 시작 - 총 {} 페이지, 배치 크기: {}", totalPages, BATCH_SIZE);

        for (int page = 0; page < totalPages; page++) {
            try {
                processBatch(page, totalSourceCount);

                // 메모리 관리
                if (page % 10 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }

            } catch (Exception e) {
                log.error("배치 처리 중 오류 발생 - 페이지: {}, 오류: {}", page, e.getMessage(), e);
                failedProcessed.addAndGet(BATCH_SIZE);
            }
        }
    }

    /**
     * 배치 단위 처리
     */
    @Transactional
    public void processBatch(int pageNumber, long totalCount) {
        Pageable pageable = PageRequest.of(pageNumber, BATCH_SIZE);
        Page<Map<String, Object>> sourcePage = sourceRepository.findAnalysisFieldsOnly(pageable);

        if (sourcePage.isEmpty()) {
            log.debug("페이지 {} - 처리할 데이터 없음", pageNumber);
            return;
        }

        List<AnalysisConvenienceStoreData> analysisDataList = new ArrayList<>();

        for (Map<String, Object> sourceData : sourcePage.getContent()) {
            try {
                AnalysisConvenienceStoreData analysisData = processIndividualData(sourceData);
                if (analysisData != null) {
                    analysisDataList.add(analysisData);
                    successfulProcessed.incrementAndGet();
                } else {
                    failedProcessed.incrementAndGet();
                }
                totalProcessed.incrementAndGet();

            } catch (Exception e) {
                log.error("개별 데이터 처리 중 오류 - 사업장명: {}, 오류: {}",
                        sourceData.get("businessName"), e.getMessage());
                failedProcessed.incrementAndGet();
                totalProcessed.incrementAndGet();
            }
        }

        // 배치 저장
        if (!analysisDataList.isEmpty()) {
            analysisRepository.saveAll(analysisDataList);
            entityManager.flush();
            entityManager.clear();
        }

        // 진행률 로깅
        long processed = totalProcessed.get();
        double progressRate = ((double) processed / totalCount) * 100;
        log.info("진행률: {}/{} ({:.1f}%) - 성공: {}, 실패: {}, 좌표성공: {}",
                processed, totalCount, progressRate,
                successfulProcessed.get(), failedProcessed.get(), coordinateSuccess.get());
    }

    /**
     * 개별 데이터 처리
     */
    private AnalysisConvenienceStoreData processIndividualData(Map<String, Object> sourceData) {
        String businessName = (String) sourceData.get("businessName");
        String detailedStatusName = (String) sourceData.get("detailedStatusName");
        String phoneNumber = (String) sourceData.get("phoneNumber");
        String lotAddress = (String) sourceData.get("lotAddress");
        String roadAddress = (String) sourceData.get("roadAddress");

        // 분석용 데이터 생성 (NULL 값 처리 포함)
        AnalysisConvenienceStoreData analysisData = AnalysisConvenienceStoreData.fromSourceData(
                businessName, detailedStatusName, phoneNumber, lotAddress, roadAddress);

        // 좌표 계산 시도
        if (analysisData.hasValidAddress()) {
            try {
                KakaoConvenienceCoordinateService.CoordinateResult coordinateResult =
                        coordinateService.calculateCoordinates(roadAddress, lotAddress, businessName);

                if (coordinateResult.isSuccess()) {
                    analysisData.setCoordinates(coordinateResult.getLatitude(), coordinateResult.getLongitude());
                    coordinateSuccess.incrementAndGet();

                    log.debug("좌표 계산 성공 - 사업장명: {}, 좌표: ({}, {})",
                            businessName, coordinateResult.getLatitude(), coordinateResult.getLongitude());
                } else {
                    coordinateFailed.incrementAndGet();
                    log.debug("좌표 계산 실패 - 사업장명: {}, 오류: {}",
                            businessName, coordinateResult.getErrorMessage());
                }

            } catch (Exception e) {
                coordinateFailed.incrementAndGet();
                log.error("좌표 계산 중 예외 발생 - 사업장명: {}, 오류: {}", businessName, e.getMessage());
            }
        } else {
            coordinateFailed.incrementAndGet();
            log.debug("유효한 주소 정보 없음 - 사업장명: {}", businessName);
        }

        return analysisData;
    }

    /**
     * 최종 처리 결과 출력
     */
    private void printFinalResults(long totalSourceCount, long startTime) {
        long endTime = System.currentTimeMillis();
        long processingTimeMs = endTime - startTime;

        // 최종 저장된 데이터 통계
        long finalCount = analysisRepository.getTotalCount();
        long coordinateCount = analysisRepository.getCoordinateCount();
        Double coordinateRate = analysisRepository.getCoordinateCompletionRate();

        // API 통계
        KakaoConvenienceCoordinateService.ApiStatistics apiStats = coordinateService.getApiStatistics();

        log.info("=== 편의점 데이터 분석용 테이블 생성 작업 완료 ===");
        log.info("원천 데이터: {} 개", totalSourceCount);
        log.info("데이터 변환: 성공 {} 개, 실패 {} 개",
                successfulProcessed.get(), failedProcessed.get());
        log.info("최종 저장: {} 개", finalCount);
        log.info("좌표 계산: 성공 {} 개 ({:.1f}%), 실패 {} 개",
                coordinateCount, coordinateRate != null ? coordinateRate : 0.0, coordinateFailed.get());

        // 상세영업상태별 분포
        List<Object[]> statusDistribution = analysisRepository.getStatusDistribution();
        log.info("상세영업상태별 분포:");
        statusDistribution.stream()
                .limit(10) // 상위 10개만 출력
                .forEach(row -> log.info("  {} : {} 개", row[0], row[1]));

        log.info("API 호출 통계: 총 {}회, 성공 {}회 ({:.1f}%), 실패 {}회",
                apiStats.getTotalCalls(), apiStats.getSuccessfulCalls(),
                apiStats.getSuccessRate(), apiStats.getFailedCalls());

        long seconds = processingTimeMs / 1000;
        long minutes = seconds / 60;
        log.info("처리 시간: {}분 {}초", minutes, seconds % 60);
    }

    /**
     * 통계 초기화
     */
    private void resetStatistics() {
        totalProcessed.set(0);
        successfulProcessed.set(0);
        failedProcessed.set(0);
        coordinateSuccess.set(0);
        coordinateFailed.set(0);
        coordinateService.resetStatistics();
    }

    /**
     * 샘플 데이터 처리 (테스트용)
     */
    @Transactional
    public void processSampleData(int sampleSize) {
        log.info("샘플 데이터 처리 시작 - 크기: {}", sampleSize);

        resetStatistics();
        long startTime = System.currentTimeMillis();

        Pageable pageable = PageRequest.of(0, Math.min(sampleSize, BATCH_SIZE));
        Page<Map<String, Object>> sourcePage = sourceRepository.findAnalysisFieldsOnly(pageable);

        if (sourcePage.isEmpty()) {
            log.warn("처리할 샘플 데이터가 없습니다.");
            return;
        }

        List<AnalysisConvenienceStoreData> analysisDataList = new ArrayList<>();

        for (Map<String, Object> sourceData : sourcePage.getContent()) {
            try {
                AnalysisConvenienceStoreData analysisData = processIndividualData(sourceData);
                if (analysisData != null) {
                    analysisDataList.add(analysisData);
                    successfulProcessed.incrementAndGet();
                } else {
                    failedProcessed.incrementAndGet();
                }
                totalProcessed.incrementAndGet();

            } catch (Exception e) {
                log.error("샘플 데이터 처리 중 오류: {}", e.getMessage());
                failedProcessed.incrementAndGet();
                totalProcessed.incrementAndGet();
            }
        }

        // 샘플 데이터 저장
        if (!analysisDataList.isEmpty()) {
            analysisRepository.saveAll(analysisDataList);
        }

        long endTime = System.currentTimeMillis();
        printFinalResults(sourcePage.getTotalElements(), startTime);

        log.info("샘플 데이터 처리 완료");
    }

    /**
     * 처리 통계 반환 (테스트 또는 모니터링용)
     */
    public ProcessingStatistics getProcessingStatistics() {
        long finalCount = analysisRepository.getTotalCount();
        long coordinateCount = analysisRepository.getCoordinateCount();

        return ProcessingStatistics.builder()
                .totalProcessed(totalProcessed.get())
                .successfulProcessed(successfulProcessed.get())
                .failedProcessed(failedProcessed.get())
                .coordinateSuccess(coordinateSuccess.get())
                .coordinateFailed(coordinateFailed.get())
                .finalDataCount(finalCount)
                .coordinateCount(coordinateCount)
                .build();
    }

    /**
     * 처리 통계 데이터 클래스
     */
    @lombok.Builder
    @lombok.Data
    public static class ProcessingStatistics {
        private long totalProcessed;
        private long successfulProcessed;
        private long failedProcessed;
        private long coordinateSuccess;
        private long coordinateFailed;
        private long finalDataCount;
        private long coordinateCount;
    }
}