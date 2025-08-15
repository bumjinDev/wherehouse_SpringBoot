# WhereHouse 프로젝트 개선 계획서 (2~3주)

## 📋 프로젝트 개요

### 현재 상황 분석
- **프로젝트명**: WhereHouse (주거지 추천 시스템)
- **현재 문제점**:
  - 단순한 CRUD 게시판 수준의 기능
  - 핵심 비즈니스 로직 부재 (RecommandCharterService, RecServiceMonthlyService의 허술한 로직)
  - 정적 데이터 사용으로 실용성 부족
  - 기술 선택의 명확한 근거 부족
  - 검색/필터 기능 전무
  - 데이터 분석 및 통계 기능 부재

### 개선 목표
기존의 단순 주거지 추천을 **"실거래가 데이터 기반의 지역별 시세 변동성 분석 및 추천 시스템"**으로 발전시켜:
1. 명확한 문제 해결 서사 구축
2. 데이터베이스 역량 어필
3. 최신 기술 트렌드 반영 (Elasticsearch)
4. 차별화된 포트폴리오 완성

---

## 🎯 핵심 기술 선택 근거

### Elasticsearch 도입 이유

| 기능 | 기존 RDBMS (MySQL/Oracle) | Elasticsearch | 근거 및 효과 |
|------|---------------------------|---------------|-------------|
| **검색/필터링** | LIKE 검색은 느리고, 복합 조건이 많아지면 쿼리가 복잡하고 성능 저하 | 검색에 특화된 엔진(Lucene 기반)으로 Faceted Search에 압도적으로 빠름 | "강남역 근처 30평대 아파트" 같은 복합 검색을 실시간 처리 |
| **통계/분석** | 복잡한 통계나 실시간 집계 시 DB에 큰 부하 | 집계(Aggregations) 기능 내장으로 대용량 데이터도 거의 실시간 분석 | "최근 6개월간 가장 가격이 안정적인 지역 TOP 5" 같은 의미있는 추천 로직 |
| **데이터 연동** | JSON 데이터를 정해진 테이블 스키마에 맞게 일일이 파싱 필요 | JSON 문서 기반으로 API 응답을 거의 그대로 저장 가능 | 부동산 API 연동을 통한 실시간 데이터 파이프라인 구축 용이 |

### Polyglot Persistence 아키텍처

| 저장소 | 담당 데이터 | 핵심 역할 및 이유 | 관련 파일 |
|--------|------------|-----------------|----------|
| **RDBMS** | • 회원 정보, 인증 정보<br>• 게시글, 댓글<br>• 사용자 북마크 (신규) | 트랜잭션과 데이터 정합성이 매우 중요한 데이터. ACID 보장 필수 | MemberEntity.java<br>BoardEntity.java<br>AuthenticationEntity.java |
| **Elasticsearch** | • 부동산 실거래가 데이터<br>• 매물 상세 설명 텍스트 | 빠른 검색과 복잡한 통계/분석이 중요한 대용량 데이터 | RealEstateTrade.java (신규) |

---

## 📝 구체적인 코드 구현 가이드

### 1단계 상세 구현 (1일차)

#### 국토교통부 API 연동 구체 코드
```java
// com/wherehouse/realestate/service/RealEstateDataService.java
@Service
@Slf4j
public class RealEstateDataService {

    private final RestTemplate restTemplate;
    private final RealEstateTradeRepository repository;
    
    // API 키는 환경변수나 설정파일에서 관리
    @Value("${molit.api.key}")
    private String apiKey;
    
    public RealEstateDataService(RealEstateTradeRepository repository) {
        this.restTemplate = new RestTemplate();
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 4 * * ?") // 매일 새벽 4시에 실행
    public void fetchAndIndexData() {
        try {
            // 1. 국토교통부 API 호출 로직
            String apiUrl = buildApiUrl("11110", getCurrentYearMonth()); // 서울 종로구 예시
            String response = restTemplate.getForObject(apiUrl, String.class);
            
            log.info("API 응답 수신 완료, 길이: {}", response != null ? response.length() : 0);

            // 2. XML 파싱 로직 (Jackson 등 사용)
            List<ApiDataDto> apiDataList = parseXmlResponse(response);
            log.info("파싱된 데이터 건수: {}", apiDataList.size());

            // 3. DTO -> Elasticsearch Document 객체로 변환
            List<RealEstateTrade> documents = apiDataList.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());

            // 4. Elasticsearch에 저장
            repository.saveAll(documents);
            log.info("실거래가 데이터 적재 완료: {} 건", documents.size());
            
        } catch (Exception e) {
            log.error("데이터 수집 중 오류 발생", e);
            // 필요시 알림 서비스 호출
        }
    }
    
    private String buildApiUrl(String sigunguCode, String dealYmd) {
        return String.format(
            "http://openapi.molit.go.kr/OpenAPI_ToolInstallPackage/service/rest/RTMSOBJSvc/getRTMSDataSvcAptTradeDev?LAWD_CD=%s&DEAL_YMD=%s&serviceKey=%s",
            sigunguCode, dealYmd, apiKey
        );
    }
    
    private String getCurrentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
    
    private List<ApiDataDto> parseXmlResponse(String xmlResponse) {
        // XML 파싱 로직 구현
        // 실제로는 JAXB나 Jackson XML 모듈 사용
        List<ApiDataDto> result = new ArrayList<>();
        
        try {
            // 파싱 로직 구현
            // 예시: DocumentBuilderFactory를 사용한 XML 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlResponse)));
            
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                ApiDataDto dto = parseXmlItem(item);
                if (dto != null) {
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("XML 파싱 중 오류 발생", e);
        }
        
        return result;
    }
    
    private ApiDataDto parseXmlItem(Node item) {
        // XML item 노드를 DTO로 변환
        // 실제 API 응답 구조에 맞게 구현
        return ApiDataDto.builder()
            .dealAmount(getTextContent(item, "거래금액"))
            .buildYear(getTextContent(item, "건축년도"))
            .dealYear(getTextContent(item, "년"))
            .dealMonth(getTextContent(item, "월"))
            .dealDay(getTextContent(item, "일"))
            .dong(getTextContent(item, "법정동"))
            .aptName(getTextContent(item, "아파트"))
            .exclusiveArea(getTextContent(item, "전용면적"))
            .floor(getTextContent(item, "층"))
            .regionCode(getTextContent(item, "지역코드"))
            .build();
    }
    
    private String getTextContent(Node parent, String tagName) {
        NodeList nodeList = ((Element) parent).getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return "";
    }
    
    private RealEstateTrade convertToDocument(ApiDataDto dto) {
        // DTO에서 Elasticsearch Document로 변환
        return RealEstateTrade.builder()
            .id(generateId(dto)) // 고유 ID 생성
            .tradeDate(parseTradeDate(dto))
            .regionName(extractRegionName(dto.getRegionCode()))
            .dong(dto.getDong())
            .aptName(dto.getAptName())
            .area(parseDouble(dto.getExclusiveArea()))
            .price(parseLong(dto.getDealAmount().replaceAll(",", "")))
            .buildYear(parseInt(dto.getBuildYear()))
            .floor(parseInt(dto.getFloor()))
            .build();
    }
    
    private String generateId(ApiDataDto dto) {
        // 중복 방지를 위한 고유 ID 생성
        return String.format("%s_%s_%s_%s_%s", 
            dto.getRegionCode(), dto.getDong(), dto.getAptName(), 
            dto.getDealYear() + dto.getDealMonth() + dto.getDealDay(),
            dto.getDealAmount().replaceAll(",", ""));
    }
}

// com/wherehouse/realestate/dto/ApiDataDto.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDataDto {
    private String dealAmount;      // 거래금액
    private String buildYear;       // 건축년도
    private String dealYear;        // 거래년
    private String dealMonth;       // 거래월
    private String dealDay;         // 거래일
    private String dong;            // 법정동
    private String aptName;         // 아파트명
    private String exclusiveArea;   // 전용면적
    private String floor;           // 층
    private String regionCode;      // 지역코드
}
```

### 2단계 구체 구현: 기존 코드 정리 및 마이그레이션

#### 기존 서비스 클래스 정리
```java
// 기존 RecommandCharterService.java에 추가
@Deprecated(since = "v2.0", forRemoval = true)
@Service
public class RecommandCharterService {
    
    // 기존 코드는 그대로 유지하되, 클래스 상단에 다음 주석 추가
    /**
     * @deprecated 이 서비스는 v2.0에서 AnalysisBasedRecommandService로 대체됩니다.
     * 정적인 점수 기반 추천에서 실거래가 데이터 기반 분석으로 개선되었습니다.
     * 
     * 마이그레이션 가이드:
     * - 기존 /api/recommandations -> /api/v2/recommendations/analyze
     * - 단순 점수 계산 -> 변동성 및 투자가치 기반 분석
     * 
     * @see AnalysisBasedRecommandService
     */
    public List<RecommandCharterDto> getRecommandations() {
        // 기존 로직 유지 (하위 호환성)
        log.warn("Deprecated API 사용됨: RecommandCharterService.getRecommandations()");
        // ... 기존 코드
    }
}

// 마찬가지로 RecServiceMonthlyService.java에도 동일하게 적용
@Deprecated(since = "v2.0", forRemoval = true) 
@Service
public class RecServiceMonthlyService {
    /**
     * @deprecated 월별 추천 서비스는 실시간 데이터 기반 분석으로 대체됩니다.
     * @see AnalysisBasedRecommandService#getRecommendations(RecommendationRequest)
     */
}
```

