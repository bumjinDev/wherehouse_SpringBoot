package com.WhereHouse.AnalysisData.cctv.processor;

import com.WhereHouse.AnalysisData.cctv.entity.AnalysisCctvStatistics;
import com.WhereHouse.AnalysisData.cctv.repository.AnalysisCctvRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Entity.CctvStatistics;
import com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Repository.CctvStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CCTV 위치 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 CCTV_STATISTICS 테이블에서 6개 핵심 필드만 선별하여
 * 분석 전용 ANALYSIS_CCTV_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 CCTV 위치 데이터 조회 및 검증
 * - 6개 핵심 필드(id, management_agency, road_address, jibun_address, wgs84_latitude, wgs84_longitude) 복사
 * - null 값을 적절한 기본값으로 변환 처리
 * - 분석용 테이블 데이터 품질 검증
 * - 관리기관별 및 구별 CCTV 밀도 순위 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CctvDataProcessor {

    // 원본 CCTV 위치 테이블 접근을 위한 Repository
    private final CctvStatisticsRepository originalCctvRepository;

    // 분석용 CCTV 위치 테이블 접근을 위한 Repository
    private final AnalysisCctvRepository analysisCctvRepository;

    /**
     * CCTV 위치 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 CCTV 위치 데이터 조회 및 검증
     * 3. 6개 핵심 필드 선별 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisCctvData() {
        log.info("=== CCTV 위치 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisCctvRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 CCTV 위치 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 CCTV 위치 데이터 조회 및 검증
        List<CctvStatistics> originalCctvDataList = originalCctvRepository.findAll();
        if (originalCctvDataList.isEmpty()) {
            log.warn("원본 CCTV 위치 데이터가 존재하지 않습니다. 먼저 CctvDataLoader를 통해 CSV 데이터를 수집해주세요.");
            return;
        }

        log.info("원본 CCTV 위치 데이터 {} 개 지점 발견", originalCctvDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수

        for (CctvStatistics originalCctvData : originalCctvDataList) {
            try {
                // 원본 데이터에서 6개 핵심 필드만 선별하여 분석용 엔티티로 변환
                AnalysisCctvStatistics analysisTargetCctvData = convertToAnalysisEntity(originalCctvData);

                // 분석용 테이블에 데이터 저장
                analysisCctvRepository.save(analysisTargetCctvData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (관리기관: {}, 좌표: {}, {})",
                        originalCctvData.getRoadAddress(),
                        originalCctvData.getManagementAgency(),
                        originalCctvData.getWgs84Latitude(),
                        originalCctvData.getWgs84Longitude());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - CCTV: {} (ID: {}), 오류: {}",
                        originalCctvData.getRoadAddress(), originalCctvData.getId(),
                        dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("CCTV 위치 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개",
                successfulConversionCount, failedConversionCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== CCTV 위치 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 CCTV 위치 엔티티를 분석용 엔티티로 변환
     *
     * 6개 핵심 필드(id, management_agency, road_address, jibun_address, wgs84_latitude, wgs84_longitude)만 복사한다.
     * null 값은 적절한 기본값으로 변환 처리한다.
     *
     * @param originalCctvData 원본 CCTV 위치 엔티티
     * @return 분석용 CCTV 위치 엔티티
     */
    private AnalysisCctvStatistics convertToAnalysisEntity(CctvStatistics originalCctvData) {
        return AnalysisCctvStatistics.builder()
                // 관리 정보
                .managementAgency(handleNullString(originalCctvData.getManagementAgency()))       // 관리기관

                // 주소 정보
                .roadAddress(handleNullString(originalCctvData.getRoadAddress()))                 // 도로명주소
                .jibunAddress(handleNullString(originalCctvData.getJibunAddress()))               // 지번주소

                // 좌표 정보 (피어슨 상관분석 핵심 데이터)
                .latitude(handleNullDouble(originalCctvData.getWgs84Latitude()))                  // 위도
                .longitude(handleNullDouble(originalCctvData.getWgs84Longitude()))                // 경도
                .build();
    }

    /**
     * 문자열 null 값 처리 - null이면 "데이터없음"으로 변환
     *
     * @param value 원본 문자열 값
     * @return null이면 "데이터없음", 아니면 원본 값
     */
    private String handleNullString(String value) {
        return value != null ? value : "데이터없음";
    }

    /**
     * Double null 값 처리 - null이면 0.0으로 변환하고 BigDecimal로 변환
     *
     * @param value 원본 Double 값
     * @return null이면 BigDecimal.ZERO, 아니면 BigDecimal로 변환된 값
     */
    private BigDecimal handleNullDouble(Double value) {
        return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 관리기관별 CCTV 밀도 순위 상위 5개 로깅
     * - 구별 CCTV 밀도 순위 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisCctvRepository.count();
            log.info("최종 분석용 CCTV 위치 데이터 저장 완료: {} 개 지점", finalAnalysisDataCount);

            // 관리기관별 CCTV 밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> agencyCctvDensityRankingList = analysisCctvRepository.findAgencyCctvDensityRanking();
            log.info("관리기관별 CCTV 밀도 순위 (상위 5개 기관):");

            agencyCctvDensityRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String agencyName = (String) rankingRow[0];           // 관리기관 이름
                        Long totalCctvCount = (Long) rankingRow[1];           // 총 CCTV 개수
                        log.info("  {} : {} 개 지점", agencyName, totalCctvCount);
                    });

            // 구별 CCTV 밀도 순위 조회 및 로깅
            List<Object[]> districtCctvDensityRankingList = analysisCctvRepository.findDistrictCctvDensityRanking();
            log.info("서울시 구별 CCTV 밀도 순위 (상위 5개구):");

            districtCctvDensityRankingList.stream()
                    .limit(5)
                    .forEach(districtRow -> {
                        String districtName = (String) districtRow[0];        // 구 이름
                        Number cctvCount = (Number) districtRow[1];           // CCTV 개수
                        log.info("  {} : {} 개 지점", districtName, cctvCount);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}