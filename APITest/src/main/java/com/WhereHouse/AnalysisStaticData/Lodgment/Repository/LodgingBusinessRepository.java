package com.WhereHouse.APITest.Lodgment.Repository;

import com.WhereHouse.APITest.Lodgment.Entity.LodgingBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LodgingBusinessRepository extends JpaRepository<LodgingBusiness, Long> {

    Optional<LodgingBusiness> findByManagementNumber(String managementNumber);

    @Query("SELECT l FROM LodgingBusiness l WHERE l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findAllActiveBusinesses();

    @Query("SELECT l FROM LodgingBusiness l WHERE l.fullAddress LIKE %:keyword% AND l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findActiveBusinessesByAddress(@Param("keyword") String keyword);

    @Query("SELECT l FROM LodgingBusiness l WHERE l.businessTypeName IN ('여관업', '여인숙업') AND l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findActiveMotelsAndInns();

    boolean existsByManagementNumber(String managementNumber);
}