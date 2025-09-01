# 숙박업 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

### 1.1 기본 테이블 구조
```sql
-- 기존 테이블 및 시퀀스 삭제
DROP TABLE LODGING_BUSINESS CASCADE CONSTRAINTS;
DROP SEQUENCE SEQ_LODGING_BUSINESS;

-- 숙박업 데이터 테이블 생성
CREATE TABLE LODGING_BUSINESS (
    ID                          NUMBER(19) PRIMARY KEY,
    SERVICE_NAME                VARCHAR2(100),
    SERVICE_ID                  VARCHAR2(100),
    LOCAL_GOV_CODE              VARCHAR2(50),
    MANAGEMENT_NUMBER           VARCHAR2(100),
    LICENSE_DATE                DATE,
    LICENSE_CANCEL_DATE         DATE,
    BUSINESS_STATUS_CODE        VARCHAR2(20),
    BUSINESS_STATUS_NAME        VARCHAR2(100),
    DETAIL_STATUS_CODE          VARCHAR2(20),
    DETAIL_STATUS_NAME          VARCHAR2(100),
    CLOSURE_DATE                DATE,
    SUSPENSION_START_DATE       DATE,
    SUSPENSION_END_DATE         DATE,
    REOPENING_DATE              DATE,
    LOCATION_PHONE              VARCHAR2(200),          -- 전화번호 필드 확장
    LOCATION_AREA               NUMBER(15,5) DEFAULT 0, -- 면적 정밀도 증가
    LOCATION_POSTAL_CODE        VARCHAR2(20),
    FULL_ADDRESS                VARCHAR2(2000),         -- 주소 필드 확장
    ROAD_ADDRESS                VARCHAR2(2000),         -- 도로명주소 필드 확장
    ROAD_POSTAL_CODE            VARCHAR2(20),
    BUSINESS_NAME               VARCHAR2(1000),         -- 사업장명 필드 확장
    LAST_UPDATE_TIME            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    DATA_UPDATE_TYPE            VARCHAR2(20),
    DATA_UPDATE_DATE            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    BUSINESS_TYPE_NAME          VARCHAR2(200),
    COORD_X                     NUMBER(25,10) DEFAULT 0, -- 좌표 precision 대폭 확장
    COORD_Y                     NUMBER(25,10) DEFAULT 0, -- 좌표 precision 대폭 확장
    HYGIENE_BUSINESS_TYPE       VARCHAR2(200),
    BUILDING_GROUND_FLOORS      NUMBER(5) DEFAULT 0,
    BUILDING_BASEMENT_FLOORS    NUMBER(5) DEFAULT 0,
    USE_START_GROUND_FLOOR      NUMBER(5) DEFAULT 0,
    USE_END_GROUND_FLOOR        NUMBER(5) DEFAULT 0,
    USE_START_BASEMENT_FLOOR    NUMBER(5) DEFAULT 0,
    USE_END_BASEMENT_FLOOR      NUMBER(5) DEFAULT 0,
    KOREAN_ROOM_COUNT           NUMBER(8) DEFAULT 0,     -- 방 개수 필드 확장
    WESTERN_ROOM_COUNT          NUMBER(8) DEFAULT 0,     -- 방 개수 필드 확장
    CONDITIONAL_PERMIT_REASON   VARCHAR2(2000),          -- 조건부허가사유 필드 확장
    CONDITIONAL_PERMIT_START    DATE,
    CONDITIONAL_PERMIT_END      DATE,
    BUILDING_OWNERSHIP_TYPE     VARCHAR2(100),
    FEMALE_EMPLOYEE_COUNT       NUMBER(8) DEFAULT 0,     -- 직원 수 필드 확장
    MALE_EMPLOYEE_COUNT         NUMBER(8) DEFAULT 0,     -- 직원 수 필드 확장
    MULTI_USE_FACILITY_YN       VARCHAR2(1) DEFAULT 'N',
    CREATED_AT                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_LODGING_MGMT_NUM UNIQUE (MANAGEMENT_NUMBER)
);

-- 시퀀스 생성
CREATE SEQUENCE SEQ_LODGING_BUSINESS START WITH 1 INCREMENT BY 1;
```

