package com.wherehouse.AnalysisData.bankcount.repository;

import com.wherehouse.AnalysisData.bankcount.entity.AnalysisBankCountStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisBankCountRepository extends JpaRepository<AnalysisBankCountStatistics, Long> {

    /**
     * 특정 시군구에 해당하는 모든 동의 은행 수 데이터를 조회합니다.
     * @param sigungu 시군구명 (예: "강남구")
     * @return 은행 수 통계 엔티티 리스트
     */
    List<AnalysisBankCountStatistics> findBySigungu(String sigungu);

    /**
     * 특정 시군구와 동에 해당하는 은행 수 데이터를 조회합니다.
     * @param sigungu 시군구명
     * @param dong 동명
     * @return 은행 수 통계 엔티티 (Optional)
     */
    Optional<AnalysisBankCountStatistics> findBySigunguAndDong(String sigungu, String dong);

    /**
     * 서울시 자치구별 총 은행 수를 계산하여 내림차순으로 정렬된 순위를 반환합니다.
     * 피어슨 상관분석 데이터셋 검증 및 EDA(탐색적 데이터 분석)에 활용됩니다.
     * @return [Object[]{자치구명, 총 은행 수}] 형태의 리스트
     */
    @Query("SELECT a.sigungu, SUM(a.wooriBankCount + a.scBankCount + a.kbBankCount + a.shinhanBankCount + " +
            "a.citiBankCount + a.hanaBankCount + a.ibkBankCount + a.nhBankCount + " +
            "a.suhyupBankCount + a.kdbBankCount + a.eximBankCount + a.foreignBankCount) as totalBanks " +
            "FROM AnalysisBankCountStatistics a GROUP BY a.sigungu ORDER BY totalBanks DESC")
    List<Object[]> findDistrictBankDensityRanking();
}