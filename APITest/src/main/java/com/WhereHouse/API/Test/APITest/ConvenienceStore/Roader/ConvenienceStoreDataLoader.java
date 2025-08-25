package com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.Roader;

import com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.Entity.ConvenienceStoreEntity;
import com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.Repository.ConvenienceStoreRepository;
import com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.DTO.ConvenienceStoreApiResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;  // JSON 파싱용

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreDataLoader  { // implements CommandLineRunner

    private final ConvenienceStoreRepository convenienceStoreRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${molit.api.safemap.service-key}")
    private String serviceKey;

    @Value("${molit.api.safemap.base-url:http://safemap.go.kr/openApiService/data/getConvenienceStoreData.do}")
    private String baseUrl;

    //@Override
    @Transactional
    public void run(String... args) {

        if (convenienceStoreRepository.count() > 0) {
            log.info("편의점 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            log.info("편의점 데이터 수집 시작...");

            int totalSaved = 0;
            int pageNo = 1;
            int numOfRows = 1000; // 한번에 1000개씩 가져오기
            boolean hasMoreData = true;

            while (hasMoreData) {
                try {
                    List<ConvenienceStoreApiResponseDto> storeData = fetchConvenienceStoreData(pageNo, numOfRows);

                    if (storeData == null || storeData.isEmpty()) {
                        hasMoreData = false;
                        break;
                    }

                    int savedCount = saveConvenienceStoreData(storeData);
                    totalSaved += savedCount;

                    log.info("페이지 {} 처리 완료: {}개 저장", pageNo, savedCount);

                    if (storeData.size() < numOfRows) {
                        hasMoreData = false; // 마지막 페이지
                    }

                    pageNo++;

                    // API 호출 간격 조절 (서버 부하 방지)
                    Thread.sleep(500);

                } catch (Exception e) {
                    log.error("페이지 {} 처리 중 오류: {}", pageNo, e.getMessage());
                    break;
                }
            }

            log.info("편의점 데이터 로딩 완료: 총 {}개 저장", totalSaved);

        } catch (Exception e) {
            log.error("편의점 데이터 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private List<ConvenienceStoreApiResponseDto> fetchConvenienceStoreData(int pageNo, int numOfRows) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .queryParam("type", "json")
                    .build()
                    .toUriString();

            log.debug("API 호출 URL: {}", url);

            // 🔍 먼저 String으로 응답을 받아서 내용 확인
            String response = restTemplate.getForObject(url, String.class);

            log.info("🔍 실제 API 응답 내용:");
            log.info("응답 길이: {} characters", response != null ? response.length() : 0);
            log.info("응답 내용 (처음 500자): {}",
                    response != null ? response.substring(0, Math.min(500, response.length())) : "null");

            // HTML 응답인지 확인
            if (response != null && response.trim().toLowerCase().startsWith("<html")) {
                log.error("❌ HTML 응답이 왔습니다. API 키나 URL이 잘못되었을 수 있습니다.");
                return new ArrayList<>();
            }

            // XML 응답인지 확인
            if (response != null && response.trim().startsWith("<?xml")) {
                log.warn("⚠️  XML 응답이 왔습니다. type=json이 작동하지 않는 것 같습니다.");
                // XML 파싱 로직 필요
                return new ArrayList<>();
            }

            // JSON 응답인지 확인
            if (response != null && (response.trim().startsWith("{") || response.trim().startsWith("["))) {
                log.info("✅ JSON 응답 확인됨");
                // JSON 파싱 진행
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> jsonResponse = mapper.readValue(response, Map.class);
                    List<ConvenienceStoreApiResponseDto> stores = parseApiResponse(jsonResponse);
                    return stores;
                } catch (Exception e) {
                    log.error("JSON 파싱 실패: {}", e.getMessage());
                    return new ArrayList<>();
                }
            }

            log.error("❓ 알 수 없는 응답 형식입니다.");
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("편의점 데이터 API 호출 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ConvenienceStoreApiResponseDto> parseApiResponse(Map<String, Object> response) {
        List<ConvenienceStoreApiResponseDto> stores = new ArrayList<>();

        try {
            // 실제 API 응답 구조를 확인 후 수정 필요
            // 예상 구조: response -> items -> item[]
            Map<String, Object> body = (Map<String, Object>) response.get("response");
            if (body != null) {
                Map<String, Object> items = (Map<String, Object>) body.get("body");
                if (items != null) {
                    List<Map<String, Object>> itemList = (List<Map<String, Object>>) items.get("items");
                    if (itemList != null) {
                        for (Map<String, Object> item : itemList) {
                            ConvenienceStoreApiResponseDto store = mapToConvenienceStore(item);
                            if (store != null) {
                                stores.add(store);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("API 응답 파싱 실패: {}", e.getMessage());
        }

        return stores;
    }

    private ConvenienceStoreApiResponseDto mapToConvenienceStore(Map<String, Object> item) {
        try {
            ConvenienceStoreApiResponseDto store = new ConvenienceStoreApiResponseDto();

            store.setStoreName(getString(item, "storeName"));
            store.setAddress(getString(item, "address"));
            store.setRoadAddress(getString(item, "roadAddress"));
            store.setPhoneNumber(getString(item, "phoneNumber"));

            // UTMK 좌표를 WGS84로 변환 (실제 좌표 변환 라이브러리 필요)
            Double utmkX = getDouble(item, "xCoordinate");
            Double utmkY = getDouble(item, "yCoordinate");

            if (utmkX != null && utmkY != null) {
                // TODO: UTMK -> WGS84 좌표 변환 로직 구현
                store.setXCoordinate(utmkX); // 임시로 원본 사용
                store.setYCoordinate(utmkY); // 임시로 원본 사용
            }

            return store;

        } catch (Exception e) {
            log.warn("편의점 데이터 매핑 실패: {}", e.getMessage());
            return null;
        }
    }

    private int saveConvenienceStoreData(List<ConvenienceStoreApiResponseDto> storeDataList) {
        int savedCount = 0;

        for (ConvenienceStoreApiResponseDto storeData : storeDataList) {
            try {
                // 중복 체크
                if (convenienceStoreRepository.existsByStoreNameAndAddress(
                        storeData.getStoreName(), storeData.getAddress())) {
                    continue;
                }

                ConvenienceStoreEntity store = ConvenienceStoreEntity.builder()
                        .storeName(cleanString(storeData.getStoreName()))
                        .sigungu(cleanString(storeData.extractSigungu()))
                        .dong(cleanString(storeData.extractDong()))
                        .address(cleanString(storeData.getAddress()))
                        .roadAddress(cleanString(storeData.getRoadAddress()))
                        .phoneNumber(cleanString(storeData.getPhoneNumber()))
                        .xCoordinate(storeData.getXCoordinate())
                        .yCoordinate(storeData.getYCoordinate())
                        .build();

                convenienceStoreRepository.save(store);
                savedCount++;

            } catch (Exception e) {
                log.warn("편의점 데이터 저장 실패: {} - {}", storeData.getStoreName(), e.getMessage());
            }
        }

        return savedCount;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanString(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}