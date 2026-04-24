package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        // log.info("=== 전세 지역구 추천 서비스 시작 ===");
        // log.info("요청 조건: 전세금={}-{}, 평수={}-{}, 우선순위={},{},{}",
        //         request.getBudgetMin(), request.getBudgetMax(),
        //         request.getAreaMin(), request.getAreaMax(),
        //         request.getPriority1(), request.getPriority2(), request.getPriority3());

        try {
            // S-01: 전 지역구 1차 검색 (전세 전용 인덱스 사용)
            Map<String, List<String>> districtProperties = performCharterStrictSearch(request, SEOUL_DISTRICTS);

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
     * S-01: 전세 매물 1차 검색 (Strict Search) - 2차 폴백 과정 전 선별 과정 및 Fallback 과정도 마찬가지로 현재 메소드에서 수행.
     */
    private Map<String, List<String>> performCharterStrictSearch(CharterRecommendationRequestDto request,
                                                                 List<String> targetDistricts) {
         log.info("S-01: 전세 매물 검색 시작 - 대상: {}", targetDistricts.size());

         /* 1차 검색 : 모든 지역구 별 안전성 점수가 기준치(HTTP Request)에 부합하는 지역구만 선별 저장 컬렉션 */
        List<String> filteredDistricts = targetDistricts;

        /* 안전성 점수 기준 부합하는 지역구 목록만 선별 */
        if (request.getMinSafetyScore() != null && request.getMinSafetyScore() > 0) {
            filteredDistricts = targetDistricts.stream()
                    .filter(district -> {
                        double districtSafetyScore = getDistrictSafetyScoreFromRedis(district);
                        return districtSafetyScore >= request.getMinSafetyScore();
                    })
                    .collect(Collectors.toList());
        }

        Map<String, List<String>> result = new HashMap<>(); // 실제 매물 검색 결과인 매물 목록을 저장하는 컬렉션
        int totalFound = 0;

        /* 실 매물 1차 검색 로직 : 앞서 과정으로 안전성 요구 점수 충족하여 선발된 지역구들에 한하여 가격과 평수 인덱스(Redis)로 선별 */
        for (String district : filteredDistricts) {

            List<String> validProperties = findValidCharterPropertiesInDistrict(district, request); // 실 로직.

            /* 현재 지역구에 대한 매물 조회 결과를 결과를 저장 로직,  */
            if (!validProperties.isEmpty()) {

                result.put(district, validProperties);

                totalFound += validProperties.size();   // 현재 안 쓰는 변수.
            }
        }

        // log.info("전세 매물 검색 완료: 총 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
        return result;
    }

    /**
     * S-02: 전세 매물 폴백 조건 판단 및 확장 검색을 호출하는 지점
     */
    private SearchResult checkAndPerformCharterFallback(Map<String, List<String>> districtProperties,
                                                        CharterRecommendationRequestDto request) {

        /* 폴백 조건 판단 : 각 지역구 별 1차로 조회한 결과로 각 지역구 내 매물 개수가 3개 이하라면 fallback 대상으로써 이를 확인 과정. */
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

        Map<String, List<String>> finalResult = new HashMap<>(districtProperties);

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
     */
    private SearchResult performCharterExpandedSearch(CharterRecommendationRequestDto request,
                                                      Map<String, List<String>> originalResult) {

        /* FallBack 조회할 지역구 목록 추출 */
        List<String> insufficientDistricts = originalResult.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 1단계: 3순위 조건 완화 - 현재 시점에서 매물 수 추가 부족하다면 2단계로 진행.
        CharterRecommendationRequestDto expandedRequest = relaxCharterThirdPriority(request);      // 3순위 우선순위 항목에 대한 수치 조정
        String relaxedCondition = getCharterRelaxedConditionMessage(request.getPriority3(), request, expandedRequest);  // 3순위 우선순위 수치 조정에 따른 사용자 응답 메시지 작성

        Map<String, List<String>> expandedResult = performCharterStrictSearch(expandedRequest, insufficientDistricts);  // Fallback 수행 : s-01 재 수행

        boolean stillInsufficient = expandedResult.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!stillInsufficient && !expandedResult.isEmpty()) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_EXPANDED")
                    .message("원하시는 조건의 전세 매물이 부족하여, " + relaxedCondition + " 완화하여 찾았어요.")
                    .districtProperties(expandedResult)
                    .build();
        }

        /* !이후 추가 최적화 가능한 지점은 현재 2차 Fallback 탐색 내 "insufficientDistricts" 을 그대로 반영 후 재 검색 하나, "stillInsufficient" 내 다소 수정 하여 해당 지역구들만
        * 추가 탐색 가능함..*/

        // 2단계: 2순위 조건 추가 완화
        CharterRecommendationRequestDto doubleExpandedRequest = relaxCharterSecondPriority(expandedRequest, request); // 2순위 우선순위 항목에 대한 수치 조정
        String doubleRelaxedCondition = relaxedCondition + ", " + getCharterRelaxedConditionMessage(request.getPriority2(), request, doubleExpandedRequest);    // 2순위 우선순위 수치 조정에 따른 사용자 응답 메시지 작성

        Map<String, List<String>> doubleExpandedResult = performCharterStrictSearch(doubleExpandedRequest, insufficientDistricts);  // Fallback 수행 : s-01 재 수행

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
     * 전세 매물 검색 - 2개 인덱스 교집합 : 1차 검색과 2차 fallback 검색 모두 적용
     */
    private List<String> findValidCharterPropertiesInDistrict(String district, CharterRecommendationRequestDto request) {
        try {
            String charterPriceIndexKey = "idx:charterPrice:" + district;
            Set<Object> priceValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(charterPriceIndexKey, request.getBudgetMin(), request.getBudgetMax());

            if (priceValidObjects == null || priceValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> priceValidIds = priceValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            String areaIndexKey = "idx:area:" + district + ":전세";
            Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

            if (areaValidObjects == null || areaValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            Set<String> areaValidIds = areaValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            priceValidIds.retainAll(areaValidIds);
            return new ArrayList<>(priceValidIds);

        } catch (Exception e) {
            log.info("전세 매물 검색 중 오류 - 지역구: {}", district, e);
            return Collections.emptyList();
        }
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
     * * [Refactoring 적용 내용]
     * - 기존: 자치구별 루프 내에서 RDB 조회 (N+1 문제 및 하드 파싱 발생)
     * - 변경: 전체 매물 ID 추출 후 1,000개 단위 Chunking 조회 (Bulk Fetch + Soft Parsing 유도)
     */
    // 실제 호출되는 메서드 (시그니처 일치)
    private Map<String, List<PropertyWithScore>> calculateCharterPropertyScores(
            Map<String, List<String>> districtProperties, CharterRecommendationRequestDto request) {

        // log.info("S-04: 전세 매물 점수 계산 시작 (Hybrid Logic - Chunking Optimization)");

        // [측정 시작] 전체 로직 수행 시간
        // long totalLoopStart = System.currentTimeMillis();
        // long totalRdbTime = 0; // RDB 조회 누적 시간
        // int rdbCallCount = 0;  // RDB 호출 횟수

        // =================================================================================
        // 1. [Optimization] 전체 매물 ID 추출 및 RDB 청크 조회 (Loop 외부 실행)
        // =================================================================================

        // 1-1. 모든 지역구의 매물 ID를 하나로 병합 (Flatten) : 현재 매물 Id들에 대해서 각각 리뷰 데이터를 DBMS 조회하기 위함(Chunk 61 단위)
        List<String> allPropertyIds = districtProperties.values().stream()
                .flatMap(List::stream)
                .distinct() // 중복 제거
                .collect(Collectors.toList());

        Map<String, ReviewStatistics> globalReviewStatsMap = new HashMap<>();   // 모든 지역구의 각 매물 ID 별 해당하는 리뷰 점수 테이블 정보를 저장.

        // 1-2. Chunking (1,000개 단위 분할 조회) 적용
        if (!allPropertyIds.isEmpty()) {
            // 리스트 분할 (1000개씩)
            List<List<String>> chunks = partitionList(allPropertyIds, 61);  // Chunk 개수 1000 개 테스트 완료 -> 22개 테스트 완료 -> 61개 테스트 진행 중
            // log.info("[Profiling] 총 {}건 매물, {}개 Chunk로 분할하여 RDB 조회 시작", allPropertyIds.size(), chunks.size());

            for (List<String> chunk : chunks) {
                // long rdbStart = System.currentTimeMillis();

                // Chunk 단위 RDB 조회
                // 효과: IN 절 파라미터 개수가 1,000개 이하로 고정되어 DB 파싱 부하 감소 및 INLIST ITERATOR 효율화
                List<ReviewStatistics> chunkStats = reviewStatisticsRepository.findAllById(chunk);

                // long rdbDuration = System.currentTimeMillis() - rdbStart;
                // totalRdbTime += rdbDuration;
                // rdbCallCount++;

                // 조회 결과를 메모리 Map에 병합
                for (ReviewStatistics stat : chunkStats) {
                    globalReviewStatsMap.put(stat.getPropertyId(), stat);
                }
            }
            // log.info("[Profiling] Chunk 조회 완료 - 총 소요시간: {}ms, 총 호출: {}회", totalRdbTime, rdbCallCount);
        }

        // =================================================================================
        // 2. 지역구별 점수 계산 및 정렬 (기존 비즈니스 로직 100% 유지)
        // =================================================================================

        Map<String, List<PropertyWithScore>> result = new HashMap<>();

        /* 각 지역구를 순차적으로 순회하면서 각 지역구 별 매물 ID 들을 기준으로 Reids 조회 */
        for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {   // districtProperties : 각 지역구 이름 별 매물 ID들을 저장한 컬렉션(요청 파라메터에 포함)

            String districtName = entry.getKey();
            List<String> propertyIds = entry.getValue();

            if (propertyIds.isEmpty()) {
                result.put(districtName, Collections.emptyList());
                continue;
            }

            // 2-1. 매물 상세 정보 조회 (Redis - Pipeline 유지) : 현재 지역구 이름 내 포함된 매물 Id들.
            List<PropertyDetail> propertyDetails = getMultipleCharterPropertiesFromRedis(propertyIds);

            // [변경점] RDB 조회 로직 제거됨 (위에서 미리 조회한 globalReviewStatsMap 사용)
            // Map<String, ReviewStatistics> reviewStatsMap = getReviewStatisticsFromRDB(propertyIds); <--- 삭제됨

            // 2-2. 기준 데이터 조회 : 지역구를 순차적으로 순회하면서 각 지역구 별 매물 ID 들을 기준으로 Reids 조회할 때에 현재 지역구 이름에 대한
            
            /* 현재 조회 중인 지역구(지역구 이름 기준)애 대한 전세금 최소/최대 및 안전성 최소/최대 정보를 객체로써 가져옴, 이 객체를 적용 하여 현재 지역구 내 각 메물에 대한 가격 점수 및 안전성 점수를 측정 */
            ScoreNormalizationBounds districtBounds = getCharterBoundsFromRedis(districtName);  
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);         // 현재 조회 중인 지역구(지역구 이름 기준)에 대한 안전성 점수를 가져옴

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();                   // 가격, 공간, 안정성 점수를 저장하는 객체(PropertyWithScore) 컬렉션

            /* == 현재 지역구 내 각 매물들을 id 별 순차적으로 각 매물 점수 산정 및 리뷰 점수 계산 후 통합. == */
            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    // === 4-1. 정량적 점수(매물 자체에 대한 점수 - 리뷰 점수 제외) 산출 (기존 로직 유지) ===
                    double priceScore = calculatePriceScore(propertyDetail.getDeposit(), districtBounds);       // 현재 매물에 대한 가격 점수 계산(가격이 낮을 수록 높은 점수(최대 100점) - 설계 명세서 명시
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);  // 현재 매물에 대한 평수 점수 계산 - 설계 명세서 명시
                    double safetyScore = propertyDetail.getSafetyScore() != null ?                              // 현재 매물 자체에 대한 안전성 점수는 없으며 지역구 단위로 미리 배치에서 계산한 점수 사용.
                            propertyDetail.getSafetyScore() : districtSafetyScore;

                    /* 최종 매물 점수 산출 : 현재 매물. */
                    double legacyScore = calculateWeightedFinalScore(
                            priceScore, spaceScore, safetyScore,
                            request.getPriority1(), request.getPriority2(), request.getPriority3());

                    // === 4-2. 정성적 점수(Review Score) 산출 로직 및 기존 각 매물 점수와 통합 ===
                    String propertyId = propertyDetail.getPropertyId();     // 현재 지역구 내 현재 선택된 매물 정보 가져오기.

                    /* 현재 점수 계산 중인 매물 ID 에 대한 매물 점수 테이블 정보 로드 */
                    ReviewStatistics stats = globalReviewStatsMap.getOrDefault(propertyId,
                            ReviewStatistics.builder().propertyId(propertyId).build());

                    /* 기존 매물 자체에 대한 최종 점수와 매물 ID 를 합산하여 현재 매물에 대한 최종 점수 확정 */
                    double finalScore = calculateHybridScore(legacyScore, stats);

                    // 결과 객체 생성
                    PropertyWithScore propertyWithScore = PropertyWithScore.builder()
                            .propertyDetail(propertyDetail)
                            .priceScore(priceScore)
                            .spaceScore(spaceScore)
                            .safetyScore(safetyScore)
                            .legacyScore(legacyScore) // 디버깅용
                            .reviewScore(calculateReviewScoreOnly(stats)) // 디버깅용
                            .finalScore(finalScore)
                            .reviewCount(stats.getReviewCount())
                            .avgRating(stats.getAvgRating().doubleValue())
                            .build();

                    propertiesWithScores.add(propertyWithScore);

                } catch (Exception e) {
                    log.debug("전세 매물 점수 계산 오류: {}", propertyDetail.getPropertyId(), e);
                }
            }

            // 점수 내림차순 정렬 (기존 로직 유지)
            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));
            result.put(districtName, propertiesWithScores);
        }

        // [측정 종료] 결과 리포트
        // long totalLoopEnd = System.currentTimeMillis();
        // long totalDuration = totalLoopEnd - totalLoopStart;

        // log.info("=== [Bottleneck Resolved: 2.1.1 (Chunking)] ===");
        // log.info("1. 총 소요 시간: {}ms", totalDuration);
        // log.info("2. RDB 조회 시간 (Chunk Sum): {}ms (전체의 {}%)", totalRdbTime, String.format("%.1f", (double)totalRdbTime/totalDuration * 100));
        // log.info("3. RDB 호출 횟수: {}회 (Chunk 단위 실행)", rdbCallCount);
        // log.info("==============================================");

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
                .build();
    }

    // ========================================
    // 전세 Redis 조회 메소드들 (기존 로직 유지)
    // ========================================

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
        private Map<String, List<String>> districtProperties;

        public static SearchResultBuilder builder() { return new SearchResultBuilder(); }

        public static class SearchResultBuilder {
            private String searchStatus;
            private String message;
            private Map<String, List<String>> districtProperties;
            public SearchResultBuilder searchStatus(String searchStatus) { this.searchStatus = searchStatus; return this; }
            public SearchResultBuilder message(String message) { this.message = message; return this; }
            public SearchResultBuilder districtProperties(Map<String, List<String>> districtProperties) { this.districtProperties = districtProperties; return this; }
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