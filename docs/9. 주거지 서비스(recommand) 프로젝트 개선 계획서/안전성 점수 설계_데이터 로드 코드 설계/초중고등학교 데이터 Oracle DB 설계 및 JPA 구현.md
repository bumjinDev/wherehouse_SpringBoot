# 초중고등학교 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE SCHOOL_STATISTICS (
    ID                      NUMBER(19) PRIMARY KEY,
    SCHOOL_ID               VARCHAR2(20) NOT NULL,
    SCHOOL_NAME             VARCHAR2(100) NOT NULL,
    SCHOOL_LEVEL            VARCHAR2(20) NOT NULL,
    ESTABLISHMENT_DATE      DATE,
    ESTABLISHMENT_TYPE      VARCHAR2(20),
    MAIN_BRANCH_TYPE        VARCHAR2(10),
    OPERATION_STATUS        VARCHAR2(20),
    LOCATION_ADDRESS        VARCHAR2(200),
    ROAD_ADDRESS            VARCHAR2(200),
    EDUCATION_OFFICE_CODE   VARCHAR2(20),
    EDUCATION_OFFICE_NAME   VARCHAR2(50),
    SUPPORT_OFFICE_CODE     VARCHAR2(20),
    SUPPORT_OFFICE_NAME     VARCHAR2(50),
    CREATE_DATE             DATE,
    MODIFY_DATE             DATE,
    LATITUDE                NUMBER,
    LONGITUDE               NUMBER,
    DATA_BASE_DATE          DATE,
    PROVIDER_CODE           VARCHAR2(20),
    PROVIDER_NAME           VARCHAR2(50),
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_SCHOOL_ID UNIQUE (SCHOOL_ID)
);

CREATE SEQUENCE SEQ_SCHOOL_STATISTICS START WITH 1 INCREMENT BY 1;
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
    school-data-path: classpath:data/전국초중등학교위치표준데이터.csv
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
@Table(name = "SCHOOL_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "school_seq")
    @SequenceGenerator(name = "school_seq", sequenceName = "SEQ_SCHOOL_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_ID", nullable = false, length = 20)
    private String schoolId;

    @Column(name = "SCHOOL_NAME", nullable = false, length = 100)
    private String schoolName;

    @Column(name = "SCHOOL_LEVEL", nullable = false, length = 20)
    private String schoolLevel;

    @Column(name = "ESTABLISHMENT_DATE")
    private LocalDate establishmentDate;

    @Column(name = "ESTABLISHMENT_TYPE", length = 20)
    private String establishmentType;

    @Column(name = "MAIN_BRANCH_TYPE", length = 10)
    private String mainBranchType;

    @Column(name = "OPERATION_STATUS", length = 20)
    private String operationStatus;

    @Column(name = "LOCATION_ADDRESS", length = 200)
    private String locationAddress;

    @Column(name = "ROAD_ADDRESS", length = 200)
    private String roadAddress;

    @Column(name = "EDUCATION_OFFICE_CODE", length = 20)
    private String educationOfficeCode;

    @Column(name = "EDUCATION_OFFICE_NAME", length = 50)
    private String educationOfficeName;

    @Column(name = "SUPPORT_OFFICE_CODE", length = 20)
    private String supportOfficeCode;

    @Column(name = "SUPPORT_OFFICE_NAME", length = 50)
    private String supportOfficeName;

    @Column(name = "CREATE_DATE")
    private LocalDate createDate;

    @Column(name = "MODIFY_DATE")
    private LocalDate modifyDate;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "DATA_BASE_DATE")
    private LocalDate dataBaseDate;

    @Column(name = "PROVIDER_CODE", length = 20)
    private String providerCode;

    @Column(name = "PROVIDER_NAME", length = 50)
    private String providerName;

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

import com.wherehouse.safety.entity.SchoolStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolStatisticsRepository extends JpaRepository<SchoolStatistics, Long> {
    Optional<SchoolStatistics> findBySchoolId(String schoolId);
    List<SchoolStatistics> findBySchoolLevel(String schoolLevel);
    List<SchoolStatistics> findByOperationStatus(String operationStatus);
    boolean existsBySchoolId(String schoolId);
    
    @Query("SELECT s FROM SchoolStatistics s WHERE s.educationOfficeName LIKE %:officeName%")
    List<SchoolStatistics> findByEducationOfficeNameContaining(@Param("officeName") String officeName);
    
    @Query("SELECT s FROM SchoolStatistics s WHERE s.latitude BETWEEN :minLat AND :maxLat AND s.longitude BETWEEN :minLon AND :maxLon")
    List<SchoolStatistics> findByLocationBounds(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat, 
                                               @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.SchoolStatistics;
import com.wherehouse.safety.repository.SchoolStatisticsRepository;
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
public class SchoolDataLoader implements CommandLineRunner {

    private final SchoolStatisticsRepository schoolRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.school-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (schoolRepository.count() > 0) {
            log.info("학교 데이터 이미 존재. 로딩 스킵");
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
                    
                    if (row.length < 20) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }
                    
                    try {
                        SchoolStatistics school = SchoolStatistics.builder()
                                .schoolId(row[0].trim())
                                .schoolName(row[1].trim())
                                .schoolLevel(row[2].trim())
                                .establishmentDate(parseDate(row[3]))
                                .establishmentType(row[4].trim())
                                .mainBranchType(row[5].trim())
                                .operationStatus(row[6].trim())
                                .locationAddress(row[7].trim())
                                .roadAddress(row[8].trim())
                                .educationOfficeCode(row[9].trim())
                                .educationOfficeName(row[10].trim())
                                .supportOfficeCode(row[11].trim())
                                .supportOfficeName(row[12].trim())
                                .createDate(parseDate(row[13]))
                                .modifyDate(parseDate(row[14]))
                                .latitude(parseDouble(row[15]))
                                .longitude(parseDouble(row[16]))
                                .dataBaseDate(parseDate(row[17]))
                                .providerCode(row[18].trim())
                                .providerName(row.length > 19 ? row[19].trim() : "")
                                .build();
                        
                        schoolRepository.save(school);
                        savedCount++;
                        
                        if (savedCount % 1000 == 0) {
                            log.info("{}개 학교 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) { // 처음 5개 오류만 상세 로그 출력
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }
                
                log.info("=== 학교 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("학교 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return null;
        }
        try {
            // YYYY-MM-DD 형식으로 파싱
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                // YYYY-M-D 형식도 시도
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ex) {
                log.debug("날짜 파싱 실패: {}", dateStr);
                return null;
            }
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", value);
            return null;
        }
    }
}
```

파일 위치: `src/main/resources/data/전국초중등학교위치표준데이터.csv`