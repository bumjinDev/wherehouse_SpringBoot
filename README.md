# WHEREHOUSE: 서울시 1인가구 주거지 추천 서비스

![Wherehouse Banner](https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8)

---

## 📌 프로젝트 개요

**[프로젝트 기간]**
- **1차 개발 (기능 구현):** 2023.09 ~ 2024.04
- **2차 개발 (고도화):** 2024.08 ~ 2025.03 (보안, 캐싱, 배포 자동화)

**[참여 인원]**
- 3명 (본인: 백엔드 설계 및 구현 총괄)

**[프로젝트 개요]**  
서울 내 1인 가구의 주거지 선택 문제를 해결하기 위해, 사용자의 **예산과 안전/편의 선호도**를 기반으로 최적의 행정구를 추천하는 서비스입니다. Spring Boot 기반으로 **인증, 인가, 캐싱, CI/CD, 보안 정책, 추천 로직**을 포함한 전체 백엔드 시스템을 주도적으로 설계하고 구현했습니다.

- **배포 URL**: [https://wherehouse.it.kr/wherehouse/](https://wherehouse.it.kr/wherehouse/)
- **상세 문서**: [비즈니스 로직 명세서](./docs/specification.md) | [설계 근거 백서](./docs/design-rationale.md)

---

## 🧩 WhereHouse Architecture

![Architecture](https://github.com/user-attachments/assets/3f642b20-0713-453b-8be0-b3a10f56a950)

### 추천 로직 데이터 흐름도

```
[사용자 요청 (Request)]
       |
       v
[API Controller] → 전세/월세 분기
       |
       v
[Recommendation Service (전세/월세)]
       |
       v
[1. Strict Search (Redis ZSET 교집합)]
       |
       v
[2. Fallback 조건 검사 (매물 수 < 3개?)] --(Yes)--> [3. Expanded Search (조건 완화 재검색)]
       | (No)
       v
[4. 매물 상세 정보 조회 (Redis Hash)]
       |
       v
[5. 매물별 점수 계산 (정규화 및 가중치 적용)]
       |
       v
[6. 지역구별 점수 계산 (대표 점수 산출)]
       |
       v
[7. Top 3 지역구 정렬]
       |
       v
[API Controller] → 최종 응답 (Response)
```

---

## 🧑‍💻 팀원 구성

| 정범진 | 이재서 |
|--------|--------|
| <img src="https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39" width="100" height="100"> | <img src="https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b" width="100" height="100"> |
| [@bumjinDev](https://github.com/bumjinDev) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |

---

## 👥 역할 분담

### **1차 프로젝트: UI 개발**
- **한준원**: 전체 UI 설계 및 제작, 주거지 추천/지역구 정보 페이지 개발
- **이재서**: 행정동별 정보 페이지 개발
- **정범진**: 상세지도 클릭 이벤트 기반 좌표 처리, CCTV 마커 표시 구현

### **2차 프로젝트: Servlet 기반 개발**
- **한준원**: 1차 주거지 추천 알고리즘 구현
- **이재서**: 상세지도 서비스 Servlet 전환
- **정범진**: 로그인/회원가입 및 게시판 기능 구현

### **3차 프로젝트: Spring Boot 전환 및 인프라 구성**
- **정범진**: 전체 백엔드 구조(Spring Boot), 인증 시스템, 데이터 파이프라인 및 추천 알고리즘 설계/구현, 배포 자동화 담당
- **이재서**: 상세지도 기능 Spring Boot 전환

---

## 💻 주요 기술 구현 내용

### **1. 배치 기반 데이터 파이프라인 및 Redis 인덱싱 시스템**

> 실시간 추천 성능을 위해 국토교통부 API 데이터를 사전 처리하여 Redis에 최적화된 검색 인덱스 구조로 저장하는 자동화 파이프라인을 구축했습니다.

#### **왜 이 구조를 선택했는가?**
기존 시스템은 매 요청마다 DB에서 복잡한 Join 연산을 수행하여 응답 속도가 느렸습니다. 이를 해결하기 위해 "계산은 미리, 검색은 빠르게"라는 원칙으로 배치-캐시 분리 아키텍처를 설계했습니다.

#### **1.1. 데이터 수집 및 정제**

-   **스케줄링**: `@Scheduled` 어노테이션으로 **매월 1일 새벽 4시 10분**에 자동 실행
-   **API 연동**: 
    - 국토교통부 전월세 실거래가 API (`/getRTMSDataSvcAptRent`) 호출
    - 서울시 25개 자치구 코드 매핑 (`SEOUL_DISTRICT_CODES`) 기반 순회
    - 페이징 처리 (1000건/페이지)로 전체 데이터 수집
    - Rate Limit 방지를 위한 200ms 호출 간격 설정
-   **데이터 정제**:
    - XML 응답 파싱 → `Property` DTO 변환
    - 면적 단위 변환 (㎡ × 0.3025 → 평수)
    - 월세금 유무 기반 임대유형 자동 판별 (전세/월세)
    - 법정동명 + 지번 조합으로 전체 주소 생성

#### **1.2. Redis 다중 인덱스 구조 설계**

**핵심 설계 철학: Redis를 단순 캐시가 아닌 검색 엔진으로 활용**

전통적인 RDBMS의 B-Tree 인덱스 대신, Redis Sorted Set의 O(log N) 범위 검색 특성을 활용하여 초고속 필터링 시스템을 구현했습니다.

**전세 매물 저장 구조:**
```
[원본 데이터] property:charter:{UUID}
  → Hash: {propertyId, aptNm, deposit, area, floor, buildYear, address...}

[검색 인덱스 1] idx:charterPrice:{지역구명}
  → Sorted Set: score=전세금, member=propertyId
  
[검색 인덱스 2] idx:area:{지역구명}:전세  
  → Sorted Set: score=평수, member=propertyId
```

**월세 매물 저장 구조:**
```
[원본 데이터] property:monthly:{UUID}
  → Hash: {propertyId, aptNm, deposit, monthlyRent, area...}

[검색 인덱스 1] idx:deposit:{지역구명}
  → Sorted Set: score=보증금, member=propertyId
  
[검색 인덱스 2] idx:monthlyRent:{지역구명}:월세
  → Sorted Set: score=월세금, member=propertyId
  
[검색 인덱스 3] idx:area:{지역구명}:월세
  → Sorted Set: score=평수, member=propertyId  
```

**핵심 장점:**
- **빠른 범위 검색**: Redis Sorted Set의 `ZRANGEBYSCORE` 명령으로 가격/평수 범위 조건을 효율적으로 필터링
- **교집합 연산 최적화**: 전세는 2개, 월세는 3개 인덱스의 교집합으로 모든 조건을 만족하는 매물 ID 즉시 추출
- **Pipeline 배치 조회**: 필터링된 매물 ID 리스트를 `executePipelined`로 일괄 조회하여 네트워크 왕복 시간 최소화

#### **1.3. 정규화 범위 사전 계산**

**왜 정규화가 필요한가?**  
강남구 전세 3억과 노원구 전세 1억을 단순 비교하면 불공정합니다. 각 지역구 내에서의 상대적 가성비를 평가하기 위해 지역구별 min/max 범위를 사전 계산하여 0~100점 척도로 정규화합니다.

**전세 정규화 구조:**
```
bounds:{지역구명}:전세
  → Hash: {minPrice, maxPrice, minArea, maxArea, propertyCount, lastUpdated}
```

**월세 정규화 구조 (보증금/월세금 분리):**
```
bounds:{지역구명}:월세  
  → Hash: {minDeposit, maxDeposit, minMonthlyRent, maxMonthlyRent, minArea, maxArea...}
```

실시간 점수 계산 시 이 범위 값으로 0~100점 정규화하여 지역구 간 공정한 비교 가능

#### **1.4. 안전성 점수 계산 및 저장**

**객관적 지표 기반 안전성 점수화**

단순 범죄 건수가 아닌, 인구 밀도와 유흥업소 밀집도를 고려한 회귀 분석 모델을 적용하여 과학적인 안전성 지표를 산출했습니다.

**데이터 수집:**
- `crimeRepository`: 지역구별 범죄 발생 건수
- `populationRepository`: 지역구별 인구수  
- `entertainmentRepository`: 영업 중인 유흥업소 수

**계산 로직:**
1. **범죄율 산출**: `(범죄 건수 / 인구수) × 100,000` (인구 10만명당)
2. **독립변수 정규화**: 유흥업소 수와 인구수를 각각 0~1 구간으로 정규화
3. **회귀 분석 기반 위험도 계산**: 
   - 범죄위험도 = 정규화된_유흥업소밀도 × 1.0229 - 정규화된_인구밀도 × 0.0034
4. **안전성 점수 변환**: `100 - (범죄위험도 × 10)`
5. **최종 정규화**: 원본 점수를 0~100점 구간으로 재정규화

**저장 구조:**
```
safety:{지역구명}
  → Hash: {districtName, safetyScore, lastUpdated, version}
```

-   **관련 소스**: `BatchScheduler.java`, `AnalysisCrimeRepository.java`, `AnalysisEntertainmentRepository.java`, `AnalysisPopulationDensityRepository.java`

---

### **2. 2단계 폴백을 적용한 하이브리드 추천 알고리즘**

> 사용자 조건을 만족하는 매물이 부족할 때 우선순위에 따라 점진적으로 조건을 완화하여 '결과 없음'을 최소화하는 지능형 검색 시스템입니다.

#### **왜 폴백 시스템이 필요한가?**
기존 시스템의 가장 큰 문제는 조건을 만족하는 매물이 없을 때 빈 결과를 반환한다는 점이었습니다. 하지만 사용자는 "완벽한 매물"이 아니라도 "차선책"을 원합니다. 이를 위해 사용자의 우선순위를 존중하면서도 유연하게 대안을 제시하는 다단계 폴백 알고리즘을 설계했습니다.

#### **2.1. 1단계: Strict Search (엄격한 검색)**

**전세 검색 (2개 인덱스 교집합):**
```java
// idx:charterPrice:{지역구} 에서 전세금 범위 조건 필터링
Set<String> priceValidIds = redis.zrangebyscore("idx:charterPrice:강남구", 20000, 30000);

// idx:area:{지역구}:전세 에서 평수 범위 조건 필터링  
Set<String> areaValidIds = redis.zrangebyscore("idx:area:강남구:전세", 20.0, 30.0);

// 두 조건을 모두 만족하는 매물 ID 추출
priceValidIds.retainAll(areaValidIds);
```

**월세 검색 (3개 인덱스 교집합):**
```java
Set<String> depositValidIds = redis.zrangebyscore("idx:deposit:마포구", 5000, 10000);
Set<String> monthlyRentValidIds = redis.zrangebyscore("idx:monthlyRent:마포구:월세", 50, 100);
Set<String> areaValidIds = redis.zrangebyscore("idx:area:마포구:월세", 15.0, 25.0);

depositValidIds.retainAll(monthlyRentValidIds);
depositValidIds.retainAll(areaValidIds);
```

**안전성 사전 필터링:**
- `minSafetyScore` 옵션이 있을 경우 지역구 레벨에서 먼저 필터링
- `safety:{지역구명}` 조회 → 기준 미달 지역구는 검색 대상에서 제외

#### **2.2. 2단계: Fallback 판단 및 Expanded Search**

**폴백 조건:**
- 지역구별 매물 수가 3개 미만일 경우 해당 지역구만 확장 검색 수행

**확장 전략 (우선순위 역순):**
1. **3순위 조건 완화**:
   - PRICE 3순위: `budgetMax × (1 + budgetFlexibility/100)` 확대
   - SPACE 3순위: `areaMin = absoluteMinArea` 완화
   - SAFETY 3순위: `minSafetyScore` 하향 조정

2. **2순위 조건 추가 완화** (3순위 완화로 부족 시):
   - 동일 파라미터로 2순위 조건도 완화하여 재검색

**사용자 중심 설계:**  
1순위 조건은 절대 완화하지 않아 사용자의 핵심 가치를 보존합니다.

**응답 메시지:**
```json
{
  "searchStatus": "SUCCESS_EXPANDED",
  "message": "원하시는 조건의 전세 매물이 부족하여, 평수 조건을 15평으로, 안전 점수 조건을 70점으로 완화하여 찾았어요."
}
```

#### **2.3. 매물별 점수 계산 로직**

**점수 정규화:**
```java
// 가격 점수 (낮을수록 높은 점수)
priceScore = 100 - ((현재가격 - 지역구최저가) / (지역구최고가 - 지역구최저가) × 100);

// 평수 점수 (넓을수록 높은 점수)  
spaceScore = ((현재평수 - 지역구최소평수) / (지역구최대평수 - 지역구최소평수) × 100);

// 안전 점수
safetyScore = 지역구_안전성_점수; // Redis safety:{지역구} 조회
```

**우선순위별 가중치 적용:**
```java
// 사용자 우선순위 설정: priority1="PRICE", priority2="SAFETY", priority3="SPACE"
Map<String, Double> weights = {
    priority1: 0.6,  // 60%
    priority2: 0.3,  // 30%  
    priority3: 0.1   // 10%
};

finalScore = (priceScore × weights.get("PRICE")) +
             (safetyScore × weights.get("SAFETY")) +
             (spaceScore × weights.get("SPACE"));
```

**월세 특별 처리:**
- PRICE 가중치를 보증금과 월세금에 50%씩 분배
```java
depositScore = 100 - ((보증금 - 최저보증금) / (최고보증금 - 최저보증금) × 100);
monthlyRentScore = 100 - ((월세 - 최저월세) / (최고월세 - 최저월세) × 100);

finalScore = (depositScore × 0.3) + (monthlyRentScore × 0.3) + 
             (safetyScore × 0.3) + (spaceScore × 0.1);
```

#### **2.4. 지역구 순위 결정**

**대표 점수 산출:**
```java
// 매물의 평균 품질과 선택의 폭을 동시 고려
representativeScore = 평균_finalScore × Math.log(매물개수 + 1);
```

**정렬 우선순위:**
1. 대표 점수 내림차순
2. 매물 개수 내림차순 (동점 시)
3. 지역구명 알파벳 순 (2차 동점 시)

**Top 3 지역구 선정 후 각 지역구별 상위 3개 매물 반환**

-   **관련 소스**: `CharterRecommendationService.java`, `MonthlyRecommendationService.java`, `RecommendationController.java`

---

### **3. JWT를 활용한 Stateless 인증 시스템**

> 서버의 확장성을 확보하고 클라이언트와의 결합도를 낮추기 위해, 순수 JWT 기반의 Stateless 인증 시스템을 구축했습니다.

-   **인증 흐름 및 구현**:
    -   **토큰 발급 및 검증**: 로그인 성공 시, 서버의 비밀 키로 서명된 **HMAC-SHA256 JWT**를 생성합니다. 모든 API 요청 시에는 이 서명을 검증하여 데이터의 무결성을 보장하며, 검증 완료 후 `SecurityContextHolder`에 사용자 정보를 등록하여 인가에 활용합니다.
    -   **안전한 토큰 전송**: 생성된 토큰은 XSS 공격으로부터 보호하기 위해 **`HttpOnly`** 속성이 부여된 쿠키를 통해 안전하게 클라이언트에 전달됩니다.
    -   **Stateless 로그아웃**: 로그아웃은 서버에 별도의 상태를 저장하는 대신, 클라이언트 측의 JWT 쿠키를 즉시 만료시키는 방식으로 구현하여 Stateless 아키텍처의 원칙을 유지합니다.

-   **관련 소스**: `LoginFilter.java`, `JwtAuthProcessorFilter.java`, `JWTUtil.java`, `CookieLogoutHandler.java`

---

### **4. Spring Security FilterChain 모듈화**

> 단일 필터 체인으로 모든 경로의 보안을 관리할 때 발생하는 복잡성을 해결하기 위해, URL 패턴별로 보안 책임을 분리하는 모듈화된 구조를 도입하여 유지보수성과 유연성을 확보했습니다.

-   **핵심 구현**:
    -   **FilterChain 분리**: `/login`, `/members/**`, `/boards/**` 등 주요 엔드포인트 별로 독립된 `SecurityFilterChain`을 `@Bean`으로 등록했습니다. `securityMatcher`를 통해 각 필터가 담당할 URL 범위를 명확히 지정하여 서비스별 최적화된 보안 정책을 적용했습니다.
    -   **인증/인가 예외 처리 분리**: 시나리오별 명확한 피드백을 위해 예외 처리 책임을 분리했습니다.
        -   **인증 실패 (401)**: `AuthenticationEntryPoint`를 커스텀하여 유효하지 않은 접근 시 쿠키 삭제 및 안내 메시지를 반환합니다.
        -   **인가 실패 (403)**: `AccessDeniedHandler`를 커스텀하여 권한이 없는 리소스 접근 시 명확한 권한 부족 메시지를 전달합니다.

-   **관련 소스**: `SecurityConfig.java`, `JwtAuthenticationFailureHandler.java`, `JwtAccessDeniedHandler.java`

---

### **5. 계층형 아키텍처 설계**

> **관심사의 분리(SoC)** 원칙에 기반하여 각 계층의 독립성을 확보하고 유지보수성을 극대화했습니다. 특히 프레임워크 기술과 핵심 도메인 로직을 분리하여 시스템의 유연성을 높였습니다.

-   **세부 구현**:
    -   **DTO-Entity 분리**: 클라이언트 통신을 위한 `DTO`와 영속성을 위한 `Entity`를 명확히 분리하고, 전용 `Converter`를 통해 계층 간 데이터 변환을 담당했습니다. 이를 통해 DB 스키마 변경이 프레젠테이션 계층에 미치는 영향을 차단했습니다.
    -   **API-View 컨트롤러 분리**: JSON 응답을 위한 `@RestController`와 JSP 페이지 렌더링을 위한 `@Controller`의 역할을 명확히 구분하여 설계했습니다. `RecommendationController`가 전세/월세 요청을 각각 다른 DTO(`CharterRecommendationRequestDto`, `MonthlyRecommendationRequestDto`)로 받아 처리하여 API의 명확성을 확보했습니다.
    -   **도메인-보안 모델 분리**: 핵심 비즈니스 로직을 담는 도메인 엔티티(`MembersEntity`)와 Spring Security 인증을 위한 보안 엔티티(`AuthenticationEntity`)를 분리하여 프레임워크 종속성을 최소화했습니다.

-   **관련 소스**: `BoardConverter.java`, `MemberConverter.java`, `MembersEntity.java`, `AuthenticationEntity.java`, `UserEntityDetails.java`, `RecommendationController.java`

---

## 🔧 문제 해결 방안

### **1. 보안 강화 (Security Hardening)**

| **보안 위협** | **문제 상황** | **해결책** | **구현 기술** |
|-------------|-------------|----------|-------------|
| **JWT 위변조** | 페이로드 디코딩 가능으로 무결성 보장 필요 | HMAC-SHA256 전자서명 적용 | `JWTUtil.java` |
| **중간자 공격** | HTTP 평문 통신으로 토큰 노출 위험 | HTTPS 프로토콜 전면 적용 | 서버 설정 |
| **XSS 공격** | 악성 스크립트의 쿠키 탈취 시도 | HttpOnly 쿠키 + CSP 헤더 설정 | `LoginFilter.java`, `SecurityConfig.java` |
| **CSRF 공격** | 외부 사이트에서의 위조 요청 | SameSite 쿠키 속성 설정 | 쿠키 정책 |
| **무차별 공격** | 비밀번호 해시 크래킹 시도 | BCrypt Salt + 반복 해싱 | `UserAuthenticationProvider.java` |
| **설정 복잡성** | 단일 필터체인의 관리 어려움 | URL별 SecurityFilterChain 모듈화 | `SecurityConfig.java` |

**상세 해결 과정:**
- **JWT 서명**: 
  1) 서버만 알고 있는 비밀 키로 HMAC-SHA256 서명 생성 
  2) 제3자 토큰 변경 시 서명 검증 실패로 즉시 차단
- **전송 암호화**: 
  1) 모든 클라이언트-서버 통신을 HTTPS로 암호화 
  2) 네트워크 스니핑을 통한 토큰 탈취 원천 차단
