package com.wherehouse.security.filter.repository;

import com.wherehouse.security.filter.entity.BannedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BANNED_IP 테이블 접근 Repository
 */
public interface BannedIpRepository extends JpaRepository<BannedIp, Long> {

    /**
     * 특정 IP가 현재 밴 상태인지 확인
     * BANNED_UNTIL이 현재 시각보다 미래인 행이 존재하면 밴 상태
     */
    boolean existsByIpAddressAndBannedUntilAfter(String ipAddress, LocalDateTime now);

    /**
     * 현재 유효한 밴 목록 조회 (서버 기동 시 Redis 로드용)
     */
    List<BannedIp> findAllByBannedUntilAfter(LocalDateTime now);
}
