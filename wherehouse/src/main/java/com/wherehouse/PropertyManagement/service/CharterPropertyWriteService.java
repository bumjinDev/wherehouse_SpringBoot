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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 전세 매물 쓰기 서비스 — F001 등록 + F002 수정 + F003 상태 변경.
 *
 * PROPERTIES_CHARTER 테이블 쓰기 + Redis 동기화를 단일 서비스 내에서 처리한다.
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
    // F001 전세 매물 등록
    // ============================================================

    @Transactional
    public PropertyCreateResponseDto createProperty(CharterCreateRequestDto dto, String userId) {

        /* 자치구 25 개 명칭 중 올바른 지역구 이름 목록 내 포함되는 지역구 명칭 사용했는지 검사. */
        validateDistrictCode(dto.getSggCd());

        /* 현재 요청한 매물에 대한 매물 ID 생성 : md5 해쉬 결과이며, 시군구코드, 지번, 매물명, 층수, 평수 5가지 조합. */
        String propertyId = idGenerator.generatePropertyId(
                dto.getSggCd(), dto.getJibun(), dto.getAptNm(),
                String.valueOf(dto.getFloor()), dto.getExcluUseAr().toPlainString());

        /* 매물 ID 를 조회하여 전세 매물들 중 중복 매물 여부 확인. */
        Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
        if (existing.isPresent()) {
            throw new DuplicatePropertyException("이미 등록된 전세 매물입니다. propertyId=" + propertyId);
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

        charterRepository.save(entity);
//        syncRedisAfterCreate(entity); // 수정 전

        // ── F008: syncRedisAfterCreate 직접 호출 → afterCommit 콜백 등록으로 교체 ──
        // RDB 커밋 확정 후에만 Redis 동기화를 실행하여 유령 데이터를 구조적으로 차단한다.
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
    // F002 전세 매물 수정
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
    // F003 전세 매물 상태 변경
    // ============================================================

    /**
     * 전세 매물 상태 변경.
     *
     * 처리 순서:
     *   1. 매물 조회 — DELETED 는 미존재 동일 취급 (설계 섹션 6.4)
     *   2. 권한 검증 3단계 (설계 섹션 9.2.3, F002 공유)
     *   3. 상태 전이 허용성 검증 (설계 섹션 6.2, 9.3.4)
     *   4. STATUS 컬럼 갱신 + MODIFIED_AT 갱신
     *   5. RDB 저장
     *   6. Redis 동기화 — 상태별 분기 (설계 섹션 9.3.5)
     *      COMPLETED: 인덱스 Member 제거 + Hash status 필드만 갱신
     *      DELETED:   인덱스 Member 제거 + Hash 전면 제거
     *   7. 응답 반환
     */
//    @Transactional    // 테스트 목적 주석
    public PropertyStatusUpdateResponseDto changeStatus(
            String propertyId, PropertyStatusUpdateRequestDto dto, String userId) {

        // 1. 매물 조회
        PropertyCharterEntity entity = charterRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId));

        // 2. 권한 검증 3단계
        verifyOwnership(entity, userId);

        /* 3. 상태 전이 허용성 검증 : 요청 시점의 상태와 실제 갱신 당시의 상태를 비교 검사
            당연히 이것만으로는 동시성을 보장할 수 없으나, 이 로직을 사용하면 전체를 락 안 걸고 이 갱신 부분만 락 거는 지 여부 등
            피드백 따른 설계 지점으로 불 수 있을듯?
        *
        * */
        PropertyStatus previous = entity.getStatus();   // PropertyStatus : Eume 객체
        PropertyStatus target = PropertyStatus.valueOf(dto.getTargetStatus());
        
        // 상태 검증
        verifyTransition(previous, target);

        // 4. STATUS + MODIFIED_AT 갱신
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(target);
        entity.setModifiedAt(now);

        // 5. RDB 저장
        charterRepository.save(entity);

        // 6. Redis 동기화
        syncRedisAfterStatusChange(entity, target);

        // 7. 응답 반환
        return PropertyStatusUpdateResponseDto.builder()
                .propertyId(entity.getPropertyId())
                .previousStatus(previous.name())
                .currentStatus(target.name())
                .modifiedAt(now)
                .build();
    }

    // ============================================================
    // Redis 동기화 (private)
    // ============================================================

    /** F001 등록 후: Hash 생성 + 인덱스 2개 추가 + bounds 경계 확장. */
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

    /** F002 수정 후: Hash 덮어쓰기 + deposit 변경 시 인덱스 Score 갱신 + bounds 확장. */
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

    /**
     * F003 상태 변경 후: 인덱스 Member 제거 + 상태별 Hash 처리.
     *
     * 설계 섹션 9.3.5, 6.4:
     *   COMPLETED — 인덱스(Sorted Set) Member 제거 + Hash 유지하되 status 필드만 갱신.
     *              리뷰 게시판이 매물 식별자로 매물 상세를 역조회하는 시나리오 대응.
     *   DELETED   — 인덱스 Member 제거 + Hash 전면 제거.
     *              등록자 의사에 의한 제거이므로 매물 정보가 조회되지 않아야 함.
     *
     * 인덱스 제거 대상 (전세):
     *   idx:charterPrice:{district}  — 전세금 인덱스
     *   idx:area:{district}:전세     — 평수 인덱스
     */
    private void syncRedisAfterStatusChange(PropertyCharterEntity entity, PropertyStatus target) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        // 인덱스 Member 제거 (COMPLETED·DELETED 공통)
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:charterPrice:" + districtName, propertyId);
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:area:" + districtName + ":전세", propertyId);

        // 상태별 Hash 처리 분기
        String hashKey = "property:charter:" + propertyId;

        if (target == PropertyStatus.COMPLETED) {
            // Hash 유지, status·modifiedAt 필드만 갱신
            redisHandler.redisTemplate.opsForHash().put(hashKey, "status", "COMPLETED");
            redisHandler.redisTemplate.opsForHash().put(hashKey, "modifiedAt",
                    entity.getModifiedAt().toString());
        } else if (target == PropertyStatus.DELETED) {
            // Hash 전면 제거
            redisHandler.redisTemplate.delete(hashKey);
        }

        log.info("전세 매물 Redis 동기화(상태변경) 완료: propertyId={}, target={}",
                propertyId, target);
    }

    // ============================================================
    // 검증 헬퍼
    // ============================================================

    /** 권한 검증 2·3단계 (설계 섹션 9.2.3). 1단계 DELETED 필터링은 호출자가 처리. */
    private void verifyOwnership(PropertyCharterEntity entity, String currentUserId) {
        if (entity.getRegisteredUserId() == null) {
            throw new UnauthorizedPropertyAccessException("매물에 대한 수정 권한이 없습니다.");
        }
        if (!entity.getRegisteredUserId().equals(currentUserId)) {
            throw new UnauthorizedPropertyAccessException("매물에 대한 수정 권한이 없습니다.");
        }
    }

    /** F002 전용. 종료 상태 수정 차단 — E4106. */
    private void verifyActiveForUpdate(PropertyStatus status) {
        if (status != PropertyStatus.ACTIVE) {
            throw new InvalidStateForUpdateException("종료 상태 매물은 수정할 수 없습니다. status=" + status);
        }
    }

    /** F003 전용. 상태 전이 허용성 검증 (설계 섹션 6.2) — E4002. */
    private void verifyTransition(PropertyStatus current, PropertyStatus target) {
        if (current != PropertyStatus.ACTIVE) {
            throw new InvalidStatusTransitionException("상태 전이 불가: " + current + " → " + target);
        }
    }

    // ============================================================
    // 유틸 헬퍼
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
    // F008 — Redis 동기화 보상 처리
    // ============================================================

    /**
     * Redis 동기화 + 예외 분류 + 즉시 재시도 + 실패 기록.
     * afterCommit 콜백 내부에서 호출된다.
     */
    private void executeRedisSyncWithCompensation(PropertyCharterEntity entity) {

        // 1차 시도
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

        // 2차 시도 (재시도 1회)
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

        // 재시도도 실패 → 실패 테이블 기록, 스케줄러 위임
        recordSyncFailure(entity.getPropertyId(), "즉시 재시도 1회 실패 후 스케줄러 위임");
    }

    /**
     * Redis 동기화 실패를 PROPERTY_SYNC_FAILURES 테이블에 기록.
     */
    private void recordSyncFailure(String propertyId, String failReason) {

        try {
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
            log.warn("[SYNC_FAILURE_RECORDED] propertyId={}", propertyId);

        } catch (Exception e) {
            log.error("[SYNC_FAILURE_RECORD_FAILED] propertyId={}, error={}",
                    propertyId, e.getMessage());
        }
    }

    /**
     * 스케줄러(PropertySyncRetryScheduler) 호출용 public 래퍼.
     */
    public void retrySyncForCreate(String propertyId) {
        PropertyCharterEntity entity = charterRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "재시도 대상 매물이 존재하지 않습니다. propertyId=" + propertyId));

        syncRedisAfterCreate(entity);
    }
}