- **XSS 방어**: 
  1) JavaScript의 쿠키 접근을 차단하는 HttpOnly 속성 
  2) 신뢰할 수 있는 도메인만 허용하는 CSP 정책
- **BCrypt 해싱**: 
  1) 자동 Salt 생성 + 여러 번 해싱 반복 
  2) 동일 비밀번호도 매번 다른 해시 생성

### **2. API 설계 및 안정성 확보**

| **API 문제점** | **발생 상황** | **해결 방안** | **구현 기술** |
|-------------|-------------|-------------|-------------|
| **데이터 검증 부재** | 클라이언트 유효하지 않은 데이터 전송 | @Valid 어노테이션 선제적 검증 | `BoardResourceController.java`, `RecCharterServiceRequestVO.java` |
| **도메인 검증 중복** | 지역구 검증 로직이 여러 서비스에 산재 | @RegionValid 커스텀 어노테이션 구현 | `RegionValid.java`, `RegionValidator.java` |
| **NPE 발생 위험** | null 반환 메서드의 예외 위험성 | Optional 패턴 일관 적용 | `BoardService.java`, `MemberService.java` |
| **API 의도 불명확** | PathVariable과 RequestParam 혼용 | RESTful 원칙 기반 명확한 구분 | `BoardResourceController.java`, `BoardPageController.java` |

