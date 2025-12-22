package com.wherehouse.review.repository;

import com.wherehouse.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * 리뷰 Repository
 *
 * 설계 명세서: 7.1.2 A. REVIEWS (리뷰 원본)
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 리뷰 목록 조회 (단일 매물 ID 또는 전체 조회용)
     *
     * [수정됨] ORA-00932(CLOB DISTINCT 불가) 해결을 위해 EXISTS 서브쿼리 사용
     *
     * @param propertyId 매물 ID (nullable)
     * @param keyword 검색 키워드 (nullable)
     * @param pageable 페이징 및 정렬
     * @return 리뷰 페이지
     */
    @Query("SELECT r FROM Review r " +
            "WHERE (:propertyId IS NULL OR r.propertyId = :propertyId) " +
            "AND (:keyword IS NULL OR " +
            "     r.content LIKE %:keyword% OR " +
            "     EXISTS (SELECT k FROM ReviewKeyword k WHERE k.reviewId = r.reviewId AND k.keyword = :keyword))")
    Page<Review> findReviews(
            @Param("propertyId") String propertyId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * [수정됨] 매물 이름으로 매물 ID 목록 검색 (인덱스 최적화 버전)
     *
     * ====================================================================
     * [성능 개선 내역]
     * ====================================================================
     * 변경 전: LIKE '%' || :name || '%'  (양방향 와일드카드)
     *         → Full Table Scan 발생, 인덱스 사용 불가
     *         → 병목 비중: 전체 응답 시간의 66.3%~86.5%
     *
     * 변경 후: LIKE :name || '%'  (후방 와일드카드만 사용)
     *         → Index Range Scan 가능
     *         → apt_nm 컬럼 인덱스 활용
     *
     * [필수 인덱스]
     * CREATE INDEX IDX_CHARTER_APT_NM ON PROPERTIES_CHARTER(APT_NM);
     * CREATE INDEX IDX_MONTHLY_APT_NM ON PROPERTIES_MONTHLY(APT_NM);
     * ====================================================================
     *
     * @param name 검색할 매물 이름 (예: "관악산 삼성산 주공 3단지")
     * @return 매물 ID 리스트
     */
    @Query(value =
            "SELECT property_id FROM properties_charter WHERE apt_nm LIKE :name || '%' " +
                    "UNION ALL " +
                    "SELECT property_id FROM properties_monthly WHERE apt_nm LIKE :name || '%'",
            nativeQuery = true)
    List<String> findPropertyIdsByName(@Param("name") String name);

    /**
     * [추가됨] 다중 매물 ID에 해당하는 리뷰 조회
     *
     * 이름 검색 결과(ID 목록)에 해당하는 리뷰들을 조회 (IN 절 사용)
     *
     * @param propertyIds 매물 ID 리스트
     * @param pageable 페이징 정보
     * @return 리뷰 페이지
     */
    @Query("SELECT r FROM Review r WHERE r.propertyId IN :propertyIds")
    Page<Review> findByPropertyIdIn(@Param("propertyIds") List<String> propertyIds, Pageable pageable);

    /**
     * 매물명 일괄 조회 (DTO 매핑용)
     *
     * @param propertyIds 매물 ID Set
     * @return [property_id, apt_nm] 리스트
     */
    @Query(value =
            "SELECT property_id, apt_nm FROM properties_charter " +
                    "WHERE property_id IN :propertyIds " +
                    "UNION ALL " +
                    "SELECT property_id, apt_nm FROM properties_monthly " +
                    "WHERE property_id IN :propertyIds",
            nativeQuery = true)
    List<Object[]> findPropertyNames(@Param("propertyIds") Set<String> propertyIds);

    /**
     * 중복 리뷰 체크
     */
    boolean existsByPropertyIdAndUserId(String propertyId, String userId);

    /**
     * 평균 별점 및 리뷰 개수 집계
     */
    @Query("SELECT COUNT(r), COALESCE(AVG(r.rating), 0.0) " +
            "FROM Review r " +
            "WHERE r.propertyId = :propertyId")
    List<Object[]> aggregateReviewStats(@Param("propertyId") String propertyId);

    /**
     * 매물 ID 목록(List)과 키워드를 모두 만족하는 리뷰 검색
     */
    @Query("SELECT r FROM Review r " +
            "WHERE r.propertyId IN :propertyIds " +
            "AND (:keyword IS NULL OR r.content LIKE %:keyword%)")
    Page<Review> findReviewsByPropertyIdsAndKeyword(
            @Param("propertyIds") List<String> propertyIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * 전체 리뷰 목록 조회
     */
    Page<Review> findAll(Pageable pageable);

    /**
     * [수정됨] 매물 자동완성 검색 (성능 제한 해제 버전 - 포트폴리오용)
     * * - FETCH FIRST (Limit) 제거됨
     * - 검색어("삼성")가 포함된 모든 행을 DB에서 가져옴 (Full Load)
     * - 추후 데이터가 많아질 경우 DB I/O 및 메모리 병목 지점(Bottleneck Point)이 됨
     */
    @Query(value =
            "SELECT property_id, apt_nm, '전세' as type FROM properties_charter " +
                    "WHERE apt_nm LIKE %:keyword% " +
                    "UNION ALL " +
                    "SELECT property_id, apt_nm, '월세' as type FROM properties_monthly " +
                    "WHERE apt_nm LIKE %:keyword%",
            nativeQuery = true)
    List<Object[]> searchPropertiesByName(@Param("keyword") String keyword);
}