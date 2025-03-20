package com.wherehouse.recommand.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import jakarta.transaction.Transactional;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class RecServiceControllerAPITest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final HttpHeaders headers = new HttpHeaders();

    RecServiceControllerAPITest() {
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    private final ParameterizedTypeReference<List<Map<String, Object>>> responseType =
            new ParameterizedTypeReference<List<Map<String, Object>>>() {};

    @Test
    @DisplayName("전세 추천 API - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseCharterRec_SafeGreaterThanCvt() {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "15000",
                "safe_score", "7",
                "cvt_score", "3"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/charter", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("종로구", response.getBody().get(0).get("gu_name"));
    }

    @Test
    @DisplayName("전세 추천 API - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseCharterRec_SafeLessThanCvt() {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "15000",
                "safe_score", "3",
                "cvt_score", "7"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/charter", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("종로구", response.getBody().get(0).get("gu_name"));
    }

    @Test
    @DisplayName("전세 추천 API - 안전성 점수와 편의 점수가 동일한 경우")
    void testChooseCharterRec_SafeEqualsCvt() {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "25000",
                "safe_score", "5",
                "cvt_score", "5"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/charter", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("송파구", response.getBody().get(0).get("gu_name"));
    }

    @Test
    @DisplayName("월세 추천 API - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeGreaterThanCvt() {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "7",
                "cvt_score", "3"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/monthly", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("강북구", response.getBody().get(0).get("gu_name"));
    }

    @Test
    @DisplayName("월세 추천 API - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeLessThanCvt() {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "3",
                "cvt_score", "7"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/monthly", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("동대문구", response.getBody().get(0).get("gu_name"));
    }

    @Test
    @DisplayName("월세 추천 API - 안전성 점수와 편의 점수가 동일한 경우")
    void testChooseMonthlyRec_SafeEqualsCvt() {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "5",
                "cvt_score", "5"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/RecServiceController/monthly", HttpMethod.POST, request, responseType);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        assertEquals("동대문구", response.getBody().get(0).get("gu_name"));
    }
}
