package com.WhereHouse.AnalysisData.subway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 개선된 지하철역 좌표 계산 서비스
 *
 * "데이터없음" 처리 및 실제 데이터 기반 전처리 로직 추가
 * 지번주소 특화 전처리 및 다중 시도 로직 적용
 * Spring Boot의 UriComponentsBuilder가 한글 인코딩을 자동 처리한다.
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Service
@Slf4j
public class SubwayCoordinateService {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    // API 호출 통계
    private int totalApiCalls = 0;
    private int successfulApiCalls = 0;
    private int failedApiCalls = 0;

    public SubwayCoordinateService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 도로명주소 기반 좌표 계산 (데이터없음 처리 추가)
     */
    public Double[] calculateCoordinatesFromRoadAddress(String roadAddress) {
        // "데이터없음" 체크 추가
        if (roadAddress == null || roadAddress.trim().isEmpty() || "데이터없음".equals(roadAddress.trim())) {
            log.warn("도로명주소가 비어있거나 데이터없음입니다: {}", roadAddress);
            return null;
        }

        log.debug("도로명주소 좌표 계산 시작: {}", roadAddress);

        // 도로명주소 전처리 시도
        String[] roadAddressVariations = generateRoadAddressVariations(roadAddress);

        for (String variation : roadAddressVariations) {
            Double[] coordinates = callKakaoGeocodingApi(variation);
            if (coordinates != null) {
                log.debug("도로명주소 좌표 계산 성공 - 변형: {}, 좌표: ({}, {})",
                        variation, coordinates[0], coordinates[1]);
                return coordinates;
            }
        }

        log.debug("도로명주소 좌표 계산 실패: {}", roadAddress);
        return null;
    }

    /**
     * 지번주소 기반 좌표 계산 (데이터없음 처리 추가)
     */
    public Double[] calculateCoordinatesFromAddress(String address) {
        // "데이터없음" 체크 추가
        if (address == null || address.trim().isEmpty() || "데이터없음".equals(address.trim())) {
            log.warn("지번주소가 비어있거나 데이터없음입니다: {}", address);
            return null;
        }

        log.debug("지번주소 좌표 계산 시작: {}", address);

        // 지번주소 전처리 시도
        String[] addressVariations = generateJibunAddressVariations(address);

        for (String variation : addressVariations) {
            Double[] coordinates = callKakaoGeocodingApi(variation);
            if (coordinates != null) {
                log.debug("지번주소 좌표 계산 성공 - 변형: {}, 좌표: ({}, {})",
                        variation, coordinates[0], coordinates[1]);
                return coordinates;
            }
        }

        log.debug("지번주소 좌표 계산 실패: {}", address);
        return null;
    }

    /**
     * 도로명주소 변형 생성
     */
    private String[] generateRoadAddressVariations(String roadAddress) {
        String original = roadAddress.trim();

        return new String[] {
                original,                                           // 원본
                cleanRoadAddress(original),                         // 기본 정제
                removeStationInfo(cleanRoadAddress(original)),      // 역 정보 제거
                removeBuildingName(cleanRoadAddress(original)),     // 건물명 제거
                extractCoreAddress(cleanRoadAddress(original)),     // 핵심 주소만
                removeFloorInfo(cleanRoadAddress(original))         // 층 정보 제거
        };
    }

    /**
     * 지번주소 변형 생성 (실제 데이터 기준 강화)
     */
    private String[] generateJibunAddressVariations(String address) {
        String original = address.trim();

        return new String[] {
                original,                                           // 원본
                cleanJibunAddress(original),                        // 기본 정제
                removeStationInfo(cleanJibunAddress(original)),     // 역 정보 제거
                removeBuildingName(cleanJibunAddress(original)),    // 건물명 제거
                extractCoreJibunAddress(cleanJibunAddress(original)), // 핵심 지번주소만
                removeDetailInfo(cleanJibunAddress(original)),      // 상세정보 제거
                extractDistrictAndDong(cleanJibunAddress(original)), // 구+동까지만
                extractDistrictOnly(cleanJibunAddress(original))    // 구까지만
        };
    }

    /**
     * 도로명주소 전용 정제
     */
    private String cleanRoadAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 특별/광역시 표기 통일
        cleaned = cleaned.replaceAll("서울특별시", "서울시").trim();

