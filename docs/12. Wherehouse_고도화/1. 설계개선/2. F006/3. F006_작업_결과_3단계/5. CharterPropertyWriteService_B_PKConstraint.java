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
import org.springframework.dao.DataIntegrityViolationException;
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
 * │  F006 3단계 — 대안 B: Oracle PK 유니크 제약 활용 (catch + 변환)  │
 * │                                                                 │
 * │  원본: CharterPropertyWriteService.java                         │
 * │  변경 범위: createProperty() 메서드만 수정                       │
 * │                                                                 │
 * │  핵심 변경:                                                      │
 * │    1. findById를 1차 필터로 유지 — 데이터 출처별 분기 메시지 추가 │
 * │    2. save()를 try-catch로 감싸 DataIntegrityViolationException  │
 * │       을 DuplicatePropertyException으로 변환 (2차 안전망)        │
 * │                                                                 │
 * │  구조적 특성:                                                    │
 * │    - 정합성 보장 책임이 Oracle PK 유니크 제약에 위임됨            │
 * │    - 동일 PK 요청에만 경합, 서로 다른 PK 요청은 병렬 처리        │
 * │    - DB 레벨 보호이므로 다중 인스턴스 배포에서도 동일하게 동작    │
 * │    - @Transactional 유지, afterCommit 콜백도 기존 패턴 그대로    │
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
    // F001 전세 매물 등록 — 대안 B: PK 유니크 제약 활용
    // ============================================================

    /**
     * 대안 B — Oracle PK 유니크 제약 활용 (catch + 변환).
     *
     * SELECT(findById)와 INSERT(save)의 역할을 명확히 분리한다:
     *
     *   1차 필터 (findById):
     *     이미 커밋된 행이 존재하는 경우를 INSERT 없이 빠르게 거부한다.
     *     정합성을 보장하지 않음 — READ COMMITTED 스냅샷 읽기이므로
     *     미커밋 행은 보이지 않는다.
     *     기존 매물의 dataSource에 따라 분기 메시지를 제공한다.
     *
     *   2차 안전망 (save + PK 제약):
     *     Oracle PK 인덱스의 배타적 락이 동일 PK 동시 INSERT를 직렬화한다.
     *     CHECK(PK 존재 확인)와 USE(행 삽입)가 하나의 원자적 연산 안에서
     *     수행되므로 TOCTOU 구조 자체가 존재하지 않는다.
     *     DataIntegrityViolationException을 DuplicatePropertyException으로 변환한다.
     */
    @Transactional
    public PropertyCreateResponseDto createProperty(CharterCreateRequestDto dto, String userId) {

        validateDistrictCode(dto.getSggCd());

        String propertyId = idGenerator.generatePropertyId(
                dto.getSggCd(), dto.getJibun(), dto.getAptNm(),
                String.valueOf(dto.getFloor()), dto.getExcluUseAr().toPlainString());

        // ── 1차 필터: 이미 커밋된 행이 존재하는 경우를 빠르게 거부 ──
        // 정합성 보장 역할이 아님. 대부분의 중복 요청을 INSERT 없이 차단하는 성능 필터.
        Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
        if (existing.isPresent()) {
            PropertyCharterEntity existingEntity = existing.get();
            if (existingEntity.getDataSource() == DataSource.BATCH) {
                throw new DuplicatePropertyException(
                        "이 매물은 국토교통부 실거래 데이터에 이미 등록된 매물입니다. propertyId=" + propertyId);
            }
            throw new DuplicatePropertyException(
                    "동일한 매물이 다른 사용자에 의해 이미 등록되어 있습니다. propertyId=" + propertyId);
        }

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

        // ── 2차 안전망: 동시 등록 레이스 대응 — Oracle PK 제약 활용 ──
        // PK 인덱스의 배타적 락이 동일 PK INSERT를 원자적으로 검증한다.
        // 선착 커밋 후 후착은 ORA-00001 → DataIntegrityViolationException 발생.
        try {
            charterRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePropertyException(
                    "동시 등록 충돌: 동일 매물이 다른 사용자에 의해 먼저 등록되었습니다. propertyId=" + propertyId, e);
        }

        // ── F008: afterCommit 콜백 — 원본 유지 ──
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executeRedisSyncWithCompensation(entity);
                    }
                }
        );

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
