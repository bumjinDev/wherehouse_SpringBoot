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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RecServiceMonthlyServiceTest {

    @Mock
    private IRecServiceEmpRepository recServiceEmpRepository;

    @InjectMocks
    private RecServiceMonthlyService recServiceMonthlyService;

    private Map<String, String> requestAjax;
    private List<RecServiceVO> mockRecServiceVO;

    @BeforeEach
    void setUp() {
    	
        requestAjax = Map.of(
            "deposit_avg", "2100",
            "monthly_avg", "50",
            "safe_score", "5",
            "cvt_score", "5"
        );

        mockRecServiceVO = List.of(
            new RecServiceVO(3, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700)
        );
    }

    @Test
    void execute_ValidInput_ReturnsRecommendations() {

        when(recServiceEmpRepository.chooseMonthlyRec(2100, 50, 5, 5))
        .thenReturn(mockRecServiceVO);
    	
        List<RecServiceVO> result = recServiceMonthlyService.execute(requestAjax);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("강북구", result.get(0).getGu_name());
        verify(recServiceEmpRepository, times(1)).chooseMonthlyRec(2100, 50, 5, 5);
    }

    @Test
    void execute_InvalidNumberFormat_ThrowsException() {
    	
        requestAjax = Map.of("deposit_avg", "invalid");

        assertThrows(NumberFormatException.class, () -> recServiceMonthlyService.execute(requestAjax));
    }
}
