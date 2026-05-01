package com.wherehouse.PropertyManagement.service;

import com.wherehouse.PropertyManagement.dto.*;
import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyCharterEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import com.wherehouse.PropertyManagement.execption.customExceptions.*;
import com.wherehouse.PropertyManagement.integration.BoundsUpdater;
import com.wherehouse.PropertyManagement.integration.PropertyHashBuilder;
import com.wherehouse.PropertyManagement.repository.PropertyCharterRegistrationRepository;
import com.wherehouse.recommand.batch.util.IdGenerator;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* 실패에 대한 대처(스케줄러) 위해 추가하는 부분들 */
import com.wherehouse.PropertyManagement.entity.PropertySyncFailure;
import com.wherehouse.PropertyManagement.repository.PropertySyncFailureRepository;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  F006 3단계 — 수정 A-2: synchronized + TransactionTemplate     │
 * │                                                                 │
 * │  원본: CharterPropertyWriteService.java                         │
 * │  변경 범위: createProperty() 메서드만 수정                       │
 * │                                                                 │
 * │  핵심 변경:                                                      │
 * │    1. @Transactional 제거 → TransactionTemplate 프로그래밍 방식  │
 * │    2. synchronized(this)로 SELECT + INSERT + 커밋을 단일 모니터  │
 * │       내부에서 완료하여 TOCTOU 갭 제거                            │
 * │                                                                 │
 * │  구조적 특성:                                                    │
 * │    - JVM synchronized 모니터의 해제 시점과 DB 커밋 시점이 일치   │
 * │    - 모든 propertyId의 모든 요청이 동일 모니터를 통과 (전역 직렬) │
 * │    - afterCommit 콜백(Redis 동기화)도 synchronized 내부에서 실행 │
 * │    - 단일 JVM에서만 유효, 다중 인스턴스 배포 시 보호 깨짐        │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharterPropertyWriteService {


    private final PropertyCharterRegistrationRepository charterRepository;
    private final RedisHandler redisHandler;
    private final PropertyHashBuilder propertyHashBuilder;
    private final BoundsUpdater boundsUpdater;
    private final IdGenerator idGenerator;

    /* 실패에 대한 대처(스케줄러) 위해 추가하는 부분들 */
    private final PropertySyncFailureRepository syncFailureRepository;

    private final PlatformTransactionManager transactionManager;

    private static final String LEASE_CHARTER_CODE = "CHARTER";
    private static final String LEASE_CHARTER_KOR = "전세";
    private static final BigDecimal PYEONG_DIVISOR = new BigDecimal("3.305785");
    private static final double PRICE_ZERO_DELTA = 1000.0;
    private static final double AREA_ZERO_DELTA = 5.0;

    private static final Map<String, String> SEOUL_DISTRICT_CODES = Map.ofEntries(
            Map.entry("11110", "종로구"), Map.entry("11140", "중구"),     Map.entry("11170", "용산구"),
            Map.entry("11200", "성동구"), Map.entry("11215", "광진구"),   Map.entry("11230", "동대문구"),
            Map.entry("11260", "중랑구"), Map.entry("11290", "성북구"),   Map.entry("11305", "강북구"),
            Map.entry("11320", "도봉구"), Map.entry("11350", "노원구"),   Map.entry("11380", "은평구"),
            Map.entry("11410", "서대문구"), Map.entry("11440", "마포구"), Map.entry("11470", "양천구"),
            Map.entry("11500", "강서구"), Map.entry("11530", "구로구"),   Map.entry("11545", "금천구"),
            Map.entry("11560", "영등포구"), Map.entry("11590", "동작구"), Map.entry("11620", "관악구"),
            Map.entry("11650", "서초구"), Map.entry("11680", "강남구"),   Map.entry("11710", "송파구"),
            Map.entry("11740", "강동구")
    );

    // ============================================================
    // F001 전세 매물 등록 — 수정 A-2: synchronized + TransactionTemplate
    // ============================================================

    /**
     * 수정 A-2 — synchronized + TransactionTemplate.
     *
     * @Transactional을 제거하고 TransactionTemplate으로 프로그래밍 방식 트랜잭션을 사용한다.
     * synchronized(this) 블록 내부에서 TransactionTemplate.execute()를 호출하므로,
     * 커밋 완료 시점이 synchronized 해제 시점보다 반드시 먼저 온다.
     *
     * 동작 순서:
     *   1. synchronized 획득
     *   2. TransactionTemplate이 트랜잭션 시작
     *   3. findById → 중복 확인 (커밋된 행 기준)
     *   4. save → INSERT
     *   5. TransactionTemplate이 커밋 + afterCommit 콜백 실행
     *   6. synchronized 해제
     *
     * 후속 스레드는 6번 이후에 진입하므로 3번의 findById에서
     * 선착 스레드가 커밋한 행을 반드시 확인할 수 있다.
     */
    // @Transactional 제거 — TransactionTemplate으로 대체
    public PropertyCreateResponseDto createProperty(CharterCreateRequestDto dto, String userId) {

        /* 순수 연산: synchronized 외부에서 수행하여 임계 구간 최소화 */
        validateDistrictCode(dto.getSggCd());

        String propertyId = idGenerator.generatePropertyId(
                dto.getSggCd(), dto.getJibun(), dto.getAptNm(),
                String.valueOf(dto.getFloor()), dto.getExcluUseAr().toPlainString());

        log.info("매물 ID={}", propertyId);

        String districtName = resolveDistrictName(dto.getSggCd());
        BigDecimal areaInPyeong = toPyeong(dto.getExcluUseAr());
        String address = buildAddress(districtName, dto.getUmdNm(), dto.getJibun());
        LocalDateTime now = LocalDateTime.now();

        PropertyCharterEntity entity = PropertyCharterEntity.builder()
                .propertyId(propertyId)
                .aptNm(dto.getAptNm()).excluUseAr(dto.getExcluUseAr())
                .floor(dto.getFloor()).buildYear(dto.getBuildYear()).dealDate(dto.getDealDate())
                .deposit(Long.valueOf(dto.getDeposit()))
                .leaseType(LEASE_CHARTER_KOR)
                .umdNm(dto.getUmdNm()).jibun(dto.getJibun()).sggCd(dto.getSggCd())
                .address(address).areaInPyeong(areaInPyeong).districtName(districtName)
                .dataSource(DataSource.USER).status(PropertyStatus.ACTIVE)
                .registeredUserId(userId).registeredAt(now)
                .build();

        /* ── 수정 A-2 핵심: synchronized + TransactionTemplate ──
         *
         * synchronized(this)로 모든 요청을 직렬화하고,
         * TransactionTemplate으로 커밋 시점을 synchronized 내부로 끌어들인다.
         *
         * @Transactional AOP 프록시와 달리, TransactionTemplate.execute()는
         * 콜백이 정상 반환하면 즉시 커밋을 수행하므로
         * synchronized 해제 전에 커밋이 완료된다.
         */
        synchronized (this) {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.execute(status -> {

                Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
                if (existing.isPresent()) {
                    throw new DuplicatePropertyException(
                            "이미 등록된 전세 매물입니다. propertyId=" + propertyId);
                }

                charterRepository.save(entity);

                // afterCommit: TransactionTemplate 내부에서도 정상 동작.
                // 커밋 직후(= synchronized 해제 전) Redis 동기화가 실행된다.
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                executeRedisSyncWithCompensation(entity);
                            }
                        }
                );

                return null;
            });
        }

        return PropertyCreateResponseDto.builder()
                .propertyId(propertyId).leaseType(LEASE_CHARTER_CODE)
                .dataSource(DataSource.USER.name()).status(PropertyStatus.ACTIVE.name())
                .registeredUserId(userId).registeredAt(now)
                .build();
    }

    // ============================================================
    // F002 전세 매물 수정 (원본 유지)
    // ============================================================

