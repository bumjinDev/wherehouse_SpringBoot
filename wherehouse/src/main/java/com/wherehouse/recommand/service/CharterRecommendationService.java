package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
//import com.wherehouse.test.F009RaceLatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 전세 전용 추천 서비스 - 명세서 4.2절 및 10.2절(Phase 2)
 *
 * 역할:
 * 1. Redis 인덱스를 활용한 매물 1차 검색 (가격, 평수 조건)
 * 2. RDB(ReviewStatistics) 조회 및 하이브리드 점수 계산 (정량+정성)
 * 3. 최종 추천 리스트 생성 및 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharterRecommendationService {

    private final RedisHandler redisHandler;
    // [Phase 2 추가] 리뷰 통계 조회를 위한 Repository 주입 (RDB 접근)
    private final ReviewStatisticsRepository reviewStatisticsRepository;

//    @Autowired(required = false)
//    private F009RaceLatch f009RaceLatch;

    // 서울시 25개 자치구 목록
    private static final List<String> SEOUL_DISTRICTS = Arrays.asList(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구",
            "강북구", "도봉구", "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구",
            "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구"
    );

    private static final int MIN_PROPERTIES_THRESHOLD = 3; // 폴백 기준 매물 수
    private static final int COLD_START_REVIEW_COUNT = 5;  // 리뷰 점수 반영 최소 기준

    /**
     * 전세 지역구 추천 메인 메소드
     * S-01 ~ S-06 단계를 순차적으로 수행하여 전세 매물 기반 지역구 추천
     */
    public CharterRecommendationResponseDto getCharterDistrictRecommendations(CharterRecommendationRequestDto request, String currentUserId) {

        // [F005] 익명 인증 주체 정규화 (설계 섹션 9.5.4)
        currentUserId = "anonymousUser".equals(currentUserId) ? null : currentUserId;

        log.info("=== 전세 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 전세금={}-{}, 평수={}-{}, 우선순위={},{},{}, 요청자: ={}",
                request.getBudgetMin(), request.getBudgetMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3(),
                currentUserId);

        try {
            // S-01: 전 지역구 1차 검색 (인덱스 조회 + Hash 상세 조회 + hard condition 검증)
            Map<String, List<PropertyDetail>> districtProperties = performCharterStrictSearch(request, SEOUL_DISTRICTS);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformCharterFallback(districtProperties, request);

            // S-04: 매물 단위 점수 계산 (하이브리드 로직 적용)
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores =
                    calculateCharterPropertyScores(searchResult.getDistrictProperties(), request);

            // S-05: 지역구 단위 점수 계산 및 정렬
            List<DistrictWithScore> sortedDistricts = calculateDistrictScoresAndSort(districtPropertiesWithScores);

            // S-06: 최종 응답 생성 (전세 전용 DTO)
            CharterRecommendationResponseDto finalResponse = generateCharterFinalResponse(sortedDistricts, searchResult, request, currentUserId);

            // log.info("=== 전세 지역구 추천 서비스 완료 ===");
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
     * S-01: 전세 매물 1차 검색 (Strict Search) — 배치 최적화.
     *
     * 전체 지역구 ZSet 조회를 단일 MULTI/EXEC, Hash 상세 조회를 단일 Pipeline으로 실행하여
     * Redis 라운드트립을 최소화한다.
     *
     * @param request         사용자 요청 DTO (가격 범위, 평수 범위, 안전성 기준 등)
     * @param targetDistricts 검색 대상 지역구 목록 (최초 호출 시 서울 25개구, Fallback 시 부족 지역구만)
     * @return 지역구명 → hard condition 통과 매물 상세 목록
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<PropertyDetail>> performCharterStrictSearch(CharterRecommendationRequestDto request,
                                                                         List<String> targetDistricts) {

        /* 1단계: 안전성 점수 기준 미달 지역구 제외 → 이후 단계에서 불필요한 ZSet 조회 방지 */
        List<String> filteredDistricts = filterDistrictsBySafetyScore(targetDistricts, request);
        if (filteredDistricts.isEmpty()) {
            return Collections.emptyMap();
        }

        /* 2단계: 전 지역구 가격·면적 인덱스(ZSet)를 단일 MULTI/EXEC로 원자적 배치 조회 → 지역구당 2개(가격,면적) Set 반환 */
        List<Object> txResults = executeZSetBatchQuery(filteredDistricts, request);
        if (txResults == null || txResults.size() < filteredDistricts.size() * 2) {
            return Collections.emptyMap();
        }

        /* 3단계: 지역구별 가격·면적 교집합(retainAll) → 양쪽 인덱스 모두 통과한 후보 propertyId 집합 도출 */
        Map<String, Set<String>> districtCandidateIds = calculateIntersections(filteredDistricts, txResults);

        Set<String> allCandidateIds = districtCandidateIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allCandidateIds.isEmpty()) {
            return Collections.emptyMap();
        }

//        // F009 테스트 훅: ZSet 후보 확정 직후 Writer 스레드 재개
//        if (f009RaceLatch != null) {
//            f009RaceLatch.releaseWriterIfTargetIncluded(allCandidateIds);
//        }

        /* 4단계: 전체 후보 propertyId에 대해 단일 Pipeline으로 Hash 상세 조회 → propertyId 기준 Map 변환 */
        Map<String, PropertyDetail> propertyDetailMap = fetchPropertyDetailMap(allCandidateIds);

        /* 5단계: Hash 실측값 기준 hard condition 검증(leaseType, status, 가격·면적 범위) 후 통과 매물만 지역구별로 조립 */
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
                                                       CharterRecommendationRequestDto request) {
        if (request.getMinSafetyScore() == null || request.getMinSafetyScore() <= 0) {
            return targetDistricts;
        }
        return targetDistricts.stream()
                .filter(district -> getDistrictSafetyScoreFromRedis(district) >= request.getMinSafetyScore())
                .collect(Collectors.toList());
    }

    /**
     * 전체 지역구 가격·면적 ZSet 범위 조회를 단일 MULTI/EXEC로 실행.
     * 지역구당 2개 커맨드(가격 ZSet + 면적 ZSet)를 트랜잭션 큐에 적재하여 원자적으로 수행.
     * 결과 리스트 인덱스: [i*2] = 가격 범위 통과 ID Set, [i*2+1] = 면적 범위 통과 ID Set.
     *
     * @param districts 조회 대상 지역구 목록
     * @param request   가격·면적 범위를 포함하는 요청 DTO
     * @return MULTI/EXEC 결과 리스트 (지역구당 2개 Set 원소), 오류 시 null
     */
    private List<Object> executeZSetBatchQuery(List<String> districts,
                                                CharterRecommendationRequestDto request) {
        try {
            return redisHandler.redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();
                    for (String district : districts) {
                        operations.opsForZSet().rangeByScore(
                                "idx:charterPrice:" + district,
                                request.getBudgetMin(), request.getBudgetMax());
                        operations.opsForZSet().rangeByScore(
                                "idx:area:" + district + ":전세",
                                request.getAreaMin(), request.getAreaMax());
                    }
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.error("전세 매물 인덱스 배치 조회 중 오류", e);
            return null;
        }
    }

    /**
     * 지역구별 가격·면적 인덱스 교집합 계산.
     * MULTI/EXEC 결과에서 지역구당 가격 Set과 면적 Set을 추출, retainAll로 교집합하여
     * 양쪽 범위 모두 통과한 후보 propertyId 집합을 지역구별로 반환한다.
     *
     * @param districts 지역구 목록 (MULTI/EXEC 커맨드 순서와 1:1 대응)
     * @param txResults MULTI/EXEC 결과 리스트
     * @return 지역구명 → 가격·면적 교집합 통과 propertyId Set
     */
    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> calculateIntersections(List<String> districts, List<Object> txResults) {
        Map<String, Set<String>> districtCandidateIds = new LinkedHashMap<>();

        for (int i = 0; i < districts.size(); i++) {
            Set<Object> priceValidObjects = (Set<Object>) txResults.get(i * 2);
            Set<Object> areaValidObjects = (Set<Object>) txResults.get(i * 2 + 1);

            if (priceValidObjects == null || areaValidObjects == null) {
                continue;
            }

            Set<String> intersectedIds = priceValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            Set<String> areaValidIds = areaValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            intersectedIds.retainAll(areaValidIds);

            if (!intersectedIds.isEmpty()) {
                districtCandidateIds.put(districts.get(i), intersectedIds);
            }
        }
        return districtCandidateIds;
    }

    /**
     * 전체 후보 매물의 Redis Hash 상세 정보를 단일 Pipeline으로 일괄 조회.
     * "property:charter:{id}" 키에 대해 HGETALL을 Pipeline으로 묶어 실행하고,
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
                            connection.hGetAll(("property:charter:" + propertyId).getBytes());
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
            log.error("전세 매물 Pipeline 조회 실패", e);
        }
        return map;
    }

    /**
     * 지역구별 hard condition 검증 후 통과 매물만 결과에 포함.
     * 교집합 후보 ID를 Hash 상세(propertyDetailMap)와 매칭한 뒤 matchesHardCondition()으로
     * leaseType, status, 가격·면적 범위를 재검증하여 최종 통과 매물만 지역구별 리스트로 조립한다.
     *
     * @param districtCandidateIds 지역구명 → 교집합 통과 propertyId Set
     * @param propertyDetailMap    propertyId → Hash 상세 정보
     * @param request              hard condition 기준(가격·면적 범위)을 포함하는 요청 DTO
     * @return 지역구명 → hard condition 통과 매물 상세 목록 (통과 매물 0건인 지역구는 제외)
     */
    private Map<String, List<PropertyDetail>> assembleValidatedResults(Map<String, Set<String>> districtCandidateIds,
                                                                       Map<String, PropertyDetail> propertyDetailMap,
                                                                       CharterRecommendationRequestDto request) {
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
     * S-02: 전세 매물 폴백 조건 판단 및 확장 검색을 호출하는 지점.
     * 변경: 입력 타입 Map<String, List<PropertyDetail>> — hard condition 검증 통과 매물 개수 기준 fallback 판단.
     */
    private SearchResult checkAndPerformCharterFallback(Map<String, List<PropertyDetail>> districtProperties,
                                                        CharterRecommendationRequestDto request) {

        if (districtProperties.isEmpty()) {
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 전세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        /* 폴백 조건 판단 : 검증 통과 매물 개수 기준 — 3개 미만이면 fallback 대상 */
        boolean hasInsufficientDistricts = districtProperties.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        /* 모든 지역구가 매물 3개 이상 있다면 더 이상 조회할 필요 없음. */
        if (!hasInsufficientDistricts) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_NORMAL")
                    .message("조건에 맞는 전세 매물을 성공적으로 찾았습니다.")
                    .districtProperties(districtProperties)
                    .build();
        }

        /* 모든 지역구 내 한 개의 지역구라도 3개 이상의 매물이 확보되지 않는 지역구가 있다면 fallback 검색 진행 */
        log.info("일부 전세 지역구의 매물 부족 - S-03 : 확장 검색 수행");
        SearchResult expandedResult = performCharterExpandedSearch(request, districtProperties);

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
     * S-03: Fallback - 전세 매물 확장 검색
     * 변경: 입력/내부 타입 Map<String, List<PropertyDetail>> — 검증 통과 매물 기준.
     */
    private SearchResult performCharterExpandedSearch(CharterRecommendationRequestDto request,
                                                      Map<String, List<PropertyDetail>> originalResult) {

        /* FallBack 조회할 지역구 목록 추출 */
        List<String> insufficientDistricts = originalResult.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 1단계: 3순위 조건 완화 - 현재 시점에서 매물 수 추가 부족하다면 2단계로 진행.
        CharterRecommendationRequestDto expandedRequest = relaxCharterThirdPriority(request);
        String relaxedCondition = getCharterRelaxedConditionMessage(request.getPriority3(), request, expandedRequest);

        Map<String, List<PropertyDetail>> expandedResult = performCharterStrictSearch(expandedRequest, insufficientDistricts);

        boolean stillInsufficient = expandedResult.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!stillInsufficient && !expandedResult.isEmpty()) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_EXPANDED")
                    .message("원하시는 조건의 전세 매물이 부족하여, " + relaxedCondition + " 완화하여 찾았어요.")
                    .districtProperties(expandedResult)
                    .build();
        }

        // 2단계: 2순위 조건 추가 완화
        CharterRecommendationRequestDto doubleExpandedRequest = relaxCharterSecondPriority(expandedRequest, request);
        String doubleRelaxedCondition = relaxedCondition + ", " + getCharterRelaxedConditionMessage(request.getPriority2(), request, doubleExpandedRequest);

        Map<String, List<PropertyDetail>> doubleExpandedResult = performCharterStrictSearch(doubleExpandedRequest, insufficientDistricts);

        if (doubleExpandedResult.isEmpty() ||
                doubleExpandedResult.values().stream().mapToInt(List::size).sum() == 0) {
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 전세 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        return SearchResult.builder()
                .searchStatus("SUCCESS_EXPANDED")
                .message("원하시는 조건의 전세 매물이 부족하여, " + doubleRelaxedCondition + " 완화하여 찾았어요.")
                .districtProperties(doubleExpandedResult)
                .build();
    }

    /**
     * Redis Hash 실측값 기준 hard condition 검증.
     * ZSet 인덱스는 Write-Read 레이스에 의해 실제 값과 불일치할 수 있으므로,
     * Hash에서 조회한 실측 deposit·areaInPyeong·leaseType·status로 재검증한다.
     * 응답에 포함되는 모든 매물은 이 조건을 만족해야 한다.
     */
    private boolean matchesHardCondition(PropertyDetail detail, CharterRecommendationRequestDto request) {
        if (detail == null) return false;
        if (detail.getDeposit() == null) return false;
        if (detail.getAreaInPyeong() == null) return false;
        if (!"전세".equals(detail.getLeaseType())) return false;
        if (!"ACTIVE".equals(detail.getStatus())) return false;
        return detail.getDeposit() >= request.getBudgetMin()
                && detail.getDeposit() <= request.getBudgetMax()
                && detail.getAreaInPyeong() >= request.getAreaMin()
                && detail.getAreaInPyeong() <= request.getAreaMax();
    }

    // ========================================
    // 전세 점수 계산 관련 메소드들 (Phase 2 핵심 로직)
    // ========================================
    /**
     * [Helper Method] 리스트를 특정 크기(partitionSize)로 분할하는 유틸리티 메서드
     * 용도: Oracle IN 절 제한(1,000개) 준수 및 파싱 부하 분산을 위한 Chunking
     */
    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {

        List<List<T>> partitions = new ArrayList<>();

        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }

    /**
     * S-04: 매물 단위 점수 계산 (하이브리드 로직 적용)
     *
     * 변경: 입력 타입 Map<String, List<PropertyDetail>> — 이미 hard condition 검증 통과된 매물.
     *       Redis Hash 재조회 제거 — 검증된 PropertyDetail을 그대로 사용하여 점수 계산만 수행.
     */
    private Map<String, List<PropertyWithScore>> calculateCharterPropertyScores(
            Map<String, List<PropertyDetail>> districtProperties, CharterRecommendationRequestDto request) {

        // =================================================================================
        // 1. [Optimization] 전체 매물 ID 추출 및 RDB 청크 조회 (Loop 외부 실행)
        // =================================================================================

        // 1-1. 검증 통과 PropertyDetail에서 ID 추출하여 리뷰 통계 Chunk 조회용으로 사용
        List<String> allPropertyIds = districtProperties.values().stream()
                .flatMap(List::stream)
                .map(PropertyDetail::getPropertyId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, ReviewStatistics> globalReviewStatsMap = new HashMap<>();

        // 1-2. Chunking (61개 단위 분할 조회) 적용
        if (!allPropertyIds.isEmpty()) {
            List<List<String>> chunks = partitionList(allPropertyIds, 61);

            for (List<String> chunk : chunks) {
                List<ReviewStatistics> chunkStats = reviewStatisticsRepository.findAllById(chunk);

                for (ReviewStatistics stat : chunkStats) {
                    globalReviewStatsMap.put(stat.getPropertyId(), stat);
                }
            }
        }

        // =================================================================================
        // 2. 지역구별 점수 계산 및 정렬 (기존 비즈니스 로직 유지)
        // =================================================================================

        Map<String, List<PropertyWithScore>> result = new HashMap<>();

        for (Map.Entry<String, List<PropertyDetail>> entry : districtProperties.entrySet()) {

            String districtName = entry.getKey();
            List<PropertyDetail> propertyDetails = entry.getValue();

            if (propertyDetails.isEmpty()) {
                result.put(districtName, Collections.emptyList());
                continue;
            }

            ScoreNormalizationBounds districtBounds = getCharterBoundsFromRedis(districtName);
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();

            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    double priceScore = calculatePriceScore(propertyDetail.getDeposit(), districtBounds);
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
                    double safetyScore = propertyDetail.getSafetyScore() != null ?
                            propertyDetail.getSafetyScore() : districtSafetyScore;

                    double legacyScore = calculateWeightedFinalScore(
                            priceScore, spaceScore, safetyScore,
                            request.getPriority1(), request.getPriority2(), request.getPriority3());

                    String propertyId = propertyDetail.getPropertyId();

                    ReviewStatistics stats = globalReviewStatsMap.getOrDefault(propertyId,
                            ReviewStatistics.builder().propertyId(propertyId).build());

                    double finalScore = calculateHybridScore(legacyScore, stats);

                    PropertyWithScore propertyWithScore = PropertyWithScore.builder()
                            .propertyDetail(propertyDetail)
                            .priceScore(priceScore)
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
                    log.debug("전세 매물 점수 계산 오류: {}", propertyDetail.getPropertyId(), e);
                }
            }

            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));
            result.put(districtName, propertiesWithScores);
        }

        return result;
    }

    /**
     * [Phase 2] 하이브리드 점수 산출 로직
     * FinalScore = (LegacyScore * 0.5) + (ReviewScore * 0.5)
     * 단, 리뷰 개수가 5개 미만인 경우 LegacyScore 100% 반영
     */
    private double calculateHybridScore(double legacyScore, ReviewStatistics stats) {
        // Cold Start 방어 로직
        if (stats.getReviewCount() < COLD_START_REVIEW_COUNT) {
            return legacyScore;
        }

        // Review Score 계산
        double reviewScore = calculateReviewScoreOnly(stats);

        // 가중치 50:50 적용
        return (legacyScore * 0.5) + (reviewScore * 0.5);
    }

    /**
     * 리뷰 점수 자체 계산 로직 : 긍정 키워드 개수 및 부정 키워드 개수 합산 처리
     * ReviewScore = (RatingScore * 0.5) + (KeywordScore * 0.5)
     */
    private double calculateReviewScoreOnly(ReviewStatistics stats) {
        // 1. 평점 점수 (100점 만점 환산)
        double ratingScore = (stats.getAvgRating().doubleValue() / 5.0) * 100.0;

        // 2. 키워드 점수 (긍정 비율)
        int positive = stats.getPositiveKeywordCount();
        int negative = stats.getNegativeKeywordCount();
        double keywordScore;

        if (positive + negative == 0) {     // 긍정 키워드와 부정 키워드 모두 0개인 상황 : 중립 점수
            keywordScore = 50.0; // 중립
        } else {                            // 긍정 키워드 비율 기반 점수 산출 (절대 개수 차이가 아닌 전체 대비 긍정 비율 — 리뷰 양 보정은 Cold Start 분기에서 처리), 설계서와 다르게 개발.
            keywordScore = ((double) positive / (positive + negative)) * 100.0;
        }
        return (ratingScore * 0.5) + (keywordScore * 0.5);  // 리뷰 평균 점수와 키워드 점수는 가중치를 동등하게 설정
    }

    /**
     * [Phase 2] RDB에서 리뷰 통계 조회
     * Redis Pipeline 대신 RDB 조회 방식을 사용하여 병목 지점 생성
     */
    private Map<String, ReviewStatistics> getReviewStatisticsFromRDB(List<String> propertyIds) {
        if (propertyIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // SELECT * FROM REVIEW_STATISTICS WHERE PROPERTY_ID IN (...)
        List<ReviewStatistics> statsList = reviewStatisticsRepository.findAllById(propertyIds);

        return statsList.stream()
                .collect(Collectors.toMap(ReviewStatistics::getPropertyId, stats -> stats));
    }


    /**
     * S-05: 지역구 단위 점수 계산 및 정렬
     */
    private List<DistrictWithScore> calculateDistrictScoresAndSort(
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores) {

        log.info("S-05: 지역구 점수 계산 및 정렬 시작");

        List<DistrictWithScore> districtScores = new ArrayList<>(); // 각 지역구 별 점수 저장 컬렉션

        /* 각 지역구를 순회 하면서 각 지역구의 매물 기준으로 지역구 별 점수 산정 */
        for (Map.Entry<String, List<PropertyWithScore>> entry : districtPropertiesWithScores.entrySet()) {
            String districtName = entry.getKey();
            List<PropertyWithScore> propertiesWithScores = entry.getValue();

            // 매물이 없는 자치구: 0점 기본값으로 DistrictWithScore를 생성하되,
            // propertiesWithScores에 빈 리스트(non-null)를 세팅하여 이후 stream/size 호출 시 NPE 방어
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

            /* 현재 선택된 지역구에 대한 실제 지역구 점수 계산. */
            // 해당 자치구 내 모든 매물의 finalScore 합산
            double totalFinalScore = propertiesWithScores.stream()
                    .mapToDouble(PropertyWithScore::getFinalScore)
                    .sum();
            // 자치구 평균 점수: 매물 간 품질 편차를 평탄화한 자치구 단위 지표
            double averageFinalScore = totalFinalScore / propertiesWithScores.size();
            // 조건 부합 매물 수: representativeScore 보정 계수 및 동점 시 2차 정렬 기준
            int propertyCount = propertiesWithScores.size();
            // 자치구 대표 점수: 평균 점수에 log(매물수+1)을 곱하여 매물 풍부도 반영
            // — 소수 고점 매물만 있는 자치구가 다수 매물 자치구를 역전하는 것을 방지
            double representativeScore = averageFinalScore * Math.log(propertyCount + 1);

            // 산출된 지표들을 DistrictWithScore 객체로 조립
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
            return Integer.compare(d2.getPropertyCount(), d1.getPropertyCount());
        });

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
                                                                          CharterRecommendationRequestDto request,
                                                                          String currentUserId) {
        // log.info("S-06: 전세 최종 응답 생성 시작");

        /* 검색 상태가 NO_RESULTS이거나 정렬된 자치구 리스트 자체가 비어 있으면, 빈 추천 리스트와 함께 "NO_RESULTS" 상태의 응답 DTO를 즉시 반환하고 종료한다. (S-03 확장 검색까지 수행했음에도 매물을 찾지 못한 경우) */
        if ("NO_RESULTS".equals(searchResult.getSearchStatus()) || sortedDistricts.isEmpty()) {
            return CharterRecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message(searchResult.getMessage())
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        /* 지역구 목록 중 추천 매물 개수가 3개 이상인 지역구 들만 선별 후 List 컬렉션 내 저장. */
        List<DistrictWithScore> validDistricts = sortedDistricts.stream()
                .filter(district -> district.getPropertyCount() > 0)
                .limit(3)
                .collect(Collectors.toList());

        List<RecommendedCharterDistrictDto> recommendedDistricts = new ArrayList<>();

        for (int i = 0; i < validDistricts.size(); i++) {
            DistrictWithScore district = validDistricts.get(i);
            int rank = i + 1;

            List<TopCharterPropertyDto> topProperties = selectTopCharterProperties(district.getPropertiesWithScores(), 3, currentUserId);
            String summary = generateDistrictSummary(district, rank, request.getPriority1());

            Double averagePriceScore = calculateAverageScore(district.getPropertiesWithScores(), "price");
            Double averageSpaceScore = calculateAverageScore(district.getPropertiesWithScores(), "space");
            Double districtSafetyScore = calculateAverageFinalScore(district.getPropertiesWithScores());

            RecommendedCharterDistrictDto districtDto = RecommendedCharterDistrictDto.builder()
                    .rank(rank)
                    .districtName(district.getDistrictName())
                    .summary(summary)
                    .topProperties(topProperties)
                    .averagePriceScore(averagePriceScore)
                    .averageSpaceScore(averageSpaceScore)
                    .districtSafetyScore(districtSafetyScore)
                    .averageFinalScore(district.getAverageFinalScore())
                    .representativeScore(district.getRepresentativeScore())
                    .build();

            recommendedDistricts.add(districtDto);
        }

        return CharterRecommendationResponseDto.builder()
                .searchStatus(searchResult.getSearchStatus())
                .message(searchResult.getMessage())
                .recommendedDistricts(recommendedDistricts)
                .build();
    }

    private Double calculateAverageScore(List<PropertyWithScore> propertiesWithScores, String scoreType) {
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

    private List<TopCharterPropertyDto> selectTopCharterProperties(List<PropertyWithScore> propertiesWithScores, int maxCount, String currentUserId) {
        return propertiesWithScores.stream()
                .limit(maxCount)
                .map(pws -> convertToTopCharterPropertyDto(pws, currentUserId))
                .collect(Collectors.toList());
    }

    /**
     * DTO 변환 시 리뷰 통계 정보 매핑
     */
    private TopCharterPropertyDto convertToTopCharterPropertyDto(PropertyWithScore propertyWithScore, String currentUserId) {
        PropertyDetail detail = propertyWithScore.getPropertyDetail();

        return TopCharterPropertyDto.builder()
                .propertyId(detail.getPropertyId())
                .propertyName(detail.getAptNm())
                .address(detail.getAddress())
                .price(detail.getDeposit())
                .leaseType("전세")
                .area(detail.getAreaInPyeong())
                .floor(detail.getFloor())
                .buildYear(detail.getBuildYear())
                .finalScore(propertyWithScore.getFinalScore())
                .reviewCount(propertyWithScore.getReviewCount())
                .avgRating(propertyWithScore.getAvgRating())
                // [F005] 매물 출처·상태·본인 소유 여부
                .dataSource(detail.getDataSource())
                .status(detail.getStatus())
                .ownedByCurrentUser(
                        currentUserId != null
                                && currentUserId.equals(detail.getRegisteredUserId()))
                .canEdit(currentUserId != null) // 인증된 사용자라면 누구나 수정 가능
                .canChangeStatus(               // 매물 상태 변경은 매물 등록자 본인만 가능
                        currentUserId != null
                                && currentUserId.equals(detail.getRegisteredUserId()))
                .build();
    }

    // ========================================
    // 전세 Redis 조회 메소드들 (기존 로직 유지)
    // ========================================

    /* 지역구 전세금 최소/최대 값 및 공간 최소/최대 값을 Redis Hash 연산자로 가져 온다.*/
    private ScoreNormalizationBounds getCharterBoundsFromRedis(String districtName) {
        try {

            String boundsKey = "bounds:" + districtName + ":전세";
            Map<Object, Object> boundsHash = redisHandler.redisTemplate.opsForHash().entries(boundsKey);

            if (boundsHash.isEmpty()) { // ScoreNormalizationBounds : "boundsHash" 데이터를 객체로써 저장하기 위한 private 클래스
                return ScoreNormalizationBounds.builder().minPrice(0.0).maxPrice(100000.0).minArea(10.0).maxArea(100.0).build();
            }
            return ScoreNormalizationBounds.builder()
                    .minPrice(Double.parseDouble(boundsHash.get("minPrice").toString()))
                    .maxPrice(Double.parseDouble(boundsHash.get("maxPrice").toString()))
                    .minArea(Double.parseDouble(boundsHash.get("minArea").toString()))
                    .maxArea(Double.parseDouble(boundsHash.get("maxArea").toString()))
                    .build();
        } catch (Exception e) {
            return ScoreNormalizationBounds.builder().minPrice(0.0).maxPrice(100000.0).minArea(10.0).maxArea(100.0).build();
        }
    }

    /* 해당 지역구의 안정성 점수를 Redis 로부터 로드. */
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
    // 전세 점수 계산 헬퍼 메소드들
    // ========================================

    private double calculatePriceScore(Integer price, ScoreNormalizationBounds bounds) {
        if (price == null) return 0.0;
        double normalizedPrice = (price - bounds.getMinPrice()) / (bounds.getMaxPrice() - bounds.getMinPrice());
        return Math.max(0.0, Math.min(100.0, 100.0 - (normalizedPrice * 100.0)));
    }

    private double calculateSpaceScore(Double areaInPyeong, ScoreNormalizationBounds bounds) {
        if (areaInPyeong == null) return 0.0;
        double normalizedArea = (areaInPyeong - bounds.getMinArea()) / (bounds.getMaxArea() - bounds.getMinArea());
        return Math.max(0.0, Math.min(100.0, normalizedArea * 100.0));
    }

    /* 매물 최종 점수 산출 : 우선순위 요소 3개에 대한 각각의 가중치 설정 및 그에 따른 점수 계산, 최종 비율 1.0 */
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
    // 전세 폴백 조건 완화 메소드들
    // ========================================

    private CharterRecommendationRequestDto relaxCharterThirdPriority(CharterRecommendationRequestDto original) {
        CharterRecommendationRequestDto relaxed = copyCharterRequest(original);
        if ("PRICE".equals(original.getPriority3()) && original.getBudgetFlexibility() > 0) {
            int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
            relaxed.setBudgetMax(original.getBudgetMax() + flexAmount);
        } else if ("SPACE".equals(original.getPriority3()) && original.getAbsoluteMinArea() > 0) {
            relaxed.setAreaMin(original.getAbsoluteMinArea());
        } else if ("SAFETY".equals(original.getPriority3()) && original.getMinSafetyScore() > 0) {
            relaxed.setMinSafetyScore(original.getMinSafetyScore());
        }
        return relaxed;
    }

    private CharterRecommendationRequestDto relaxCharterSecondPriority(CharterRecommendationRequestDto expandedRequest,
                                                                       CharterRecommendationRequestDto original) {
        CharterRecommendationRequestDto doubleRelaxed = copyCharterRequest(expandedRequest);
        if ("PRICE".equals(original.getPriority2()) && original.getBudgetFlexibility() > 0) {
            int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
            doubleRelaxed.setBudgetMax(Math.max(doubleRelaxed.getBudgetMax(), original.getBudgetMax() + flexAmount));
        } else if ("SPACE".equals(original.getPriority2()) && original.getAbsoluteMinArea() > 0) {
            doubleRelaxed.setAreaMin(Math.min(doubleRelaxed.getAreaMin(), original.getAbsoluteMinArea()));
        } else if ("SAFETY".equals(original.getPriority2()) && original.getMinSafetyScore() > 0) {
            doubleRelaxed.setMinSafetyScore(Math.min(doubleRelaxed.getMinSafetyScore(), original.getMinSafetyScore()));
        }
        return doubleRelaxed;
    }

    private CharterRecommendationRequestDto copyCharterRequest(CharterRecommendationRequestDto original) {
        return CharterRecommendationRequestDto.builder()
                .budgetMin(original.getBudgetMin()).budgetMax(original.getBudgetMax())
                .areaMin(original.getAreaMin()).areaMax(original.getAreaMax())
                .priority1(original.getPriority1()).priority2(original.getPriority2()).priority3(original.getPriority3())
                .budgetFlexibility(original.getBudgetFlexibility()).minSafetyScore(original.getMinSafetyScore())
                .absoluteMinArea(original.getAbsoluteMinArea()).build();
    }

    private String getCharterRelaxedConditionMessage(String priority, CharterRecommendationRequestDto original,
                                                     CharterRecommendationRequestDto relaxed) {
        if ("PRICE".equals(priority)) return "전세금 조건을 " + relaxed.getBudgetMax() + "만원으로";
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
        } catch (Exception e) {
            return null;
        }
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
        private double legacyScore; // [Phase 2] 디버깅용 정량 점수
        private double reviewScore; // [Phase 2] 디버깅용 리뷰 점수
        private double finalScore;  // [Phase 2] 최종 점수
        private int reviewCount;    // [Phase 2] 리뷰 개수
        private double avgRating;   // [Phase 2] 평균 별점
    }

    @lombok.Builder
    @lombok.Getter
    private static class DistrictWithScore {
        private String districtName;                          // 자치구명 (예: "강남구")
        private List<PropertyWithScore> propertiesWithScores; // 해당 자치구 내 조건 부합 매물별 점수 리스트
        private double averageFinalScore;                     // 자치구 내 전체 매물 finalScore 산술 평균
        private int propertyCount;                            // 조건 부합 매물 수 — representativeScore 산출 및 동점 시 2차 정렬 기준
        private double representativeScore;                   // 자치구 대표 점수 (averageFinalScore * log(propertyCount+1)) — 1차 정렬 기준
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
        private String dataSource;        // [F005]
        private String status;            // [F005]
        private String registeredUserId;  // [F005] ownedByCurrentUser 산출용
    }

    @lombok.Builder
    @lombok.Getter
    private static class ScoreNormalizationBounds {
        private double minPrice;
        private double maxPrice;
        private double minArea;
        private double maxArea;
    }
}