package com.wherehouse;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.wherehouse.recommand.dao.RecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.RecServiceCharterService;
import com.wherehouse.recommand.service.RecServiceMonthlyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

class RecServiceMockTest {

    @InjectMocks
    private RecServiceCharterService recServiceCharterService;
    
    @InjectMocks
    private RecServiceMonthlyService recServiceMonthlyService;

    @Mock
    private RecServiceEmpRepository recServiceEmpRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock 객체 검증
        assertNotNull(recServiceCharterService, "recServiceCharterService should not be null");
        assertNotNull(recServiceMonthlyService, "recServiceMonthlyService should not be null");
        assertNotNull(recServiceEmpRepository, "recServiceEmpRepository should not be null");

        System.out.println("\nMock initialization successful.");
    }

    @Test
    void testCharterRecommendation() {
        System.out.println("\ntestCharterRecommendation");

        Map<String, String> requestAjax = new HashMap<>();
        requestAjax.put("charter_avg", "15000");
        requestAjax.put("safe_score", "4");
        requestAjax.put("cvt_score", "3");

        List<RecServiceVO> recServiceVOList = getExpectedRecServiceVOList();

        // Mock 레포지토리 설정
        when(recServiceEmpRepository.chooseCharterRec(anyInt(), anyInt(), anyInt())).thenReturn(recServiceVOList);

        List<RecServiceVO> result = recServiceCharterService.execute(requestAjax);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(validateRecServiceVOList(result));

        verify(recServiceEmpRepository, times(1)).chooseCharterRec(anyInt(), anyInt(), anyInt());
        
    }

    @Test
    void testMonthlyRecommendation() {
        System.out.println("\ntestMonthlyRecommendation");

        Map<String, String> requestData = new HashMap<>();
        requestData.put("deposit_avg", "5000");
        requestData.put("monthly_avg", "50");
        requestData.put("safe_score", "4");
        requestData.put("cvt_score", "3");

        List<RecServiceVO> recServiceVOList = getExpectedRecServiceVOList();

        // Mock 레포지토리 설정
        when(recServiceEmpRepository.chooseMonthlyRec(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(recServiceVOList);

        List<RecServiceVO> result = recServiceMonthlyService.execute(requestData);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(validateRecServiceVOList(result));

        verify(recServiceEmpRepository, times(1)).chooseMonthlyRec(anyInt(), anyInt(), anyInt(), anyInt());
    }

//    @Test
//    void testCharterRecommendationWithInvalidData() {
//        System.out.println("\ntestCharterRecommendationWithInvalidData");
//
//        Map<String, String> requestData = new HashMap<>();
//        requestData.put("charter_avg", "-1"); // Invalid value
//
//        when(recServiceCharterService.execute(anyMap())).thenThrow(new IllegalArgumentException("Invalid input"));
//        assertThrows(IllegalArgumentException.class, () -> recServiceCharterService.execute(requestData));
//    }
//
//    @Test
//    void testMonthlyRecommendationWithMissingData() {
//        System.out.println("\ntestMonthlyRecommendationWithMissingData");
//
//        Map<String, String> requestData = new HashMap<>();
//        requestData.put("safe_score", "4"); // Missing required field "monthly_avg"
//
//        when(recServiceMonthlyService.execute(anyMap())).thenThrow(new IllegalArgumentException("Missing required fields"));
//        assertThrows(IllegalArgumentException.class, () -> recServiceMonthlyService.execute(requestData));
//    }
//
//    @Test
//    void testCharterRecommendationWithEmptyValues() {
//        System.out.println("\ntestCharterRecommendationWithEmptyValues");
//
//        Map<String, String> requestData = new HashMap<>();
//        requestData.put("charter_avg", ""); // Empty value
//
//        when(recServiceCharterService.execute(requestData)).thenThrow(new IllegalArgumentException("Empty values not allowed"));
//        assertThrows(IllegalArgumentException.class, () -> recServiceCharterService.execute(requestData));
//    }

    public static List<RecServiceVO> getExpectedRecServiceVOList() {
        System.out.println("\ngetExpectedRecServiceVOList()!");

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
