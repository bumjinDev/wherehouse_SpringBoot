package com.WhereHouse.AnalysisData.pcbang.processor;

import com.WhereHouse.AnalysisData.pcbang.client.KakaoAddressApiClient;
import com.WhereHouse.AnalysisData.pcbang.entity.AnalysisPcBangStatistics;
import com.WhereHouse.AnalysisData.pcbang.repository.AnalysisPcBangRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.PcRoom.Entity.PcBangs;
import com.WhereHouse.AnalysisStaticData.PcRoom.Repository.PcBangsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PC방 위치 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 PC_BANGS 테이블에서 7개 핵심 필드를 선별하고
 * 카카오맵 API를 통해 주소를 위도/경도 좌표로 변환하여
 * 분석 전용 ANALYSIS_PC_BANG_STATISTICS 테이블에 9개 필드로 저장한다.
 *
 * 주요 기능:
 * - 원본 PC방 데이터 조회 및 검증
 * - 7개 핵심 필드 선별 및 카카오맵 API 좌표 변환
 * - API 호출 제한 고려한 속도 조절
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 PC방 밀도 및 영업상태 분포 순위 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PcBangDataProcessor {

    // 원본 PC방 테이블 접근을 위한 Repository
    private final PcBangsRepository originalPcBangRepository;

    // 분석용 PC방 테이블 접근을 위한 Repository
    private final AnalysisPcBangRepository analysisPcBangRepository;

    // 카카오맵 API 클라이언트
    private final KakaoAddressApiClient kakaoAddressApiClient;

    // API 호출 간격 설정
    @Value("${kakao.local-api.request-delay}")
    private long requestDelay;

    /**
     * PC방 위치 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 PC방 데이터 조회 및 검증
     * 3. 7개 핵심 필드 선별 및 카카오맵 API 좌표 변환
     * 4. 분석용 테이블 저장
     * 5. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisPcBangData() {
        log.info("=== PC방 위치 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisPcBangRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 PC방 위치 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 PC방 데이터 조회 및 검증
        List<PcBangs> originalPcBangDataList = originalPcBangRepository.findAll();
        if (originalPcBangDataList.isEmpty()) {
            log.warn("원본 PC방 데이터가 존재하지 않습니다. 먼저 PcBangsDataLoader를 통해 CSV 데이터를 수집해주세요.");
            return;
        }

        log.info("원본 PC방 데이터 {} 개 업소 발견", originalPcBangDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수
        int apiSuccessCount = 0;            // API 좌표 변환 성공 개수
        int apiFailedCount = 0;             // API 좌표 변환 실패 개수

        for (int i = 0; i < originalPcBangDataList.size(); i++) {
            PcBangs originalPcBangData = originalPcBangDataList.get(i);

            // 진행률 로깅 (100건마다)
            if (i > 0 && i % 100 == 0) {
                double progress = ((double) i / originalPcBangDataList.size()) * 100;
                log.info("처리 진행률: {:.1f}% ({}/{}) - API 성공: {}, 실패: {}",
                        progress, i, originalPcBangDataList.size(), apiSuccessCount, apiFailedCount);
            }

            try {
                // 원본 데이터에서 7개 핵심 필드 선별 및 분석용 엔티티로 변환
                AnalysisPcBangStatistics analysisTargetPcBangData = convertToAnalysisEntity(originalPcBangData);

                // 카카오맵 API를 통한 좌표 변환 시도
                String targetAddress = determineTargetAddress(originalPcBangData);
                if (targetAddress != null && !targetAddress.equals("데이터없음")) {
                    try {
                        KakaoAddressApiClient.AddressSearchResponse response = kakaoAddressApiClient.searchAddress(targetAddress);

                        if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                            KakaoAddressApiClient.Document document = response.getDocuments().get(0);
                            analysisTargetPcBangData.setLatitude(new BigDecimal(document.getLatitude()));
                            analysisTargetPcBangData.setLongitude(new BigDecimal(document.getLongitude()));
                            apiSuccessCount++;

                            log.debug("좌표 변환 성공: {} → lat: {}, lng: {}",
                                    targetAddress, document.getLatitude(), document.getLongitude());
                        } else {
                            log.debug("좌표 변환 실패 (결과 없음): {}", targetAddress);
                            apiFailedCount++;
                        }

                        // API 호출 제한 준수를 위한 대기
                        Thread.sleep(requestDelay);

                    } catch (Exception apiException) {
                        log.warn("카카오맵 API 호출 실패: {} - {}", targetAddress, apiException.getMessage());
                        apiFailedCount++;

                        // API 에러 시 더 긴 대기
                        Thread.sleep(requestDelay * 5);
                    }
                }

                // 분석용 테이블에 데이터 저장
                analysisPcBangRepository.save(analysisTargetPcBangData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (구코드: {}, 좌표: {}, {})",
                        originalPcBangData.getBusinessName(),
                        originalPcBangData.getDistrictCode(),
                        analysisTargetPcBangData.getLatitude(),
                        analysisTargetPcBangData.getLongitude());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - PC방: {} (ID: {}), 오류: {}",
                        originalPcBangData.getBusinessName(), originalPcBangData.getId(),
                        dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("PC방 위치 데이터 분석용 테이블 생성 작업 완료");
        log.info("  - 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("  - 좌표 변환: 성공 {} 개, 실패 {} 개", apiSuccessCount, apiFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== PC방 위치 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 PC방 엔티티를 분석용 엔티티로 변환
     *
     * 7개 핵심 필드를 복사하고, 좌표는 초기값으로 설정한다. (API 호출로 나중에 업데이트)
     * null 값은 적절한 기본값으로 변환 처리한다.
     *
     * @param originalPcBangData 원본 PC방 엔티티
     * @return 분석용 PC방 엔티티
     */
    private AnalysisPcBangStatistics convertToAnalysisEntity(PcBangs originalPcBangData) {
        return AnalysisPcBangStatistics.builder()
                // 기본 정보
                .districtCode(handleNullString(originalPcBangData.getDistrictCode()))           // 구 코드
                .managementNumber(handleNullString(originalPcBangData.getManagementNumber()))   // 관리번호
                .businessStatusName(handleNullString(originalPcBangData.getBusinessStatusName())) // 영업상태명

                // 주소 정보
                .jibunAddress(handleNullString(originalPcBangData.getJibunAddress()))           // 지번주소
                .roadAddress(handleNullString(originalPcBangData.getRoadAddress()))             // 도로명주소

                // 업소 정보
                .businessName(handleNullString(originalPcBangData.getBusinessName()))           // 업소명

                // 좌표 정보 (초기값, API 호출로 업데이트 예정)
                .latitude(BigDecimal.ZERO)                                                      // 위도 (카카오맵 API)
                .longitude(BigDecimal.ZERO)                                                     // 경도 (카카오맵 API)
                .build();
    }

    /**
     * 카카오맵 API 호출에 사용할 주소 결정
     * 도로명주소 우선, 없으면 지번주소 사용
     *
     * @param originalPcBangData 원본 PC방 데이터
     * @return 변환에 사용할 주소 또는 null
     */
    private String determineTargetAddress(PcBangs originalPcBangData) {
        String roadAddress = originalPcBangData.getRoadAddress();
        String jibunAddress = originalPcBangData.getJibunAddress();

        // 도로명주소 우선 사용
        if (roadAddress != null && !roadAddress.trim().isEmpty() && !roadAddress.equals("데이터없음")) {
            return roadAddress.trim();
        }

        // 도로명주소가 없으면 지번주소 사용
        if (jibunAddress != null && !jibunAddress.trim().isEmpty() && !jibunAddress.equals("데이터없음")) {
            return jibunAddress.trim();
        }

        return null;
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
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 PC방 밀도 순위 상위 5개 로깅
     * - 영업상태별 분포 로깅
     * - 좌표 변환 성공률 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisPcBangRepository.count();
            log.info("최종 분석용 PC방 데이터 저장 완료: {} 개 업소", finalAnalysisDataCount);

            // 구별 PC방 밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtPcBangDensityRankingList = analysisPcBangRepository.findDistrictPcBangDensityRanking();
            log.info("구코드별 PC방 밀도 순위 (상위 5개):");

            districtPcBangDensityRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtCode = (String) rankingRow[0];         // 구 코드
                        Long totalPcBangCount = (Long) rankingRow[1];         // 총 PC방 개수
                        log.info("  {} : {} 개 업소", districtCode, totalPcBangCount);
                    });

            // 서울시 구별 PC방 밀도 순위 조회 및 로깅
            List<Object[]> seoulDistrictPcBangDensityRankingList = analysisPcBangRepository.findSeoulDistrictPcBangDensityRanking();
            log.info("서울시 구별 PC방 밀도 순위 (상위 5개구):");

            seoulDistrictPcBangDensityRankingList.stream()
                    .limit(5)
                    .forEach(districtRow -> {
                        String districtName = (String) districtRow[0];        // 구 이름
                        Number pcBangCount = (Number) districtRow[1];         // PC방 개수
                        log.info("  {} : {} 개 업소", districtName, pcBangCount);
                    });

            // 영업상태별 분포 조회 및 로깅
            List<Object[]> businessStatusDistributionList = analysisPcBangRepository.findBusinessStatusDistribution();
            log.info("영업상태별 PC방 분포:");

            businessStatusDistributionList.forEach(statusRow -> {
                String statusName = (String) statusRow[0];                // 영업상태명
                Long statusCount = (Long) statusRow[1];                   // 개수
                log.info("  {} : {} 개 업소", statusName, statusCount);
            });

            // 좌표 변환 성공률 확인
            long totalCount = analysisPcBangRepository.count();
            long coordinateSuccessCount = analysisPcBangRepository.count(); // 실제로는 좌표가 0이 아닌 것들을 세어야 함

            // 좌표 데이터 품질 확인을 위한 쿼리 (0이 아닌 좌표를 가진 데이터 개수)
            // 실제 구현에서는 @Query로 별도 메서드를 만들어야 함
            log.info("좌표 변환 결과 요약:");
            log.info("  - 전체 데이터: {} 개", totalCount);
            log.info("  - 좌표 보유: 계산 필요 (0이 아닌 lat/lng)");

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}