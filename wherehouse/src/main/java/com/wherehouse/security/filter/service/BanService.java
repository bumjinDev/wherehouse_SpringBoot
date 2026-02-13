package com.wherehouse.security.filter.service;


import com.wherehouse.security.filter.entity.BannedIp;
import com.wherehouse.security.filter.repository.BannedIpRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IP 밴 관리 서비스
 *
 * ============================================================================
 * [역할]
 * ============================================================================
 * 1. 밴 등록: Oracle BANNED_IP 테이블 + Redis 키 동시 기록
 * 2. 밴 조회: Redis 키 존재 여부로 O(1) 판별
 * 3. 캐시 워밍업: 서버 기동 시 DB의 유효한 밴 목록을 Redis로 로드
 *
 * [Redis 키 설계]
 * ============================================================================
 * 키:   ban:ip:{ipAddress}
 * 값:   "1" (존재 여부만 판별)
 * TTL:  밴 잔여 시간 (BANNED_UNTIL - 현재 시각)
 *
 * Redis TTL 만료 시 키가 자동 삭제되므로 밴 해제 스케줄러가 불필요하다.
 * 관리자가 DB에서 DELETE하면, 다음 서버 재기동 시 Redis에 로드되지 않아 해제된다.
 * 즉시 해제가 필요하면 Redis 키도 함께 삭제하면 된다.
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanService {

    private final BannedIpRepository bannedIpRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BAN_KEY_PREFIX = "ban:ip:";
    private static final long BAN_DAYS = 7;

    // =========================================================================
    // [캐시 워밍업] 서버 기동 시 DB → Redis 로드
    // =========================================================================
    @PostConstruct
    public void loadBannedIpsToRedis() {
        List<BannedIp> activeBans = bannedIpRepository.findAllByBannedUntilAfter(LocalDateTime.now());

        int loadCount = 0;
        for (BannedIp ban : activeBans) {
            long remainingSeconds = Duration.between(LocalDateTime.now(), ban.getBannedUntil()).getSeconds();
            if (remainingSeconds > 0) {
                String redisKey = BAN_KEY_PREFIX + ban.getIpAddress();
                redisTemplate.opsForValue().set(redisKey, "1", remainingSeconds, TimeUnit.SECONDS);
                loadCount++;
            }
        }

        log.info("[BAN_INIT] 밴 IP 캐시 로드 완료: DB 조회={}건, Redis 로드={}건", activeBans.size(), loadCount);
    }

    // =========================================================================
    // [밴 여부 확인] Redis O(1) 조회
    // =========================================================================
    public boolean isBanned(String ipAddress) {
        String redisKey = BAN_KEY_PREFIX + ipAddress;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("[BAN_CHECK] Redis 조회 실패, 밴 미적용: ip={}, error={}", ipAddress, e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // [밴 등록] DB + Redis 동시 기록
    // =========================================================================
    public void banIp(String ipAddress, String reason) {

        // 이미 밴 상태인지 확인 (중복 밴 방지)
        if (isBanned(ipAddress)) {
            log.info("[BAN_SKIP] 이미 밴 상태: ip={}", ipAddress);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bannedUntil = now.plusDays(BAN_DAYS);
        long ttlSeconds = Duration.between(now, bannedUntil).getSeconds();

        // DB 기록
        BannedIp bannedIp = BannedIp.builder()
                .ipAddress(ipAddress)
                .reason(reason)
                .bannedAt(now)
                .bannedUntil(bannedUntil)
                .build();
        bannedIpRepository.save(bannedIp);

        // Redis 기록
        String redisKey = BAN_KEY_PREFIX + ipAddress;
        redisTemplate.opsForValue().set(redisKey, "1", ttlSeconds, TimeUnit.SECONDS);

        log.warn("[BAN_REGISTERED] IP 밴 등록: ip={}, reason={}, bannedUntil={}",
                ipAddress, reason, bannedUntil);
    }
}