//    @Transactional    // 테스트 목적 주석
    public PropertyUpdateResponseDto updateProperty(
            String propertyId, CharterUpdateRequestDto dto, String userId) {

        PropertyCharterEntity entity = charterRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId));

//        verifyOwnership(entity, userId);  // 요구사항 수정 : 모든 사용자가 등록자 외에도 접근하여 수정 가능하도록.
        verifyActiveForUpdate(entity.getStatus());

        List<String> changedFields = new ArrayList<>();

        if (dto.getDeposit() != null && !longEqualsInteger(entity.getDeposit(), dto.getDeposit())) {
            entity.setDeposit(Long.valueOf(dto.getDeposit()));
            changedFields.add("deposit");
        }
        if (dto.getBuildYear() != null && !dto.getBuildYear().equals(entity.getBuildYear())) {
            entity.setBuildYear(dto.getBuildYear());
            changedFields.add("buildYear");
        }
        if (dto.getDealDate() != null && !dto.getDealDate().equals(entity.getDealDate())) {
            entity.setDealDate(dto.getDealDate());
            changedFields.add("dealDate");
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setModifiedAt(now);
        charterRepository.save(entity);

        syncRedisAfterUpdate(entity, changedFields);

        return PropertyUpdateResponseDto.builder()
                .propertyId(entity.getPropertyId())
                .modifiedAt(now)
                .changedFields(Collections.unmodifiableList(changedFields))
                .build();
    }

    // ============================================================
    // F003 전세 매물 상태 변경 (원본 유지)
    // ============================================================