---

## 🚀 완성된 시스템의 최종 모습

### 전체 아키텍처 플로우
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  국토교통부     │────▶│   Spring Boot    │────▶│  Elasticsearch  │
│  실거래가 API   │    │   Scheduler      │    │   (실거래 데이터) │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                        │
                                                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   사용자 요청   │────▶│  Recommendation  │◀───│   Analysis      │
│ (예산, 지역 등) │    │   Controller     │    │   Service       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │     MySQL       │
                       │ (사용자, 북마크)  │
                       └─────────────────┘
```

### 완성된 API 목록
```
GET  /api/v2/recommendations/volatility     # 지역별 변동성 조회
POST /api/v2/recommendations/analyze        # 맞춤 추천 (핵심 API)
GET  /api/v2/recommendations/statistics     # 지역별 통계
GET  /api/v2/recommendations/trends         # 거래량 추이

POST /api/bookmarks                         # 북마크 추가
GET  /api/bookmarks                         # 내 북마크 목록
DELETE /api/bookmarks/{propertyId}          # 북마크 삭제

# 기존 API (하위 호환성 유지)
GET /api/recommandations                    # @Deprecated
GET /api/recommandations/monthly            # @Deprecated
```

### 데이터베이스 최종 구조
```sql
-- 기존 테이블들 (유지)
members, board, authentication_entity

-- 신규 추가 테이블들
bookmarks          # 사용자 북마크
properties         # 매물 기본 정보 (Elasticsearch 매핑용)

-- Elasticsearch 인덱스
real_estate_trades # 실거래가 데이터 (검색/분석용)
```

---

## 📊 성과 측정 지표

### 개발 완료 후 측정 가능한 지표들

#### 1. 기술적 성능 지표
- **검색 응답 시간**: 복합 조건 검색 시 200ms 이내 목표
- **데이터 처리량**: 일일 수천 건의 실거래 데이터 자동 처리
- **동시 사용자**: 100명 동시 접속 시에도 안정적 서비스

#### 2. 코드 품질 지표  
- **테스트 커버리지**: 핵심 비즈니스 로직 80% 이상
- **코드 중복도**: SonarQube 기준 5% 이하
- **기술 부채**: 0개 (Deprecated 코드 제외)

#### 3. 사용성 지표
- **API 응답 시간**: 평균 100ms 이내
- **에러율**: 1% 이하 
- **데이터 정확도**: 공공 API 대비 100% 일치

---

## 🎓 학습 및 성장 포인트

### 이 프로젝트를 통해 습득할 수 있는 핵심 역량

#### 1. **백엔드 개발 역량**
- RESTful API 설계 및 구현
- 데이터베이스 설계 및 최적화
- 배치 처리 시스템 구축
- 에러 핸들링 및 로깅

#### 2. **데이터 엔지니어링 역량**
- 외부 API 연동 및 데이터 수집
- 대용량 데이터 처리 및 저장
- 실시간 데이터 분석 시스템 구축
- 데이터 파이프라인 설계

#### 3. **검색 엔진 활용 역량**
- Elasticsearch 클러스터 운영
- 복잡한 검색 쿼리 작성
- 집계(Aggregation) 기반 통계 분석
- 성능 튜닝 및 최적화

#### 4. **시스템 설계 역량**
- Polyglot Persistence 아키텍처
- 마이크로서비스 설계 원칙
- 확장 가능한 시스템 구조
- 모니터링 및 운영

#### 5. **문제 해결 역량**
- 비즈니스 요구사항 분석
- 기술적 제약사항 해결
- 성능 병목 지점 파악 및 개선
- 데이터 품질 관리

---

## 🔍 트러블슈팅 가이드

### 예상되는 문제점과 해결 방안

#### 1. **Elasticsearch 연동 실패**
**문제**: Docker 컨테이너 실행 후 연결 불가
```bash
# 해결 방법
docker-compose down
docker system prune -f
docker-compose up -d

# 로그 확인
docker logs elasticsearch
docker logs kibana
```

#### 2. **API 호출 한도 초과**
**문제**: 국토교통부 API 일일 호출 제한
```java
// 해결 방법: 재시도 로직 구현
@Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
public String fetchDataWithRetry(String apiUrl) {
    // API 호출 로직
}
```

#### 3. **메모리 부족 에러**
**문제**: 대용량 데이터 처리 시 OutOfMemoryError
```java
// 해결 방법: 배치 처리로 분할
private void processBatchData(List<ApiDataDto> data) {
    int batchSize = 1000;
    for (int i = 0; i < data.size(); i += batchSize) {
        List<ApiDataDto> batch = data.subList(i, Math.min(i + batchSize, data.size()));
        processBatch(batch);
        System.gc(); // 명시적 가비지 컬렉션 (선택적)
    }
}
```

#### 4. **데이터 동기화 문제**
**문제**: MySQL과 Elasticsearch 간 데이터 불일치
```java
// 해결 방법: 데이터 검증 로직 추가
@EventListener
public void validateDataConsistency() {
    long mysqlCount = propertyRepository.count();
    long esCount = elasticsearchOperations.count(Query.findAll(), RealEstateTrade.class);
    
    if (Math.abs(mysqlCount - esCount) > 100) {
        log.warn("데이터 불일치 감지: MySQL={}, ES={}", mysqlCount, esCount);
        // 알림 또는 동기화 로직 실행
    }
}
```

---

## 📈 향후 확장 로드맵 (6개월 계획)

### Phase 1: 기본 시스템 구축 (완료, 2-3주)
- ✅ Elasticsearch 도입 및 데이터 파이프라인 구축
- ✅ 기본 추천 API 개발
- ✅ RDBMS 최적화

### Phase 2: 고도화 (4-6주 추가)
- 🔄 머신러닝 기반 가격 예측 모델
- 🔄 실시간 알림 시스템
- 🔄 관리자 대시보드 및 모니터링

### Phase 3: 스케일링 (2-3개월 추가)
- 📋 Redis 캐싱 레이어 추가
- 📋 Elasticsearch 클러스터링
- 📋 무중단 배포 파이프라인

### Phase 4: 비즈니스 확장 (3-6개월 추가)  
- 📋 다른 지역 (부산, 대구 등) 데이터 추가
- 📋 오피스텔, 빌라 등 다양한 부동산 유형 지원
- 📋 모바일 앱 API 제공

각 Phase별로 새로운 기술 스택을 도입하며 지속적으로 학습하고 성장할 수 있는 구조로 설계되어 있습니다.

이제 정말로 원본 문서의 모든 내용을 빠짐없이 포함한 완전한 계획서가 완성되었습니다! 🎉

## 📅 2주 개선 로드맵

#### 국토교통부 API 연동 구체 코드
```java
// com/wherehouse/realestate/service/RealEstateDataService.java
@Service
@Slf4j
public class RealEstateDataService {

    private final RestTemplate restTemplate;
    private final RealEstateTradeRepository repository;
    
    // API 키는 환경변수나 설정파일에서 관리
    @Value("${molit.api.key}")
    private String apiKey;
    
    public RealEstateDataService(RealEstateTradeRepository repository) {
        this.restTemplate = new RestTemplate();
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 4 * * ?") // 매일 새벽 4시에 실행
    public void fetchAndIndexData() {
        try {
            // 1. 국토교통부 API 호출 로직
            String apiUrl = buildApiUrl("11110", getCurrentYearMonth()); // 서울 종로구 예시
            String response = restTemplate.getForObject(apiUrl, String.class);
            
            log.info("API 응답 수신 완료, 길이: {}", response != null ? response.length() : 0);

            // 2. XML 파싱 로직 (Jackson 등 사용)
            List<ApiDataDto> apiDataList = parseXmlResponse(response);
            log.info("파싱된 데이터 건수: {}", apiDataList.size());

            // 3. DTO -> Elasticsearch Document 객체로 변환
            List<RealEstateTrade> documents = apiDataList.stream()
                .map(this::convertToDocument)
                .collect(Collectors.toList());

            // 4. Elasticsearch에 저장
            repository.saveAll(documents);
            log.info("실거래가 데이터 적재 완료: {} 건", documents.size());
            
        } catch (Exception e) {
            log.error("데이터 수집 중 오류 발생", e);
            // 필요시 알림 서비스 호출
        }
    }
    
    private String buildApiUrl(String sigunguCode, String dealYmd) {
        return String.format(
            "http://openapi.molit.go.kr/OpenAPI_ToolInstallPackage/service/rest/RTMSOBJSvc/getRTMSDataSvcAptTradeDev?LAWD_CD=%s&DEAL_YMD=%s&serviceKey=%s",
            sigunguCode, dealYmd, apiKey
        );
    }
    
    private String getCurrentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }
    
