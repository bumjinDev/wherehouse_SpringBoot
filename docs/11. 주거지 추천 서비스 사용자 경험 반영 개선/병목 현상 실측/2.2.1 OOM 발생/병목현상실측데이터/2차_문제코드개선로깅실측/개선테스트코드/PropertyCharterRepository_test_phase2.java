package com.wherehouse.recommand.batch.repository;

import com.wherehouse.recommand.batch.entity.PropertyCharter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 전세 매물 데이터 접근 객체
 *
 * 역할: PROPERTIES_CHARTER 테이블에 대한 CRUD 수행
 * 주요 기능: 배치 수집 데이터의 대량 적재 (saveAll -> Upsert)
 *
 * [2차 테스트 변경사항 - 2026-01-30]
 * - Slice 기반 페이징 조회 메서드 추가 (findAllBy)
 * - 목적: OOM 병목 해소를 위한 청크 단위 데이터 로드
 */
@Repository
public interface PropertyCharterRepository extends JpaRepository<PropertyCharter, String> {

    /**
     * 등록일자 기준 전세 매물 조회
     * 배치 프로세스에서 오늘 저장된 데이터를 Redis로 동기화할 때 사용
     *
     * @param rgstDate 등록일자 (yyyyMMdd 형식)
     * @return 해당 일자에 등록된 전세 매물 목록
     */
    List<PropertyCharter> findByRgstDate(String rgstDate);

    /**
     * [2차 테스트] Slice 기반 전체 매물 페이징 조회
     *
     * ================================================================================
     * 설계 근거: Slice vs Page 선택
     * ================================================================================
     *
     * Page<T>와 Slice<T>는 모두 Spring Data JPA의 페이징 반환 타입이지만,
     * 내부 동작 방식에서 결정적 차이가 존재한다.
     *
     * [Page<T>의 동작]
     * - 데이터 조회 쿼리 + COUNT(*) 쿼리 = 총 2회 쿼리 실행
     * - getTotalElements(), getTotalPages() 메서드 제공
     * - 전체 페이지 수 기반 UI 페이지네이션에 적합
     *
     * [Slice<T>의 동작]
     * - 데이터 조회 쿼리 1회만 실행 (COUNT 쿼리 없음)
     * - 요청 크기 + 1건을 조회하여 hasNext() 여부만 판단
     * - 예: pageSize=5000 요청 시 실제로 5001건 조회 시도
     *   - 5001건 조회됨 → hasNext() = true, 5000건만 반환
     *   - 5000건 이하 조회됨 → hasNext() = false
     *
     * [배치 처리에서 Slice 선택 이유]
     * 1. COUNT 쿼리 제거: 대용량 테이블(58,660건)에서 COUNT(*)는 Full Table Scan 유발
     * 2. 전체 페이지 수 불필요: while(slice.hasNext()) 패턴으로 순차 처리하므로
     *    총 몇 페이지인지 알 필요가 없음
     * 3. 일관된 처리 시간: 각 청크 조회 시간이 균일 (COUNT 오버헤드 없음)
     *
     * ================================================================================
     * Spring Data JPA 쿼리 메서드 명명 규칙
     * ================================================================================
     *
     * "findAllBy"는 Spring Data JPA의 Query Method 명명 규칙을 따른다:
     * - find: SELECT 연산
     * - All: 전체 대상 (조건 없음)
     * - By: 조건절 시작 지시자 (뒤에 조건이 없으면 WHERE 없이 전체 조회)
     *
     * 반환 타입이 Slice<T>이고 Pageable 파라미터가 있으면,
     * Spring Data JPA가 자동으로 LIMIT/OFFSET 기반 페이징 쿼리를 생성한다.
     *
     * 생성되는 쿼리 예시 (Oracle):
     * SELECT * FROM PROPERTIES_CHARTER
     * ORDER BY {Pageable.sort 기준}
     * OFFSET {page * size} ROWS FETCH NEXT {size + 1} ROWS ONLY
     *
     * ================================================================================
     * 사용 예시
     * ================================================================================
     *
     * <pre>
     * {@code
     * Pageable pageable = PageRequest.of(0, 5000, Sort.by("propertyId"));
     * Slice<PropertyCharter> slice;
     *
     * do {
     *     slice = propertyCharterRepository.findAllBy(pageable);
     *     List<PropertyCharter> chunk = slice.getContent();
     *
     *     // 청크 단위 처리 로직
     *     processChunk(chunk);
     *
     *     // 다음 페이지 요청 준비
     *     pageable = slice.nextPageable();
     *
     * } while (slice.hasNext());
     * }
     * </pre>
     *
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 현재 청크의 매물 목록과 다음 페이지 존재 여부를 포함한 Slice 객체
     */
    Slice<PropertyCharter> findAllBy(Pageable pageable);
}