### 1.2 필드 크기 설정 근거
- **LOCATION_PHONE**: 100 → 200자로 증가 (비정상적인 전화번호 데이터 대응)
- **FULL_ADDRESS, ROAD_ADDRESS**: 1000 → 2000자로 증가 (긴 주소 대응)
- **BUSINESS_NAME**: 500 → 1000자로 증가 (긴 사업장명 대응)
- **COORD_X, COORD_Y**: NUMBER(15,10) → NUMBER(25,10)으로 증가 (좌표 정밀도 문제 해결)
- **LOCATION_AREA**: NUMBER(10,2) → NUMBER(15,5)로 증가 (면적 정밀도 향상)

## 2. application.yml 설정

```yaml
# CSV 파일 경로 설정
app:
  csv:
    crime-data-path: classpath:data/5대범죄발생현황_20250822144657.csv
    lodging-data-path: classpath:data/lodging_business_data.csv
```

## 3. JPA Entity

```java
package com.WhereHouse.APITest.Lodgment.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "LODGING_BUSINESS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LodgingBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lodging_seq")
    @SequenceGenerator(name = "lodging_seq", sequenceName = "SEQ_LODGING_BUSINESS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERVICE_NAME", length = 100)
    private String serviceName = "데이터없음";

    @Column(name = "SERVICE_ID", length = 100)
    private String serviceId = "데이터없음";

    @Column(name = "LOCAL_GOV_CODE", length = 50)
    private String localGovCode = "데이터없음";

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber = "데이터없음";

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode = "데이터없음";

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName = "데이터없음";

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode = "데이터없음";

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName = "데이터없음";

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPENING_DATE")
    private LocalDate reopeningDate;

    @Column(name = "LOCATION_PHONE", length = 200)  // 확장된 크기
    private String locationPhone = "데이터없음";

    @Column(name = "LOCATION_AREA", precision = 15, scale = 5)  // 정밀도 증가
    private BigDecimal locationArea = BigDecimal.ZERO;

    @Column(name = "LOCATION_POSTAL_CODE", length = 20)
    private String locationPostalCode = "데이터없음";

    @Column(name = "FULL_ADDRESS", length = 2000)  // 확장된 크기
    private String fullAddress = "데이터없음";

    @Column(name = "ROAD_ADDRESS", length = 2000)  // 확장된 크기
    private String roadAddress = "데이터없음";

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode = "데이터없음";

    @Column(name = "BUSINESS_NAME", length = 1000)  // 확장된 크기
    private String businessName = "데이터없음";

    @Column(name = "LAST_UPDATE_TIME")
    private LocalDateTime lastUpdateTime;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType = "데이터없음";

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_TYPE_NAME", length = 200)
    private String businessTypeName = "데이터없음";

    @Column(name = "COORD_X", precision = 25, scale = 10)  // 대폭 확장된 정밀도
    private BigDecimal coordX = BigDecimal.ZERO;

    @Column(name = "COORD_Y", precision = 25, scale = 10)  // 대폭 확장된 정밀도
    private BigDecimal coordY = BigDecimal.ZERO;

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 200)
    private String hygieneBusinessType = "데이터없음";

    @Column(name = "BUILDING_GROUND_FLOORS")
    private Integer buildingGroundFloors = 0;

    @Column(name = "BUILDING_BASEMENT_FLOORS")
    private Integer buildingBasementFloors = 0;

    @Column(name = "USE_START_GROUND_FLOOR")
    private Integer useStartGroundFloor = 0;

    @Column(name = "USE_END_GROUND_FLOOR")
    private Integer useEndGroundFloor = 0;

    @Column(name = "USE_START_BASEMENT_FLOOR")
    private Integer useStartBasementFloor = 0;

    @Column(name = "USE_END_BASEMENT_FLOOR")
    private Integer useEndBasementFloor = 0;

    @Column(name = "KOREAN_ROOM_COUNT")
    private Integer koreanRoomCount = 0;

    @Column(name = "WESTERN_ROOM_COUNT")
    private Integer westernRoomCount = 0;

    @Column(name = "CONDITIONAL_PERMIT_REASON", length = 2000)  // 확장된 크기
    private String conditionalPermitReason = "데이터없음";

    @Column(name = "CONDITIONAL_PERMIT_START")
    private LocalDate conditionalPermitStart;

    @Column(name = "CONDITIONAL_PERMIT_END")
    private LocalDate conditionalPermitEnd;

    @Column(name = "BUILDING_OWNERSHIP_TYPE", length = 100)
    private String buildingOwnershipType = "데이터없음";

    @Column(name = "FEMALE_EMPLOYEE_COUNT")
    private Integer femaleEmployeeCount = 0;

    @Column(name = "MALE_EMPLOYEE_COUNT")
    private Integer maleEmployeeCount = 0;

    @Column(name = "MULTI_USE_FACILITY_YN", length = 1)
    private String multiUseFacilityYn = "N";

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastUpdateTime == null) {
            lastUpdateTime = LocalDateTime.now();
        }
        if (dataUpdateDate == null) {
            dataUpdateDate = LocalDateTime.now();
        }

        // NULL 값들을 기본값으로 설정
        if (serviceName == null) serviceName = "데이터없음";
        if (serviceId == null) serviceId = "데이터없음";
        if (localGovCode == null) localGovCode = "데이터없음";
        if (managementNumber == null) managementNumber = "데이터없음";
        if (businessStatusCode == null) businessStatusCode = "데이터없음";
        if (businessStatusName == null) businessStatusName = "데이터없음";
        if (detailStatusCode == null) detailStatusCode = "데이터없음";
        if (detailStatusName == null) detailStatusName = "데이터없음";
        if (locationPhone == null) locationPhone = "데이터없음";
        if (locationPostalCode == null) locationPostalCode = "데이터없음";
        if (fullAddress == null) fullAddress = "데이터없음";
        if (roadAddress == null) roadAddress = "데이터없음";
        if (roadPostalCode == null) roadPostalCode = "데이터없음";
        if (businessName == null) businessName = "데이터없음";
        if (dataUpdateType == null) dataUpdateType = "데이터없음";
        if (businessTypeName == null) businessTypeName = "데이터없음";
        if (hygieneBusinessType == null) hygieneBusinessType = "데이터없음";
        if (conditionalPermitReason == null) conditionalPermitReason = "데이터없음";
        if (buildingOwnershipType == null) buildingOwnershipType = "데이터없음";
    }
}
```

