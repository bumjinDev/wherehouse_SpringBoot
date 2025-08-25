# 가로등 원본 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE STREETLIGHT_RAW_DATA (
    ID                      NUMBER(19) PRIMARY KEY,
    MANAGEMENT_NUMBER       VARCHAR2(50) NOT NULL,
    LATITUDE                NUMBER(10,7) DEFAULT 0,
    LONGITUDE               NUMBER(10,7) DEFAULT 0,
    CREATED_AT              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_STREETLIGHT_RAW_MGMT_NUMBER UNIQUE (MANAGEMENT_NUMBER)
);

CREATE SEQUENCE SEQ_STREETLIGHT_RAW_DATA START WITH 1 INCREMENT BY 1;
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
    streetlight-data-path: classpath:data/streetlight_raw_data.csv
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "STREETLIGHT_RAW_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreetlightRawData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "streetlight_raw_seq")
    @SequenceGenerator(name = "streetlight_raw_seq", sequenceName = "SEQ_STREETLIGHT_RAW_DATA", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_NUMBER", nullable = false, length = 50)
    private String managementNumber;

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude = BigDecimal.ZERO;

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude = BigDecimal.ZERO;

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

import com.wherehouse.safety.entity.StreetlightRawData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StreetlightRawDataRepository extends JpaRepository<StreetlightRawData, Long> {
    Optional<StreetlightRawData> findByManagementNumber(String managementNumber);
    List<StreetlightRawData> findAllByOrderByManagementNumberAsc();
    boolean existsByManagementNumber(String managementNumber);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.StreetlightRawData;
import com.wherehouse.safety.repository.StreetlightRawDataRepository;
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
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreetlightRawDataLoader implements CommandLineRunner {

    private final StreetlightRawDataRepository streetlightRawDataRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.streetlight-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (streetlightRawDataRepository.count() > 0) {
            log.info("원본 데이터 이미 존재. 로딩 스킵");
            return;
        }
        
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                
                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;
                
                log.info("가로등 원본 데이터 로딩 시작 - 총 {} 건", totalRows);
                
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    
                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률: {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }
                    
                    if (row.length >= 3) {
                        try {
                            StreetlightRawData streetlightRaw = StreetlightRawData.builder()
                                    .managementNumber(parseString(row[0]))
                                    .latitude(parseBigDecimal(row[1]))
                                    .longitude(parseBigDecimal(row[2]))
                                    .build();
                            
                            streetlightRawDataRepository.save(streetlightRaw);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 3개)", i, row.length);
                    }
                }
                
                log.info("가로등 원본 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건", 
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

파일 위치: `src/main/resources/data/streetlight_raw_data.csv`

## 추후 확장 예정 테이블

```sql
-- 가공된 가로등 데이터 (구/동 단위 매핑 후)
CREATE TABLE STREETLIGHT_PROCESSED_DATA (
    ID                      NUMBER(19) PRIMARY KEY,
    RAW_DATA_ID             NUMBER(19) NOT NULL,
    MANAGEMENT_NUMBER       VARCHAR2(50) NOT NULL,
    DISTRICT_NAME           VARCHAR2(50),  -- 구 정보
    DONG_NAME               VARCHAR2(50),  -- 동 정보
    LATITUDE                NUMBER(10,7) DEFAULT 0,
    LONGITUDE               NUMBER(10,7) DEFAULT 0,
    GRID_X                  NUMBER(10),    -- 격자 X 좌표
    GRID_Y                  NUMBER(10),    -- 격자 Y 좌표
    PROCESSED_AT            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT FK_STREETLIGHT_RAW FOREIGN KEY (RAW_DATA_ID) 
        REFERENCES STREETLIGHT_RAW_DATA(ID)
);

CREATE SEQUENCE SEQ_STREETLIGHT_PROCESSED_DATA START WITH 1 INCREMENT BY 1;
```