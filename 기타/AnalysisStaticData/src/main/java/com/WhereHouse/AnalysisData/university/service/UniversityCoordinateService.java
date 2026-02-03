package com.WhereHouse.AnalysisData.university.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 대학교 주소 기반 좌표 계산 서비스
 *
 * Kakao Local API를 활용하여 주소를 정확한 위도, 경도 좌표로 변환한다.
 * Spring Boot의 UriComponentsBuilder가 한글 인코딩을 자동 처리한다.
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Service
@Slf4j
public class UniversityCoordinateService {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    // API 호출 통계
    private int totalApiCalls = 0;
    private int successfulApiCalls = 0;
    private int failedApiCalls = 0;

    public UniversityCoordinateService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 도로명주소 기반 좌표 계산
     *
     * @param roadAddress 도로명 주소
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    public Double[] calculateCoordinatesFromRoadAddress(String roadAddress) {
        if (roadAddress == null || roadAddress.trim().isEmpty()) {
            log.warn("도로명주소가 비어있습니다.");
            return null;
        }

        // 주소 전처리
        String cleanedAddress = cleanAddress(roadAddress);
        log.debug("주소 정제 - 원본: {}, 정제 후: {}", roadAddress, cleanedAddress);

        return callKakaoGeocodingApi(cleanedAddress);
    }

    /**
     * Kakao API가 인식할 수 있도록 주소 정제
     *
     * @param address 원본 주소
     * @return 정제된 주소
     */
    private String cleanAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 괄호 안의 내용 제거 (덕명동, 한밭대학교) -> 빈 문자열
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "").trim();

        // 2. 대학교명, 학교명 등 불필요한 정보 제거
        cleaned = cleaned.replaceAll("(대학교|대학|학교|캠퍼스)$", "").trim();

        // 3. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 4. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        // 5. 만약 정제 후 주소가 너무 짧으면 원본 사용
        if (cleaned.length() < 5) {
            log.debug("정제된 주소가 너무 짧아 원본 사용 - 원본: {}, 정제: {}", address, cleaned);
            return address;
        }

        return cleaned;
    }

    /**
     * Kakao Local API를 호출하여 주소를 좌표로 변환
     * UriComponentsBuilder가 자동으로 한글 인코딩 처리
     *
     * @param address 변환할 주소
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] callKakaoGeocodingApi(String address) {
        totalApiCalls++;

        try {
            log.debug("Kakao API 호출 - 주소: {}", address);

            // API 키 검증
            if (kakaoApiKey == null || kakaoApiKey.trim().isEmpty()) {
                log.error("Kakao API 키가 설정되지 않았습니다. application.yml에 kakao.api.key를 설정해주세요.");
                failedApiCalls++;
                return null;
            }

            // API URL 구성 - UriComponentsBuilder가 자동으로 한글 인코딩 처리
            String apiUrl = UriComponentsBuilder.fromHttpUrl(KAKAO_LOCAL_API_URL)
                    .queryParam("query", address)
                    .build()
                    .toUriString();

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                Double[] coordinates = parseKakaoApiResponse(response.getBody(), address);
                if (coordinates != null) {
                    successfulApiCalls++;
                } else {
                    failedApiCalls++;
                }
                return coordinates;
            } else {
                log.error("Kakao API 호출 실패 - 상태코드: {}, 주소: {}", response.getStatusCode(), address);
                failedApiCalls++;
                return null;
            }

        } catch (Exception e) {
            log.error("Kakao API 호출 중 오류 발생 - 주소: {}, 오류: {}", address, e.getMessage());
            failedApiCalls++;
            return null;
        }
    }

    /**
     * Kakao API 응답을 파싱하여 좌표 추출
     *
     * @param responseBody API 응답 JSON
     * @param originalAddress 원본 주소 (로깅용)
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] parseKakaoApiResponse(String responseBody, String originalAddress) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode documentsNode = rootNode.path("documents");

            if (documentsNode.isArray() && documentsNode.size() > 0) {
                // 첫 번째 결과 사용
                JsonNode firstResult = documentsNode.get(0);

                // 도로명주소 결과 우선 선택
                JsonNode roadAddressNode = firstResult.path("road_address");
                if (!roadAddressNode.isMissingNode() && !roadAddressNode.isNull()) {
                    return extractCoordinatesFromNode(roadAddressNode, originalAddress, "도로명주소");
                }

                // 도로명주소가 없으면 지번주소 사용
                JsonNode addressNode = firstResult.path("address");
                if (!addressNode.isMissingNode() && !addressNode.isNull()) {
                    return extractCoordinatesFromNode(addressNode, originalAddress, "지번주소");
                }
            }

            log.warn("Kakao API 응답에서 주소를 찾을 수 없습니다 - 주소: {}", originalAddress);
            return null;

        } catch (Exception e) {
            log.error("Kakao API 응답 파싱 실패 - 주소: {}, 오류: {}", originalAddress, e.getMessage());
            return null;
        }
    }

    /**
     * JSON 노드에서 좌표 정보 추출
     *
     * @param node 좌표 정보가 포함된 JSON 노드
     * @param originalAddress 원본 주소 (로깅용)
     * @param addressType 주소 타입 (로깅용)
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] extractCoordinatesFromNode(JsonNode node, String originalAddress, String addressType) {
        try {
            String xStr = node.path("x").asText(); // 경도
            String yStr = node.path("y").asText(); // 위도

            if (!xStr.isEmpty() && !yStr.isEmpty()) {
                double longitude = Double.parseDouble(xStr);
                double latitude = Double.parseDouble(yStr);

                // 한국 좌표 범위 검증
                if (isValidKoreanCoordinate(latitude, longitude)) {
                    log.debug("좌표 계산 성공 - 주소: {}, 타입: {}, 좌표: ({}, {})",
                            originalAddress, addressType, latitude, longitude);
                    return new Double[]{latitude, longitude};
                } else {
                    log.warn("유효하지 않은 한국 좌표 - 주소: {}, 좌표: ({}, {})",
                            originalAddress, latitude, longitude);
                    return null;
                }
            }

            log.warn("좌표 정보가 비어있습니다 - 주소: {}", originalAddress);
            return null;

        } catch (NumberFormatException e) {
            log.error("좌표 값 파싱 실패 - 주소: {}, 오류: {}", originalAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 한국 영역 내 좌표인지 검증
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 유효한 한국 좌표 여부
     */
    private boolean isValidKoreanCoordinate(double latitude, double longitude) {
        // 한국 좌표 범위 (대략적)
        // 위도: 33.0 ~ 38.7 (제주도 남단 ~ 함경북도)
        // 경도: 124.0 ~ 132.0 (서해 ~ 동해)
        return latitude >= 33.0 && latitude <= 38.7 &&
                longitude >= 124.0 && longitude <= 132.0;
    }

    /**
     * API 호출 통계 출력
     */
    public void printApiStatistics() {
        log.info("=== Kakao API 호출 통계 ===");
        log.info("총 호출 횟수: {}", totalApiCalls);
        log.info("성공 횟수: {} ({:.1f}%)", successfulApiCalls,
                totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls * 100 : 0);
        log.info("실패 횟수: {} ({:.1f}%)", failedApiCalls,
                totalApiCalls > 0 ? (double) failedApiCalls / totalApiCalls * 100 : 0);
    }
}