## 4. Repository

```java
package com.WhereHouse.APITest.Lodgment.Repository;

import com.WhereHouse.APITest.Lodgment.Entity.LodgingBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LodgingBusinessRepository extends JpaRepository<LodgingBusiness, Long> {
    
    Optional<LodgingBusiness> findByManagementNumber(String managementNumber);
    
    @Query("SELECT l FROM LodgingBusiness l WHERE l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findAllActiveBusinesses();
    
    @Query("SELECT l FROM LodgingBusiness l WHERE l.fullAddress LIKE %:keyword% AND l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findActiveBusinessesByAddress(@Param("keyword") String keyword);
    
    @Query("SELECT l FROM LodgingBusiness l WHERE l.businessTypeName IN ('여관업', '여인숙업') AND l.businessStatusName = '영업/정상'")
    List<LodgingBusiness> findActiveMotelsAndInns();
    
    boolean existsByManagementNumber(String managementNumber);
}
```

## 5. 배치 처리 CSV 로더

### 5.1 핵심 특징
- **배치 처리**: 1,000개씩 나누어 처리하여 메모리 및 트랜잭션 최적화
- **트랜잭션 분리**: 각 배치를 독립적인 트랜잭션으로 처리
- **실패 복구**: 배치 실패 시 개별 저장 시도
- **다양한 날짜 형식 지원**: `yyyy-MM-dd`, `yyyyMMdd` 형식 모두 처리

