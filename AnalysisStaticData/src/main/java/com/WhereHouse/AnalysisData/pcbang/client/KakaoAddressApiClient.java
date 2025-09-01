package com.WhereHouse.AnalysisData.pcbang.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoAddressApiClient {

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.local-api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 주소를 위도/경도 좌표로 변환
     */
    public AddressSearchResponse searchAddress(String address) {
        String url = baseUrl + "/search/address.json";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("query", address);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<AddressSearchResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    AddressSearchResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 주소 검색 API 호출 실패: address={}, error={}", address, e.getMessage());
            return new AddressSearchResponse(); // 빈 객체 반환
        }
    }

    // 응답 DTO 클래스들
    public static class AddressSearchResponse {
        private Meta meta;
        private java.util.List<Document> documents;

        public Meta getMeta() { return meta; }
        public void setMeta(Meta meta) { this.meta = meta; }
        public java.util.List<Document> getDocuments() { return documents; }
        public void setDocuments(java.util.List<Document> documents) { this.documents = documents; }
    }

    public static class Meta {
        private int total_count;
        private boolean is_end;

        public int getTotal_count() { return total_count; }
        public void setTotal_count(int total_count) { this.total_count = total_count; }
        public boolean isIs_end() { return is_end; }
        public void setIs_end(boolean is_end) { this.is_end = is_end; }
    }

    public static class Document {
        private String address_name;
        private String x; // 경도
        private String y; // 위도

        public String getAddress_name() { return address_name; }
        public void setAddress_name(String address_name) { this.address_name = address_name; }
        public String getX() { return x; }
        public void setX(String x) { this.x = x; }
        public String getY() { return y; }
        public void setY(String y) { this.y = y; }

        public String getLatitude() { return y; }
        public String getLongitude() { return x; }
    }
}