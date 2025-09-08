package com.WhereHouse.AnalysisData.hospital.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class KakaoCoordinateService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 병원 관련 키워드 목록
    private static final List<String> HOSPITAL_KEYWORDS = Arrays.asList(
            "병원", "의원", "한의원", "치과", "요양병원", "재활병원", "정신병원",
            "클리닉", "센터", "의료센터", "건강센터", "보건소", "한방",
            "치과의원", "정형외과", "내과", "외과", "산부인과", "소아과",
            "피부과", "안과", "이비인후과", "의료", "진료소", "요양원", "한방진료실"
    );

    // 특별시/광역시 표기 통일을 위한 패턴
    private static final Pattern SPECIAL_CITY_PATTERN = Pattern.compile("서울특별시");
    private static final Pattern METRO_CITY_PATTERN = Pattern.compile("(부산|대구|인천|광주|대전|울산)광역시");

    /**
     * 주소를 기반으로 좌표를 계산합니다.
     * @param roadAddress 도로명주소
     * @param lotAddress 지번주소
     * @param businessName 사업장명 (로깅용)
     * @return [위도, 경도] 배열, 실패시 null
     */
    public BigDecimal[] getCoordinates(String roadAddress, String lotAddress, String businessName) {
        // 도로명주소 우선 시도
        if (roadAddress != null && !roadAddress.trim().isEmpty()) {
            BigDecimal[] coordinates = tryGetCoordinatesWithVariations(roadAddress, businessName);
            if (coordinates != null) {
                return coordinates;
            }
        }

        // 지번주소로 재시도
        if (lotAddress != null && !lotAddress.trim().isEmpty()) {
            BigDecimal[] coordinates = tryGetCoordinatesWithVariations(lotAddress, businessName);
            if (coordinates != null) {
                return coordinates;
            }
        }

        log.error("좌표 계산 실패 - 사업장명: {}, 도로명주소: {}, 지번주소: {}",
                businessName, roadAddress, lotAddress);
        return null;
    }

    /**
     * 주소 변형을 통한 좌표 계산 시도
     */
    private BigDecimal[] tryGetCoordinatesWithVariations(String address, String businessName) {
        List<String> addressVariations = generateAddressVariations(address);

        for (String variation : addressVariations) {
            BigDecimal[] coordinates = callKakaoApi(variation);
            if (coordinates != null) {
                log.debug("좌표 계산 성공 - 사업장명: {}, 사용된 주소: {}, 위도: {}, 경도: {}",
                        businessName, variation, coordinates[0], coordinates[1]);
                return coordinates;
            }
        }

        return null;
    }

    /**
     * 주소 변형 패턴 생성
     */
    private List<String> generateAddressVariations(String originalAddress) {
        String cleaned = originalAddress.trim();

        // 1. 원본 주소
        String variation1 = cleaned;

        // 2. 병원 키워드 제거
        String variation2 = removeHospitalKeywords(cleaned);

        // 3. 건물 정보 제거 (층수, 호수)
        String variation3 = removeBuildingInfo(variation2);

        // 4. 괄호 내용 제거
        String variation4 = removeParentheses(variation3);

        // 5. 핵심 주소 추출 (시/도/구/동/번지)
        String variation5 = extractCoreAddress(variation4);

        return Arrays.asList(variation1, variation2, variation3, variation4, variation5)
                .stream()
                .distinct()
                .filter(addr -> addr.length() >= 5) // 최소 길이 보장
                .toList();
    }

    /**
     * 병원 관련 키워드 제거
     */
    private String removeHospitalKeywords(String address) {
        String result = address;
        for (String keyword : HOSPITAL_KEYWORDS) {
            result = result.replaceAll(keyword, "");
        }
        return normalizeAddress(result);
    }

    /**
     * 건물 정보 제거 (층수, 호수, 건물명)
     */
    private String removeBuildingInfo(String address) {
        return address
                .replaceAll("\\d+층", "")
                .replaceAll("\\d+호", "")
                .replaceAll("지하\\d*", "")
                .replaceAll("[가-힣]+빌딩", "")
                .replaceAll("[가-힣]+타워", "")
                .replaceAll("[가-힣]+상가", "")
                .trim();
    }

    /**
     * 괄호 내용 제거
     */
    private String removeParentheses(String address) {
        return address.replaceAll("\\([^)]*\\)", "").trim();
    }

    /**
     * 핵심 주소 추출
     */
    private String extractCoreAddress(String address) {
        // 기본적인 주소 구성요소만 남기기
        String result = address
                .replaceAll("[,\\-]+$", "") // 끝의 특수문자 제거
                .replaceAll("\\s+", " ") // 연속 공백 정리
                .trim();

        return normalizeAddress(result);
    }

    /**
     * 주소 표기 통일
     */
    private String normalizeAddress(String address) {
        String result = address;

        // 특별시/광역시 표기 통일
        result = SPECIAL_CITY_PATTERN.matcher(result).replaceAll("서울시");
        result = METRO_CITY_PATTERN.matcher(result).replaceAll("$1시");

        // 연속 공백 정리
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    /**
     * Kakao API 호출
     */
    private BigDecimal[] callKakaoApi(String address) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://dapi.kakao.com/v2/local/search/address.json")
                    .queryParam("query", address)
                    .encode()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseCoordinatesFromResponse(response.getBody());
            }
        } catch (Exception e) {
            log.warn("Kakao API 호출 중 오류 발생 - 주소: {}, 오류: {}", address, e.getMessage());
        }

        return null;
    }

    /**
     * API 응답에서 좌표 추출
     */
    private BigDecimal[] parseCoordinatesFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.get("documents");

            if (documents != null && documents.isArray() && documents.size() > 0) {
                JsonNode firstDoc = documents.get(0);

                // road_address 우선 확인
                JsonNode roadAddress = firstDoc.get("road_address");
                if (roadAddress != null && !roadAddress.isNull()) {
                    String lat = roadAddress.get("y").asText();
                    String lon = roadAddress.get("x").asText();
                    BigDecimal[] coordinates = {new BigDecimal(lat), new BigDecimal(lon)};

                    if (isValidKoreanCoordinate(coordinates)) {
                        return coordinates;
                    }
                }

                // address 확인
                JsonNode address = firstDoc.get("address");
                if (address != null && !address.isNull()) {
                    String lat = address.get("y").asText();
                    String lon = address.get("x").asText();
                    BigDecimal[] coordinates = {new BigDecimal(lat), new BigDecimal(lon)};

                    if (isValidKoreanCoordinate(coordinates)) {
                        return coordinates;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("좌표 파싱 중 오류 발생: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 한국 영역 내 좌표인지 검증
     */
    private boolean isValidKoreanCoordinate(BigDecimal[] coordinates) {
        if (coordinates == null || coordinates.length != 2) {
            return false;
        }

        BigDecimal lat = coordinates[0];
        BigDecimal lon = coordinates[1];

        // 한국 영역: 위도 33.0~38.7, 경도 124.0~132.0
        return lat.compareTo(new BigDecimal("33.0")) >= 0 &&
                lat.compareTo(new BigDecimal("38.7")) <= 0 &&
                lon.compareTo(new BigDecimal("124.0")) >= 0 &&
                lon.compareTo(new BigDecimal("132.0")) <= 0;
    }
}