```java
package com.WhereHouse.APITest.Lodgment.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.Lodgment.Entity.LodgingBusiness;
import com.WhereHouse.APITest.Lodgment.Repository.LodgingBusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LodgingDataLoader implements CommandLineRunner {

    private final LodgingBusinessRepository lodgingRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.lodging-data-path}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 1000; // 배치 크기
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER3 = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm");

    @Override
    public void run(String... args) {
        long existingCount = lodgingRepository.count();
        if (existingCount > 0) {
            log.info("숙박업 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("CSV 파일에서 {} 행 읽음 (헤더 포함)", csvData.size());

                List<LodgingBusiness> batch = new ArrayList<>();
                int totalSaved = 0;
                int totalErrors = 0;
                
                for (int i = 1; i < csvData.size(); i++) { // 헤더 스킵
                    String[] row = csvData.get(i);
                    try {
                        if (row.length >= 41) {
                            LodgingBusiness lodging = createLodgingBusiness(row);
                            batch.add(lodging);
                            
                            // 배치가 찼거나 마지막 행이면 저장
                            if (batch.size() >= BATCH_SIZE || i == csvData.size() - 1) {
                                int saved = saveBatch(batch);
                                totalSaved += saved;
                                batch.clear();
                                
                                if (totalSaved % (BATCH_SIZE * 10) == 0) {
                                    log.info("진행 상황: {} 개 데이터 저장 완료 (현재 DB 카운트: {})", 
                                        totalSaved, lodgingRepository.count());
                                }
                            }
                        }
                    } catch (Exception e) {
                        totalErrors++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        
                        if (totalErrors > 100) {
                            log.error("오류가 너무 많아서 처리를 중단합니다. 총 오류 수: {}", totalErrors);
                            break;
                        }
                    }
                }
                
                // 최종 상태 확인
                long finalCount = lodgingRepository.count();
                log.info("로딩 완료 - 처리 시도: {} 개, 최종 DB 저장: {} 개, 오류: {} 개", 
                    totalSaved, finalCount, totalErrors);
            }
        } catch (Exception e) {
            log.error("숙박업 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<LodgingBusiness> batch) {
        try {
            List<LodgingBusiness> saved = lodgingRepository.saveAll(batch);
            log.debug("배치 저장 성공: {} 개", saved.size());
            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}", e.getMessage());
            
            // 배치 실패 시 개별 저장 시도
            int savedCount = 0;
            for (LodgingBusiness lodging : batch) {
                try {
                    lodgingRepository.save(lodging);
                    savedCount++;
                } catch (Exception individualError) {
                    log.warn("개별 저장 실패 - 관리번호: {}, 오류: {}", 
                        lodging.getManagementNumber(), individualError.getMessage());
                }
            }
            
            log.info("개별 저장 완료: {} / {} 개", savedCount, batch.size());
            return savedCount;
        }
    }

    private LodgingBusiness createLodgingBusiness(String[] row) {
        return LodgingBusiness.builder()
                .serviceName(parseString(row[1]))
                .serviceId(parseString(row[2]))
                .localGovCode(parseString(row[3]))
                .managementNumber(parseString(row[4]))
                .licenseDate(parseDate(row[5]))
                .licenseCancelDate(parseDate(row[6]))
                .businessStatusCode(parseString(row[7]))
                .businessStatusName(parseString(row[8]))
                .detailStatusCode(parseString(row[9]))
                .detailStatusName(parseString(row[10]))
                .closureDate(parseDate(row[11]))
                .suspensionStartDate(parseDate(row[12]))
                .suspensionEndDate(parseDate(row[13]))
                .reopeningDate(parseDate(row[14]))
                .locationPhone(parseString(row[15]))
                .locationArea(parseDecimal(row[16]))
                .locationPostalCode(parseString(row[17]))
                .fullAddress(parseString(row[18]))
                .roadAddress(parseString(row[19]))
                .roadPostalCode(parseString(row[20]))
                .businessName(parseString(row[21]))
                .lastUpdateTime(parseDateTime(row[22]))
                .dataUpdateType(parseString(row[23]))
                .dataUpdateDate(parseDateTime(row[24]))
                .businessTypeName(parseString(row[25]))
                .coordX(parseDecimal(row[26]))
                .coordY(parseDecimal(row[27]))
                .hygieneBusinessType(parseString(row[28]))
                .buildingGroundFloors(parseInteger(row[29]))
                .buildingBasementFloors(parseInteger(row[30]))
                .useStartGroundFloor(parseInteger(row[31]))
                .useEndGroundFloor(parseInteger(row[32]))
                .useStartBasementFloor(parseInteger(row[33]))
                .useEndBasementFloor(parseInteger(row[34]))
                .koreanRoomCount(parseInteger(row[35]))
                .westernRoomCount(parseInteger(row[36]))
                .conditionalPermitReason(parseString(row[37]))
                .conditionalPermitStart(parseDate(row[38]))
                .conditionalPermitEnd(parseDate(row[39]))
                .buildingOwnershipType(parseString(row[40]))
                .femaleEmployeeCount(row.length > 41 ? parseInteger(row[41]) : 0)
                .maleEmployeeCount(row.length > 42 ? parseInteger(row[42]) : 0)
                .multiUseFacilityYn(row.length > 43 ? parseString(row[43]) : "N")
                .build();
    }

    // 파싱 메서드들
    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return "데이터없음";
        }
        String trimmed = value.trim();
        
        if (trimmed.length() > 1900) {
            trimmed = trimmed.substring(0, 1900);
            log.debug("문자열 길이 제한으로 자름: 원본 길이 {} -> 1900", value.length());
        }
        
        return trimmed;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            String cleaned = value.trim().replace(",", "");
            long longValue = Long.parseLong(cleaned);
            
            if (longValue > Integer.MAX_VALUE || longValue < Integer.MIN_VALUE) {
                log.debug("정수값 오버플로: {}, 0으로 설정", longValue);
                return 0;
            }
            
            return (int) longValue;
        } catch (NumberFormatException e) {
            log.debug("정수 파싱 실패: '{}', 0으로 설정", value);
            return 0;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return BigDecimal.ZERO;
        }
        try {
            String cleaned = value.trim().replace(",", "");
            BigDecimal result = new BigDecimal(cleaned);
            
            int precision = result.precision();
            
            if (precision > 25) {
                log.debug("BigDecimal precision 초과: {} (precision: {}), 반올림 적용", value, precision);
                result = result.setScale(10, BigDecimal.ROUND_HALF_UP);
            }
            
            return result;
        } catch (NumberFormatException e) {
            log.debug("소수 파싱 실패: '{}', 0으로 설정", value);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDate.of(2025, 1, 1);
        }
        
        String trimmed = value.trim();
        
        try {
            // yyyy-MM-dd 형식 시도
            return LocalDate.parse(trimmed, DATE_FORMATTER);
        } catch (Exception e1) {
            try {
                // yyyyMMdd 형식 시도 (20210118 같은 형식)
                return LocalDate.parse(trimmed, DATE_FORMATTER_COMPACT);
            } catch (Exception e2) {
                log.debug("날짜 파싱 실패: '{}', 기본값으로 설정", value);
                return LocalDate.of(2025, 1, 1);
            }
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDateTime.of(2025, 1, 1, 0, 0);
        }
        
        String trimmed = value.trim();
        DateTimeFormatter[] formatters = {DATETIME_FORMATTER1, DATETIME_FORMATTER2, DATETIME_FORMATTER3};
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception e) {
                // 다음 형식 시도
            }
        }
        
        log.debug("날짜시간 파싱 실패: '{}', 기본값으로 설정", value);
        return LocalDateTime.of(2025, 1, 1, 0, 0);
    }
}
```

