package com.wherehouse.recommand.batch.repository;

import com.wherehouse.recommand.batch.entity.PropertyCharter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 전세 매물 데이터 접근 객체
 *
 * 역할: PROPERTIES_CHARTER 테이블에 대한 CRUD 수행
 * 주요 기능: 배치 수집 데이터의 대량 적재 (saveAll -> Upsert)
 */
@Repository
public interface PropertyCharterRepository extends JpaRepository<PropertyCharter, String> {
    // JpaRepository가 제공하는 save(), saveAll(), findById() 등을 그대로 사용
}