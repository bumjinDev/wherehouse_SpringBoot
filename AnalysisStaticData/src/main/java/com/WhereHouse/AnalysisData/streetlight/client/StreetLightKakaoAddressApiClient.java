package com.WhereHouse.AnalysisData.streetlight.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Kakao Map API 역지오코딩 클라이언트
 *
 * 좌표(위도, 경도)를 입력받아 주소 정보를 반환하는 Kakao Map API 호출 클라이언트
 *
 * 주요 기능:
 * - 좌표 → 주소 변환 (역지오코딩)
 * - 도로명 주소 및 지번 주소 반환
 * - 행정구역 정보 (구, 동) 반환
 *
 * API 제한사항:
 * - 무료 플랜: 300,000회/일
 * - 호출 간격 제한: 없음 (하지만 100ms 대기 권장)
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreetLightKakaoAddressApiClient {

    @Value("${app.kakao.api.key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;

    // Kakao Map API 역지오코딩 엔드포인트
    private static final String KAKAO_COORD2ADDRESS_URL = "https://dapi.kakao.com/v2/local/geo/coord2address.json";

    /**
     * 좌표를 주소로 변환하는 역지오코딩 API 호출
     *
     * @param longitude 경도 (x 좌표)
     * @param latitude 위도 (y 좌표)
     * @return 주소 정보가 포함된 응답 객체
     * @throws Exception API 호출 실패 시
     */
    public CoordinateToAddressResponse coordinateToAddress(String longitude, String latitude) throws Exception {

        // API 요청 URL 생성
        String requestUrl = UriComponentsBuilder.fromHttpUrl(KAKAO_COORD2ADDRESS_URL)
                .queryParam("x", longitude)  // 경도
                .queryParam("y", latitude)   // 위도
                .queryParam("input_coord", "WGS84")  // 입력 좌표계 (GPS 좌표계)
                .build()
                .toUriString();

        // HTTP 헤더 설정 (인증키 포함)
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        headers.set("Content-Type", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            log.debug("Kakao API 호출: {}", requestUrl);

            // API 호출
            ResponseEntity<CoordinateToAddressResponse> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    entity,
                    CoordinateToAddressResponse.class
            );

            CoordinateToAddressResponse responseBody = response.getBody();

            if (responseBody != null && responseBody.getDocuments() != null && !responseBody.getDocuments().isEmpty()) {
                log.debug("Kakao API 응답 성공: 좌표({}, {}) → 주소 {} 개 반환",
                        longitude, latitude, responseBody.getDocuments().size());
                return responseBody;
            } else {
                log.warn("Kakao API 응답에 주소 정보가 없습니다: 좌표({}, {})", longitude, latitude);
                return new CoordinateToAddressResponse(); // 빈 응답 반환
            }

        } catch (Exception e) {
            log.error("Kakao API 호출 실패: 좌표({}, {}), 오류: {}", longitude, latitude, e.getMessage());
            throw new Exception("Kakao Map API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Kakao Map API 좌표→주소 변환 응답 DTO
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoordinateToAddressResponse {

        @JsonProperty("documents")
        private List<Document> documents;

        @JsonProperty("meta")
        private Meta meta;

        /**
         * 주소 문서 정보
         */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Document {

            @JsonProperty("road_address")
            private RoadAddress roadAddress;

            @JsonProperty("address")
            private Address address;
        }

        /**
         * 도로명 주소 정보
         */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RoadAddress {

            @JsonProperty("address_name")
            private String addressName;

            @JsonProperty("region_1depth_name")
            private String region1DepthName;

            @JsonProperty("region_2depth_name")
            private String region2DepthName;

            @JsonProperty("region_3depth_name")
            private String region3DepthName;

            @JsonProperty("road_name")
            private String roadName;

            @JsonProperty("underground_yn")
            private String undergroundYn;

            @JsonProperty("main_building_no")
            private String mainBuildingNo;

            @JsonProperty("sub_building_no")
            private String subBuildingNo;

            @JsonProperty("building_name")
            private String buildingName;

            @JsonProperty("zone_no")
            private String zoneNo;
        }

        /**
         * 지번 주소 정보
         */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Address {

            @JsonProperty("address_name")
            private String addressName;

            @JsonProperty("region_1depth_name")
            private String region1DepthName;

            @JsonProperty("region_2depth_name")
            private String region2DepthName;

            @JsonProperty("region_3depth_name")
            private String region3DepthName;

            @JsonProperty("mountain_yn")
            private String mountainYn;

            @JsonProperty("main_address_no")
            private String mainAddressNo;

            @JsonProperty("sub_address_no")
            private String subAddressNo;

            @JsonProperty("zip_code")
            private String zipCode;
        }

        /**
         * 메타 정보
         */
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Meta {

            @JsonProperty("total_count")
            private Integer totalCount;
        }
    }
}