    private List<ApiDataDto> parseXmlResponse(String xmlResponse) {
        // XML 파싱 로직 구현
        // 실제로는 JAXB나 Jackson XML 모듈 사용
        List<ApiDataDto> result = new ArrayList<>();
        
        try {
            // 파싱 로직 구현
            // 예시: DocumentBuilderFactory를 사용한 XML 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmlResponse)));
            
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Node item = items.item(i);
                ApiDataDto dto = parseXmlItem(item);
                if (dto != null) {
                    result.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("XML 파싱 중 오류 발생", e);
        }
        
        return result;
    }
    
    private ApiDataDto parseXmlItem(Node item) {
        // XML item 노드를 DTO로 변환
        // 실제 API 응답 구조에 맞게 구현
        return ApiDataDto.builder()
            .dealAmount(getTextContent(item, "거래금액"))
            .buildYear(getTextContent(item, "건축년도"))
            .dealYear(getTextContent(item, "년"))
            .dealMonth(getTextContent(item, "월"))
            .dealDay(getTextContent(item, "일"))
            .dong(getTextContent(item, "법정동"))
            .aptName(getTextContent(item, "아파트"))
            .exclusiveArea(getTextContent(item, "전용면적"))
            .floor(getTextContent(item, "층"))
            .regionCode(getTextContent(item, "지역코드"))
            .build();
    }
    
    private String getTextContent(Node parent, String tagName) {
        NodeList nodeList = ((Element) parent).getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return "";
    }
    
    private RealEstateTrade convertToDocument(ApiDataDto dto) {
        // DTO에서 Elasticsearch Document로 변환
        return RealEstateTrade.builder()
            .id(generateId(dto)) // 고유 ID 생성
            .tradeDate(parseTradeDate(dto))
            .regionName(extractRegionName(dto.getRegionCode()))
            .dong(dto.getDong())
            .aptName(dto.getAptName())
            .area(parseDouble(dto.getExclusiveArea()))
            .price(parseLong(dto.getDealAmount().replaceAll(",", "")))
            .buildYear(parseInt(dto.getBuildYear()))
            .floor(parseInt(dto.getFloor()))
            .build();
    }
    
    private String generateId(ApiDataDto dto) {
        // 중복 방지를 위한 고유 ID 생성
        return String.format("%s_%s_%s_%s_%s", 
            dto.getRegionCode(), dto.getDong(), dto.getAptName(), 
            dto.getDealYear() + dto.getDealMonth() + dto.getDealDay(),
            dto.getDealAmount().replaceAll(",", ""));
    }
}

// com/wherehouse/realestate/dto/ApiDataDto.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDataDto {
    private String dealAmount;      // 거래금액
    private String buildYear;       // 건축년도
    private String dealYear;        // 거래년
    private String dealMonth;       // 거래월
    private String dealDay;         // 거래일
    private String dong;            // 법정동
    private String aptName;         // 아파트명
    private String exclusiveArea;   // 전용면적
    private String floor;           // 층
    private String regionCode;      // 지역코드
}
```

### 1주차: 데이터 파이프라인 및 기반 구축

#### 1단계: 공공 데이터 API 연동 (1일차)
**목표**: 국토교통부 실거래가 정보 API 활용해 최근 1년 서울시 아파트 실거래 데이터 수집 기능 구현

**작업 내용**:
- API 키 발급 및 테스트
- 특정 지역과 기간을 파라미터로 받는 Java 모듈 구현
- JSON/XML 형태의 실거래 데이터 파싱 로직 구현
- 에러 핸들링 및 로깅 시스템 구축

**결과물**: 
```java
// com/wherehouse/realestate/service/RealEstateDataService.java
public class RealEstateDataService {
    public List<ApiDataDto> fetchRealEstateData(String region, String period);
}
```

#### 2단계: Elasticsearch 도입 및 데이터 모델링 (2-3일차)
**목표**: Docker로 Elasticsearch 환경 구축 및 데이터 저장 구조 설계

**작업 내용**:
1. **Docker 환경 구축**
   ```yaml
   # docker-compose.yml 생성
   version: '3.8'
   services:
     elasticsearch:
       image: elasticsearch:8.14.0
       container_name: elasticsearch
       ports:
         - "9200:9200"
       environment:
         - "discovery.type=single-node"
         - "xpack.security.enabled=false"
     
     kibana:
       image: kibana:8.14.0
       container_name: kibana
       ports:
         - "5601:5601"
       depends_on:
         - elasticsearch
       environment:
         - "ELASTICSEARCH_HOSTS=http://elasticsearch:9200"
   ```

2. **Spring Boot 연동 설정**
   ```gradle
   // build.gradle에 추가
   implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
   ```
   
   ```properties
   # application.properties에 추가
   spring.elasticsearch.uris=http://localhost:9200
   ```

3. **데이터 모델 설계**
   ```java
   // com/wherehouse/realestate/model/RealEstateTrade.java
   @Document(indexName = "real_estate_trades")
   public class RealEstateTrade {
       @Id
       private String id;
       
       @Field(type = FieldType.Date, format = DateFormat.year_month_day)
       private LocalDate tradeDate;
       
       @Field(type = FieldType.Keyword)
       private String regionName;
       
       @Field(type = FieldType.Keyword)
       private String dong;
       
       @Field(type = FieldType.Double)
       private double area;
       
       @Field(type = FieldType.Long)
       private long price;
       
       // Builder, Getter, Setter...
   }
   ```

**결과물**: 
- docker-compose.yml 파일
- Elasticsearch 인덱스 매핑 정의
- Document 클래스 완성

#### 3단계: 데이터 적재(Indexing) 파이프라인 구축 (4-7일차)
**목표**: 자동화된 데이터 수집 및 저장 시스템 구축

**작업 내용**:
1. **Repository 인터페이스 생성**
   ```java
   // com/wherehouse/realestate/dao/RealEstateTradeRepository.java
   public interface RealEstateTradeRepository extends ElasticsearchRepository<RealEstateTrade, String> {
       // 기본 CRUD 및 간단한 쿼리 메소드
   }
   ```

2. **스케줄러 기반 배치 시스템**
   ```java
   // com/wherehouse/realestate/service/RealEstateDataService.java
   @Service
   public class RealEstateDataService {
       
       private final RestTemplate restTemplate;
       private final RealEstateTradeRepository repository;
       
       @Scheduled(cron = "0 0 4 * * ?") // 매일 새벽 4시 실행
       public void fetchAndIndexData() {
           // 1. 국토교통부 API 호출
           String apiUrl = "http://openapi.molit.go.kr/OpenAPI_ToolInstallPackage/service/...";
           String response = restTemplate.getForObject(apiUrl, String.class);
           
           // 2. XML/JSON 파싱
           List<ApiDataDto> apiDataList = parseResponse(response);
           
           // 3. DTO -> Document 변환
           List<RealEstateTrade> documents = apiDataList.stream()
               .map(this::convertToDocument)
               .collect(Collectors.toList());
           
           // 4. Elasticsearch에 저장
           repository.saveAll(documents);
           
           log.info("실거래가 데이터 적재 완료: {} 건", documents.size());
       }
       
       private RealEstateTrade convertToDocument(ApiDataDto dto) {
           return RealEstateTrade.builder()
               .id(dto.getId())
               .tradeDate(LocalDate.parse(dto.getTradeDate()))
               .regionName(dto.getRegionName())
               .dong(dto.getDong())
               .area(Double.parseDouble(dto.getArea()))
               .price(Long.parseLong(dto.getPrice()))
               .build();
       }
   }
   ```

3. **에러 핸들링 및 모니터링**
   - API 호출 실패 시 재시도 로직
   - 데이터 품질 검증
   - 로그 시스템 구축

**결과물**:
- 스케줄러가 포함된 서비스 코드
- Elasticsearch에 실거래가 데이터 자동 적재 확인
- 에러 핸들링 시스템

### 2주차: 핵심 로직 구현 및 API 개발

### 4단계: 분석/통계 로직 개발 (8-10일차)
**목표**: Elasticsearch Aggregation API를 활용한 핵심 비즈니스 로직 구현

**작업 내용**:

#### 1. 지역별 평균/최고/최저가 통계
#### 2. 기간별 거래량 추이 통계  
#### 3. (핵심) 지역별 가격 변동성(표준편차) 계산 로직

**구체적 구현**:
1. **분석 서비스 구현**
   ```java
   // com/wherehouse/recommand/service/AnalysisBasedRecommandService.java
   @Service
   public class AnalysisBasedRecommandService {
       
       private final ElasticsearchOperations operations;
       
       // 1. 지역별 평균/최고/최저가 통계
       public Map<String, PriceStatistics> getRegionPriceStatistics() {
           NativeQuery query = new NativeQueryBuilder()
               .withAggregation("group_by_region", AggregationBuilders.terms("region_agg")
                   .field("regionName.keyword")
                   .subAggregation(AggregationBuilders.extendedStats("price_stats")
                       .field("price")))
               .withMaxResults(0)
               .build();
               
           SearchHits<RealEstateTrade> searchHits = operations.search(query, RealEstateTrade.class);
           
           // 결과 파싱 및 반환
           return parseStatisticsResult(searchHits);
       }
       
       // 2. 기간별 거래량 추이 통계
       public Map<String, List<TradeTrend>> getTradeTrendByPeriod() {
           NativeQuery query = new NativeQueryBuilder()
               .withAggregation("trade_trend", AggregationBuilders.dateHistogram("date_histogram")
                   .field("tradeDate")
                   .calendarInterval(DateHistogramInterval.MONTH)
                   .subAggregation(AggregationBuilders.terms("region_trades")
                       .field("regionName.keyword")
                       .subAggregation(AggregationBuilders.count("trade_count"))))
               .withMaxResults(0)
               .build();
               
           // 쿼리 실행 및 결과 파싱
           return parseTrendResult(operations.search(query, RealEstateTrade.class));
       }
       
       // 3. 핵심: 지역별 가격 변동성(표준편차) 계산
       public Map<String, Double> getRegionVolatility() {
           NativeQuery query = new NativeQueryBuilder()
               .withAggregation("group_by_region", AggregationBuilders.terms("region_agg")
                   .field("regionName.keyword")
                   .subAggregation(AggregationBuilders.extendedStats("price_stats")
                       .field("price")))
               .withMaxResults(0)
               .build();
               
           SearchHits<RealEstateTrade> searchHits = operations.search(query, RealEstateTrade.class);
           
           Map<String, Double> volatilityMap = new HashMap<>();
           Terms regionAgg = searchHits.getAggregations().get("group_by_region");
           
           for (Terms.Bucket bucket : regionAgg.getBuckets()) {
               String regionName = bucket.getKeyAsString();
               ExtendedStats priceStats = bucket.getAggregations().get("price_stats");
               
               // 변동성 = 표준편차
               double volatility = priceStats.getStdDeviation();
               volatilityMap.put(regionName, volatility);
           }
           
           return volatilityMap;
       }
   }
   ```

2. **DTO 클래스 생성**
   ```java
   // com/wherehouse/recommand/dto/PriceStatistics.java
   public class PriceStatistics {
       private String regionName;
       private double averagePrice;
       private double maxPrice;
       private double minPrice;
       private double standardDeviation;
       private long tradeCount;
       // Getters, Setters, Builder...
   }
   
   // com/wherehouse/recommand/dto/TradeTrend.java
   public class TradeTrend {
       private String period;
       private long tradeCount;
       private double averagePrice;
       // Getters, Setters, Builder...
   }
   ```

**결과물**:
- 지역별 평균/최고/최저가 통계 서비스
- 기간별 거래량 추이 분석 서비스
- **핵심**: 지역별 가격 변동성 계산 로직

### 5단계: 신규 추천 API 개발 (11-13일차)
**목표**: 기존 추천 API를 대체할 데이터 기반 추천 시스템 구축

**핵심 로직**:
- **Request**: 예산 범위, 선호 지역, 중요 가치 (안정성 vs 투자 가치)
- **Response**: 조건에 맞는 지역 리스트를 변동성 점수가 낮은 순(안정적) 또는 **상승률이 높은 순(투자 가치)**으로 정렬하여 반환

**결과물**: 새로운 추천 로직이 반영된 REST API 엔드포인트 (/api/v2/recommendations/analyze)

**작업 내용**:
1. **추천 요청/응답 DTO 정의**
   ```java
   // com/wherehouse/recommand/dto/RecommendationRequest.java
   public class RecommendationRequest {
       private long minPrice;           // 최소 예산
       private long maxPrice;           // 최대 예산
       private List<String> preferredRegions; // 선호 지역
       private String priorityType;     // "STABILITY" 또는 "INVESTMENT"
       private double minArea;          // 최소 평수
       private double maxArea;          // 최대 평수
       // Getters, Setters...
   }
   
   // com/wherehouse/recommand/dto/RecommendationResponse.java
   public class RecommendationResponse {
       private String regionName;
       private double stabilityScore;    // 안정성 점수 (낮은 변동성)
       private double investmentScore;   // 투자 가치 점수 (높은 상승률)
       private PriceStatistics priceInfo;
       private String recommendation;    // 추천 이유
       // Getters, Setters...
   }
   ```

2. **고도화된 추천 로직 구현**
   ```java
   // AnalysisBasedRecommandService.java에 추가
   public List<RecommendationResponse> getRecommendations(RecommendationRequest request) {
       // 1. 기본 필터링 (가격, 지역, 평수)
       BoolQuery.Builder boolQuery = new BoolQuery.Builder();
       
       if (request.getMinPrice() > 0) {
           boolQuery.must(RangeQuery.of(r -> r.field("price").gte(JsonData.of(request.getMinPrice()))));
       }
       if (request.getMaxPrice() > 0) {
           boolQuery.must(RangeQuery.of(r -> r.field("price").lte(JsonData.of(request.getMaxPrice()))));
       }
       if (!request.getPreferredRegions().isEmpty()) {
           boolQuery.must(TermsQuery.of(t -> t.field("regionName.keyword")
               .terms(TermsQueryField.of(tf -> tf.value(request.getPreferredRegions().stream()
                   .map(FieldValue::of).collect(Collectors.toList()))))));
       }
       
       // 2. 집계 쿼리로 지역별 통계 계산
       NativeQuery query = new NativeQueryBuilder()
           .withQuery(boolQuery.build()._toQuery())
           .withAggregation("region_analysis", AggregationBuilders.terms("region_agg")
               .field("regionName.keyword")
               .subAggregation(AggregationBuilders.extendedStats("price_stats").field("price"))
               .subAggregation(AggregationBuilders.dateHistogram("price_trend")
                   .field("tradeDate")
                   .calendarInterval(DateHistogramInterval.MONTH)
                   .subAggregation(AggregationBuilders.avg("monthly_avg").field("price"))))
           .withMaxResults(0)
           .build();
           
       SearchHits<RealEstateTrade> searchHits = operations.search(query, RealEstateTrade.class);
       
       // 3. 결과를 추천 로직에 따라 정렬
       List<RecommendationResponse> recommendations = parseAndScore(searchHits, request.getPriorityType());
       
       // 4. 상위 10개 지역만 반환
       return recommendations.stream()
           .limit(10)
           .collect(Collectors.toList());
   }
   
   private List<RecommendationResponse> parseAndScore(SearchHits<RealEstateTrade> searchHits, String priorityType) {
       List<RecommendationResponse> responses = new ArrayList<>();
       Terms regionAgg = searchHits.getAggregations().get("region_analysis");
       
       for (Terms.Bucket bucket : regionAgg.getBuckets()) {
           String regionName = bucket.getKeyAsString();
           ExtendedStats priceStats = bucket.getAggregations().get("price_stats");
           
           // 안정성 점수: 변동성이 낮을수록 높은 점수
           double stabilityScore = calculateStabilityScore(priceStats.getStdDeviation());
           
           // 투자 점수: 상승률이 높을수록 높은 점수
           double investmentScore = calculateInvestmentScore(bucket);
           
           RecommendationResponse response = RecommendationResponse.builder()
               .regionName(regionName)
               .stabilityScore(stabilityScore)
               .investmentScore(investmentScore)
               .priceInfo(PriceStatistics.from(priceStats))
               .recommendation(generateRecommendationText(regionName, stabilityScore, investmentScore))
               .build();
               
           responses.add(response);
       }
       
       // 우선순위에 따라 정렬
       if ("STABILITY".equals(priorityType)) {
           responses.sort((a, b) -> Double.compare(b.getStabilityScore(), a.getStabilityScore()));
       } else {
           responses.sort((a, b) -> Double.compare(b.getInvestmentScore(), a.getInvestmentScore()));
       }
       
       return responses;
   }
   ```

3. **새로운 컨트롤러 구현**
   ```java
   // com/wherehouse/recommand/controller/RecommendationControllerV2.java
   @RestController
   @RequestMapping("/api/v2/recommendations")
   @Validated
   public class RecommendationControllerV2 {
       
       private final AnalysisBasedRecommandService recommendationService;
       
       @PostMapping("/analyze")
       public ResponseEntity<List<RecommendationResponse>> getRecommendations(
           @Valid @RequestBody RecommendationRequest request) {
           
           List<RecommendationResponse> recommendations = 
               recommendationService.getRecommendations(request);
               
           return ResponseEntity.ok(recommendations);
       }
       
       @GetMapping("/volatility")
       public ResponseEntity<Map<String, Double>> getRegionVolatility() {
           Map<String, Double> volatility = recommendationService.getRegionVolatility();
           return ResponseEntity.ok(volatility);
       }
       
       @GetMapping("/statistics")
       public ResponseEntity<Map<String, PriceStatistics>> getRegionStatistics() {
           Map<String, PriceStatistics> statistics = 
               recommendationService.getRegionPriceStatistics();
           return ResponseEntity.ok(statistics);
       }
   }
   ```

**결과물**:
- 새로운 추천 로직이 반영된 REST API 엔드포인트
- 조건에 맞는 지역을 변동성/투자가치 기준으로 정렬하여 반환하는 시스템

### 6단계: 문서화 및 회고 (14일차)
**목표**: README 파일을 업데이트하여 **'왜 Elasticsearch를 사용했는가'**를 명확히 기술합니다.

**핵심 문서화 내용**:
- **문제 정의**: 기존 RDBMS로는 비정형 데이터 검색과 복잡한 통계 분석에 성능 한계가 예상됨
- **해결**: 검색과 집계(Aggregation)에 특화된 Elasticsearch를 도입하여 해결  
- **결과**: 아키텍처 다이어그램(API -> Batch -> Elasticsearch -> Recommendation API)을 그리고, API 명세를 정리

**결과물**:
- 완전히 새로워진 README.md
- 상세한 API 명세서  
- 아키텍처 다이어그램
- 기술 선택의 명확한 근거 문서

**작업 내용**:
1. **README.md 대폭 업데이트**
   ```markdown
   # WhereHouse - 실거래가 데이터 기반 주거지 추천 시스템
   
   ## 🏠 프로젝트 개요
   기존의 정적인 주거지 정보 제공을 넘어, **국토교통부 실거래가 데이터를 실시간으로 수집·분석**하여 
   사용자에게 **데이터 기반의 과학적인 주거지 추천**을 제공하는 시스템입니다.
   
   ## 🚀 핵심 기술 및 선택 근거
   
   ### 왜 Elasticsearch를 도입했는가?
   
   **문제 정의**: 
   - 기존 RDBMS로는 비정형 데이터 검색과 복잡한 통계 분석에 성능 한계 예상
   - 대용량의 실거래가 데이터에서 실시간 검색 및 집계 처리 필요
   
   **해결책**: 
   - 검색과 집계(Aggregation)에 특화된 Elasticsearch 도입
   - 복잡한 조건의 부동산 검색을 실시간으로 처리
   - 지역별 가격 변동성, 거래량 추이 등 통계 분석을 빠르게 수행
   
   ## 🏗️ 아키텍처
   ```
   [국토교통부 API] → [Spring Scheduler] → [Elasticsearch] → [분석 API] → [추천 결과]
                                      ↓
                            [MySQL (사용자/게시글 데이터)]
   ```
   
   ## 📊 주요 기능
   
   ### 1. 실시간 데이터 수집
   - 국토교통부 실거래가 API 연동
   - 매일 자동으로 최신 거래 데이터 수집 및 적재
   
   ### 2. 고도화된 분석 기능
   - **지역별 가격 변동성 분석**: 표준편차를 통한 시세 안정성 측정
   - **거래량 추이 분석**: 월별 거래량 변화를 통한 시장 활성도 측정
   - **투자 가치 분석**: 최근 상승률을 통한 투자 잠재력 평가
   
   ### 3. 맞춤형 추천 시스템
   - 사용자의 예산, 선호 지역, 우선순위(안정성 vs 투자가치)를 종합 고려
   - 데이터 기반의 객관적인 추천 근거 제시
   ```

2. **API 명세서 작성**
   ```markdown
   ## 📡 API 명세
   
   ### POST /api/v2/recommendations/analyze
   **설명**: 사용자 조건에 맞는 지역 추천
   
   **Request Body**:
   ```json
   {
     "minPrice": 50000,
     "maxPrice": 100000,
     "preferredRegions": ["강남구", "서초구"],
     "priorityType": "STABILITY",
     "minArea": 20.0,
     "maxArea": 40.0
   }
   ```
   
   **Response**:
   ```json
   [
     {
       "regionName": "강남구",
       "stabilityScore": 85.2,
       "investmentScore": 72.1,
       "priceInfo": {
         "averagePrice": 75000,
         "maxPrice": 120000,
         "minPrice": 45000,
         "standardDeviation": 12500.5,
         "tradeCount": 150
       },
       "recommendation": "최근 6개월간 가격 변동성이 낮아 안정적인 투자처입니다."
     }
   ]
   ```
   ```

3. **기술 회고 문서 작성**
   ```markdown
   ## 🔍 기술적 고민과 해결 과정
   
   ### 1. 데이터 저장소 선택
   **고민**: 부동산 실거래가 데이터를 어떤 저장소에 저장할 것인가?
   
   **고려사항**:
   - 대용량 데이터 (월 수만 건의 거래 데이터)
   - 복잡한 검색 조건 (지역, 가격, 평수, 날짜 등의 다중 필터)
   - 실시간 통계 분석 필요
   
   **결정**: Polyglot Persistence 패턴 채택
   - RDBMS: 사용자, 게시글 등 트랜잭션이 중요한 데이터
   - Elasticsearch: 부동산 데이터의 검색과 분석
   
   ### 2. 배치 처리 시스템 설계
   **고민**: 대용량 데이터를 어떻게 안정적으로 수집하고 처리할 것인가?
   
   **해결책**:
   - Spring의 @Scheduled를 활용한 배치 시스템
   - 점진적 데이터 로딩 (일별 증분 수집)
   - 실패 시 재시도 로직 및 모니터링
   ```

**결과물**:
- 완전히 새로워진 README.md
- 상세한 API 명세서
- 아키텍처 다이어그램
- 기술 선택의 명확한 근거 문서

## 🎯 기대 효과 및 면접 어필 포인트

### 1. 기술 선택의 타당성 
**오버엔지니어링 문제 극복**: "왜 Redis를 썼나요?"라는 질문에 JWT 저장용이라고 답하는 오버엔지니어링 문제를 극복하고, **"대용량 데이터 검색 및 실시간 분석이라는 명확한 문제를 해결하기 위해 RDBMS가 아닌 Elasticsearch를 선택했습니다"** 라고 자신 있게 답할 수 있습니다.

### 2. 데이터 엔지니어링 역량 
단순히 DB를 사용하는 것을 넘어, **데이터를 수집-가공-적재-분석하는 파이프라인을 구축한 경험**을 어필할 수 있습니다.

### 3. 문제 해결 능력
프로젝트의 명백한 약점(비즈니스 로직 부재)을 스스로 파악하고, **구체적인 데이터 기반의 해결책을 적용했다는 점**을 보여주어 주도적인 신입의 인상을 줄 수 있습니다.

### 4. 현재 취업 시장에서의 유용성
**매우 유용합니다.** 이 기술 스택을 적용하면 **"게시판 만들 줄 아는 신입"에서 "데이터를 다룰 줄 아는 신입"**으로 포지셔닝이 완전히 달라집니다.

#### '차별화'된 포트폴리오
대부분의 신입 지원자들은 Spring Boot + JPA + RDBMS로 구성된 CRUD 게시판 프로젝트를 포트폴리오로 제출합니다. 여기에 **Elasticsearch를 활용한 데이터 수집, 분석, 검색 프로젝트는 단연 눈에 띨 수밖에 없습니다.**

#### 수요가 높은 기술 스택
- **이커머스**: 쿠팡, 무신사 등의 상품 검색, 추천 시스템
- **배달 플랫폼**: 배달의민족, 요기요 등의 가게 및 메뉴 검색  
- **콘텐츠/채용 플랫폼**: 넷플릭스, 원티드 등 콘텐츠 및 채용 공고 검색
- **로그 분석**: 모든 IT 기업의 서비스 운영에 필수적인 로그 데이터를 수집하고 분석하는 시스템 (ELK Stack)

위와 같이 검색, 추천, 데이터 분석이 필요한 거의 모든 현대적인 서비스 기업에서 Elasticsearch는 핵심 기술로 사용됩니다.

---

## 🔧 RDBMS 역량 강화 방안

### 왜 RDBMS 역량이 여전히 중요한가?

이 프로젝트는 Elasticsearch를 도입한다고 해서 DBMS의 역할이 줄어드는 것이 아니라, 오히려 **각 데이터의 성격에 맞게 최적의 도구를 선택하고 함께 사용하는 역량**을 보여줄 수 있어 DBMS를 더 깊이 있게 다룰 기회가 생깁니다.

결론적으로, 직접 DBMS를 다룰 일이 분명히 있으며, 그 중요성은 더 커집니다.

### 1. 데이터 모델링 심화

**문제 상황**: 개선 방향성 문서에서 "방별 평수 등 핵심적인 데이터가 누락" 되어 있고, "상세 데이터를 저장하고 관리할 수 있는 체계(데이터베이스 등)가 완성되어야 한다" 고 지적했습니다. 이를 직접 해결하며 DBMS 역량을 보여줄 수 있습니다.

#### 신규 테이블 설계
**목표**: Elasticsearch로 추천받은 특정 매물을 사용자가 '찜'하는 기능을 추가한다고 가정하여 새로운 데이터 모델을 설계합니다.

```sql
-- 사용자 북마크 테이블
CREATE TABLE bookmarks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    property_id VARCHAR(255) NOT NULL, -- Elasticsearch document ID
    region_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES members(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_property (user_id, property_id)
);

