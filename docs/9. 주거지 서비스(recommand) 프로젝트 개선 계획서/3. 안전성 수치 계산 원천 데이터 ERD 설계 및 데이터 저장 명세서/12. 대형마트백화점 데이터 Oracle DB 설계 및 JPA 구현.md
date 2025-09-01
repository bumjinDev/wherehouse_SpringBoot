# 대형마트/백화점 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE MART_STATISTICS (
    ID                      NUMBER(19) PRIMARY KEY,
    LOCAL_GOVT_CODE         VARCHAR2(50) NOT NULL,
    MANAGEMENT_NO           VARCHAR2(50) NOT NULL,
    LICENSE_DATE            DATE,
    LICENSE_CANCEL_DATE     DATE,
    BUSINESS_STATUS_CODE    VARCHAR2(20),
    BUSINESS_STATUS_NAME    VARCHAR2(100),
    DETAIL_STATUS_CODE      VARCHAR2(20),
    DETAIL_STATUS_NAME      VARCHAR2(100),
    CLOSURE_DATE            DATE,
    SUSPENSION_START_DATE   DATE,
    SUSPENSION_END_DATE     DATE,
    REOPEN_DATE             DATE,
    PHONE_NUMBER            VARCHAR2(50),
    LOCATION_AREA           NUMBER(10,2),
    LOCATION_POSTAL_CODE    VARCHAR2(50),
    ADDRESS                 VARCHAR2(500),
    ROAD_ADDRESS            VARCHAR2(500),
    ROAD_POSTAL_CODE        VARCHAR2(50),
    BUSINESS_NAME           VARCHAR2(200),
    LAST_UPDATE_DATE        DATE,
    DATA_UPDATE_TYPE        VARCHAR2(20),
    DATA_UPDATE_DATE        VARCHAR2(50),
    BUSINESS_TYPE_NAME      VARCHAR2(100),
    COORD_X                 NUMBER(15,7),
    COORD_Y                 NUMBER(15,7),
    STORE_TYPE_NAME         VARCHAR2(100),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE SEQ_MART_STATISTICS START WITH 1 INCREMENT BY 1;