## 6. 파일 위치

```
src/main/resources/data/lodging_business_data.csv
```

## 7. 트러블슈팅

### 7.1 주요 해결된 문제들

**ORA-01438 오류 (전체 자릿수 초과)**
- **원인**: 좌표 데이터의 precision이 NUMBER(15,10) 한계 초과
- **해결**: NUMBER(25,10)으로 확장하여 정수부 15자리까지 수용

**트랜잭션 커밋 문제**
- **원인**: 대용량 데이터를 단일 트랜잭션으로 처리
- **해결**: 1,000개씩 배치 처리 및 독립 트랜잭션 적용

**다양한 날짜 형식**
- **원인**: CSV 데이터에 `yyyy-MM-dd`, `yyyyMMdd` 등 혼재
- **해결**: 다중 DateTimeFormatter 적용

**문자열 길이 초과**
- **원인**: VARCHAR2 크기보다 긴 데이터 존재
- **해결**: 필드 크기 확장 및 자동 문자열 자르기

### 7.2 성능 최적화

**배치 처리 도입**
- 1,000개씩 배치 처리하여 메모리 사용량 최적화
- 독립 트랜잭션으로 중간 실패 시에도 이미 처리된 데이터 보존

**로그 레벨 조정**
- 대용량 데이터 처리 시 불필요한 WARN 로그를 DEBUG로 변경
- 진행 상황 로그는 10,000개 단위로 출력

