package com.wherehouse.information.dao;

import com.wherehouse.information.entity.ArrestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ArrestRate 테이블 접근을 위한 JPA Repository
 *
 * 주요 기능:
 * - 구 이름으로 검거율 조회
 *
 * 사용 위치:
 * - LocationAnalysisServiceImpl: 안전성 점수 계산 시 검거율 조회
 */
@Repository
public interface ArrestRateRepository extends JpaRepository<ArrestRate, String> {

    /**
     * 구 이름으로 검거율 조회
     *
     * @param addr 서울시 구 이름 (예: "중구", "종로구")
     * @return 검거율 정보 (Optional)
     *
     * 예시:
     * - findByAddr("중구") → Optional<ArrestRate(addr="중구", rate=0.866910799)>
     * - findByAddr("존재하지않는구") → Optional.empty()
     */
    Optional<ArrestRate> findByAddr(String addr);
}