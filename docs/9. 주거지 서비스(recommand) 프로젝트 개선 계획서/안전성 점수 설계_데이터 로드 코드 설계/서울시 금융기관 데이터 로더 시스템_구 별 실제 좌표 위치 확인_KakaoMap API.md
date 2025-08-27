# 은행 데이터 Oracle DB 설계 및 JPA 구현

## 1. Oracle 테이블 DDL

```sql
-- 1단계: 애플리케이션 완전 종료 후 실행
-- 기존 테이블과 데이터를 완전히 삭제
DROP TABLE BANK_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_BANK_STATISTICS;

-- 2단계: 완전히 새로운 테이블 생성 (모든 컬럼을 최대한 크게)
CREATE TABLE BANK_STATISTICS (
    ID NUMBER,                          -- 제한 없음
    KAKAO_PLACE_ID VARCHAR2(4000),      -- 최대 크기
    PLACE_NAME VARCHAR2(4000),          -- 최대 크기
    CATEGORY_NAME VARCHAR2(4000),       -- 최대 크기
    CATEGORY_GROUP_CODE VARCHAR2(4000), -- 최대 크기
    PHONE VARCHAR2(4000),               -- 최대 크기
    ADDRESS_NAME VARCHAR2(4000),        -- 최대 크기
    ROAD_ADDRESS_NAME VARCHAR2(4000),   -- 최대 크기
    LONGITUDE NUMBER,                   -- 제한 없음
    LATITUDE NUMBER,                    -- 제한 없음
    PLACE_URL VARCHAR2(4000),           -- 최대 크기
    DISTRICT VARCHAR2(4000),            -- 최대 크기
    BANK_BRAND VARCHAR2(4000),          -- 최대 크기
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3단계: 시퀀스 생성
CREATE SEQUENCE SEQ_BANK_STATISTICS 
    START WITH 1 
    INCREMENT BY 1 
    NOCACHE
    NOCYCLE;

-- 4단계: 확인
DESC BANK_STATISTICS;
SELECT COUNT(*) FROM BANK_STATISTICS;

-- 5단계: 간단한 테스트 삽입
INSERT INTO BANK_STATISTICS (ID, PLACE_NAME) VALUES (1, '테스트');
SELECT * FROM BANK_STATISTICS;
DELETE FROM BANK_STATISTICS WHERE ID = 1;
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
	
	// HTTP 클라이언트
	implementation 'org.springframework.boot:spring-boot-starter-webflux'
	implementation 'org.apache.httpcomponents:httpclient:4.5.14'
	
	// JSON 처리
	implementation 'com.fasterxml.jackson.core:jackson-databind'
	implementation 'com.fasterxml.jackson.core:jackson-core'
	implementation 'com.fasterxml.jackson.core:jackson-annotations'

	// Lombok
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'

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

# 카카오 API 설정
kakao:
  rest-api-key: YOUR_KAKAO_REST_API_KEY_HERE
  local-api:
    base-url: https://dapi.kakao.com/v2/local
    search-radius: 5000
    max-page: 45
    page-size: 15
    request-delay: 100
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
@Table(name = "BANK_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_seq")
    @SequenceGenerator(name = "bank_seq", sequenceName = "SEQ_BANK_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "KAKAO_PLACE_ID", length = 50)
    private String kakaoPlaceId;

    @Column(name = "PLACE_NAME", nullable = false, length = 200)
    private String placeName;

    @Column(name = "CATEGORY_NAME", length = 100)
    private String categoryName;

    @Column(name = "CATEGORY_GROUP_CODE", length = 10)
    private String categoryGroupCode;

    @Column(name = "PHONE", length = 20)
    private String phone;

    @Column(name = "ADDRESS_NAME", length = 300)
    private String addressName;

    @Column(name = "ROAD_ADDRESS_NAME", length = 300)
    private String roadAddressName;

    @Column(name = "LONGITUDE", precision = 10, scale = 8)
    private BigDecimal longitude;

    @Column(name = "LATITUDE", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "PLACE_URL", length = 500)
    private String placeUrl;

    @Column(name = "DISTRICT", length = 50)
    private String district;

    @Column(name = "BANK_BRAND", length = 50)
    private String bankBrand;

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

import com.wherehouse.safety.entity.BankStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankStatisticsRepository extends JpaRepository<BankStatistics, Long> {
    
    Optional<BankStatistics> findByKakaoPlaceId(String kakaoPlaceId);
    
    List<BankStatistics> findByDistrict(String district);
    
    List<BankStatistics> findByBankBrand(String bankBrand);
    
    boolean existsByKakaoPlaceId(String kakaoPlaceId);
    
    @Query("SELECT COUNT(b) FROM BankStatistics b WHERE b.district = :district")
    long countByDistrict(@Param("district") String district);
    
    @Query("SELECT b.district, COUNT(b) FROM BankStatistics b GROUP BY b.district ORDER BY COUNT(b) DESC")
    List<Object[]> countBanksByDistrict();
}
```

