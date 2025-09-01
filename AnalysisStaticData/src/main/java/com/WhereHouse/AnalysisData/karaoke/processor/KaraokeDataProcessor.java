package com.WhereHouse.AnalysisData.karaoke.component;

import com.WhereHouse.AnalysisData.karaoke.entity.AnalysisKaraokeRooms;
import com.WhereHouse.AnalysisData.karaoke.repository.AnalysisKaraokeRepository;
import com.WhereHouse.AnalysisData.karaoke.service.KaraokeGeocodingService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.KaraokeRooms.Entity.KaraokeRooms;
import com.WhereHouse.AnalysisStaticData.KaraokeRooms.Repository.KaraokeRoomsRepository;
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
 * 노래연습장 데이터 분석용 테이블 생성 및 지오코딩 처리 컴포넌트
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KaraokeDataProcessor {

    private final KaraokeRoomsRepository originalKaraokeRepository;
    private final AnalysisKaraokeRepository analysisKaraokeRepository;
    private final KaraokeGeocodingService karaokeGeocodingService;

    @Value("${app.analysis.karaoke-rooms.batch-size}")
    private Integer batchSize;

    @Value("${app.analysis.karaoke-rooms.geocoding-delay}")
    private Integer geocodingDelay;

    private static final Pattern DISTRICT_PATTERN = Pattern.compile("서울특별시\\s+([가-힣]+구)");

    /**
     * 노래연습장 데이터 분석용 테이블 생성 메인 프로세스
     */
    @Transactional
    public void processAnalysisKaraokeData() {
        log.info("=== 노래연습장 데이터 분석용 테이블 생성 및 지오코딩 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 존재 여부 확인
        long existingAnalysisDataCount = analysisKaraokeRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 노래연습장 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 노래연습장 데이터 조회 및 영업상태 필터링
        List<KaraokeRooms> allKaraokeDataList = originalKaraokeRepository.findAll();
        if (allKaraokeDataList.isEmpty()) {
            log.warn("원본 노래연습장 데이터가 존재하지 않습니다.");
            return;
        }

        // 영업 중인 업소만 필터링
        List<KaraokeRooms> activeKaraokeDataList = allKaraokeDataList.stream()
                .filter(this::isActiveKaraoke)
                .collect(Collectors.toList());

        log.info("전체 노래연습장 데이터: {} 개, 영업 중인 업소: {} 개",
                allKaraokeDataList.size(), activeKaraokeDataList.size());

        // Step 3: 배치 단위 지오코딩 처리
        int totalDataCount = activeKaraokeDataList.size();
        int processedCount = 0;
        int successfulGeocodingCount = 0;
        int failedGeocodingCount = 0;
        int roadAddressSuccessCount = 0;  // 도로명주소 성공 카운트
        int jibunAddressSuccessCount = 0; // 지번주소 성공 카운트

        log.info("배치 크기 {} 단위로 총 {} 개 노래연습장 지오코딩 처리 시작", batchSize, totalDataCount);

        for (int batchStart = 0; batchStart < totalDataCount; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, totalDataCount);
            List<KaraokeRooms> currentBatch = activeKaraokeDataList.subList(batchStart, batchEnd);

            log.info("배치 처리 중: {}/{} ({}-{} 범위)",
                    batchStart / batchSize + 1, (totalDataCount + batchSize - 1) / batchSize,
                    batchStart + 1, batchEnd);

            for (KaraokeRooms originalKaraokeData : currentBatch) {
                try {
                    // 원본 데이터를 분석용 엔티티로 변환
                    AnalysisKaraokeRooms analysisTargetKaraokeData = convertToAnalysisEntity(originalKaraokeData);

                    // 이중 주소 기반 Kakao API 지오코딩 처리
                    KaraokeGeocodingService.EnhancedGeocodingResult geocodingResult =
                            karaokeGeocodingService.getCoordinatesWithDualAddress(
                                    originalKaraokeData.getJibunAddress(),
                                    originalKaraokeData.getRoadAddress(),
                                    originalKaraokeData.getBusinessName()
                            );

                    if (geocodingResult.isSuccess()) {
                        // 지오코딩 성공 시 좌표 정보 설정
                        analysisTargetKaraokeData.setCoordX(geocodingResult.getLongitude());
                        analysisTargetKaraokeData.setCoordY(geocodingResult.getLatitude());
                        analysisTargetKaraokeData.setGeocodingStatus("SUCCESS");
                        analysisTargetKaraokeData.setGeocodingAddressType(geocodingResult.getAddressType());
                        analysisTargetKaraokeData.setApiResponseAddress(geocodingResult.getApiResponseAddress());
                        successfulGeocodingCount++;

                        // 주소 타입별 성공 카운트
                        if ("ROAD".equals(geocodingResult.getAddressType())) {
                            roadAddressSuccessCount++;
                        } else if ("JIBUN".equals(geocodingResult.getAddressType())) {
                            jibunAddressSuccessCount++;
                        }

                        log.debug("지오코딩 성공: {} [{}] - 위도: {}, 경도: {}",
                                originalKaraokeData.getBusinessName(), geocodingResult.getAddressType(),
                                geocodingResult.getLatitude(), geocodingResult.getLongitude());
                    } else {
                        // 지오코딩 실패 시 기본값 설정
                        analysisTargetKaraokeData.setCoordX(0.0);
                        analysisTargetKaraokeData.setCoordY(0.0);
                        analysisTargetKaraokeData.setGeocodingStatus("FAILED");
                        analysisTargetKaraokeData.setGeocodingAddressType("NONE");
                        analysisTargetKaraokeData.setApiResponseAddress(geocodingResult.getErrorMessage());
                        failedGeocodingCount++;

                        log.warn("지오코딩 실패: {} - 도로명: {}, 지번: {}, 사유: {}",
                                originalKaraokeData.getBusinessName(),
                                originalKaraokeData.getRoadAddress(),
                                originalKaraokeData.getJibunAddress(),
                                geocodingResult.getErrorMessage());
                    }

                    // 분석용 테이블에 데이터 저장
                    analysisKaraokeRepository.save(analysisTargetKaraokeData);
                    processedCount++;

                    // API Rate Limit 준수를 위한 지연 처리
                    if (geocodingDelay > 0) {
                        Thread.sleep(geocodingDelay);
                    }

                } catch (Exception dataConversionException) {
                    log.error("분석용 데이터 생성 실패 - 업소명: {}, 오류: {}",
                            originalKaraokeData.getBusinessName(), dataConversionException.getMessage());
                    failedGeocodingCount++;
                }
            }

            // 배치별 진행 상황 로깅
            log.info("배치 처리 완료: {} / {} (성공: {}, 실패: {})",
                    processedCount, totalDataCount, successfulGeocodingCount, failedGeocodingCount);
        }

        // Step 4: 지오코딩 처리 결과 로깅
        log.info("노래연습장 데이터 분석용 테이블 생성 및 지오코딩 작업 완료");
        log.info("총 처리: {} 개, 지오코딩 성공: {} 개, 실패: {} 개",
                processedCount, successfulGeocodingCount, failedGeocodingCount);
        log.info("지오코딩 성공률: {:.2f}%",
                totalDataCount > 0 ? (double) successfulGeocodingCount / totalDataCount * 100 : 0);
        log.info("주소 타입별 성공률 - 도로명주소: {} 개, 지번주소: {} 개",
                roadAddressSuccessCount, jibunAddressSuccessCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 노래연습장 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 영업 중인 노래연습장인지 확인
     */
    private boolean isActiveKaraoke(KaraokeRooms karaokeRoom) {
        String statusName = karaokeRoom.getBusinessStatusName();
        if (statusName == null) return false;

        // 영업 중인 상태로 판단되는 키워드들
        return statusName.contains("정상") ||
                statusName.contains("영업") ||
                statusName.contains("운영") ||
                "1".equals(karaokeRoom.getBusinessStatusCode());
    }

    /**
     * 원본 노래연습장 엔티티를 분석용 엔티티로 변환
     */
    private AnalysisKaraokeRooms convertToAnalysisEntity(KaraokeRooms originalKaraokeData) {
        // 주소에서 구별 정보 추출 (도로명주소 우선, 없으면 지번주소 사용)
        String addressForParsing = originalKaraokeData.getRoadAddress();
        if (addressForParsing == null || "데이터없음".equals(addressForParsing)) {
            addressForParsing = originalKaraokeData.getJibunAddress();
        }
        String districtName = extractDistrictFromAddress(addressForParsing);

        return AnalysisKaraokeRooms.builder()
                // 필수 정보만 선별 복사
                .districtCode(originalKaraokeData.getDistrictCode())
                .managementNumber(originalKaraokeData.getManagementNumber())
                .businessStatusName(originalKaraokeData.getBusinessStatusName())
                .phoneNumber(originalKaraokeData.getPhoneNumber())
                .jibunAddress(originalKaraokeData.getJibunAddress())
                .roadAddress(originalKaraokeData.getRoadAddress())
                .businessName(originalKaraokeData.getBusinessName())

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
            long finalAnalysisDataCount = analysisKaraokeRepository.count();
            log.info("최종 분석용 노래연습장 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 지오코딩 성공률 통계 출력
            List<Object[]> geocodingStatsList = analysisKaraokeRepository.getGeocodingStatistics();
            log.info("지오코딩 처리 결과 통계:");
            geocodingStatsList.forEach(statRow -> {
                String status = (String) statRow[0];
                Long count = (Long) statRow[1];
                double percentage = finalAnalysisDataCount > 0 ? (double) count / finalAnalysisDataCount * 100 : 0;
                log.info("  {} : {} 개 ({:.1f}%)", status, count, percentage);
            });

            // 주소 타입별 지오코딩 성공률 통계
            List<Object[]> addressTypeStatsList = analysisKaraokeRepository.getGeocodingStatisticsByAddressType();
            log.info("주소 타입별 지오코딩 성공률:");
            addressTypeStatsList.forEach(statRow -> {
                String addressType = (String) statRow[0];
                String status = (String) statRow[1];
                Long count = (Long) statRow[2];
                log.info("  {} - {} : {} 개", addressType, status, count);
            });

            // 구별 노래연습장 밀도 순위 조회 및 로깅
            List<Object[]> districtKaraokeRankingList = analysisKaraokeRepository.countKaraokeRoomsByDistrict();
            log.info("서울시 구별 노래연습장 밀도 순위 (상위 10개구):");

            districtKaraokeRankingList.stream()
                    .limit(10)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];
                        Long karaokeCount = (Long) rankingRow[1];
                        log.info("  {} : {} 개소", districtName, karaokeCount);
                    });

            // 좌표 유효성 검증
            List<AnalysisKaraokeRooms> validCoordsList = analysisKaraokeRepository.findValidCoordinates();
            log.info("유효한 좌표를 가진 노래연습장: {} 개", validCoordsList.size());

            // 지오코딩 실패 데이터 분석
            List<AnalysisKaraokeRooms> failedGeocodingList = analysisKaraokeRepository.findFailedGeocodingData();
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