package com.WhereHouse.AnalysisData.convenience.processor;

import com.WhereHouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreStatistics;
import com.WhereHouse.AnalysisData.convenience.repository.AnalysisConvenienceStoreRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.ConvenienceStore.Entity.ConvenienceStoreStatistics;
import com.WhereHouse.AnalysisStaticData.ConvenienceStore.Repository.ConvenienceStoreStatisticsRepository;
import com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Entity.SchoolStatistics;
import com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Repository.SchoolStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 편의점 데이터 분석용 테이블 생성 처리 컴포넌트 (서울지역 한정)
 *
 * 기존 CONVENIENCE_STORE_STATISTICS 테이블에서 서울지역 데이터를 조회하여
 * 분석 전용 ANALYSIS_CONVENIENCE_STORE_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 편의점 통계 데이터 중 서울지역만 조회 및 검증
 * - 지정된 12개 필드만 복사 (카카오 장소 ID, 장소명, 카테고리명 등)
 * - 기존 위도, 경도 좌표 데이터 그대로 활용
 * - 분석용 테이블 데이터 품질 검증
 * - 브랜드별 및 구별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreDataProcessor {

    // 원본 편의점 테이블 접근을 위한 Repository
    private final ConvenienceStoreStatisticsRepository originalConvenienceStoreRepository;

    // 분석용 편의점 테이블 접근을 위한 Repository
    private final AnalysisConvenienceStoreRepository analysisConvenienceStoreRepository;

    /**
     * 편의점 데이터 분석용 테이블 생성 메인 프로세스 (서울지역)
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 편의점 데이터 중 서울지역만 조회 및 검증
     * 3. 데이터 변환 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisConvenienceStoreData() {
        log.info("=== 편의점 데이터 분석용 테이블 생성 작업 시작 (서울지역) ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisConvenienceStoreRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 편의점 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 편의점 데이터 중 서울지역만 조회 및 검증
        List<ConvenienceStoreStatistics> seoulConvenienceStoreDataList = getSeoulConvenienceStoreData();
        if (seoulConvenienceStoreDataList.isEmpty()) {
            log.warn("서울지역 편의점 데이터가 존재하지 않습니다. 먼저 데이터를 수집해주세요.");
            return;
        }

        log.info("서울지역 편의점 데이터 {} 개 발견", seoulConvenienceStoreDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateAvailableCount = 0;       // 좌표 정보 보유 개수
        int coordinateMissingCount = 0;         // 좌표 정보 누락 개수

        // 처리 진행률 추적
        int processedCount = 0;
        int totalCount = seoulConvenienceStoreDataList.size();
        int logInterval = Math.max(1, totalCount / 10); // 10% 간격으로 로그 출력

        for (ConvenienceStoreStatistics originalConvenienceStoreData : seoulConvenienceStoreDataList) {
            processedCount++;

            try {
                // 원본 데이터를 분석용 엔티티로 변환 (지정된 12개 필드만)
                AnalysisConvenienceStoreStatistics analysisTargetConvenienceStoreData = convertToAnalysisEntity(originalConvenienceStoreData);

                // 좌표 정보 확인
                if (originalConvenienceStoreData.getLatitude() != null && originalConvenienceStoreData.getLongitude() != null) {
                    coordinateAvailableCount++;
                } else {
                    coordinateMissingCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisConvenienceStoreRepository.save(analysisTargetConvenienceStoreData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} ({}, 브랜드: {}, 구: {}, 좌표: {}, {})",
                        originalConvenienceStoreData.getPlaceName(),
                        originalConvenienceStoreData.getCategoryName(),
                        originalConvenienceStoreData.getStoreBrand(),
                        originalConvenienceStoreData.getDistrict(),
                        originalConvenienceStoreData.getLatitude() != null ? originalConvenienceStoreData.getLatitude() : "없음",
                        originalConvenienceStoreData.getLongitude() != null ? originalConvenienceStoreData.getLongitude() : "없음");

                // 진행률 로그 (10% 간격)
                if (processedCount % logInterval == 0 || processedCount == totalCount) {
                    double progressPercentage = (double) processedCount / totalCount * 100;
                    log.info("진행률: {:.1f}% 완료 ({}/{})", progressPercentage, processedCount, totalCount);
                }

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 편의점명: {}, 오류: {}",
                        originalConvenienceStoreData.getPlaceName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("편의점 데이터 분석용 테이블 생성 작업 완료 (서울지역)");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 정보: 보유 {} 개, 누락 {} 개", coordinateAvailableCount, coordinateMissingCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 편의점 데이터 분석용 테이블 생성 작업 종료 (서울지역) ===");
    }

    /**
     * 서울지역 편의점 데이터 조회
     *
     * 주소를 기준으로 서울지역 편의점만 필터링
     *
     * @return 서울지역 편의점 데이터 목록
     */
    private List<ConvenienceStoreStatistics> getSeoulConvenienceStoreData() {
        // 모든 편의점 데이터 조회 후 서울지역만 필터링
        List<ConvenienceStoreStatistics> allConvenienceStores = originalConvenienceStoreRepository.findAll();

        return allConvenienceStores.stream()
                .filter(this::isSeoulConvenienceStore)
                .toList();
    }

    /**
     * 서울지역 편의점인지 판단
     *
     * @param convenienceStore 편의점 데이터
     * @return 서울지역 편의점 여부
     */
    private boolean isSeoulConvenienceStore(ConvenienceStoreStatistics convenienceStore) {
        // 1. 주소명으로 확인
        if (convenienceStore.getAddressName() != null &&
                convenienceStore.getAddressName().startsWith("서울")) {
            return true;
        }

        // 2. 도로명주소로 확인
        if (convenienceStore.getRoadAddressName() != null &&
                convenienceStore.getRoadAddressName().startsWith("서울")) {
            return true;
        }

        // 3. 구 정보로 확인 (서울시 구 목록)
        if (convenienceStore.getDistrict() != null) {
            String district = convenienceStore.getDistrict();
            return isSeoulDistrict(district);
        }

        return false;
    }

    /**
     * 서울시 구인지 확인
     */
    private boolean isSeoulDistrict(String district) {
        String[] seoulDistricts = {
                "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구",
                "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구",
                "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구",
                "은평구", "종로구", "중구", "중랑구"
        };

        for (String seoulDistrict : seoulDistricts) {
            if (district.contains(seoulDistrict)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 원본 편의점 엔티티를 분석용 엔티티로 변환
     *
     * 지정된 12개 필드만 복사한다.
     *
     * @param originalConvenienceStoreData 원본 편의점 엔티티
     * @return 분석용 편의점 엔티티
     */
    private AnalysisConvenienceStoreStatistics convertToAnalysisEntity(ConvenienceStoreStatistics originalConvenienceStoreData) {
        return AnalysisConvenienceStoreStatistics.builder()
                // 지정된 12개 필드 복사
                .kakaoPlaceId(originalConvenienceStoreData.getKakaoPlaceId())         // 카카오 장소 ID
                .placeName(originalConvenienceStoreData.getPlaceName())               // 장소명
                .categoryName(originalConvenienceStoreData.getCategoryName())         // 카테고리명
                .categoryGroupCode(originalConvenienceStoreData.getCategoryGroupCode()) // 카테고리 그룹 코드
                .phone(originalConvenienceStoreData.getPhone())                       // 전화번호
                .addressName(originalConvenienceStoreData.getAddressName())           // 주소명
                .roadAddressName(originalConvenienceStoreData.getRoadAddressName())   // 도로명 주소명
                .longitude(originalConvenienceStoreData.getLongitude())               // 경도 (기존 데이터 사용)
                .latitude(originalConvenienceStoreData.getLatitude())                 // 위도 (기존 데이터 사용)
                .placeUrl(originalConvenienceStoreData.getPlaceUrl())                 // 장소 URL
                .district(originalConvenienceStoreData.getDistrict())                 // 구 정보
                .storeBrand(originalConvenienceStoreData.getStoreBrand())             // 편의점 브랜드
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 분포 상위 5개 로깅
     * - 브랜드별 분포 로깅
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisConvenienceStoreRepository.count();
            log.info("최종 분석용 편의점 데이터 저장 완료 (서울지역): {} 개", finalAnalysisDataCount);

            // 구별 분포 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> storeCountByDistrictList = analysisConvenienceStoreRepository.findStoreCountByDistrict();
            log.info("구별 분포 (상위 5개):");

            storeCountByDistrictList.stream()
                    .limit(5)
                    .forEach(districtRow -> {
                        String district = (String) districtRow[0];       // 구
                        Long districtCount = (Long) districtRow[1];      // 해당 구 수
                        log.info("  {} : {} 개", district, districtCount);
                    });

            // 브랜드별 분포 조회 및 로깅
            List<Object[]> storeCountByBrandList = analysisConvenienceStoreRepository.findStoreCountByBrand();
            log.info("브랜드별 분포:");

            storeCountByBrandList.forEach(brandRow -> {
                String storeBrand = (String) brandRow[0];           // 브랜드
                Long brandCount = (Long) brandRow[1];               // 해당 브랜드 수
                if (storeBrand != null) {
                    log.info("  {} : {} 개", storeBrand, brandCount);
                }
            });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisConvenienceStoreRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisConvenienceStoreRepository.countMissingCoordinates();

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