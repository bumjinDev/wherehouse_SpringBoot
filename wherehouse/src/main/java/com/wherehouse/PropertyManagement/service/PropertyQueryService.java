package com.wherehouse.PropertyManagement.service;

import com.wherehouse.PropertyManagement.dto.PropertyDetailDto;
import com.wherehouse.PropertyManagement.dto.PropertyListRequestDto;
import com.wherehouse.PropertyManagement.dto.PropertyListResponseDto;
import com.wherehouse.PropertyManagement.dto.PropertySummaryDto;
import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyCharterEntity;
import com.wherehouse.PropertyManagement.entity.PropertyMonthlyEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import com.wherehouse.PropertyManagement.execption.customExceptions.PropertyNotFoundException;
import com.wherehouse.PropertyManagement.repository.PropertyCharterRegistrationRepository;
import com.wherehouse.PropertyManagement.repository.PropertyMonthlyRegistrationRepository;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 매물 조회 서비스 — F004 매물 목록 조회 + 매물 상세 조회.
 *
 * Oracle RDB 직접 조회 (설계 섹션 5.3). Redis 미경유.
 *
 * 상세 조회 — 양쪽 테이블 동시 조회:
 *   MD5 해시 식별자 생성 입력에 leaseType 이 포함되지 않으므로 (설계 섹션 9.1.1),
 *   동일 propertyId 가 PROPERTIES_CHARTER 와 PROPERTIES_MONTHLY 양쪽에 존재할 수 있다.
 *   따라서 양쪽 테이블을 모두 조회하여 DELETED 가 아닌 유효 레코드 전부를 반환한다.
 *   최대 2건(전세 1건 + 월세 1건)이 응답 배열에 포함될 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyQueryService {

    private final PropertyCharterRegistrationRepository charterRepository;
    private final PropertyMonthlyRegistrationRepository monthlyRepository;
    private final ReviewStatisticsRepository reviewStatisticsRepository;

    // ============================================================
    // F004 매물 목록 조회
    // ============================================================

    public PropertyListResponseDto getProperties(PropertyListRequestDto request) {

        String leaseType = normalize(request.getLeaseType());
        String district = normalize(request.getDistrict());
        String statusStr = normalize(request.getStatus());
        String dataSourceStr = normalize(request.getDataSource());
        String keyword = normalize(request.getKeyword());
        String sort = request.getSort();
        int page = request.getPage();
        int size = request.getSize();

        PropertyStatus status = (statusStr != null) ? PropertyStatus.valueOf(statusStr) : null;
        DataSource dataSource = (dataSourceStr != null) ? DataSource.valueOf(dataSourceStr) : null;

        if ("CHARTER".equals(leaseType)) {
            return queryCharter(status, dataSource, district, keyword, sort, page, size);
        }
        if ("MONTHLY".equals(leaseType)) {
            return queryMonthly(status, dataSource, district, keyword, sort, page, size);
        }
        return queryMerged(status, dataSource, district, keyword, sort, page, size);
    }

    // ============================================================
    // F004 매물 상세 조회 (설계 섹션 7.6, 9.4.2)
    // ============================================================

    /**
     * 매물 상세 조회 — 양쪽 테이블 동시 조회.
     *
     * MD5 해시에 leaseType 이 포함되지 않으므로 동일 propertyId 가
     * 전세·월세 양쪽 테이블에 각각 존재할 수 있다.
     * 양쪽을 모두 조회하여 DELETED 가 아닌 유효 레코드를 전부 수집한다.
     *
     * 반환값: 최소 1건, 최대 2건의 PropertyDetailDto 배열.
     * 양쪽 모두 미존재 또는 DELETED 이면 E4201.
     *
     * 리뷰 통계는 propertyId 기준으로 REVIEW_STATISTICS 테이블에서 1회 조회 후
     * 양쪽 Detail DTO 에 동일하게 주입한다.
     */
    public List<PropertyDetailDto> getPropertyDetail(String propertyId) {

        // 양쪽 테이블 동시 조회 + DELETED 필터링
        Optional<PropertyCharterEntity> charterOpt = charterRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED);

        Optional<PropertyMonthlyEntity> monthlyOpt = monthlyRepository.findById(propertyId)
                .filter(e -> e.getStatus() != PropertyStatus.DELETED);

        // 양쪽 모두 미존재 또는 DELETED → E4201
        if (charterOpt.isEmpty() && monthlyOpt.isEmpty()) {
            throw new PropertyNotFoundException("매물을 찾을 수 없습니다. propertyId=" + propertyId);
        }

        // 리뷰 통계 1회 조회 (propertyId 기준, 리뷰 통계 데이터는 양쪽 공유)
        ReviewStatistics stats = reviewStatisticsRepository.findById(propertyId).orElse(null);
        int reviewCount = (stats != null) ? stats.getReviewCount() : 0;
        BigDecimal avgRating = (stats != null) ? stats.getAvgRating() : BigDecimal.ZERO;

        // 유효 레코드 수집
        List<PropertyDetailDto> results = new ArrayList<>();

        charterOpt.ifPresent(entity -> results.add(
                buildCharterDetail(entity, reviewCount, avgRating)));

        monthlyOpt.ifPresent(entity -> results.add(
                buildMonthlyDetail(entity, reviewCount, avgRating)));

        return results;
    }

    // ============================================================
    // 상세 조회 — Detail DTO 조립
    // ============================================================

    private PropertyDetailDto buildCharterDetail(
            PropertyCharterEntity entity, int reviewCount, BigDecimal avgRating) {

        return PropertyDetailDto.builder()
                .summary(toCharterSummary(entity))
                .umdNm(entity.getUmdNm())
                .jibun(entity.getJibun())
                .sggCd(entity.getSggCd())
                .dealDate(entity.getDealDate())
//                .dealDate(parseDealDate(entity.getDealDate()))
                .registeredUserId(entity.getRegisteredUserId())
                .modifiedAt(entity.getModifiedAt())
                .reviewCount(reviewCount)
                .avgRating(avgRating)
                .build();
    }

    private PropertyDetailDto buildMonthlyDetail(
            PropertyMonthlyEntity entity, int reviewCount, BigDecimal avgRating) {

        return PropertyDetailDto.builder()
                .summary(toMonthlySummary(entity))
                .umdNm(entity.getUmdNm())
                .jibun(entity.getJibun())
                .sggCd(entity.getSggCd())
                .dealDate(entity.getDealDate())
//                .dealDate(parseDealDate(entity.getDealDate()))
                .registeredUserId(entity.getRegisteredUserId())
                .modifiedAt(entity.getModifiedAt())
                .reviewCount(reviewCount)
                .avgRating(avgRating)
                .build();
    }

    // ============================================================
    // 목록 조회 — 단일 테이블
    // ============================================================

    private PropertyListResponseDto queryCharter(
            PropertyStatus status, DataSource dataSource, String district,
            String keyword, String sort, int page, int size) {

        Page<PropertyCharterEntity> result;

        if ("latest".equals(sort)) {
            result = charterRepository.findByFiltersOrderByLatest(
                    status, dataSource, district, keyword,
                    PageRequest.of(page, size, Sort.unsorted()));
        } else {
            Pageable pageable = buildPageable(sort, page, size);
            result = charterRepository.findByFilters(
                    status, dataSource, district, keyword, pageable);
        }

        List<PropertySummaryDto> summaries = result.getContent().stream()
                .map(this::toCharterSummary)
                .toList();

        return buildResponse(summaries, result.getTotalElements(), result.getTotalPages(), page, size);
    }

    private PropertyListResponseDto queryMonthly(
            PropertyStatus status, DataSource dataSource, String district,
            String keyword, String sort, int page, int size) {

        Page<PropertyMonthlyEntity> result;

        if ("latest".equals(sort)) {
            result = monthlyRepository.findByFiltersOrderByLatest(
                    status, dataSource, district, keyword,
                    PageRequest.of(page, size, Sort.unsorted()));
        } else {
            Pageable pageable = buildPageable(sort, page, size);
            result = monthlyRepository.findByFilters(
                    status, dataSource, district, keyword, pageable);
        }

        List<PropertySummaryDto> summaries = result.getContent().stream()
                .map(this::toMonthlySummary)
                .toList();

        return buildResponse(summaries, result.getTotalElements(), result.getTotalPages(), page, size);
    }

    // ============================================================
    // 목록 조회 — 양쪽 병합
    // ============================================================

    private PropertyListResponseDto queryMerged(
            PropertyStatus status, DataSource dataSource, String district,
            String keyword, String sort, int page, int size) {

        int fetchLimit = 10000;
        Pageable fetchAll = PageRequest.of(0, fetchLimit, Sort.unsorted());

        Page<PropertyCharterEntity> charterPage = charterRepository.findByFilters(
                status, dataSource, district, keyword, fetchAll);
        Page<PropertyMonthlyEntity> monthlyPage = monthlyRepository.findByFilters(
                status, dataSource, district, keyword, fetchAll);

        List<PropertySummaryDto> merged = new ArrayList<>();
        charterPage.getContent().forEach(e -> merged.add(toCharterSummary(e)));
        monthlyPage.getContent().forEach(e -> merged.add(toMonthlySummary(e)));

        Comparator<PropertySummaryDto> comparator = resolveComparator(sort);
        merged.sort(comparator);

        long totalElements = merged.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, merged.size());
        int toIndex = Math.min(fromIndex + size, merged.size());
        List<PropertySummaryDto> pageContent = merged.subList(fromIndex, toIndex);

        return buildResponse(pageContent, totalElements, totalPages, page, size);
    }

    // ============================================================
    // Entity → Summary 매핑
    // ============================================================

    private PropertySummaryDto toCharterSummary(PropertyCharterEntity e) {
        return PropertySummaryDto.builder()
                .propertyId(e.getPropertyId())
                .leaseType("CHARTER")
                .aptNm(e.getAptNm())
                .districtName(e.getDistrictName())
                .address(e.getAddress())
                .floor(e.getFloor())
                .excluUseAr(e.getExcluUseAr())
                .areaInPyeong(e.getAreaInPyeong())
                .deposit(e.getDeposit() != null ? e.getDeposit().intValue() : null)
                .monthlyRent(null)
                .buildYear(e.getBuildYear())
                .dataSource(e.getDataSource() != null ? e.getDataSource().name() : null)
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .registeredUserId(e.getRegisteredUserId())
                .registeredAt(e.getRegisteredAt())
                .lastUpdated(resolveLastUpdated(e.getModifiedAt(), e.getRegisteredAt(), e.getLastUpdated()))
                .build();
    }

    private PropertySummaryDto toMonthlySummary(PropertyMonthlyEntity e) {
        return PropertySummaryDto.builder()
                .propertyId(e.getPropertyId())
                .leaseType("MONTHLY")
                .aptNm(e.getAptNm())
                .districtName(e.getDistrictName())
                .address(e.getAddress())
                .floor(e.getFloor())
                .excluUseAr(e.getExcluUseAr())
                .areaInPyeong(e.getAreaInPyeong())
                .deposit(e.getDeposit() != null ? e.getDeposit().intValue() : null)
                .monthlyRent(e.getMonthlyRent() != null ? e.getMonthlyRent().intValue() : null)
                .buildYear(e.getBuildYear())
                .dataSource(e.getDataSource() != null ? e.getDataSource().name() : null)
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .registeredUserId(e.getRegisteredUserId())
                .registeredAt(e.getRegisteredAt())
                .lastUpdated(resolveLastUpdated(e.getModifiedAt(), e.getRegisteredAt(), e.getLastUpdated()))
                .build();
    }

    // ============================================================
    // 내부 헬퍼
    // ============================================================

    private LocalDateTime resolveLastUpdated(
            LocalDateTime modifiedAt, LocalDateTime registeredAt, LocalDateTime lastUpdated) {
        if (modifiedAt != null) return modifiedAt;
        if (registeredAt != null) return registeredAt;
        return lastUpdated;
    }

    private LocalDate parseDealDate(String dealDate) {
        if (dealDate == null || dealDate.isBlank()) return null;
        try {
            return LocalDate.parse(dealDate);
        } catch (DateTimeParseException e) {
            log.warn("dealDate 파싱 실패: value={}", dealDate);
            return null;
        }
    }

    private Pageable buildPageable(String sort, int page, int size) {
        Sort springSort = switch (sort) {
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "deposit");
            case "priceAsc"  -> Sort.by(Sort.Direction.ASC, "deposit");
            case "areaDesc"  -> Sort.by(Sort.Direction.DESC, "excluUseAr");
            case "areaAsc"   -> Sort.by(Sort.Direction.ASC, "excluUseAr");
            default          -> Sort.unsorted();
        };
        return PageRequest.of(page, size, springSort);
    }

    private Comparator<PropertySummaryDto> resolveComparator(String sort) {
        return switch (sort) {
            case "priceDesc" -> Comparator.comparing(
                    PropertySummaryDto::getDeposit, Comparator.nullsLast(Comparator.reverseOrder()));
            case "priceAsc" -> Comparator.comparing(
                    PropertySummaryDto::getDeposit, Comparator.nullsLast(Comparator.naturalOrder()));
            case "areaDesc" -> Comparator.comparing(
                    PropertySummaryDto::getExcluUseAr, Comparator.nullsLast(Comparator.reverseOrder()));
            case "areaAsc" -> Comparator.comparing(
                    PropertySummaryDto::getExcluUseAr, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(
                    PropertySummaryDto::getLastUpdated, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private PropertyListResponseDto buildResponse(
            List<PropertySummaryDto> content, long totalElements, int totalPages, int page, int size) {
        return PropertyListResponseDto.builder()
                .properties(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .build();
    }
}