## 6. 카카오 API 응답 DTO

```java
package com.wherehouse.safety.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class KakaoLocalApiResponse {
    private Meta meta;
    private List<Document> documents;
    
    @Data
    public static class Meta {
        @JsonProperty("total_count")
        private int totalCount;
        
        @JsonProperty("pageable_count") 
        private int pageableCount;
        
        @JsonProperty("is_end")
        private boolean isEnd;
    }
    
    @Data
    public static class Document {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("place_name")
        private String placeName;
        
        @JsonProperty("category_name")
        private String categoryName;
        
        @JsonProperty("category_group_code")
        private String categoryGroupCode;
        
        @JsonProperty("phone")
        private String phone;
        
        @JsonProperty("address_name")
        private String addressName;
        
        @JsonProperty("road_address_name")
        private String roadAddressName;
        
        @JsonProperty("x")
        private String longitude;
        
        @JsonProperty("y")
        private String latitude;
        
        @JsonProperty("place_url")
        private String placeUrl;
        
        @JsonProperty("distance")
        private String distance;
    }
}
```

## 7. 수집 진행 상황 및 오류 추적 DTO

```java
package com.wherehouse.safety.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class CollectionProgress {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AtomicInteger totalProcessed = new AtomicInteger(0);
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger errorCount = new AtomicInteger(0);
    private AtomicInteger skipCount = new AtomicInteger(0);
    private List<ErrorDetail> errors = new ArrayList<>();
    private String currentTask = "";
    private String currentDistrict = "";
    private int currentPage = 0;
    private int totalDistricts = 25;
    private int completedDistricts = 0;
    
    @Data
    public static class ErrorDetail {
        private LocalDateTime timestamp;
        private String errorType;
        private String errorMessage;
        private String context;
        private String placeName;
        private String placeId;
        private String district;
        private String stackTrace;
        
        public ErrorDetail(String errorType, String errorMessage, String context) {
            this.timestamp = LocalDateTime.now();
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.context = context;
        }
        
        public ErrorDetail(String errorType, String errorMessage, String context, 
                          String placeName, String placeId, String district) {
            this(errorType, errorMessage, context);
            this.placeName = placeName;
            this.placeId = placeId;
            this.district = district;
        }
    }
    
    public void addError(ErrorDetail error) {
        this.errors.add(error);
        this.errorCount.incrementAndGet();
    }
    
    public void incrementSuccess() {
        this.successCount.incrementAndGet();
        this.totalProcessed.incrementAndGet();
    }
    
    public void incrementSkip() {
        this.skipCount.incrementAndGet();
        this.totalProcessed.incrementAndGet();
    }
    
    public double getProgressPercentage() {
        if (totalDistricts == 0) return 0.0;
        return (double) completedDistricts / totalDistricts * 100.0;
    }
    
    public String getProgressStatus() {
        return String.format("[%d/%d 구 완료] 성공: %d, 오류: %d, 스킵: %d", 
                           completedDistricts, totalDistricts, 
                           successCount.get(), errorCount.get(), skipCount.get());
    }
}
```

