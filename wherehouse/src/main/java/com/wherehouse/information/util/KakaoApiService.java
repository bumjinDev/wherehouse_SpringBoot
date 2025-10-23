package com.wherehouse.information.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.information.model.AddressDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오맵 API 연동 서비스
 *
 * 주요 기능:
 * 1. 좌표 → 주소 변환 (Reverse Geocoding)
 * 2. 편의시설 검색 (15개 카테고리)
 *
 * API 키 관리:
 * - application.yml에서 환경변수로 주입
 * - 클라이언트에 노출되지 않도록 서버에서만 사용
 *
 * 타임아웃:
 * - 연결 타임아웃: 3초
 * - 읽기 타임아웃: 5초
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoApiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

//    @Value("${kakao.api.key}")
    @Value("${KAKAO_API_KEY}")
    private String kakaoApiKey;

    private static final String KAKAO_API_BASE_URL = "https://dapi.kakao.com";

    /**
     * 좌표를 주소로 변환 (Reverse Geocoding)
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 도로명 주소와 지번 주소를 포함한 AddressDto
     *
     * API 엔드포인트:
     * GET https://dapi.kakao.com/v2/local/geo/coord2address.json?x={경도}&y={위도}
     *
     * 응답 예시:
     * {
     *   "documents": [{
     *     "road_address": {"address_name": "서울특별시 중구 세종대로 110"},
     *     "address": {"address_name": "서울특별시 중구 태평로1가 31"}
     *   }]
     * }
     */
    public AddressDto getAddress(double latitude, double longitude) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode documents = root.path("documents");

            if (documents.isEmpty()) {

                log.warn("주소 변환 실패: 좌표({}, {})에 해당하는 주소 없음", latitude, longitude);

                return AddressDto.builder()
                        .roadAddress("주소 없음")
                        .jibunAddress("주소 없음")
                        .build();
            }

            JsonNode firstDoc = documents.get(0);
            String roadAddress = firstDoc.path("road_address").path("address_name").asText("주소 없음");
            String jibunAddress = firstDoc.path("address").path("address_name").asText("주소 없음");

            return AddressDto.builder()
                    .roadAddress(roadAddress)
                    .jibunAddress(jibunAddress)
                    .build();

        } catch (Exception e) {
            log.error("카카오맵 주소 변환 API 호출 실패", e);
            return AddressDto.builder()
                    .roadAddress("조회 실패")
                    .jibunAddress("조회 실패")
                    .build();
        }
    }

    /**
     * 특정 카테고리의 편의시설 검색
     *
     * @param latitude 검색 중심 위도
     * @param longitude 검색 중심 경도
     * @param categoryCode 카테고리 코드 (예: "CS2" - 편의점 등)
     * @param radius 검색 반경 (미터, 최대 20000)
     * @return 해당 카테고리의 장소 목록
     *
     * API 엔드포인트:
     * GET https://dapi.kakao.com/v2/local/search/category.json
     *     ?category_group_code={카테고리}&x={경도}&y={위도}&radius={반경}
     *
     * 응답 예시:
     * {
     *   "documents": [{
     *     "place_name": "GS25 서소문점",
     *     "x": "126.9775",
     *     "y": "37.5658",
     *     "distance": "120",
     *     "category_group_name": "편의점"
     *   }]
     * }
     */
    public List<Map<String, Object>> searchPlacesByCategory(
            double latitude, double longitude, String categoryCode, int radius) {

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("dapi.kakao.com")
                            .path("/v2/local/search/category.json")
                            .queryParam("category_group_code", categoryCode)
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .queryParam("radius", radius)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode documents = root.path("documents");

            List<Map<String, Object>> places = new ArrayList<>();
            for (JsonNode doc : documents) {
                Map<String, Object> place = new HashMap<>();
                place.put("name", doc.path("place_name").asText());
                place.put("latitude", doc.path("y").asDouble());
                place.put("longitude", doc.path("x").asDouble());
                place.put("distance", doc.path("distance").asInt());
                place.put("categoryName", doc.path("category_group_name").asText());
                places.add(place);
            }

            return places;

        } catch (Exception e) {
            log.error("카카오맵 장소 검색 API 호출 실패 (카테고리: {})", categoryCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 15개 카테고리 편의시설 병렬 검색
     *
     * @param latitude 검색 중심 위도
     * @param longitude 검색 중심 경도
     * @param radius 검색 반경 (미터)
     * @return 카테고리별 장소 목록 맵 (key: 카테고리 코드, value: 장소 목록)
     *
     * 카테고리 목록 (amenity.js 기준):
     * - SW8: 지하철역
     * - CS2: 편의점
     * - FD6: 음식점
     * - CE7: 카페
     * - MT1: 대형마트
     * - BK9: 은행
     * - PO3: 공공기관
     * - CT1: 문화시설
     * - HP8: 병원
     * - PM9: 약국
     * - PK6: 주차장
     * - OL7: 주유소
     * - SC4: 학교
     * - AC5: 학원
     * - AT4: 관광명소
     */
    public Map<String, List<Map<String, Object>>> searchAllAmenities(
            double latitude, double longitude, int radius) {

        String[] categories = {"SW8", "CS2", "FD6", "CE7", "MT1", "BK9", "PO3",
                "CT1", "HP8", "PM9", "PK6", "OL7", "SC4", "AC5", "AT4"};

        Map<String, List<Map<String, Object>>> results = new HashMap<>();

        for (String category : categories) {
            List<Map<String, Object>> places = searchPlacesByCategory(latitude, longitude, category, radius);
            results.put(category, places);
        }

        return results;
    }
}