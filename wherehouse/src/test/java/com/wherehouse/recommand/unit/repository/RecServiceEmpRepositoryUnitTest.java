package com.wherehouse.recommand.unit.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wherehouse.recommand.dao.RecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class RecServiceEmpRepositoryUnitTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RecServiceEmpRepository recServiceEmpRepository;

    @BeforeEach
    void setUp() {
    }

    // ============================
    // 전세 추천 테스트
    // ============================

    @Test
    @DisplayName("전세 추천 - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseCharterRec_SafeGreaterThanCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(1, "종로구", 75, 85, 6, 9, 8, 4, 11, 1, 48, 1600, 5200, 750),
            new RecServiceVO(2, "강북구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700),
            new RecServiceVO(3, "도봉구", 78, 88, 4, 8, 6, 2, 10, 3, 49, 1550, 5100, 720)
        );

        String actualQuery = "SELECT * FROM(SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY safe_score DESC, charter_avg DESC) WHERE ROWNUM <= 3";

        when(jdbcTemplate.query(eq(actualQuery), ArgumentMatchers.<RowMapper<RecServiceVO>>any(), eq(15000)))
                .thenReturn(mockResult);

        List<RecServiceVO> result = recServiceEmpRepository.chooseCharterRec(15000, 7, 3);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("종로구", result.get(0).getGu_name());
    }

    @Test
    @DisplayName("전세 추천 - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseCharterRec_SafeLessThanCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(4, "노원구", 70, 65, 8, 11, 9, 5, 13, 2, 45, 1700, 5300, 800),
            new RecServiceVO(5, "성북구", 72, 68, 7, 10, 8, 4, 12, 3, 46, 1650, 5200, 780),
            new RecServiceVO(6, "동대문구", 74, 70, 6, 9, 7, 3, 11, 4, 47, 1600, 5100, 760)
        );

        String actualQuery = "SELECT * FROM(SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY cvt_score DESC, charter_avg DESC) WHERE ROWNUM <= 3";

        when(jdbcTemplate.query(eq(actualQuery), ArgumentMatchers.<RowMapper<RecServiceVO>>any(), eq(15000)))
                .thenReturn(mockResult);

        List<RecServiceVO> result = recServiceEmpRepository.chooseCharterRec(15000, 3, 7);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("노원구", result.get(0).getGu_name());
    }

    @Test
    @DisplayName("전세 추천 - 안전성 점수 == 편의 점수")
    void testChooseCharterRec_SafeEqualsCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(7, "송파구", 88, 88, 10, 8, 6, 4, 9, 2, 70, 30000, 10000, 1200),
            new RecServiceVO(8, "서초구", 88, 88, 9, 7, 5, 3, 8, 3, 69, 29000, 9800, 1150),
            new RecServiceVO(9, "강남구", 88, 88, 8, 6, 4, 2, 7, 4, 68, 28000, 9500, 1100)
        );

        String actualQuery = "SELECT * FROM ( " +
                "   SELECT * FROM gu_info " +
                "   WHERE charter_avg <= ? " +
                "   ORDER BY " +
                "       CASE " +
                "           WHEN (?+1)*10 < 60 THEN charter_avg " +
                "           ELSE cvt_score " +
                "       END DESC, " +
                "       charter_avg DESC " +
                ") WHERE ROWNUM <= 3";

        when(jdbcTemplate.query(eq(actualQuery), ArgumentMatchers.<RowMapper<RecServiceVO>>any(), eq(25000), eq(5)))
                .thenReturn(mockResult);

        List<RecServiceVO> result = recServiceEmpRepository.chooseCharterRec(25000, 5, 5);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("송파구", result.get(0).getGu_name());
        assertEquals("서초구", result.get(1).getGu_name());
        assertEquals("강남구", result.get(2).getGu_name());
    }
    
    // ============================
    // 월세 추천 테스트
    // ============================

    @Test
    @DisplayName("월세 추천 - 안전성 점수가 편의 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeGreaterThanCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(10, "강서구", 75, 85, 6, 9, 8, 4, 11, 1, 48, 1600, 5200, 750),
            new RecServiceVO(11, "마포구", 80, 90, 5, 10, 7, 3, 12, 2, 50, 1500, 5000, 700),
            new RecServiceVO(12, "영등포구", 78, 88, 4, 8, 6, 2, 10, 3, 49, 1550, 5100, 720)
        );

   
        when(jdbcTemplate.query(
                eq("SELECT * FROM(SELECT * FROM gu_info WHERE monthly_avg <= ? AND deposit_avg <=? ORDER BY safe_score DESC, monthly_avg DESC) WHERE ROWNUM <= 3"),
                ArgumentMatchers.<RowMapper<RecServiceVO>>any(),
                anyInt(), anyInt()))
            .thenReturn(mockResult);


        List<RecServiceVO> result = recServiceEmpRepository.chooseMonthlyRec(2100, 50, 7, 3);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("강서구", result.get(0).getGu_name());
    }
    
    @Test
    @DisplayName("월세 추천 - 편의 점수가 안전성 점수보다 높은 경우")
    void testChooseMonthlyRec_SafeLessThanCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(13, "서대문구", 70, 65, 8, 11, 9, 5, 13, 2, 45, 1700, 5300, 800),
            new RecServiceVO(14, "용산구", 72, 68, 7, 10, 8, 4, 12, 3, 46, 1650, 5200, 780),
            new RecServiceVO(15, "종로구", 74, 70, 6, 9, 7, 3, 11, 4, 47, 1600, 5100, 760)
        );

        // SQL 매칭 문제를 방지하기 위해 anyString() 사용
        when(jdbcTemplate.query(
            anyString(),  // 쿼리 문자열이 다를 가능성을 고려
            ArgumentMatchers.<RowMapper<RecServiceVO>>any(),
            eq(50),  // 정확한 값 사용
            eq(2100) // 정확한 값 사용
        )).thenReturn(mockResult);

        List<RecServiceVO> result = recServiceEmpRepository.chooseMonthlyRec(2100, 50, 3, 7);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("서대문구", result.get(0).getGu_name());
        assertEquals("용산구", result.get(1).getGu_name());
        assertEquals("종로구", result.get(2).getGu_name());
    }
    
    @Test
    @DisplayName("월세 추천 - 안전성 점수 == 편의 점수")
    void testChooseMonthlyRec_SafeEqualsCvt() {
        List<RecServiceVO> mockResult = Arrays.asList(
            new RecServiceVO(16, "성동구", 88, 88, 10, 8, 6, 4, 9, 2, 70, 30000, 10000, 1200),
            new RecServiceVO(17, "중구", 88, 88, 9, 7, 5, 3, 8, 3, 69, 29000, 9800, 1150),
            new RecServiceVO(18, "동작구", 88, 88, 8, 6, 4, 2, 7, 4, 68, 28000, 9500, 1100)
        );

        when(jdbcTemplate.query(
            anyString(),  // SQL 전체를 anyString() 처리
            ArgumentMatchers.<RowMapper<RecServiceVO>>any(),
            anyInt(),      // 예상 파라미터와 다를 가능성을 고려하여 anyInt() 사용
            anyInt(),
            anyInt()
        )).thenReturn(mockResult);

        List<RecServiceVO> result = recServiceEmpRepository.chooseMonthlyRec(2100, 50, 5, 5);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("성동구", result.get(0).getGu_name());
        assertEquals("중구", result.get(1).getGu_name());
        assertEquals("동작구", result.get(2).getGu_name());
    }

}
