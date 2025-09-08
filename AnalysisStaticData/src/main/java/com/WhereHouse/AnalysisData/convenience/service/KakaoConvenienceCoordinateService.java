package com.WhereHouse.AnalysisData.convenience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kakao Local API를 활용한 편의점 특화 좌표 계산 서비스
 * 편의점 브랜드명 제거 및 주소 전처리를 통한 API 인식률 향상
 */
@Service
@Slf4j
public class KakaoConvenienceCoordinateService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // API 호출 통계
    private final AtomicLong totalApiCalls = new AtomicLong(0);
    private final AtomicLong successfulApiCalls = new AtomicLong(0);
    private final AtomicLong failedApiCalls = new AtomicLong(0);

    // 편의점 브랜드 키워드 (제거 대상)
    private static final Set<String> CONVENIENCE_STORE_KEYWORDS = Set.of(
            "세븐일레븐", "세븐-일레븐", "7-ELEVEN", "7ELEVEN",
            "CU", "씨유",
            "GS25", "지에스25",
            "이마트24", "EMART24",
            "미니스톱", "MINISTOP",
            "바이더웨이", "BY THE WAY",
            "편의점", "점포", "매장", "스토어", "STORE"
    );

    // 주소 정제 시 제거할 패턴
    private static final List<String> BUILDING_PATTERNS = Arrays.asList(
            "\\d+층", "\\d+호", "지하\\d+층", "\\d+F", "B\\d+",
            "빌딩", "타워", "상가", "플라자", "센터", "몰"
    );

    public KakaoConvenienceCoordinateService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 편의점 주소를 기반으로 좌표 계산
     * 편의점 특화 주소 전처리를 통해 API 인식률 향상
     */
    public CoordinateResult calculateCoordinates(String roadAddress, String lotAddress, String businessName) {
        log.debug("좌표 계산 시작 - 사업장명: {}", businessName);

        // 처리 우선순위: roadAddress -> lotAddress
        String primaryAddress = (roadAddress != null && !roadAddress.trim().isEmpty()) ? roadAddress : lotAddress;

        if (primaryAddress == null || primaryAddress.trim().isEmpty()) {
            log.debug("유효한 주소 정보 없음 - 사업장명: {}", businessName);
            return CoordinateResult.failure("주소 정보 없음");
        }

        // 편의점 특화 주소 전처리 단계별 시도
        List<String> addressVariants = generateAddressVariants(primaryAddress.trim(), businessName);

        for (int i = 0; i < addressVariants.size(); i++) {
            String processedAddress = addressVariants.get(i);
            log.debug("주소 변형 {} 시도: {} -> {}", i + 1, primaryAddress, processedAddress);

            CoordinateResult result = callKakaoApi(processedAddress);
            if (result.isSuccess()) {
                log.debug("좌표 계산 성공 - 사업장명: {}, 변형단계: {}, 좌표: ({}, {})",
                        businessName, i + 1, result.getLatitude(), result.getLongitude());
                return result;
            }
        }

        log.error("좌표 계산 실패 - 사업장명: {}, 원본주소: {}", businessName, primaryAddress);
        return CoordinateResult.failure("모든 주소 변형 시도 실패");
    }

    /**
     * 편의점 특화 주소 변형 생성
     * 단계별로 주소를 정제하여 API 인식률 향상
     */
    private List<String> generateAddressVariants(String originalAddress, String businessName) {
        List<String> variants = new ArrayList<>();

        // 1단계: 원본 주소 그대로
        variants.add(originalAddress);

        // 2단계: 편의점 키워드 제거
        String withoutStoreKeywords = removeConvenienceStoreKeywords(originalAddress, businessName);
        if (!withoutStoreKeywords.equals(originalAddress)) {
            variants.add(withoutStoreKeywords);
        }

        // 3단계: 건물 정보 제거
        String withoutBuilding = removeBuildingInfo(withoutStoreKeywords);
        if (!withoutBuilding.equals(withoutStoreKeywords)) {
            variants.add(withoutBuilding);
        }

        // 4단계: 괄호 내용 제거
        String withoutParentheses = removeParentheses(withoutBuilding);
        if (!withoutParentheses.equals(withoutBuilding)) {
            variants.add(withoutParentheses);
        }

        // 5단계: 핵심 주소만 추출 (시/도/구/동/번지)
        String coreAddress = extractCoreAddress(withoutParentheses);
        if (!coreAddress.equals(withoutParentheses) && coreAddress.length() >= 5) {
            variants.add(coreAddress);
        }

        return variants;
    }

    /**
     * 편의점 브랜드 키워드 제거
     */
    private String removeConvenienceStoreKeywords(String address, String businessName) {
        String result = address;

        // 사업장명에서 추출한 브랜드명도 제거 대상에 추가
        Set<String> keywordsToRemove = new HashSet<>(CONVENIENCE_STORE_KEYWORDS);
        if (businessName != null) {
            for (String keyword : CONVENIENCE_STORE_KEYWORDS) {
                if (businessName.contains(keyword)) {
                    keywordsToRemove.add(keyword);
                }
            }
        }

        // 키워드 제거
        for (String keyword : keywordsToRemove) {
            result = result.replaceAll(keyword, "").trim();
        }

        return cleanupSpaces(result);
    }

    /**
     * 건물 정보 제거
     */
    private String removeBuildingInfo(String address) {
        String result = address;

        for (String pattern : BUILDING_PATTERNS) {
            result = result.replaceAll(pattern, "").trim();
        }

        return cleanupSpaces(result);
    }

    /**
     * 괄호 내용 제거
     */
    private String removeParentheses(String address) {
        return address.replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[[^]]*\\]", "")
                .replaceAll("\\{[^}]*\\}", "")
                .trim();
    }

    /**
     * 핵심 주소 추출 (시/도/구/동/번지 수준)
     */
    private String extractCoreAddress(String address) {
        String result = address;

        // 특별시/광역시 표기 통일
        result = result.replace("특별시", "시")
                .replace("광역시", "시");

        // 상세 번지나 건물명 제거 (기본 번지까지만 유지)
        result = result.replaceAll("\\s+\\d+[-~]\\d+", "")
                .replaceAll("\\s+[가-힣]+\\d+", "");

        return cleanupSpaces(result);
    }

    /**
     * 연속 공백 정리 및 특수문자 제거
     */
    private String cleanupSpaces(String text) {
        return text.replaceAll("\\s+", " ")
                .replaceAll("[,\\-]+$", "")
                .trim();
    }

    /**
     * Kakao Local API 호출
     */
    private CoordinateResult callKakaoApi(String address) {
        totalApiCalls.incrementAndGet();

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://dapi.kakao.com/v2/local/search/address.json")
                    .queryParam("query", address)
                    .build()
                    .toUriString();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseKakaoResponse(response.getBody());
            } else {
                failedApiCalls.incrementAndGet();
                return CoordinateResult.failure("API 호출 실패: " + response.getStatusCode());
            }

        } catch (Exception e) {
            failedApiCalls.incrementAndGet();
            log.error("Kakao API 호출 중 예외 발생 - 주소: {}, 오류: {}", address, e.getMessage());
            return CoordinateResult.failure("API 호출 예외: " + e.getMessage());
        }
    }

    /**
     * Kakao API 응답 파싱
     */
    private CoordinateResult parseKakaoResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.get("documents");

            if (documents == null || documents.isEmpty()) {
                failedApiCalls.incrementAndGet();
                return CoordinateResult.failure("검색 결과 없음");
            }

            JsonNode firstResult = documents.get(0);

            // road_address 우선, 없으면 address 사용
            JsonNode roadAddress = firstResult.get("road_address");
            JsonNode address = firstResult.get("address");

            BigDecimal latitude = null;
            BigDecimal longitude = null;

            if (roadAddress != null && !roadAddress.isNull()) {
                latitude = new BigDecimal(roadAddress.get("y").asText());
                longitude = new BigDecimal(roadAddress.get("x").asText());
            } else if (address != null && !address.isNull()) {
                latitude = new BigDecimal(address.get("y").asText());
                longitude = new BigDecimal(address.get("x").asText());
            }

            if (latitude != null && longitude != null) {
                // 한국 영역 내 좌표 검증
                if (isValidKoreanCoordinates(latitude, longitude)) {
                    successfulApiCalls.incrementAndGet();
                    return CoordinateResult.success(latitude, longitude);
                } else {
                    failedApiCalls.incrementAndGet();
                    return CoordinateResult.failure("좌표가 한국 영역을 벗어남");
                }
            } else {
                failedApiCalls.incrementAndGet();
                return CoordinateResult.failure("좌표 정보 파싱 실패");
            }

        } catch (Exception e) {
            failedApiCalls.incrementAndGet();
            log.error("Kakao API 응답 파싱 중 오류: {}", e.getMessage());
            return CoordinateResult.failure("응답 파싱 오류: " + e.getMessage());
        }
    }

    /**
     * 한국 영역 내 좌표 검증
     */
    private boolean isValidKoreanCoordinates(BigDecimal latitude, BigDecimal longitude) {
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();

        // 한국 영역: 위도 33.0~38.7, 경도 124.0~132.0
        return lat >= 33.0 && lat <= 38.7 && lng >= 124.0 && lng <= 132.0;
    }

    /**
     * API 호출 통계 반환
     */
    public ApiStatistics getApiStatistics() {
        return new ApiStatistics(
                totalApiCalls.get(),
                successfulApiCalls.get(),
                failedApiCalls.get()
        );
    }

    /**
     * 통계 초기화
     */
    public void resetStatistics() {
        totalApiCalls.set(0);
        successfulApiCalls.set(0);
        failedApiCalls.set(0);
    }

    /**
     * 좌표 계산 결과 클래스
     */
    public static class CoordinateResult {
        private final boolean success;
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final String errorMessage;

        private CoordinateResult(boolean success, BigDecimal latitude, BigDecimal longitude, String errorMessage) {
            this.success = success;
            this.latitude = latitude;
            this.longitude = longitude;
            this.errorMessage = errorMessage;
        }

        public static CoordinateResult success(BigDecimal latitude, BigDecimal longitude) {
            return new CoordinateResult(true, latitude, longitude, null);
        }

        public static CoordinateResult failure(String errorMessage) {
            return new CoordinateResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public BigDecimal getLatitude() { return latitude; }
        public BigDecimal getLongitude() { return longitude; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * API 호출 통계 클래스
     */
    public static class ApiStatistics {
        private final long totalCalls;
        private final long successfulCalls;
        private final long failedCalls;

        public ApiStatistics(long totalCalls, long successfulCalls, long failedCalls) {
            this.totalCalls = totalCalls;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
        }

        public long getTotalCalls() { return totalCalls; }
        public long getSuccessfulCalls() { return successfulCalls; }
        public long getFailedCalls() { return failedCalls; }
        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successfulCalls / totalCalls * 100 : 0.0;
        }
    }
}