-- 속성 상세 정보 테이블 (Elasticsearch와 매핑)
CREATE TABLE properties (
    id VARCHAR(255) PRIMARY KEY, -- Elasticsearch document ID와 동일
    region_name VARCHAR(100) NOT NULL,
    dong VARCHAR(100) NOT NULL,
    building_name VARCHAR(200),
    build_year INT,
    total_floors INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_region_dong (region_name, dong),
    INDEX idx_build_year (build_year)
);
```

**핵심 포인트**: 이렇게 되면 User와 Bookmark, Property 간의 **1:N, N:M 관계를 직접 설계하고 JPA Entity로 구현하는 경험**을 할 수 있습니다.
```sql
-- 사용자 북마크 테이블
CREATE TABLE bookmarks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    property_id VARCHAR(255) NOT NULL, -- Elasticsearch document ID
    region_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES members(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_property (user_id, property_id)
);

-- 속성 상세 정보 테이블 (Elasticsearch와 매핑)
CREATE TABLE properties (
    id VARCHAR(255) PRIMARY KEY, -- Elasticsearch document ID와 동일
    region_name VARCHAR(100) NOT NULL,
    dong VARCHAR(100) NOT NULL,
    building_name VARCHAR(200),
    build_year INT,
    total_floors INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_region_dong (region_name, dong),
    INDEX idx_build_year (build_year)
);
```

#### JPA Entity 구현
```java
// com/wherehouse/bookmark/entity/BookmarkEntity.java
@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private MemberEntity member;
    
    @Column(name = "property_id", nullable = false)
    private String propertyId; // Elasticsearch document ID
    
    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Getters, Setters, Builder...
}

