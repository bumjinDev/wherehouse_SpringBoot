package com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.Repository;

import com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.entity.FinancialInstitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialInstitutionRepository extends JpaRepository<FinancialInstitution, Long> {

    List<FinancialInstitution> findBySigungu(String sigungu);
    Optional<FinancialInstitution> findBySigunguAndDong(String sigungu, String dong);
    boolean existsBySigunguAndDong(String sigungu, String dong);
}