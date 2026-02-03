package com.WhereHouse.AnalysisData.danran.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.List;

/**
 * 단란주점 전용 Kakao 지오코딩 서비스
 *
 * 지번주소와 도로명주소 모두 활용한 이중 지오코딩 처리
 *
 * 주요 기능:
 * - Kakao Local API를 통한 주소 → 좌표 변환
 * - 도로명주소 우선, 실패 시 지번주소로 재시도
 * - 서울시 영역 유효성 검증
 * - 주소 타입별 성공률 추적
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DanranGeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    @Value("${kakao.api.geocoding.url}")
    private String geocodingUrl;

    @Value("${apps.analysis.police-facility.seoul-bounds.min-lat}")
    private Double seoulMinLat;

    @Value("${apps.analysis.police-facility.seoul-bounds.max-lat}")
    private Double seoulMaxLat;

    @Value("${apps.analysis.police-facility.seoul-bounds.min-lng}")
    private Double seoulMinLng;

    @Value("${apps.analysis.police-facility.seoul-bounds.max-lng}")
    private Double seoulMaxLng;

    /**
     * 단란주점 이중 주소 기반 지오코딩
     *
     * @param jibunAddress 지번주소
     * @param roadAddress 도로명주소
     * @param businessName 업소명 (로깅용)
     * @return EnhancedGeocodingResult 객체 (위도, 경도, 사용된 주소 타입 포함)
     */
    public EnhancedGeocodingResult getCoordinatesWithDualAddress(
            String jibunAddress, String roadAddress, String businessName) {

        // 1차 시도: 도로명주소 우선 (일반적으로 더 정확함)
        if (roadAddress != null && !roadAddress.trim().isEmpty() && !"데이터없음".equals(roadAddress)) {
            log.debug("[{}] 도로명주소 지오코딩 시도: {}", businessName, roadAddress);

            GeocodingResult roadResult = getCoordinates(roadAddress);

            if (roadResult.isSuccess()) {
                log.debug("[{}] 도로명주소 지오코딩 성공", businessName);
                return EnhancedGeocodingResult.success(
                        roadResult.getLatitude(), roadResult.getLongitude(),
                        "ROAD", roadResult.getApiResponseAddress(), roadAddress);
            }
        }

        // 2차 시도: 지번주소 (도로명주소 실패 시)
        if (jibunAddress != null && !jibunAddress.trim().isEmpty() && !"데이터없음".equals(jibunAddress)) {
            log.debug("[{}] 지번주소 지오코딩 시도: {}", businessName, jibunAddress);

            GeocodingResult jibunResult = getCoordinates(jibunAddress);

            if (jibunResult.isSuccess()) {
                log.debug("[{}] 지번주소 지오코딩 성공", businessName);
                return EnhancedGeocodingResult.success(
                        jibunResult.getLatitude(), jibunResult.getLongitude(),
                        "JIBUN", jibunResult.getApiResponseAddress(), jibunAddress);
            }
        }

        // 모든 시도 실패
        log.warn("[{}] 모든 주소 지오코딩 실패 - 도로명: {}, 지번: {}",
                businessName, roadAddress, jibunAddress);

        return EnhancedGeocodingResult.failed("모든 주소 지오코딩 실패");
    }

    /**
     * 단일 주소를 기반으로 좌표 정보를 조회
     *
     * @param address 지오코딩할 주소
     * @return GeocodingResult 객체
     */
    private GeocodingResult getCoordinates(String address) {
        try {
            // 주소 전처리
            String cleanedAddress = preprocessAddress(address);
            log.debug("지오코딩 요청 - 원본: {}, 전처리: {}", address, cleanedAddress);

            // Kakao API 요청 URL 구성
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(geocodingUrl)
                    .queryParam("query", cleanedAddress)
                    .build()
                    .encode()
                    .toUri();

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            RequestEntity<Void> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);

            // API 호출 및 응답 처리
            ResponseEntity<Map> response = restTemplate.exchange(requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseApiResponse(response.getBody(), address);
            } else {
                return GeocodingResult.failed(address, "API 호출 실패");
            }

        } catch (Exception e) {
            log.error("지오코딩 처리 중 예외 발생 - 주소: {}, 오류: {}", address, e.getMessage());
            return GeocodingResult.failed(address, "예외 발생: " + e.getMessage());
        }
    }

    /**
     * 주소 전처리 메서드
     */
    private String preprocessAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }

        String processed = address.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[()（）]", "")
                .replaceAll("\\d+층.*$", "")
                .trim();

        if (!processed.startsWith("서울") && !processed.startsWith("Seoul")) {
            processed = "서울특별시 " + processed;
        }

        return processed;
    }

    /**
     * API 응답 파싱 및 좌표 추출
     */
    @SuppressWarnings("unchecked")
    private GeocodingResult parseApiResponse(Map<String, Object> apiResponse, String originalAddress) {
        try {
            List<Map<String, Object>> documents = (List<Map<String, Object>>) apiResponse.get("documents");

            if (documents == null || documents.isEmpty()) {
                return GeocodingResult.failed(originalAddress, "좌표 정보 없음");
            }

            Map<String, Object> firstResult = documents.get(0);
            Map<String, Object> addressInfo = null;
            String resultAddress = null;

            if (firstResult.containsKey("road_address") && firstResult.get("road_address") != null) {
                addressInfo = (Map<String, Object>) firstResult.get("road_address");
                resultAddress = (String) addressInfo.get("address_name");
            } else if (firstResult.containsKey("address") && firstResult.get("address") != null) {
                addressInfo = (Map<String, Object>) firstResult.get("address");
                resultAddress = (String) addressInfo.get("address_name");
            }

            if (addressInfo == null) {
                return GeocodingResult.failed(originalAddress, "주소 정보 파싱 실패");
            }

            String xCoord = (String) addressInfo.get("x");
            String yCoord = (String) addressInfo.get("y");

            if (xCoord == null || yCoord == null) {
                return GeocodingResult.failed(originalAddress, "좌표 값 없음");
            }

            Double longitude = Double.parseDouble(xCoord);
            Double latitude = Double.parseDouble(yCoord);

            // 서울시 영역 유효성 검증
            if (!isValidSeoulCoordinate(latitude, longitude)) {
                return GeocodingResult.failed(originalAddress, "서울시 영역 외부 좌표");
            }

            return GeocodingResult.success(originalAddress, latitude, longitude, resultAddress);

        } catch (Exception e) {
            return GeocodingResult.failed(originalAddress, "응답 파싱 오류");
        }
    }

    /**
     * 서울시 영역 내 좌표인지 검증
     */
    private boolean isValidSeoulCoordinate(Double latitude, Double longitude) {
        return latitude >= seoulMinLat && latitude <= seoulMaxLat &&
                longitude >= seoulMinLng && longitude <= seoulMaxLng;
    }

    /**
     * 기본 지오코딩 결과 클래스
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class GeocodingResult {
        private String originalAddress;
        private Double latitude;
        private Double longitude;
        private String apiResponseAddress;
        private boolean success;
        private String errorMessage;

        public static GeocodingResult success(String originalAddress, Double latitude, Double longitude, String apiResponseAddress) {
            return GeocodingResult.builder()
                    .originalAddress(originalAddress)
                    .latitude(latitude)
                    .longitude(longitude)
                    .apiResponseAddress(apiResponseAddress)
                    .success(true)
                    .build();
        }

        public static GeocodingResult failed(String originalAddress, String errorMessage) {
            return GeocodingResult.builder()
                    .originalAddress(originalAddress)
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    /**
     * 확장된 지오코딩 결과 클래스 (주소 타입 포함)
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class EnhancedGeocodingResult {
        private Double latitude;
        private Double longitude;
        private String addressType;           // JIBUN, ROAD
        private String apiResponseAddress;
        private String originalAddress;
        private boolean success;
        private String errorMessage;

        public static EnhancedGeocodingResult success(
                Double latitude, Double longitude, String addressType,
                String apiResponseAddress, String originalAddress) {
            return EnhancedGeocodingResult.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .addressType(addressType)
                    .apiResponseAddress(apiResponseAddress)
                    .originalAddress(originalAddress)
                    .success(true)
                    .build();
        }

        public static EnhancedGeocodingResult failed(String errorMessage) {
            return EnhancedGeocodingResult.builder()
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}