//    @Transactional    // 테스트 목적 주석
    public PropertyStatusUpdateResponseDto changeStatus(
            String propertyId, PropertyStatusUpdateRequestDto dto, String userId) {

        PropertyCharterEntity entity = charterRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId));

        verifyOwnership(entity, userId);

        PropertyStatus previous = entity.getStatus();
        PropertyStatus target = PropertyStatus.valueOf(dto.getTargetStatus());

        verifyTransition(previous, target);

        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(target);
        entity.setModifiedAt(now);

        charterRepository.save(entity);

        syncRedisAfterStatusChange(entity, target);

        return PropertyStatusUpdateResponseDto.builder()
                .propertyId(entity.getPropertyId())
                .previousStatus(previous.name())
                .currentStatus(target.name())
                .modifiedAt(now)
                .build();
    }

    // ============================================================
    // Redis 동기화 (private) — 원본 유지
    // ============================================================

    private void syncRedisAfterCreate(PropertyCharterEntity entity) {

        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        String hashKey = "property:charter:" + propertyId;
        Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);
        redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

        redisHandler.redisTemplate.opsForZSet().add(
                "idx:charterPrice:" + districtName, propertyId,
                entity.getDeposit().doubleValue());

        redisHandler.redisTemplate.opsForZSet().add(
                "idx:area:" + districtName + ":전세", propertyId,
                entity.getAreaInPyeong().doubleValue());

        String boundsKey = "bounds:" + districtName + ":전세";
        boundsUpdater.tryExtend(boundsKey, "minPrice", "maxPrice",
                entity.getDeposit().doubleValue(), PRICE_ZERO_DELTA);
        boundsUpdater.tryExtend(boundsKey, "minArea", "maxArea",
                entity.getAreaInPyeong().doubleValue(), AREA_ZERO_DELTA);

        log.info("전세 매물 Redis 동기화(등록) 완료: propertyId={}", propertyId);
    }

    private void syncRedisAfterUpdate(PropertyCharterEntity entity, List<String> changedFields) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        String hashKey = "property:charter:" + propertyId;
        Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);
        redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

        if (changedFields.contains("deposit")) {
            redisHandler.redisTemplate.opsForZSet().add(
                    "idx:charterPrice:" + districtName, propertyId,
                    entity.getDeposit().doubleValue());
            String boundsKey = "bounds:" + districtName + ":전세";
            boundsUpdater.tryExtend(boundsKey, "minPrice", "maxPrice",
                    entity.getDeposit().doubleValue(), PRICE_ZERO_DELTA);
        }

        log.info("전세 매물 Redis 동기화(수정) 완료: propertyId={}, changedFields={}",
                propertyId, changedFields);
    }

    private void syncRedisAfterStatusChange(PropertyCharterEntity entity, PropertyStatus target) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:charterPrice:" + districtName, propertyId);
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:area:" + districtName + ":전세", propertyId);

        String hashKey = "property:charter:" + propertyId;

        if (target == PropertyStatus.COMPLETED) {
            redisHandler.redisTemplate.opsForHash().put(hashKey, "status", "COMPLETED");
            redisHandler.redisTemplate.opsForHash().put(hashKey, "modifiedAt",
                    entity.getModifiedAt().toString());
        } else if (target == PropertyStatus.DELETED) {
            redisHandler.redisTemplate.delete(hashKey);
        }

        log.info("전세 매물 Redis 동기화(상태변경) 완료: propertyId={}, target={}",
                propertyId, target);
    }

    // ============================================================
    // 검증 헬퍼 — 원본 유지
    // ============================================================

    private void verifyOwnership(PropertyCharterEntity entity, String currentUserId) {
        if (entity.getRegisteredUserId() == null) {
            throw new UnauthorizedPropertyAccessException("매물에 대한 수정 권한이 없습니다.");
        }
        if (!entity.getRegisteredUserId().equals(currentUserId)) {
            throw new UnauthorizedPropertyAccessException("매물에 대한 수정 권한이 없습니다.");
        }
    }

    private void verifyActiveForUpdate(PropertyStatus status) {
        if (status != PropertyStatus.ACTIVE) {
            throw new InvalidStateForUpdateException("종료 상태 매물은 수정할 수 없습니다. status=" + status);
        }
    }

    private void verifyTransition(PropertyStatus current, PropertyStatus target) {
        if (current != PropertyStatus.ACTIVE) {
            throw new InvalidStatusTransitionException("상태 전이 불가: " + current + " → " + target);
        }
    }

    // ============================================================
    // 유틸 헬퍼 — 원본 유지
    // ============================================================

    private void validateDistrictCode(String sggCd) {
        if (!SEOUL_DISTRICT_CODES.containsKey(sggCd)) {
            throw new PropertyValidationException("유효하지 않은 서울시 자치구 코드입니다. sggCd=" + sggCd);
        }
    }

    private String resolveDistrictName(String sggCd) { return SEOUL_DISTRICT_CODES.get(sggCd); }
    private BigDecimal toPyeong(BigDecimal ar) { return ar.divide(PYEONG_DIVISOR, 4, RoundingMode.HALF_UP); }
    private String buildAddress(String d, String u, String j) { return "서울특별시 " + d + " " + u + " " + j; }

    private boolean longEqualsInteger(Long entityVal, Integer dtoVal) {
        if (entityVal == null) return false;
        return entityVal.equals(Long.valueOf(dtoVal));
    }

    // ============================================================
    // F008 — Redis 동기화 보상 처리 — 원본 유지
    // ============================================================

    private void executeRedisSyncWithCompensation(PropertyCharterEntity entity) {

        try {
            syncRedisAfterCreate(entity);
            return;
        } catch (RedisConnectionFailureException e) {
            log.warn("[REDIS_SYNC_RETRY] 연결 실패, 재시도 진입: propertyId={}",
                    entity.getPropertyId());
        } catch (QueryTimeoutException e) {
            log.warn("[REDIS_SYNC_RETRY] 타임아웃, 재시도 진입: propertyId={}, cause={}",
                    entity.getPropertyId(), e.getClass().getSimpleName());
        } catch (RedisSystemException e) {
            log.error("[REDIS_SYNC_INFRA] 인프라 장애(OOM 등), 재시도 생략: propertyId={}, msg={}",
                    entity.getPropertyId(), e.getMessage());
            recordSyncFailure(entity.getPropertyId(), e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[REDIS_SYNC_UNEXPECTED] 예상 외 예외: propertyId={}, msg={}",
                    entity.getPropertyId(), e.getMessage());
            recordSyncFailure(entity.getPropertyId(), e.getMessage());
            return;
        }

        try {
            Thread.sleep(200);
            syncRedisAfterCreate(entity);
            log.info("[REDIS_SYNC_RETRY_OK] 재시도 성공: propertyId={}", entity.getPropertyId());
            return;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception retryEx) {
            log.warn("[REDIS_SYNC_RETRY_FAIL] 재시도 실패: propertyId={}", entity.getPropertyId());
        }

        recordSyncFailure(entity.getPropertyId(), "즉시 재시도 1회 실패 후 스케줄러 위임");
    }

    private void recordSyncFailure(String propertyId, String failReason) {

        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            txTemplate.execute(status -> {
                PropertySyncFailure failure = PropertySyncFailure.builder()
                        .propertyId(propertyId)
                        .leaseType("CHARTER")
                        .operationType("CREATE")
                        .failStep("FULL")
                        .failReason(failReason != null && failReason.length() > 500
                                ? failReason.substring(0, 500) : failReason)
                        .failTime(LocalDateTime.now())
                        .retryCount(0)
                        .maxRetries(5)
                        .resolved("N")
                        .build();

                syncFailureRepository.save(failure);
                return null;
            });

            log.warn("[SYNC_FAILURE_RECORDED] propertyId={}", propertyId);

        } catch (Exception e) {
            log.error("[SYNC_FAILURE_RECORD_FAILED] propertyId={}, error={}",
                    propertyId, e.getMessage());
        }
    }

    public void retrySyncForCreate(String propertyId) {
        PropertyCharterEntity entity = charterRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "재시도 대상 매물이 존재하지 않습니다. propertyId=" + propertyId));

        syncRedisAfterCreate(entity);
    }
}
