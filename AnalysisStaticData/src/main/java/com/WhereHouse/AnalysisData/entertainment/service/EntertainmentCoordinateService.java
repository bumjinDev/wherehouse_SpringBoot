package com.WhereHouse.AnalysisData.entertainment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 개선된 유흥주점 좌표 계산 서비스 (강화된 주소 전처리)
 *
 * "데이터없음" 처리 및 실제 데이터 기반 전처리 로직 추가
 * 복잡한 층수 정보, 괄호 내용, 특수 문자 처리 강화
 * Spring Boot의 UriComponentsBuilder가 한글 인코딩을 자동 처리한다.
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Service
@Slf4j
public class EntertainmentCoordinateService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String KAKAO_LOCAL_API_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    // API 호출 통계
    private int totalApiCalls = 0;
    private int successfulApiCalls = 0;
    private int failedApiCalls = 0;

    public EntertainmentCoordinateService() {
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
     * 도로명주소 변형 생성 (강화된 전처리)
     */
    private String[] generateRoadAddressVariations(String roadAddress) {
        String original = roadAddress.trim();

        return new String[] {
                original,                                           // 원본
                cleanRoadAddress(original),                         // 기본 정제
                removeComplexFloorInfo(cleanRoadAddress(original)), // 복잡한 층수 정보 제거
                removeEntertainmentInfo(cleanRoadAddress(original)), // 유흥업소명 제거
                removeBuildingName(cleanRoadAddress(original)),     // 건물명 제거
                extractCoreRoadAddress(cleanRoadAddress(original)), // 핵심 도로명주소만
                removeAllSpecialInfo(cleanRoadAddress(original))    // 모든 부가 정보 제거
        };
    }

    /**
     * 지번주소 변형 생성 (강화된 전처리)
     */
    private String[] generateJibunAddressVariations(String address) {
        String original = address.trim();

        return new String[] {
                original,                                           // 원본
                cleanJibunAddress(original),                        // 기본 정제
                removeComplexFloorInfo(cleanJibunAddress(original)), // 복잡한 층수 정보 제거
                removeEntertainmentInfo(cleanJibunAddress(original)), // 유흥업소명 제거
                removeBuildingName(cleanJibunAddress(original)),    // 건물명 제거
                extractCoreJibunAddress(cleanJibunAddress(original)), // 핵심 지번주소만
                removeDetailInfo(cleanJibunAddress(original)),      // 상세정보 제거
                extractDistrictAndDong(cleanJibunAddress(original)), // 구+동까지만
                extractDistrictOnly(cleanJibunAddress(original))    // 구까지만
        };
    }

    /**
     * 도로명주소 전용 정제 (강화된 버전)
     */
    private String cleanRoadAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 특별/광역시 표기 통일
        cleaned = cleaned.replaceAll("서울특별시", "서울시").trim();

        // 2. 복잡한 층수 정보 제거 (쉼표 뒤의 층수 정보)
        cleaned = removeComplexFloorInfo(cleaned);

        // 3. 괄호 안의 내용 제거
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "").trim();

        // 4. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 5. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 지번주소 전용 정제 (강화된 버전)
     */
    private String cleanJibunAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }

        String cleaned = address.trim();

        // 1. 특별/광역시 표기 통일
        cleaned = cleaned.replaceAll("서울특별시", "서울시").trim();

        // 2. 복잡한 층수 정보 제거
        cleaned = removeComplexFloorInfo(cleaned);

        // 3. 유흥주점 관련 업체명 제거
        cleaned = removeEntertainmentBusinessInfo(cleaned);

        // 4. 연속된 공백을 하나로 통합
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        // 5. 끝에 남은 쉼표나 특수문자 제거
        cleaned = cleaned.replaceAll("[,\\-\\s]+$", "").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 복잡한 층수 정보 제거 (강화된 버전)
     */
    private String removeComplexFloorInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 1. 쉼표 뒤의 층수 정보 완전 제거
        cleaned = cleaned.replaceAll(",\\s*[0-9\\-,\\s]*층.*$", "").trim();
        cleaned = cleaned.replaceAll(",\\s*[0-9\\-,\\s]*지하.*$", "").trim();
        cleaned = cleaned.replaceAll(",\\s*B[0-9].*$", "").trim();
        cleaned = cleaned.replaceAll(",\\s*[0-9]+F.*$", "").trim();

        // 2. 단독으로 나타나는 층수 정보 제거
        cleaned = cleaned.replaceAll("\\s+[0-9]+\\-[0-9]+층$", "").trim();
        cleaned = cleaned.replaceAll("\\s+[0-9]+층$", "").trim();
        cleaned = cleaned.replaceAll("\\s+지하[0-9]+층?$", "").trim();
        cleaned = cleaned.replaceAll("\\s+B[0-9]+$", "").trim();
        cleaned = cleaned.replaceAll("\\s+[0-9]+F$", "").trim();

        // 3. 복잡한 번지수 뒤의 층수 정보 제거
        cleaned = cleaned.replaceAll("([0-9]+),\\s*[0-9,\\-\\s]*층.*$", "$1").trim();

        // 4. 남은 쉼표 정리
        cleaned = cleaned.replaceAll(",\\s*$", "").trim();

        return cleaned;
    }

    /**
     * 핵심 도로명주소만 추출 (강화된 버전)
     */
    private String extractCoreRoadAddress(String address) {
        if (address == null) return address;

        // 도로명주소 패턴: 시/도 구/군 도로명 번지
        String[] parts = address.split("\\s+");
        StringBuilder core = new StringBuilder();

        for (String part : parts) {
            // 도로명 키워드 확인 (로, 길)
            if (part.contains("로") || part.contains("길")) {
                if (core.length() > 0) core.append(" ");
                core.append(part);

                // 다음 숫자 부분(번지)까지 추가
                int currentIndex = java.util.Arrays.asList(parts).indexOf(part);
                if (currentIndex + 1 < parts.length) {
                    String nextPart = parts[currentIndex + 1];
                    if (nextPart.matches(".*[0-9]+.*")) {
                        // 순수한 번지수만 추출 (쉼표나 기타 정보 제거)
                        String pureBungi = nextPart.replaceAll("[^0-9\\-].*", "");
                        if (!pureBungi.isEmpty()) {
                            core.append(" ").append(pureBungi);
                        }
                    }
                }
                break;
            } else {
                // 시/도, 구/군 부분 추가
                if (core.length() > 0) core.append(" ");
                core.append(part);
            }
        }

        return core.toString().trim();
    }

    /**
     * 모든 부가 정보 제거 (최후의 수단)
     */
    private String removeAllSpecialInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 모든 괄호 정보 제거
        cleaned = cleaned.replaceAll("\\([^)]*\\)", "").trim();

        // 쉼표 이후 모든 정보 제거
        if (cleaned.contains(",")) {
            cleaned = cleaned.substring(0, cleaned.indexOf(",")).trim();
        }

        // 층수 관련 모든 정보 제거
        cleaned = cleaned.replaceAll("[0-9\\-,\\s]*층.*", "").trim();
        cleaned = cleaned.replaceAll("지하.*", "").trim();
        cleaned = cleaned.replaceAll("B[0-9].*", "").trim();
        cleaned = cleaned.replaceAll("[0-9]+F.*", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 유흥주점 관련 상호명/업체명 정보 제거
     */
    private String removeEntertainmentBusinessInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 유흥주점 관련 키워드 제거
        cleaned = cleaned.replaceAll("(룸|카페|바|pub|클럽|나이트|노래방|단란주점|유흥주점)", "").trim();

        // 일반적인 업소명 제거
        cleaned = cleaned.replaceAll("(엔터테인먼트|레저|라운지|살롱|스튜디오|하우스)", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
    }

    /**
     * 상호명/업체명 정보 제거
     */
    private String removeEntertainmentInfo(String address) {
        if (address == null) return address;

        String cleaned = address.trim();

        // 유흥 관련 키워드 제거
        cleaned = cleaned.replaceAll("(룸|카페|바|클럽|나이트|노래방|단란주점)", "").trim();

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
        cleaned = cleaned.replaceAll("(타워|빌딩|센터|플라자|상가)", "").trim();

        // 일반적인 건물명 패턴
        cleaned = cleaned.replaceAll("([가-힣]+빌딩|[가-힣]+타워|[가-힣]+센터)", "").trim();

        // 연속된 공백 정리
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned.length() < 5 ? address : cleaned;
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

        // (잠원동), (을지로6가), (동교동) 같은 패턴에서 동명 추출
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
        log.info("=== 개선된 Kakao API 호출 통계 (유흥주점) ===");
        log.info("총 호출 횟수: {}", totalApiCalls);
        log.info("성공 횟수: {} ({:.1f}%)", successfulApiCalls,
                totalApiCalls > 0 ? (double) successfulApiCalls / totalApiCalls * 100 : 0);
        log.info("실패 횟수: {} ({:.1f}%)", failedApiCalls,
                totalApiCalls > 0 ? (double) failedApiCalls / totalApiCalls * 100 : 0);
    }
}