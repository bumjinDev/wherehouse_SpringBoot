package com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.Repository;

import com.WhereHouse.API.Test.APITest.ConvenienceStore_noneUsed.Entity.ConvenienceStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConvenienceStoreRepository extends JpaRepository<ConvenienceStoreEntity, Long> {

    List<ConvenienceStoreEntity> findBySigungu(String sigungu);

    List<ConvenienceStoreEntity> findBySigunguAndDong(String sigungu, String dong);

    boolean existsByStoreNameAndAddress(String storeName, String address);

    @Query("SELECT cs FROM ConvenienceStoreEntity cs WHERE cs.sigungu LIKE :sigungu%")
    List<ConvenienceStoreEntity> findBySeoulDistrict(@Param("sigungu") String sigungu);

    long countBySigungu(String sigungu);
}