## 8. 서울시 구별 좌표 데이터

```java
package com.wherehouse.safety.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
public class SeoulDistrictCoords {
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    public static final List<SeoulDistrictCoords> SEOUL_DISTRICTS = Arrays.asList(
        new SeoulDistrictCoords("강남구", new BigDecimal("37.5173"), new BigDecimal("127.0473")),
        new SeoulDistrictCoords("강동구", new BigDecimal("37.5301"), new BigDecimal("127.1238")),
        new SeoulDistrictCoords("강북구", new BigDecimal("37.6369"), new BigDecimal("127.0254")),
        new SeoulDistrictCoords("강서구", new BigDecimal("37.5509"), new BigDecimal("126.8495")),
        new SeoulDistrictCoords("관악구", new BigDecimal("37.4781"), new BigDecimal("126.9515")),
        new SeoulDistrictCoords("광진구", new BigDecimal("37.5384"), new BigDecimal("127.0822")),
        new SeoulDistrictCoords("구로구", new BigDecimal("37.4954"), new BigDecimal("126.8874")),
        new SeoulDistrictCoords("금천구", new BigDecimal("37.4569"), new BigDecimal("126.8896")),
        new SeoulDistrictCoords("노원구", new BigDecimal("37.6542"), new BigDecimal("127.0568")),
        new SeoulDistrictCoords("도봉구", new BigDecimal("37.6688"), new BigDecimal("127.0471")),
        new SeoulDistrictCoords("동대문구", new BigDecimal("37.5744"), new BigDecimal("127.0396")),
        new SeoulDistrictCoords("동작구", new BigDecimal("37.5124"), new BigDecimal("126.9393")),
        new SeoulDistrictCoords("마포구", new BigDecimal("37.5663"), new BigDecimal("126.9019")),
        new SeoulDistrictCoords("서대문구", new BigDecimal("37.5794"), new BigDecimal("126.9368")),
        new SeoulDistrictCoords("서초구", new BigDecimal("37.4837"), new BigDecimal("127.0324")),
        new SeoulDistrictCoords("성동구", new BigDecimal("37.5635"), new BigDecimal("127.0370")),
        new SeoulDistrictCoords("성북구", new BigDecimal("37.5894"), new BigDecimal("127.0167")),
        new SeoulDistrictCoords("송파구", new BigDecimal("37.5145"), new BigDecimal("127.1059")),
        new SeoulDistrictCoords("양천구", new BigDecimal("37.5168"), new BigDecimal("126.8664")),
        new SeoulDistrictCoords("영등포구", new BigDecimal("37.5264"), new BigDecimal("126.8962")),
        new SeoulDistrictCoords("용산구", new BigDecimal("37.5326"), new BigDecimal("126.9906")),
        new SeoulDistrictCoords("은평구", new BigDecimal("37.6026"), new BigDecimal("126.9292")),
        new SeoulDistrictCoords("종로구", new BigDecimal("37.5735"), new BigDecimal("126.9788")),
        new SeoulDistrictCoords("중구", new BigDecimal("37.5640"), new BigDecimal("126.9979")),
        new SeoulDistrictCoords("중랑구", new BigDecimal("37.6063"), new BigDecimal("127.0926"))
    );
}
```

## 9. 카카오 API 클라이언트