## 8. 데이터 검증 쿼리

```sql
-- 총 데이터 개수 확인
SELECT COUNT(*) FROM LODGING_BUSINESS;

-- 영업 중인 숙박업소 개수
SELECT COUNT(*) FROM LODGING_BUSINESS WHERE BUSINESS_STATUS_NAME = '영업/정상';

-- 업종별 분포
SELECT BUSINESS_TYPE_NAME, COUNT(*) as CNT
FROM LODGING_BUSINESS 
GROUP BY BUSINESS_TYPE_NAME 
ORDER BY CNT DESC;

-- 지역별 분포 (앞 2자리 기준)
SELECT SUBSTR(LOCAL_GOV_CODE, 1, 2) as REGION_CODE, COUNT(*) as CNT
FROM LODGING_BUSINESS 
GROUP BY SUBSTR(LOCAL_GOV_CODE, 1, 2)
ORDER BY CNT DESC;

-- 좌표 데이터 유효성 확인
SELECT COUNT(*) as TOTAL_COUNT,
       COUNT(CASE WHEN COORD_X != 0 AND COORD_Y != 0 THEN 1 END) as COORD_EXISTS,
       COUNT(CASE WHEN COORD_X = 0 AND COORD_Y = 0 THEN 1 END) as NO_COORD
FROM LODGING_BUSINESS;

-- 최근 업데이트된 데이터
SELECT COUNT(*) FROM LODGING_BUSINESS 
WHERE DATA_UPDATE_DATE >= DATE'2024-01-01';
```

## 9. API 활용 예제

### 9.1 기본 조회 API

```java
@RestController
@RequestMapping("/api/lodging")
@RequiredArgsConstructor
public class LodgingController {
    
    private final LodgingBusinessRepository lodgingRepository;
    
    @GetMapping("/active")
    public List<LodgingBusiness> getActiveBusinesses() {
        return lodgingRepository.findAllActiveBusinesses();
    }
    
    @GetMapping("/search")
    public List<LodgingBusiness> searchByAddress(@RequestParam String keyword) {
        return lodgingRepository.findActiveBusinessesByAddress(keyword);
    }
    
    @GetMapping("/motels")
    public List<LodgingBusiness> getMotelsAndInns() {
        return lodgingRepository.findActiveMotelsAndInns();
    }
}
```

### 9.2 통계 API

```java
@GetMapping("/stats/by-type")
public Map<String, Long> getStatsByType() {
    return lodgingRepository.findAll().stream()
        .collect(Collectors.groupingBy(
            LodgingBusiness::getBusinessTypeName,
            Collectors.counting()
        ));
}

@GetMapping("/stats/by-region")
public Map<String, Long> getStatsByRegion() {
    return lodgingRepository.findAll().stream()
        .collect(Collectors.groupingBy(
            l -> l.getLocalGovCode().substring(0, 2),
            Collectors.counting()
        ));
}
```

