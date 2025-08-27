package com.WhereHouse.APITest.FinancialInstitutionDetail.Client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.WhereHouse.APITest.FinancialInstitutionDetail.DTO.KakaoLocalApiResponse;
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
public class KakaoLocalApiClient {

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.local-api.base-url}")
    private String baseUrl;

    @Value("${kakao.local-api.search-radius}")
    private int searchRadius;

    @Value("${kakao.local-api.page-size}")
    private int pageSize;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 키워드로 장소 검색
     */
    public KakaoLocalApiResponse searchByKeyword(String query, BigDecimal x, BigDecimal y, int page) {
        String url = baseUrl + "/search/keyword.json";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("query", query)
                .queryParam("x", x.toString())
                .queryParam("y", y.toString())
                .queryParam("radius", searchRadius)
                .queryParam("page", page)
                .queryParam("size", pageSize)
                .queryParam("sort", "distance");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalApiResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    KakaoLocalApiResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 키워드 검색 실패: query={}, error={}", query, e.getMessage());
            throw e;
        }
    }

    /**
     * 카테고리로 장소 검색 (BK9: 은행)
     */
    public KakaoLocalApiResponse searchByCategory(String categoryCode, BigDecimal x, BigDecimal y, int page) {
        String url = baseUrl + "/search/category.json";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", x.toString())
                .queryParam("y", y.toString())
                .queryParam("radius", searchRadius)
                .queryParam("page", page)
                .queryParam("size", pageSize)
                .queryParam("sort", "distance");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalApiResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    KakaoLocalApiResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 카테고리 검색 실패: category={}, error={}", categoryCode, e.getMessage());
            throw e;
        }
    }
}