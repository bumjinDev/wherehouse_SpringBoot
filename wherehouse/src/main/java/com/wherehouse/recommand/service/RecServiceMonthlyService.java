package com.wherehouse.recommand.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wherehouse.recommand.dao.IRecServiceEmpRepository;
import com.wherehouse.recommand.model.RecMonthlyServiceRequestVO;
import com.wherehouse.recommand.model.RecServiceVO;

/* 월세 요청 처리 */
@Service
public class RecServiceMonthlyService implements IRecServiceMonthlyService {

    private static final Logger logger = LoggerFactory.getLogger(RecServiceMonthlyService.class);
    private final IRecServiceEmpRepository recServiceEmpRepository;

    public RecServiceMonthlyService(IRecServiceEmpRepository recServiceEmpRepository) {
        this.recServiceEmpRepository = recServiceEmpRepository;
    }

    @Override
    public List<RecServiceVO> monthlyRecommandService(RecMonthlyServiceRequestVO recMonthlyServiceRequestVO) {
        logger.info("RecServiceMonthlyService.execute()!");

        logger.info("Parsed Params - deposit: {}, monthly: {}, safe: {}, cvt: {}",
        		recMonthlyServiceRequestVO.getDeposit_avg(),
        		recMonthlyServiceRequestVO.getMonthly_avg(),
        		recMonthlyServiceRequestVO.getSafe_score(),
        		recMonthlyServiceRequestVO.getCvt_score());

        String query;
        Object[] params;

        if (recMonthlyServiceRequestVO.getSafe_score() > recMonthlyServiceRequestVO.getCvt_score()) {
            query = "SELECT * FROM(SELECT * FROM gu_info WHERE monthly_avg <= ? AND deposit_avg <= ? ORDER BY safe_score DESC, monthly_avg DESC) WHERE ROWNUM <= 3";
            params = new Object[]{recMonthlyServiceRequestVO.getMonthly_avg(), recMonthlyServiceRequestVO.getDeposit_avg()};
        } else if (recMonthlyServiceRequestVO.getSafe_score() < recMonthlyServiceRequestVO.getCvt_score()) {
            query = "SELECT * FROM(SELECT * FROM gu_info WHERE monthly_avg <= ? AND deposit_avg <= ? ORDER BY cvt_score DESC, monthly_avg DESC) WHERE ROWNUM <= 3";
            params = new Object[]{recMonthlyServiceRequestVO.getMonthly_avg(), recMonthlyServiceRequestVO.getDeposit_avg()};
        } else {
            query = "SELECT * FROM(SELECT * FROM gu_info WHERE monthly_avg <= ? AND deposit_avg <= ? ORDER BY CASE WHEN ?*10 < 50 THEN monthly_avg ELSE cvt_score END DESC, monthly_avg DESC) WHERE ROWNUM <= 3";
            params = new Object[]{recMonthlyServiceRequestVO.getMonthly_avg(), recMonthlyServiceRequestVO.getDeposit_avg(), recMonthlyServiceRequestVO.getSafe_score()};
        }

        return recServiceEmpRepository.chooseMonthlyRec(query, params);
    }
}
