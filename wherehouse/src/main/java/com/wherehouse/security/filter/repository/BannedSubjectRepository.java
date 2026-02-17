package com.wherehouse.security.filter.repository;

import com.wherehouse.security.filter.entity.BannedSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BANNED_SUBJECT 테이블 접근 Repository
 *
 * ============================================================================
 * [변경 이력]
 * ============================================================================
 * BannedIpRepository → BannedSubjectRepository 전환.
 * IP 단일 조회에서 IP + userId 복합 조회로 확장되었다.
 *
 * [쿼리 메서드 설계]
 * ============================================================================
 * - existsByIpAddressAndBannedUntilAfter: IP 기준 밴 확인 (비인증 요청 차단)
 * - existsByUserIdAndBannedUntilAfter:   userId 기준 밴 확인 (IP 변경 우회 차단)
 * - findAllByBannedUntilAfter:           서버 기동 시 Redis 캐시 워밍업용 전체 조회
 * ============================================================================
 */
public interface BannedSubjectRepository extends JpaRepository<BannedSubject, Long> {

    /**
     * 특정 IP가 현재 밴 상태인지 확인.
     * BANNED_UNTIL이 현재 시각보다 미래인 행이 존재하면 밴 상태이다.
     */
    boolean existsByIpAddressAndBannedUntilAfter(String ipAddress, LocalDateTime now);

    /**
     * 특정 userId가 현재 밴 상태인지 확인.
     * 공격자가 IP를 변경해도 동일 userId로 요청하면 이 쿼리에서 차단된다.
     * userId가 NULL인 행은 이 쿼리에 매칭되지 않는다.
     */
    boolean existsByUserIdAndBannedUntilAfter(String userId, LocalDateTime now);

    /**
     * 현재 유효한 밴 목록 조회 (서버 기동 시 Redis 로드용)
     */
    List<BannedSubject> findAllByBannedUntilAfter(LocalDateTime now);
}