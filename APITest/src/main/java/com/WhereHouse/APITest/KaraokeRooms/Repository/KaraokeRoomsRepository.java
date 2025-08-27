package com.WhereHouse.API.Test.APITest.KaraokeRooms.Repository;

import com.WhereHouse.API.Test.APITest.KaraokeRooms.Entity.KaraokeRooms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KaraokeRoomsRepository extends JpaRepository<KaraokeRooms, Long> {

    List<KaraokeRooms> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);

    @Query("SELECT COUNT(k) FROM KaraokeRooms k WHERE k.districtCode = :districtCode AND k.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);

    @Query("SELECT k.businessCategory, COUNT(k) FROM KaraokeRooms k WHERE k.businessStatusCode = '1' GROUP BY k.businessCategory")
    List<Object[]> countByBusinessCategory();

    @Query("SELECT k FROM KaraokeRooms k WHERE k.businessStatusCode = '3'")
    List<KaraokeRooms> findClosedKaraokeRooms();

    @Query("SELECT k FROM KaraokeRooms k WHERE k.youthRoomAvailable = 'Y'")
    List<KaraokeRooms> findKaraokeRoomsWithYouthRooms();

    boolean existsByManagementNumber(String managementNumber);
}