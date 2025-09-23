package com.WhereHouse.AnalysisData.police.processor;

import com.WhereHouse.AnalysisData.police.entity.AnalysisPoliceFacility;
import com.WhereHouse.AnalysisData.police.repository.AnalysisPoliceRepository;
import com.WhereHouse.AnalysisData.police.service.KakaoGeocodingService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.Police.Entity.PoliceFacility;
import com.WhereHouse.AnalysisStaticData.Police.Repository.PoliceFacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 경찰시설 데이터 분석용 테이블 생성 및 지오코딩 처리 컴포넌트
 *
 * 기존 POLICE_FACILITY 테이블에서 데이터를 조회하여
 * Kakao API를 통한 정확한 좌표 계산 후 분석 전용 ANALYSIS_POLICE_FACILITY 테이블로 처리하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 경찰시설 데이터 조회 및 검증
 * - Kakao 지오코딩 API를 통한 정확한 위도/경도 계산
 * - 주소 파싱을 통한 구별 정보 추출
 * - 배치 처리를 통한 API Rate Limit 준수
 * - 지오코딩 성공률 통계 및 품질 검증
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PoliceDataProcessor {

    // 원본 경찰시설 테이블 접근을 위한 Repository
    private final PoliceFacilityRepository originalPoliceRepository;

    // 분석용 경찰시설 테이블 접근을 위한 Repository
    private final AnalysisPoliceRepository analysisPoliceRepository;

    // Kakao 지오코딩 API 서비스
    private final KakaoGeocodingService geocodingService;

    @Value("${apps.analysis.police-facility.batch-size}")
    private Integer batchSize;

    @Value("${apps.analysis.police-facility.geocoding-delay}")
    private Integer geocodingDelay;

    // 서울시 구 이름 추출을 위한 정규식 패턴
    private static final Pattern DISTRICT_PATTERN = Pattern.compile("서울특별시\\s+([가-힣]+구)");

    /**
     * 경찰시설 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 경찰시설 데이터 조회 및 검증
     * 3. 배치 단위로 지오코딩 처리 및 분석용 테이블 저장
     * 4. 지오코딩 성공률 통계 및 결과 검증
     */
    @Transactional
    public void processAnalysisPoliceData() {
        log.info("=== 경찰시설 데이터 분석용 테이블 생성 및 지오코딩 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisPoliceRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 경찰시설 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 경찰시설 데이터 조회 및 검증
        List<PoliceFacility> originalPoliceDataList = originalPoliceRepository.findAll();
        if (originalPoliceDataList.isEmpty()) {
            log.warn("원본 경찰시설 데이터가 존재하지 않습니다. 먼저 PoliceFacilityDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 경찰시설 데이터 {} 개 발견", originalPoliceDataList.size());

        // Step 3: 배치 단위 지오코딩 처리
        int totalDataCount = originalPoliceDataList.size();
        int processedCount = 0;
        int successfulGeocodingCount = 0;
        int failedGeocodingCount = 0;

        log.info("배치 크기 {} 단위로 총 {} 개 경찰시설 지오코딩 처리 시작", batchSize, totalDataCount);

        for (int batchStart = 0; batchStart < totalDataCount; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, totalDataCount);
            List<PoliceFacility> currentBatch = originalPoliceDataList.subList(batchStart, batchEnd);

            log.info("배치 처리 중: {}/{} ({}-{} 범위)",
                    batchStart / batchSize + 1, (totalDataCount + batchSize - 1) / batchSize,
                    batchStart + 1, batchEnd);

            for (PoliceFacility originalPoliceData : currentBatch) {
                try {
                    // 서울 주소가 아니면 스킵
                    if (!originalPoliceData.getAddress().contains("서울")) {
                        log.debug("서울이 아닌 주소 스킵: {} - {}",
                                originalPoliceData.getFacilityName(),
                                originalPoliceData.getAddress());
                        continue;
                    }

                    // 원본 데이터를 분석용 엔티티로 변환 (좌표 제외)
                    AnalysisPoliceFacility analysisTargetPoliceData = convertToAnalysisEntity(originalPoliceData);

                    // Kakao API를 통한 지오코딩 처리
                    KakaoGeocodingService.GeocodingResult geocodingResult =
                            geocodingService.getCoordinates(originalPoliceData.getAddress());

                    if (geocodingResult.isSuccess()) {
                        // 지오코딩 성공 시 좌표 정보 설정
                        analysisTargetPoliceData.setCoordX(geocodingResult.getLongitude());
                        analysisTargetPoliceData.setCoordY(geocodingResult.getLatitude());
                        analysisTargetPoliceData.setGeocodingStatus("SUCCESS");
                        analysisTargetPoliceData.setApiResponseAddress(geocodingResult.getApiResponseAddress());
                        successfulGeocodingCount++;

                        log.debug("지오코딩 성공: {} - 위도: {}, 경도: {}",
                                originalPoliceData.getFacilityName(),
                                geocodingResult.getLatitude(), geocodingResult.getLongitude());
                    } else {
                        // 지오코딩 실패 시 기본값 설정
                        analysisTargetPoliceData.setCoordX(0.0);
                        analysisTargetPoliceData.setCoordY(0.0);
                        analysisTargetPoliceData.setGeocodingStatus("FAILED");
                        analysisTargetPoliceData.setApiResponseAddress(geocodingResult.getErrorMessage());
                        failedGeocodingCount++;

                        log.warn("지오코딩 실패: {} - 주소: {}, 사유: {}",
                                originalPoliceData.getFacilityName(),
                                originalPoliceData.getAddress(),
                                geocodingResult.getErrorMessage());
                    }

                    // 분석용 테이블에 데이터 저장 (서울 주소만 여기까지 도달)
                    analysisPoliceRepository.save(analysisTargetPoliceData);
                    processedCount++;

                    // API Rate Limit 준수를 위한 지연 처리
                    if (geocodingDelay > 0) {
                        Thread.sleep(geocodingDelay);
                    }

                } catch (Exception dataConversionException) {
                    log.error("분석용 데이터 생성 실패 - 시설명: {}, 오류: {}",
                            originalPoliceData.getFacilityName(), dataConversionException.getMessage());
                    failedGeocodingCount++;
                }
            }

            // 배치별 진행 상황 로깅
            log.info("배치 처리 완료: {} / {} (성공: {}, 실패: {})",
                    processedCount, totalDataCount, successfulGeocodingCount, failedGeocodingCount);
        }

        // Step 4: 지오코딩 처리 결과 로깅
        log.info("경찰시설 데이터 분석용 테이블 생성 및 지오코딩 작업 완료");
        log.info("총 처리: {} 개, 지오코딩 성공: {} 개, 실패: {} 개",
                processedCount, successfulGeocodingCount, failedGeocodingCount);
        log.info("지오코딩 성공률: {:.2f}%",
                totalDataCount > 0 ? (double) successfulGeocodingCount / totalDataCount * 100 : 0);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 경찰시설 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 경찰시설 엔티티를 분석용 엔티티로 변환
     *
     * CREATED_AT 필드를 제외한 모든 경찰시설 정보를 복사하고,
     * 주소에서 구별 정보를 파싱하여 추가한다.
     *
     * @param originalPoliceData 원본 경찰시설 엔티티
     * @return 분석용 경찰시설 엔티티 (좌표 정보는 추후 지오코딩으로 설정)
     */
    private AnalysisPoliceFacility convertToAnalysisEntity(PoliceFacility originalPoliceData) {
        // 주소에서 구별 정보 추출
        String districtName = extractDistrictFromAddress(originalPoliceData.getAddress());

        return AnalysisPoliceFacility.builder()
                // 기본 정보 복사
                .serialNo(originalPoliceData.getSerialNo())               // 일련번호
                .cityProvince(originalPoliceData.getCityProvince())       // 시도청
                .policeStation(originalPoliceData.getPoliceStation())     // 경찰서
                .facilityName(originalPoliceData.getFacilityName())       // 시설명
                .facilityType(originalPoliceData.getFacilityType())       // 시설 유형
                .phoneNumber(originalPoliceData.getPhoneNumber())         // 전화번호
                .address(originalPoliceData.getAddress())                 // 주소

                // 분석용 추가 정보
                .districtName(districtName)                               // 구별 정보 (주소에서 파싱)
                .geocodingStatus("PENDING")                               // 초기 상태

                // 좌표 정보는 지오코딩 처리 후 별도 설정
                .coordX(0.0)  // 임시값 (지오코딩으로 업데이트 예정)
                .coordY(0.0)  // 임시값 (지오코딩으로 업데이트 예정)
                .build();
    }

    /**
     * 주소에서 서울시 구별 정보 추출
     *
     * 정규식을 사용하여 "서울특별시 XX구" 패턴에서 구 이름을 추출한다.
     * 추출 실패 시 전체 주소에서 "구" 단위 정보를 찾아 반환한다.
     *
     * @param address 전체 주소
     * @return 구별 이름 (예: "중구", "강남구") 또는 "구정보없음"
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "구정보없음";
        }

        // 1차: 정규식을 통한 정확한 패턴 매칭
        Matcher matcher = DISTRICT_PATTERN.matcher(address);
        if (matcher.find()) {
            String districtName = matcher.group(1);  // "중구", "강남구" 등
            log.debug("구별 정보 추출 성공: {} -> {}", address, districtName);
            return districtName;
        }

        // 2차: "구" 단위 정보 직접 검색 (백업 로직)
        String[] addressParts = address.split("\\s+");
        for (String part : addressParts) {
            if (part.endsWith("구") && part.length() >= 2) {
                log.debug("구별 정보 백업 추출: {} -> {}", address, part);
                return part;
            }
        }

        log.warn("구별 정보 추출 실패: {}", address);
        return "구정보없음";
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 및 지오코딩 성공률 확인
     * - 구별 경찰시설 밀도 순위 상위 10개 로깅
     * - 좌표 유효성 검증 및 이상값 탐지
     * - 지오코딩 실패 데이터 상세 분석
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisPoliceRepository.count();
            log.info("최종 분석용 경찰시설 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 지오코딩 성공률 통계 출력
            List<Object[]> geocodingStatsList = analysisPoliceRepository.getGeocodingStatistics();
            log.info("지오코딩 처리 결과 통계:");
            geocodingStatsList.forEach(statRow -> {
                String status = (String) statRow[0];
                Long count = (Long) statRow[1];
                double percentage = finalAnalysisDataCount > 0 ? (double) count / finalAnalysisDataCount * 100 : 0;
                log.info("  {} : {} 개 ({:.1f}%)", status, count, percentage);
            });

            // 구별 경찰시설 밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtPoliceRankingList = analysisPoliceRepository.countFacilitiesByDistrict();
            log.info("서울시 구별 경찰시설 밀도 순위 (상위 10개구):");

            districtPoliceRankingList.stream()
                    .limit(10)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];      // 구 이름
                        Long facilityCount = (Long) rankingRow[1];         // 경찰시설 개수
                        log.info("  {} : {} 개소", districtName, facilityCount);
                    });

            // 좌표 유효성 검증
            List<AnalysisPoliceFacility> validCoordsList = analysisPoliceRepository.findValidCoordinates();
            log.info("유효한 좌표를 가진 경찰시설: {} 개", validCoordsList.size());

            // 지오코딩 실패 데이터 분석
            List<AnalysisPoliceFacility> failedGeocodingList = analysisPoliceRepository.findFailedGeocodingData();
            if (!failedGeocodingList.isEmpty()) {
                log.warn("지오코딩 실패 데이터 {} 개 발견:", failedGeocodingList.size());
                failedGeocodingList.stream()
                        .limit(5)  // 상위 5개만 로깅
                        .forEach(failedData -> {
                            log.warn("  실패 시설: {} (주소: {}, 사유: {})",
                                    failedData.getFacilityName(),
                                    failedData.getAddress(),
                                    failedData.getApiResponseAddress());
                        });
            }

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}