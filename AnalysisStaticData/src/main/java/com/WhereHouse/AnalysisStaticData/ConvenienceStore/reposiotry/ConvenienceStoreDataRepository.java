package com.WhereHouse.AnalysisStaticData.ConvenienceStore.reposiotry;

import com.WhereHouse.AnalysisStaticData.ConvenienceStore.entity.ConvenienceStoreData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConvenienceStoreDataRepository extends JpaRepository<ConvenienceStoreData, Long> {

    // 관리번호로 조회
    Optional<ConvenienceStoreData> findByManagementNumber(String managementNumber);

    // 중복 체크
    boolean existsByManagementNumber(String managementNumber);

    // 영업상태별 조회
    List<ConvenienceStoreData> findByBusinessStatusCode(String businessStatusCode);
    List<ConvenienceStoreData> findByBusinessStatusName(String businessStatusName);

    // 영업중인 편의점만 조회
    @Query("SELECT c FROM ConvenienceStoreData c WHERE c.businessStatusCode = '1' AND c.businessStatusName LIKE '%영업%'")
    List<ConvenienceStoreData> findActiveStores();

    // 주소 기반 검색
    List<ConvenienceStoreData> findByRoadAddressContaining(String address);
    List<ConvenienceStoreData> findByLotAddressContaining(String address);

    // 사업장명으로 검색
    List<ConvenienceStoreData> findByBusinessNameContaining(String businessName);

    // 특정 지역 편의점 조회
    @Query("SELECT c FROM ConvenienceStoreData c WHERE c.roadAddress LIKE %:region% OR c.lotAddress LIKE %:region%")
    List<ConvenienceStoreData> findByRegion(@Param("region") String region);

    // 영업상태별 통계
    @Query("SELECT c.businessStatusName, COUNT(c) FROM ConvenienceStoreData c GROUP BY c.businessStatusName ORDER BY COUNT(c) DESC")
    List<Object[]> countByBusinessStatus();

    // 지역별 편의점 수 통계 (도로명주소 기준)
    @Query(value = """
        SELECT 
            CASE 
                WHEN ROAD_ADDRESS LIKE '%강남구%' THEN '강남구'
                WHEN ROAD_ADDRESS LIKE '%서초구%' THEN '서초구'
                WHEN ROAD_ADDRESS LIKE '%송파구%' THEN '송파구'
                WHEN ROAD_ADDRESS LIKE '%강동구%' THEN '강동구'
                WHEN ROAD_ADDRESS LIKE '%마포구%' THEN '마포구'
                WHEN ROAD_ADDRESS LIKE '%서대문구%' THEN '서대문구'
                WHEN ROAD_ADDRESS LIKE '%은평구%' THEN '은평구'
                WHEN ROAD_ADDRESS LIKE '%종로구%' THEN '종로구'
                WHEN ROAD_ADDRESS LIKE '%중구%' THEN '중구'
                WHEN ROAD_ADDRESS LIKE '%용산구%' THEN '용산구'
                WHEN ROAD_ADDRESS LIKE '%성동구%' THEN '성동구'
                WHEN ROAD_ADDRESS LIKE '%동대문구%' THEN '동대문구'
                WHEN ROAD_ADDRESS LIKE '%중랑구%' THEN '중랑구'
                WHEN ROAD_ADDRESS LIKE '%성북구%' THEN '성북구'
                WHEN ROAD_ADDRESS LIKE '%강북구%' THEN '강북구'
                WHEN ROAD_ADDRESS LIKE '%도봉구%' THEN '도봉구'
                WHEN ROAD_ADDRESS LIKE '%노원구%' THEN '노원구'
                WHEN ROAD_ADDRESS LIKE '%광진구%' THEN '광진구'
                WHEN ROAD_ADDRESS LIKE '%영등포구%' THEN '영등포구'
                WHEN ROAD_ADDRESS LIKE '%동작구%' THEN '동작구'
                WHEN ROAD_ADDRESS LIKE '%관악구%' THEN '관악구'
                WHEN ROAD_ADDRESS LIKE '%서초구%' THEN '서초구'
                WHEN ROAD_ADDRESS LIKE '%강서구%' THEN '강서구'
                WHEN ROAD_ADDRESS LIKE '%양천구%' THEN '양천구'
                WHEN ROAD_ADDRESS LIKE '%구로구%' THEN '구로구'
                ELSE '기타'
            END as district,
            COUNT(*) as count
        FROM CONVENIENCE_STORE_DATA 
        WHERE ROAD_ADDRESS IS NOT NULL
        GROUP BY 
            CASE 
                WHEN ROAD_ADDRESS LIKE '%강남구%' THEN '강남구'
                WHEN ROAD_ADDRESS LIKE '%서초구%' THEN '서초구'
                WHEN ROAD_ADDRESS LIKE '%송파구%' THEN '송파구'
                WHEN ROAD_ADDRESS LIKE '%강동구%' THEN '강동구'
                WHEN ROAD_ADDRESS LIKE '%마포구%' THEN '마포구'
                WHEN ROAD_ADDRESS LIKE '%서대문구%' THEN '서대문구'
                WHEN ROAD_ADDRESS LIKE '%은평구%' THEN '은평구'
                WHEN ROAD_ADDRESS LIKE '%종로구%' THEN '종로구'
                WHEN ROAD_ADDRESS LIKE '%중구%' THEN '중구'
                WHEN ROAD_ADDRESS LIKE '%용산구%' THEN '용산구'
                WHEN ROAD_ADDRESS LIKE '%성동구%' THEN '성동구'
                WHEN ROAD_ADDRESS LIKE '%동대문구%' THEN '동대문구'
                WHEN ROAD_ADDRESS LIKE '%중랑구%' THEN '중랑구'
                WHEN ROAD_ADDRESS LIKE '%성북구%' THEN '성북구'
                WHEN ROAD_ADDRESS LIKE '%강북구%' THEN '강북구'
                WHEN ROAD_ADDRESS LIKE '%도봉구%' THEN '도봉구'
                WHEN ROAD_ADDRESS LIKE '%노원구%' THEN '노원구'
                WHEN ROAD_ADDRESS LIKE '%광진구%' THEN '광진구'
                WHEN ROAD_ADDRESS LIKE '%영등포구%' THEN '영등포구'
                WHEN ROAD_ADDRESS LIKE '%동작구%' THEN '동작구'
                WHEN ROAD_ADDRESS LIKE '%관악구%' THEN '관악구'
                WHEN ROAD_ADDRESS LIKE '%서초구%' THEN '서초구'
                WHEN ROAD_ADDRESS LIKE '%강서구%' THEN '강서구'
                WHEN ROAD_ADDRESS LIKE '%양천구%' THEN '양천구'
                WHEN ROAD_ADDRESS LIKE '%구로구%' THEN '구로구'
                ELSE '기타'
            END
        ORDER BY COUNT(*) DESC
        """, nativeQuery = true)
    List<Object[]> countByDistrict();

    // 날짜 범위별 조회
    List<ConvenienceStoreData> findByLicenseDateBetween(LocalDate startDate, LocalDate endDate);
    List<ConvenienceStoreData> findByClosureDateBetween(LocalDate startDate, LocalDate endDate);

    // 페이징 처리된 전체 조회
    Page<ConvenienceStoreData> findAll(Pageable pageable);

    // 영업중인 편의점 페이징 조회
    @Query("SELECT c FROM ConvenienceStoreData c WHERE c.businessStatusCode = '1' AND c.businessStatusName LIKE '%영업%'")
    Page<ConvenienceStoreData> findActiveStores(Pageable pageable);
}