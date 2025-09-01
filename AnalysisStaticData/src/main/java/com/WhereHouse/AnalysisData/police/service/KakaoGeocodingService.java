package com.WhereHouse.AnalysisData.police.service;

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
 * Kakao 지오코딩 API 연동 서비스
 *
 * 주소 정보를 바탕으로 정확한 위도/경도 좌표를 계산하는 서비스
 *
 * 주요 기능:
 * - Kakao Local API를 통한 주소 → 좌표 변환
 * - API 응답 파싱 및 좌표 추출
 * - 서울시 영역 유효성 검증
 * - Rate Limit 준수를 위한 호출 제한
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoGeocodingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    @Value("${kakao.api.geocoding.url}")
    private String geocodingUrl;

    @Value("${app.analysis.police-facility.seoul-bounds.min-lat}")
    private Double seoulMinLat;

    @Value("${app.analysis.police-facility.seoul-bounds.max-lat}")
    private Double seoulMaxLat;

    @Value("${app.analysis.police-facility.seoul-bounds.min-lng}")
    private Double seoulMinLng;

    @Value("${app.analysis.police-facility.seoul-bounds.max-lng}")
    private Double seoulMaxLng;

    /**
     * 주소를 기반으로 좌표 정보를 조회
     *
     * @param address 지오코딩할 주소 (예: "서울특별시 중구 태평로2가 13")
     * @return GeocodingResult 객체 (위도, 경도, 상태 정보 포함)
     */
    public GeocodingResult getCoordinates(String address) {
        try {
            // 주소 전처리 (분석 정확도 향상을 위한 클렌징)
            String cleanedAddress = preprocessAddress(address);
            log.debug("지오코딩 요청 - 원본 주소: {}, 전처리된 주소: {}", address, cleanedAddress);

            // Kakao API 요청 URL 구성
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(geocodingUrl)
                    .queryParam("query", cleanedAddress)
                    .build()
                    .encode()
                    .toUri();

            // HTTP 헤더 설정 (Authorization 포함)
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            RequestEntity<Void> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);

            // API 호출 및 응답 처리
            ResponseEntity<Map> response = restTemplate.exchange(requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseApiResponse(response.getBody(), address);
            } else {
                log.warn("Kakao API 호출 실패 - 상태 코드: {}, 주소: {}", response.getStatusCode(), address);
                return GeocodingResult.failed(address, "API 호출 실패");
            }

        } catch (Exception e) {
            log.error("지오코딩 처리 중 예외 발생 - 주소: {}, 오류: {}", address, e.getMessage(), e);
            return GeocodingResult.failed(address, "예외 발생: " + e.getMessage());
        }
    }

    /**
     * 주소 전처리 메서드
     * Kakao API 정확도 향상을 위한 주소 정제
     */
    private String preprocessAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "";
        }

        String processed = address.trim()
                .replaceAll("\\s+", " ")                    // 연속된 공백 제거
                .replaceAll("[()（）]", "")                  // 괄호 제거
                .replaceAll("(구)\\s*$", "")                // 주소 끝의 "구" 제거
                .replaceAll("(동)\\s*$", "")                // 주소 끝의 "동" 제거
                .replaceAll("번지$", "")                    // 주소 끝의 "번지" 제거
                .replaceAll("\\d+층.*$", "")                // 층수 정보 제거
                .trim();

        // 서울특별시로 시작하지 않는 경우 추가
        if (!processed.startsWith("서울") && !processed.startsWith("Seoul")) {
            processed = "서울특별시 " + processed;
        }

        return processed;
    }

    /**
     * Kakao API 응답 파싱 및 좌표 추출
     */
    @SuppressWarnings("unchecked")
    private GeocodingResult parseApiResponse(Map<String, Object> apiResponse, String originalAddress) {
        try {
            List<Map<String, Object>> documents = (List<Map<String, Object>>) apiResponse.get("documents");

            if (documents == null || documents.isEmpty()) {
                log.warn("API 응답에서 좌표 정보를 찾을 수 없음 - 주소: {}", originalAddress);
                return GeocodingResult.failed(originalAddress, "좌표 정보 없음");
            }

            // 첫 번째 결과 사용 (일반적으로 가장 정확한 결과)
            Map<String, Object> firstResult = documents.get(0);

            // 도로명 주소 또는 지번 주소 선택
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

            // 좌표 정보 추출
            String xCoord = (String) addressInfo.get("x");  // 경도
            String yCoord = (String) addressInfo.get("y");  // 위도

            if (xCoord == null || yCoord == null) {
                return GeocodingResult.failed(originalAddress, "좌표 값 없음");
            }

            Double longitude = Double.parseDouble(xCoord);
            Double latitude = Double.parseDouble(yCoord);

            // 서울시 영역 유효성 검증
            if (!isValidSeoulCoordinate(latitude, longitude)) {
                log.warn("서울시 영역을 벗어난 좌표 - 주소: {}, 위도: {}, 경도: {}", originalAddress, latitude, longitude);
                return GeocodingResult.failed(originalAddress, "서울시 영역 외부 좌표");
            }

            log.debug("지오코딩 성공 - 주소: {}, 위도: {}, 경도: {}, API 응답 주소: {}",
                    originalAddress, latitude, longitude, resultAddress);

            return GeocodingResult.success(originalAddress, latitude, longitude, resultAddress);

        } catch (Exception e) {
            log.error("API 응답 파싱 중 오류 발생 - 주소: {}, 오류: {}", originalAddress, e.getMessage());
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
     * 지오코딩 결과를 담는 내부 클래스
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class GeocodingResult {
        private String originalAddress;      // 입력 주소
        private Double latitude;             // 위도
        private Double longitude;            // 경도
        private String apiResponseAddress;   // API 응답 주소
        private boolean success;             // 성공 여부
        private String errorMessage;         // 실패 사유

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
}