        // 2. 괄호 안의 내용 제거
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "").trim();

        // 3. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 4. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 지번주소 전용 정제 (실제 데이터 기준 강화)
     */
    private String cleanJibunAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 특별/광역시 표기 통일
        cleaned = cleaned.replaceAll("서울특별시", "서울시").trim();

        // 2. 역명이 뒤에 붙은 경우 제거
        cleaned = removeTrailingStationName(cleaned);

        // 3. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 4. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 뒤에 붙은 역명 제거
     */
    private String removeTrailingStationName(String address) {
        if (address == null) return address;

        // 실제 데이터 패턴: "주소 + 역명" 형태에서 역명 제거
        String[] parts = address.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            // 역명으로 보이는 부분은 제외
            if (part.contains("역") && (part.contains("(") || part.contains("호선"))) {
                continue;
            }

            if (result.length() > 0) result.append(" ");
            result.append(part);
        }

        return result.toString().trim();
    }

    /**
     * 역/지하철 관련 정보 제거
     */
    private String removeStationInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 지하철역 관련 키워드 제거
        cleaned = cleaned.replaceAll("(지하철|역사|출구|입구|\\d+번출구|\\d+호선)", "").trim();

        // 역명 제거 (괄호 안의 역 정보 포함)
        cleaned = cleaned.replaceAll("\\([^)]*역[^)]*\\)", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 건물명 제거 (실제 데이터 기준)
     */
    private String removeBuildingName(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 실제 데이터에서 나타나는 건물명 패턴 제거
        cleaned = cleaned.replaceAll("(지하|지상|상가|센터|빌딩|타워)", "").trim();

        // 일반적인 건물명 패턴
        cleaned = cleaned.replaceAll("([가-힣]+빌딩|[가-힣]+타워|[가-힣]+센터|[가-힣]+상가)", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 핵심 주소만 추출 (번지수까지)
     */
    private String extractCoreAddress(String address) {
        if (address == null) return address;

        // 시/도 구/군 동 번지 형태만 남기기
        String[] parts = address.split("\\s+");
        StringBuilder core = new StringBuilder();

        int count = 0;
        for (String part : parts) {
            if (count >= 4) break; // 시도, 구군, 동, 번지 정도까지만

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
     * 핵심 지번주소만 추출 (도로명주소 구분 처리)
     */
    private String extractCoreJibunAddress(String address) {
        if (address == null) return address;

        // 도로명주소인지 확인 (로, 길 포함)
        if (address.contains("로") || address.contains("길")) {
            // 도로명주소는 번지까지만 남기고 나머지 제거
            String cleaned = address.replaceAll("(층|호|건물|상가|센터).*", "").trim();
            return cleaned.length() < 5 ? address : cleaned;
        } else {
            // 지번주소만 기존 로직 적용
            String cleaned = address.replaceAll("(번지|번|호).*", "").trim();
            return cleaned.length() < 5 ? address : cleaned;
        }
    }

    /**
     * 상세 정보 제거 (층, 호수 등)
     */
    private String removeFloorInfo(String address) {
        if (address == null) return address;

        return address.replaceAll("(\\d+층|\\d+호|지하\\d+|B\\d+|\\d+F).*", "").trim();
    }

    /**
     * 세부 정보 제거
     */
    private String removeDetailInfo(String address) {
        if (address == null) return address;

        return address.replaceAll("(내부|앞|뒤|옆|건너편|맞은편).*", "").trim();
    }

    /**
     * 구+동까지만 추출
     */
    private String extractDistrictAndDong(String address) {
        if (address == null) return address;

        // 괄호 안의 동명 우선 사용
        String dongInBrackets = extractDongFromBrackets(address);
        if (dongInBrackets != null) {
            String district = extractDistrictOnly(address);
            return district + " " + dongInBrackets;
        }

        // 일반적인 방식으로 구+동 추출
        String[] parts = address.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(part);

            // 동으로 끝나면 중단
            if (part.endsWith("동")) {
                break;
            }
        }

        return result.toString().trim();
    }

    /**
     * 괄호 안의 동명 추출
     */
    private String extractDongFromBrackets(String address) {
        if (address == null) return null;

        // (남대문로5가), (을지로6가) 같은 패턴에서 동명 추출
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(([^)]*동|[^)]*가)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(address);

        if (matcher.find()) {
            return matcher.group(1); // 괄호 안의 내용만 반환
        }

        return null;
    }

    /**
     * 구/군 단위까지만 추출
     */
    private String extractDistrictOnly(String address) {
        if (address == null) return address;

        String[] parts = address.split("\\s+");
        StringBuilder district = new StringBuilder();

        for (String part : parts) {
            if (district.length() > 0) district.append(" ");
            district.append(part);

            // 구/군으로 끝나면 중단
            if (part.endsWith("구") || part.endsWith("군") || part.endsWith("시")) {
                break;
            }
        }

        return district.toString().trim();
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
                log.error("Kakao API 호출 실패 - 상태코드: {}, 주소: {}", response.getStatusCode(), address);
                failedApiCalls++;
                return null;
            }

        } catch (Exception e) {
            log.debug("Kakao API 호출 중 오류 발생 - 주소: {}, 오류: {}", address, e.getMessage());
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
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] extractCoordinatesFromNode(JsonNode node) {
        try {
            String xStr = node.path("x").asText(); // 경도
            String yStr = node.path("y").asText(); // 위도

            if (!xStr.isEmpty() && !yStr.isEmpty()) {
                double longitude = Double.parseDouble(xStr);
                double latitude = Double.parseDouble(yStr);

                // 한국 좌표 범위 검증
                if (isValidKoreanCoordinate(latitude, longitude)) {
                    log.debug("좌표 계산 성공 - 좌표: ({}, {})", latitude, longitude);
                    return new Double[]{latitude, longitude};
                } else {
                    log.warn("유효하지 않은 한국 좌표 - 좌표: ({}, {})", latitude, longitude);
                    return null;
                }
            }

            log.warn("좌표 정보가 비어있습니다");
            return null;

        } catch (NumberFormatException e) {
            log.error("좌표 값 파싱 실패 - 오류: {}", e.getMessage());
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
        log.info("=== 개선된 Kakao API 호출 통계 (지하철역) ===");
        log.info("총 호출 횟수: {}", totalApiCalls);
        log.info("성공 횟수: {} ({:.1f}%)", successfulApiCalls,
                totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls * 100 : 0);
        log.info("실패 횟수: {} ({:.1f}%)", failedApiCalls,
                totalApiCalls > 0 ? (double) failedApiCalls / totalApiCalls * 100 : 0);
    }
}