package com.wherehouse.recommand.unit.repository;

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

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecServiceEmpRepositoryTest {

    private final RecServiceEmpRepository recServiceEmpRepository;

    @Autowired
    public RecServiceEmpRepositoryTest(RecServiceEmpRepository recServiceEmpRepository) {
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

    /* =========================================== */
    /* == 잘못된 범주의 값들에 대한 정상적인 예외 처리 여부 == */
    
    /**
     * 4) 전세금 한계 테스트 (charter_avg 바로 근처 값)
     *    - (예: 9000, 10000, 15000 등 DB 상태에 맞춰 조정)
     */
    
    /*
    @Test
    void testChooseCharterRec_CharterAvgEdgeCase() {
        Map<String, String> requestAjax = Map.of(
            "charter_avg", "9000",
            "safe_score", "5",
            "cvt_score", "5"
        );

        List<RecServiceVO> recRepository = recServiceEmpRepository.chooseCharterRec(
            Integer.parseInt(requestAjax.get("charter_avg")),
            Integer.parseInt(requestAjax.get("safe_score")),
            Integer.parseInt(requestAjax.get("cvt_score"))
        );

        // 결과가 없을 수도 있으니, 그 경우 assertTrue(...isEmpty()) 등으로 검증
        // 또는 나올 수 있는 구들을 예측해 검증
        assertTrue(recRepository.isEmpty());
    }
*/
    /**
     * 5) 전세금이 매우 큰 경우(실제 DB 데이터 범위를 초과)
     *    - 예: 999999, DB에는 대부분 20000 이하 라고 가정
     */
//    @Test
//    void testChooseCharterRec_NoResults() {
//        Map<String, String> requestAjax = Map.of(
//            "charter_avg", "999999",
//            "safe_score", "10",
//            "cvt_score", "0"
//        );
//
//        List<RecServiceVO> recRepository = recServiceEmpRepository.chooseCharterRec(
//            Integer.parseInt(requestAjax.get("charter_avg")),
//            Integer.parseInt(requestAjax.get("safe_score")),
//            Integer.parseInt(requestAjax.get("cvt_score"))
//        );
//
//        // 만약 해당 범위 내 전세금이 없어서 결과가 없을 수 있음
//        assertTrue(recRepository.isEmpty());
//    }
}