```

## 2. build.gradle (전체 파일)

```gradle
plugins {
	id 'java'
	id 'org.springframework.boot' version '3.3.5'
	id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.wherehouse'
version = '0.1.0'

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom annotationProcessor
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot 기본
	implementation 'org.springframework.boot:spring-boot-starter'
	implementation 'org.springframework.boot:spring-boot-starter-web'
	implementation 'org.springframework.boot:spring-boot-starter-webflux'
	
	// JPA 및 데이터베이스
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'jakarta.persistence:jakarta.persistence-api:3.1.0'
	implementation 'org.hibernate:hibernate-core:6.2.2.Final'
	implementation 'org.springframework.boot:spring-boot-starter-jdbc'
	implementation 'com.oracle.database.jdbc:ojdbc8:19.8.0.0'
	
	// MyBatis
	implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:2.2.2'
	implementation 'org.mybatis:mybatis-spring:3.0.3'
	
	// XML 처리용
	implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'
	implementation 'javax.xml.bind:jaxb-api:2.3.1'
	implementation 'org.glassfish.jaxb:jaxb-runtime:2.3.1'

	// Lombok
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'

	// OpenCSV
	implementation 'com.opencsv:opencsv:5.9'

	// Spring Security
	implementation 'org.springframework.boot:spring-boot-starter-security'
	
	// JSP
	implementation 'org.apache.tomcat.embed:tomcat-embed-jasper'
	implementation 'org.glassfish.web:jakarta.servlet.jsp.jstl:2.0.0'
	
	// JWT
	implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
	implementation 'io.jsonwebtoken:jjwt-impl:0.11.5'
	implementation 'io.jsonwebtoken:jjwt-jackson:0.11.5'
	implementation group: 'org.javassist', name: 'javassist', version: '3.15.0-GA'
	
	// Redis
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'

	// Elasticsearch
	implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
	
	// JDBC
	implementation 'org.springframework.boot:spring-boot-starter-data-jdbc'
	
	// Validation
	implementation 'org.springframework.boot:spring-boot-starter-validation'

	// 테스트
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'io.projectreactor:reactor-test'
	testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.test {
	useJUnitPlatform()
}

tasks.withType(JavaCompile) {
	options.compilerArgs << '-parameters'
}
```

## 3. application.yml (전체 파일)

```yaml
spring:
  application:
    name: wherehouse

  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@127.0.0.1:1521:xe
    username: SCOTT
    password: tiger

    hikari:
      auto-commit: false
      idle-timeout: 30000
      max-lifetime: 1800000

  data:
    redis:
      host: 43.202.178.156
      port: 6379
      timeout: 0
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
          min-idle: 10
          max-wait: -1ms
          time-between-eviction-runs: 10s

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.OracleDialect
        format_sql: true
        default_batch_fetch_size: 50
        jdbc.fetch_size: 100
        cache.use_query_cache: false
        physical_naming_strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    open-in-view: false

  mvc:
    view:
      prefix: /WEB-INF/view/
      suffix: .jsp

mybatis:
  mapper-locations: classpath:/mapper/*Mapper.xml
  type-aliases-package: com.wherehouse.information.model
  configuration:
    jdbc-type-for-null: null

server:
  port: 8185
  servlet:
    context-path: /wherehouse

logging:
  file:
    name: log/wherehouse.log
  level:
    root: info
    com.wherehouse: debug
    org.hibernate.SQL: debug

# CSV 설정
app:
  csv:
    mart-data-path: classpath:data/대형마트백화점데이터.csv
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "MART_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MartStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mart_seq")
    @SequenceGenerator(name = "mart_seq", sequenceName = "SEQ_MART_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "LOCAL_GOVT_CODE", nullable = false, length = 20)
    private String localGovtCode;

    @Column(name = "MANAGEMENT_NO", nullable = false, length = 50)
    private String managementNo;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 10)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 50)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 10)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 30)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 30)
    private String phoneNumber;

    @Column(name = "LOCATION_AREA")
    private Double locationArea;

    @Column(name = "LOCATION_POSTAL_CODE", length = 20)
    private String locationPostalCode;

    @Column(name = "ADDRESS", length = 300)
    private String address;

    @Column(name = "ROAD_ADDRESS", length = 300)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 100)
    private String businessName;

    @Column(name = "LAST_UPDATE_DATE")
    private LocalDate lastUpdateDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 10)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE", length = 20)
    private String dataUpdateDate;

    @Column(name = "BUSINESS_TYPE_NAME", length = 50)
    private String businessTypeName;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

    @Column(name = "STORE_TYPE_NAME", length = 50)
    private String storeTypeName;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## 5. Repository

```java
package com.wherehouse.safety.repository;

import com.wherehouse.safety.entity.MartStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MartStatisticsRepository extends JpaRepository<MartStatistics, Long> {
    List<MartStatistics> findByBusinessStatusName(String businessStatusName);
    List<MartStatistics> findByBusinessTypeName(String businessTypeName);
    Optional<MartStatistics> findByManagementNo(String managementNo);
    boolean existsByManagementNo(String managementNo);
    
    @Query("SELECT m FROM MartStatistics m WHERE m.businessName LIKE %:name%")
    List<MartStatistics> findByBusinessNameContaining(@Param("name") String name);
    
    @Query("SELECT m FROM MartStatistics m WHERE m.coordX BETWEEN :minX AND :maxX AND m.coordY BETWEEN :minY AND :maxY")
    List<MartStatistics> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX, 
                                             @Param("minY") Double minY, @Param("maxY") Double maxY);
    
    @Query("SELECT COUNT(m) FROM MartStatistics m WHERE m.businessStatusName = :statusName")
    Long countByBusinessStatus(@Param("statusName") String statusName);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.MartStatistics;
import com.wherehouse.safety.repository.MartStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MartDataLoader implements CommandLineRunner {

    private final MartStatisticsRepository martRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.mart-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (martRepository.count() > 0) {
            log.info("대형마트/백화점 데이터 이미 존재. 로딩 스킵");
            return;
        }
        
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            log.info("CSV 파일 경로: {}", csvFilePath);
            log.info("CSV 파일 존재 여부: {}", resource.exists());
            
            if (!resource.exists()) {
                log.error("CSV 파일이 존재하지 않습니다: {}", csvFilePath);
                return;
            }
            
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("총 CSV 행 수: {}", csvData.size());
                
                if (csvData.size() == 0) {
                    log.warn("CSV 파일이 비어있습니다");
                    return;
                }
                
                if (csvData.size() > 0) {
                    log.info("첫 번째 행 컬럼 수: {}", csvData.get(0).length);
                    log.info("첫 번째 행 내용: {}", Arrays.toString(csvData.get(0)));
                }
                
                if (csvData.size() > 1) {
                    log.info("두 번째 행 컬럼 수: {}", csvData.get(1).length);
                    log.info("두 번째 행 내용: {}", Arrays.toString(csvData.get(1)));
                }
                
                int savedCount = 0;
                int skipCount = 0;
                
                // 첫 번째 행은 헤더이므로 스킵
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    
                    if (row.length < 26) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }
                    
                    try {
                        MartStatistics mart = MartStatistics.builder()
                                .localGovtCode(processStringWithLength(row[0], 20, "지자체코드"))
                                .managementNo(processStringWithLength(row[1], 50, "관리번호"))
                                .licenseDate(parseDate(row[2]))
                                .licenseCancelDate(parseDate(row[3]))
                                .businessStatusCode(processStringWithLength(row[4], 10, "영업상태코드"))
                                .businessStatusName(processStringWithLength(row[5], 50, "영업상태명"))
                                .detailStatusCode(processStringWithLength(row[6], 10, "상세상태코드"))
                                .detailStatusName(processStringWithLength(row[7], 30, "상세상태명"))
                                .closureDate(parseDate(row[8]))
                                .suspensionStartDate(parseDate(row[9]))
                                .suspensionEndDate(parseDate(row[10]))
                                .reopenDate(parseDate(row[11]))
                                .phoneNumber(processStringWithLength(row[12], 30, "전화번호"))
                                .locationArea(parseDouble(row[13]))
                                .locationPostalCode(processStringWithLength(row[14], 20, "소재지우편번호"))
                                .address(processStringWithLength(row[15], 300, "주소"))
                                .roadAddress(processStringWithLength(row[16], 300, "도로명주소"))
                                .roadPostalCode(processStringWithLength(row[17], 20, "도로명우편번호"))
                                .businessName(processStringWithLength(row[18], 100, "사업장명"))
                                .lastUpdateDate(parseDate(row[19]))
                                .dataUpdateType(processStringWithLength(row[20], 10, "데이터갱신구분"))
                                .dataUpdateDate(processStringWithLength(row[21], 20, "데이터갱신일자"))
                                .businessTypeName(processStringWithLength(row[22], 50, "업태구분명"))
                                .coordX(parseDouble(row[23]))
                                .coordY(parseDouble(row[24]))
                                .storeTypeName(processStringWithLength(row[25], 50, "점포구분명"))
                                .build();
                        
                        martRepository.save(mart);
                        savedCount++;
                        
                        if (savedCount % 100 == 0) {
                            log.info("{}개 대형마트/백화점 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) {
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }
                
                log.info("=== 대형마트/백화점 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("대형마트/백화점 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private String processString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        String trimmed = value.trim();
        
        // 문자열 길이 검증 및 로깅
        if (trimmed.length() > 300) {
            log.warn("긴 문자열 데이터 발견 (길이: {}): {}", trimmed.length(), 
                    trimmed.length() > 50 ? trimmed.substring(0, 50) + "..." : trimmed);
            return trimmed.substring(0, 300); // 최대 길이로 자름
        }
        
        return trimmed;
    }

    private String processStringWithLength(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        String trimmed = value.trim();
        
        if (trimmed.length() > maxLength) {
            log.warn("{} 필드 길이 초과 (실제: {}, 최대: {}): {}", 
                    fieldName, trimmed.length(), maxLength, 
                    trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed);
            return trimmed.substring(0, maxLength);
        }
        
        return trimmed;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return LocalDate.of(1900, 1, 1); // 기본 날짜
        }
        
        String trimmed = dateStr.trim();
        
        // 시간이 포함된 경우 날짜 부분만 추출 (예: "2025-03-10 16:43" -> "2025-03-10")
        if (trimmed.contains(" ")) {
            trimmed = trimmed.split(" ")[0];
        }
        
        try {
            // YYYY-MM-DD 형식으로 파싱
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                // YYYY-M-D 형식도 시도
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ex) {
                log.debug("날짜 파싱 실패: {}", dateStr);
                return LocalDate.of(1900, 1, 1); // 기본 날짜
            }
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", value);
            return 0.0;
        }
    }
}
```

파일 위치: `src/main/resources/data/대형마트백화점데이터.csv`