package com.WhereHouse.AnalysisData.bankcount.repository;

import com.WhereHouse.AnalysisData.bankcount.entity.AnalysisBankCountStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisBankCountRepository extends JpaRepository<AnalysisBankCountStatistics, Long> {

    List<AnalysisBankCountStatistics> findBySigungu(String sigungu);

    Optional<AnalysisBankCountStatistics> findBySigunguAndDong(String sigungu, String dong);

    boolean existsBySigunguAndDong(String sigungu, String dong);

    @Query("SELECT COUNT(a) FROM AnalysisBankCountStatistics a")
    long countAnalysisData();

    @Query("SELECT a.sigungu, SUM(a.wooriBankCount + a.scBankCount + a.kbBankCount + a.shinhanBankCount + " +
            "a.citiBankCount + a.hanaBankCount + a.ibkBankCount + a.nhBankCount + " +
            "a.suhyupBankCount + a.kdbBankCount + a.eximBankCount + a.foreignBankCount) as totalBanks " +
            "FROM AnalysisBankCountStatistics a GROUP BY a.sigungu ORDER BY totalBanks DESC")
    List<Object[]> findDistrictBankDensityRanking();
}