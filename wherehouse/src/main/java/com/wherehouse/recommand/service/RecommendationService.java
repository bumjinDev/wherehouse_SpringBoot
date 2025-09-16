package com.wherehouse.recommand.service;

import com.wherehouse.recommand.model.*;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 부동산 추천 서비스 - 설계서 기반 구현
 * S-01 ~ S-06 단계를 순차적으로 수행하여 지역구 추천 결과 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final RedisHandler redisHandler;

    // 서울시 25개 자치구 목록
    private static final List<String> SEOUL_DISTRICTS = Arrays.asList(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구",
            "강북구", "도봉구", "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구",
            "구로구", "금천구", "영등포구", "동작구", "관악구", "서초구", "강남구", "송파구", "강동구"
    );

    private static final int MIN_PROPERTIES_THRESHOLD = 3; // 폴백 기준 매물 수

    /**
     * 메인 추천 로직 - 설계서 S-01 ~ S-06 단계 실행
     *
     * 목적: 사용자 조건에 맞는 최적의 지역구를 추천
     * 과정: 1차 검색 → 폴백 판단 → 점수 계산 → 순위 결정 → 응답 생성
     * 반환: 추천 지역구 목록이 포함된 응답 DTO
     */
    public RecommendationResponseDto getDistrictRecommendations(RecommendationRequestDto request) {
        log.info("=== 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 임대유형={}, 예산={}-{}, 평수={}-{}, 우선순위={},{},{}",
                request.getLeaseType(), request.getBudgetMin(), request.getBudgetMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3());

        try {
            // S-01: 전 지역구 1차 검색 (Strict Search)
            Map<String, List<String>> districtProperties = performStrictSearch(request, SEOUL_DISTRICTS);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformFallback(districtProperties, request);

            // S-04: 매물 단위 점수 계산 (지역구별 정규화 적용)
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores =
                    calculatePropertyScores(searchResult.getDistrictProperties(), request);

            // S-05: 지역구 단위 점수 계산 및 정렬
            List<DistrictWithScore> sortedDistricts = calculateDistrictScoresAndSort(districtPropertiesWithScores);

            // S-06: 최종 응답 생성
            RecommendationResponseDto finalResponse = generateFinalResponse(sortedDistricts, searchResult, request);

            log.info("=== 지역구 추천 서비스 완료 ===");
            return finalResponse;

        } catch (Exception e) {
            log.error("추천 서비스 처리 중 오류 발생", e);
            throw new RuntimeException("추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * S-01: 지정된 지역구들에서 조건 검색 수행
     *
     * 목적: 전체 또는 특정 지역구 범위에서 사용자 조건에 맞는 매물 ID 검색
     * 과정: targetDistricts 리스트의 지역구들만 순회하여 Redis 인덱스에서 조건별 검색
     * 반환: 지정된 지역구별 유효 매물 ID 목록 (Map<지역구명, 매물ID리스트>)
     *
     * 필터링 정책: 매물이 0개인 지역구는 추천 대상에서 완전 제외
     * - 이후 모든 단계(점수 계산, 순위 결정)에서 해당 지역구는 고려되지 않음
     * - 근거: 매물이 없는 지역구를 추천할 실용적 가치가 없음
     */
    private Map<String, List<String>> performStrictSearch(RecommendationRequestDto request,
                                                          List<String> targetDistricts) {
        log.info("S-01: 지정 지역구 검색 시작 - 대상: {}", targetDistricts);

        Map<String, List<String>> result = new HashMap<>();
        // 영문 임대유형을 한글로 변환 (Redis 인덱스 키가 한글로 저장되어 있음)
        String leaseTypeKorean = "CHARTER".equals(request.getLeaseType()) ? "전세" : "월세";
        int totalFound = 0;

        // 지정된 지역구들만 순회하며 조건에 맞는 매물 검색
        for (String district : targetDistricts) {
            List<String> validProperties = findValidPropertiesInDistrict(
                    district, leaseTypeKorean, request);

            // 매물이 있든 없든 모든 지역구를 결과에 포함 (폴백 검색 기회 제공)
            if (!validProperties.isEmpty()) {
                result.put(district, validProperties);
                totalFound += validProperties.size();
                log.debug("지역구 [{}]: {}개 매물 ID 발견", district, validProperties.size());
            } else {
                result.put(district, Collections.emptyList()); // 빈 리스트 추가
                log.debug("지역구 [{}]: 매물 ID 없음 (폴백 검색 대상)", district);
            }
        }

        log.info("지정 지역구 검색 완료: 이 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
        return result;
    }

    /**
     * S-02: 폴백 조건 판단 및 확장 검색 수행
     *
     * 목적: 1차 검색 결과가 부족할 때 확장 검색 필요성 판단 및 실행
     * 과정: 1) 매물 부족 지역구 확인 → 2) 폴백 필요시 확장 검색 실행
     * 반환: 검색 상태와 최종 지역구별 매물 목록이 포함된 결과 객체
     */
    private SearchResult checkAndPerformFallback(Map<String, List<String>> districtProperties,
                                                 RecommendationRequestDto request) {

        // === 1단계: 매물 부족 지역구 존재 여부 확인 ===
        // 각 지역구의 매물 ID 개수를 확인하여 3개 미만인 지역구가 있는지 판단
        // 예시: 강남구 매물ID 2개, 서초구 매물ID 4개 → 강남구가 부족하므로 true
        boolean hasInsufficientDistricts = districtProperties.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        // === 2단계: 전체 통계 계산 ===
        // 모든 지역구의 매물 ID 개수를 합산하여 전체 유효 매물 ID 총 개수 계산
        int totalPropertiesFound = districtProperties.values().stream()
                .mapToInt(List::size).sum();

        // 실제로 매물 ID가 1개 이상 발견된 지역구의 개수 계산
        int districtsWithProperties = (int) districtProperties.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .count();

        log.info("S-02: 폴백 조건 판단 - 전체 매물 ID: {}개, 매물 보유 지역구: {}개 / 전체 {}개",
                totalPropertiesFound, districtsWithProperties, districtProperties.size());

        // === 3단계: 폴백 필요성 판단 ===
        // 모든 지역구가 3개 이상의 매물을 보유하고 있다면 정상 처리
        if (!hasInsufficientDistricts) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_NORMAL")
                    .message("조건에 맞는 매물을 성공적으로 찾았습니다.")
                    .districtProperties(districtProperties)
                    .build();
        }

        // === 4단계: 매물 부족 지역구 목록 추출 ===
        // 매물 ID 개수가 3개 미만인 지역구들의 이름만 추출하여 리스트로 생성
        List<String> insufficientDistricts = districtProperties.entrySet().stream()
                .filter(entry -> entry.getValue().size() < MIN_PROPERTIES_THRESHOLD)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("매물 부족 지역구: {} - S-03 확장 검색 수행", insufficientDistricts);

        // 폴백 수행 후 기존 정상 지역구 결과와 병합
        SearchResult expandedResult = performExpandedSearch(request, insufficientDistricts);

        // 기존 정상 지역구 결과 보존 + 폴백 결과 병합
        Map<String, List<String>> finalResult = new HashMap<>(districtProperties);
        insufficientDistricts.forEach(finalResult::remove); // 부족한 지역구 제거
        finalResult.putAll(expandedResult.getDistrictProperties()); // 폴백 결과 추가

        return SearchResult.builder()
                .searchStatus(expandedResult.getSearchStatus())
                .message(expandedResult.getMessage())
                .districtProperties(finalResult) // 병합된 결과
                .build();
    }

    /**
     * S-03: 2차 확장 검색 (Expanded Search)
     *
     * 목적: 매물 부족 시 사용자 우선순위에 따라 조건을 단계적으로 완화하여 재검색
     * 과정: 1) 3순위 조건 완화 → 재검색 → 2) 부족시 2순위 조건 추가 완화 → 재검색
     * 반환: 확장 검색 결과와 완화된 조건에 대한 안내 메시지
     */
    private SearchResult performExpandedSearch(RecommendationRequestDto originalRequest,
                                               List<String> insufficientDistricts) {
        log.info("S-03: 2차 확장 검색 시작 - 대상 지역구: {}", insufficientDistricts);

        // === 1단계: 3순위 조건 완화 ===
        // 사용자의 3순위 항목에 해당하는 조건을 완화 임계값에 따라 조정
        RecommendationRequestDto expandedRequest = relaxThirdPriority(originalRequest);
        String relaxedCondition = getRelaxedConditionMessage(originalRequest.getPriority3(), originalRequest, expandedRequest);

        // 완화된 조건으로 부족한 지역구들만 재검색
        Map<String, List<String>> expandedResult = performStrictSearch(expandedRequest, insufficientDistricts);

        // 3순위 완화만으로 충분한지 검사 (모든 지역구가 3개 이상 매물 보유)
        boolean stillInsufficient = expandedResult.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        if (!stillInsufficient && !expandedResult.isEmpty()) {
            log.info("3순위 조건 완화로 충분한 매물 확보: {} 지역구", expandedResult.size());
            return SearchResult.builder()
                    .searchStatus("SUCCESS_EXPANDED")
                    .message("원하시는 조건의 매물이 부족하여, " + relaxedCondition + " 완화하여 찾았어요.")
                    .districtProperties(expandedResult)
                    .build();
        }

        // === 2단계: 2순위 조건 추가 완화 ===
        log.info("3순위 완화 불충분 - 2순위 조건 추가 완화 시도");
        // 이미 3순위가 완화된 요청에 2순위 조건도 완화 적용
        RecommendationRequestDto doubleExpandedRequest = relaxSecondPriority(expandedRequest, originalRequest);
        String doubleRelaxedCondition = relaxedCondition + ", " + getRelaxedConditionMessage(originalRequest.getPriority2(), originalRequest, doubleExpandedRequest);

        // 2순위까지 완화된 조건으로 부족한 지역구들만 최종 검색
        Map<String, List<String>> doubleExpandedResult = performStrictSearch(doubleExpandedRequest, insufficientDistricts);

        // 최종 결과 확인 - 매물이 아예 없으면 검색 실패
        if (doubleExpandedResult.isEmpty() ||
                doubleExpandedResult.values().stream().mapToInt(List::size).sum() == 0) {
            log.warn("2순위까지 완화했으나 매물 없음");
            return SearchResult.builder()
                    .searchStatus("NO_RESULTS")
                    .message("아쉽지만 조건에 맞는 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .districtProperties(Collections.emptyMap())
                    .build();
        }

        log.info("2순위까지 완화하여 매물 확보: {} 지역구", doubleExpandedResult.size());
        return SearchResult.builder()
                .searchStatus("SUCCESS_EXPANDED")
                .message("원하시는 조건의 매물이 부족하여, " + doubleRelaxedCondition + " 완화하여 찾았어요.")
                .districtProperties(doubleExpandedResult)
                .build();
    }

    /**
     * S-04: 매물 단위 점수 계산 (지역구별 정규화 적용)
     *
     * 목적: 각 지역구의 유효 매물에 대해 사용자 우선순위에 따른 가중치를 적용하여 최종 점수 계산
     * 과정: 1) 매물 상세 정보 조회 → 2) 지역구별 개별 항목 점수화 → 3) 가중치 적용하여 최종 점수 산출 → 4) 매물 정렬
     * 반환: 지역구별 점수가 계산되고 정렬된 매물 목록 (Map<지역구명, PropertyWithScore리스트>)
     *
     * 핵심 특징: 각 지역구 내에서만의 상대적 점수 계산 (지역구별 가격/평수 범위 기준)
     */
    private Map<String, List<PropertyWithScore>> calculatePropertyScores(
            Map<String, List<String>> districtProperties, RecommendationRequestDto request) {

        log.info("S-04: 매물 단위 점수 계산 시작 (지역구별 정규화 적용)");

        // 결과 저장용 Map 초기화: 지역구별 점수 계산된 매물 목록을 담을 컨테이너
        // Key: 지역구명(String), Value: 해당 지역구의 점수 계산된 매물 리스트
        Map<String, List<PropertyWithScore>> result = new HashMap<>();
        String leaseTypeKorean = "CHARTER".equals(request.getLeaseType()) ? "전세" : "월세";

        // 새로운 Map 생성: 입력 데이터 타입 변환 및 원본 보호
        // 입력: Map<String, List<String>> → 출력: Map<String, List<PropertyWithScore>>
        for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {
            String districtName = entry.getKey();
            List<String> propertyIds = entry.getValue();

            if (propertyIds.isEmpty()) {
                // === 매물 없는 지역구 처리 로직 ===
                // 이전 S-01/S-02 단계에서 검색 결과가 없는 지역구는 빈 리스트(Collections.emptyList())로 통일하여 저장함
                // 따라서 여기서는 null 체크가 아닌 isEmpty() 체크만 수행하면 됨
                //
                // 처리 방식:
                // 1) 점수 계산을 시도하지 않음 (0개 매물에 대한 불필요한 연산 방지)
                // 2) 해당 지역구를 최종 result Map에서 빈 리스트로 등록
                // 3) continue로 다음 지역구로 즉시 이동하여 효율성 확보
                //
                // 비즈니스 의미: 매물이 없는 지역구는 사용자에게 추천할 가치가 없으므로
                // 최종 응답에서도 제외되거나 낮은 우선순위로 처리됨
                result.put(districtName, Collections.emptyList());
                continue;
            }

            log.debug("지역구 [{}]: {}개 매물 점수 계산", districtName, propertyIds.size());

            // 1. 해당 지역구의 모든 매물 상세 정보 일괄 조회 (성능 최적화)
            // 기존: 매물마다 개별 Redis 호출 → 개선: 한 번에 모든 매물 조회
            List<PropertyDetail> propertyDetails = getMultiplePropertiesFromRedis(propertyIds);

            // 2. 해당 지역구의 사전 계산된 정규화 범위 조회 (배치 B-04에서 저장된 데이터)
            ScoreNormalizationBounds districtBounds = getBoundsFromRedis(districtName, leaseTypeKorean);

            // 3. 해당 지역구의 사전 계산된 안전성 점수 조회 (배치 B-05에서 저장된 데이터)
            double districtSafetyScore = getDistrictSafetyScoreFromRedis(districtName);

            List<PropertyWithScore> propertiesWithScores = new ArrayList<>();

            for (PropertyDetail propertyDetail : propertyDetails) {
                try {
                    // 3. 개별 항목 점수화 (해당 지역구 기준 0~100점 척도)
                    double priceScore = calculatePriceScore(calculateTotalPrice(propertyDetail), districtBounds);
                    double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
                    double safetyScore = propertyDetail.getSafetyScore() != null ?
                            propertyDetail.getSafetyScore() : districtSafetyScore; // 지역구 안전성 점수 사용

                    // 4. 사용자 우선순위에 따른 가중치 적용 (60%, 30%, 10%)
                    double finalScore = calculateWeightedFinalScore(
                            priceScore, spaceScore, safetyScore, request);

                    // 5. PropertyWithScore 객체 생성
                    PropertyWithScore propertyWithScore = PropertyWithScore.builder()
                            .propertyDetail(propertyDetail)
                            .priceScore(priceScore)
                            .spaceScore(spaceScore)
                            .safetyScore(safetyScore)
                            .finalScore(finalScore)
                            .build();

                    propertiesWithScores.add(propertyWithScore);

                } catch (Exception e) {
                    log.debug("매물 [{}] 점수 계산 중 오류", propertyDetail.getPropertyId(), e);
                }
            }

            // 6. 매물을 최종 점수 기준으로 내림차순 정렬
            propertiesWithScores.sort((p1, p2) -> Double.compare(p2.getFinalScore(), p1.getFinalScore()));

            result.put(districtName, propertiesWithScores);
            log.debug("지역구 [{}]: 점수 계산 완료 - {}개 매물",
                    districtName, propertiesWithScores.size());
        }

        log.info("S-04: 매물 단위 점수 계산 완료 - {}개 지역구 처리", result.size());
        return result;
    }

    /**
     * S-05: 지역구 단위 점수 계산 및 정렬
     *
     * 목적: 각 지역구의 매물들을 종합하여 지역구별 대표 점수를 계산하고 순위 결정
     * 과정: 1) 지역구별 대표 점수 계산 → 2) 대표 점수 기준 내림차순 정렬 → 3) 동점시 매물 개수 → 지역구명 순
     * 반환: 순위가 결정된 지역구 목록 (List<DistrictWithScore>)
     *
     * 핵심 공식: 지역구 대표 점수 = (해당 지역구의 유효 매물 전체의 평균 finalScore) × log(매물 개수 + 1)
     */
    private List<DistrictWithScore> calculateDistrictScoresAndSort(
            Map<String, List<PropertyWithScore>> districtPropertiesWithScores) {

        log.info("S-05: 지역구 단위 점수 계산 및 정렬 시작");

        List<DistrictWithScore> districtScores = new ArrayList<>();

        // === 1단계: 각 지역구별 대표 점수 계산 ===
        for (Map.Entry<String, List<PropertyWithScore>> entry : districtPropertiesWithScores.entrySet()) {
            String districtName = entry.getKey();
            List<PropertyWithScore> propertiesWithScores = entry.getValue();

            if (propertiesWithScores.isEmpty()) {
                // 매물이 없는 지역구는 점수 0으로 처리
                DistrictWithScore districtWithScore = DistrictWithScore.builder()
                        .districtName(districtName)
                        .propertiesWithScores(propertiesWithScores)
                        .averageFinalScore(0.0)
                        .propertyCount(0)
                        .representativeScore(0.0)
                        .build();

                districtScores.add(districtWithScore);
                log.debug("지역구 [{}]: 매물 없음 - 대표 점수: 0.0", districtName);
                continue;
            }

            // 해당 지역구의 유효 매물 전체의 평균 finalScore 계산
            double totalFinalScore = propertiesWithScores.stream()
                    .mapToDouble(PropertyWithScore::getFinalScore)
                    .sum();
            double averageFinalScore = totalFinalScore / propertiesWithScores.size();

            // 매물 개수
            int propertyCount = propertiesWithScores.size();

            // 지역구 대표 점수 계산: (평균 finalScore) × log(매물 개수 + 1)
            double representativeScore = averageFinalScore * Math.log(propertyCount + 1);

            DistrictWithScore districtWithScore = DistrictWithScore.builder()
                    .districtName(districtName)
                    .propertiesWithScores(propertiesWithScores)
                    .averageFinalScore(averageFinalScore)
                    .propertyCount(propertyCount)
                    .representativeScore(representativeScore)
                    .build();

            districtScores.add(districtWithScore);

            log.debug("지역구 [{}]: 평균점수={:.2f}, 매물수={}, 대표점수={:.2f}",
                    districtName, averageFinalScore, propertyCount, representativeScore);
        }

        // === 2단계: 대표 점수 기준으로 내림차순 정렬 ===
        districtScores.sort((d1, d2) -> {
            // 1차 정렬: 대표 점수 내림차순
            int scoreComparison = Double.compare(d2.getRepresentativeScore(), d1.getRepresentativeScore());
            if (scoreComparison != 0) {
                return scoreComparison;
            }

            // 2차 정렬: 매물 개수 내림차순 (동점 처리)
            int countComparison = Integer.compare(d2.getPropertyCount(), d1.getPropertyCount());
            if (countComparison != 0) {
                return countComparison;
            }

            // 3차 정렬: 지역구명 알파벳 순서 (오름차순)
            return d1.getDistrictName().compareTo(d2.getDistrictName());
        });

        log.info("S-05: 지역구 단위 점수 계산 및 정렬 완료 - 이 {}개 지역구", districtScores.size());

        // 상위 지역구 로그 출력 (최대 5개)
        log.info("=== 상위 지역구 순위 ===");
        for (int i = 0; i < Math.min(5, districtScores.size()); i++) {
            DistrictWithScore district = districtScores.get(i);
            log.info("{}위: {} (대표점수: {:.2f}, 평균점수: {:.2f}, 매물수: {}개)",
                    i + 1, district.getDistrictName(), district.getRepresentativeScore(),
                    district.getAverageFinalScore(), district.getPropertyCount());
        }

        return districtScores;
    }

    /**
     * S-06: 최종 응답 생성
     *
     * 목적: S-05에서 정렬된 지역구 목록을 기반으로 API 명세에 맞는 최종 응답 생성
     * 과정: 1) 상위 3개 지역구 선택 → 2) 각 지역구별 대표 매물 추출 → 3) 요약 메시지 생성 → 4) DTO 변환
     * 반환: API 명세서에 정의된 형식의 추천 응답 DTO
     */
    private RecommendationResponseDto generateFinalResponse(List<DistrictWithScore> sortedDistricts,
                                                            SearchResult searchResult,
                                                            RecommendationRequestDto request) {
        log.info("S-06: 최종 응답 생성 시작");

        // NO_RESULTS 케이스 처리
        if ("NO_RESULTS".equals(searchResult.getSearchStatus()) || sortedDistricts.isEmpty()) {
            log.info("추천할 지역구 없음 - NO_RESULTS 응답 생성");
            return RecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message(searchResult.getMessage())
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        // 상위 3개 지역구 선택 (매물이 있는 지역구만)
        List<DistrictWithScore> validDistricts = sortedDistricts.stream()
                .filter(district -> district.getPropertyCount() > 0)
                .limit(3)
                .collect(Collectors.toList());

        if (validDistricts.isEmpty()) {
            log.warn("유효한 지역구 없음 - NO_RESULTS로 처리");
            return RecommendationResponseDto.builder()
                    .searchStatus("NO_RESULTS")
                    .message("조건에 맞는 매물을 찾을 수 없었어요. 조건을 변경하여 다시 시도해 보세요.")
                    .recommendedDistricts(Collections.emptyList())
                    .build();
        }

        // 지역구별 추천 DTO 생성
        List<RecommendedDistrictDto> recommendedDistricts = new ArrayList<>();

        for (int i = 0; i < validDistricts.size(); i++) {
            DistrictWithScore district = validDistricts.get(i);
            int rank = i + 1;

            // 해당 지역구의 대표 매물 선택 (최대 3개)
            List<TopPropertyDto> topProperties = selectTopProperties(district.getPropertiesWithScores(), 3);

            // 지역구 요약 메시지 생성
            String summary = generateDistrictSummary(district, rank, request.getPriority1());

            RecommendedDistrictDto districtDto = RecommendedDistrictDto.builder()
                    .rank(rank)
                    .districtName(district.getDistrictName())
                    .summary(summary)
                    .topProperties(topProperties)
                    .build();

            recommendedDistricts.add(districtDto);

            log.debug("{}위 지역구 [{}]: 대표매물 {}개, 요약: {}",
                    rank, district.getDistrictName(), topProperties.size(), summary);
        }

        log.info("S-06: 최종 응답 생성 완료 - {}개 지역구 추천", recommendedDistricts.size());

        return RecommendationResponseDto.builder()
                .searchStatus(searchResult.getSearchStatus())
                .message(searchResult.getMessage())
                .recommendedDistricts(recommendedDistricts)
                .build();
    }

    /**
     * 지역구의 대표 매물 선택
     *
     * 목적: 점수가 높은 상위 N개 매물을 선택하여 API 응답용 DTO로 변환
     * 과정: 1) 이미 정렬된 매물 리스트에서 상위 N개 선택 → 2) DTO 변환
     * 반환: API 응답용 대표 매물 DTO 리스트
     */
    private List<TopPropertyDto> selectTopProperties(List<PropertyWithScore> propertiesWithScores, int maxCount) {
        return propertiesWithScores.stream()
                .limit(maxCount)
                .map(this::convertToTopPropertyDto)
                .collect(Collectors.toList());
    }

    /**
     * 내부 매물 객체를 API 응답용 DTO로 변환
     *
     * 목적: PropertyWithScore 내부 객체를 API 명세에 맞는 TopPropertyDto로 변환
     * 과정: 필드 매핑 및 데이터 형식 변환 (특히 가격 표시 형식)
     * 반환: API 응답용 매물 DTO
     */
    private TopPropertyDto convertToTopPropertyDto(PropertyWithScore propertyWithScore) {
        PropertyDetail detail = propertyWithScore.getPropertyDetail();

        // 가격 표시 로직: 전세는 보증금만, 월세는 "보증금/월세" 형태
        Integer displayPrice;
        String leaseTypeDisplay;

        if ("전세".equals(detail.getLeaseType())) {
            displayPrice = detail.getDeposit();
            leaseTypeDisplay = "전세";
        } else if ("월세".equals(detail.getLeaseType())) {
            displayPrice = detail.getDeposit(); // API에서는 보증금만 표시
            leaseTypeDisplay = "월세";
        } else {
            displayPrice = detail.getDeposit();
            leaseTypeDisplay = detail.getLeaseType();
        }

        return TopPropertyDto.builder()
                .propertyId(detail.getPropertyId()) // UUID 문자열을 그대로 사용
                .propertyName(detail.getAptNm())
                .address(detail.getAddress())
                .price(displayPrice)
                .leaseType(leaseTypeDisplay)
                .area(detail.getAreaInPyeong())
                .floor(detail.getFloor())
                .buildYear(detail.getBuildYear())
                .finalScore(propertyWithScore.getFinalScore())
                .build();
    }

    /**
     * 지역구 추천 요약 메시지 생성
     *
     * 목적: 해당 지역구를 추천하는 핵심 근거를 사용자 우선순위 기반으로 설명
     * 과정: 1순위 우선순위와 매물 개수를 활용한 개인화된 메시지 생성
     * 반환: 지역구 추천 근거 설명 문구
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

    /**
     * Redis에서 사전 계산된 지역구별 정규화 범위 조회 (배치 B-04 결과 활용)
     *
     * 목적: 배치 시스템에서 미리 계산하여 저장한 지역구별 정규화 범위를 빠르게 조회
     * 과정: Redis에서 bounds:{지역구명}:{임대유형} 키로 저장된 min/max 값들을 조회
     * 반환: 해당 지역구+임대유형의 정규화 범위 정보
     *
     * 성능 개선: 실시간 계산 대신 사전 계산된 값 사용으로 응답 속도 대폭 향상
     */
    private ScoreNormalizationBounds getBoundsFromRedis(String districtName, String leaseType) {
        try {
            String boundsKey = "bounds:" + districtName + ":" + leaseType;
            Map<Object, Object> boundsHash = redisHandler.redisTemplate.opsForHash().entries(boundsKey);

            if (boundsHash.isEmpty()) {
                log.warn("정규화 범위 데이터 없음 - Key: {} (기본값 사용)", boundsKey);
                // 데이터가 없는 경우 기본 범위 반환 (정규화 오류 방지)
                return ScoreNormalizationBounds.builder()
                        .minPrice(0.0)
                        .maxPrice(100000.0) // 10억원 기본값
                        .minArea(10.0)      // 10평 기본값
                        .maxArea(100.0)     // 100평 기본값
                        .build();
            }

            // Redis에서 조회한 문자열 값들을 Double로 변환
            double minPrice = Double.parseDouble(boundsHash.get("minPrice").toString());
            double maxPrice = Double.parseDouble(boundsHash.get("maxPrice").toString());
            double minArea = Double.parseDouble(boundsHash.get("minArea").toString());
            double maxArea = Double.parseDouble(boundsHash.get("maxArea").toString());

            // 배치 시스템에서 저장한 메타데이터 추출 (디버깅 및 모니터링 목적)
            // - propertyCount: 정규화 범위 계산에 사용된 매물 개수
            // - lastUpdated: 배치에서 해당 데이터를 마지막 업데이트한 시점
            String propertyCount = boundsHash.get("propertyCount") != null ?
                    boundsHash.get("propertyCount").toString() : "N/A";
            String lastUpdated = boundsHash.get("lastUpdated") != null ?
                    boundsHash.get("lastUpdated").toString() : "N/A";

            log.debug("정규화 범위 로드 성공 - Key: {}, 매물수: {}, 업데이트: {}",
                    boundsKey, propertyCount, lastUpdated);
            log.debug("범위값 - 가격: {}-{}, 평수: {}-{}",
                    String.format("%.0f", minPrice), String.format("%.0f", maxPrice),
                    String.format("%.1f", minArea), String.format("%.1f", maxArea));

            return ScoreNormalizationBounds.builder()
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .minArea(minArea)
                    .maxArea(maxArea)
                    .build();

        } catch (Exception e) {
            log.error("정규화 범위 조회 실패 - 지역구: {}, 임대유형: {}", districtName, leaseType, e);
            // 조회 실패시 기본값으로 폴백하여 서비스 중단 방지
            return ScoreNormalizationBounds.builder()
                    .minPrice(0.0)
                    .maxPrice(100000.0)
                    .minArea(10.0)
                    .maxArea(100.0)
                    .build();
        }
    }

    /**
     * Redis에서 사전 계산된 지역구별 안전성 점수 조회 (배치 B-05 결과 활용)
     *
     * 목적: 배치 시스템에서 미리 계산하여 저장한 지역구별 안전성 점수를 빠르게 조회
     * 과정: Redis에서 safety:{지역구명} 키로 저장된 안전성 점수를 조회
     * 반환: 해당 지역구의 안전성 점수 (0~100점)
     *
     * 성능 개선: 실시간 계산 대신 사전 계산된 값 사용으로 응답 속도 향상
     */
    private double getDistrictSafetyScoreFromRedis(String districtName) {
        try {
            String safetyKey = "safety:" + districtName;
            Map<Object, Object> safetyHash = redisHandler.redisTemplate.opsForHash().entries(safetyKey);

            if (safetyHash.isEmpty()) {
                log.warn("안전성 점수 데이터 없음 - Key: {} (기본값 50.0 사용)", safetyKey);
                return 50.0; // 기본값
            }

            // Redis에서 조회한 안전성 점수를 Double로 변환
            double safetyScore = Double.parseDouble(safetyHash.get("safetyScore").toString());

            log.debug("안전성 점수 로드 성공 - Key: {}, Score: {}", safetyKey, safetyScore);
            return safetyScore;

        } catch (Exception e) {
            log.error("안전성 점수 조회 실패 - 지역구: {}", districtName, e);
            return 50.0; // 조회 실패시 기본값으로 폴백
        }
    }

    /**
     * Redis에서 여러 매물의 상세 정보를 일괄 조회 (성능 최적화)
     *
     * 목적: 개별 호출 대신 Redis Pipeline을 사용하여 네트워크 비용 최소화
     * 과정: 1) 매물 ID 리스트를 받아서 → 2) Pipeline으로 일괄 조회 → 3) PropertyDetail 리스트 반환
     * 반환: 조회 성공한 매물들의 상세 정보 리스트
     *
     * 성능 개선: 100개 매물 시 100번 네트워크 호출 → 1번 네트워크 호출
     */
    private List<PropertyDetail> getMultiplePropertiesFromRedis(List<String> propertyIds) {
        List<PropertyDetail> propertyDetails = new ArrayList<>();

        try {
            // Pipeline 결과를 Object 리스트로 받기
            List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String propertyId : propertyIds) {
                            String propertyKey = "property:" + propertyId;
                            // RedisTemplate 메소드 대신 connection을 직접 사용
                            connection.hGetAll(propertyKey.getBytes());
                        }
                        return null; // Pipeline에서는 null 반환
                    });

            // Object를 Map으로 변환
            List<Map<Object, Object>> allPropertyHashes = new ArrayList<>();
            for (Object result : pipelineResults) {
                if (result instanceof Map) {
                    allPropertyHashes.add((Map<Object, Object>) result);
                }
            }

            // Pipeline 결과를 PropertyDetail 객체로 변환
            for (int i = 0; i < propertyIds.size(); i++) {
                Map<Object, Object> propertyHash = allPropertyHashes.get(i);

                if (propertyHash != null && !propertyHash.isEmpty()) {
                    PropertyDetail detail = PropertyDetail.builder()
                            .propertyId(propertyIds.get(i))
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
                            .safetyScore(null) // 현재 안전 점수는 저장되지 않음
                            .build();

                    propertyDetails.add(detail);
                } else {
                    log.debug("매물 [{}] 데이터 없음 - Pipeline 조회 결과", propertyIds.get(i));
                }
            }

            log.debug("Pipeline 일괄 조회 완료: {}개 요청 → {}개 성공", propertyIds.size(), propertyDetails.size());

        } catch (Exception e) {
            log.error("Pipeline 일괄 조회 실패 - 개별 조회로 폴백", e);
            // Pipeline 실패 시 기존 방식으로 폴백
            for (String propertyId : propertyIds) {
                PropertyDetail detail = getPropertyDetailFromRedis(propertyId);
                if (detail != null) {
                    propertyDetails.add(detail);
                }
            }
        }

        return propertyDetails;
    }

    /**
     * Redis에서 매물 상세 정보 조회 (개별 조회용 - 폴백 전용)
     */
    private PropertyDetail getPropertyDetailFromRedis(String propertyId) {
        try {
            String propertyKey = "property:" + propertyId;
            Map<Object, Object> propertyHash = redisHandler.redisTemplate.opsForHash().entries(propertyKey);

            if (propertyHash.isEmpty()) {
                return null;
            }

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
                    .safetyScore(null) // 현재 안전 점수는 저장되지 않음
                    .build();

        } catch (Exception e) {
            log.debug("매물 [{}] 상세 정보 조회 실패", propertyId, e);
            return null;
        }
    }

    /**
     * 전세/월세 구분하여 총 가격 계산
     * 전세: 보증금만, 월세: 보증금 + (월세 × 24개월) 환산
     */
    private Integer calculateTotalPrice(PropertyDetail detail) {

        if (detail.getDeposit() == null) return null;

        // 전세인 경우: 보증금만
        if ("전세".equals(detail.getLeaseType())) {
            return detail.getDeposit();
        }

        // 월세인 경우: 보증금 + (월세 × 24개월)
        if ("월세".equals(detail.getLeaseType()) && detail.getMonthlyRent() != null) {
            return detail.getDeposit() + (detail.getMonthlyRent() * 24);
        }

        // 기본적으로 보증금만 반환
        return detail.getDeposit();
    }

    /**
     * 가격 점수 계산 (낮은 가격일수록 높은 점수)
     * 설계서 공식: 가격_점수 = 100 - ((현재_가격 - 최저_가격) / (최고_가격 - 최저_가격) × 100)
     */
    private double calculatePriceScore(Integer price, ScoreNormalizationBounds bounds) {
        if (price == null) return 0.0;

        double normalizedPrice = (price - bounds.getMinPrice()) / (bounds.getMaxPrice() - bounds.getMinPrice());
        return Math.max(0.0, Math.min(100.0, 100.0 - (normalizedPrice * 100.0)));
    }

    /**
     * 공간 점수 계산 (넓은 평수일수록 높은 점수)
     * 설계서 공식: 공간_점수 = ((현재_평수 - 최소_평수) / (최대_평수 - 최소_평수) × 100)
     */
    private double calculateSpaceScore(Double areaInPyeong, ScoreNormalizationBounds bounds) {
        if (areaInPyeong == null) return 0.0;

        double normalizedArea = (areaInPyeong - bounds.getMinArea()) / (bounds.getMaxArea() - bounds.getMinArea());
        return Math.max(0.0, Math.min(100.0, normalizedArea * 100.0));
    }

    /**
     * 사용자 우선순위에 따른 가중치 적용하여 최종 점수 계산
     * 설계서 공식: 최종_점수 = (항목1_점수 × 가중치1) + (항목2_점수 × 가중치2) + (항목3_점수 × 가중치3)
     * 가중치: 1순위 60%, 2순위 30%, 3순위 10%
     */
    private double calculateWeightedFinalScore(double priceScore, double spaceScore, double safetyScore,
                                               RecommendationRequestDto request) {

        // 우선순위별 가중치 매핑
        Map<String, Double> priorityWeights = new HashMap<>();
        priorityWeights.put(request.getPriority1(), 0.6); // 1순위: 60%
        priorityWeights.put(request.getPriority2(), 0.3); // 2순위: 30%
        priorityWeights.put(request.getPriority3(), 0.1); // 3순위: 10%

        // 각 항목별 점수에 가중치 적용
        double weightedPriceScore = priceScore * priorityWeights.getOrDefault("PRICE", 0.0);
        double weightedSpaceScore = spaceScore * priorityWeights.getOrDefault("SPACE", 0.0);
        double weightedSafetyScore = safetyScore * priorityWeights.getOrDefault("SAFETY", 0.0);

        double finalScore = weightedPriceScore + weightedSpaceScore + weightedSafetyScore;

        log.debug("점수 계산: 가격={} × {} + 공간={} × {} + 안전={} × {} = {}",
                String.format("%.1f", priceScore), priorityWeights.getOrDefault("PRICE", 0.0),
                String.format("%.1f", spaceScore), priorityWeights.getOrDefault("SPACE", 0.0),
                String.format("%.1f", safetyScore), priorityWeights.getOrDefault("SAFETY", 0.0),
                String.format("%.1f", finalScore));

        return finalScore;
    }

    /**
     * Redis Hash에서 String 값 안전하게 추출
     */
    private String getStringValue(Map<Object, Object> hash, String key) {
        Object value = hash.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Redis Hash에서 Integer 값 안전하게 추출
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
     * Redis Hash에서 Double 값 안전하게 추출
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

    /**
     * 특정 지역구에서 조건에 맞는 매물 ID 목록을 찾기
     *
     * 목적: Redis Sorted Set 인덱스를 활용해 가격과 평수 조건을 모두 만족하는 매물 검색
     * 과정: 1) 가격 조건 매물 검색 → 2) 평수 조건 매물 검색 → 3) 교집합으로 최종 매물 선별
     * 반환: 두 조건을 모두 만족하는 매물 ID 리스트
     */
    private List<String> findValidPropertiesInDistrict(String district, String leaseType,
                                                       RecommendationRequestDto request) {
        try {
            // === 1단계: 가격 조건 만족 매물 ID 조회 ===
            String priceIndexKey = "idx:price:" + district + ":" + leaseType;
            Set<Object> priceValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(priceIndexKey, request.getBudgetMin(), request.getBudgetMax());

            if (priceValidObjects == null || priceValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            // Redis Object를 String으로 변환 (매물 ID는 문자열로 저장됨)
            Set<String> priceValidIds = priceValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            // === 2단계: 평수 조건 만족 매물 ID 조회 ===
            String areaIndexKey = "idx:area:" + district + ":" + leaseType;
            Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                    .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

            if (areaValidObjects == null || areaValidObjects.isEmpty()) {
                return Collections.emptyList();
            }

            // Redis Object를 String으로 변환
            Set<String> areaValidIds = areaValidObjects.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            // === 3단계: 교집합 연산 (두 조건 모두 만족하는 매물만 선별) ===
            priceValidIds.retainAll(areaValidIds);

            return new ArrayList<>(priceValidIds);

        } catch (Exception e) {
            log.debug("지역구 [{}] 검색 중 오류", district, e);
            // 특정 지역구의 검색 실패가 전체 프로세스를 중단시키지 않도록 빈 리스트 반환
            return Collections.emptyList();
        }
    }

    /**
     * 3순위 조건을 완화한 새로운 요청 객체 생성
     *
     * 목적: 매물 부족 시 3순위 조건을 사용자 설정에 따라 완화
     * 과정: 1) 원본 요청 복사 → 2) 3순위에 해당하는 조건 완화 → 3) 새 요청 객체 반환
     * 반환: 3순위 조건이 완화된 검색 요청 DTO
     */
    private RecommendationRequestDto relaxThirdPriority(RecommendationRequestDto original) {
        RecommendationRequestDto relaxed = copyRequest(original);

        switch (original.getPriority3()) {
            case "PRICE":
                // 예산 완화: budgetFlexibility %만큼 최대 예산 증가
                if (original.getBudgetFlexibility() != null && original.getBudgetFlexibility() > 0) {
                    int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
                    relaxed.setBudgetMax(original.getBudgetMax() + flexAmount);
                    log.debug("예산 완화: {}만원 → {}만원", original.getBudgetMax(), relaxed.getBudgetMax());
                }
                break;
            case "SAFETY":
                // 안전 점수 완화: minSafetyScore까지 허용
                // TODO: 현재는 안전 점수 기반 인덱스가 없으므로 실제 적용 불가
                // 향후 안전 점수 Sorted Set 구현 시 활용
                log.debug("안전 점수 완화 (현재 미구현)");
                break;
            case "SPACE":
                // 평수 완화: absoluteMinArea까지 허용
                if (original.getAbsoluteMinArea() != null && original.getAbsoluteMinArea() > 0) {
                    relaxed.setAreaMin(original.getAbsoluteMinArea());
                    log.debug("평수 완화: {}평 → {}평", original.getAreaMin(), relaxed.getAreaMin());
                }
                break;
            default:
                log.warn("알 수 없는 우선순위: {}", original.getPriority3());
        }

        return relaxed;
    }

    /**
     * 2순위 조건을 추가로 완화한 새로운 요청 객체 생성
     *
     * 목적: 3순위 완화로도 부족할 때 2순위 조건을 추가로 완화
     * 과정: 1) 이미 완화된 요청 복사 → 2) 2순위 조건 완화 → 3) 중복 완화시 더 관대한 값 적용
     * 반환: 2순위까지 완화된 검색 요청 DTO
     */
    private RecommendationRequestDto relaxSecondPriority(RecommendationRequestDto expandedRequest,
                                                         RecommendationRequestDto original) {
        RecommendationRequestDto doubleRelaxed = copyRequest(expandedRequest);

        switch (original.getPriority2()) {
            case "PRICE":
                if (original.getBudgetFlexibility() != null && original.getBudgetFlexibility() > 0) {
                    int flexAmount = (int) (original.getBudgetMax() * (original.getBudgetFlexibility() / 100.0));
                    int newBudgetMax = original.getBudgetMax() + flexAmount;
                    // 이미 3순위에서 예산이 완화된 경우 더 큰 값 사용
                    doubleRelaxed.setBudgetMax(Math.max(doubleRelaxed.getBudgetMax(), newBudgetMax));
                    log.debug("예산 2차 완화: {}만원", doubleRelaxed.getBudgetMax());
                }
                break;
            case "SAFETY":
                // TODO: 안전 점수 기반 검색 로직 구현 시 적용
                log.debug("안전 점수 2차 완화 (현재 미구현)");
                break;
            case "SPACE":
                if (original.getAbsoluteMinArea() != null && original.getAbsoluteMinArea() > 0) {
                    // 이미 3순위에서 평수가 완화된 경우 더 작은 값 사용 (더 관대한 조건)
                    doubleRelaxed.setAreaMin(Math.min(doubleRelaxed.getAreaMin(), original.getAbsoluteMinArea()));
                    log.debug("평수 2차 완화: {}평", doubleRelaxed.getAreaMin());
                }
                break;
            default:
                log.warn("알 수 없는 우선순위: {}", original.getPriority2());
        }

        return doubleRelaxed;
    }

    /**
     * 요청 객체를 깊은 복사하여 새 인스턴스 생성
     *
     * 목적: 원본 요청을 변경하지 않고 완화된 조건의 새 요청 생성
     * 과정: 모든 필드를 개별적으로 복사하여 독립적인 새 객체 생성
     * 반환: 원본과 동일한 내용의 새로운 요청 DTO
     */
    private RecommendationRequestDto copyRequest(RecommendationRequestDto original) {
        return RecommendationRequestDto.builder()
                .leaseType(original.getLeaseType())
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
     * 완화된 조건에 대한 사용자 안내 메시지 생성
     *
     * 목적: 어떤 조건이 어떻게 완화되었는지 사용자에게 명확히 안내
     * 과정: 우선순위 유형과 완화된 값을 기반으로 적절한 메시지 생성
     * 반환: 완화 내용을 설명하는 안내 문구
     */
    private String getRelaxedConditionMessage(String priority, RecommendationRequestDto original,
                                              RecommendationRequestDto relaxed) {
        switch (priority) {
            case "PRICE":
                return "예산 조건을 " + relaxed.getBudgetMax() + "만원으로";
            case "SAFETY":
                return "안전 점수 기준을";
            case "SPACE":
                return "평수 조건을 " + relaxed.getAreaMin() + "평으로";
            default:
                return "검색 조건을";
        }
    }

    // === 내부 헬퍼 클래스 ===

    /**
     * 검색 결과를 담는 내부 클래스
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

    // === S-04 관련 내부 클래스들 ===

    /**
     * 점수가 계산된 매물 정보를 담는 클래스
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

    // === S-05 관련 내부 클래스들 ===

    /**
     * 지역구별 대표 점수가 계산된 정보를 담는 클래스
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
     * 매물 상세 정보를 담는 클래스 (Redis 저장 구조와 동일)
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
     * 점수 정규화를 위한 범위 정보를 담는 클래스
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