**구체적 구현 과정:**
- **방어적 API 설계**: 
  1) 컨트롤러에서 `@Valid` 적용 
  2) 서버 로직 실행 전 데이터 유효성 검증 
  3) `MethodArgumentNotValidException` 발생 시 중앙 예외 핸들러에서 400 Bad Request + 구체적 오류 메시지 반환
- **커스텀 검증**: 
  1) 서울시 25개 구 + "미선택" 옵션을 `Set<String>`으로 관리하는 지역 검증기 구현 
  2) DTO 필드에 커스텀 어노테이션 한 줄 추가만으로 도메인 검증 완료
- **Null Safety**: 
  1) JPA Repository 반환 타입을 `Optional<T>`로 지정 
  2) 서비스에서 `.orElseThrow()` 패턴 일관 사용 
  3) `if(result == null)` 같은 조건문 제거하고 명시적 비즈니스 예외로 전환

### **3. 예외 처리 및 흐름 제어**

| **예외 처리 문제** | **기존 방식의 한계** | **개선된 해결책** | **구현 기술** |
|-----------------|-------------------|------------------|-------------|
| **예외 로직 분산** | 비즈니스 로직에 try-catch 블록 산재 | 중앙 집중식 예외 핸들러 도입 | `@RestControllerAdvice`, `@ControllerAdvice` |
| **인증/인가 혼재** | 401/403 동일 처리로 사용자 혼란 | Spring Security 예외 처리 책임 분리 | `AuthenticationEntryPoint`, `AccessDeniedHandler` |
| **JWT 클레임 동기화** | 사용자 정보 수정 후 UI 미반영 | 토큰 재발급 및 강제 만료 전략 | `MemberService.java` |

