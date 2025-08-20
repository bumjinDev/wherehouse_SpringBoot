# WhereHouse 프로젝트를 위한 Elasticsearch 통합 학습 가이드

## 프로젝트 개요

본 문서는 WhereHouse 프로젝트 개선을 위한 Elasticsearch 학습 범위와 일정을 정의한다.

프로젝트 목표 달성을 위해서는 Elasticsearch의 모든 기능을 학습할 필요는 없다. 핵심적으로 요구되는 세 가지 기능에 집중한다:
1. **데이터 색인(Indexing)**: 외부 API 데이터를 Elasticsearch에 저장
2. **조건부 검색(Query)**: 사용자 조건에 맞는 매물 검색  
3. **통계 분석(Aggregation)**: 지역별 시세, 안전도 등 통계 데이터 생성

기존 단순 분기문 기반 로직과 달리, 개선된 시스템의 핵심 비즈니스 로직은 Elasticsearch 쿼리로 구현된다. 따라서 데이터 가공 및 조회 방식의 이해가 프로젝트 성공의 핵심 요소가 된다.

## 왜 Elasticsearch인가?

서비스 빈에서 모든 데이터를 가져와 직접 계산하는 방식은 이론적으로는 가능하지만, 실제 서비스 환경에서는 **성능, 기능 구현의 복잡성, 확장성** 문제 때문에 Elasticsearch와 같은 검색 엔진을 사용하는 것이 필수적이다.
서비스 빈에서 모든 데이터를 가져와 직접 계산하는 방식의 명백한 한계는 다음과 같다.

### 1. 검색 속도 및 성능

가장 결정적인 이유이다.
* **서비스 빈 방식**: 서울의 모든 매물 데이터(수만 ~ 수십만 건)를 DB나 API에서 애플리케이션 메모리로 **전부 가져온 후**, Java의 `for`문 등을 사용해 사용자의 조건(가격, 지역 등)에 맞는 데이터를 일일이 찾아야 한다. 데이터가 10만 건만 되어도 응답 시간은 수초에서 수십 초까지 늘어날 수 있으며, 이는 서비스로 사용 불가능한 수준이다.
* **Elasticsearch 방식**: Elasticsearch는 **역색인(Inverted Index)**이라는 특별한 데이터 구조를 사용한다. 이는 책의 맨 뒤에 있는 '찾아보기'와 같다. 특정 단어(예: "강남구")가 어떤 문서에 있는지 즉시 알 수 있으므로, 전체 데이터를 스캔할 필요 없이 수백만 건의 데이터 속에서도 단 몇 밀리초 만에 원하는 결과를 찾아낼 수 있다.

### 2. 전문화된 기능 구현의 한계

계획하신 핵심 기능들은 Java 코드로 직접 구현하기 매우 어렵거나 비효율적이다.
* **지리 공간 쿼리**: "특정 매물 위치 반경 500m 내 CCTV 개수"를 계산하려면, 서비스 빈에서는 모든 CCTV 좌표와 매물 좌표 간의 거리를 복잡한 수학 공식을 이용해 일일이 계산해야 한다. 이는 엄청난 계산 부하를 유발한다. 반면 Elasticsearch의 `geo_distance` 쿼리는 이 기능을 내부적으로 매우 빠르고 효율적으로 처리하도록 설계되었다.
* **실시간 통계 분석 (Aggregation)**: "구별 평균 가격과 가격 변동성(표준편차)"을 계산하려면, 서비스 빈에서는 먼저 '구'별로 데이터를 그룹화하고 각 그룹에 대해 통계 계산을 반복해야 한다. Elasticsearch의 **집계(Aggregations)** 기능은 이런 종류의 데이터 분석을 단 한 번의 쿼리로 매우 빠르게 처리하도록 최적화되어 있다.

### 3. 확장성 및 시스템 부하

* **서비스 빈 방식**: 모든 계산 부하가 **애플리케이션 서버(Spring Boot)**에 집중된다. 사용자가 몰리면 애플리케이션 서버의 CPU와 메모리가 한계에 도달하고, 전체 서비스가 마비될 수 있다.
* **Elasticsearch 방식**: 검색, 필터링, 분석과 같은 무거운 작업을 **검색 엔진(Elasticsearch)에 위임**한다. 애플리케이션 서버는 사용자 요청을 받아 Elasticsearch에 전달하고, 그 결과를 가공해 보여주는 역할만 하므로 부하가 분산된다. 이를 통해 더 많은 사용자 트래픽을 안정적으로 처리할 수 있다.

결론적으로, API 데이터를 단순히 가져와 Java 코드로 계산하는 것은 소규모의 정적인 데이터나 간단한 필터링에는 적용할 수 있으나, **대용량의 동적 데이터를 다루고 복합적인 검색/분석 기능을 제공해야 하는 현대적인 웹 서비스에는 부적합한 방식**이다.