## 10. 운영 가이드

### 10.1 데이터 재로딩

```bash
# 1. 기존 데이터 백업 (선택사항)
sqlplus user/password@db
CREATE TABLE LODGING_BUSINESS_BACKUP AS SELECT * FROM LODGING_BUSINESS;

# 2. 기존 데이터 삭제
DELETE FROM LODGING_BUSINESS;
COMMIT;

# 3. 애플리케이션 재시작하여 새 데이터 로딩
```

### 10.2 성능 모니터링

```sql
-- 테이블 크기 확인
SELECT 
    ROUND(SUM(bytes)/1024/1024, 2) AS SIZE_MB
FROM user_segments 
WHERE segment_name = 'LODGING_BUSINESS';

-- 인덱스 사용률 확인 (인덱스가 있는 경우)
SELECT index_name, num_rows, distinct_keys, clustering_factor
FROM user_indexes 
WHERE table_name = 'LODGING_BUSINESS';
```

### 10.3 데이터 품질 체크

```sql
-- 필수 필드 NULL 체크
SELECT 
    COUNT(CASE WHEN MANAGEMENT_NUMBER IS NULL OR MANAGEMENT_NUMBER = '데이터없음' THEN 1 END) as NO_MGMT_NUM,
    COUNT(CASE WHEN BUSINESS_NAME IS NULL OR BUSINESS_NAME = '데이터없음' THEN 1 END) as NO_BUSINESS_NAME,
    COUNT(CASE WHEN FULL_ADDRESS IS NULL OR FULL_ADDRESS = '데이터없음' THEN 1 END) as NO_ADDRESS
FROM LODGING_BUSINESS;

-- 좌표 품질 체크
SELECT 
    COUNT(CASE WHEN COORD_X BETWEEN 124 AND 132 AND COORD_Y BETWEEN 33 AND 43 THEN 1 END) as VALID_COORDS,
    COUNT(CASE WHEN COORD_X = 0 AND COORD_Y = 0 THEN 1 END) as ZERO_COORDS,
    COUNT(*) as TOTAL
FROM LODGING_BUSINESS;

-- 중복 데이터 체크
SELECT MANAGEMENT_NUMBER, COUNT(*) 
FROM LODGING_BUSINESS 
GROUP BY MANAGEMENT_NUMBER 
HAVING COUNT(*) > 1;
```

## 11. 주요 특징 요약

### 11.1 데이터베이스 설계
- **확장 가능한 스키마**: VARCHAR2와 NUMBER 필드 크기를 여유있게 설정
- **제약조건**: MANAGEMENT_NUMBER에 UNIQUE 제약으로 중복 방지
- **기본값 설정**: NULL 처리를 위한 적절한 DEFAULT 값 설정

### 11.2 JPA 구현
- **Builder 패턴**: 객체 생성의 유연성 제공
- **@PrePersist**: 자동 기본값 설정 및 타임스탬프 처리
- **정밀도 제어**: BigDecimal의 precision과 scale 명시적 설정

### 11.3 데이터 로딩
- **배치 처리**: 메모리 효율적인 1,000개 단위 배치 처리
- **오류 복구**: 배치 실패 시 개별 저장으로 최대한 데이터 보존
- **다형성 파싱**: 다양한 날짜/시간 형식 자동 처리
- **실시간 모니터링**: 진행 상황 및 DB 카운트 실시간 확인

### 11.4 확장성
- **Repository 패턴**: 비즈니스 로직에 맞는 쿼리 메서드 제공
- **API 준비**: RESTful API 구현을 위한 기본 구조 제공
- **통계 기능**: 업종별, 지역별 통계 기능 포함

이 문서는 숙박업 데이터의 완전한 Oracle DB 설계부터 JPA 구현, 대용량 CSV 데이터 로딩까지 전 과정을 다루며, 실제 운영 환경에서 발생할 수 있는 다양한 문제점들과 해결방안을 포함하고 있습니다.