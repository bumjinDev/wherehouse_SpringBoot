package com.wherehouse.recommand.service;


import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class RecServiceCharterServiceUnitTest {

    @Mock
    private IRecServiceEmpRepository recServiceEmpRepository;

    @InjectMocks
    private RecServiceCharterService recServiceCharterService;

    private Map<String, String> requestAjax;
    private List<RecServiceVO> mockRecServiceVO;

    @BeforeEach
    void setUp() {
    	
    	// 리포지토리에서 반환될 Mock 데이터
        mockRecServiceVO = List.of(
            new RecServiceVO(1, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700)
        );
    }

    /*  통상적인 호출 검증  */
    @Test
    void execute_ValidInput_ReturnsRecommendations() {
    	
    	 // 정상 입력값으로 구성된 Mock 데이터
        requestAjax = Map.of(
            "charter_avg", "1000",
            "safe_score", "5",
            "cvt_score", "5"
        );

        // Repository Mock 동작 설정
        when(recServiceEmpRepository.chooseCharterRec(1000, 5, 5))
            .thenReturn(mockRecServiceVO);
        // 실제 서비스 메서드 호출
        List<RecServiceVO> result = recServiceCharterService.execute(requestAjax);
        // 결과 검증
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("강북구", result.get(0).getGu_name());
        // Mock 객체 메서드가 정확히 한 번 호출되었는지 확인
        verify(recServiceEmpRepository, times(1)).chooseCharterRec(1000, 5, 5);
    }
}