## 1. Elasticsearch 최소 학습 범위 (Must-Know)

이 프로젝트만을 위한 핵심 바운더리는 다음과 같다. 아래 목록 외의 기능은 학습 대상에서 제외해도 좋다.

### 1.1 기본 환경 구축 (1순위)

**학습 내용**: Docker를 이용한 Elasticsearch 및 Kibana 설치 및 실행

**프로젝트 역할**: 개발 환경을 구축하는 가장 첫 단계이다. Kibana는 Elasticsearch에 데이터가 잘 들어갔는지 눈으로 확인하는 필수 시각화 도구이다.

### 1.2 핵심 개념 이해

**학습 내용**: Index, Document, Mapping 이 세 가지 용어의 관계

**프로젝트 역할**: Elasticsearch의 기본 구조를 이해하기 위해 필요하다. 간단히 Index는 '데이터베이스', Document는 '테이블의 한 행(Row)', Mapping은 '테이블 스키마(Schema)' 와 유사하다고 생각하면 된다.

### 1.3 Spring Boot 연동

**학습 내용**: Spring Data Elasticsearch를 사용한 연동 방법

- application.properties 설정
- @Document 어노테이션을 사용한 엔티티 클래스 정의
- ElasticsearchRepository 인터페이스를 사용한 기본 데이터 저장/조회

**프로젝트 역할**: 현재의 Java 백엔드와 Elasticsearch를 연결하는 다리 역할을 한다.

### 1.4 필수 쿼리 3종 세트

이 부분이 가장 중요하며, 비즈니스 로직의 핵심이 된다.

#### 1.4.1 필터링 쿼리 (Filtering)

**학습 내용**: Bool Query를 사용하여 Term(정확히 일치), Match(텍스트 검색), Range(범위) 조건을 조합하는 방법

**프로젝트 역할**: "강남구에서", "전세금이 2억에서 3억 사이인" 매물을 찾는 1단계 기본 검색 로직을 구현한다.

#### 1.4.2 지리 공간 쿼리 (Geo-spatial Query)

**학습 내용**: geo_distance 쿼리 사용법

**프로젝트 역할**: "사용자가 찍은 위치(Pin-Point)에서 반경 500m 내 CCTV 개수" 또는 **"가장 가까운 파출소까지의 거리"**를 계산하는 핵심 안전 점수 로직을 구현한다.

#### 1.4.3 집계 쿼리 (Aggregations)

**학습 내용**: Terms(그룹화), Extended Stats(평균, 표준편차 등 통계) 집계 사용법

**프로젝트 역할**: "구별 평균 가격", "가격 변동성(표준편차)" 등 통계 데이터를 생성하는 로직을 구현한다.

## 2. 지금은 몰라도 되는 범위 (Don't-Need-to-Know)

학습 시간을 단축하기 위해 아래 내용들은 과감히 무시한다.

- **클러스터 운영 및 관리**: 샤드, 레플리카 설정 등 분산 시스템 관리 (단일 노드로 충분)
- **고급 텍스트 분석**: 형태소 분석기(Analyzer), 토크나이저(Tokenizer) 등 자연어 처리 기술
- **성능 튜닝**: 인덱스 설정 최적화, 캐시 관리 등
- **데이터 파이프라인**: Logstash, Beats 등 ELK Stack의 다른 구성 요소

## 3. 현실적인 2주 계획

| 기간 | 목표 | 핵심 활동 | 결과물 |
|------|------|-----------|--------|
| **1주차 (1~4일)** | 데이터 적재 (Getting Data In) | Docker로 ES/Kibana 실행, Spring Boot 연동, 외부 API 데이터 파싱 및 ES 저장 로직 구현 | Kibana에서 API로 수집한 데이터가 조회되는 상태 |
| **1주차 (5~7일)** | 기본 검색 API 구현 | Bool Query를 사용한 1단계 매물 검색 API 개발 | 조건에 맞는 매물 목록을 JSON으로 반환하는 API |
| **2주차 (8~12일)** | 핵심 로직 구현 | geo_distance 쿼리로 위치 기반 안전 점수 계산, Aggregations로 통계 데이터 생성, 2단계 폴백(Fallback) 로직에 위 기능들 통합 | 안전 점수와 통계가 포함된 최종 추천 API |
| **2주차 (13~14일)** | 정리 및 테스트 | 코드 리팩토링 및 최종 테스트, README 문서 업데이트 | 동작 가능한 프로젝트 최종 버전 |

---

## [1순위] 기본 환경 구축 및 핵심 개념
이 단계는 Elasticsearch를 직접 실행해보고 기본적인 데이터 구조를 이해하는 것을 목표로 한다. 이론과 실습을 병행하는 것이 중요하다.