```java
package com.wherehouse.safety.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherehouse.safety.dto.KakaoLocalApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoLocalApiClient {

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.local-api.base-url}")
    private String baseUrl;

    @Value("${kakao.local-api.search-radius}")
    private int searchRadius;

    @Value("${kakao.local-api.page-size}")
    private int pageSize;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 키워드로 장소 검색
     */
    public KakaoLocalApiResponse searchByKeyword(String query, BigDecimal x, BigDecimal y, int page) {
        String url = baseUrl + "/search/keyword.json";
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("query", query)
                .queryParam("x", x.toString())
                .queryParam("y", y.toString())
                .queryParam("radius", searchRadius)
                .queryParam("page", page)
                .queryParam("size", pageSize)
                .queryParam("sort", "distance");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalApiResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    KakaoLocalApiResponse.class
            );
            
            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 키워드 검색 실패: query={}, error={}", query, e.getMessage());
            throw e;
        }
    }

    /**
     * 카테고리로 장소 검색 (BK9: 은행)
     */
    public KakaoLocalApiResponse searchByCategory(String categoryCode, BigDecimal x, BigDecimal y, int page) {
        String url = baseUrl + "/search/category.json";
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", x.toString())
                .queryParam("y", y.toString())
                .queryParam("radius", searchRadius)
                .queryParam("page", page)
                .queryParam("size", pageSize)
                .queryParam("sort", "distance");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + restApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<KakaoLocalApiResponse> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    KakaoLocalApiResponse.class
            );
            
            return response.getBody();
        } catch (Exception e) {
            log.error("카카오 API 카테고리 검색 실패: category={}, error={}", categoryCode, e.getMessage());
            throw e;
        }
    }
}
```

## 10. 은행 데이터 수집 로더 Component (진행상황 및 오류추적 강화)

