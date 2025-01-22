package com.wherehouse;

import static org.mockito.Mockito.*;

import com.wherehouse.recommand.controller.RecServiceController;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.RecServiceCharterService;
import com.wherehouse.recommand.service.RecServiceMonthlyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RecControllerMockTest {

    private MockMvc mockMvc;

    @InjectMocks
    private RecServiceController recServiceController;

    @Mock
    private RecServiceCharterService recServiceCharterService;
    
    @Mock
    private RecServiceMonthlyService recServiceMonthlyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(recServiceController).build();
    }

    @Test
    void testCharterRecommendation() throws Exception {
        Map<String, String> requestData = new HashMap<>();
        requestData.put("charter_avg", "15000");
        requestData.put("safe_score", "4");
        requestData.put("cvt_score", "3");

        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = objectMapper.writeValueAsString(requestData);

        List<RecServiceVO> recServiceVOList = getExpectedRecServiceVOList();
        
        when(recServiceCharterService.execute(any())).thenReturn(recServiceVOList);

        mockMvc.perform(post("/RecServiceController/charter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestContent))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<RecServiceVO> responseList = objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<List<RecServiceVO>>() {});
                    assertTrue(validateRecServiceVOList(responseList));
                });

        verify(recServiceCharterService, times(1)).execute(any());
    }

    @Test
    void testMonthlyRecommendation() throws Exception {
    	
        Map<String, String> requestData = new HashMap<>();
        
        requestData.put("deposit_avg", "5000");
        requestData.put("monthly_avg", "50");
        requestData.put("safe_score", "4");
        requestData.put("cvt_score", "3");

        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = objectMapper.writeValueAsString(requestData);

        List<RecServiceVO> recServiceVOList = getExpectedRecServiceVOList();
        
        when(recServiceMonthlyService.execute(any())).thenReturn(recServiceVOList);

        mockMvc.perform(post("/RecServiceController/monthly")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestContent))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<RecServiceVO> responseList = objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), new TypeReference<List<RecServiceVO>>() {});
                    assertTrue(validateRecServiceVOList(responseList));
                });

        verify(recServiceMonthlyService, times(1)).execute(any());
    }

    public static List<RecServiceVO> getExpectedRecServiceVOList() {
        List<RecServiceVO> recServiceVOList = new ArrayList<>();
        recServiceVOList.add(RecServiceVO.builder().gu_id(24).gu_name("종로구").cvt_score(56).safe_score(93).cafe(1690).cvt_store(56).daiso(7).oliveYoung(12).restourant(7206).police_office(32).cctv(1966).charter_avg(14013).deposit_avg(2193).monthly_avg(51).build());
        recServiceVOList.add(RecServiceVO.builder().gu_id(16).gu_name("강북구").cvt_score(30).safe_score(76).cafe(574).cvt_store(30).daiso(8).oliveYoung(6).restourant(3526).police_office(20).cctv(3321).charter_avg(12256).deposit_avg(1512).monthly_avg(40).build());
        recServiceVOList.add(RecServiceVO.builder().gu_id(15).gu_name("도봉구").cvt_score(23).safe_score(70).cafe(489).cvt_store(23).daiso(8).oliveYoung(6).restourant(2224).police_office(15).cctv(2385).charter_avg(14315).deposit_avg(1835).monthly_avg(42).build());
        return recServiceVOList;
    }

    public static boolean validateRecServiceVOList(List<RecServiceVO> actual) {
        List<RecServiceVO> expected = getExpectedRecServiceVOList();
        return actual.equals(expected);
    }
}
