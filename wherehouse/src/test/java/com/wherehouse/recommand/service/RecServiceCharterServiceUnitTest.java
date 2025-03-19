package com.wherehouse.recommand.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class) // Mockito 자동 초기화
class RecServiceCharterServiceUnitTest {

    @Mock
    IRecServiceEmpRepository recServiceEmpRepository;

    @InjectMocks
    RecServiceCharterService recServiceCharterService;

    Map<String, String> requestAjax;
    List<RecServiceVO> mockRecServiceVO;

    private static final Logger logger = LoggerFactory.getLogger(RecServiceCharterServiceUnitTest.class);

    @BeforeEach
    void setUp() {
       
        logger.info("==== setUp() 실행됨 ====");

        requestAjax = new HashMap<>();
        requestAjax.put("charter_avg", "2100");
        requestAjax.put("safe_score", "5");
        requestAjax.put("cvt_score", "5");

        mockRecServiceVO = List.of(
            new RecServiceVO(1, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700),
            new RecServiceVO(2, "종로구", 75, 85, 6, 9, 8, 4, 11, 1, 48, 1600, 5200, 750)
        );
    }

    @Test
    void execute_ValidInput_ReturnsRecommendations() {
    
        logger.info("==== 테스트 실행됨 ====");


        when(recServiceEmpRepository.chooseCharterRec(anyInt(), anyInt(), anyInt()))
            .thenReturn(mockRecServiceVO);

        // 서비스 메서드 실행
        logger.info("==== execute() 실행 전 ====");
        List<RecServiceVO> result = recServiceCharterService.execute(requestAjax);
        logger.info("결과 크기: " + result.size());

        // 검증
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals("강북구", result.get(0).getGu_name());

        // verify() 실행 여부 확인
        logger.info("==== verify() 실행 전 ====");
        verify(recServiceEmpRepository, times(1)).chooseCharterRec(anyInt(), anyInt(), anyInt());
    }
}
