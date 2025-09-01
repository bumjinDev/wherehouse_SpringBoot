package com.WhereHouse.AnalysisData.university.processor;

import com.WhereHouse.AnalysisData.university.entity.AnalysisUniversityStatistics;
import com.WhereHouse.AnalysisData.university.repository.AnalysisUniversityRepository;
import com.WhereHouse.AnalysisData.university.service.UniversityCoordinateService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.University.Entity.UniversityStatistics;
import com.WhereHouse.AnalysisStaticData.University.Repository.UniversityStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 대학교 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 UNIVERSITY_STATISTICS 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_UNIVERSITY_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 대학교 통계 데이터 조회 및 검증
 * - CREATED_AT 필드 제외한 모든 대학교 정보 필드 복사
 * - 주소 기반 위도, 경도 좌표 계산 및 추가
 * - 분석용 테이블 데이터 품질 검증
 * - 시도별 대학교 수 및 대학 유형별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UniversityDataProcessor {

    // 원본 대학교 통계 테이블 접근을 위한 Repository
    private final UniversityStatisticsRepository originalUniversityRepository;

    // 분석용 대학교 통계 테이블 접근을 위한 Repository
    private final AnalysisUniversityRepository analysisUniversityRepository;

    // 좌표 계산을 위한 서비스
    private final UniversityCoordinateService coordinateService;

    /**
     * 대학교 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 대학교 데이터 조회 및 검증
     * 3. 데이터 변환 및 좌표 계산 후 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisUniversityData() {
        log.info("=== 대학교 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisUniversityRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 대학교 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 대학교 통계 데이터 조회 및 검증
        List<UniversityStatistics> originalUniversityDataList = originalUniversityRepository.findAll();
        if (originalUniversityDataList.isEmpty()) {
            log.warn("원본 대학교 통계 데이터가 존재하지 않습니다. 먼저 UniversityDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 대학교 통계 데이터 {} 개 발견", originalUniversityDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        for (UniversityStatistics originalUniversityData : originalUniversityDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환 (CREATED_AT 필드 제외)
                AnalysisUniversityStatistics analysisTargetUniversityData = convertToAnalysisEntity(originalUniversityData);

                // 좌표 계산 및 설정
                Double[] coordinates = calculateCoordinatesForUniversity(originalUniversityData);
                if (coordinates != null) {
                    analysisTargetUniversityData.setLatitude(coordinates[0]);
                    analysisTargetUniversityData.setLongitude(coordinates[1]);
                    coordinateCalculationSuccessCount++;
                } else {
                    coordinateCalculationFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisUniversityRepository.save(analysisTargetUniversityData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (유형: {}, 좌표: {}, {})",
                        originalUniversityData.getSchoolName(),
                        originalUniversityData.getUniversityType(),
                        coordinates != null ? coordinates[0] : "없음",
                        coordinates != null ? coordinates[1] : "없음");

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 대학명: {}, 오류: {}",
                        originalUniversityData.getSchoolName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("대학교 데이터 분석용 테이블 생성 작업 완료");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 대학교 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 대학교 통계 엔티티를 분석용 엔티티로 변환
     *
     * CREATED_AT 필드를 제외한 모든 대학교 통계 필드를 복사한다.
     * 좌표 정보는 별도 메서드에서 계산하여 설정한다.
     *
     * @param originalUniversityData 원본 대학교 통계 엔티티
     * @return 분석용 대학교 통계 엔티티
     */
    private AnalysisUniversityStatistics convertToAnalysisEntity(UniversityStatistics originalUniversityData) {
        return AnalysisUniversityStatistics.builder()
                // 기본 정보
                .schoolName(originalUniversityData.getSchoolName())                           // 학교명
                .schoolNameEng(originalUniversityData.getSchoolNameEng())                     // 학교명(영문)
                .mainBranchType(originalUniversityData.getMainBranchType())                   // 본분교구분
                .universityType(originalUniversityData.getUniversityType())                   // 대학구분
                .schoolType(originalUniversityData.getSchoolType())                           // 학교종류
                .establishmentType(originalUniversityData.getEstablishmentType())             // 설립구분

                // 지역 정보
                .sidoCode(originalUniversityData.getSidoCode())                               // 시도코드
                .sidoName(originalUniversityData.getSidoName())                               // 시도명

                // 주소 정보
                .roadAddress(originalUniversityData.getRoadAddress())                         // 도로명주소
                .locationAddress(originalUniversityData.getLocationAddress())                 // 지번주소
                .roadPostalCode(originalUniversityData.getRoadPostalCode())                   // 도로명우편번호
                .locationPostalCode(originalUniversityData.getLocationPostalCode())           // 지번우편번호

                // 연락처 정보
                .homepageUrl(originalUniversityData.getHomepageUrl())                         // 홈페이지주소
                .mainPhone(originalUniversityData.getMainPhone())                             // 대표전화번호
                .mainFax(originalUniversityData.getMainFax())                                 // 팩스번호

                // 일자 정보
                .establishmentDate(originalUniversityData.getEstablishmentDate())             // 설립일자
                .baseYear(originalUniversityData.getBaseYear())                               // 기준연도
                .dataBaseDate(originalUniversityData.getDataBaseDate())                       // 데이터기준일자

                // 제공기관 정보
                .providerCode(originalUniversityData.getProviderCode())                       // 제공기관코드
                .providerName(originalUniversityData.getProviderName())                       // 제공기관명

                // 좌표 정보는 별도 설정 (초기값 null)
                .latitude(null)
                .longitude(null)
                .build();
    }

    /**
     * 대학교 주소 정보 기반 좌표 계산
     *
     * 도로명주소를 우선으로 하고, 없는 경우 지번주소를 활용하여 좌표를 계산한다.
     *
     * @param universityData 원본 대학교 데이터
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] calculateCoordinatesForUniversity(UniversityStatistics universityData) {
        try {
            // 1순위: 도로명주소 기반 좌표 계산
            if (universityData.getRoadAddress() != null && !universityData.getRoadAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromRoadAddress(universityData.getRoadAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            // 2순위: 지번주소 기반 좌표 계산
            if (universityData.getLocationAddress() != null && !universityData.getLocationAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromLocationAddress(universityData.getLocationAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 계산 실패 - 대학명: {}, 주소 정보 부족", universityData.getSchoolName());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 계산 중 오류 발생 - 대학명: {}, 오류: {}",
                    universityData.getSchoolName(), coordinateCalculationException.getMessage());
            return null;
        }
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 시도별 대학교 수 상위 5개 로깅
     * - 대학 유형별 분포 로깅
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisUniversityRepository.count();
            log.info("최종 분석용 대학교 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 시도별 대학교 수 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> universityCountBySidoList = analysisUniversityRepository.findUniversityCountBySido();
            log.info("시도별 대학교 수 순위 (상위 5개 시도):");

            universityCountBySidoList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String sidoName = (String) rankingRow[0];           // 시도명
                        Long universityCount = (Long) rankingRow[1];        // 대학교 수
                        log.info("  {} : {} 개", sidoName, universityCount);
                    });

            // 대학 유형별 분포 조회 및 로깅
            List<Object[]> universityCountByTypeList = analysisUniversityRepository.findUniversityCountByType();
            log.info("대학 유형별 분포:");

            universityCountByTypeList.forEach(typeRow -> {
                String universityType = (String) typeRow[0];        // 대학 유형
                Long typeCount = (Long) typeRow[1];                 // 해당 유형 수
                log.info("  {} : {} 개", universityType, typeCount);
            });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisUniversityRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisUniversityRepository.countMissingCoordinates();

            log.info("좌표 정보 완성도:");
            log.info("  좌표 보유: {} 개 ({:.1f}%)", coordinateCompleteCount,
                    (double) coordinateCompleteCount / finalAnalysisDataCount * 100);
            log.info("  좌표 누락: {} 개 ({:.1f}%)", coordinateMissingCount,
                    (double) coordinateMissingCount / finalAnalysisDataCount * 100);

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}