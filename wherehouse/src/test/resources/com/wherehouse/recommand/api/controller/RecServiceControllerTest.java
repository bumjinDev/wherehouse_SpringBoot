package com.wherehouse.recommand.unit;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest	// 실제 웹 서버 환경에서 테스트 하므로 컨텍스트만 로드
@AutoConfigureMockMvc	// 실제 HTTP 요청 테스트(tetRestTemplate 의 실제 HTTPServletRequest 요청과 달리 "MockHttpServletRequest"요청이 디스패처 서블릿만 호출)
public class RecServiceControllerTest {
	
	@Autowired
	private MockMvc mockMvc;
	
	@Autowired
    private ObjectMapper objectMapperBody;
	
	private Map<String, String> recommandCharteRequest;		// 주거지 추천 서비스 중 "전세 테스트" 사용될 요청 객체.
	private Map<String, String> recommandMonthlyRequest;	// 주거지 추천 서비스 중 "월세 테스트" 사용될 요청 객체.

	
	@BeforeEach
    void setUp() {
    	
    	// 주거지 추천 서비스 중 "전세 테스트" 사용될 요청 객체.
		recommandCharteRequest = Map.of(
				"charter_avg", "15000",
	            "safe_score", "5",
	            "cvt_score", "5"
        );
		
		// 주거지 추천 서비스 중 "월세 테스트" 사용될 요청 객체.
		recommandMonthlyRequest = Map.of(
				"deposit_avg", "2100",
	            "monthly_avg", "50",
	            "safe_score", "5",
	            "cvt_score", "5"
        );
    }
	
	@Test
	@DisplayName("전세 추천 API 테스트 - 정상 호출")
	void testGetCharterRecommendation() throws Exception {
	    
		String requestBody= objectMapperBody.writeValueAsString(recommandCharteRequest);
		
	    mockMvc.perform(post("/RecServiceController/charter")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(requestBody))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.size()").value(3))
	            .andExpect(jsonPath("$[0].gu_name").value("종로구"))
	            .andExpect(jsonPath("$[1].gu_name").value("노원구"))
	            .andExpect(jsonPath("$[2].gu_name").value("강북구"))
	            .andDo(result -> {
	            	
	                String jsonResponse = result.getResponse().getContentAsString();
	               
	                assertThat(jsonResponse).isNotNull();
	                System.out.println("API Response: " + jsonResponse);
	            });
	}
	
	@Test
	@DisplayName("전세 추천 API 테스트 - 정상 호출")
	void testGetMonthlyRecommendation() throws Exception {
	    
		String requestBody= objectMapperBody.writeValueAsString(recommandMonthlyRequest);
		
	    mockMvc.perform(post("/RecServiceController/monthly")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(requestBody))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.size()").value(3))		// jsonPath("$.size()")는 Content-Type이 application/json 아닐 경우 사용할 수 없다
	            .andExpect(jsonPath("$[0].gu_name").value("동대문구"))
	            .andExpect(jsonPath("$[1].gu_name").value("성북구"))
	            .andExpect(jsonPath("$[2].gu_name").value("노원구"))
	            .andDo(result -> {
	            	
	                String jsonResponse = result.getResponse().getContentAsString();
	               
	                assertThat(jsonResponse).isNotNull();
	                System.out.println("API Response: " + jsonResponse);
	            });
	}

}