package com.WhereHouse.AnalysisData.school.processor;

import com.WhereHouse.AnalysisData.school.entity.AnalysisSchoolStatistics;
import com.WhereHouse.AnalysisData.school.repository.AnalysisSchoolRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Entity.SchoolStatistics;
import com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Repository.SchoolStatisticsRepository;
import com.WhereHouse.AnalysisStaticData.SubwayStation.Entity.SubwayStation;
import com.WhereHouse.AnalysisStaticData.SubwayStation.Repository.SubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 초중고등학교 데이터 분석용 테이블 생성 처리 컴포넌트 (서울지역 한정)
 *
 * 기존 SCHOOL_STATISTICS 테이블에서 서울지역 데이터를 조회하여
 * 분석 전용 ANALYSIS_SCHOOL_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 초중고등학교 통계 데이터 중 서울지역만 조회 및 검증
 * - 지정된 13개 필드만 복사 (학교ID, 학교명, 학교급, 설립유형 등)
 * - 기존 위도, 경도 좌표 데이터 그대로 활용
 * - 분석용 테이블 데이터 품질 검증
 * - 학교급별 및 운영상태별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchoolDataProcessor {

    // 원본 초중고등학교 테이블 접근을 위한 Repository
    private final SchoolStatisticsRepository originalSchoolRepository;

    // 분석용 초중고등학교 테이블 접근을 위한 Repository
    private final AnalysisSchoolRepository analysisSchoolRepository;

    /**
     * 초중고등학교 데이터 분석용 테이블 생성 메인 프로세스 (서울지역)
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 초중고등학교 데이터 중 서울지역만 조회 및 검증
     * 3. 데이터 변환 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisSchoolData() {
        log.info("=== 초중고등학교 데이터 분석용 테이블 생성 작업 시작 (서울지역) ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisSchoolRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 초중고등학교 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 초중고등학교 데이터 중 서울지역만 조회 및 검증
        List<SchoolStatistics> seoulSchoolDataList = getSeoulSchoolData();
        if (seoulSchoolDataList.isEmpty()) {
            log.warn("서울지역 초중고등학교 데이터가 존재하지 않습니다. 먼저 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("서울지역 초중고등학교 데이터 {} 개 발견", seoulSchoolDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateAvailableCount = 0;       // 좌표 정보 보유 개수
        int coordinateMissingCount = 0;         // 좌표 정보 누락 개수

        // 처리 진행률 추적
        int processedCount = 0;
        int totalCount = seoulSchoolDataList.size();
        int logInterval = Math.max(1, totalCount / 10); // 10% 간격으로 로그 출력

        for (SchoolStatistics originalSchoolData : seoulSchoolDataList) {
            processedCount++;

            try {
                // 원본 데이터를 분석용 엔티티로 변환 (지정된 13개 필드만)
                AnalysisSchoolStatistics analysisTargetSchoolData = convertToAnalysisEntity(originalSchoolData);

                // 좌표 정보 확인
                if (originalSchoolData.getLatitude() != null && originalSchoolData.getLongitude() != null) {
                    coordinateAvailableCount++;
                } else {
                    coordinateMissingCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisSchoolRepository.save(analysisTargetSchoolData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} ({}, 설립: {}, 운영: {}, 좌표: {}, {})",
                        originalSchoolData.getSchoolName(),
                        originalSchoolData.getSchoolLevel(),
                        originalSchoolData.getEstablishmentType(),
                        originalSchoolData.getOperationStatus(),
                        originalSchoolData.getLatitude() != null ? originalSchoolData.getLatitude() : "없음",
                        originalSchoolData.getLongitude() != null ? originalSchoolData.getLongitude() : "없음");

                // 진행률 로그 (10% 간격)
                if (processedCount % logInterval == 0 || processedCount == totalCount) {
                    double progressPercentage = (double) processedCount / totalCount * 100;
                    log.info("진행률: {:.1f}% 완료 ({}/{})", progressPercentage, processedCount, totalCount);
                }

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 학교명: {}, 오류: {}",
                        originalSchoolData.getSchoolName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("초중고등학교 데이터 분석용 테이블 생성 작업 완료 (서울지역)");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 정보: 보유 {} 개, 누락 {} 개", coordinateAvailableCount, coordinateMissingCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 초중고등학교 데이터 분석용 테이블 생성 작업 종료 (서울지역) ===");
    }

    /**
     * 서울지역 초중고등학교 데이터 조회
     *
     * 교육청명 또는 주소를 기준으로 서울지역 학교만 필터링
     *
     * @return 서울지역 초중고등학교 데이터 목록
     */
    private List<SchoolStatistics> getSeoulSchoolData() {
        // 모든 학교 데이터 조회 후 서울지역만 필터링
        List<SchoolStatistics> allSchools = originalSchoolRepository.findAll();

        return allSchools.stream()
                .filter(this::isSeoulSchool)
                .toList();
    }

    /**
     * 서울지역 학교인지 판단
     *
     * @param school 학교 데이터
     * @return 서울지역 학교 여부
     */
    private boolean isSeoulSchool(SchoolStatistics school) {
        // 1. 교육청명으로 확인
        if (school.getEducationOfficeName() != null &&
                school.getEducationOfficeName().contains("서울")) {
            return true;
        }

        // 2. 소재지주소로 확인
        if (school.getLocationAddress() != null &&
                school.getLocationAddress().startsWith("서울")) {
            return true;
        }

        // 3. 도로명주소로 확인
        if (school.getRoadAddress() != null &&
                school.getRoadAddress().startsWith("서울")) {
            return true;
        }

        return false;
    }

    /**
     * 원본 초중고등학교 엔티티를 분석용 엔티티로 변환
     *
     * 지정된 13개 필드만 복사한다.
     *
     * @param originalSchoolData 원본 초중고등학교 엔티티
     * @return 분석용 초중고등학교 엔티티
     */
    private AnalysisSchoolStatistics convertToAnalysisEntity(SchoolStatistics originalSchoolData) {
        return AnalysisSchoolStatistics.builder()
                // 지정된 13개 필드 복사
                .schoolId(originalSchoolData.getSchoolId())                           // 학교ID
                .schoolName(originalSchoolData.getSchoolName())                       // 학교명
                .schoolLevel(originalSchoolData.getSchoolLevel())                     // 학교급
                .establishmentType(originalSchoolData.getEstablishmentType())         // 설립유형
                .mainBranchType(originalSchoolData.getMainBranchType())               // 본분교구분
                .operationStatus(originalSchoolData.getOperationStatus())             // 운영상태
                .locationAddress(originalSchoolData.getLocationAddress())             // 소재지주소
                .roadAddress(originalSchoolData.getRoadAddress())                     // 도로명주소
                .educationOfficeName(originalSchoolData.getEducationOfficeName())     // 교육청명
                .supportOfficeName(originalSchoolData.getSupportOfficeName())         // 교육지원청명
                .latitude(originalSchoolData.getLatitude())                           // 위도 (기존 데이터 사용)
                .longitude(originalSchoolData.getLongitude())                         // 경도 (기존 데이터 사용)
                .providerName(originalSchoolData.getProviderName())                   // 제공기관명
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 학교급별 분포 상위 5개 로깅
     * - 운영상태별 분포 로깅
     * - 설립유형별 분포 로깅
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisSchoolRepository.count();
            log.info("최종 분석용 초중고등학교 데이터 저장 완료 (서울지역): {} 개", finalAnalysisDataCount);

            // 학교급별 분포 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> schoolCountByLevelList = analysisSchoolRepository.findSchoolCountByLevel();
            log.info("학교급별 분포:");

            schoolCountByLevelList.forEach(levelRow -> {
                String schoolLevel = (String) levelRow[0];       // 학교급
                Long levelCount = (Long) levelRow[1];            // 해당 급 수
                log.info("  {} : {} 개", schoolLevel, levelCount);
            });

            // 운영상태별 분포 조회 및 로깅
            List<Object[]> schoolCountByStatusList = analysisSchoolRepository.findSchoolCountByOperationStatus();
            log.info("운영상태별 분포:");

            schoolCountByStatusList.forEach(statusRow -> {
                String operationStatus = (String) statusRow[0];     // 운영상태
                Long statusCount = (Long) statusRow[1];             // 해당 상태 수
                log.info("  {} : {} 개", operationStatus, statusCount);
            });

            // 설립유형별 분포 조회 및 로깅
            List<Object[]> schoolCountByTypeList = analysisSchoolRepository.findSchoolCountByEstablishmentType();
            log.info("설립유형별 분포:");

            schoolCountByTypeList.forEach(typeRow -> {
                String establishmentType = (String) typeRow[0];     // 설립유형
                Long typeCount = (Long) typeRow[1];                 // 해당 유형 수
                log.info("  {} : {} 개", establishmentType, typeCount);
            });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisSchoolRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisSchoolRepository.countMissingCoordinates();

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