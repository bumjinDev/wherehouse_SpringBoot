package com.wherehouse.recommand.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // API 테스트 이므로 실제 Spring ApplicationContext 로드 한다.
@AutoConfigureMockMvc	// MockMvc 의 자동 설정 적용.
@ActiveProfiles("test")  // 본 프로젝트의 application.yml 사용 안함.
@Transactional  // 테스트 실행 후 DB 상태 초기화
class RecServiceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc; // MockBean이 아닌 실제 컨트롤러를 통한 API 테스트
//
//    @Autowired
//    @Qualifier("recServiceCharterService")
//    private IRecService recServiceCharterService;
//
//    @Autowired
//    @Qualifier("recServiceMonthlyService")
//    private IRecService recServiceMonthlyService;

    // ================================
    // 전세 추천 API 테스트
    // ================================

    @Autowired
    private ObjectMapper objectMapper; // JSON 변환을 위한 ObjectMapper 주입

    // ================================
    // 전세 추천 API 테스트
    // ================================

    @Test
    @DisplayName("전세 추천 API - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseCharterRec_SafeGreaterThanCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "15000",
                "safe_score", "7",
                "cvt_score", "3"
        );

        mockMvc.perform(post("/RecServiceController/charter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // 3건이 응답되었는지 확인
                .andExpect(jsonPath("$[0].gu_name", is("종로구")));
    }

    @Test
    @DisplayName("전세 추천 API - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseCharterRec_SafeLessThanCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "15000",
                "safe_score", "3",
                "cvt_score", "7"
        );

        mockMvc.perform(post("/RecServiceController/charter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].gu_name", is("종로구")));
    }

    @Test
    @DisplayName("전세 추천 API - 안전성 점수와 편의 점수가 동일한 경우")
    void testChooseCharterRec_SafeEqualsCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "charter_avg", "25000",
                "safe_score", "5",
                "cvt_score", "5"
        );

        mockMvc.perform(post("/RecServiceController/charter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].gu_name", is("송파구")));
    }

    // ================================
    // 월세 추천 API 테스트
    // ================================

    @Test
    @DisplayName("월세 추천 API - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeGreaterThanCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "7",
                "cvt_score", "3"
        );

        mockMvc.perform(post("/RecServiceController/monthly")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].gu_name", is("강북구")));
    }

    @Test
    @DisplayName("월세 추천 API - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeLessThanCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "3",
                "cvt_score", "7"
        );

        mockMvc.perform(post("/RecServiceController/monthly")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].gu_name", is("동대문구")));
    }

    @Test
    @DisplayName("월세 추천 API - 안전성 점수와 편의 점수가 동일한 경우")
    void testChooseMonthlyRec_SafeEqualsCvt() throws Exception {
        Map<String, String> requestBody = Map.of(
                "deposit_avg", "2100",
                "monthly_avg", "50",
                "safe_score", "5",
                "cvt_score", "5"
        );

        mockMvc.perform(post("/RecServiceController/monthly")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].gu_name", is("동대문구")));
    }
}
