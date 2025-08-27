package com.WhereHouse.API.Test.APITest.StreetLightRaw.Repository;

import com.WhereHouse.API.Test.APITest.StreetLightRaw.Entity.StreetlightRawData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StreetlightRawDataRepository extends JpaRepository<StreetlightRawData, Long> {
    Optional<StreetlightRawData> findByManagementNumber(String managementNumber);
    List<StreetlightRawData> findAllByOrderByManagementNumberAsc();
    boolean existsByManagementNumber(String managementNumber);
}