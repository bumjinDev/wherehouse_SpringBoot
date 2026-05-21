package com.wherehouse.PropertyManagement.service;

import com.wherehouse.PropertyManagement.dto.*;
import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyMonthlyEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import com.wherehouse.PropertyManagement.execption.customExceptions.*;
import com.wherehouse.PropertyManagement.integration.BoundsUpdater;
import com.wherehouse.PropertyManagement.integration.PropertyHashBuilder;
import com.wherehouse.PropertyManagement.repository.PropertyMonthlyRegistrationRepository;
import com.wherehouse.VisitReservation.service.VisitReservationWriteService;
import com.wherehouse.recommand.batch.util.IdGenerator;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 월세 매물 쓰기 서비스 — F001 등록 + F002 수정 + F003 상태 변경.
 *
 * PROPERTIES_MONTHLY 테이블 쓰기 + Redis 동기화를 단일 서비스 내에서 처리한다.
 *
 * CharterPropertyWriteService 와의 Redis 동기화 차이:
 *   인덱스 제거 대상: 전세 2개 vs 월세 3개 (deposit + monthlyRent + area)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyPropertyWriteService {


    private final PropertyMonthlyRegistrationRepository monthlyRepository;
    private final RedisHandler redisHandler;
    private final PropertyHashBuilder propertyHashBuilder;
    private final BoundsUpdater boundsUpdater;
    private final IdGenerator idGenerator;

    /* 방문 예약 연동 (설계 명세서 섹션 2.1 매물 상태 변경 연동) — 비활성 전이 시 활성 윈도우 일괄 철회 */
    private final VisitReservationWriteService visitReservationWriteService;

    private static final String LEASE_MONTHLY_CODE = "MONTHLY";
    private static final String LEASE_MONTHLY_KOR = "월세";
    private static final BigDecimal PYEONG_DIVISOR = new BigDecimal("3.305785");
    private static final double DEPOSIT_ZERO_DELTA = 1000.0;
    private static final double MONTHLY_RENT_ZERO_DELTA = 10.0;
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
    // F001 월세 매물 등록
    // ============================================================

    @Transactional
    public PropertyCreateResponseDto createProperty(MonthlyCreateRequestDto dto, String userId) {

        validateDistrictCode(dto.getSggCd());

        String propertyId = idGenerator.generatePropertyId(
                dto.getSggCd(), dto.getJibun(), dto.getAptNm(),
                String.valueOf(dto.getFloor()), dto.getExcluUseAr().toPlainString());

        Optional<PropertyMonthlyEntity> existing = monthlyRepository.findById(propertyId);
        if (existing.isPresent()) {
            throw new DuplicatePropertyException("이미 등록된 월세 매물입니다. propertyId=" + propertyId);
        }

        String districtName = resolveDistrictName(dto.getSggCd());
        BigDecimal areaInPyeong = toPyeong(dto.getExcluUseAr());
        String address = buildAddress(districtName, dto.getUmdNm(), dto.getJibun());
        LocalDateTime now = LocalDateTime.now();

        PropertyMonthlyEntity entity = PropertyMonthlyEntity.builder()
                .propertyId(propertyId)
                .aptNm(dto.getAptNm()).excluUseAr(dto.getExcluUseAr())
                .floor(dto.getFloor()).buildYear(dto.getBuildYear()).dealDate(dto.getDealDate())
                .deposit(Long.valueOf(dto.getDeposit()))
                .monthlyRent(Long.valueOf(dto.getMonthlyRent()))
                .leaseType(LEASE_MONTHLY_KOR)
                .umdNm(dto.getUmdNm()).jibun(dto.getJibun()).sggCd(dto.getSggCd())
                .address(address).areaInPyeong(areaInPyeong).districtName(districtName)
                .dataSource(DataSource.USER).status(PropertyStatus.ACTIVE)
                .registeredUserId(userId).registeredAt(now)
                .build();

        monthlyRepository.save(entity);
        syncRedisAfterCreate(entity);

        return PropertyCreateResponseDto.builder()
                .propertyId(propertyId).leaseType(LEASE_MONTHLY_CODE)
                .dataSource(DataSource.USER.name()).status(PropertyStatus.ACTIVE.name())
                .registeredUserId(userId).registeredAt(now)
                .build();
    }

    // ============================================================
    // F002 월세 매물 수정
    // ============================================================

//    @Transactional    // 테스트 목적
    public PropertyUpdateResponseDto updateProperty(
            String propertyId, MonthlyUpdateRequestDto dto, String userId) {

        PropertyMonthlyEntity entity = monthlyRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId));


        // 방문 예약 도입에 따른 정책 강화: 매물 수정은 등록자 본인만 가능.
        // 등록자가 매물을 관리해야 그 매물의 방문 윈도우/슬롯/예약 책임 주체가 일관된다.
        verifyOwnership(entity, userId);
        verifyActiveForUpdate(entity.getStatus());

        List<String> changedFields = new ArrayList<>();

        if (dto.getDeposit() != null && !longEqualsInteger(entity.getDeposit(), dto.getDeposit())) {
            entity.setDeposit(Long.valueOf(dto.getDeposit()));
            changedFields.add("deposit");
        }
        if (dto.getMonthlyRent() != null && !longEqualsInteger(entity.getMonthlyRent(), dto.getMonthlyRent())) {
            entity.setMonthlyRent(Long.valueOf(dto.getMonthlyRent()));
            changedFields.add("monthlyRent");
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
        monthlyRepository.save(entity);
        syncRedisAfterUpdate(entity, changedFields);

        return PropertyUpdateResponseDto.builder()
                .propertyId(entity.getPropertyId())
                .modifiedAt(now)
                .changedFields(Collections.unmodifiableList(changedFields))
                .build();
    }

    // ============================================================
    // F003 월세 매물 상태 변경
    // ============================================================

    /**
     * 월세 매물 상태 변경.
     *
     * CharterPropertyWriteService.changeStatus 와의 차이:
     *   인덱스 제거 대상이 3개 (idx:deposit + idx:monthlyRent + idx:area).
     *   전세는 2개 (idx:charterPrice + idx:area).
     */
