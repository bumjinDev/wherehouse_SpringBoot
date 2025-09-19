package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 전세 전용 추천 서비스 - 명세서 4.2절
 * 전세 매물 기반 지역구 추천 전용 서비스 클래스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharterRecommendationService {

    private final RedisHandler redisHandler;

    // 서울시 25개 자치구 목록
    private static final List<String> SEOUL_DISTRICTS = Arrays.asList(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구",
            "강북구", "도봉구", "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구",
            "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구"
    );

    private static final int MIN_PROPERTIES_THRESHOLD = 3; // 폴백 기준 매물 수

    /**
     * 전세 지역구 추천 메인 메소드 - 명세서 4.2절
     * S-01 ~ S-06 단계를 순차적으로 수행하여 전세 매물 기반 지역구 추천
     */
    public CharterRecommendationResponseDto getCharterDistrictRecommendations(CharterRecommendationRequestDto request) {
        log.info("=== 전세 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 전세금={}-{}, 평수={}-{}, 우선순위={},{},{}",
                request.getBudgetMin(), request.getBudgetMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3());

        try {
            // S-01: 전 지역구 1차 검색 (전세 전용 인덱스 사용)
            Map<String, List<String>> districtProperties = performCharterStrictSearch(request, SEOUL_DISTRICTS);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformCharterFallback(districtProperties, request);

            // S-04: 매물 단위 점수 계산 (전세 매물 대상)
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores =
                    calculateCharterPropertyScores(searchResult.getDistrictProperties(), request);

            // S-05: 지역구 단위 점수 계산 및 정렬
            List<DistrictWithScore> sortedDistricts = calculateDistrictScoresAndSort(districtPropertiesWithScores);

            // S-06: 최종 응답 생성 (전세 전용 DTO)
            CharterRecommendationResponseDto finalResponse = generateCharterFinalResponse(sortedDistricts, searchResult, request);

            log.info("=== 전세 지역구 추천 서비스 완료 ===");
            return finalResponse;

        } catch (Exception e) {
            log.error("전세 추천 서비스 처리 중 오류 발생", e);
            throw new RuntimeException("전세 추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    // ========================================
    // 전세 검색 관련 Private 메소드들
    // ========================================

    /**
     * S-01: 전세 매물 1차 검색 (Strict Search)
     * 2개 인덱스 교집합: idx:charterPrice + idx:area:*:전세
     * 안전성 조건이 있을 경우 지역구 레벨에서 사전 필터링 적용
     */
    private Map<String, List<String>> performCharterStrictSearch(CharterRecommendationRequestDto request,
                                                                 List<String> targetDistricts) {
        log.info("S-01: 전세 매물 검색 시작 - 대상: {}", targetDistricts.size());

        // 안전성 조건이 있을 경우 지역구 레벨에서 사전 필터링
        List<String> filteredDistricts = targetDistricts;
        if (request.getMinSafetyScore() != null && request.getMinSafetyScore() > 0) {
            filteredDistricts = targetDistricts.stream()
                    .filter(district -> {
                        double districtSafetyScore = getDistrictSafetyScoreFromRedis(district);
                        boolean meetsSafetyRequirement = districtSafetyScore >= request.getMinSafetyScore();
                        if (!meetsSafetyRequirement) {
                            log.debug("안전성 조건 미충족으로 지역구 제외: {} (점수: {}, 기준: {})",
                                    district, districtSafetyScore, request.getMinSafetyScore());
                        }
                        return meetsSafetyRequirement;
                    })
                    .collect(Collectors.toList());

            log.info("안전성 필터링 적용 - 원본: {}개 지역구 → 필터링 후: {}개 지역구 (기준: {}점 이상)",
                    targetDistricts.size(), filteredDistricts.size(), request.getMinSafetyScore());
        }

        Map<String, List<String>> result = new HashMap<>();
        int totalFound = 0;

        for (String district : filteredDistricts) {
            List<String> validProperties = findValidCharterPropertiesInDistrict(district, request);

            if (!validProperties.isEmpty()) {
                result.put(district, validProperties);
                totalFound += validProperties.size();
                log.debug("지역구 [{}]: {}개 전세 매물 ID 발견", district, validProperties.size());
            }
        }

        log.info("전세 매물 검색 완료: 총 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
        return result;
    }

    /**
     * S-02: 전세 매물 폴백 조건 판단 및 확장 검색 수행
     */
    private SearchResult checkAndPerformCharterFallback(Map<String, List<String>> districtProperties,
                                                        CharterRecommendationRequestDto request) {

        boolean hasInsufficientDistricts = districtProperties.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        int totalPropertiesFound = districtProperties.values().stream()
                .mapToInt(List::size).sum();

        int districtsWithProperties = districtProperties.size();

        log.info("S-02: 폴백 조건 판단 - 전체 매물: {}개, 매물 보유 지역구: {}개",
                totalPropertiesFound, districtsWithProperties);

        if (!hasInsufficientDistricts) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_NORMAL")
                    .message("조건에 맞는 전세 매물을 성공적으로 찾았습니다.")
                    .districtProperties(districtProperties)
                    .build();
        }

        log.info("일부 전세 지역구의 매물 부족 - S-03 확장 검색 수행");
        SearchResult expandedResult = performCharterExpandedSearch(request, districtProperties);

        // 기존 정상 지역구 결과 보존 + 폴백 결과 병합
        Map<String, List<String>> finalResult = new HashMap<>(districtProperties);

        // 부족한 지역구들을 제거하고 확장 검색 결과로 교체하기 위한 지역구 이름 리스트 추출.
        List<String> insufficientDistricts = districtProperties.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        insufficientDistricts.forEach(finalResult::remove);
        finalResult.putAll(expandedResult.getDistrictProperties());

        return SearchResult.builder()
                .searchStatus(expandedResult.getSearchStatus())
                .message(expandedResult.getMessage())
                .districtProperties(finalResult)        // List<String, <List<String>> : 지역구 이름 별 실제 매물 ID 들
                .build();
    }

    /**
     * S-03: 전세 매물 확장 검색
     */
    private SearchResult performCharterExpandedSearch(CharterRecommendationRequestDto request,
                                                      Map<String, List<String>> originalResult) {

        // === 4단계: 매물 부족 지역구 목록 추출 ===
        // 매물 ID 개수가 3개 미만인 지역구들의 이름만 추출하여 리스트로 생성
        List<String> insufficientDistricts = originalResult.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("전세 매물 부족 지역구: {} - S-03 확장 검색 수행", insufficientDistricts);

        // === 1단계: 3순위 조건 완화 ===
        // 사용자의 3순위 항목에 해당하는 조건을 완화 임계값에 따라 조정
        CharterRecommendationRequestDto expandedRequest = relaxCharterThirdPriority(request);
        String relaxedCondition = getCharterRelaxedConditionMessage(request.getPriority3(), request, expandedRequest);

        // 완화된 조건으로 부족한 지역구들만 재검색
        Map<String, List<String>> expandedResult = performCharterStrictSearch(expandedRequest, insufficientDistricts);

        // 3순위 완화만으로 충분한지 검사 (모든 지역구가 3개 이상 매물 보유)
        boolean stillInsufficient = expandedResult.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!stillInsufficient && !expandedResult.isEmpty()) {
            log.info("3순위 조건 완화로 충분한 매물 확보: {} 지역구", expandedResult.size());
            return SearchResult.builder()
                    .searchStatus("SUCCESS_EXPANDED")
                    .message("원하시는 조건의 전세 매물이 부족하여, " + relaxedCondition + " 완화하여 찾았어요.")
                    .districtProperties(expandedResult)
                    .build();
        }

        // === 2단계: 2순위 조건 추가 완화 ===
        log.info("3순위 완화 불충분 - 2순위 조건 추가 완화 시도");
        // 이미 3순위가 완화된 요청에 2순위 조건도 완화 적용
        CharterRecommendationRequestDto doubleExpandedRequest = relaxCharterSecondPriority(expandedRequest, request);
        String doubleRelaxedCondition = relaxedCondition + ", " + getCharterRelaxedConditionMessage(request.getPriority2(), request, doubleExpandedRequest);

        // 2순위까지 완화된 조건으로 부족한 지역구들만 최종 검색
        Map<String, List<String>> doubleExpandedResult = performCharterStrictSearch(doubleExpandedRequest, insufficientDistricts);

        // 최종 결과 확인 - 매물이 아예 없으면 검색 실패
        if (doubleExpandedResult.isEmpty() ||
                doubleExpandedResult.values().stream().mapToInt(List::size).sum() == 0) {
            log.warn("2순위까지 완화했으나 매물 없음");
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 전세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        log.info("2순위까지 완화하여 매물 확보: {} 지역구", doubleExpandedResult.size());
        return SearchResult.builder()
                .searchStatus("SUCCESS_EXPANDED")
                .message("원하시는 조건의 전세 매물이 부족하여, " + doubleRelaxedCondition + " 완화하여 찾았어요.")
                .districtProperties(doubleExpandedResult)
                .build();
    }

    /**
     * 전세 매물 검색 - 2개 인덱스 교집합
     */
    private List<String> findValidCharterPropertiesInDistrict(String district, CharterRecommendationRequestDto request) {
        try {
            // 1. 전세금 조건 매물 ID 조회
            String charterPriceIndexKey = "idx:charterPrice:" + district;
            Set<Object> priceValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(charterPriceIndexKey, request.getBudgetMin(), request.getBudgetMax());

            if (priceValidObjects == null || priceValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> priceValidIds = priceValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            // 2. 평수 조건 매물 ID 조회
            String areaIndexKey = "idx:area:" + district + ":전세";
            Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

            if (areaValidObjects == null || areaValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> areaValidIds = areaValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            // 3. 교집합 연산
            priceValidIds.retainAll(areaValidIds);
            return new ArrayList<>(priceValidIds);

        } catch (Exception e) {
            log.debug("전세 매물 검색 중 오류 - 지역구: {}", district, e);
            return Collections.emptyList();
        }
    }

    // ========================================
    // 전세 점수 계산 관련 메소드들
    // ========================================

    /**
     * S-04: 전세 매물 점수 계산
     */
    private Map<String, List<PropertyWithScore>> calculateCharterPropertyScores(
            Map<String, List<String>> districtProperties, CharterRecommendationRequestDto request) {

        log.info("S-04: 전세 매물 점수 계산 시작");
        Map<String, List<PropertyWithScore>> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {

            String districtName = entry.getKey();
            List<String> propertyIds = entry.getValue();

            if (propertyIds.isEmpty()) {

                result.put(districtName, Collections.emptyList());
                continue;
            }

            log.debug("지역구 [{}]: {}개 전세 매물 점수 계산", districtName, propertyIds.size());

            // 전세 매물 상세 정보 조회 (property:charter:{id} 패턴)
            List<PropertyDetail> propertyDetails = getMultipleCharterPropertiesFromRedis(propertyIds);

            // 전세 전용 정규화 범위 조회
            ScoreNormalizationBounds districtBounds = getCharterBoundsFromRedis(districtName);

            // 안전성 점수 조회
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();

            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    // 전세금 점수 계산
                    double priceScore = calculatePriceScore(propertyDetail.getDeposit(), districtBounds);
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
                    double safetyScore = propertyDetail.getSafetyScore() != null ?
                            propertyDetail.getSafetyScore() : districtSafetyScore;

                    // 우선순위 가중치 적용
                    double finalScore = calculateWeightedFinalScore(
                            priceScore, spaceScore, safetyScore,
                            request.getPriority1(), request.getPriority2(), request.getPriority3());

                    PropertyWithScore propertyWithScore = PropertyWithScore.builder()
                            .propertyDetail(propertyDetail)
                            .priceScore(priceScore)
                            .spaceScore(spaceScore)
                            .safetyScore(safetyScore)
                            .finalScore(finalScore)
                            .build();

                    propertiesWithScores.add(propertyWithScore);

                } catch (Exception e) {
                    log.debug("전세 매물 점수 계산 오류: {}", propertyDetail.getPropertyId(), e);
                }
            }

            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));
            result.put(districtName, propertiesWithScores);
        }

        log.info("S-04: 전세 매물 점수 계산 완료 - {}개 지역구", result.size());
        return result;
    }

    /**
     * S-05: 지역구 단위 점수 계산 및 정렬
     */
    private List<DistrictWithScore> calculateDistrictScoresAndSort(
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores) {

        log.info("S-05: 지역구 점수 계산 및 정렬 시작");
        List<DistrictWithScore> districtScores = new ArrayList<>();

        for (Map.Entry<String, List<PropertyWithScore>> entry : districtPropertiesWithScores.entrySet()) {
            String districtName = entry.getKey();
            List<PropertyWithScore> propertiesWithScores = entry.getValue();

            if (propertiesWithScores.isEmpty()) {
                DistrictWithScore districtWithScore = DistrictWithScore.builder()
                        .districtName(districtName)
                        .propertiesWithScores(propertiesWithScores)
                        .averageFinalScore(0.0)
                        .propertyCount(0)
                        .representativeScore(0.0)
                        .build();

                districtScores.add(districtWithScore);
                continue;
            }

            double totalFinalScore = propertiesWithScores.stream()
                    .mapToDouble(PropertyWithScore::getFinalScore)
                    .sum();
            double averageFinalScore = totalFinalScore / propertiesWithScores.size();
            int propertyCount = propertiesWithScores.size();
            double representativeScore = averageFinalScore * Math.log(propertyCount + 1);

            DistrictWithScore districtWithScore = DistrictWithScore.builder()
                    .districtName(districtName)
                    .propertiesWithScores(propertiesWithScores)
                    .averageFinalScore(averageFinalScore)
                    .propertyCount(propertyCount)
                    .representativeScore(representativeScore)
                    .build();

            districtScores.add(districtWithScore);
        }

        // 정렬
        districtScores.sort((d1, d2) -> {
            int scoreComparison = Double.compare(d2.getRepresentativeScore(), d1.getRepresentativeScore());
            if (scoreComparison != 0) return scoreComparison;

            int countComparison = Integer.compare(d2.getPropertyCount(), d1.getPropertyCount());
            if (countComparison != 0) return countComparison;

            return d1.getDistrictName().compareTo(d2.getDistrictName());
        });

        log.info("S-05: 지역구 점수 계산 및 정렬 완료 - {}개 지역구", districtScores.size());
        return districtScores;
    }

    // ========================================
    // 전세 응답 생성 메소드들
    // ========================================

    /**
     * S-06: 전세 전용 최종 응답 생성
     */
    private CharterRecommendationResponseDto generateCharterFinalResponse(List<DistrictWithScore> sortedDistricts,
                                                                          SearchResult searchResult,
                                                                          CharterRecommendationRequestDto request) {
        log.info("S-06: 전세 최종 응답 생성 시작");

        if ("NO_RESULTS".equals(searchResult.getSearchStatus()) || sortedDistricts.isEmpty()) {
            return CharterRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message(searchResult.getMessage())
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        List<DistrictWithScore> validDistricts = sortedDistricts.stream()
                .filter(district -> district.getPropertyCount() > 0)
                .limit(3)
                .collect(Collectors.toList());

        if (validDistricts.isEmpty()) {
            return CharterRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message("조건에 맞는 전세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        List<RecommendedCharterDistrictDto> recommendedDistricts = new ArrayList<>();

        for (int i = 0; i < validDistricts.size(); i++) {
            DistrictWithScore district = validDistricts.get(i);
            int rank = i + 1;

            List<TopCharterPropertyDto> topProperties = selectTopCharterProperties(district.getPropertiesWithScores(), 3);
            String summary = generateDistrictSummary(district, rank, request.getPriority1());

            // === 2차 명세: 상세 순위 정보 패널용 점수 계산 추가 ===
            Double averagePriceScore = calculateAverageScore(district.getPropertiesWithScores(), "price");
            Double averageSpaceScore = calculateAverageScore(district.getPropertiesWithScores(), "space");
            Double districtSafetyScore = getFirstPropertySafetyScore(district.getPropertiesWithScores());

            RecommendedCharterDistrictDto districtDto = RecommendedCharterDistrictDto.builder()
                    .rank(rank)
                    .districtName(district.getDistrictName())
                    .summary(summary)
                    .topProperties(topProperties)
                    .averagePriceScore(averagePriceScore)
                    .averageSpaceScore(averageSpaceScore)
                    .districtSafetyScore(districtSafetyScore)
                    .build();

            recommendedDistricts.add(districtDto);
        }

        log.info("S-06: 전세 최종 응답 생성 완료 - {}개 지역구 추천", recommendedDistricts.size());

        return CharterRecommendationResponseDto.builder()
                .searchStatus(searchResult.getSearchStatus())
                .message(searchResult.getMessage())
                .recommendedDistricts(recommendedDistricts)
                .build();
    }

// === 2차 명세: 상세 순위 정보 패널용 점수 계산 헬퍼 메서드 추가 ===
    /**
     * 지역구 내 매물들의 특정 점수 평균 계산
     */
    private Double calculateAverageScore(List<PropertyWithScore> propertiesWithScores, String scoreType) {
        if (propertiesWithScores == null || propertiesWithScores.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        for (PropertyWithScore property : propertiesWithScores) {
            switch (scoreType) {
                case "price":
                    totalScore += property.getPriceScore();
                    break;
                case "space":
                    totalScore += property.getSpaceScore();
                    break;
                default:
                    return 0.0;
            }
        }

        return totalScore / propertiesWithScores.size();
    }

    /**
     * 지역구 안전성 점수 추출 (첫 번째 매물의 safetyScore 사용)
     */
    private Double getFirstPropertySafetyScore(List<PropertyWithScore> propertiesWithScores) {
        if (propertiesWithScores == null || propertiesWithScores.isEmpty()) {
            return 50.0; // 기본값
        }

        PropertyWithScore firstProperty = propertiesWithScores.get(0);
        return firstProperty.getSafetyScore();
    }

    /**
     * 전세 매물 DTO 변환
     */
    private List<TopCharterPropertyDto> selectTopCharterProperties(List<PropertyWithScore> propertiesWithScores, int maxCount) {
        return propertiesWithScores.stream()
                .limit(maxCount)
                .map(this::convertToTopCharterPropertyDto)
                .collect(Collectors.toList());
    }

    /**
     * 전세 매물 DTO 변환
     */
    private TopCharterPropertyDto convertToTopCharterPropertyDto(PropertyWithScore propertyWithScore) {
        PropertyDetail detail = propertyWithScore.getPropertyDetail();

        return TopCharterPropertyDto.builder()
                .propertyId(detail.getPropertyId())
                .propertyName(detail.getAptNm())
                .address(detail.getAddress())
                .price(detail.getDeposit()) // 전세금
                .leaseType("전세")
                .area(detail.getAreaInPyeong())
                .floor(detail.getFloor())
                .buildYear(detail.getBuildYear())
                .finalScore(propertyWithScore.getFinalScore())
                .build();
    }

    // ========================================
    // 전세 Redis 조회 메소드들
    // ========================================

    /**
     * 전세 매물 상세 정보 조회 (property:charter:{id} 패턴)
     */
    private List<PropertyDetail> getMultipleCharterPropertiesFromRedis(List<String> propertyIds) {

        List<PropertyDetail> propertyDetails = new ArrayList<>();

        try {
            List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(

                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String propertyId : propertyIds) {
                            String propertyKey = "property:charter:" + propertyId;
                            connection.hGetAll(propertyKey.getBytes());
                        }
                        return null;
                    });

            for (int i = 0; i < propertyIds.size(); i++) {
                Object result = pipelineResults.get(i);
                if (result instanceof Map) {
                    Map<Object, Object> propertyHash = (Map<Object, Object>) result;
                    if (!propertyHash.isEmpty()) {
                        PropertyDetail detail = convertHashToPropertyDetail(propertyIds.get(i), propertyHash);
                        if (detail != null) {
                            propertyDetails.add(detail);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("전세 매물 Pipeline 조회 실패", e);
        }

        return propertyDetails;
    }

    /**
     * 전세 전용 정규화 범위 조회 - 전세금과 평수만 필요
     */
    private ScoreNormalizationBounds getCharterBoundsFromRedis(String districtName) {
        try {
            String boundsKey = "bounds:" + districtName + ":전세";
            Map<Object, Object> boundsHash = redisHandler.redisTemplate.opsForHash().entries(boundsKey);

            if (boundsHash.isEmpty()) {
                return ScoreNormalizationBounds.builder()
                        .minPrice(0.0)
                        .maxPrice(100000.0)
                        .minArea(10.0)
                        .maxArea(100.0)
                        .build();
            }

            return ScoreNormalizationBounds.builder()
                    .minPrice(Double.parseDouble(boundsHash.get("minPrice").toString())) // 전세금 범위
                    .maxPrice(Double.parseDouble(boundsHash.get("maxPrice").toString())) // 전세금 범위
                    .minArea(Double.parseDouble(boundsHash.get("minArea").toString()))   // 평수 범위
                    .maxArea(Double.parseDouble(boundsHash.get("maxArea").toString()))   // 평수 범위
                    .build();

        } catch (Exception e) {
            log.error("전세 정규화 범위 조회 실패 - 지역구: {}", districtName, e);
            return ScoreNormalizationBounds.builder()
                    .minPrice(0.0)
                    .maxPrice(100000.0)
                    .minArea(10.0)
                    .maxArea(100.0)
                    .build();
        }
    }

    /**
     * 안전성 점수 조회 - 배치에서 생성한 safety:{지역구명} 패턴 사용
     */
    private double getDistrictSafetyScoreFromRedis(String districtName) {
        try {
            String safetyKey = "safety:" + districtName;
            Map<Object, Object> safetyHash = redisHandler.redisTemplate.opsForHash().entries(safetyKey);

            if (safetyHash.isEmpty()) {
                log.debug("안전성 점수 없음 - 지역구: {}, 기본값 50.0 사용", districtName);
                return 50.0;
            }

            Object safetyScoreObj = safetyHash.get("safetyScore");
            if (safetyScoreObj == null) {
                log.debug("safetyScore 필드 없음 - 지역구: {}, 기본값 50.0 사용", districtName);
                return 50.0;
            }

            double safetyScore = Double.parseDouble(safetyScoreObj.toString());
            log.debug("안전성 점수 조회 성공 - 지역구: {}, 점수: {}", districtName, safetyScore);
            return safetyScore;

        } catch (NumberFormatException e) {
            log.error("안전성 점수 숫자 변환 실패 - 지역구: {}, 값: {}", districtName,
                    redisHandler.redisTemplate.opsForHash().get("safety:" + districtName, "safetyScore"), e);
            return 50.0;
        } catch (Exception e) {
            log.error("안전성 점수 조회 실패 - 지역구: {}", districtName, e);
            return 50.0;
        }
    }

    // ========================================
    // 전세 점수 계산 헬퍼 메소드들
    // ========================================

    /**
     * 가격 점수 계산 (낮은 가격일수록 높은 점수) - 전세용
     */
    private double calculatePriceScore(Integer price, ScoreNormalizationBounds bounds) {
        if (price == null) return 0.0;

        double normalizedPrice = (price - bounds.getMinPrice()) / (bounds.getMaxPrice() - bounds.getMinPrice());
        return Math.max(0.0, Math.min(100.0, 100.0 - (normalizedPrice * 100.0)));
    }

    /**
     * 공간 점수 계산 (넓은 평수일수록 높은 점수) - 전세용
     */
    private double calculateSpaceScore(Double areaInPyeong, ScoreNormalizationBounds bounds) {
        if (areaInPyeong == null) return 0.0;

        double normalizedArea = (areaInPyeong - bounds.getMinArea()) / (bounds.getMaxArea() - bounds.getMinArea());
        return Math.max(0.0, Math.min(100.0, normalizedArea * 100.0));
    }

    /**
     * 우선순위 가중치 적용 최종 점수 계산 - 전세용
     */
    private double calculateWeightedFinalScore(double priceScore, double spaceScore, double safetyScore,
                                               String priority1, String priority2, String priority3) {
        Map<String, Double> priorityWeights = new HashMap<>();
        priorityWeights.put(priority1, 0.6);
        priorityWeights.put(priority2, 0.3);
        priorityWeights.put(priority3, 0.1);

        double weightedPriceScore = priceScore * priorityWeights.getOrDefault("PRICE", 0.0);
        double weightedSpaceScore = spaceScore * priorityWeights.getOrDefault("SPACE", 0.0);
        double weightedSafetyScore = safetyScore * priorityWeights.getOrDefault("SAFETY", 0.0);

        return weightedPriceScore + weightedSpaceScore + weightedSafetyScore;
    }

    /**
     * 지역구 요약 메시지 생성
     */
    private String generateDistrictSummary(DistrictWithScore district, int rank, String primaryPriority) {
        String priorityName;
        switch (primaryPriority) {
            case "PRICE":
                priorityName = "가격";
                break;
            case "SAFETY":
                priorityName = "안전";
                break;
            case "SPACE":
                priorityName = "공간";
                break;
            default:
                priorityName = "종합";
        }

        if (rank == 1) {
            return String.format("%s 1순위 조건에 가장 부합하며, 조건 내 추천 매물이 %d건 있습니다.",
                    priorityName, district.getPropertyCount());
        } else {
            return String.format("%s 조건에서 %d위를 차지하며, 추천 매물이 %d건 있습니다.",
                    priorityName, rank, district.getPropertyCount());
        }
    }

    // ========================================
    // 전세 폴백 조건 완화 메소드들
    // ========================================

    /**
     * 전세 3순위 조건 완화
     */
    private CharterRecommendationRequestDto relaxCharterThirdPriority(CharterRecommendationRequestDto original) {

        CharterRecommendationRequestDto relaxed = copyCharterRequest(original);

        switch (original.getPriority3()) {
            case "PRICE":
                if (original.getBudgetFlexibility() != null && original.getBudgetFlexibility() > 0) {
                    int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
                    relaxed.setBudgetMax(original.getBudgetMax() + flexAmount);
                }
                break;
            case "SPACE":
                if (original.getAbsoluteMinArea() != null && original.getAbsoluteMinArea() > 0) {
                    relaxed.setAreaMin(original.getAbsoluteMinArea());
                }
                break;
            case "SAFETY":
                if (original.getMinSafetyScore() != null && original.getMinSafetyScore() > 0) {
                    relaxed.setMinSafetyScore(original.getMinSafetyScore());
                }
                break;
        }

        return relaxed;
    }

    /**
     * 전세 2순위 조건 완화
     */
    private CharterRecommendationRequestDto relaxCharterSecondPriority(CharterRecommendationRequestDto expandedRequest,
                                                                       CharterRecommendationRequestDto original) {
        CharterRecommendationRequestDto doubleRelaxed = copyCharterRequest(expandedRequest);

        switch (original.getPriority2()) {
            case "PRICE":
                if (original.getBudgetFlexibility() != null && original.getBudgetFlexibility() > 0) {
                    int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
                    int newBudgetMax = original.getBudgetMax() + flexAmount;
                    doubleRelaxed.setBudgetMax(Math.max(doubleRelaxed.getBudgetMax(), newBudgetMax));
                }
                break;
            case "SPACE":
                if (original.getAbsoluteMinArea() != null && original.getAbsoluteMinArea() > 0) {
                    doubleRelaxed.setAreaMin(Math.min(doubleRelaxed.getAreaMin(), original.getAbsoluteMinArea()));
                }
                break;
            case "SAFETY":
                if (original.getMinSafetyScore() != null && original.getMinSafetyScore() > 0) {
                    // 2순위까지 완화 시에는 더 낮은 안전 점수까지 허용
                    doubleRelaxed.setMinSafetyScore(Math.min(doubleRelaxed.getMinSafetyScore(), original.getMinSafetyScore()));
                }
                break;
        }

        return doubleRelaxed;
    }

    /**
     * 전세 요청 객체 깊은 복사
     */
    private CharterRecommendationRequestDto copyCharterRequest(CharterRecommendationRequestDto original) {

        return CharterRecommendationRequestDto.builder()
                .budgetMin(original.getBudgetMin())
                .budgetMax(original.getBudgetMax())
                .areaMin(original.getAreaMin())
                .areaMax(original.getAreaMax())
                .priority1(original.getPriority1())
                .priority2(original.getPriority2())
                .priority3(original.getPriority3())
                .budgetFlexibility(original.getBudgetFlexibility())
                .minSafetyScore(original.getMinSafetyScore())
                .absoluteMinArea(original.getAbsoluteMinArea())
                .build();
    }

    /**
     * 전세 완화 조건 메시지 생성
     */
    private String getCharterRelaxedConditionMessage(String priority, CharterRecommendationRequestDto original,
                                                     CharterRecommendationRequestDto relaxed) {
        switch (priority) {
            case "PRICE":
                return "전세금 조건을 " + relaxed.getBudgetMax() + "만원으로";
            case "SPACE":
                return "평수 조건을 " + relaxed.getAreaMin() + "평으로";
            case "SAFETY":
                return "안전성 점수 조건을 " + relaxed.getMinSafetyScore() + "점으로";
            default:
                return "검색 조건을";
        }
    }

    // ========================================
    // 공통 유틸리티 메소드들
    // ========================================

    /**
     * Redis Hash를 PropertyDetail 객체로 변환
     */
    private PropertyDetail convertHashToPropertyDetail(String propertyId, Map<Object, Object> propertyHash) {
        try {
            return PropertyDetail.builder()
                    .propertyId(propertyId)
                    .aptNm(getStringValue(propertyHash, "aptNm"))
                    .excluUseAr(getDoubleValue(propertyHash, "excluUseAr"))
                    .floor(getIntegerValue(propertyHash, "floor"))
                    .buildYear(getIntegerValue(propertyHash, "buildYear"))
                    .dealDate(getStringValue(propertyHash, "dealDate"))
                    .deposit(getIntegerValue(propertyHash, "deposit"))
                    .monthlyRent(getIntegerValue(propertyHash, "monthlyRent"))
                    .leaseType(getStringValue(propertyHash, "leaseType"))
                    .umdNm(getStringValue(propertyHash, "umdNm"))
                    .jibun(getStringValue(propertyHash, "jibun"))
                    .sggCd(getStringValue(propertyHash, "sggCd"))
                    .address(getStringValue(propertyHash, "address"))
                    .areaInPyeong(getDoubleValue(propertyHash, "areaInPyeong"))
                    .rgstDate(getStringValue(propertyHash, "rgstDate"))
                    .districtName(getStringValue(propertyHash, "districtName"))
                    .safetyScore(null)
                    .build();
        } catch (Exception e) {
            log.debug("PropertyDetail 변환 실패: {}", propertyId, e);
            return null;
        }
    }

    /**
     * String 값 안전 추출
     */
    private String getStringValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Integer 값 안전 추출
     */
    private Integer getIntegerValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        if (value == null) return null;
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Double 값 안전 추출
     */
    private Double getDoubleValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        if (value == null) return null;
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========================================
    // 내부 클래스들
    // ========================================

    /**
     * 검색 결과 클래스
     */
    private static class SearchResult {

        private String searchStatus;
        private String message;

        private Map<String, List<String>> districtProperties;

        public static SearchResultBuilder builder() {
            return new SearchResultBuilder();
        }

        public static class SearchResultBuilder {
            private String searchStatus;
            private String message;
            private Map<String, List<String>> districtProperties;

            public SearchResultBuilder searchStatus(String searchStatus) {
                this.searchStatus = searchStatus;
                return this;
            }

            public SearchResultBuilder message(String message) {
                this.message = message;
                return this;
            }

            public SearchResultBuilder districtProperties(Map<String, List<String>> districtProperties) {
                this.districtProperties = districtProperties;
                return this;
            }

            public SearchResult build() {
                SearchResult result = new SearchResult();
                result.searchStatus = this.searchStatus;
                result.message = this.message;
                result.districtProperties = this.districtProperties;
                return result;
            }
        }

        public String getSearchStatus() { return searchStatus; }
        public String getMessage() { return message; }
        public Map<String, List<String>> getDistrictProperties() { return districtProperties; }
    }

    /**
     * 점수 포함 매물 클래스
     */
    @lombok.Builder
    @lombok.Getter
    private static class PropertyWithScore {
        private PropertyDetail propertyDetail;
        private double priceScore;
        private double spaceScore;
        private double safetyScore;
        private double finalScore;
    }

    /**
     * 점수 포함 지역구 클래스
     */
    @lombok.Builder
    @lombok.Getter
    private static class DistrictWithScore {
        private String districtName;
        private List<PropertyWithScore> propertiesWithScores;
        private double averageFinalScore;
        private int propertyCount;
        private double representativeScore;
    }

    /**
     * 매물 상세 정보 클래스
     */
    @lombok.Builder
    @lombok.Getter
    private static class PropertyDetail {
        private String propertyId;
        private String aptNm;
        private Double excluUseAr;
        private Integer floor;
        private Integer buildYear;
        private String dealDate;
        private Integer deposit;
        private Integer monthlyRent;
        private String leaseType;
        private String umdNm;
        private String jibun;
        private String sggCd;
        private String address;
        private Double areaInPyeong;
        private String rgstDate;
        private String districtName;
        private Double safetyScore;
    }

    /**
     * 전세 전용 정규화 범위 클래스
     */
    @lombok.Builder
    @lombok.Getter
    private static class ScoreNormalizationBounds {
        private double minPrice;
        private double maxPrice;
        private double minArea;
        private double maxArea;
    }
}