**상세 해결 과정:**
- **중앙 집중식 처리**: 
  1) `@RestControllerAdvice`로 모든 예외를 한 곳에서 처리 
  2) 비즈니스 로직은 핵심 기능에만 집중 
  3) 일관된 오류 응답 체계 구축
- **예외 타입별 분리**: 
  1) 인증되지 않은 사용자 접근 시 `AuthenticationEntryPoint`(401) 동작 
  2) 인증되었으나 권한 없는 접근 시 `AccessDeniedHandler`(403) 동작 
  3) 시나리오별 명확한 상태 코드와 메시지 제공
- **실시간 동기화**: 
  1) 닉네임 수정 시 Redis에서 기존 토큰 즉시 삭제로 무효화 
  2) 변경 정보로 새 토큰 재발급 
  3) 재로그인 없이 즉시 동기화된 정보 확인 가능

### **4. 데이터베이스 최적화**

| **DB 문제점** | **발생 원인** | **최적화 방안** | **구현 기술** |
|-------------|-------------|-------------|-------------|
| **데이터 정합성 위험** | 다중 테이블 업데이트 중 일부 실패 | @Transactional 원자적 처리 | `UserEntityDetailService.java` |
| **페이징 성능 저하** | JPQL 변환 과정의 오버헤드 | Oracle 최적화 네이티브 쿼리 직접 작성 | `@Query(nativeQuery = true)` |