```java
package com.wherehouse.safety.component;

import com.wherehouse.safety.client.KakaoLocalApiClient;
import com.wherehouse.safety.config.SeoulDistrictCoords;
import com.wherehouse.safety.dto.CollectionProgress;
import com.wherehouse.safety.dto.KakaoLocalApiResponse;
import com.wherehouse.safety.entity.BankStatistics;
import com.wherehouse.safety.repository.BankStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankDataLoader implements CommandLineRunner {

    private final BankStatisticsRepository bankRepository;
    private final KakaoLocalApiClient kakaoApiClient;

    @Value("${kakao.local-api.max-page}")
    private int maxPage;

    @Value("${kakao.local-api.request-delay}")
    private long requestDelay;

    private static final String BANK_CATEGORY_CODE = "BK9";
    
    private static final List<String> MAJOR_BANKS = Arrays.asList(
            "KB국민은행", "신한은행", "우리은행", "하나은행", "IBK기업은행",
            "NH농협은행", "부산은행", "대구은행", "경남은행", "광주은행",
            "전북은행", "제주은행"
    );

    private CollectionProgress progress = new CollectionProgress();

    @Override
    @Transactional
    public void run(String... args) {
        progress.setStartTime(LocalDateTime.now());
        
        try {
            // 기존 데이터 체크
            long existingCount = bankRepository.count();
            if (existingCount > 0) {
                log.info("은행 데이터 이미 존재 ({} 개). 로딩 스킵", existingCount);
                return;
            }

            log.info("🏦 서울시 은행 지점 수집을 시작합니다...");
            log.info("📋 수집 설정: 최대 {}페이지, {}ms 대기, {} 구 대상", maxPage, requestDelay, SeoulDistrictCoords.SEOUL_DISTRICTS.size());
            
            Set<String> processedIds = new HashSet<>();

            // 전략 1: 구별 카테고리 검색
            progress.setCurrentTask("구별 카테고리 검색");
            collectBanksByDistrict(processedIds);
            
            // 전략 2: 은행 브랜드별 키워드 검색  
            progress.setCurrentTask("브랜드별 키워드 검색");
            collectBanksByBrand(processedIds);

            progress.setEndTime(LocalDateTime.now());
            printFinalSummary();

        } catch (Exception e) {
            progress.addError(new CollectionProgress.ErrorDetail(
                "SYSTEM_ERROR", 
                "전체 시스템 오류: " + e.getMessage(),
                "BankDataLoader.run()",
                null, null, null
            ));
            log.error("🚨 시스템 전체 오류 발생", e);
            throw e;
        }
    }

    /**
     * 구별 은행 카테고리 검색 (상세 진행상황 추적)
     */
    private void collectBanksByDistrict(Set<String> processedIds) {
        log.info("📍 구별 은행 카테고리 검색 시작 ({} 개 구)", SeoulDistrictCoords.SEOUL_DISTRICTS.size());
        
        int districtIndex = 0;
        for (SeoulDistrictCoords district : SeoulDistrictCoords.SEOUL_DISTRICTS) {
            districtIndex++;
            progress.setCurrentDistrict(district.getName());
            progress.setCurrentPage(0);
            
            log.info("🔍 [{}/{}] {} 은행 검색 중...", districtIndex, SeoulDistrictCoords.SEOUL_DISTRICTS.size(), district.getName());

            int pageCount = 0;
            int districtBankCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    // API 호출 시작 로그
                    log.debug("  📄 페이지 {} 요청 중... (좌표: {}, {})", page, district.getLatitude(), district.getLongitude());

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByCategory(
                                BANK_CATEGORY_CODE,
                                district.getLongitude(),
                                district.getLatitude(),
                                page
                        );
                    } catch (HttpClientErrorException e) {
                        String errorMsg = String.format("카카오 API 클라이언트 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_CLIENT_ERROR", errorMsg, 
                            String.format("구별검색-%s-페이지%d", district.getName(), page),
                            null, null, district.getName()
                        ));
                        log.warn("  ⚠️ API 클라이언트 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_SERVER_ERROR", errorMsg,
                            String.format("구별검색-%s-페이지%d", district.getName(), page),
                            null, null, district.getName()
                        ));
                        log.warn("  ⚠️ API 서버 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "NETWORK_ERROR", errorMsg,
                            String.format("구별검색-%s-페이지%d", district.getName(), page),
                            null, null, district.getName()
                        ));
                        log.warn("  ⚠️ 네트워크 오류: {} - 페이지 {} 스킵 후 재시도", district.getName(), page);
                        
                        // 네트워크 오류시 더 긴 대기 후 재시도
                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_UNKNOWN_ERROR", errorMsg,
                            String.format("구별검색-%s-페이지%d", district.getName(), page),
                            null, null, district.getName()
                        ));
                        log.error("  🚨 예상치 못한 API 오류: {} - 페이지 {}", district.getName(), page, e);
                        hasError = true;
                        break;
                    }

                    // 응답 데이터 검증
                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                            String.format("구별검색-%s-페이지%d", district.getName(), page),
                            null, null, district.getName()
                        ));
                        log.warn("  ⚠️ 빈 응답: {} - 페이지 {}", district.getName(), page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  📄 페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  📄 페이지 {} 응답: {} 개 은행 발견", page, response.getDocuments().size());

                    // 각 은행 데이터 처리
                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            // 이미 처리된 경우 스킵
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    🔄 중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            // 서울시 주소가 아닌 경우 스킵
                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    🗺️ 서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            // 엔티티 변환 및 저장
                            BankStatistics bank = convertToEntity(doc, district.getName());
                            
                            try {
                                bankRepository.save(bank);
                                processedIds.add(doc.getId());
                                districtBankCount++;
                                progress.incrementSuccess();
                                
                                log.trace("    ✅ 저장완료: {} - {}", bank.getPlaceName(), bank.getAddressName());
                                
                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반 (중복 키 등): %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                    "DATA_INTEGRITY_ERROR", errorMsg,
                                    String.format("구별검색-%s-페이지%d", district.getName(), page),
                                    doc.getPlaceName(), doc.getId(), district.getName()
                                ));
                                log.debug("    ⚠️ 데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());
                                
                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                    "DATABASE_ERROR", errorMsg,
                                    String.format("구별검색-%s-페이지%d", district.getName(), page),
                                    doc.getPlaceName(), doc.getId(), district.getName()
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);
                                
                                log.warn("    🚨 DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            String errorMsg = String.format("데이터 처리 오류: %s", e.getMessage());
                            CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                "DATA_PROCESSING_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                doc.getPlaceName(), doc.getId(), district.getName()
                            );
                            error.setStackTrace(getStackTrace(e));
                            progress.addError(error);
                            
                            log.error("    🚨 데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    // 마지막 페이지 확인
                    if (response.getMeta().isEnd()) {
                        log.debug("  📄 마지막 페이지 도달 - 검색 완료");
                        break;
                    }

                    // API 호출 간격 조정
                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                String errorMsg = String.format("구별 검색 전체 실패: %s", e.getMessage());
                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                    "DISTRICT_SEARCH_ERROR", errorMsg,
                    String.format("구별검색-%s-전체", district.getName()),
                    null, null, district.getName()
                );
                error.setStackTrace(getStackTrace(e));
                progress.addError(error);
                
                log.error("🚨 {} 전체 검색 실패", district.getName(), e);
                hasError = true;
            }

            // 구별 완료 로그
            progress.setCompletedDistricts(progress.getCompletedDistricts() + 1);
            String statusIcon = hasError ? "⚠️" : "✅";
            log.info("  {} {} 완료: {} 개 은행, {} 페이지 검색 | {}", 
                    statusIcon, district.getName(), districtBankCount, pageCount, progress.getProgressStatus());
        }

        log.info("✅ 구별 검색 완료 | 전체 진행률: {:.1f}% | {}", 
                progress.getProgressPercentage(), progress.getProgressStatus());
    }

    /**
     * 은행 브랜드별 키워드 검색 (상세 진행상황 추적)
     */
    private void collectBanksByBrand(Set<String> processedIds) {
        log.info("🏢 브랜드별 은행 키워드 검색 시작 ({} 개 브랜드)", MAJOR_BANKS.size());

        // 서울 중심부 좌표 (시청 기준)
        BigDecimal seoulCenterX = new BigDecimal("126.9780");
        BigDecimal seoulCenterY = new BigDecimal("37.5665");

        int brandIndex = 0;
        for (String bankBrand : MAJOR_BANKS) {
            brandIndex++;
            progress.setCurrentDistrict(bankBrand);
            progress.setCurrentPage(0);
            
            log.info("🔍 [{}/{}] {} 검색 중...", brandIndex, MAJOR_BANKS.size(), bankBrand);

            String query = bankBrand + " 서울";
            int pageCount = 0;
            int brandBankCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    log.debug("  📄 페이지 {} 요청 중... (키워드: {})", page, query);

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByKeyword(
                                query,
                                seoulCenterX,
                                seoulCenterY,
                                page
                        );
                    } catch (HttpClientErrorException e) {
                        String errorMsg = String.format("카카오 API 클라이언트 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_CLIENT_ERROR", errorMsg,
                            String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                            null, null, bankBrand
                        ));
                        log.warn("  ⚠️ API 클라이언트 오류: {} - 페이지 {} 스킵", bankBrand, page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_SERVER_ERROR", errorMsg,
                            String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                            null, null, bankBrand
                        ));
                        log.warn("  ⚠️ API 서버 오류: {} - 페이지 {} 스킵", bankBrand, page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "NETWORK_ERROR", errorMsg,
                            String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                            null, null, bankBrand
                        ));
                        log.warn("  ⚠️ 네트워크 오류: {} - 페이지 {} 재시도", bankBrand, page);
                        
                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "API_UNKNOWN_ERROR", errorMsg,
                            String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                            null, null, bankBrand
                        ));
                        log.error("  🚨 예상치 못한 API 오류: {} - 페이지 {}", bankBrand, page, e);
                        hasError = true;
                        break;
                    }

                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                            "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                            String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                            null, null, bankBrand
                        ));
                        log.warn("  ⚠️ 빈 응답: {} - 페이지 {}", bankBrand, page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  📄 페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  📄 페이지 {} 응답: {} 개 결과", page, response.getDocuments().size());

                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    🔄 중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    🗺️ 서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            String district = extractDistrictFromAddress(doc.getAddressName());
                            BankStatistics bank = convertToEntity(doc, district);
                            bank.setBankBrand(bankBrand);
                            
                            try {
                                bankRepository.save(bank);
                                processedIds.add(doc.getId());
                                brandBankCount++;
                                progress.incrementSuccess();
                                
                                log.trace("    ✅ 저장완료: {} - {}", bank.getPlaceName(), bank.getAddressName());
                                
                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반: %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                    "DATA_INTEGRITY_ERROR", errorMsg,
                                    String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                    doc.getPlaceName(), doc.getId(), bankBrand
                                ));
                                log.debug("    ⚠️ 데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());
                                
                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                    "DATABASE_ERROR", errorMsg,
                                    String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                    doc.getPlaceName(), doc.getId(), bankBrand
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);
                                
                                log.warn("    🚨 DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            String errorMsg = String.format("데이터 처리 오류: %s", e.getMessage());
                            CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                "DATA_PROCESSING_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                doc.getPlaceName(), doc.getId(), bankBrand
                            );
                            error.setStackTrace(getStackTrace(e));
                            progress.addError(error);
                            
                            log.error("    🚨 데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    if (response.getMeta().isEnd()) {
                        log.debug("  📄 마지막 페이지 도달 - 검색 완료");
                        break;
                    }

                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                String errorMsg = String.format("브랜드 검색 전체 실패: %s", e.getMessage());
                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                    "BRAND_SEARCH_ERROR", errorMsg,
                    String.format("브랜드검색-%s-전체", bankBrand),
                    null, null, bankBrand
                );
                error.setStackTrace(getStackTrace(e));
                progress.addError(error);
                
                log.error("🚨 {} 브랜드 전체 검색 실패", bankBrand, e);
                hasError = true;
            }

            String statusIcon = hasError ? "⚠️" : "✅";
            log.info("  {} {} 완료: {} 개 은행, {} 페이지 검색", statusIcon, bankBrand, brandBankCount, pageCount);
        }

        log.info("✅ 브랜드별 검색 완료 | {}", progress.getProgressStatus());
    }

    /**
     * 최종 수집 결과 요약 출력
     */
    private void printFinalSummary() {
        Duration duration = Duration.between(progress.getStartTime(), progress.getEndTime());
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;

        log.info("🎯=================== 은행 데이터 수집 완료 ===================");
        log.info("📊 수집 통계:");
        log.info("   • 시작 시간: {}", progress.getStartTime());
        log.info("   • 종료 시간: {}", progress.getEndTime());
        log.info("   • 소요 시간: {}분 {}초", minutes, seconds);
        log.info("   • 총 처리: {} 개", progress.getTotalProcessed().get());
        log.info("   • 성공 저장: {} 개", progress.getSuccessCount().get());
        log.info("   • 중복 스킵: {} 개", progress.getSkipCount().get());
        log.info("   • 오류 발생: {} 개", progress.getErrorCount().get());
        
        if (progress.getErrorCount().get() > 0) {
            log.info("📋 오류 유형별 통계:");
            Map<String, Long> errorStats = progress.getErrors().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    CollectionProgress.ErrorDetail::getErrorType,
                    java.util.stream.Collectors.counting()
                ));
            
            errorStats.forEach((type, count) -> 
                log.info("   • {}: {} 건", type, count));
                
            log.info("⚠️ 상세 오류 내역:");
            progress.getErrors().stream()
                .limit(10) // 최대 10개만 출력
                .forEach(error -> log.warn("   [{}] {} - {} ({})", 
                    error.getErrorType(), 
                    error.getErrorMessage(), 
                    error.getPlaceName() != null ? error.getPlaceName() : "N/A",
                    error.getContext()));
                    
            if (progress.getErrors().size() > 10) {
                log.info("   ... 외 {} 건의 오류가 더 발생했습니다.", progress.getErrors().size() - 10);
            }
        }
        
        // DB 최종 확인
        try {
            long finalCount = bankRepository.count();
            log.info("💾 DB 저장 확인: {} 개 은행 데이터", finalCount);
            
            // 구별 분포 확인
            List<Object[]> districtStats = bankRepository.countBanksByDistrict();
            log.info("🗺️ 구별 분포 상위 5개:");
            districtStats.stream()
                .limit(5)
                .forEach(stat -> log.info("   • {}: {} 개", stat[0], stat[1]));
                
        } catch (Exception e) {
            log.error("DB 최종 확인 실패", e);
        }
        
        log.info("🎯==========================================================");
    }

    /**
     * 카카오 응답 데이터를 Entity로 변환
     */
    private BankStatistics convertToEntity(KakaoLocalApiResponse.Document doc, String district) {
        try {
            return BankStatistics.builder()
                    .kakaoPlaceId(doc.getId())
                    .placeName(doc.getPlaceName())
                    .categoryName(doc.getCategoryName())
                    .categoryGroupCode(doc.getCategoryGroupCode())
                    .phone(doc.getPhone())
                    .addressName(doc.getAddressName())
                    .roadAddressName(doc.getRoadAddressName())
                    .longitude(parseBigDecimal(doc.getLongitude()))
                    .latitude(parseBigDecimal(doc.getLatitude()))
                    .placeUrl(doc.getPlaceUrl())
                    .district(district)
                    .bankBrand(extractBankBrandFromName(doc.getPlaceName()))
                    .build();
        } catch (Exception e) {
            log.error("Entity 변환 실패: {}", doc.getPlaceName(), e);
            throw new RuntimeException("Entity 변환 실패: " + doc.getPlaceName(), e);
        }
    }

    /**
     * 서울시 주소인지 확인
     */
    private boolean isSeoulAddress(String address) {
        return address != null && address.startsWith("서울");
    }

    /**
     * 주소에서 구 정보 추출
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null) return null;
        
        for (SeoulDistrictCoords district : SeoulDistrictCoords.SEOUL_DISTRICTS) {
            if (address.contains(district.getName())) {
                return district.getName();
            }
        }
        return null;
    }

    /**
     * 상호명에서 은행 브랜드 추출
     */
    private String extractBankBrandFromName(String placeName) {
        if (placeName == null) return null;
        
        for (String brand : MAJOR_BANKS) {
            if (placeName.contains(brand.replace("은행", ""))) {
                return brand;
            }
        }
        return null;
    }

    /**
     * 문자열을 BigDecimal로 변환
     */
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 변환 실패: {}", value);
            return null;
        }
    }

    /**
     * API 호출 간 대기 (오버로드 버전)
     */
    private void sleepDelay(long delayMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("대기 중 인터럽트 발생");
        }
    }

    /**
     * API 호출 간 대기 (기본 버전)
     */
    private void sleepDelay() {
        sleepDelay(requestDelay);
    }

    /**
     * Exception Stack Trace를 문자열로 변환
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
```