// com/wherehouse/property/entity/PropertyEntity.java
@Entity
@Table(name = "properties", indexes = {
    @Index(name = "idx_region_dong", columnList = "regionName, dong"),
    @Index(name = "idx_build_year", columnList = "buildYear")
})
public class PropertyEntity {
    @Id
    private String id; // Elasticsearch document ID와 동일
    
    @Column(name = "region_name", nullable = false, length = 100)
    private String regionName;
    
    @Column(name = "dong", nullable = false, length = 100)
    private String dong;
    
    @Column(name = "building_name", length = 200)
    private String buildingName;
    
    @Column(name = "build_year")
    private Integer buildYear;
    
    @Column(name = "total_floors")
    private Integer totalFloors;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 북마크와의 관계 (양방향 매핑)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookmarkEntity> bookmarks = new ArrayList<>();
    
    // Getters, Setters, Builder...
}
```

### 2. JPA 심화 활용

**현재 상황**: 현재 프로젝트는 기본적인 JpaRepository 사용에 그치고 있습니다. 여기서 더 나아가 다음과 같은 고급 JPA 기법을 활용할 수 있습니다.

#### @Query를 사용한 직접 쿼리 작성
**목표**: 단순한 findById, findAll을 넘어, 복잡한 조건의 조인(Join)이 필요한 경우 JPQL이나 Native Query를 직접 작성하여 성능을 최적화하는 모습을 보여줍니다.

#### 엔티티 관계 매핑 최적화
**목표**: @OneToMany, @ManyToOne 관계를 설정할 때 발생하는 **N+1 문제** 등을 인지하고, Fetch Join 등을 사용해 해결하는 과정을 코드로 보여주고 설명할 수 있습니다.

#### 트랜잭션 관리
**목표**: MemberService에서 MembersEntity와 AuthenticationEntity 두 테이블에 동시에 데이터를 저장하는데, 이때 **@Transactional의 역할과 데이터 정합성을 어떻게 보장했는지** 깊이 있게 설명할 수 있습니다.

#### 복잡한 쿼리 작성
```java
// com/wherehouse/bookmark/repository/BookmarkRepository.java
public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {
    
    // N+1 문제 해결을 위한 Fetch Join 사용
    @Query("SELECT b FROM BookmarkEntity b " +
           "JOIN FETCH b.member m " +
           "JOIN FETCH b.property p " +
           "WHERE m.id = :userId " +
           "ORDER BY b.createdAt DESC")
    List<BookmarkEntity> findByUserIdWithMemberAndProperty(@Param("userId") Long userId);
    
    // 특정 지역의 인기 매물 조회 (북마크 수 기준)
    @Query("SELECT p.regionName, p.dong, COUNT(b.id) as bookmarkCount " +
           "FROM BookmarkEntity b " +
           "JOIN b.property p " +
           "WHERE p.regionName = :regionName " +
           "GROUP BY p.regionName, p.dong " +
           "HAVING COUNT(b.id) >= :minBookmarks " +
           "ORDER BY bookmarkCount DESC")
    List<Object[]> findPopularPropertiesByRegion(
        @Param("regionName") String regionName, 
        @Param("minBookmarks") Long minBookmarks);
    
    // 네이티브 쿼리를 활용한 복잡한 통계 조회
    @Query(value = """
        SELECT 
            p.region_name,
            COUNT(DISTINCT b.user_id) as unique_users,
            COUNT(b.id) as total_bookmarks,
            AVG(TIMESTAMPDIFF(DAY, b.created_at, NOW())) as avg_days_since_bookmark
        FROM bookmarks b
        JOIN properties p ON b.property_id = p.id
        WHERE b.created_at >= DATE_SUB(NOW(), INTERVAL :days DAY)
        GROUP BY p.region_name
        ORDER BY total_bookmarks DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> getBookmarkStatisticsByRegion(
        @Param("days") int days, 
        @Param("limit") int limit);
}
```

#### 트랜잭션 관리 심화
```java
// com/wherehouse/bookmark/service/BookmarkService.java
@Service
@Transactional(readOnly = true)
public class BookmarkService {
    
    private final BookmarkRepository bookmarkRepository;
    private final PropertyRepository propertyRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    
    @Transactional // 쓰기 작업에 대해서만 트랜잭션 활성화
    public BookmarkEntity addBookmark(Long userId, String propertyId) {
        // 1. Elasticsearch에서 매물 정보 조회
        RealEstateTrade tradeInfo = findTradeInfoFromElasticsearch(propertyId);
        if (tradeInfo == null) {
            throw new PropertyNotFoundException("매물을 찾을 수 없습니다: " + propertyId);
        }
        
        // 2. RDBMS에 Property 정보가 없으면 생성 (Upsert 패턴)
        PropertyEntity property = propertyRepository.findById(propertyId)
            .orElseGet(() -> createPropertyFromTradeInfo(tradeInfo));
        
        // 3. 중복 북마크 검사
        if (bookmarkRepository.existsByMemberIdAndPropertyId(userId, propertyId)) {
            throw new DuplicateBookmarkException("이미 북마크한 매물입니다.");
        }
        
        // 4. 북마크 생성 및 저장
        BookmarkEntity bookmark = BookmarkEntity.builder()
            .member(memberRepository.getReferenceById(userId)) // Lazy 로딩 활용
            .propertyId(propertyId)
            .regionName(property.getRegionName())
            .build();
            
        return bookmarkRepository.save(bookmark);
    }
    
    @Transactional
    public void removeBookmark(Long userId, String propertyId) {
        BookmarkEntity bookmark = bookmarkRepository
            .findByMemberIdAndPropertyId(userId, propertyId)
            .orElseThrow(() -> new BookmarkNotFoundException("북마크를 찾을 수 없습니다."));
            
        bookmarkRepository.delete(bookmark);
        
        // 해당 매물에 북마크가 더 이상 없으면 Property 정보도 삭제 (선택적)
        if (!bookmarkRepository.existsByPropertyId(propertyId)) {
            propertyRepository.deleteById(propertyId);
        }
    }
    
    private PropertyEntity createPropertyFromTradeInfo(RealEstateTrade tradeInfo) {
        return PropertyEntity.builder()
            .id(tradeInfo.getId())
            .regionName(tradeInfo.getRegionName())
            .dong(tradeInfo.getDong())
            .buildingName(extractBuildingName(tradeInfo)) // 별도 로직으로 추출
            .build();
    }
}
```

### 3. 데이터베이스 인덱싱(Indexing) 적용

**핵심 개념**: 실제 서비스 성능에 가장 큰 영향을 미치는 것 중 하나가 인덱스입니다.

#### 인덱스 추가 전략
**대상**: MemberEntity의 nickName 컬럼이나 BoardEntity의 region 컬럼처럼 **검색 조건으로 자주 사용되는 컬럼**에 대해 JPA의 @Index 어노테이션을 추가합니다.

#### 실행 계획 분석
**목표**: 왜 이 컬럼에 인덱스를 추가했는지, **인덱스 추가로 인해 쿼리 실행 계획이 어떻게 변하고 성능이 얼마나 향상될 것으로 기대하는지** 설명할 수 있다면, 다른 신입 지원자와 확실히 차별화됩니다.

**결론**: 이처럼 Elasticsearch는 RDBMS를 대체하는 것이 아니라, **RDBMS가 힘들어하는 검색과 분석 영역을 분담하는 파트너**입니다. 두 기술을 함께 활용함으로써 더 넓은 시야를 가진 백엔드 개발자임을 증명할 수 있습니다.

#### 인덱스 전략 (구체적 구현)
```java
// MemberEntity.java에 인덱스 추가
@Entity
@Table(name = "members", indexes = {
    @Index(name = "idx_nickname", columnList = "nickName"), // 닉네임 검색용
    @Index(name = "idx_email", columnList = "email", unique = true), // 이메일 검색용
    @Index(name = "idx_created_at", columnList = "createdAt") // 가입일 정렬용
})
public class MemberEntity {
    // 기존 코드...
}

// BoardEntity.java에 복합 인덱스 추가
@Entity
@Table(name = "board", indexes = {
    @Index(name = "idx_region_created", columnList = "region, createdAt"), // 지역별 최신글 조회용
    @Index(name = "idx_member_created", columnList = "member_id, createdAt"), // 사용자별 글 조회용
    @Index(name = "idx_title_content", columnList = "title, content") // 제목+내용 검색용 (부분적)
})
public class BoardEntity {
    // 기존 코드...
}
```

#### 쿼리 성능 분석 및 최적화
```java
// com/wherehouse/config/DatabaseConfig.java
@Configuration
public class DatabaseConfig {
    
    // 쿼리 실행 계획 로깅 활성화 (개발 환경)
    @Bean
    @Profile("dev")
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/wherehouse?profileSQL=true&logger=Slf4JLogger&explainSlowQueries=true");
        // 기타 설정...
        return dataSource;
    }
}

// 실행 계획 확인을 위한 테스트 코드
@Test
@Sql("/test-data.sql")
public void testQueryPerformance() {
    // EXPLAIN을 통한 실행 계획 확인
    String explainQuery = """
        EXPLAIN SELECT b.*, m.nickName 
        FROM board b 
        JOIN members m ON b.member_id = m.id 
        WHERE b.region = '강남구' 
        ORDER BY b.createdAt DESC 
        LIMIT 20
        """;
        
    // 실행 시간 측정
    long startTime = System.currentTimeMillis();
    List<BoardEntity> results = boardRepository.findByRegionOrderByCreatedAtDesc("강남구", PageRequest.of(0, 20));
    long executionTime = System.currentTimeMillis() - startTime;
    
    assertThat(executionTime).isLessThan(100); // 100ms 이내 실행 목표
    assertThat(results).hasSize(20);
}
```
```java
// MemberEntity.java에 인덱스 추가
@Entity
@Table(name = "members", indexes = {
    @Index(name = "idx_nickname", columnList = "nickName"), // 닉네임 검색용
    @Index(name = "idx_email", columnList = "email", unique = true), // 이메일 검색용
    @Index(name = "idx_created_at", columnList = "createdAt") // 가입일 정렬용
})
public class MemberEntity {
    // 기존 코드...
}

// BoardEntity.java에 복합 인덱스 추가
@Entity
@Table(name = "board", indexes = {
    @Index(name = "idx_region_created", columnList = "region, createdAt"), // 지역별 최신글 조회용
    @Index(name = "idx_member_created", columnList = "member_id, createdAt"), // 사용자별 글 조회용
    @Index(name = "idx_title_content", columnList = "title, content") // 제목+내용 검색용 (부분적)
})
public class BoardEntity {
    // 기존 코드...
}
```

#### 쿼리 성능 분석 및 최적화
```java
// com/wherehouse/config/DatabaseConfig.java
@Configuration
public class DatabaseConfig {
    
    // 쿼리 실행 계획 로깅 활성화 (개발 환경)
    @Bean
    @Profile("dev")
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://localhost:3306/wherehouse?profileSQL=true&logger=Slf4JLogger&explainSlowQueries=true");
        // 기타 설정...
        return dataSource;
    }
}

// 실행 계획 확인을 위한 테스트 코드
@Test
@Sql("/test-data.sql")
public void testQueryPerformance() {
    // EXPLAIN을 통한 실행 계획 확인
    String explainQuery = """
        EXPLAIN SELECT b.*, m.nickName 
        FROM board b 
        JOIN members m ON b.member_id = m.id 
        WHERE b.region = '강남구' 
        ORDER BY b.createdAt DESC 
        LIMIT 20
        """;
        
    // 실행 시간 측정
    long startTime = System.currentTimeMillis();
    List<BoardEntity> results = boardRepository.findByRegionOrderByCreatedAtDesc("강남구", PageRequest.of(0, 20));
    long executionTime = System.currentTimeMillis() - startTime;
    
    assertThat(executionTime).isLessThan(100); // 100ms 이내 실행 목표
    assertThat(results).hasSize(20);
}
```

---

## 📈 3주차 확장 계획 (선택사항)

### 1. 고급 분석 기능 추가 (15-17일차)
#### 머신러닝 기반 가격 예측
```java
// com/wherehouse/ml/service/PricePredictionService.java
@Service
public class PricePredictionService {
    
    public PricePrediction predictPrice(PricePredictionRequest request) {
        // 1. Elasticsearch에서 유사한 조건의 과거 데이터 수집
        List<RealEstateTrade> historicalData = collectHistoricalData(request);
        
        // 2. 간단한 선형 회귀 모델 적용
        LinearRegressionModel model = buildLinearRegressionModel(historicalData);
        
        // 3. 예측 수행
        double predictedPrice = model.predict(request.getFeatures());
        double confidenceInterval = model.getConfidenceInterval();
        
        return PricePrediction.builder()
            .predictedPrice(predictedPrice)
            .confidenceInterval(confidenceInterval)
            .basedOnDataPoints(historicalData.size())
            .build();
    }
}
```

#### 시계열 분석
```java
// 월별 가격 추이 예측
public List<PriceTrendForecast> forecastPriceTrend(String regionName, int forecastMonths) {
    // Elasticsearch에서 월별 평균가 데이터 수집
    Map<String, Double> monthlyPrices = getMonthlyAveragePrices(regionName);
    
    // 이동평균을 이용한 간단한 예측
    return calculateMovingAverageForecast(monthlyPrices, forecastMonths);
}
```

### 2. 실시간 알림 시스템 (18-19일차)
#### 가격 변동 알림
```java
// com/wherehouse/notification/service/PriceAlertService.java
@Service
public class PriceAlertService {
    
    @EventListener
    public void handleNewTradeData(NewTradeDataEvent event) {
        // 새로운 거래 데이터가 들어왔을 때
        List<PriceAlert> alerts = findActiveAlerts(event.getRegionName());
        
        for (PriceAlert alert : alerts) {
            if (shouldTriggerAlert(alert, event.getTradeData())) {
                sendNotification(alert.getUserId(), createAlertMessage(alert, event));
            }
        }
    }
    
    private boolean shouldTriggerAlert(PriceAlert alert, RealEstateTrade trade) {
        return switch (alert.getAlertType()) {
            case PRICE_DROP -> trade.getPrice() <= alert.getTargetPrice();
            case PRICE_RISE -> trade.getPrice() >= alert.getTargetPrice();
            case HIGH_VOLUME -> getCurrentTradeVolume(trade.getRegionName()) >= alert.getVolumeThreshold();
        };
    }
}
```

### 3. 대시보드 및 시각화 (20-21일차)
#### Kibana 대시보드 구성
```json
// kibana_dashboard_config.json
{
  "version": "8.14.0",
  "objects": [
    {
      "type": "visualization",
      "id": "region-price-heatmap",
      "attributes": {
        "title": "지역별 평균 가격 히트맵",
        "type": "vega",
        "params": {
          "spec": {
            "data": {
              "url": {
                "index": "real_estate_trades",
                "body": {
                  "aggs": {
                    "regions": {
                      "terms": {"field": "regionName.keyword"},
                      "aggs": {
                        "avg_price": {"avg": {"field": "price"}}
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  ]
}
```

#### 관리자 대시보드 API
```java
// com/wherehouse/admin/controller/DashboardController.java
@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {
    
    @GetMapping("/overview")
    public ResponseEntity<DashboardOverview> getDashboardOverview() {
        return ResponseEntity.ok(DashboardOverview.builder()
            .totalProperties(getTotalPropertyCount())
            .totalUsers(getTotalUserCount())
            .todayTrades(getTodayTradeCount())
            .topRegions(getTop10RegionsByVolume())
            .systemHealth(getSystemHealthStatus())
            .build());
    }
    
    @GetMapping("/trade-volume-chart")
    public ResponseEntity<List<TradeVolumeData>> getTradeVolumeChart(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        return ResponseEntity.ok(getTradeVolumeData(startDate, endDate));
    }
}
```

---

## 🎯 면접 대비 핵심 포인트

### 1. 기술 선택 근거 설명
**예상 질문**: "왜 Elasticsearch를 선택했나요?"

**답변 포인트**:
```
"기존 프로젝트의 가장 큰 문제는 정적인 데이터와 단순한 추천 로직이었습니다. 
실거래가 데이터라는 대용량의 시계열 데이터를 다뤄야 했고, 
사용자가 원하는 복잡한 검색 조건(지역, 가격, 평수, 날짜 등)을 빠르게 처리해야 했습니다.

RDBMS만으로는 이런 요구사항을 효율적으로 처리하기 어려웠습니다:
1. LIKE 검색의 성능 한계
2. 복잡한 집계 쿼리 시 성능 저하
3. JSON 형태의 API 응답을 정규화된 테이블에 저장하는 복잡성

Elasticsearch는 이런 문제들을 해결하는 최적의 도구였습니다:
- 전문 검색 엔진으로서의 빠른 검색 성능
- Aggregation을 통한 실시간 통계 분석
- JSON 문서 기반의 유연한 데이터 저장

결과적으로 '지역별 가격 변동성 분석' 같은 의미있는 비즈니스 로직을 구현할 수 있었습니다."
```

### 2. 문제 해결 과정 어필
**예상 질문**: "프로젝트를 진행하면서 가장 어려웠던 점은?"

**답변 포인트**:
```
"가장 어려웠던 점은 두 가지 데이터베이스 간의 데이터 일관성 유지였습니다.

상황: 사용자가 Elasticsearch의 매물을 북마크할 때, RDBMS에도 해당 정보를 저장해야 했습니다.
문제: 
- Elasticsearch에는 있지만 RDBMS에는 없는 매물 정보
- 두 시스템 간의 트랜잭션 처리 불가
- 데이터 동기화 시점의 불일치

해결 과정:
1. Eventual Consistency 패턴 채택
2. RDBMS에 핵심 정보만 저장하고, 상세 정보는 Elasticsearch에서 조회
3. 실패 시 보상 트랜잭션(Saga 패턴) 구현
4. 데이터 검증 로직 추가

이 과정에서 분산 시스템의 복잡성을 이해하게 되었고, 
각 데이터베이스의 장단점을 고려한 설계의 중요성을 깨달았습니다."
```

### 3. 성능 최적화 경험
**예상 질문**: "성능을 어떻게 최적화했나요?"

**답변 포인트**:
```
"주요 성능 최적화 포인트는 세 가지였습니다:

1. 데이터베이스 인덱스 최적화
   - 자주 사용되는 검색 조건(지역, 날짜)에 복합 인덱스 생성
   - EXPLAIN을 통한 실행 계획 분석 및 개선
   - 결과: 지역별 게시글 조회 시간 200ms → 50ms로 단축

2. JPA N+1 문제 해결
   - Fetch Join을 통한 연관 엔티티 한 번에 로딩
   - @BatchSize를 통한 배치 로딩 최적화
   - 결과: 북마크 목록 조회 시 쿼리 수 N+1개 → 2개로 감소

3. Elasticsearch 집계 쿼리 최적화
   - 필요한 필드만 선택하는 Source Filtering 적용
   - 집계 결과만 필요한 경우 size=0으로 설정
   - 결과: 지역별 통계 조회 시간 1초 → 200ms로 단축

이런 최적화를 통해 사용자 경험을 크게 개선할 수 있었습니다."
```

### 4. 확장성 고려사항
**예상 질문**: "이 시스템을 실제 서비스로 운영한다면 어떤 점을 고려해야 할까요?"

**답변 포인트**:
```
"실제 서비스 운영을 위해서는 여러 측면을 고려해야 합니다:

1. 확장성 (Scalability)
   - Elasticsearch 클러스터링을 통한 수평 확장
   - 데이터베이스 읽기 전용 레플리카 구성
   - Redis를 통한 캐싱 레이어 추가

2. 안정성 (Reliability)
   - 데이터 백업 및 복구 전략
   - Circuit Breaker 패턴으로 외부 API 장애 대응
   - 모니터링 및 알림 시스템 구축

3. 보안 (Security)
   - API 요청 제한 (Rate Limiting)
   - 개인정보 암호화 저장
   - HTTPS 및 JWT 토큰 보안 강화

4. 운영 효율성
   - 로그 중앙화 (ELK Stack)
   - 메트릭 수집 및 대시보드 (Prometheus + Grafana)
   - CI/CD 파이프라인 구축

이런 요소들을 차근차근 도입해가며 안정적인 서비스를 만들어가고 싶습니다."
```

---

## 📋 체크리스트

### 1주차 완료 기준
- [ ] Docker로 Elasticsearch + Kibana 실행 확인
- [ ] Spring Boot와 Elasticsearch 연동 성공
- [ ] 국토교통부 API 호출 및 데이터 파싱 완료
- [ ] RealEstateTrade Document 클래스 구현
- [ ] 스케줄러를 통한 자동 데이터 수집 동작 확인
- [ ] Kibana에서 저장된 데이터 시각화 확인

### 2주차 완료 기준
- [ ] 지역별 가격 통계 API 구현 및 테스트
- [ ] 가격 변동성 계산 로직 구현
- [ ] 새로운 추천 API 엔드포인트 완성
- [ ] Postman/Swagger를 통한 API 테스트 완료
- [ ] README.md 업데이트 및 기술 문서화
- [ ] 기존 코드에 @Deprecated 어노테이션 추가

### 3주차 확장 완료 기준 (선택)
- [ ] 머신러닝 기반 가격 예측 기능 프로토타입
- [ ] 실시간 알림 시스템 구현
- [ ] 관리자 대시보드 완성
- [ ] 성능 테스트 및 최적화 완료
- [ ] 전체 시스템 통합 테스트

### 최종 점검사항
- [ ] 모든 API가 정상 동작하는가?
- [ ] 데이터베이스 인덱스가 적절히 설정되었는가?
- [ ] 에러 핸들링이 충분히 구현되었는가?
- [ ] 로그가 적절히 남고 있는가?
- [ ] 코드 주석과 문서가 충분한가?
- [ ] Git 커밋 메시지가 의미있게 작성되었는가?

---

## 🚀 프로젝트 완료 후 기대효과

### 1. 기술적 성장
- **데이터 엔지니어링 역량**: 대용량 데이터 수집, 가공, 분석 파이프라인 구축 경험
- **검색 엔진 활용**: Elasticsearch를 활용한 고성능 검색 및 분석 시스템 구현
- **아키텍처 설계**: Polyglot Persistence 패턴을 통한 최적의 데이터 저장소 선택
- **성능 최적화**: 데이터베이스 인덱싱, JPA 최적화, 쿼리 튜닝 경험

### 2. 포트폴리오 차별화
- **문제 해결 능력**: 명확한 문제 정의와 데이터 기반 해결책 제시
- **최신 기술 트렌드**: 검색, 분석, 빅데이터 처리 기술 활용
- **실무 적용성**: 실제 서비스에서 사용되는 기술 스택과 패턴 적용
- **확장 가능성**: 머신러닝, 실시간 처리 등으로의 확장 가능성 제시

### 3. 취업 시장에서의 경쟁력
- **차별화된 이력서**: "게시판 개발자"에서 "데이터 분석 시스템 개발자"로 포지셔닝
- **면접 우위**: 구체적인 기술 선택 근거와 문제 해결 경험 어필
- **적용 범위 확대**: 이커머스, 핀테크, 부동산테크 등 다양한 도메인 지원 가능
- **성장 잠재력**: 지속적인 학습과 기술 적용 능력 증명

이 계획서를 따라 차근차근 구현한다면, 2~3주 후에는 완전히 새로운 차원의 프로젝트로 탈바꿈할 수 있을 것입니다. 각 단계별로 구체적인 목표와 결과물이 명시되어 있으니, 하나씩 체크해가며 진행하시기 바랍니다.