package com.WhereHouse.AnalysisData.danran.processor;

import com.WhereHouse.AnalysisData.danran.entity.AnalysisDanranBars;
import com.WhereHouse.AnalysisData.danran.repository.AnalysisDanranRepository;
import com.WhereHouse.AnalysisData.danran.service.DanranGeocodingService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.DanranPubRoad.Entity.DanranBars;
import com.WhereHouse.AnalysisStaticData.DanranPubRoad.Repository.DanranBarsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 단란주점 데이터 분석용 테이블 생성 및 지오코딩 처리 컴포넌트
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DanranDataProcessor {

    private final DanranBarsRepository originalDanranRepository;
    private final AnalysisDanranRepository analysisDanranRepository;
    private final DanranGeocodingService danranGeocodingService;

    @Value("${apps.analysis.karaoke-rooms.batch-size}")
    private Integer batchSize;

    @Value("${apps.analysis.karaoke-rooms.geocoding-delay}")
    private Integer geocodingDelay;

    private static final Pattern DISTRICT_PATTERN = Pattern.compile("서울특별시\\s+([가-힣]+구)");

    /**
     * 단란주점 데이터 분석용 테이블 생성 메인 프로세스
     */
    @Transactional
    public void processAnalysisDanranData() {
        log.info("=== 단란주점 데이터 분석용 테이블 생성 및 지오코딩 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 존재 여부 확인
        long existingAnalysisDataCount = analysisDanranRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 단란주점 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 단란주점 데이터 조회 및 영업상태 필터링
        List<DanranBars> allDanranDataList = originalDanranRepository.findAll();
        if (allDanranDataList.isEmpty()) {
            log.warn("원본 단란주점 데이터가 존재하지 않습니다.");
            return;
        }

        // 영업 중인 업소만 필터링
        List<DanranBars> activeDanranDataList = allDanranDataList.stream()
                .filter(this::isActiveDanran)
                .collect(Collectors.toList());

        log.info("전체 단란주점 데이터: {} 개, 영업 중인 업소: {} 개",
                allDanranDataList.size(), activeDanranDataList.size());

        // Step 3: 배치 단위 지오코딩 처리
        int totalDataCount = activeDanranDataList.size();
        int processedCount = 0;
        int successfulGeocodingCount = 0;
        int failedGeocodingCount = 0;
        int roadAddressSuccessCount = 0;  // 도로명주소 성공 카운트
        int jibunAddressSuccessCount = 0; // 지번주소 성공 카운트

        log.info("배치 크기 {} 단위로 총 {} 개 단란주점 지오코딩 처리 시작", batchSize, totalDataCount);

        for (int batchStart = 0; batchStart < totalDataCount; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, totalDataCount);
            List<DanranBars> currentBatch = activeDanranDataList.subList(batchStart, batchEnd);

            log.info("배치 처리 중: {}/{} ({}-{} 범위)",
                    batchStart / batchSize + 1, (totalDataCount + batchSize - 1) / batchSize,
                    batchStart + 1, batchEnd);

            for (DanranBars originalDanranData : currentBatch) {
                try {
                    // 원본 데이터를 분석용 엔티티로 변환
                    AnalysisDanranBars analysisTargetDanranData = convertToAnalysisEntity(originalDanranData);

                    // 이중 주소 기반 Kakao API 지오코딩 처리
                    DanranGeocodingService.EnhancedGeocodingResult geocodingResult =
                            danranGeocodingService.getCoordinatesWithDualAddress(
                                    originalDanranData.getJibunAddress(),
                                    originalDanranData.getRoadAddress(),
                                    originalDanranData.getBusinessName()
                            );

                    if (geocodingResult.isSuccess()) {
                        // 지오코딩 성공 시 좌표 정보 설정
                        analysisTargetDanranData.setCoordX(geocodingResult.getLongitude());
                        analysisTargetDanranData.setCoordY(geocodingResult.getLatitude());
                        analysisTargetDanranData.setGeocodingStatus("SUCCESS");
                        analysisTargetDanranData.setGeocodingAddressType(geocodingResult.getAddressType());
                        analysisTargetDanranData.setApiResponseAddress(geocodingResult.getApiResponseAddress());
                        successfulGeocodingCount++;

                        // 주소 타입별 성공 카운트
                        if ("ROAD".equals(geocodingResult.getAddressType())) {
                            roadAddressSuccessCount++;
                        } else if ("JIBUN".equals(geocodingResult.getAddressType())) {
                            jibunAddressSuccessCount++;
                        }

                        log.debug("지오코딩 성공: {} [{}] - 위도: {}, 경도: {}",
                                originalDanranData.getBusinessName(), geocodingResult.getAddressType(),
                                geocodingResult.getLatitude(), geocodingResult.getLongitude());
                    } else {
                        // 지오코딩 실패 시 기본값 설정
                        analysisTargetDanranData.setCoordX(0.0);
                        analysisTargetDanranData.setCoordY(0.0);
                        analysisTargetDanranData.setGeocodingStatus("FAILED");
                        analysisTargetDanranData.setGeocodingAddressType("NONE");
                        analysisTargetDanranData.setApiResponseAddress(geocodingResult.getErrorMessage());
                        failedGeocodingCount++;

                        log.warn("지오코딩 실패: {} - 도로명: {}, 지번: {}, 사유: {}",
                                originalDanranData.getBusinessName(),
                                originalDanranData.getRoadAddress(),
                                originalDanranData.getJibunAddress(),
                                geocodingResult.getErrorMessage());
                    }

                    // 분석용 테이블에 데이터 저장
                    analysisDanranRepository.save(analysisTargetDanranData);
                    processedCount++;

                    // API Rate Limit 준수를 위한 지연 처리
                    if (geocodingDelay > 0) {
                        Thread.sleep(geocodingDelay);
                    }

                } catch (Exception dataConversionException) {
                    log.error("분석용 데이터 생성 실패 - 업소명: {}, 오류: {}",
                            originalDanranData.getBusinessName(), dataConversionException.getMessage());
                    failedGeocodingCount++;
                }
            }

            // 배치별 진행 상황 로깅
            log.info("배치 처리 완료: {} / {} (성공: {}, 실패: {})",
                    processedCount, totalDataCount, successfulGeocodingCount, failedGeocodingCount);
        }

        // Step 4: 지오코딩 처리 결과 로깅
        log.info("단란주점 데이터 분석용 테이블 생성 및 지오코딩 작업 완료");
        log.info("총 처리: {} 개, 지오코딩 성공: {} 개, 실패: {} 개",
                processedCount, successfulGeocodingCount, failedGeocodingCount);
        log.info("지오코딩 성공률: {:.2f}%",
                totalDataCount > 0 ? (double) successfulGeocodingCount / totalDataCount * 100 : 0);
        log.info("주소 타입별 성공률 - 도로명주소: {} 개, 지번주소: {} 개",
                roadAddressSuccessCount, jibunAddressSuccessCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 단란주점 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 영업 중인 단란주점인지 확인
     */
    private boolean isActiveDanran(DanranBars danranBar) {
        String statusName = danranBar.getBusinessStatusName();
        if (statusName == null) return false;

        // 영업 중인 상태로 판단되는 키워드들
        return statusName.contains("정상") ||
                statusName.contains("영업") ||
                statusName.contains("운영") ||
                "1".equals(danranBar.getBusinessStatusCode());
    }

    /**
     * 원본 단란주점 엔티티를 분석용 엔티티로 변환
     */
    private AnalysisDanranBars convertToAnalysisEntity(DanranBars originalDanranData) {
        // 주소에서 구별 정보 추출 (도로명주소 우선, 없으면 지번주소 사용)
        String addressForParsing = originalDanranData.getRoadAddress();
        if (addressForParsing == null || "데이터없음".equals(addressForParsing)) {
            addressForParsing = originalDanranData.getJibunAddress();
        }
        String districtName = extractDistrictFromAddress(addressForParsing);

        return AnalysisDanranBars.builder()
                // 필수 정보만 선별 복사
                .districtCode(originalDanranData.getDistrictCode())
                .managementNumber(originalDanranData.getManagementNumber())
                .businessStatusName(originalDanranData.getBusinessStatusName())
                .phoneNumber(originalDanranData.getPhoneNumber())
                .jibunAddress(originalDanranData.getJibunAddress())
                .roadAddress(originalDanranData.getRoadAddress())
                .businessName(originalDanranData.getBusinessName())

                // 분석용 추가 정보
                .districtName(districtName)
                .geocodingStatus("PENDING")

                // 좌표 정보는 지오코딩 처리 후 별도 설정
                .coordX(0.0)
                .coordY(0.0)
                .build();
    }

    /**
     * 주소에서 서울시 구별 정보 추출
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null || address.trim().isEmpty() || "데이터없음".equals(address)) {
            return "구정보없음";
        }

        // 1차: 정규식을 통한 정확한 패턴 매칭
        Matcher matcher = DISTRICT_PATTERN.matcher(address);
        if (matcher.find()) {
            String districtName = matcher.group(1);
            log.debug("구별 정보 추출 성공: {} -> {}", address, districtName);
            return districtName;
        }

        // 2차: "구" 단위 정보 직접 검색
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
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisDanranRepository.count();
            log.info("최종 분석용 단란주점 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 지오코딩 성공률 통계 출력
            List<Object[]> geocodingStatsList = analysisDanranRepository.getGeocodingStatistics();
            log.info("지오코딩 처리 결과 통계:");
            geocodingStatsList.forEach(statRow -> {
                String status = (String) statRow[0];
                Long count = (Long) statRow[1];
                double percentage = finalAnalysisDataCount > 0 ? (double) count / finalAnalysisDataCount * 100 : 0;
                log.info("  {} : {} 개 ({:.1f}%)", status, count, percentage);
            });

            // 주소 타입별 지오코딩 성공률 통계
            List<Object[]> addressTypeStatsList = analysisDanranRepository.getGeocodingStatisticsByAddressType();
            log.info("주소 타입별 지오코딩 성공률:");
            addressTypeStatsList.forEach(statRow -> {
                String addressType = (String) statRow[0];
                String status = (String) statRow[1];
                Long count = (Long) statRow[2];
                log.info("  {} - {} : {} 개", addressType, status, count);
            });

            // 구별 단란주점 밀도 순위 조회 및 로깅
            List<Object[]> districtDanranRankingList = analysisDanranRepository.countDanranBarsByDistrict();
            log.info("서울시 구별 단란주점 밀도 순위 (상위 10개구):");

            districtDanranRankingList.stream()
                    .limit(10)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];
                        Long danranCount = (Long) rankingRow[1];
                        log.info("  {} : {} 개소", districtName, danranCount);
                    });

            // 좌표 유효성 검증
            List<AnalysisDanranBars> validCoordsList = analysisDanranRepository.findValidCoordinates();
            log.info("유효한 좌표를 가진 단란주점: {} 개", validCoordsList.size());

            // 지오코딩 실패 데이터 분석
            List<AnalysisDanranBars> failedGeocodingList = analysisDanranRepository.findFailedGeocodingData();
            if (!failedGeocodingList.isEmpty()) {
                log.warn("지오코딩 실패 데이터 {} 개 발견:", failedGeocodingList.size());
                failedGeocodingList.stream()
                        .limit(5)
                        .forEach(failedData -> {
                            log.warn("  실패 업소: {} (도로명: {}, 지번: {}, 사유: {})",
                                    failedData.getBusinessName(),
                                    failedData.getRoadAddress(),
                                    failedData.getJibunAddress(),
                                    failedData.getApiResponseAddress());
                        });
            }

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}