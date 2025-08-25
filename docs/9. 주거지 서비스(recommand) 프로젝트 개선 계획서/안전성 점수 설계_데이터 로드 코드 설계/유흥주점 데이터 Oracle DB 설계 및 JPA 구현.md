# 유흥주점 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE ENTERTAINMENT_BARS (
    ID                      NUMBER(19) PRIMARY KEY,
    DISTRICT_CODE           VARCHAR2(20) NOT NULL,
    MANAGEMENT_NUMBER       VARCHAR2(100),
    LICENSE_DATE            DATE,
    CANCEL_DATE             DATE,
    BUSINESS_STATUS_CODE    VARCHAR2(20),
    BUSINESS_STATUS_NAME    VARCHAR2(100),
    DETAIL_STATUS_CODE      VARCHAR2(20),
    DETAIL_STATUS_NAME      VARCHAR2(100),
    CLOSURE_DATE            DATE,
    SUSPEND_START_DATE      DATE,
    SUSPEND_END_DATE        DATE,
    REOPEN_DATE             DATE,
    PHONE_NUMBER            VARCHAR2(50),
    AREA_SIZE               NUMBER(10,2),
    POSTAL_CODE             VARCHAR2(20),
    JIBUN_ADDRESS           VARCHAR2(1000),
    ROAD_ADDRESS            VARCHAR2(1000),
    ROAD_POSTAL_CODE        VARCHAR2(20),
    BUSINESS_NAME           VARCHAR2(500),
    LAST_MODIFIED_DATE      TIMESTAMP,
    DATA_UPDATE_TYPE        VARCHAR2(20),
    DATA_UPDATE_DATE        TIMESTAMP,
    BUSINESS_CATEGORY       VARCHAR2(100),
    COORDINATE_X            NUMBER(15,7),
    COORDINATE_Y            NUMBER(15,7),
    HYGIENE_BUSINESS_TYPE   VARCHAR2(100),
    MALE_EMPLOYEES          NUMBER(6),
    FEMALE_EMPLOYEES        NUMBER(6),
    SURROUNDING_AREA        VARCHAR2(200),
    GRADE_CATEGORY          VARCHAR2(100),
    WATER_SUPPLY_TYPE       VARCHAR2(100),
    TOTAL_EMPLOYEES         NUMBER(6),
    HEAD_OFFICE_EMPLOYEES   NUMBER(6),
    FACTORY_OFFICE_EMPLOYEES NUMBER(6),
    FACTORY_SALES_EMPLOYEES NUMBER(6),
    FACTORY_PRODUCTION_EMPLOYEES NUMBER(6),
    BUILDING_OWNERSHIP      VARCHAR2(100),
    GUARANTEE_AMOUNT        NUMBER(15),
    MONTHLY_RENT            NUMBER(15),
    MULTI_USE_FACILITY      VARCHAR2(20),
    TOTAL_FACILITY_SIZE     NUMBER(10,2),
    TRADITIONAL_DESIGNATION_NUMBER VARCHAR2(100),
    TRADITIONAL_MAIN_FOOD   VARCHAR2(500),
    HOMEPAGE                VARCHAR2(1000),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_ENTERTAINMENT_MGMT_NUM UNIQUE (MANAGEMENT_NUMBER)
);

CREATE SEQUENCE SEQ_ENTERTAINMENT_BARS START WITH 1 INCREMENT BY 1;
```

## 2. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ENTERTAINMENT_BARS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntertainmentBars {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entertainment_seq")
    @SequenceGenerator(name = "entertainment_seq", sequenceName = "SEQ_ENTERTAINMENT_BARS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE", nullable = false, length = 20)
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "CANCEL_DATE")
    private LocalDate cancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPEND_START_DATE")
    private LocalDate suspendStartDate;

    @Column(name = "SUSPEND_END_DATE")
    private LocalDate suspendEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "AREA_SIZE", precision = 10, scale = 2)
    private BigDecimal areaSize;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "JIBUN_ADDRESS", length = 1000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 1000)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 500)
    private String businessName;

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_CATEGORY", length = 100)
    private String businessCategory;

    @Column(name = "COORDINATE_X", precision = 15, scale = 7)
    private BigDecimal coordinateX;

    @Column(name = "COORDINATE_Y", precision = 15, scale = 7)
    private BigDecimal coordinateY;

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 100)
    private String hygieneBusinessType;

    @Column(name = "MALE_EMPLOYEES")
    private Integer maleEmployees;

    @Column(name = "FEMALE_EMPLOYEES")
    private Integer femaleEmployees;

    @Column(name = "SURROUNDING_AREA", length = 200)
    private String surroundingArea;

    @Column(name = "GRADE_CATEGORY", length = 100)
    private String gradeCategory;

    @Column(name = "WATER_SUPPLY_TYPE", length = 100)
    private String waterSupplyType;

    @Column(name = "TOTAL_EMPLOYEES")
    private Integer totalEmployees;

    @Column(name = "HEAD_OFFICE_EMPLOYEES")
    private Integer headOfficeEmployees;

    @Column(name = "FACTORY_OFFICE_EMPLOYEES")
    private Integer factoryOfficeEmployees;

    @Column(name = "FACTORY_SALES_EMPLOYEES")
    private Integer factorySalesEmployees;

    @Column(name = "FACTORY_PRODUCTION_EMPLOYEES")
    private Integer factoryProductionEmployees;

    @Column(name = "BUILDING_OWNERSHIP", length = 100)
    private String buildingOwnership;

    @Column(name = "GUARANTEE_AMOUNT", precision = 15)
    private BigDecimal guaranteeAmount;

    @Column(name = "MONTHLY_RENT", precision = 15)
    private BigDecimal monthlyRent;

    @Column(name = "MULTI_USE_FACILITY", length = 20)
    private String multiUseFacility;

    @Column(name = "TOTAL_FACILITY_SIZE", precision = 10, scale = 2)
    private BigDecimal totalFacilitySize;

    @Column(name = "TRADITIONAL_DESIGNATION_NUMBER", length = 100)
    private String traditionalDesignationNumber;

    @Column(name = "TRADITIONAL_MAIN_FOOD", length = 500)
    private String traditionalMainFood;

    @Column(name = "HOMEPAGE", length = 1000)
    private String homepage;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## 3. Repository

```java
package com.wherehouse.safety.repository;

