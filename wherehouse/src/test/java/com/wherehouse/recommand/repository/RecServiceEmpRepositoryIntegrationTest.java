package com.wherehouse.recommand.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.wherehouse.recommand.dao.RecServiceEmpRepository;
import com.wherehouse.recommand.model.RecServiceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/* 테스트용 DB 내 실제 동작 테스트 */

@SpringBootTest
//@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecServiceEmpRepositoryIntegrationTest {

    private final RecServiceEmpRepository recServiceEmpRepository;

    @Autowired
    public RecServiceEmpRepositoryIntegrationTest(RecServiceEmpRepository recServiceEmpRepository) {
        this.recServiceEmpRepository = recServiceEmpRepository;
    }

    @BeforeEach
    void setUp() {
    }

    /**
     * 1) 안전성 점수가 편의 점수보다 큰 경우 (safe > cvt)
     *    - "SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY safe_score DESC, charter_avg DESC"
     */
    @Test
    void testChooseCharterRec_SafeGreaterThanCvt() {
    	
        Map<String, String> requestAjax = Map.of(
            "charter_avg", "15000",
            "safe_score", "7",   // 안전성이 더 큼
            "cvt_score", "3"
        );

        List<RecServiceVO> recRepository = recServiceEmpRepository.chooseCharterRec(
            Integer.parseInt(requestAjax.get("charter_avg")),
            Integer.parseInt(requestAjax.get("safe_score")),
            Integer.parseInt(requestAjax.get("cvt_score"))
        );

        assertFalse(recRepository.isEmpty());
        
        // 3건이 나올 것으로 기대 (로직 상 ROWNUM <= 3)
        assertEquals(3, recRepository.size());
        assertEquals("종로구", recRepository.get(0).getGu_name());
        assertEquals("강북구", recRepository.get(1).getGu_name());
        assertEquals("도봉구", recRepository.get(2).getGu_name());
        // 나머지도 필요하다면 검증
    }

    /**
     * 2) 안전성 점수가 편의 점수보다 작은 경우 (safe < cvt)
     *    - "SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY cvt_score DESC, charter_avg DESC"
     */
    @Test
    void testChooseCharterRec_SafeLessThanCvt() {
        Map<String, String> requestAjax = Map.of(
            "charter_avg", "15000",
            "safe_score", "3",
            "cvt_score", "7"    // 편의 점수가 더 큼
        );

        List<RecServiceVO> recRepository = recServiceEmpRepository.chooseCharterRec(
            Integer.parseInt(requestAjax.get("charter_avg")),
            Integer.parseInt(requestAjax.get("safe_score")),
            Integer.parseInt(requestAjax.get("cvt_score"))
        );

        assertFalse(recRepository.isEmpty());
        assertEquals(3, recRepository.size());
        assertEquals("종로구", recRepository.get(0).getGu_name());
        assertEquals("노원구", recRepository.get(1).getGu_name());
        assertEquals("강북구", recRepository.get(2).getGu_name());
    }

    /**
     * 3) 안전성 점수와 편의 점수가 같은 경우 (safe == cvt)
     *    - "(?+1)*10 < 60"이면 전세금 기준 정렬, 그 외 cvt_score 기준
     *    - "SELECT ... CASE WHEN (?+1)*10 < 60 THEN charter_avg ELSE cvt_score END DESC ..."
     */
    @Test
    void testChooseCharterRec_SafeEqualsCvt() {
        // 예: safe = 5, cvt = 5
        Map<String, String> requestAjax = Map.of(
            "charter_avg", "25000",
            "safe_score", "5",
            "cvt_score", "5"
        );

        List<RecServiceVO> recRepository = recServiceEmpRepository.chooseCharterRec(
            Integer.parseInt(requestAjax.get("charter_avg")),
            Integer.parseInt(requestAjax.get("safe_score")),
            Integer.parseInt(requestAjax.get("cvt_score"))
        );

        assertFalse(recRepository.isEmpty());
   
        assertEquals(3, recRepository.size());
        assertEquals("송파구", recRepository.get(0).getGu_name());
        assertEquals("중구", recRepository.get(1).getGu_name());
        assertEquals("마포구", recRepository.get(2).getGu_name());
    }
}