//    @Transactional
    public PropertyStatusUpdateResponseDto changeStatus(
            String propertyId, PropertyStatusUpdateRequestDto dto, String userId) {

        // 1. 매물 조회
        PropertyMonthlyEntity entity = monthlyRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED)
                .orElseThrow(() -> new PropertyNotFoundException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId));

        // 2. 권한 검증 : 매물 등록자인지 확인.
        verifyOwnership(entity, userId);

        // 3. 상태 전이 허용성 검증 : ACTIVE -> COMPLETED, ACTIVE -> DELETED
        PropertyStatus previous = entity.getStatus();

        // 요청 당시와 현재 확인 시점의 매물 올바른지 확인 - 설계 고려사항으로 현재 메소드 대부분을 락 걸것인지 혹은 이렇게 구조 변경해서 락 걸것인지..
        PropertyStatus target = PropertyStatus.valueOf(dto.getTargetStatus());

        verifyTransition(previous, target);

        // 4. STATUS + MODIFIED_AT 갱신
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(target);
        entity.setModifiedAt(now);

        // 5. RDB 저장
        monthlyRepository.save(entity);

        // 5b. 방문 예약 연동 (설계 명세서 섹션 2.1) — ACTIVE → COMPLETED/DELETED 전이 시
        //     해당 매물의 활성 윈도우를 일괄 철회하고 영향받은 탐색자에게 PROPERTY_DEACTIVATED 통지.
        //     활성 윈도우가 없으면 내부에서 즉시 종료되며 부작용 없음.
        visitReservationWriteService.withdrawAllActiveWindowsForProperty(entity.getPropertyId());

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

    /** F001 등록 후: Hash 생성 + 인덱스 3개 추가 + bounds 경계 확장. */
    private void syncRedisAfterCreate(PropertyMonthlyEntity entity) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        String hashKey = "property:monthly:" + propertyId;
        Map<String, Object> hashFields = propertyHashBuilder.buildMonthlyHash(entity);
        redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

        redisHandler.redisTemplate.opsForZSet().add(
                "idx:deposit:" + districtName, propertyId,
                entity.getDeposit().doubleValue());
        redisHandler.redisTemplate.opsForZSet().add(
                "idx:monthlyRent:" + districtName + ":월세", propertyId,
                entity.getMonthlyRent().doubleValue());
        redisHandler.redisTemplate.opsForZSet().add(
                "idx:area:" + districtName + ":월세", propertyId,
                entity.getAreaInPyeong().doubleValue());

        String boundsKey = "bounds:" + districtName + ":월세";
        boundsUpdater.tryExtend(boundsKey, "minDeposit", "maxDeposit",
                entity.getDeposit().doubleValue(), DEPOSIT_ZERO_DELTA);
        boundsUpdater.tryExtend(boundsKey, "minMonthlyRent", "maxMonthlyRent",
                entity.getMonthlyRent().doubleValue(), MONTHLY_RENT_ZERO_DELTA);
        boundsUpdater.tryExtend(boundsKey, "minArea", "maxArea",
                entity.getAreaInPyeong().doubleValue(), AREA_ZERO_DELTA);

        log.info("월세 매물 Redis 동기화(등록) 완료: propertyId={}", propertyId);
    }

    /** F002 수정 후: Hash 덮어쓰기 + deposit/monthlyRent 변경 시 인덱스 Score 갱신 + bounds 확장. */
    private void syncRedisAfterUpdate(PropertyMonthlyEntity entity, List<String> changedFields) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        String hashKey = "property:monthly:" + propertyId;
        Map<String, Object> hashFields = propertyHashBuilder.buildMonthlyHash(entity);
        redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

        String boundsKey = "bounds:" + districtName + ":월세";

        if (changedFields.contains("deposit")) {
            redisHandler.redisTemplate.opsForZSet().add(
                    "idx:deposit:" + districtName, propertyId,
                    entity.getDeposit().doubleValue());
            boundsUpdater.tryExtend(boundsKey, "minDeposit", "maxDeposit",
                    entity.getDeposit().doubleValue(), DEPOSIT_ZERO_DELTA);
        }
        if (changedFields.contains("monthlyRent")) {
            redisHandler.redisTemplate.opsForZSet().add(
                    "idx:monthlyRent:" + districtName + ":월세", propertyId,
                    entity.getMonthlyRent().doubleValue());
            boundsUpdater.tryExtend(boundsKey, "minMonthlyRent", "maxMonthlyRent",
                    entity.getMonthlyRent().doubleValue(), MONTHLY_RENT_ZERO_DELTA);
        }

        log.info("월세 매물 Redis 동기화(수정) 완료: propertyId={}, changedFields={}",
                propertyId, changedFields);
    }

    /**
     * F003 상태 변경 후: 인덱스 Member 제거 + 상태별 Hash 처리.
     *
     * 인덱스 제거 대상 (월세, 3개) : 기존 주거지 추천 서비스 로직 내 포함되면 안되기 때문
     *   idx:deposit:{district}            — 보증금 인덱스
     *   idx:monthlyRent:{district}:월세   — 월세금 인덱스
     *   idx:area:{district}:월세          — 평수 인덱스
     *
     * Hash 처리:
     *   COMPLETED — Hash 유지, status·modifiedAt 필드만 갱신  : 단순 매물 목록 조회에는 표현
     *   DELETED   — Hash 전면 제거 : 단순 매물 목록 조회 또한 표현 되면 안됨.
     */
    private void syncRedisAfterStatusChange(PropertyMonthlyEntity entity, PropertyStatus target) {
        String propertyId = entity.getPropertyId();
        String districtName = entity.getDistrictName();

        // 인덱스 Member 제거 (COMPLETED·DELETED 공통, 3개)
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:deposit:" + districtName, propertyId);
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:monthlyRent:" + districtName + ":월세", propertyId);
        redisHandler.redisTemplate.opsForZSet().remove(
                "idx:area:" + districtName + ":월세", propertyId);

        // 상태별 Hash 처리 분기
        String hashKey = "property:monthly:" + propertyId;

        if (target == PropertyStatus.COMPLETED) {
            redisHandler.redisTemplate.opsForHash().put(hashKey, "status", "COMPLETED");
            redisHandler.redisTemplate.opsForHash().put(hashKey, "modifiedAt",
                    entity.getModifiedAt().toString());
        } else if (target == PropertyStatus.DELETED) {
            redisHandler.redisTemplate.delete(hashKey);
        }

        log.info("월세 매물 Redis 동기화(상태변경) 완료: propertyId={}, target={}",
                propertyId, target);
    }

    // ============================================================
    // 검증 헬퍼
    // ============================================================

    private void verifyOwnership(PropertyMonthlyEntity entity, String currentUserId) {
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
}