**구체적 최적화 과정:**
- **트랜잭션 보장**: 
  1) 회원가입 시 여러 테이블 동시 저장 작업을 `@Transactional`로 묶어 원자적 단위 처리 
  2) 중간 오류 발생 시 모든 작업 롤백으로 데이터 정합성 보장
- **DB별 최적화**: 
  1) Oracle의 `OFFSET-FETCH` 페이징 구문을 네이티브 쿼리로 직접 작성 
  2) JPQL 변환 과정 생략하고 가장 효율적인 DB 페이징 구현

---

## 📈 성능 최적화 결과

| **최적화 영역** | **개선 전 (DB 직접 조회)** | **개선 후 (Redis 기반)** | **핵심 기술** |
|-------------|------------|------------|-----------|
| **추천 로직 응답 속도** | 복잡한 Join 및 Full Scan으로 지연 발생 | 메모리 기반 인덱스 검색으로 빠른 응답 | Redis Sorted Set, Hash, Pipelining |
| **DB 부하** | 모든 사용자 요청 시마다 DB 조회 발생 | DB 조회 없음 (배치 시점에만 1회 접근) | Batch Scheduler, Cache-Aside Pattern |
| **검색 확장성** | 데이터 증가 시 성능 저하 우려 | 인덱스 구조로 안정적인 검색 성능 유지 | Redis Sorted Set 인덱싱 |

