package com.WhereHouse.AnalysisData.hospital.processor;

import com.WhereHouse.AnalysisData.hospital.entity.AnalysisHospitalStatistics;
import com.WhereHouse.AnalysisData.hospital.repository.AnalysisHospitalRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.HospitalSave.Entitiy.HospitalInfo;
import com.WhereHouse.AnalysisStaticData.HospitalSave.Repository.HospitalInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 병원 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 HOSPITAL_INFO 테이블에서 데이터를 조회하여
 * 분석에 필요한 7개 컬럼만 선별하여
 * 분석 전용 ANALYSIS_HOSPITAL_STATISTICS 테이블로 저장하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 병원 데이터 조회 및 검증
 * - 필수 컬럼만 선별하여 직관적인 컬럼명으로 변경
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 병원 개수 통계 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalDataProcessor {

    // 원본 병원 테이블 접근을 위한 Repository
    private final HospitalInfoRepository originalHospitalRepository;

    // 분석용 병원 테이블 접근을 위한 Repository
    private final AnalysisHospitalRepository analysisHospitalRepository;

    /**
     * 병원 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 병원 데이터 조회 및 검증
     * 3. 필요한 7개 컬럼만 선별하여 변환
     * 4. 분석용 테이블에 저장
     * 5. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisHospitalData() {
        log.info("=== 병원 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisHospitalRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 병원 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 병원 데이터 조회 및 검증
        List<HospitalInfo> originalHospitalDataList = originalHospitalRepository.findAll();
        if (originalHospitalDataList.isEmpty()) {
            log.warn("원본 병원 데이터가 존재하지 않습니다. 먼저 HospitalDataLoader를 통해 API 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 병원 데이터 {} 개 발견", originalHospitalDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수

        for (HospitalInfo originalHospitalData : originalHospitalDataList) {
            try {
                // 진행률 출력 (100개마다)
                if ((successfulConversionCount + failedConversionCount) % 100 == 0) {
                    double progress = ((double)(successfulConversionCount + failedConversionCount) / originalHospitalDataList.size()) * 100;
                    log.info("진행률: {:.1f}% ({}/{})", progress,
                            successfulConversionCount + failedConversionCount, originalHospitalDataList.size());
                }

                // 원본 데이터를 분석용 엔티티로 변환 (7개 컬럼만 선별)
                AnalysisHospitalStatistics analysisTargetHospitalData = convertToAnalysisEntity(originalHospitalData);

                // 분석용 테이블에 데이터 저장
                analysisHospitalRepository.save(analysisTargetHospitalData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (구: {}, 종별: {})",
                        originalHospitalData.getYadmNm(),
                        analysisTargetHospitalData.getDistrictName(),
                        analysisTargetHospitalData.getHospitalType());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 병원명: {}, 오류: {}",
                        originalHospitalData.getYadmNm(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("병원 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개",
                successfulConversionCount, failedConversionCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 병원 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 병원 데이터에서 필요한 7개 컬럼만 선별하여 분석용 엔티티로 변환
     *
     * 선별 컬럼:
     * - YADM_NM → HOSPITAL_NAME (병원명)
     * - CL_CD_NM → HOSPITAL_TYPE (종별명: 종합병원 등)
     * - SIDO_CD_NM → SIDO_NAME (시도명: 서울)
     * - SGGU_CD_NM → DISTRICT_NAME (시군구명: 강남구, 종로구 등)
     * - ADDR → ADDRESS (주소)
     * - X_POS → LONGITUDE (경도)
     * - Y_POS → LATITUDE (위도)
     *
     * @param originalHospitalData 원본 병원 데이터 엔티티
     * @return 필요한 컬럼만 포함된 분석용 병원 엔티티
     */
    private AnalysisHospitalStatistics convertToAnalysisEntity(HospitalInfo originalHospitalData) {
        return AnalysisHospitalStatistics.builder()
                .hospitalName(originalHospitalData.getYadmNm())           // 병원명
                .hospitalType(originalHospitalData.getClCdNm())           // 종별명 (종합병원 등)
                .sidoName(originalHospitalData.getSidoCdNm())             // 시도명 (서울)
                .districtName(originalHospitalData.getSgguCdNm())         // 시군구명 (강남구 등)
                .address(originalHospitalData.getAddr())                  // 주소
                .longitude(originalHospitalData.getXPos())                // 경도
                .latitude(originalHospitalData.getYPos())                 // 위도
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 병원 개수 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisHospitalRepository.count();
            log.info("최종 분석용 병원 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 구별 병원 개수 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtHospitalCountList = analysisHospitalRepository.findHospitalCountByDistrict();
            log.info("서울시 구별 병원 개수 순위 (상위 5개구):");

            districtHospitalCountList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];    // 구 이름
                        Long hospitalCount = (Long) rankingRow[1];       // 병원 개수
                        log.info("  {} : {} 개", districtName, hospitalCount);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}