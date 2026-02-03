package com.WhereHouse.AnalysisData.residentcenter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 개선된 서울시 주민센터 좌표 계산 서비스
 *
 * 지번주소/도로명주소 특화 전처리 및 다중 시도 로직 추가
 * Kakao Local API를 활용하여 주소를 정확한 위도, 경도 좌표로 변환한다.
 * Spring Boot의 UriComponentsBuilder가 한글 인코딩을 자동 처리한다.
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Service
@Slf4j
public class ResidentCenterCoordinateService {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    // API 호출 통계
    private int totalApiCalls = 0;
    private int successfulApiCalls = 0;
    private int failedApiCalls = 0;

    public ResidentCenterCoordinateService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 주소 기반 좌표 계산 (개선된 버전)
     *
     * @param address 주민센터 주소
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    public Double[] calculateCoordinatesFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            log.warn("주소가 비어있습니다.");
            return null;
        }

        log.debug("주민센터 주소 좌표 계산 시작: {}", address);

        // 주소 변형 생성 및 순차 시도
        String[] addressVariations = generateAddressVariations(address);

        for (String variation : addressVariations) {
            Double[] coordinates = callKakaoGeocodingApi(variation);
            if (coordinates != null) {
                log.debug("주소 좌표 계산 성공 - 변형: {}, 좌표: ({}, {})",
                        variation, coordinates[0], coordinates[1]);
                return coordinates;
            }
        }

        log.debug("주소 좌표 계산 실패: {}", address);
        return null;
    }

    /**
     * 주민센터 주소 변형 생성 (서울시 행정구역 특화)
     *
     * @param address 원본 주소
     * @return 주소 변형 배열
     */
    private String[] generateAddressVariations(String address) {
        String original = address.trim();

        return new String[] {
                original,                                           // 원본 주소
                cleanGovernmentAddress(original),                   // 기관명 정제
                removeGovernmentInfo(cleanGovernmentAddress(original)), // 기관 관련 정보 제거
                extractCoreAddress(cleanGovernmentAddress(original)),   // 핵심 주소만
                removeDetailInfo(cleanGovernmentAddress(original)),     // 상세정보 제거
                extractDistrictOnly(cleanGovernmentAddress(original)),  // 구 단위까지만
                extractDongOnly(cleanGovernmentAddress(original)),      // 동 단위까지만
                addSeoulPrefix(cleanGovernmentAddress(original))        // 서울시 prefix 추가
        };
    }

    /**
     * 공공기관 주소 전용 정제
     *
     * @param address 원본 주소
     * @return 정제된 주소
     */
    private String cleanGovernmentAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 괄호 안의 내용 제거
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "").trim();

        // 2. 서울시/서울특별시 표기 통일
        cleaned = cleaned.replaceAll("서울특별시", "서울시").trim();

        // 3. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 4. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 기관 관련 정보 제거 (주민센터 특화)
     *
     * @param address 주소
     * @return 기관명이 제거된 주소
     */
    private String removeGovernmentInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 주민센터 관련 키워드 제거
        cleaned = cleaned.replaceAll("(주민센터|동사무소|구민회관|시민회관|동주민센터|구청|동사무소)", "").trim();

        // 공공기관 관련 키워드 제거
        cleaned = cleaned.replaceAll("(행정복지센터|복지센터|민원실|청사)", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 핵심 주소만 추출 (서울시 행정구역 기준)
     *
     * @param address 주소
     * @return 핵심 주소
     */
    private String extractCoreAddress(String address) {
        if (address == null) return address;

        // 서울시 구 동 번지 형태만 남기기
        String[] parts = address.split("\\s+");
        StringBuilder core = new StringBuilder();

        int count = 0;
        for (String part : parts) {
            if (count >= 4) break; // 서울시, 구, 동, 번지 정도까지만

            // 숫자가 포함된 부분은 번지일 가능성이 높음
            if (part.matches(".*\\d+.*") || count < 3) {
                if (core.length() > 0) core.append(" ");
                core.append(part);
                count++;
            }
        }

        return core.toString().trim();
    }

    /**
     * 상세 정보 제거 (위치 관련 상대적 표현)
     *
     * @param address 주소
     * @return 상세정보가 제거된 주소
     */
    private String removeDetailInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 상대적 위치 표현 제거
        cleaned = cleaned.replaceAll("(내부|앞|뒤|옆|건너편|맞은편|근처|일대)", "").trim();

        // 층, 호수 정보 제거
        cleaned = cleaned.replaceAll("(\\d+층|\\d+호|지하\\d+|B\\d+|\\d+F)", "").trim();

        // 방향 정보 제거
        cleaned = cleaned.replaceAll("(동쪽|서쪽|남쪽|북쪽)", "").trim();

        return cleaned.replaceAll("\\s+", " ").trim();
    }

    /**
     * 구 단위까지만 추출
     *
     * @param address 주소
     * @return 구 단위 주소
     */
    private String extractDistrictOnly(String address) {
        if (address == null) return address;

        String[] parts = address.split("\\s+");
        StringBuilder district = new StringBuilder();

        for (String part : parts) {
            if (district.length() > 0) district.append(" ");
            district.append(part);

            // 구로 끝나면 중단 (서울시는 모두 구 단위)
            if (part.endsWith("구")) {
                break;
            }
        }

        return district.toString().trim();
    }

    /**
     * 동 단위까지만 추출
     *
     * @param address 주소
     * @return 동 단위 주소
     */
    private String extractDongOnly(String address) {
        if (address == null) return address;

        String[] parts = address.split("\\s+");
        StringBuilder dong = new StringBuilder();

        for (String part : parts) {
            if (dong.length() > 0) dong.append(" ");
            dong.append(part);

            // 동으로 끝나면 중단
            if (part.endsWith("동")) {
                break;
            }
        }

        return dong.toString().trim();
    }

    /**
     * 서울시 prefix 추가 (혹시 누락된 경우)
     *
     * @param address 주소
     * @return 서울시가 추가된 주소
     */
    private String addSeoulPrefix(String address) {
        if (address == null) return address;

        String trimmed = address.trim();

        // 이미 서울시가 포함되어 있으면 그대로 반환
        if (trimmed.startsWith("서울") || trimmed.contains("서울")) {
            return trimmed;
        }

        // 구로 시작하는 경우 서울시 추가
        if (trimmed.matches("^[가-힣]+구\\s+.*")) {
            return "서울시 " + trimmed;
        }

        return trimmed;
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
            if (kakaoApiKey == null || kakaoApiKey.trim().isEmpty()) {
                log.error("Kakao API 키가 설정되지 않았습니다.");
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
                    apiUrl, HttpMethod.GET, entity, String.class
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
                failedApiCalls++;
                return null;
            }

        } catch (Exception e) {
            log.debug("Kakao API 호출 실패 - 주소: {}, 오류: {}", address, e.getMessage());
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
                    return extractCoordinatesFromNode(roadAddressNode);
                }

                // 도로명주소가 없으면 지번주소 사용
                JsonNode addressNode = firstResult.path("address");
                if (!addressNode.isMissingNode() && !addressNode.isNull()) {
                    return extractCoordinatesFromNode(addressNode);
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("Kakao API 응답 파싱 실패 - 주소: {}", originalAddress);
            return null;
        }
    }

    /**
     * JSON 노드에서 좌표 정보 추출
     *
     * @param node 좌표 정보가 포함된 JSON 노드
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] extractCoordinatesFromNode(JsonNode node) {
        try {
            String xStr = node.path("x").asText(); // 경도
            String yStr = node.path("y").asText(); // 위도

            if (!xStr.isEmpty() && !yStr.isEmpty()) {
                double longitude = Double.parseDouble(xStr);
                double latitude = Double.parseDouble(yStr);

                // 서울시 좌표 범위 검증
                if (isValidSeoulCoordinate(latitude, longitude)) {
                    return new Double[]{latitude, longitude};
                }
            }

            return null;

        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 서울시 영역 내 좌표인지 검증 (더 정밀한 범위)
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 유효한 서울시 좌표 여부
     */
    private boolean isValidSeoulCoordinate(double latitude, double longitude) {
        // 서울시 좌표 범위 (더 정밀한 범위)
        // 위도: 37.413 ~ 37.715 (서울시 남북 경계)
        // 경도: 126.764 ~ 127.184 (서울시 동서 경계)
        return latitude >= 37.413 && latitude <= 37.715 &&
                longitude >= 126.764 && longitude <= 127.184;
    }

    /**
     * API 호출 통계 출력
     */
    public void printApiStatistics() {
        log.info("=== 개선된 Kakao API 호출 통계 (서울시 주민센터) ===");
        log.info("총 호출 횟수: {}", totalApiCalls);
        log.info("성공 횟수: {} ({:.1f}%)", successfulApiCalls,
                totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls * 100 : 0);
        log.info("실패 횟수: {} ({:.1f}%)", failedApiCalls,
                totalApiCalls > 0 ? (double) failedApiCalls / totalApiCalls * 100 : 0);
    }
}