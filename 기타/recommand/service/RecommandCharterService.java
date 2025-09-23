package com.wherehouse.recommand.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecCharterServiceRequestVO;
import com.wherehouse.recommand.model.RecServiceVO;

/* 전세금 요청 처리 */
@Service
public class RecommandCharterService implements IRecommandCharterService {

    private final Logger logger = LoggerFactory.getLogger(RecommandCharterService.class);
    private final IRecServiceEmpRepository recServiceEmpRepository;

    public RecommandCharterService(IRecServiceEmpRepository recServiceEmpRepository) {
        this.recServiceEmpRepository = recServiceEmpRepository;
    }

    @Override
    public List<RecServiceVO> recommandCharterService(RecCharterServiceRequestVO recCharterServiceRequestVO) {
        logger.info("RecServiceCharterService.execute()!");

        String query;
        Object[] params;

        if (recCharterServiceRequestVO.getSafe_score() > recCharterServiceRequestVO.getCvt_score()) {
        	
            query = "SELECT * FROM(SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY safe_score DESC, charter_avg DESC) WHERE ROWNUM <= 3";
            params = new Object[]{recCharterServiceRequestVO.getCharter_avg()};
            
        } else if (recCharterServiceRequestVO.getSafe_score() > recCharterServiceRequestVO.getCvt_score()) {
            
        	query = "SELECT * FROM(SELECT * FROM gu_info WHERE charter_avg <= ? ORDER BY cvt_score DESC, charter_avg DESC) WHERE ROWNUM <= 3";
            params = new Object[]{recCharterServiceRequestVO.getCharter_avg()};
            
        } else {
            query = "SELECT * FROM ( " +
                    "   SELECT * FROM gu_info " +
                    "   WHERE charter_avg <= ? " +
                    "   ORDER BY " +
                    "       CASE " +
                    "           WHEN (?+1)*10 < 60 THEN charter_avg " +
                    "           ELSE cvt_score " +
                    "       END DESC, " +
                    "       charter_avg DESC " +
                    ") WHERE ROWNUM <= 3";
            params = new Object[]{recCharterServiceRequestVO.getCharter_avg(), recCharterServiceRequestVO.getSafe_score()};
        }
        return recServiceEmpRepository.chooseCharterRec(query, params);
    }
}
