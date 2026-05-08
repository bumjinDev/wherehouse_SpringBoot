package com.wherehouse.recommand.batch.BatchScheduler;

import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.recommand.batch.dto.Property;
import com.wherehouse.recommand.batch.entity.PropertyCharter;
import com.wherehouse.recommand.batch.entity.PropertyMonthly;
import com.wherehouse.recommand.batch.repository.PropertyCharterRepository;
import com.wherehouse.recommand.batch.repository.PropertyMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 배치-사용자 데이터 충돌 처리 프로세서 (F007)
 *
 * 배치 매물 적재 시 동일 MD5 해시(propertyId)를 가진 기존 레코드의 DATA_SOURCE를 확인하여:
 * - BATCH → 기존 배치 동작 그대로 UPSERT (무조건 덮어쓰기)
 * - USER  → 임계값 기반 하이브리드 머지 정책 적용
 * - MERGED → 이전 머지 결과 위에 재머지 (배치 데이터 기준 갱신)
 *
 * 머지 정책:
 * - 가격 차이율 = |배치가격 - 사용자가격| / 사용자가격 × 100
 * - 임계값 이내 → 최신 데이터(타임스탬프 비교) 메인 가격 채택
 * - 임계값 초과 → 국토부 데이터(배치) 메인 가격 채택
 * - 사용자 원본 가격은 USER_PROPOSED_DEPOSIT / USER_PROPOSED_MONTHLY_RENT에 보존
 * - DATA_SOURCE = MERGED로 전환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchMergeProcessor {

    private final PropertyCharterRepository charterRepository;
    private final PropertyMonthlyRepository monthlyRepository;

    @Value("${batch.merge.price-diff-threshold-percent:20}")
    private double thresholdPercent;

    /**
     * 전세 매물 머지 처리 후 저장
     */
    public void saveCharterWithMerge(List<Property> batchProperties) {
        if (batchProperties == null || batchProperties.isEmpty()) return;

        int insertCount = 0;
        int upsertCount = 0;
        int mergeCount = 0;

        for (Property batchProp : batchProperties) {
            String propertyId = batchProp.getPropertyId();
            Optional<PropertyCharter> existingOpt = charterRepository.findById(propertyId);

            if (existingOpt.isEmpty()) {
                charterRepository.save(PropertyCharter.from(batchProp));
                insertCount++;
                continue;
            }

            PropertyCharter existing = existingOpt.get();

            if (existing.getDataSource() == DataSource.BATCH) {
                charterRepository.save(PropertyCharter.from(batchProp));
                upsertCount++;
                continue;
            }

            // DATA_SOURCE = USER 또는 MERGED → 머지 처리
            mergeCharterProperty(existing, batchProp);
            charterRepository.save(existing);
            mergeCount++;

            log.info("[F007:MERGE:CHARTER] propertyId={}, diffRate={}%, result=MERGED",
                    propertyId, String.format("%.1f", calculateDiffRate(
                            batchProp.getDeposit(),
                            existing.getDeposit())));
        }

        log.info("[F007:CHARTER] 처리 완료 — INSERT: {}건, UPSERT(BATCH): {}건, MERGE(USER): {}건",
                insertCount, upsertCount, mergeCount);
    }

    /**
     * 월세 매물 머지 처리 후 저장
     */
    public void saveMonthlyWithMerge(List<Property> batchProperties) {
        if (batchProperties == null || batchProperties.isEmpty()) return;

        int insertCount = 0;
        int upsertCount = 0;
        int mergeCount = 0;

        for (Property batchProp : batchProperties) {
            String propertyId = batchProp.getPropertyId();
            Optional<PropertyMonthly> existingOpt = monthlyRepository.findById(propertyId);

            if (existingOpt.isEmpty()) {
                monthlyRepository.save(PropertyMonthly.from(batchProp));
                insertCount++;
                continue;
            }

            PropertyMonthly existing = existingOpt.get();

            if (existing.getDataSource() == DataSource.BATCH) {
                monthlyRepository.save(PropertyMonthly.from(batchProp));
                upsertCount++;
                continue;
            }

            mergeMonthlyProperty(existing, batchProp);
            monthlyRepository.save(existing);
            mergeCount++;

            log.info("[F007:MERGE:MONTHLY] propertyId={}, depositDiffRate={}%, result=MERGED",
                    propertyId, String.format("%.1f", calculateDiffRate(
                            batchProp.getDeposit(),
                            existing.getDeposit())));
        }

        log.info("[F007:MONTHLY] 처리 완료 — INSERT: {}건, UPSERT(BATCH): {}건, MERGE(USER): {}건",
                insertCount, upsertCount, mergeCount);
    }

    // =================================================================================
    // 머지 핵심 로직
    // =================================================================================

    private void mergeCharterProperty(PropertyCharter existing, Property batchProp) {
        Integer userDeposit = existing.getDeposit();
        Integer batchDeposit = batchProp.getDeposit();

        // 사용자 원본 가격 보존
        existing.setUserProposedDeposit(userDeposit != null ? userDeposit.longValue() : null);

        // 메인 가격 결정
        existing.setDeposit(resolvePrice(batchDeposit, userDeposit, existing.getDealDate(), batchProp.getDealDate()));

        // 배치 데이터로 갱신할 필드
        existing.setDealDate(batchProp.getDealDate());
        existing.setLastUpdated(LocalDateTime.now());
        existing.setDataSource(DataSource.MERGED);
        existing.setModifiedAt(LocalDateTime.now());
    }

    private void mergeMonthlyProperty(PropertyMonthly existing, Property batchProp) {
        Integer userDeposit = existing.getDeposit();
        Integer batchDeposit = batchProp.getDeposit();
        Integer userMonthlyRent = existing.getMonthlyRent();
        Integer batchMonthlyRent = batchProp.getMonthlyRent();

        // 사용자 원본 가격 보존
        existing.setUserProposedDeposit(userDeposit != null ? userDeposit.longValue() : null);
        existing.setUserProposedMonthlyRent(userMonthlyRent != null ? userMonthlyRent.longValue() : null);

        // 메인 가격 결정 (보증금)
        existing.setDeposit(resolvePrice(batchDeposit, userDeposit, existing.getDealDate(), batchProp.getDealDate()));

        // 메인 가격 결정 (월세금)
        existing.setMonthlyRent(resolvePrice(batchMonthlyRent, userMonthlyRent, existing.getDealDate(), batchProp.getDealDate()));

        // 배치 데이터로 갱신할 필드
        existing.setDealDate(batchProp.getDealDate());
        existing.setLastUpdated(LocalDateTime.now());
        existing.setDataSource(DataSource.MERGED);
        existing.setModifiedAt(LocalDateTime.now());
    }

    /**
     * 임계값 기반 가격 결정
     *
     * - 임계값 이내 → 최신 데이터(dealDate 비교) 채택
     * - 임계값 초과 → 국토부 데이터(배치) 채택 (신뢰성 우선)
     */
    private Integer resolvePrice(Integer batchPrice, Integer userPrice,
                                 String existingDealDate, String batchDealDate) {
        if (userPrice == null || userPrice == 0) return batchPrice;
        if (batchPrice == null) return userPrice;

        double diffRate = calculateDiffRate(batchPrice, userPrice);

        if (diffRate <= thresholdPercent) {
            // 임계값 이내 → 최신 데이터 채택
            if (batchDealDate != null && existingDealDate != null
                    && batchDealDate.compareTo(existingDealDate) >= 0) {
                return batchPrice;
            }
            return userPrice;
        }

        // 임계값 초과 → 국토부(배치) 데이터 채택
        return batchPrice;
    }

    private double calculateDiffRate(Integer batchPrice, Integer userPrice) {
        if (userPrice == null || userPrice == 0) return 0.0;
        return Math.abs(batchPrice - userPrice) / (double) userPrice * 100.0;
    }
}
