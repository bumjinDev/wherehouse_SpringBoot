package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import com.wherehouse.review.domain.ReviewStatisticsMonthly;
import com.wherehouse.review.repository.ReviewStatisticsMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 월세 전용 추천 서비스 - 명세서 4.3절 및 10.2절(Phase 2)
 *
 * 역할:
 * 1. Redis 인덱스를 활용한 매물 1차 검색 (보증금, 월세, 평수 조건)
 * 2. RDB(ReviewStatisticsMonthly) 조회 및 하이브리드 점수 계산 (정량+정성)
 * 3. 최종 추천 리스트 생성 및 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyRecommendationService {

    private final RedisHandler redisHandler;
    private final ReviewStatisticsMonthlyRepository reviewStatisticsRepository;

    private static final List<String> SEOUL_DISTRICTS = Arrays.asList(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구",
            "강북구", "도봉구", "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구",
            "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구"
    );

    private static final int MIN_PROPERTIES_THRESHOLD = 3;
    private static final int COLD_START_REVIEW_COUNT = 5;

    /**
     * 월세 지역구 추천 메인 메소드
     * S-01 ~ S-06 단계를 순차적으로 수행하여 월세 매물 기반 지역구 추천
     */
    public MonthlyRecommendationResponseDto getMonthlyDistrictRecommendations(MonthlyRecommendationRequestDto request, String currentUserId) {

        currentUserId = "anonymousUser".equals(currentUserId) ? null : currentUserId;

        log.info("=== 월세 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 보증금={}-{}, 월세={}-{}, 평수={}-{}, 우선순위={},{},{}, 요청자={}",
                request.getBudgetMin(), request.getBudgetMax(),
                request.getMonthlyRentMin(), request.getMonthlyRentMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3(),
                currentUserId);

        try {
            // S-01: 전 지역구 1차 검색 (인덱스 조회 + Hash 상세 조회 + hard condition 검증)
            Map<String, List<PropertyDetail>> districtProperties = performMonthlyStrictSearch(request, SEOUL_DISTRICTS);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformMonthlyFallback(districtProperties, request);

            // S-04: 매물 단위 점수 계산 (하이브리드 로직 적용)
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores =
                    calculateMonthlyPropertyScores(searchResult.getDistrictProperties(), request);

            // S-05: 지역구 단위 점수 계산 및 정렬
            List<DistrictWithScore> sortedDistricts = calculateDistrictScoresAndSort(districtPropertiesWithScores);

            // S-06: 최종 응답 생성 (월세 전용 DTO)
            return generateMonthlyFinalResponse(sortedDistricts, searchResult, request, currentUserId);

        } catch (Exception e) {
            log.error("월세 추천 서비스 처리 중 오류 발생", e);
            throw new RuntimeException("월세 추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    // ========================================
    // S-01: 월세 매물 1차 검색 + 서브 루틴
    // ========================================

    /**
     * S-01: 월세 매물 1차 검색 (Strict Search) — 배치 최적화.
     *
     * 전체 지역구 ZSet 조회를 단일 MULTI/EXEC, Hash 상세 조회를 단일 Pipeline으로 실행하여
     * Redis 라운드트립을 최소화한다. 월세는 인덱스 3개(보증금, 월세금, 면적)를 사용.
     *
     * @param request         사용자 요청 DTO (보증금·월세금·면적 범위, 안전성 기준 등)
     * @param targetDistricts 검색 대상 지역구 목록 (최초 호출 시 서울 25개구, Fallback 시 부족 지역구만)
     * @return 지역구명 → hard condition 통과 매물 상세 목록
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<PropertyDetail>> performMonthlyStrictSearch(MonthlyRecommendationRequestDto request,
                                                                         List<String> targetDistricts) {

        /* 1단계: 안전성 점수 기준 미달 지역구 제외 → 이후 단계에서 불필요한 ZSet 조회 방지 */
        List<String> filteredDistricts = filterDistrictsBySafetyScore(targetDistricts, request);
        if (filteredDistricts.isEmpty()) {
            return Collections.emptyMap();
        }

        /* 2단계: 전 지역구 보증금·월세금·면적 인덱스(ZSet)를 단일 MULTI/EXEC로 원자적 배치 조회 → 지역구당 3개 Set 반환 */
        List<Object> txResults = executeZSetBatchQuery(filteredDistricts, request);
        if (txResults == null || txResults.size() < filteredDistricts.size() * 3) {
            return Collections.emptyMap();
        }

        /* 3단계: 지역구별 보증금·월세금·면적 교집합(retainAll) → 세 인덱스 모두 통과한 후보 propertyId 집합 도출 */
        Map<String, Set<String>> districtCandidateIds = calculateIntersections(filteredDistricts, txResults);

        Set<String> allCandidateIds = districtCandidateIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allCandidateIds.isEmpty()) {
            return Collections.emptyMap();
        }

        /* 4단계: 전체 후보 propertyId에 대해 단일 Pipeline으로 Hash 상세 조회 → propertyId 기준 Map 변환 */
        Map<String, PropertyDetail> propertyDetailMap = fetchPropertyDetailMap(allCandidateIds);

        /* 5단계: Hash 실측값 기준 hard condition 검증(leaseType, status, 보증금·월세금·면적 범위) 후 통과 매물만 지역구별로 조립 */
        return assembleValidatedResults(districtCandidateIds, propertyDetailMap, request);
    }

    /**
     * 안전성 점수 기준 미달 지역구 제외.
     * Redis "safety:{지역구}" Hash에서 조회한 점수가 요청 기준(minSafetyScore) 미만이면 탈락.
     *
     * @param targetDistricts 필터링 전 지역구 목록
     * @param request         안전성 기준 점수를 포함하는 요청 DTO
     * @return 안전성 기준 충족 지역구 목록 (기준 미설정 시 원본 그대로 반환)
     */
    private List<String> filterDistrictsBySafetyScore(List<String> targetDistricts,
                                                       MonthlyRecommendationRequestDto request) {
        if (request.getMinSafetyScore() == null || request.getMinSafetyScore() <= 0) {
            return targetDistricts;
        }
        return targetDistricts.stream()
                .filter(district -> getDistrictSafetyScoreFromRedis(district) >= request.getMinSafetyScore())
                .collect(Collectors.toList());
    }

    /**
     * 전체 지역구 보증금·월세금·면적 인덱스(ZSet) 범위 조회를 단일 MULTI/EXEC로 실행.
     * 지역구당 3개 커맨드(보증금 ZSet + 월세금 ZSet + 면적 ZSet)를 트랜잭션 큐에 적재하여 원자적으로 수행.
     * 결과 리스트 인덱스: [i*3] = 보증금, [i*3+1] = 월세금, [i*3+2] = 면적 범위 통과 ID Set.
     *
     * @param districts 조회 대상 지역구 목록
     * @param request   보증금·월세금·면적 범위를 포함하는 요청 DTO
     * @return MULTI/EXEC 결과 리스트 (지역구당 3개 Set 원소), 오류 시 null
     */
    private List<Object> executeZSetBatchQuery(List<String> districts,
                                                MonthlyRecommendationRequestDto request) {
        try {
            return redisHandler.redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    for (String district : districts) {
                        operations.opsForZSet().rangeByScore(
                                "idx:deposit:" + district,
                                request.getBudgetMin(), request.getBudgetMax());
                        operations.opsForZSet().rangeByScore(
                                "idx:monthlyRent:" + district + ":월세",
                                request.getMonthlyRentMin(), request.getMonthlyRentMax());
                        operations.opsForZSet().rangeByScore(
                                "idx:area:" + district + ":월세",
                                request.getAreaMin(), request.getAreaMax());
                    }
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.error("월세 매물 인덱스 배치 조회 중 오류", e);
            return null;
        }
    }

    /**
     * 지역구별 보증금·월세금·면적 인덱스 교집합 계산.
     * MULTI/EXEC 결과에서 지역구당 3개 Set을 추출, retainAll로 교집합하여
     * 세 범위 모두 통과한 후보 propertyId 집합을 지역구별로 반환한다.
     *
     * @param districts 지역구 목록 (MULTI/EXEC 커맨드 순서와 1:1 대응)
     * @param txResults MULTI/EXEC 결과 리스트
     * @return 지역구명 → 보증금·월세금·면적 교집합 통과 propertyId Set
     */
    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> calculateIntersections(List<String> districts, List<Object> txResults) {
        Map<String, Set<String>> districtCandidateIds = new LinkedHashMap<>();

        for (int i = 0; i < districts.size(); i++) {
            Set<Object> depositObjects = (Set<Object>) txResults.get(i * 3);
            Set<Object> rentObjects = (Set<Object>) txResults.get(i * 3 + 1);
            Set<Object> areaObjects = (Set<Object>) txResults.get(i * 3 + 2);

            if (depositObjects == null || rentObjects == null || areaObjects == null) {
                continue;
            }

            Set<String> intersectedIds = depositObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            Set<String> rentIds = rentObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            Set<String> areaIds = areaObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            intersectedIds.retainAll(rentIds);
            intersectedIds.retainAll(areaIds);

            if (!intersectedIds.isEmpty()) {
                districtCandidateIds.put(districts.get(i), intersectedIds);
            }
        }
        return districtCandidateIds;
    }

    /**
     * 전체 후보 매물의 Redis Hash 상세 정보를 단일 Pipeline으로 일괄 조회.
     * "property:monthly:{id}" 키에 대해 HGETALL을 Pipeline으로 묶어 실행하고,
     * 결과를 propertyId 기준 Map으로 변환하여 반환한다.
     *
     * @param candidateIds 교집합 통과한 전체 후보 propertyId 집합
     * @return propertyId → PropertyDetail 매핑 (Hash 누락·파싱 실패 건은 제외)
     */
    private Map<String, PropertyDetail> fetchPropertyDetailMap(Set<String> candidateIds) {
        Map<String, PropertyDetail> map = new HashMap<>();
        List<String> idList = new ArrayList<>(candidateIds);

        try {
            List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String propertyId : idList) {
                            connection.hGetAll(("property:monthly:" + propertyId).getBytes());
                        }
                        return null;
                    });

            for (int i = 0; i < idList.size(); i++) {
                Object result = pipelineResults.get(i);
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> propertyHash = (Map<Object, Object>) result;
                    if (!propertyHash.isEmpty()) {
                        PropertyDetail detail = convertHashToPropertyDetail(idList.get(i), propertyHash);
                        if (detail != null) {
                            map.put(detail.getPropertyId(), detail);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("월세 매물 Pipeline 조회 실패", e);
        }
        return map;
    }

    /**
     * 지역구별 hard condition 검증 후 통과 매물만 결과에 포함.
     * 교집합 후보 ID를 Hash 상세(propertyDetailMap)와 매칭한 뒤 matchesHardCondition()으로
     * leaseType, status, 보증금·월세금·면적 범위를 재검증하여 최종 통과 매물만 지역구별 리스트로 조립한다.
     *
     * @param districtCandidateIds 지역구명 → 교집합 통과 propertyId Set
     * @param propertyDetailMap    propertyId → Hash 상세 정보
     * @param request              hard condition 기준(보증금·월세금·면적 범위)을 포함하는 요청 DTO
     * @return 지역구명 → hard condition 통과 매물 상세 목록 (통과 매물 0건인 지역구는 제외)
     */
    private Map<String, List<PropertyDetail>> assembleValidatedResults(Map<String, Set<String>> districtCandidateIds,
                                                                       Map<String, PropertyDetail> propertyDetailMap,
                                                                       MonthlyRecommendationRequestDto request) {
        Map<String, List<PropertyDetail>> result = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : districtCandidateIds.entrySet()) {
            List<PropertyDetail> validProperties = entry.getValue().stream()
                    .map(propertyDetailMap::get)
                    .filter(Objects::nonNull)
                    .filter(detail -> matchesHardCondition(detail, request))
                    .collect(Collectors.toList());

            if (!validProperties.isEmpty()) {
                result.put(entry.getKey(), validProperties);
            }
        }
        return result;
    }

    /**
     * Redis Hash 실측값 기준 hard condition 검증.
     * ZSet 인덱스는 Write-Read 레이스에 의해 실제 값과 불일치할 수 있으므로,
     * Hash에서 조회한 실측 deposit·monthlyRent·areaInPyeong·leaseType·status로 재검증한다.
     */
    private boolean matchesHardCondition(PropertyDetail detail, MonthlyRecommendationRequestDto request) {
        if (detail == null) return false;
        if (detail.getDeposit() == null) return false;
        if (detail.getMonthlyRent() == null) return false;
        if (detail.getAreaInPyeong() == null) return false;
        if (!"월세".equals(detail.getLeaseType())) return false;
        if (!"ACTIVE".equals(detail.getStatus())) return false;
        return detail.getDeposit() >= request.getBudgetMin()
                && detail.getDeposit() <= request.getBudgetMax()
                && detail.getMonthlyRent() >= request.getMonthlyRentMin()
                && detail.getMonthlyRent() <= request.getMonthlyRentMax()
                && detail.getAreaInPyeong() >= request.getAreaMin()
                && detail.getAreaInPyeong() <= request.getAreaMax();
    }

    // ========================================
    // S-02 ~ S-03: 폴백 관련 메소드들
    // ========================================

    /**
     * S-02: 월세 매물 폴백 조건 판단 및 확장 검색 수행.
     * 검증 통과 매물 개수 기준 — 3개 미만이면 fallback 대상.
     */
    private SearchResult checkAndPerformMonthlyFallback(Map<String, List<PropertyDetail>> districtProperties,
                                                        MonthlyRecommendationRequestDto request) {

        if (districtProperties.isEmpty()) {
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 월세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        boolean hasInsufficientDistricts = districtProperties.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!hasInsufficientDistricts) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_NORMAL")
                    .message("조건에 맞는 월세 매물을 성공적으로 찾았습니다.")
                    .districtProperties(districtProperties)
                    .build();
        }

        log.info("일부 월세 지역구의 매물 부족 - S-03 확장 검색 수행");
        SearchResult expandedResult = performMonthlyExpandedSearch(request, districtProperties);

        Map<String, List<PropertyDetail>> finalResult = new HashMap<>(districtProperties);

        List<String> insufficientDistricts = districtProperties.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        insufficientDistricts.forEach(finalResult::remove);
        finalResult.putAll(expandedResult.getDistrictProperties());

        return SearchResult.builder()
                .searchStatus(expandedResult.getSearchStatus())
                .message(expandedResult.getMessage())
                .districtProperties(finalResult)
                .build();
    }

    /**
     * S-03: Fallback - 월세 매물 확장 검색
     */
    private SearchResult performMonthlyExpandedSearch(MonthlyRecommendationRequestDto request,
                                                      Map<String, List<PropertyDetail>> originalResult) {

        List<String> insufficientDistricts = originalResult.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 1단계: 3순위 조건 완화
        MonthlyRecommendationRequestDto expandedRequest = relaxMonthlyThirdPriority(request);
        String relaxedCondition = getMonthlyRelaxedConditionMessage(request.getPriority3(), request, expandedRequest);

        Map<String, List<PropertyDetail>> expandedResult = performMonthlyStrictSearch(expandedRequest, insufficientDistricts);

        boolean stillInsufficient = expandedResult.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!stillInsufficient && !expandedResult.isEmpty()) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_EXPANDED")
                    .message("원하시는 조건의 월세 매물이 부족하여, " + relaxedCondition + " 완화하여 찾았어요.")
                    .districtProperties(expandedResult)
                    .build();
        }

        // 2단계: 2순위 조건 추가 완화
        MonthlyRecommendationRequestDto doubleExpandedRequest = relaxMonthlySecondPriority(expandedRequest, request);
        String doubleRelaxedCondition = relaxedCondition + ", " + getMonthlyRelaxedConditionMessage(request.getPriority2(), request, doubleExpandedRequest);

        Map<String, List<PropertyDetail>> doubleExpandedResult = performMonthlyStrictSearch(doubleExpandedRequest, insufficientDistricts);

        if (doubleExpandedResult.isEmpty() ||
                doubleExpandedResult.values().stream().mapToInt(List::size).sum() == 0) {
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 월세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        return SearchResult.builder()
                .searchStatus("SUCCESS_EXPANDED")
                .message("원하시는 조건의 월세 매물이 부족하여, " + doubleRelaxedCondition + " 완화하여 찾았어요.")
                .districtProperties(doubleExpandedResult)
                .build();
    }

    // ========================================
    // 월세 점수 계산 관련 메소드들 (Phase 2 핵심 로직)
    // ========================================

    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }

    /**
     * S-04: 매물 단위 점수 계산 (하이브리드 로직 적용).
     * 입력: hard condition 검증 통과된 매물(PropertyDetail). Redis Hash 재조회 없이 점수 계산만 수행.
     */
    private Map<String, List<PropertyWithScore>> calculateMonthlyPropertyScores(
            Map<String, List<PropertyDetail>> districtProperties, MonthlyRecommendationRequestDto request) {

        List<String> allPropertyIds = districtProperties.values().stream()
                .flatMap(List::stream)
                .map(PropertyDetail::getPropertyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, ReviewStatisticsMonthly> globalReviewStatsMap = new HashMap<>();

        if (!allPropertyIds.isEmpty()) {
            List<List<String>> chunks = partitionList(allPropertyIds, 61);
            for (List<String> chunk : chunks) {
                List<ReviewStatisticsMonthly> chunkStats = reviewStatisticsRepository.findAllById(chunk);
                for (ReviewStatisticsMonthly stat : chunkStats) {
                    globalReviewStatsMap.put(stat.getPropertyId(), stat);
                }
            }
        }

        Map<String, List<PropertyWithScore>> result = new HashMap<>();

        for (Map.Entry<String, List<PropertyDetail>> entry : districtProperties.entrySet()) {

            String districtName = entry.getKey();
            List<PropertyDetail> propertyDetails = entry.getValue();

            if (propertyDetails.isEmpty()) {
                result.put(districtName, Collections.emptyList());
                continue;
            }

            MonthlyScoreNormalizationBounds districtBounds = getMonthlyBoundsFromRedis(districtName);
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();

            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    double depositScore = calculateDepositScore(propertyDetail.getDeposit(), districtBounds);
                    double monthlyRentScore = calculateMonthlyRentScore(propertyDetail.getMonthlyRent(), districtBounds);
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
                    double safetyScore = propertyDetail.getSafetyScore() != null ?
                            propertyDetail.getSafetyScore() : districtSafetyScore;

                    double displayPriceScore = (depositScore + monthlyRentScore) / 2.0;

                    double legacyScore = calculateMonthlyWeightedFinalScore(
                            depositScore, monthlyRentScore, spaceScore, safetyScore,
                            request.getPriority1(), request.getPriority2(), request.getPriority3());

                    String propertyId = propertyDetail.getPropertyId();
                    ReviewStatisticsMonthly stats = globalReviewStatsMap.getOrDefault(propertyId,
                            ReviewStatisticsMonthly.builder().propertyId(propertyId).build());

                    double finalScore = calculateHybridScore(legacyScore, stats);

                    PropertyWithScore propertyWithScore = PropertyWithScore.builder()
                            .propertyDetail(propertyDetail)
                            .priceScore(displayPriceScore)
                            .spaceScore(spaceScore)
                            .safetyScore(safetyScore)
                            .legacyScore(legacyScore)
                            .reviewScore(calculateReviewScoreOnly(stats))
                            .finalScore(finalScore)
                            .reviewCount(stats.getReviewCount())
                            .avgRating(stats.getAvgRating().doubleValue())
                            .build();

                    propertiesWithScores.add(propertyWithScore);

                } catch (Exception e) {
                    log.debug("월세 매물 점수 계산 오류: {}", propertyDetail.getPropertyId(), e);
                }
            }

            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));
            result.put(districtName, propertiesWithScores);
        }

        return result;
    }

    private double calculateHybridScore(double legacyScore, ReviewStatisticsMonthly stats) {
        if (stats.getReviewCount() < COLD_START_REVIEW_COUNT) {
            return legacyScore;
        }
        double reviewScore = calculateReviewScoreOnly(stats);
        return (legacyScore * 0.5) + (reviewScore * 0.5);
    }

    private double calculateReviewScoreOnly(ReviewStatisticsMonthly stats) {
        double ratingScore = (stats.getAvgRating().doubleValue() / 5.0) * 100.0;
        int positive = stats.getPositiveKeywordCount();
        int negative = stats.getNegativeKeywordCount();
        double keywordScore;

        if (positive + negative == 0) {
            keywordScore = 50.0;
        } else {
            keywordScore = ((double) positive / (positive + negative)) * 100.0;
        }

        return (ratingScore * 0.5) + (keywordScore * 0.5);
    }

    /**
     * S-05: 지역구 단위 점수 계산 및 정렬
     */
    private List<DistrictWithScore> calculateDistrictScoresAndSort(
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores) {

        List<DistrictWithScore> districtScores = new ArrayList<>();

        for (Map.Entry<String, List<PropertyWithScore>> entry : districtPropertiesWithScores.entrySet()) {
            String districtName = entry.getKey();
            List<PropertyWithScore> propertiesWithScores = entry.getValue();

            if (propertiesWithScores.isEmpty()) {
                districtScores.add(DistrictWithScore.builder()
                        .districtName(districtName)
                        .propertiesWithScores(propertiesWithScores)
                        .averageFinalScore(0.0)
                        .propertyCount(0)
                        .representativeScore(0.0)
                        .build());
                continue;
            }

            double totalFinalScore = propertiesWithScores.stream()
                    .mapToDouble(PropertyWithScore::getFinalScore)
                    .sum();
            double averageFinalScore = totalFinalScore / propertiesWithScores.size();
            int propertyCount = propertiesWithScores.size();
            double representativeScore = averageFinalScore * Math.log(propertyCount + 1);

            districtScores.add(DistrictWithScore.builder()
                    .districtName(districtName)
                    .propertiesWithScores(propertiesWithScores)
                    .averageFinalScore(averageFinalScore)
                    .propertyCount(propertyCount)
                    .representativeScore(representativeScore)
                    .build());
        }

        districtScores.sort((d1, d2) -> {
            int scoreComparison = Double.compare(d2.getRepresentativeScore(), d1.getRepresentativeScore());
            if (scoreComparison != 0) return scoreComparison;
            return Integer.compare(d2.getPropertyCount(), d1.getPropertyCount());
        });

        return districtScores;
    }

    // ========================================
    // 월세 응답 생성 메소드들
    // ========================================

    /**
     * S-06: 월세 전용 최종 응답 생성
     */
    private MonthlyRecommendationResponseDto generateMonthlyFinalResponse(List<DistrictWithScore> sortedDistricts,
                                                                          SearchResult searchResult,
                                                                          MonthlyRecommendationRequestDto request,
                                                                          String currentUserId) {

        if ("NO_RESULTS".equals(searchResult.getSearchStatus()) || sortedDistricts.isEmpty()) {
            return MonthlyRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message(searchResult.getMessage())
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        List<DistrictWithScore> validDistricts = sortedDistricts.stream()
                .filter(district -> district.getPropertyCount() > 0)
                .limit(3)
                .collect(Collectors.toList());

        List<RecommendedMonthlyDistrictDto> recommendedDistricts = new ArrayList<>();

        for (int i = 0; i < validDistricts.size(); i++) {
            DistrictWithScore district = validDistricts.get(i);
            int rank = i + 1;

            List<TopMonthlyPropertyDto> topProperties = selectTopMonthlyProperties(district.getPropertiesWithScores(), 3, currentUserId);
            String summary = generateDistrictSummary(district, rank, request.getPriority1());

            Double averagePriceScore = calculateMonthlyAverageScore(district.getPropertiesWithScores(), "price");
            Double averageSpaceScore = calculateMonthlyAverageScore(district.getPropertiesWithScores(), "space");
            Double districtSafetyScore = calculateAverageFinalScore(district.getPropertiesWithScores());

            recommendedDistricts.add(RecommendedMonthlyDistrictDto.builder()
                    .rank(rank)
                    .districtName(district.getDistrictName())
                    .summary(summary)
                    .topProperties(topProperties)
                    .averagePriceScore(averagePriceScore)
                    .averageSpaceScore(averageSpaceScore)
                    .districtSafetyScore(districtSafetyScore)
                    .averageFinalScore(district.getAverageFinalScore())
                    .representativeScore(district.getRepresentativeScore())
                    .build());
        }

        return MonthlyRecommendationResponseDto.builder()
                .searchStatus(searchResult.getSearchStatus())
                .message(searchResult.getMessage())
                .recommendedDistricts(recommendedDistricts)
                .build();
    }

    private Double calculateMonthlyAverageScore(List<PropertyWithScore> propertiesWithScores, String scoreType) {
        if (propertiesWithScores == null || propertiesWithScores.isEmpty()) return 0.0;
        double totalScore = 0.0;
        for (PropertyWithScore property : propertiesWithScores) {
            switch (scoreType) {
                case "price": totalScore += property.getPriceScore(); break;
                case "space": totalScore += property.getSpaceScore(); break;
            }
        }
        return totalScore / propertiesWithScores.size();
    }

    private Double calculateAverageFinalScore(List<PropertyWithScore> propertiesWithScores) {
        if (propertiesWithScores == null || propertiesWithScores.isEmpty()) return 0.0;
        double totalScore = 0.0;
        for (PropertyWithScore property : propertiesWithScores) {
            totalScore += property.getFinalScore();
        }
        return totalScore / propertiesWithScores.size();
    }

    private List<TopMonthlyPropertyDto> selectTopMonthlyProperties(List<PropertyWithScore> propertiesWithScores, int maxCount, String currentUserId) {
        return propertiesWithScores.stream()
                .limit(maxCount)
                .map(pws -> convertToTopMonthlyPropertyDto(pws, currentUserId))
                .collect(Collectors.toList());
    }

    private TopMonthlyPropertyDto convertToTopMonthlyPropertyDto(PropertyWithScore propertyWithScore, String currentUserId) {
        PropertyDetail detail = propertyWithScore.getPropertyDetail();

        return TopMonthlyPropertyDto.builder()
                .propertyId(detail.getPropertyId())
                .propertyName(detail.getAptNm())
                .address(detail.getAddress())
                .price(detail.getDeposit())
                .monthlyRent(detail.getMonthlyRent())
                .leaseType("월세")
                .area(detail.getAreaInPyeong())
                .floor(detail.getFloor())
                .buildYear(detail.getBuildYear())
                .finalScore(propertyWithScore.getFinalScore())
                .reviewCount(propertyWithScore.getReviewCount())
                .avgRating(propertyWithScore.getAvgRating())
                .dataSource(detail.getDataSource())
                .status(detail.getStatus())
                .ownedByCurrentUser(
                        currentUserId != null
                        && currentUserId.equals(detail.getRegisteredUserId()))
                // 방문 예약 도입에 따른 정책 강화: 수정·상태 변경 모두 등록자 본인만 가능.
                .canEdit(
                        currentUserId != null
                        && currentUserId.equals(detail.getRegisteredUserId()))
                .canChangeStatus(
                        currentUserId != null
                        && currentUserId.equals(detail.getRegisteredUserId()))
                .build();
    }

    // ========================================
    // Redis 조회 메소드들
    // ========================================

    private MonthlyScoreNormalizationBounds getMonthlyBoundsFromRedis(String districtName) {
        try {
            String boundsKey = "bounds:" + districtName + ":월세";
            Map<Object, Object> boundsHash = redisHandler.redisTemplate.opsForHash().entries(boundsKey);

            if (boundsHash.isEmpty()) {
                return MonthlyScoreNormalizationBounds.builder().minDeposit(0.0).maxDeposit(50000.0)
                        .minMonthlyRent(0.0).maxMonthlyRent(500.0).minArea(10.0).maxArea(100.0).build();
            }
            return MonthlyScoreNormalizationBounds.builder()
                    .minDeposit(Double.parseDouble(boundsHash.get("minDeposit").toString()))
                    .maxDeposit(Double.parseDouble(boundsHash.get("maxDeposit").toString()))
                    .minMonthlyRent(Double.parseDouble(boundsHash.get("minMonthlyRent").toString()))
                    .maxMonthlyRent(Double.parseDouble(boundsHash.get("maxMonthlyRent").toString()))
                    .minArea(Double.parseDouble(boundsHash.get("minArea").toString()))
                    .maxArea(Double.parseDouble(boundsHash.get("maxArea").toString()))
                    .build();
        } catch (Exception e) {
            return MonthlyScoreNormalizationBounds.builder().minDeposit(0.0).maxDeposit(50000.0)
                    .minMonthlyRent(0.0).maxMonthlyRent(500.0).minArea(10.0).maxArea(100.0).build();
        }
    }

    private double getDistrictSafetyScoreFromRedis(String districtName) {
        try {
            String safetyKey = "safety:" + districtName;
            Map<Object, Object> safetyHash = redisHandler.redisTemplate.opsForHash().entries(safetyKey);
            if (safetyHash.isEmpty() || safetyHash.get("safetyScore") == null) return 50.0;
            return Double.parseDouble(safetyHash.get("safetyScore").toString());
        } catch (Exception e) {
            return 50.0;
        }
    }

    // ========================================
    // 점수 계산 헬퍼 메소드들
    // ========================================

    private double calculateDepositScore(Integer deposit, MonthlyScoreNormalizationBounds bounds) {
        if (deposit == null) return 0.0;
        double normalized = (deposit - bounds.getMinDeposit()) / (bounds.getMaxDeposit() - bounds.getMinDeposit());
        return Math.max(0.0, Math.min(100.0, 100.0 - (normalized * 100.0)));
    }

    private double calculateMonthlyRentScore(Integer monthlyRent, MonthlyScoreNormalizationBounds bounds) {
        if (monthlyRent == null) return 0.0;
        double normalized = (monthlyRent - bounds.getMinMonthlyRent()) / (bounds.getMaxMonthlyRent() - bounds.getMinMonthlyRent());
        return Math.max(0.0, Math.min(100.0, 100.0 - (normalized * 100.0)));
    }

    private double calculateSpaceScore(Double areaInPyeong, MonthlyScoreNormalizationBounds bounds) {
        if (areaInPyeong == null) return 0.0;
        double normalized = (areaInPyeong - bounds.getMinArea()) / (bounds.getMaxArea() - bounds.getMinArea());
        return Math.max(0.0, Math.min(100.0, normalized * 100.0));
    }

    private double calculateMonthlyWeightedFinalScore(double depositScore, double monthlyRentScore, double spaceScore, double safetyScore,
                                                      String priority1, String priority2, String priority3) {
        Map<String, Double> priorityWeights = new HashMap<>();
        priorityWeights.put(priority1, 0.6);
        priorityWeights.put(priority2, 0.3);
        priorityWeights.put(priority3, 0.1);

        double priceWeight = priorityWeights.getOrDefault("PRICE", 0.0);
        double weightedDepositScore = depositScore * (priceWeight * 0.5);
        double weightedMonthlyRentScore = monthlyRentScore * (priceWeight * 0.5);
        double weightedSpaceScore = spaceScore * priorityWeights.getOrDefault("SPACE", 0.0);
        double weightedSafetyScore = safetyScore * priorityWeights.getOrDefault("SAFETY", 0.0);

        return weightedDepositScore + weightedMonthlyRentScore + weightedSpaceScore + weightedSafetyScore;
    }

    private String generateDistrictSummary(DistrictWithScore district, int rank, String primaryPriority) {
        String priorityName = "종합";
        if ("PRICE".equals(primaryPriority)) priorityName = "가격";
        else if ("SAFETY".equals(primaryPriority)) priorityName = "안전";
        else if ("SPACE".equals(primaryPriority)) priorityName = "공간";

        if (rank == 1) {
            return String.format("%s 1순위 조건에 가장 부합하며, 조건 내 추천 매물이 %d건 있습니다.",
                    priorityName, district.getPropertyCount());
        } else {
            return String.format("%s 조건에서 %d위를 차지하며, 추천 매물이 %d건 있습니다.",
                    priorityName, rank, district.getPropertyCount());
        }
    }

    // ========================================
    // 폴백 조건 완화 메소드들
    // ========================================

    private MonthlyRecommendationRequestDto relaxMonthlyThirdPriority(MonthlyRecommendationRequestDto original) {
        MonthlyRecommendationRequestDto relaxed = copyMonthlyRequest(original);
        if ("PRICE".equals(original.getPriority3()) && original.getBudgetFlexibility() > 0) {
            int depositFlex = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
            relaxed.setBudgetMax(original.getBudgetMax() + depositFlex);
            int rentFlex = (int) (original.getMonthlyRentMax() * (original.getBudgetFlexibility() / 100.0));
            relaxed.setMonthlyRentMax(original.getMonthlyRentMax() + rentFlex);
        } else if ("SPACE".equals(original.getPriority3()) && original.getAbsoluteMinArea() > 0) {
            relaxed.setAreaMin(original.getAbsoluteMinArea());
        } else if ("SAFETY".equals(original.getPriority3()) && original.getMinSafetyScore() > 0) {
            relaxed.setMinSafetyScore(original.getMinSafetyScore());
        }
        return relaxed;
    }

    private MonthlyRecommendationRequestDto relaxMonthlySecondPriority(MonthlyRecommendationRequestDto expandedRequest,
                                                                       MonthlyRecommendationRequestDto original) {
        MonthlyRecommendationRequestDto doubleRelaxed = copyMonthlyRequest(expandedRequest);
        if ("PRICE".equals(original.getPriority2()) && original.getBudgetFlexibility() > 0) {
            int depositFlex = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
            doubleRelaxed.setBudgetMax(Math.max(doubleRelaxed.getBudgetMax(), original.getBudgetMax() + depositFlex));
            int rentFlex = (int) (original.getMonthlyRentMax() * (original.getBudgetFlexibility() / 100.0));
            doubleRelaxed.setMonthlyRentMax(Math.max(doubleRelaxed.getMonthlyRentMax(), original.getMonthlyRentMax() + rentFlex));
        } else if ("SPACE".equals(original.getPriority2()) && original.getAbsoluteMinArea() > 0) {
            doubleRelaxed.setAreaMin(Math.min(doubleRelaxed.getAreaMin(), original.getAbsoluteMinArea()));
        } else if ("SAFETY".equals(original.getPriority2()) && original.getMinSafetyScore() > 0) {
            doubleRelaxed.setMinSafetyScore(Math.min(doubleRelaxed.getMinSafetyScore(), original.getMinSafetyScore()));
        }
        return doubleRelaxed;
    }

    private MonthlyRecommendationRequestDto copyMonthlyRequest(MonthlyRecommendationRequestDto original) {
        return MonthlyRecommendationRequestDto.builder()
                .budgetMin(original.getBudgetMin()).budgetMax(original.getBudgetMax())
                .monthlyRentMin(original.getMonthlyRentMin()).monthlyRentMax(original.getMonthlyRentMax())
                .areaMin(original.getAreaMin()).areaMax(original.getAreaMax())
                .priority1(original.getPriority1()).priority2(original.getPriority2()).priority3(original.getPriority3())
                .budgetFlexibility(original.getBudgetFlexibility()).minSafetyScore(original.getMinSafetyScore())
                .absoluteMinArea(original.getAbsoluteMinArea()).build();
    }

    private String getMonthlyRelaxedConditionMessage(String priority, MonthlyRecommendationRequestDto original,
                                                     MonthlyRecommendationRequestDto relaxed) {
        if ("PRICE".equals(priority)) return String.format("보증금 조건을 %d만원, 월세 조건을 %d만원으로", relaxed.getBudgetMax(), relaxed.getMonthlyRentMax());
        if ("SPACE".equals(priority)) return "평수 조건을 " + relaxed.getAreaMin() + "평으로";
        if ("SAFETY".equals(priority)) return "안전성 점수 조건을 " + relaxed.getMinSafetyScore() + "점으로";
        return "검색 조건을";
    }

    // ========================================
    // 공통 유틸리티 메소드들
    // ========================================

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
                    .dataSource(getStringValue(propertyHash, "dataSource"))
                    .status(getStringValue(propertyHash, "status"))
                    .registeredUserId(getStringValue(propertyHash, "registeredUserId"))
                    .build();
        } catch (Exception e) { return null; }
    }

    private String getStringValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        if (value == null) return null;
        try { return Integer.valueOf(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Double getDoubleValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        if (value == null) return null;
        try { return Double.valueOf(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    // ========================================
    // 내부 클래스들
    // ========================================

    private static class SearchResult {
        private String searchStatus;
        private String message;
        private Map<String, List<PropertyDetail>> districtProperties;
        public static SearchResultBuilder builder() { return new SearchResultBuilder(); }
        public static class SearchResultBuilder {
            private String searchStatus;
            private String message;
            private Map<String, List<PropertyDetail>> districtProperties;
            public SearchResultBuilder searchStatus(String searchStatus) { this.searchStatus = searchStatus; return this; }
            public SearchResultBuilder message(String message) { this.message = message; return this; }
            public SearchResultBuilder districtProperties(Map<String, List<PropertyDetail>> districtProperties) { this.districtProperties = districtProperties; return this; }
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
        public Map<String, List<PropertyDetail>> getDistrictProperties() { return districtProperties; }
    }

    @lombok.Builder
    @lombok.Getter
    private static class PropertyWithScore {
        private PropertyDetail propertyDetail;
        private double priceScore;
        private double spaceScore;
        private double safetyScore;
        private double legacyScore;
        private double reviewScore;
        private double finalScore;
        private int reviewCount;
        private double avgRating;
    }

    @lombok.Builder
    @lombok.Getter
    private static class DistrictWithScore {
        private String districtName;
        private List<PropertyWithScore> propertiesWithScores;
        private double averageFinalScore;
        private int propertyCount;
        private double representativeScore;
    }

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
        private String dataSource;
        private String status;
        private String registeredUserId;
    }

    @lombok.Builder
    @lombok.Getter
    private static class MonthlyScoreNormalizationBounds {
        private double minDeposit;
        private double maxDeposit;
        private double minMonthlyRent;
        private double maxMonthlyRent;
        private double minArea;
        private double maxArea;
    }
}
