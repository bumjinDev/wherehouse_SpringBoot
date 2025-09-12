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
     */
    public RecommendationResponseDto getDistrictRecommendations(RecommendationRequestDto request) {
        log.info("=== 지역구 추천 서비스 시작 ===");
        log.info("요청 조건: 임대유형={}, 예산={}-{}, 평수={}-{}, 우선순위={},{},{}",
                request.getLeaseType(), request.getBudgetMin(), request.getBudgetMax(),
                request.getAreaMin(), request.getAreaMax(),
                request.getPriority1(), request.getPriority2(), request.getPriority3());

        try {
            // S-01: 전 지역구 1차 검색 (Strict Search)
            Map<String, List<String>> districtProperties = performStrictSearch(request);

            // S-02: 폴백 조건 판단 및 확장 검색
            SearchResult searchResult = checkAndPerformFallback(districtProperties, request);

            // TODO: S-04 ~ S-06 구현 예정
            // - 매물 단위 점수 계산
            // - 지역구 단위 점수 계산 및 정렬
            // - 최종 응답 생성

            log.info("=== 지역구 추천 서비스 완료 (임시) ===");

            return RecommendationResponseDto.builder()
                    .searchStatus(searchResult.getSearchStatus())
                    .message(searchResult.getMessage())
                    .recommendedDistricts(Collections.emptyList()) // 임시로 빈 리스트
                    .build();

        } catch (Exception e) {
            log.error("추천 서비스 처리 중 오류 발생", e);
            throw new RuntimeException("추천 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * S-01: 전 지역구 1차 검색 (Strict Search)
     *
     * 서울시 25개 자치구를 순회하며 사용자 조건에 맞는 매물 ID 목록을 Redis Sorted Set에서 검색한다.
     * 가격 조건과 평수 조건을 모두 만족하는 매물만 선별하여 지역구별로 그룹화한다.
     *
     * @param request 사용자 요청 조건 (임대유형, 예산범위, 평수범위, 우선순위 등)
     * @return Map<String, List<String>> 지역구별 유효 매물 목록
     *         - Key: 지역구명 (예: "강남구", "서초구")
     *         - Value: 해당 지역구에서 조건을 만족하는 매물 ID 리스트
     *         - 매물이 없는 지역구는 Map에 포함되지 않음 (빈 리스트가 아닌 키 자체가 없음)
     */
    private Map<String, List<String>> performStrictSearch(RecommendationRequestDto request) {
        log.info("S-01: 전 지역구 1차 검색 시작");

        Map<String, List<String>> result = new HashMap<>();
        // 영문 임대유형을 한글로 변환 (Redis 저장 시 한글로 저장했기 때문)
        String leaseTypeKorean = "CHARTER".equals(request.getLeaseType()) ? "전세" : "월세";
        int totalFound = 0;

        // 서울시 25개 자치구 전체 순회
        for (String district : SEOUL_DISTRICTS) {
            List<String> validProperties = findValidPropertiesInDistrict(
                    district, leaseTypeKorean, request);

            // 조건을 만족하는 매물이 있는 지역구만 결과에 포함
            if (!validProperties.isEmpty()) {
                result.put(district, validProperties);
                totalFound += validProperties.size();
                log.debug("지역구 [{}]: {}개 매물 발견", district, validProperties.size());
            }
        }

        log.info("1차 검색 완료: 총 {}개 매물 발견 ({}개 지역구)", totalFound, result.size());
        return result;
    }

    /**
     * S-02: 폴백 조건 판단 및 확장 검색 수행
     *
     * 폴백 판단 기준:
     * - 매물이 발견된 지역구들 중에서 3개 미만인 곳이 하나라도 있으면 폴백 수행
     * - 매물이 아예 없는 지역구(districtProperties에 키가 없는 지역구)는 판단 대상에서 제외
     *
     * 제외 근거:
     * 1. 매물이 없는 지역구는 애초에 추천 대상이 될 수 없음
     * 2. 사용자에게 "매물 0개 지역구"를 추천할 의미가 없음
     * 3. 실제 추천 가능한 지역구들의 매물 부족 여부만 판단하는 것이 합리적
     *
     * 예시:
     * - 강남구: 5개, 서초구: 2개, 송파구: 4개 → 서초구(2<3) 때문에 폴백 수행
     * - 강남구: 5개, 서초구: 3개, 송파구: 4개 → 모두 3개 이상이므로 정상 처리
     * - 강남구: 0개(키 없음), 서초구: 3개, 송파구: 4개 → 강남구는 판단 제외, 정상 처리
     *
     * @param districtProperties 1차 검색 결과. Key=지역구명, Value=해당 지역구의 유효 매물 ID 리스트
     * @param request 사용자 요청 정보 (확장 검색에 필요한 완화 조건 포함)
     * @return SearchResult 검색 상태, 메시지, 최종 지역구별 매물 목록을 포함한 결과 객체
     */
    private SearchResult checkAndPerformFallback(Map<String, List<String>> districtProperties,
                                                 RecommendationRequestDto request) {

        // 매물이 발견된 지역구들 중에서 3개 미만인 곳이 있는지 검사
        // 매물이 아예 없는 지역구(Map에 키가 없는 지역구)는 자동으로 제외됨
        boolean hasInsufficientDistricts = districtProperties.values().stream()
                .anyMatch(propertyList -> propertyList.size() < MIN_PROPERTIES_THRESHOLD);

        int totalPropertiesFound = districtProperties.values().stream()
                .mapToInt(List::size).sum();

        int districtsWithProperties = districtProperties.size(); // 매물이 있는 지역구 수

        log.info("S-02: 폴백 조건 판단 - 전체 매물: {}개, 매물 보유 지역구: {}개",
                totalPropertiesFound, districtsWithProperties);

        // 매물이 있는 모든 지역구가 3개 이상의 매물을 보유한 경우
        if (!hasInsufficientDistricts) {
            return SearchResult.builder()
                    .searchStatus("SUCCESS_NORMAL")
                    .message("조건에 맞는 매물을 성공적으로 찾았습니다.")
                    .districtProperties(districtProperties)
                    .build();
        }

        // 일부 지역구의 매물이 3개 미만 - 확장 검색 수행
        log.info("일부 지역구의 매물 부족 - S-03 확장 검색 수행");
        return performExpandedSearch(request);
    }

    /**
     * S-03: 2차 확장 검색 (Expanded Search)
     *
     * 1차 검색에서 일부 지역구의 매물이 부족할 때, 사용자의 우선순위에 따라 조건을 단계적으로 완화한다.
     * 3순위 → 2순위 순서로 조건을 완화하여 재검색하며, 1순위 조건은 원칙적으로 유지한다.
     *
     * @param originalRequest 사용자의 원본 요청 조건
     * @param insufficientDistricts 매물이 부족한 지역구 목록 (3개 미만인 지역구들)
     * @return SearchResult 확장 검색 결과
     *         - searchStatus: "SUCCESS_EXPANDED" 또는 "NO_RESULTS"
     *         - message: 어떤 조건이 완화되었는지 사용자에게 알리는 메시지
     *         - districtProperties: 완화된 조건으로 검색한 지역구별 매물 목록
     */
    private SearchResult performExpandedSearch(RecommendationRequestDto originalRequest,
                                               List<String> insufficientDistricts) {
        log.info("S-03: 2차 확장 검색 시작 - 대상 지역구: {}", insufficientDistricts);

        // === 1단계: 3순위 조건 완화 ===
        RecommendationRequestDto expandedRequest = relaxThirdPriority(originalRequest);
        String relaxedCondition = getRelaxedConditionMessage(originalRequest.getPriority3(), originalRequest, expandedRequest);

        // 완화된 조건으로 재검색
        Map<String, List<String>> expandedResult = performStrictSearch(expandedRequest);

        // 3순위 완화만으로 충분한지 검사 (각 지역구별 3개 이상 기준)
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
        RecommendationRequestDto doubleExpandedRequest = relaxSecondPriority(expandedRequest, originalRequest);
        String doubleRelaxedCondition = relaxedCondition + ", " + getRelaxedConditionMessage(originalRequest.getPriority2(), originalRequest, doubleExpandedRequest);

        Map<String, List<String>> doubleExpandedResult = performStrictSearch(doubleExpandedRequest);

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
     * 특정 지역구에서 조건에 맞는 매물 ID 목록을 찾기
     *
     * Redis Sorted Set 인덱스를 사용하여 가격 조건과 평수 조건을 모두 만족하는 매물들을 검색한다.
     * 두 개의 Sorted Set(가격 인덱스, 평수 인덱스)에서 각각 범위 검색을 수행한 후 교집합을 구한다.
     *
     * @param district 검색할 지역구명 (예: "강남구")
     * @param leaseType 한글 임대유형 ("전세" 또는 "월세")
     * @param request 사용자 요청 조건 (예산 및 평수 범위)
     * @return List<String> 두 조건을 모두 만족하는 매물 ID 리스트
     *         - 빈 리스트: 조건을 만족하는 매물이 없음
     *         - Redis 오류 시에도 빈 리스트 반환 (예외를 상위로 전파하지 않음)
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
     * 사용자가 설정한 완화 임계값을 사용하여 해당 우선순위 조건을 완화한다.
     *
     * @param original 원본 요청
     * @return RecommendationRequestDto 3순위 조건이 완화된 요청 객체
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
     * 이미 3순위가 완화된 요청에 대해 2순위 조건도 완화한다.
     * 동일한 조건이 중복 완화되는 경우 더 관대한 값을 적용한다.
     *
     * @param expandedRequest 이미 3순위가 완화된 요청
     * @param original 원본 요청 (완화 기준값 참조용)
     * @return RecommendationRequestDto 2순위까지 완화된 요청 객체
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
     * 원본 요청을 변경하지 않고 완화된 조건의 새 요청을 생성하기 위해 사용한다.
     *
     * @param original 복사할 원본 요청
     * @return RecommendationRequestDto 복사된 새 요청 객체
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
     * @param priority 완화된 우선순위 ("PRICE", "SAFETY", "SPACE")
     * @param original 원본 요청
     * @param relaxed 완화된 요청
     * @return String 사용자에게 보여줄 완화 내용 메시지
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
}