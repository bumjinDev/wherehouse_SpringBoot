# CCTV 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
CREATE TABLE CCTV_STATISTICS (
    ID                      NUMBER(19) PRIMARY KEY,
    MANAGEMENT_AGENCY       VARCHAR2(100) DEFAULT '데이터없음',
    ROAD_ADDRESS           VARCHAR2(200) DEFAULT '데이터없음',
    JIBUN_ADDRESS          VARCHAR2(200) DEFAULT '데이터없음',
    INSTALL_PURPOSE        VARCHAR2(50) DEFAULT '데이터없음',
    CAMERA_COUNT           NUMBER(6) DEFAULT 0,
    CAMERA_PIXEL           NUMBER(6) DEFAULT 0,
    SHOOTING_DIRECTION     VARCHAR2(100) DEFAULT '데이터없음',
    STORAGE_DAYS           NUMBER(4) DEFAULT 0,
    INSTALL_DATE           VARCHAR2(20) DEFAULT '데이터없음',
    MANAGEMENT_PHONE       VARCHAR2(20) DEFAULT '데이터없음',
    WGS84_LATITUDE         NUMBER(10,7) DEFAULT 0,
    WGS84_LONGITUDE        NUMBER(10,7) DEFAULT 0,
    DATA_BASE_DATE         VARCHAR2(20) DEFAULT '데이터없음',
    CREATED_AT             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT UK_CCTV_ADDRESS UNIQUE (ROAD_ADDRESS, JIBUN_ADDRESS)
);

CREATE SEQUENCE SEQ_CCTV_STATISTICS START WITH 1 INCREMENT BY 1;
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
    cctv-data-path: classpath:data/cctv_data.csv
```

## 4. JPA Entity

```java
package com.wherehouse.safety.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "CCTV_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CctvStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cctv_seq")
    @SequenceGenerator(name = "cctv_seq", sequenceName = "SEQ_CCTV_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_AGENCY", length = 100)
    private String managementAgency = "데이터없음";

    @Column(name = "ROAD_ADDRESS", length = 200)
    private String roadAddress = "데이터없음";

    @Column(name = "JIBUN_ADDRESS", length = 200)
    private String jibunAddress = "데이터없음";

    @Column(name = "INSTALL_PURPOSE", length = 50)
    private String installPurpose = "데이터없음";

    @Column(name = "CAMERA_COUNT")
    private Integer cameraCount = 0;

    @Column(name = "CAMERA_PIXEL")
    private Integer cameraPixel = 0;

    @Column(name = "SHOOTING_DIRECTION", length = 100)
    private String shootingDirection = "데이터없음";

    @Column(name = "STORAGE_DAYS")
    private Integer storageDays = 0;

    @Column(name = "INSTALL_DATE", length = 20)
    private String installDate = "데이터없음";

    @Column(name = "MANAGEMENT_PHONE", length = 20)
    private String managementPhone = "데이터없음";

    @Column(name = "WGS84_LATITUDE", precision = 10, scale = 7)
    private Double wgs84Latitude = 0.0;

    @Column(name = "WGS84_LONGITUDE", precision = 10, scale = 7)
    private Double wgs84Longitude = 0.0;

    @Column(name = "DATA_BASE_DATE", length = 20)
    private String dataBaseDate = "데이터없음";

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

import com.wherehouse.safety.entity.CctvStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CctvStatisticsRepository extends JpaRepository<CctvStatistics, Long> {
    Optional<CctvStatistics> findByRoadAddress(String roadAddress);
    List<CctvStatistics> findAllByOrderByCameraCountDesc();
    boolean existsByRoadAddress(String roadAddress);
}
```

## 6. CSV 로더 Component

```java
package com.wherehouse.safety.component;

import com.opencsv.CSVReader;
import com.wherehouse.safety.entity.CctvStatistics;
import com.wherehouse.safety.repository.CctvStatisticsRepository;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CctvDataLoader implements CommandLineRunner {

    private final CctvStatisticsRepository cctvRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.cctv-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (cctvRepository.count() > 0) {
            log.info("데이터 이미 존재. 로딩 스킵");
            return;
        }
        
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                
                int savedCount = 0;
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    if (row.length >= 14) {
                        CctvStatistics cctv = CctvStatistics.builder()
                                .managementAgency(parseString(row[1]))
                                .roadAddress(parseString(row[2]))
                                .jibunAddress(parseString(row[3]))
                                .installPurpose(parseString(row[4]))
                                .cameraCount(parseInteger(row[5]))
                                .cameraPixel(parseInteger(row[6]))
                                .shootingDirection(parseString(row[7]))
                                .storageDays(parseInteger(row[8]))
                                .installDate(parseString(row[9]))
                                .managementPhone(parseString(row[10]))
                                .wgs84Latitude(parseDouble(row[11]))
                                .wgs84Longitude(parseDouble(row[12]))
                                .dataBaseDate(parseString(row[13]))
                                .build();
                        
                        cctvRepository.save(cctv);
                        savedCount++;
                    }
                }
                log.info("{} 개 CCTV 데이터 저장 완료", savedCount);
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

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
```

파일 위치: `src/main/resources/data/cctv_data.csv`