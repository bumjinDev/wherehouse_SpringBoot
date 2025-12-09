package com.wherehouse.recommand.batch.repository;

import com.wherehouse.recommand.batch.entity.PropertyCharter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 전세 매물 데이터 접근 객체
 *
 * 역할: PROPERTIES_CHARTER 테이블에 대한 CRUD 수행
 * 주요 기능: 배치 수집 데이터의 대량 적재 (saveAll -> Upsert)
 */
@Repository
public interface PropertyCharterRepository extends JpaRepository<PropertyCharter, String> {
    // JpaRepository가 제공하는 save(), saveAll(), findById() 등을 그대로 사용

    /**
     * 등록일자 기준 전세 매물 조회
     * 배치 프로세스에서 오늘 저장된 데이터를 Redis로 동기화할 때 사용
     *
     * @param rgstDate 등록일자 (yyyyMMdd 형식)
     * @return 해당 일자에 등록된 전세 매물 목록
     */
    List<PropertyCharter> findByRgstDate(String rgstDate);
}