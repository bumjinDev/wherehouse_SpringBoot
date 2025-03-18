package com.wherehouse.recommand.controller;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.IRecService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest(controllers = RecServiceController.class)
@ContextConfiguration(classes = {RecServiceController.class}) // 강제로 특정 빈만 로드
@AutoConfigureMockMvc(addFilters = false)  // Security 필터 비활성화// 실제 HTTP 요청 테스트(tetRestTemplate 의 실제 HTTPServletRequest 요청과 달리 "MockHttpServletRequest"요청이 디스패처 서블릿만 호출)
public class RecServiceControllerUnitTest {
	
	private final Logger logger = LoggerFactory.getLogger(RecServiceControllerUnitTest.class);
	
	@Autowired
	private MockMvc mockMvc;
	
	@MockBean
    @Qualifier("recServiceCharterService")
    private IRecService recServiceCharterService;

    @MockBean
    @Qualifier("recServiceMonthlyService")
    private IRecService recServiceMonthlyService;
	
	@Autowired
    private ObjectMapper objectMapperBody;
	
	/* 요청 데이터 */
	private Map<String, String> recommandCharteRequest;		// 주거지 추천 서비스 중 "전세 테스트" 사용될 요청 객체.
	private Map<String, String> recommandMonthlyRequest;	// 주거지 추천 서비스 중 "월세 테스트" 사용될 요청 객체.

	/* 응답 "when" 데이터 */
	List<RecServiceVO> mockResponseCharter;
	List<RecServiceVO> mockResponseMonthly;
	
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
		
		 // Mock 객체가 실행될 때 반환할 데이터 정의
	    mockResponseCharter = List.of(
	    	new RecServiceVO(2, "종로구", 75, 85, 6, 9, 8, 4, 11, 1, 48, 1600, 5200, 750),
	        new RecServiceVO(14, "노원구", 38, 70, 434, 302, 12, 12, 3488, 23, 2626, 11717, 1487, 37),
	        new RecServiceVO(1, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700)
	    );

	    mockResponseMonthly = List.of(
	        new RecServiceVO(19, "동대문구", 54, 71, 730, 316, 9, 10, 4526, 27, 2759, 20095, 1865, 47),
	        new RecServiceVO(17, "성북구", 44, 75,	807, 297,	9,	12,	3802, 31,	4957,	18338,	2059,	40),
	        new RecServiceVO(14, "노원구",	38,	70,	434,	302,	12,	12,	3488,	23,	2626,	11717,	1487,	37)
	    );
	    
    }
	
	@Test
	@DisplayName("전세 추천 API 테스트 - 정상 호출")
	void testGetCharterRecommendation() throws Exception {
	    
		logger.info("RecServiceControllerUnitTest.testGetCharterRecommendation()");
		
		when(recServiceCharterService.execute(recommandCharteRequest)).thenReturn(mockResponseCharter);
		
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
	@DisplayName("월세 추천 API 테스트 - 정상 호출")
	void testGetMonthlyRecommendation() throws Exception {
	    
		logger.info("RecServiceControllerUnitTest.testGetMonthlyRecommendation()");
		
		when(recServiceMonthlyService.execute(recommandMonthlyRequest)).thenReturn(mockResponseMonthly);
		
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