---

## 📁 프로젝트 구조

<pre>
WhereHouse
├── board
│   ├── controller / dao / model / service
├── information
│   ├── controller / dao / model / service
├── mainpage
│   └── controller
├── members
│   ├── controller / dao / model / service
└── recommand
    ├── controller / dao / model / service
</pre>

---

## 📄 페이지별 주요 기능

### **[메인 페이지]**
- 로그인/회원가입 진입점 역할을 수행하며, 전체 서비스의 라우팅 중심 역할을 합니다.
- 상단 네비게이션 바를 통해 게시판, 추천 서비스, 마이페이지 등으로 접근이 가능하며, 로그인 상태에 따라 사용자 맞춤 UI가 표시됩니다.

![메인 페이지](https://github.com/user-attachments/assets/8e2c3413-97a5-4380-884b-32c4bce70275)

### **[거주지 추천 페이지]**
사용자의 복합적인 주거 니즈를 해결하기 위한 프로젝트의 핵심 페이지입니다. 전세와 월세 유형에 따라 완전히 분리된 UI와 API 로직을 통해 최적화된 추천 경험을 제공합니다.

[ 다차원적 조건 설정 ] 사용자는 단순한 예산을 넘어 다음과 같은 구체적인 조건을 설정할 수 있습니다.

- 임대 유형: 전세/월세 선택에 따라 입력 UI가 동적으로 변경됩니다.
- 예산 및 평수: 보증금, 월세, 평수의 최소-최대 범위를 지정합니다.
- 가치관 반영: 가격(PRICE), 안전(SAFETY), 공간(SPACE) 세 가지 가치에 대해 1, 2, 3순위 가중치를 직접 부여하여 개인의 선호도를 추천에 반영합니다.
- 유연성 제어: 매물이 부족할 경우를 대비해 예산 초과 허용 범위, 최소 안전 점수, 절대 최소 평수 등 폴백(Fallback) 검색의 기준을 직접 제어할 수 있습니다.


[ 추천 결과 ] 입력된 조건을 바탕으로 다단계 폴백 알고리즘이 작동하여 Top 3 행정구를 추천합니다.

- 조건 완화 알림: 만약 초기 조건에 맞는 매물이 부족해 **조건 완화 검색(Expanded Search)**이 수행된 경우, 어떤 조건이 어떻게 완화되었는지 사용자에게 명확히 알려줍니다.
- 대표 점수 기반 순위: 각 지역구는 '매물의 평균적인 질(점수)'과 '선택의 폭(매물 수)'을 함께 고려한 대표 점수를 기준으로 공정하게 순위가 결정됩니다.

[ 인터랙티브 결과 탐색 ] 추천 결과는 정적인 목록이 아닌, 사용자가 깊이 있게 탐색할 수 있도록 동적인 UI로 제공됩니다.
- 상세 점수 모달: '지역구 추천 정보' 버튼 클릭 시, 해당 지역구의 가격, 공간, 안전 점수가 어떻게 산출되었는지 상세 분석 패널을 모달창으로 확인할 수 있습니다.
- 상세 매물 모달: '상세 매물들 보기' 버튼 클릭 시, 해당 지역구의 상위 매물 목록을 점수와 함께 모달창에서 즉시 확인할 수 있습니다.
- 지도 시각화 연동: 추천된 Top 3 지역구의 경계가 Kakao Map API와 GeoJSON 데이터를 통해 지도 위에 시각적으로 표시되어 직관적인 위치 파악을 돕습니다.

![추천 페이지](https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37)

### **[상세 지도 페이지]**
- 지도 위 특정 지역 클릭 시, 반경 500m 이내 CCTV 위치 및 생활 편의시설(약국, 편의점 등)을 시각적으로 표시합니다.
- KakaoMap API를 기반으로 동작하며, 지역에 따라 마커 데이터가 동적으로 갱신됩니다.
- 마커 클릭 시 관련 정보(시설명, 유형 등)를 확인할 수 있습니다.
- 사용자 경험을 고려해 각 요소는 레이어 방식으로 표시됩니다.
- **추천 결과 연동**: 추천된 Top 3 지역구의 경계를 Kakao Map API와 GeoJSON 데이터를 연동하여 지도 위에 시각적으로 표시합니다.

![상세지도 페이지](https://github.com/user-attachments/assets/ba07bf7d-f11b-4355-b81d-42c6b8ad9376)

---

## ⚙ 개발 환경 및 기술 스택

![Java](https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=spring)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis)
![Oracle](https://img.shields.io/badge/Oracle-F80000?style=for-the-badge&logo=oracle)
![Jenkins](https://img.shields.io/badge/Jenkins-D24939?style=for-the-badge&logo=jenkins)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens)
![AWS EC2](https://img.shields.io/badge/AWS%20EC2-232F3E?style=for-the-badge&logo=amazonaws)

---

## 📚 상세 문서

### 프로젝트 개선 계획서
- [1. 프로젝트 기획서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B8%B0%ED%9A%8D%EC%84%9C.md) - 2단계 폴백 하이브리드 추천 시스템 기획
- [2. 부동산 추천 로직 2단계 폴백 시스템 설계 근거](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/2.%20%EB%B6%80%EB%8F%99%EC%82%B0%20%EC%B6%94%EC%B2%9C%20%EB%A1%9C%EC%A7%81%202%EB%8B%A8%EA%B3%84%20%ED%8F%B4%EB%B0%B1%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20%EC%84%A4%EA%B3%84%20%EA%B7%BC%EA%B1%B0.md) - 알고리즘 설계 배경 및 의사결정 과정
- [3. 부동산 추천 시스템 - 매물 정보 데이터 연동 명세서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/3.%20%EB%B6%80%EB%8F%99%EC%82%B0%20%EC%B6%94%EC%B2%9C%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20-%20%EB%A7%A4%EB%AC%BC%20%EC%A0%95%EB%B3%B4%20%EB%8D%B0%EC%9D%B4%ED%84%B0%20%EC%97%B0%EB%8F%99%20%EB%AA%85%EC%84%B8%EC%84%9C.md) - 국토교통부 API 연동 상세
- [4. 부동산 추천 시스템 - 안전성 점수 데이터 연동 명세서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/4.%20%EB%B6%80%EB%8F%99%EC%82%B0%20%EC%B6%94%EC%B2%9C%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20-%20%EC%95%88%EC%A0%84%EC%84%B1%20%EC%A0%90%EC%88%98%20%EB%8D%B0%EC%9D%B4%ED%84%B0%20%EC%97%B0%EB%8F%99%20%EB%AA%85%EC%84%B8%EC%84%9C.md) - 회귀 분석 기반 안전성 지표 산출
- [5. 부동산 추천 시스템 안전성 점수 설계서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/5.%20%EB%B6%80%EB%8F%99%EC%82%B0%20%EC%B6%94%EC%B2%9C%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20%EC%95%88%EC%A0%84%EC%84%B1%20%EC%A0%90%EC%88%98%20%EC%84%A4%EA%B3%84%EC%84%9C.md) - 범죄율 정규화 및 점수 계산 로직
- [6. 안전성 점수 산출을 위한 데이터 분석 프로세스 명세서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/1.%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B8%B0%ED%9A%8D%20%EB%B0%8F%20%EC%84%A4%EA%B3%84%EC%84%9C/6.%20%EC%95%88%EC%A0%84%EC%84%B1%20%EC%A0%90%EC%88%98%20%EC%82%B0%EC%B6%9C%EC%9D%84%20%EC%9C%84%ED%95%9C%20%EB%8D%B0%EC%9D%B4%ED%84%B0%20%EB%B6%84%EC%84%9D%20%ED%94%84%EB%A1%9C%EC%84%B8%EC%8A%A4%20%EB%AA%85%EC%84%B8%EC%84%9C.md) - 통계 분석 방법론

### 실제 비즈니스 로직 설계
- [부동산 추천 시스템 비즈니스 로직 설계 명세서](https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/5.%20%EC%8B%A4%EC%A0%9C%20%EB%B9%84%EC%A6%88%EB%8B%88%EC%8A%A4%20%EB%A1%9C%EC%A7%81%20%EC%84%A4%EA%B3%84%EC%84%9C) - 배치 처리 및 실시간 추천 서비스 전체 구현

### UI 설계 명세서
- [WhereHouse 부동산 추천 시스템 UI 설계 명세서](https://github.com/bumjinDev/wherehouse_SpringBoot/blob/master/docs/9.%20%EC%A3%BC%EA%B1%B0%EC%A7%80%20%EC%84%9C%EB%B9%84%EC%8A%A4(recommand)%20%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B0%9C%EC%84%A0%20%EA%B3%84%ED%9A%8D%EC%84%9C/6.%20UI%20%EC%84%A4%EA%B3%84%20%EB%AA%85%EC%84%B8%EC%84%9C/WhereHouse%20%EB%B6%80%EB%8F%99%EC%82%B0%20%EC%B6%94%EC%B2%9C%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20UI%20%EC%84%A4%EA%B3%84%20%EB%AA%85%EC%84%B8%EC%84%9C.md) - 사용자 인터페이스 상세 설계

---