import com.wherehouse.safety.entity.EntertainmentBars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EntertainmentBarsRepository extends JpaRepository<EntertainmentBars, Long> {
    
    List<EntertainmentBars> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);
    
    @Query("SELECT COUNT(e) FROM EntertainmentBars e WHERE e.districtCode = :districtCode AND e.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);
    
    @Query("SELECT e.businessCategory, COUNT(e) FROM EntertainmentBars e WHERE e.businessStatusCode = '1' GROUP BY e.businessCategory")
    List<Object[]> countByBusinessCategory();
    
    boolean existsByManagementNumber(String managementNumber);
}
```

## 4. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.EntertainmentBars;
import com.wherehouse.safety.repository.EntertainmentBarsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntertainmentBarsDataLoader implements CommandLineRunner {

    private final EntertainmentBarsRepository entertainmentBarsRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.entertainment-bars-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (entertainmentBarsRepository.count() > 0) {
            log.info("유흥주점 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;

                log.info("유흥주점 데이터 로딩 시작 - 총 {} 건", totalRows);

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률: {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }

                    if (row.length >= 43) {
                        try {
                            EntertainmentBars bar = EntertainmentBars.builder()
                                    .districtCode(parseString(row[0]))
                                    .managementNumber(parseString(row[1]))
                                    .licenseDate(parseDate(row[2]))
                                    .cancelDate(parseDate(row[3]))
                                    .businessStatusCode(parseString(row[4]))
                                    .businessStatusName(parseString(row[5]))
                                    .detailStatusCode(parseString(row[6]))
                                    .detailStatusName(parseString(row[7]))
                                    .closureDate(parseDate(row[8]))
                                    .suspendStartDate(parseDate(row[9]))
                                    .suspendEndDate(parseDate(row[10]))
                                    .reopenDate(parseDate(row[11]))
                                    .phoneNumber(parseString(row[12]))
                                    .areaSize(parseBigDecimal(row[13]))
                                    .postalCode(parseString(row[14]))
                                    .jibunAddress(parseString(row[15]))
                                    .roadAddress(parseString(row[16]))
                                    .roadPostalCode(parseString(row[17]))
                                    .businessName(parseString(row[18]))
                                    .lastModifiedDate(parseDateTime(row[19]))
                                    .dataUpdateType(parseString(row[20]))
                                    .dataUpdateDate(parseDateTime(row[21]))
                                    .businessCategory(parseString(row[22]))
                                    .coordinateX(parseBigDecimal(row[23]))
                                    .coordinateY(parseBigDecimal(row[24]))
                                    .hygieneBusinessType(parseString(row[25]))
                                    .maleEmployees(parseInteger(row[26]))
                                    .femaleEmployees(parseInteger(row[27]))
                                    .surroundingArea(parseString(row[28]))
                                    .gradeCategory(parseString(row[29]))
                                    .waterSupplyType(parseString(row[30]))
                                    .totalEmployees(parseInteger(row[31]))
                                    .headOfficeEmployees(parseInteger(row[32]))
                                    .factoryOfficeEmployees(parseInteger(row[33]))
                                    .factorySalesEmployees(parseInteger(row[34]))
                                    .factoryProductionEmployees(parseInteger(row[35]))
                                    .buildingOwnership(parseString(row[36]))
                                    .guaranteeAmount(parseBigDecimal(row[37]))
                                    .monthlyRent(parseBigDecimal(row[38]))
                                    .multiUseFacility(parseString(row[39]))
                                    .totalFacilitySize(parseBigDecimal(row[40]))
                                    .traditionalDesignationNumber(parseString(row[41]))
                                    .traditionalMainFood(parseString(row[42]))
                                    .homepage(row.length > 43 ? parseString(row[43]) : "데이터없음")
                                    .build();

                            entertainmentBarsRepository.save(bar);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 43개)", i, row.length);
                    }
                }

                log.info("유흥주점 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건",
                        savedCount, errorCount, totalRows);
            }
        } catch (Exception e) {
            log.error("CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDate.of(1900, 1, 1); // 기본값: 1900-01-01
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.of(1900, 1, 1); // 파싱 실패시도 기본값
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 기본값: 1900-01-01 00:00:00
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 파싱 실패시도 기본값
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
```

## 5. application.yml 추가 설정

```yaml
# CSV 설정 (기존 설정에 추가)
app:
  csv:
    crime-data-path: classpath:data/5대범죄발생현황_20250822144657.csv
    entertainment-bars-data-path: classpath:data/유흥주점_데이터.csv
```

파일 위치: `src/main/resources/data/유흥주점_데이터.csv`