### 학습 리스트
- Docker를 이용한 Elasticsearch 및 Kibana 실행
- 핵심 개념 3가지(Index, Document, Mapping)의 관계 이해

### 배경지식 및 설명
**Docker 사용 이유**: Elasticsearch를 직접 설치하는 과정은 운영체제별로 복잡하고 번거롭다. Docker를 사용하면 간단한 명령어 몇 줄로 항상 동일하고 격리된 환경에서 Elasticsearch와 시각화 도구인 Kibana를 실행할 수 있다. 프로젝트 환경을 빠르고 일관성 있게 구축하기 위해 필수적이다.

**Kibana의 역할**: Kibana는 Elasticsearch에 저장된 데이터를 눈으로 직접 확인하고, 쿼리를 테스트해볼 수 있는 웹 기반 GUI이다. 개발 단계에서는 거의 항상 함께 사용한다.

**핵심 개념의 관계 (RDBMS 비유)**:

- **Index**: 관계형 데이터베이스(RDBMS)의 데이터베이스(Database) 와 유사한 개념이다. 데이터가 저장되는 가장 큰 단위이다.
- **Document**: RDBMS의 행(Row) 에 해당하며, 데이터가 저장되는 기본 단위이다. Elasticsearch는 이 Document를 JSON 형식으로 저장한다.
- **Mapping**: RDBMS의 스키마(Schema) 와 같다. Document에 포함된 각 필드가 어떤 데이터 타입(텍스트, 숫자, 날짜, 위치정보 등)을 갖는지 정의하는 것이다.

🎯 **검색 키워드**

**Docker & Basic Setup:**
- `How to install Elasticsearch Kibana with Docker`
- `Run Elasticsearch and Kibana on Docker`

**Core Concepts:**
- `Elasticsearch index document mapping basic concepts`
- `Elasticsearch data structures tutorial`

## [2순위] Spring Boot 연동 및 데이터 색인
이 단계는 Java 백엔드와 Elasticsearch를 연결하고, 외부 API로부터 가져온 데이터를 Elasticsearch에 저장(색인)하는 것을 목표로 한다.

### 학습 리스트
- Spring Data Elasticsearch 연동 설정 (application.yml)
- @Document 어노테이션과 ElasticsearchRepository 인터페이스 사용법
- 부동산 API 응답 데이터를 Document 객체로 변환하여 저장하는 로직 구현

### 배경지식 및 설명
**Spring Data Elasticsearch**: Spring Data 생태계의 일부로, 개발자가 Elasticsearch의 복잡한 클라이언트 API를 직접 다루지 않고도 마치 **JPA(Java Persistence API)**처럼 친숙한 방식으로 Elasticsearch와 상호작용할 수 있게 해주는 추상화 라이브러리이다.

**@Document**: 이 어노테이션을 Java 클래스에 붙이면, "이 클래스는 Elasticsearch의 특정 인덱스에 저장될 문서(Document)다"라고 선언하는 것과 같다. indexName 속성으로 인덱스 이름을 지정할 수 있다.

**ElasticsearchRepository**: 이 인터페이스를 상속받아 Repository를 만들면, JPA의 JpaRepository처럼 save(), findById(), findAll()과 같은 기본적인 CRUD(Create, Read, Update, Delete) 메소드가 자동으로 제공된다.

### 구현 흐름:
1. 공공데이터포털에서 부동산 데이터를 호출한다.
2. 응답받은 JSON 데이터를 @Document 어노테이션이 붙은 자바 객체(예: Property.java)로 변환한다.
3. ElasticsearchRepository의 save() 메소드를 호출하여 이 객체를 Elasticsearch에 저장(색인)한다.

🎯 **검색 키워드**

**Integration:**
- `Spring Data Elasticsearch integration guide`
- `How to connect Spring Boot with Elasticsearch`

**Repository Usage:**
- `Spring Boot ElasticsearchRepository tutorial`
- `How to use ElasticsearchRepository in Spring Boot`

## [3순위] 핵심 쿼리 구현
이 단계가 프로젝트의 핵심 기능을 구현하는 가장 중요한 부분이다. 계획서에 명시된 기능과 직접적으로 연결되는 3가지 쿼리 유형을 집중적으로 학습한다.

### 3.1. 필터링 쿼리 (Bool Query)
**학습 리스트**: bool 쿼리, must vs filter 절의 차이, term (정확한 값), range (범위) 쿼리 조합.

**배경지식 및 설명**:

**bool 쿼리**: 여러 개의 쿼리 조건을 논리적으로(AND, OR, NOT) 조합하는 '컨테이너' 역할을 한다.

**must 와 filter의 결정적 차이**: 두 절 모두 'AND' 조건처럼 동작하지만, must는 검색 결과의 관련성 점수(_score) 계산에 영향을 주고, filter는 점수 계산을 생략한다.

