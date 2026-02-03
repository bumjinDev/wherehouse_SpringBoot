package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 월세 전용 추천 서비스 - 명세서 4.3절 및 10.2절(Phase 2)
 *
 * 역할:
 * 1. Redis 인덱스를 활용한 매물 1차 검색 (보증금, 월세, 평수 조건)
 * 2. RDB(ReviewStatistics) 조회 및 하이브리드 점수 계산 (정량+정성)
 * 3. 최종 추천 리스트 생성 및 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyRecommendationService {

    private final RedisHandler redisHandler;
    // [Phase 2 추가] 리뷰 통계 조회를 위한 Repository 주입 (RDB 접근 - 병목 시뮬레이션용)
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
     * 월세 지역구 추천 메인 메소드
     * S-01 ~ S-06 단계를 순차적으로 수행하여 월세 매물 기반 지역구 추천
     */
    public MonthlyRecommendationResponseDto getMonthlyDistrictRecommendations(MonthlyRecommendationRequestDto request) {
        log.info("=== 월세 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 보증금={}-{}, 월세={}-{}, 평수={}-{}, 우선순위={},{},{}",
                request.getBudgetMin(), request.getBudgetMax(),
                request.getMonthlyRentMin(), request.getMonthlyRentMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3());

        try {
            // S-01: 전 지역구 1차 검색 (월세 전용 인덱스 사용)
            Map<String, List<String>> districtProperties = performMonthlyStrictSearch(request, SEOUL_DISTRICTS);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformMonthlyFallback(districtProperties, request);

            // S-04: 매물 단위 점수 계산 (하이브리드 로직 적용)
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores =
                    calculateMonthlyPropertyScores(searchResult.getDistrictProperties(), request);

            // S-05: 지역구 단위 점수 계산 및 정렬
            List<DistrictWithScore> sortedDistricts = calculateDistrictScoresAndSort(districtPropertiesWithScores);

            // S-06: 최종 응답 생성 (월세 전용 DTO)
            MonthlyRecommendationResponseDto finalResponse = generateMonthlyFinalResponse(sortedDistricts, searchResult, request);

            log.info("=== 월세 지역구 추천 서비스 완료 ===");
            return finalResponse;

        } catch (Exception e) {
            log.error("월세 추천 서비스 처리 중 오류 발생", e);
            throw new RuntimeException("월세 추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    // ========================================
    // 월세 검색 관련 Private 메소드들
    // ========================================

    /**
     * S-01: 월세 매물 1차 검색 (Strict Search)
     * [Before 측정 버전] 25개 지역구 순차 순회 시간 측정
     */
    private Map<String, List<String>> performMonthlyStrictSearch(MonthlyRecommendationRequestDto request,
                                                                 List<String> targetDistricts) {
        log.info("S-01: 월세 매물 검색 시작 - 대상: {}", targetDistricts.size());

        // 전체 순회 시작 시간
        long loopStartNano = System.nanoTime();

        List<String> filteredDistricts = targetDistricts;
        if (request.getMinSafetyScore() != null && request.getMinSafetyScore() > 0) {
            filteredDistricts = targetDistricts.stream()
                    .filter(district -> {
                        double districtSafetyScore = getDistrictSafetyScoreFromRedis(district);
                        return districtSafetyScore >= request.getMinSafetyScore();
                    })
                    .collect(Collectors.toList());
        }

        Map<String, List<String>> result = new HashMap<>();
        int totalFound = 0;
        int successCount = 0;
        int emptyCount = 0;

        for (String district : filteredDistricts) {
            System.out.println("district=" + district);
            List<String> validProperties = findValidMonthlyPropertiesInDistrict(district, request);

            if (!validProperties.isEmpty()) {
                result.put(district, validProperties);
                totalFound += validProperties.size();
                successCount++;
            } else {
                emptyCount++;
            }
        }

        // 전체 순회 종료 시간
        long loopEndNano = System.nanoTime();
        double loopTimeMs = (loopEndNano - loopStartNano) / 1_000_000.0;
        double avgTimePerDistrict = filteredDistricts.size() > 0
                ? loopTimeMs / filteredDistricts.size() : 0;

        log.info("[Metrics-LoopSummary] mode=SEQUENTIAL, totalDistricts={}, " +
                        "successDistricts={}, emptyDistricts={}, totalProperties={}, " +
                        "loopTime={} ms, avgPerDistrict={} ms",
                filteredDistricts.size(),
                successCount,
                emptyCount,
                totalFound,
                String.format("%.4f", loopTimeMs),
                String.format("%.4f", avgTimePerDistrict));

        log.info("월세 매물 검색 완료: 총 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
        return result;
    }

    /**
     * S-02: 월세 매물 폴백 조건 판단 및 확장 검색 수행
     */
    private SearchResult checkAndPerformMonthlyFallback(Map<String, List<String>> districtProperties,
                                                        MonthlyRecommendationRequestDto request) {

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
     * S-03: 월세 매물 확장 검색
     */
    private SearchResult performMonthlyExpandedSearch(MonthlyRecommendationRequestDto request,
                                                      Map<String, List<String>> originalResult) {

        List<String> insufficientDistricts = originalResult.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 1단계: 3순위 조건 완화
        MonthlyRecommendationRequestDto expandedRequest = relaxMonthlyThirdPriority(request);
        String relaxedCondition = getMonthlyRelaxedConditionMessage(request.getPriority3(), request, expandedRequest);

        Map<String, List<String>> expandedResult = performMonthlyStrictSearch(expandedRequest, insufficientDistricts);

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

        Map<String, List<String>> doubleExpandedResult = performMonthlyStrictSearch(doubleExpandedRequest, insufficientDistricts);

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

    /**
     * 월세 매물 검색 - 3개 인덱스 교집합
     * [Before 측정 버전] 계획서 5.3.1절 기반
     *
     * 측정 항목:
     * - 각 Redis Command의 개별 Latency
     * - 총 Command Latency (3개 명령 합계)
     * - 메소드 총 실행 시간
     * - I/O 비율 (Command Latency / 메소드 시간)
     */
    private List<String> findValidMonthlyPropertiesInDistrict(String district, MonthlyRecommendationRequestDto request) {

        long methodStart = System.nanoTime();
        long totalCommandLatency = 0;
        long cmd1Latency = 0, cmd2Latency = 0, cmd3Latency = 0;
        int resultSize1 = 0, resultSize2 = 0, resultSize3 = 0;

        try {
            // ========================================
            // Command #1: 보증금 범위 검색
            // ========================================
            String depositIndexKey = "idx:deposit:" + district;

            long cmd1Start = System.nanoTime();
            Set<Object> depositValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(depositIndexKey, request.getBudgetMin(), request.getBudgetMax());
            long cmd1End = System.nanoTime();

            cmd1Latency = cmd1End - cmd1Start;
            totalCommandLatency += cmd1Latency;
            resultSize1 = (depositValidObjects != null) ? depositValidObjects.size() : 0;

            if (depositValidObjects == null || depositValidObjects.isEmpty()) {
                logSequentialMetrics(district, methodStart, totalCommandLatency,
                        cmd1Latency, 0, 0,
                        resultSize1, 0, 0,
                        1, "EARLY_RETURN_DEPOSIT_EMPTY");
                return Collections.emptyList();
            }
            Set<String> depositValidIds = depositValidObjects.stream()
                    .map(Object::toString).collect(Collectors.toSet());

            // ========================================
            // Command #2: 월세금 범위 검색
            // ========================================
            String monthlyRentIndexKey = "idx:monthlyRent:" + district + ":월세";

            long cmd2Start = System.nanoTime();
            Set<Object> monthlyRentValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(monthlyRentIndexKey, request.getMonthlyRentMin(), request.getMonthlyRentMax());
            long cmd2End = System.nanoTime();

            cmd2Latency = cmd2End - cmd2Start;
            totalCommandLatency += cmd2Latency;
            resultSize2 = (monthlyRentValidObjects != null) ? monthlyRentValidObjects.size() : 0;

            if (monthlyRentValidObjects == null || monthlyRentValidObjects.isEmpty()) {
                logSequentialMetrics(district, methodStart, totalCommandLatency,
                        cmd1Latency, cmd2Latency, 0,
                        resultSize1, resultSize2, 0,
                        2, "EARLY_RETURN_RENT_EMPTY");
                return Collections.emptyList();
            }
            Set<String> monthlyRentValidIds = monthlyRentValidObjects.stream()
                    .map(Object::toString).collect(Collectors.toSet());

            // ========================================
            // Command #3: 평수 범위 검색
            // ========================================
            String areaIndexKey = "idx:area:" + district + ":월세";

            long cmd3Start = System.nanoTime();
            Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());
            long cmd3End = System.nanoTime();

            cmd3Latency = cmd3End - cmd3Start;
            totalCommandLatency += cmd3Latency;
            resultSize3 = (areaValidObjects != null) ? areaValidObjects.size() : 0;

            if (areaValidObjects == null || areaValidObjects.isEmpty()) {
                logSequentialMetrics(district, methodStart, totalCommandLatency,
                        cmd1Latency, cmd2Latency, cmd3Latency,
                        resultSize1, resultSize2, resultSize3,
                        3, "EARLY_RETURN_AREA_EMPTY");
                return Collections.emptyList();
            }
            Set<String> areaValidIds = areaValidObjects.stream()
                    .map(Object::toString).collect(Collectors.toSet());

            // ========================================
            // 교집합 계산 (Java 측)
            // ========================================
            depositValidIds.retainAll(monthlyRentValidIds);
            depositValidIds.retainAll(areaValidIds);

            int intersectionSize = depositValidIds.size();

            logSequentialMetrics(district, methodStart, totalCommandLatency,
                    cmd1Latency, cmd2Latency, cmd3Latency,
                    resultSize1, resultSize2, resultSize3,
                    3, "SUCCESS|intersection=" + intersectionSize);

            return new ArrayList<>(depositValidIds);

        } catch (Exception e) {
            logSequentialMetrics(district, methodStart, totalCommandLatency,
                    cmd1Latency, cmd2Latency, cmd3Latency,
                    resultSize1, resultSize2, resultSize3,
                    -1, "ERROR|" + e.getClass().getSimpleName());
            log.debug("월세 매물 검색 중 오류 - 지역구: {}", district, e);
            return Collections.emptyList();
        }
    }

    /**
     * [Before 측정] 순차 호출 측정 결과 로깅
     * 계획서 5.3.3절 기반 - 확장 버전
     *
     * 로그 포맷:
     * [Metrics-Sequential] district=강남구, commands=3,
     *   methodTime=X.XXXXms, totalCmdLatency=X.XXXXms, ioRatio=XX.XX%,
     *   cmd1=X.XXXXms(N건), cmd2=X.XXXXms(N건), cmd3=X.XXXXms(N건),
     *   status=SUCCESS
     */
    private void logSequentialMetrics(String district, long methodStartNano, long totalCommandLatencyNano,
                                      long cmd1LatencyNano, long cmd2LatencyNano, long cmd3LatencyNano,
                                      int resultSize1, int resultSize2, int resultSize3,
                                      int executedCommands, String status) {

        long methodEndNano = System.nanoTime();
        long methodTimeNano = methodEndNano - methodStartNano;

        // 나노초 → 밀리초 변환
        double methodTimeMs = methodTimeNano / 1_000_000.0;
        double totalCmdLatencyMs = totalCommandLatencyNano / 1_000_000.0;
        double cmd1Ms = cmd1LatencyNano / 1_000_000.0;
        double cmd2Ms = cmd2LatencyNano / 1_000_000.0;
        double cmd3Ms = cmd3LatencyNano / 1_000_000.0;

        // I/O 비율 계산: (Command Latency / Method Time) × 100
        double ioRatio = (methodTimeMs > 0) ? (totalCmdLatencyMs / methodTimeMs) * 100 : 0;

        // 비-I/O 시간 (교집합 계산, 스트림 변환 등)
        double nonIoTimeMs = methodTimeMs - totalCmdLatencyMs;

        log.info("[Metrics-Sequential] district={}, commands={}, " +
                        "methodTime={} ms, totalCmdLatency={} ms, ioRatio={} %, nonIoTime={} ms, " +
                        "cmd1={} ms ({}건), cmd2={} ms ({}건), cmd3={} ms ({}건), status={}",
                district,
                executedCommands,
                String.format("%.4f", methodTimeMs),
                String.format("%.4f", totalCmdLatencyMs),
                String.format("%.2f", ioRatio),
                String.format("%.4f", nonIoTimeMs),
                String.format("%.4f", cmd1Ms), resultSize1,
                String.format("%.4f", cmd2Ms), resultSize2,
                String.format("%.4f", cmd3Ms), resultSize3,
                status);
    }

    // ========================================
    // 월세 점수 계산 관련 메소드들 (Phase 2 핵심 로직)
    // ========================================

    /**
     * S-04: 월세 매물 점수 계산 (하이브리드 알고리즘 적용)
     */
    private Map<String, List<PropertyWithScore>> calculateMonthlyPropertyScores(
            Map<String, List<String>> districtProperties, MonthlyRecommendationRequestDto request) {

        log.info("S-04: 월세 매물 점수 계산 시작 (Hybrid Logic)");
        Map<String, List<PropertyWithScore>> result = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {

            String districtName = entry.getKey();
            List<String> propertyIds = entry.getValue();

            if (propertyIds.isEmpty()) {
                result.put(districtName, Collections.emptyList());
                continue;
            }

            // 1. 매물 상세 정보 조회 (Redis)
            List<PropertyDetail> propertyDetails = getMultipleMonthlyPropertiesFromRedis(propertyIds);

            // 2. [Phase 2 신규] 리뷰 통계 정보 조회 (RDB 직접 조회 - 의도적 병목 지점)
            Map<String, ReviewStatistics> reviewStatsMap = getReviewStatisticsFromRDB(propertyIds);

            // 3. 기준 데이터 조회
            MonthlyScoreNormalizationBounds districtBounds = getMonthlyBoundsFromRedis(districtName);
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();

            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    // === 4-1. 정량적 점수(Legacy Score) 산출 ===
                    double depositScore = calculateDepositScore(propertyDetail.getDeposit(), districtBounds);
                    double monthlyRentScore = calculateMonthlyRentScore(propertyDetail.getMonthlyRent(), districtBounds);
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
                    double safetyScore = propertyDetail.getSafetyScore() != null ?
                            propertyDetail.getSafetyScore() : districtSafetyScore;

                    // 월세는 보증금점수와 월세금점수의 평균을 가격 점수로 사용 (표시용)
                    double displayPriceScore = (depositScore + monthlyRentScore) / 2.0;

                    double legacyScore = calculateMonthlyWeightedFinalScore(
                            depositScore, monthlyRentScore, spaceScore, safetyScore,
                            request.getPriority1(), request.getPriority2(), request.getPriority3());

                    // === 4-2. 정성적 점수(Review Score) 산출 및 통합 ===
                    String propertyId = propertyDetail.getPropertyId();
                    ReviewStatistics stats = reviewStatsMap.getOrDefault(propertyId,
                            ReviewStatistics.builder().propertyId(propertyId).build());

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

            // 점수 내림차순 정렬
            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));
            result.put(districtName, propertiesWithScores);
        }

        return result;
    }

    /**
     * [Phase 2] 하이브리드 점수 산출 로직
     * FinalScore = (LegacyScore * 0.5) + (ReviewScore * 0.5)
     */
    private double calculateHybridScore(double legacyScore, ReviewStatistics stats) {
        // Cold Start 방어 로직 (리뷰 5개 미만)
        if (stats.getReviewCount() < COLD_START_REVIEW_COUNT) {
            return legacyScore;
        }

        double reviewScore = calculateReviewScoreOnly(stats);
        return (legacyScore * 0.5) + (reviewScore * 0.5);
    }

    /**
     * 리뷰 점수 자체 계산 로직
     */
    private double calculateReviewScoreOnly(ReviewStatistics stats) {
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
     * [Phase 2] RDB에서 리뷰 통계 조회
     */
    private Map<String, ReviewStatistics> getReviewStatisticsFromRDB(List<String> propertyIds) {
        if (propertyIds.isEmpty()) return Collections.emptyMap();
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

            DistrictWithScore districtWithScore = DistrictWithScore.builder()
                    .districtName(districtName)
                    .propertiesWithScores(propertiesWithScores)
                    .averageFinalScore(averageFinalScore)
                    .propertyCount(propertyCount)
                    .representativeScore(representativeScore)
                    .build();

            districtScores.add(districtWithScore);
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
                                                                          MonthlyRecommendationRequestDto request) {
        log.info("S-06: 월세 최종 응답 생성 시작");

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

            List<TopMonthlyPropertyDto> topProperties = selectTopMonthlyProperties(district.getPropertiesWithScores(), 3);
            String summary = generateDistrictSummary(district, rank, request.getPriority1());

            Double averagePriceScore = calculateMonthlyAverageScore(district.getPropertiesWithScores(), "price");
            Double averageSpaceScore = calculateMonthlyAverageScore(district.getPropertiesWithScores(), "space");
            Double districtSafetyScore = calculateAverageFinalScore(district.getPropertiesWithScores());

            RecommendedMonthlyDistrictDto districtDto = RecommendedMonthlyDistrictDto.builder()
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

    private List<TopMonthlyPropertyDto> selectTopMonthlyProperties(List<PropertyWithScore> propertiesWithScores, int maxCount) {
        return propertiesWithScores.stream()
                .limit(maxCount)
                .map(this::convertToTopMonthlyPropertyDto)
                .collect(Collectors.toList());
    }

    private TopMonthlyPropertyDto convertToTopMonthlyPropertyDto(PropertyWithScore propertyWithScore) {
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
                // [Phase 2] 리뷰 정보 매핑
                .reviewCount(propertyWithScore.getReviewCount())
                .avgRating(propertyWithScore.getAvgRating())
                .build();
    }

    // ========================================
    // 월세 Redis 조회 메소드들 (기존 로직 유지)
    // ========================================

    private List<PropertyDetail> getMultipleMonthlyPropertiesFromRedis(List<String> propertyIds) {
        List<PropertyDetail> propertyDetails = new ArrayList<>();
        try {
            List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String propertyId : propertyIds) {
                            String propertyKey = "property:monthly:" + propertyId;
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
                        if (detail != null) propertyDetails.add(detail);
                    }
                }
            }
        } catch (Exception e) {
            log.error("월세 매물 Pipeline 조회 실패", e);
        }
        return propertyDetails;
    }

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
    // 월세 점수 계산 헬퍼 메소드들
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
    // 월세 폴백 조건 완화 메소드들
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
                    .safetyScore(null).build();
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
        private double legacyScore; // [Phase 2] 디버깅용
        private double reviewScore; // [Phase 2] 디버깅용
        private double finalScore;
        private int reviewCount;    // [Phase 2]
        private double avgRating;   // [Phase 2]
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