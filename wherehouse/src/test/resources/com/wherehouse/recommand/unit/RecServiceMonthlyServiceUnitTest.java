package com.wherehouse.recommand.unit;

import static org.mockito.Mockito.*;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;
import com.wherehouse.recommand.service.RecServiceMonthlyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;

/* Unit Test : 주거지 추천 서비스 - [전세금] 테스트
 * */
@ExtendWith(MockitoExtension.class)
class RecServiceMonthlyServiceUnitTest {
    
    @Mock
    private IRecServiceEmpRepository recServiceEmpRepository;
    
    @InjectMocks
    private RecServiceMonthlyService recServiceMonthlyService;
    
    // 호출 흐름 검사 시 요청 데이터
    private Map <String, String> requestAjax;
    
    // 호출 흐름 검사 시 반환되어야 하는 Mock 객체
    private List<RecServiceVO> mockRecServiceVO;
    
    @BeforeEach
    void setUp() {

    	requestAjax = new HashMap<>();
    	mockRecServiceVO = new ArrayList<RecServiceVO>();
    	
    	/* 월세금 기준 주거지 추천 요청 테스트 Mock 생성 */
    	requestAjax.put("deposit_avg", "2100");	// 월세 보증금
    	requestAjax.put("monthly_avg", "50");	// 월세금
    	requestAjax.put("safe_score", "5");
    	requestAjax.put("cvt_score", "5");
    	
    	/* 응답 Mock 생성 */
    	// 강북구 데이터
    	
    	/* 응답 Mock 생성 */
        mockRecServiceVO = new ArrayList<>(List.of(
            new RecServiceVO(3, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700),
            new RecServiceVO(4, "강북구", 75, 85, 6, 8, 6, 4, 10, 3, 40, 1400, 4800, 650)
        ));

        mockRecServiceVO.addAll(List.of(
            new RecServiceVO(5, "노원구", 85, 95, 4, 9, 8, 2, 15, 4, 55, 1600, 5200, 750),
            new RecServiceVO(6, "노원구", 70, 80, 7, 12, 5, 3, 9, 2, 45, 1350, 4700, 620)
        ));

        mockRecServiceVO.addAll(List.of(
            new RecServiceVO(7, "종로구", 90, 98, 3, 8, 6, 1, 20, 5, 60, 1700, 5400, 800),
            new RecServiceVO(8, "종로구", 78, 88, 6, 11, 9, 2, 13, 3, 48, 1450, 4900, 680)
        ));
        
    }
    
    /* "정상 범위" 값에 해당하는 요청을 했을 때 기본 호출 흐름을 보장하는 지 확인. */
    @Test
    void RecommendationMonthlyUnitTest() {
    	
    	// 전세금 테스트 시 호출되는 레포지토리 : 전세금 15000 만원 / 안정성 5점 / 편의성 점수 5 으로 요청 시 
        when(recServiceEmpRepository.chooseMonthlyRec(2100, 50, 5, 5)).thenReturn(mockRecServiceVO);

        List<RecServiceVO> result = recServiceMonthlyService.execute(requestAjax);
                
        assertNotNull(result);
        assertEquals("강북구", result.get(0).getGu_name());
       
        verify(recServiceEmpRepository, times(1)).chooseMonthlyRec(2100, 50, 5, 5);
    }
}