**핵심**: filter 절은 점수 계산을 생략하기 때문에 성능이 훨씬 빠르고 결과를 캐싱할 수 있다. 따라서 "가격이 2억에서 3억 사이" 또는 "건물 유형이 '아파트'"와 같이 예/아니오로 명확히 구분되는 조건에는 반드시 filter 절을 사용해야 한다. 이는 성능 최적화의 기본이다.

### 3.2. 지리 공간 쿼리 (Geo-spatial Query)
**학습 리스트**: geo_point 데이터 타입 매핑, geo_distance 쿼리 사용법.

**배경지식 및 설명**:

**geo_point 타입**: Elasticsearch에 위치 정보를 저장하려면, 먼저 매핑 단계에서 해당 필드의 타입을 geo_point로 지정해야 한다. 이렇게 하면 Elasticsearch가 해당 필드를 위도/경도 좌표로 인식하고 인덱싱한다.

**geo_distance 쿼리**: 특정 좌표(예: 사용자가 선택한 매물 위치)를 중심으로 일정 반경(예: 500m) 내에 있는 모든 문서를 검색하는 기능이다. 프로젝트의 '복합 안전성 점수' (예: 주변 CCTV 개수, 가장 가까운 파출소 거리 계산)를 구현하는 데 필수적인 기술이다.

### 3.3. 집계 쿼리 (Aggregations)
**학습 리스트**: terms (그룹화) 집계, extended_stats (통계) 집계.

**배경지식 및 설명**:

**Aggregations**: RDBMS의 GROUP BY와 유사하지만, 훨씬 더 강력하고 다양한 분석 기능을 제공하는 Elasticsearch의 핵심 기능이다.

**terms 집계**: 특정 필드의 값을 기준으로 문서를 그룹화한다. 예를 들어, "gu_name" 필드를 기준으로 terms 집계를 실행하면 "구별" 통계를 낼 수 있다.

**extended_stats 집계**: 숫자 필드에 대해 평균, 합계, 최소/최대값, 표준편차 등 다양한 통계치를 한 번에 계산해준다.

**활용**: terms 집계로 "구별" 그룹을 만들고, 각 그룹 내에서 extended_stats 집계를 price 필드에 적용하면 '구별 평균 시세'와 '가격 변동성(표준편차)' 를 단 한 번의 쿼리로 효율적으로 계산할 수 있다.

🎯 **검색 키워드**

**3.1. Filtering (Bool Query)**
- `Elasticsearch bool query filter vs must`
- `Elasticsearch query vs filter context`
- `Elasticsearch term match range query example`

**3.2. Geo-spatial Query**
- `Elasticsearch geo_distance query tutorial`
- `Spring Data Elasticsearch geo_point mapping`
- `Elasticsearch geo spatial query examples`

**3.3. Aggregations**
- `Elasticsearch Aggregations beginner's guide`
- `Elasticsearch terms and extended_stats aggregation example`
- `Spring Data Elasticsearch aggregations tutorial`

## 최종 체크리스트

### 1주차 완료 기준
- [ ] Docker로 Elasticsearch + Kibana 실행 성공
- [ ] Spring Boot에서 Elasticsearch 연결 확인 (application.properties 설정 포함)
- [ ] @Document 어노테이션을 사용한 엔티티 클래스 정의 완료
- [ ] ElasticsearchRepository 인터페이스를 사용한 기본 데이터 저장/조회 구현
- [ ] 외부 API 데이터를 Document 객체로 변환하여 저장
- [ ] Kibana에서 저장된 데이터 조회 확인
- [ ] Bool Query를 사용하여 Term(정확히 일치), Match(텍스트 검색), Range(범위) 조건을 조합한 기본 필터링 검색 API 구현

### 2주차 완료 기준  
- [ ] geo_point 데이터 타입 매핑 완료
- [ ] geo_distance 쿼리로 위치 기반 안전 점수 계산 로직 구현
- [ ] Terms(그룹화), Extended Stats(평균, 표준편차 등 통계) 집계 구현
- [ ] 구별 평균 시세, 가격 변동성(표준편차) 통계 생성
- [ ] 2단계 폴백(Fallback) 로직에 위 기능들 통합
- [ ] 안전 점수와 통계가 포함된 최종 추천 API 완성
- [ ] 코드 리팩토링 및 최종 테스트
- [ ] README 문서 업데이트

**최종 목표**: 기존 단순 분기문 로직을 Elasticsearch 쿼리 기반의 고도화된 매물 추천 시스템으로 전환, 동작 가능한 프로젝트 최종 버전 완성

---

[ 참고 문헌 ]
- 공식 사이트 : https://www.elastic.co/docs/get-started/
- * 중요) 기본 데이터 셋 설명 : https://www.elastic.co/docs/manage-data/data-store/index-basics#elasticsearch-intro-documents-fields-data-metadata