## 실행 방법 및 설정

### 1. 카카오 개발자센터 설정
1. https://developers.kakao.com 접속
2. 앱 생성 후 [카카오맵] 사용 설정 ON
3. REST API 키 복사하여 `application.yml`의 `kakao.rest-api-key`에 설정

### 2. 데이터베이스 설정
1. Oracle DB에 DDL 스크립트 실행
2. `application.yml`의 DB 연결 정보 수정

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

## 예상 수집 결과

### 로그 출력 예시
```
🏦 서울시 은행 지점 수집을 시작합니다...
📋 수집 설정: 최대 45페이지, 100ms 대기, 25 구 대상

🔍 [1/25] 강남구 은행 검색 중...
  📄 페이지 1 요청 중... (좌표: 37.5173, 127.0473)
  📄 페이지 1 응답: 15 개 은행 발견
    ✅ 저장완료: KB국민은행 강남점 - 서울 강남구 테헤란로 123
  ✅ 강남구 완료: 12 개 은행, 3 페이지 검색 | [1/25 구 완료] 성공: 45, 오류: 2, 스킵: 8

🎯=================== 은행 데이터 수집 완료 ===================
📊 수집 통계:
   • 소요 시간: 25분 34초
   • 성공 저장: 823 개
   • 중복 스킵: 398 개
   • 오류 발생: 26 개
💾 DB 저장 확인: 823 개 은행 데이터
🎯==========================================================
```

파일 설정: 카카오 개발자센터에서 발급받은 REST API 키를 `application.yml`의 `kakao.rest-api-key`에 설정 후 애플리케이션 실행하면 자동으로 은행 데이터 수집 및 DB 저장 수행