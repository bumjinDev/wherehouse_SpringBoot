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

- **배포 URL**: [wherehouse](https://wherehouse.it.kr/wherehouse/)

---

## 🧩 WhereHouse Architecture

![Architecture](https://github.com/user-attachments/assets/3f642b20-0713-453b-8be0-b3a10f56a950)

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
- **한준원**: 주거지 추천 알고리즘 구현
- **이재서**: 상세지도 서비스 Servlet 전환
- **정범진**: 로그인/회원가입 및 게시판 기능 구현

### **3차 프로젝트: Spring Boot 전환 및 인프라 구성**
- **정범진**: 전체 백엔드 구조(Spring Boot), 인증 시스템, 배포 자동화 담당
- **이재서**: 상세지도 기능 Spring Boot 전환

---

## 💻 주요 기술 구현 내용

### **1. JWT를 활용한 Stateless 인증 시스템**

> 서버의 확장성을 확보하고 클라이언트와의 결합도를 낮추기 위해, 순수 JWT 기반의 Stateless 인증 시스템을 구축했습니다.

-   **인증 흐름 및 구현**:
    -   **토큰 발급 및 검증**: 로그인 성공 시, 서버의 비밀 키로 서명된 **HMAC-SHA256 JWT**를 생성합니다. 모든 API 요청 시에는 이 서명을 검증하여 데이터의 무결성을 보장하며, 검증 완료 후 `SecurityContextHolder`에 사용자 정보를 등록하여 인가에 활용합니다.
    -   **안전한 토큰 전송**: 생성된 토큰은 XSS 공격으로부터 보호하기 위해 **`HttpOnly`** 속성이 부여된 쿠키를 통해 안전하게 클라이언트에 전달됩니다.
    -   **Stateless 로그아웃**: 로그아웃은 서버에 별도의 상태를 저장하는 대신, 클라이언트 측의 JWT 쿠키를 즉시 만료시키는 방식으로 구현하여 Stateless 아키텍처의 원칙을 유지합니다.

-   **관련 소스**: `LoginFilter.java`, `JwtAuthProcessorFilter.java`, `JWTUtil.java`, `CookieLogoutHandler.java`

---

### **2. Spring Security FilterChain 모듈화**

> 단일 필터 체인으로 모든 경로의 보안을 관리할 때 발생하는 복잡성을 해결하기 위해, URL 패턴별로 보안 책임을 분리하는 모듈화된 구조를 도입하여 유지보수성과 유연성을 확보했습니다.

-   **핵심 구현**:
    -   **FilterChain 분리**: `/login`, `/members/**`, `/boards/**` 등 주요 엔드포인트 별로 독립된 `SecurityFilterChain`을 `@Bean`으로 등록했습니다. `securityMatcher`를 통해 각 필터가 담당할 URL 범위를 명확히 지정하여 서비스별 최적화된 보안 정책을 적용했습니다.
    -   **인증/인가 예외 처리 분리**: 시나리오별 명확한 피드백을 위해 예외 처리 책임을 분리했습니다.
        -   **인증 실패 (401)**: `AuthenticationEntryPoint`를 커스텀하여 유효하지 않은 접근 시 쿠키 삭제 및 안내 메시지를 반환합니다.
        -   **인가 실패 (403)**: `AccessDeniedHandler`를 커스텀하여 권한이 없는 리소스 접근 시 명확한 권한 부족 메시지를 전달합니다.

-   **관련 소스**: `SecurityConfig.java`, `JwtAuthenticationFailureHandler.java`, `JwtAccessDeniedHandler.java`

---

### **3. Redis 이원화 캐시 전략**

> 반복적인 DB 조회로 인한 응답 지연을 해결하기 위해 **Cache-Aside 패턴**을 도입했습니다. 특히, 데이터의 변경 빈도에 따라 캐시 유효기간(TTL)을 차등 적용하는 **이원화 전략**으로 캐시 효율과 데이터 정합성을 동시에 확보했습니다.

-   **구현 세부사항**:
    -   **Cache-Aside 패턴**: 서비스 로직은 DB 조회 전 항상 Redis를 먼저 확인합니다. Cache Hit 시 DB 접근 없이 즉시 데이터를 반환하고, Cache Miss 발생 시에만 DB에서 데이터를 조회하여 Redis에 저장한 후 반환합니다.
    -   **이원화 TTL 전략**:
        -   **전체 지도 데이터 (변경 빈도 낮음)**: **24시간**의 긴 TTL을 설정하여 캐시 효율 극대화.
        -   **사용자 선택 지역 데이터 (요청 기반)**: **1시간**의 짧은 TTL을 설정하여 데이터 최신성 확보.
-   **성과**: **평균 응답 속도 35% 단축** 및 **SQL 쿼리 호출 99% 감소**.

-   **관련 소스**: `MapDataService.java`

---

### **4. 계층형 아키텍처 설계**

> **관심사의 분리(SoC)** 원칙에 기반하여 각 계층의 독립성을 확보하고 유지보수성을 극대화했습니다. 특히 프레임워크 기술과 핵심 도메인 로직을 분리하여 시스템의 유연성을 높였습니다.

-   **세부 구현**:
    -   **DTO-Entity 분리**: 클라이언트 통신을 위한 `DTO`와 영속성을 위한 `Entity`를 명확히 분리하고, 전용 `Converter`를 통해 계층 간 데이터 변환을 담당했습니다. 이를 통해 DB 스키마 변경이 프레젠테ATION 계층에 미치는 영향을 차단했습니다.
    -   **API-View 컨트롤러 분리**: JSON 응답을 위한 `@RestController`와 JSP 페이지 렌더링을 위한 `@Controller`의 역할을 명확히 구분하여 설계했습니다.
    -   **도메인-보안 모델 분리**: 핵심 비즈니스 로직을 담는 도메인 엔티티(`MembersEntity`)와 Spring Security 인증을 위한 보안 엔티티(`AuthenticationEntity`)를 분리하여 프레임워크 종속성을 최소화했습니다.

-   **관련 소스**: `BoardConverter.java`, `MemberConverter.java`, `MembersEntity.java`, `AuthenticationEntity.java`, `UserEntityDetails.java`

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

| **최적화 영역** | **개선 전** | **개선 후** | **개선율** | **핵심 기술** |
|-------------|------------|------------|-----------|-------------|
| **평균 응답 속도** | 기준값 | 단축됨 | **35% ↓** | Redis 이원화 캐시 |
| **SQL 쿼리 호출** | 매번 DB 조회 | 캐시 우선 조회 | **99% ↓** | Cache-Aside 패턴 |
| **캐시 적중률** | 0% (캐시 없음) | 높은 적중률 | **대폭 향상** | TTL 차등 전략 |

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
- 사용자로부터 안전성/편의성 선호 비중 및 전세/월세 금액 조건을 입력받습니다.
- 입력값을 기반으로 전략 패턴 기반 추천 서비스가 호출되며, 행정구 단위 Top 3 지역이 추천됩니다.
- 추천 결과는 지역 이름, 평균 시세, 안전/편의 점수를 포함하여 표시됩니다.
- KakaoMap을 기반으로 각 추천 지역 위치를 시각적으로 함께 제공합니다.

![추천 페이지](https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37)

### **[상세 지도 페이지]**
- 지도 위 특정 지역 클릭 시, 반경 500m 이내 CCTV 위치 및 생활 편의시설(약국, 편의점 등)을 시각적으로 표시합니다.
- KakaoMap API를 기반으로 동작하며, 지역에 따라 마커 데이터가 동적으로 갱신됩니다.
- 마커 클릭 시 관련 정보(시설명, 유형 등)를 확인할 수 있습니다.
- 사용자 경험을 고려해 각 요소는 레이어 방식으로 표시됩니다.

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




