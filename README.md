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

- **배포 URL**: [https://wherehouse.servehttp.com/wherehouse/](https://wherehouse.servehttp.com/wherehouse/)

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

## 🔥 주요 역할 및 핵심 성과

### 1. JWT와 Redis를 활용한 상태 관리형 인증 시스템
**Stateless한 JWT의 장점을 살리면서도, Redis를 통해 서버 측에서 세션을 통제할 수 있는 인증 시스템을 구현했습니다.**

- **로그인 및 토큰 발급 프로세스**
  - 로그인 요청 시 `LoginFilter`는 **JSON** 또는 **Form** 형식의 요청을 모두 처리합니다.
  - `UserAuthenticationProvider`에서 ID/PW 검증 성공 시, `JWTUtil`을 통해 **HMAC-SHA256 서명 키**와 **JWT**를 각각 생성합니다.
  - 생성된 **JWT 자체를 Key**로, **Base64로 인코딩된 서명 키를 Value**로 하여 Redis에 저장합니다.
  - 발급된 JWT는 **HttpOnly, Secure** 속성이 부여된 쿠키로 클라이언트에 전달하여 XSS 공격으로부터 토큰을 보호합니다.

- **모든 요청에 대한 JWT 검증**
  - `JwtAuthProcessorFilter`는 클라이언트로부터 전달된 쿠키에서 JWT를 추출합니다.
  - 추출된 JWT를 Key로 사용하여 **Redis에 해당 서명 키가 존재하는지 확인**합니다. (존재하지 않을 시, 만료되었거나 강제 로그아웃된 토큰으로 간주)
  - 조회한 서명 키로 JWT의 유효성을 최종 검증한 후, 사용자 정보를 `SecurityContextHolder`에 등록하여 요청에 대한 인증을 완료합니다.

- **사용자 정보 변경 시 JWT 클레임 동기화**
  - `MemberService`에서 닉네임 등 회원 정보가 수정되면, `JWTUtil.modifyClaim`을 호출하여 **변경된 정보가 담긴 새로운 JWT를 생성**합니다.
  - Redis에서 **기존 토큰(Key)과 서명 키(Value)를 삭제**하고, **새로운 토큰과 기존 서명 키를 다시 저장**하여 토큰 정보를 갱신합니다.
  - 이 과정을 통해 사용자가 재로그인하지 않아도 변경된 정보가 즉시 반영되도록 구현했습니다.

### 2. Spring Security FilterChain 모듈화 및 예외 처리
**URL 패턴별로 `SecurityFilterChain`을 분리하여, 각기 다른 보안 정책을 독립적으로 적용할 수 있도록 설계했습니다.**

- **FilterChain 분리 구성**
  - `SecurityConfig`에서 `@Bean`으로 등록된 각 `SecurityFilterChain`은 `securityMatcher`를 통해 `/login`, `/logout`, `/members/**`, `/boards/**` 등 특정 경로의 요청만 처리합니다.
  - 이를 통해 **필터의 적용 범위를 최소화**하고, 각 서비스의 보안 요구사항에 맞는 커스텀 필터와 핸들러를 명확하게 설정했습니다.

- **인증/인가 실패 시나리오별 핸들러 적용**
  - **인증 실패(401):** 인증되지 않은 사용자가 보호된 리소스에 접근할 경우, `JwtAuthenticationFailureHandler`가 동작하여 401 응답과 함께 쿠키를 삭제하고 안내 메시지를 반환합니다.
  - **인가 실패(403):** 인증은 되었으나 해당 리소스에 접근 권한이 없을 경우, `JwtAccessDeniedHandler`가 동작하여 403 응답과 함께 권한 부족 메시지를 반환합니다.

### 3. Redis를 활용한 이원화 캐시 전략을 통한 성능 최적화
**데이터의 성격과 변경 주기를 고려하여 TTL(Time-To-Live)을 차등 적용하는 캐시 전략으로 성능을 최적화했습니다.**

- **Cache-Aside 패턴 적용**
  - `MapDataService`에서 모든 지도 데이터 조회 요청 시, 먼저 Redis 캐시를 확인합니다.
  - 캐시 미스(Cache Miss)가 발생한 경우에만 DB에서 데이터를 조회하고, 그 결과를 Redis에 저장한 뒤 클라이언트에 반환합니다.

- **데이터 성격에 따른 TTL 차등 적용**
  - 변경 빈도가 매우 낮은 **전체 지도 데이터**는 **24시간의 긴 TTL**을 적용하여 불필요한 DB 조회를 최소화했습니다.
  - 사용자의 선택에 따라 동적으로 조회되는 **특정 지역구 데이터**는 **1시간의 짧은 TTL**을 적용하여 데이터의 최신성과 캐시 효율 사이의 균형을 맞췄습니다.

- **[정량적 성과]** 캐싱 적용 후, 지도 데이터 조회 API의 **평균 응답 속도를 35% 단축**하고 관련 **SQL 쿼리 호출 수를 99% 감소**시켰습니다.

### 4. 계층형 설계 및 역할 분리를 통한 유지보수성 강화
**관심사의 분리(Separation of Concerns) 원칙에 따라 각 계층의 역할을 명확히 하여 코드의 유지보수성을 높였습니다.**

- **API와 View 컨트롤러 분리**
  - JSON 형식의 데이터를 반환하는 API는 `@RestController`로, JSP 페이지를 렌더링하는 요청은 `@Controller`로 구현하여 역할을 명확히 분리했습니다.

- **DTO-Entity 계층 분리**
  - `BoardConverter`, `MemberConverter` 등 전용 Converter 클래스를 두어 **서비스 계층과 영속성 계층 간의 데이터 변환**을 담당하게 했습니다.
  - 이를 통해 DB 테이블 구조에 종속적인 Entity 객체가 프레젠테이션 계층으로 노출되는 것을 방지하고, 각 계층의 독립성을 확보했습니다.

- **예외 처리 계층 분리**
  - `@RestControllerAdvice`를 활용해 **도메인별 예외 처리 핸들러**를 구현하여, 비즈니스 로직과 예외 처리 코드를 분리하고 일관된 오류 응답 체계를 구축했습니다.

### 5. Jenkins와 Docker 기반의 CI/CD 파이프라인 구축
- GitHub Webhook과 Jenkins를 연동하여, **소스 코드 Push 시 EC2 서버로 자동으로 빌드 및 배포**되는 CI/CD 파이프라인을 구축했습니다.
- Docker를 이용해 Oracle DB 환경을 컨테이너화하여 개발 및 배포 환경의 일관성을 확보했습니다.

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
- 상단 네비게이션 바를 통해 게시판, 추천 서비스, 마이페이지 등으로 접근이 가능하며,
  로그인 상태에 따라 사용자 맞춤 UI가 표시됩니다.

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

### **[게시판]**
- 인증된 사용자만 접근 가능한 게시판 기능으로, 게시글 등록, 수정, 삭제, 조회 기능을 지원합니다.
- 게시글 상세 페이지에서 댓글 기능을 통해 사용자 간 상호작용이 가능합니다.
- 게시자 정보는 JWT에서 추출된 userId를 기준으로 자동 저장됩니다.
- 삭제/수정 시 작성자 검증 로직이 포함되어 있습니다.

### **[회원 관리]**
- 회원가입, 로그인, 정보 수정 기능 제공
- 로그인 시 JWT가 쿠키로 발급되며, Redis에 서명 키가 저장됩니다.
- 정보 수정(예: 닉네임 변경) 시 기존 토큰은 폐기되며, 새로운 JWT가 재발급됩니다.
- JWT 기반으로 사용자 인증 상태를 유지하면서도 동기화를 보장하는 구조입니다.

---

## 🧠 문제 해결 사례

| 문제 | 원인 | 해결 방안 |
|------|------|-------------|
| 인증/인가 예외 처리 충돌 | 401/403 구분 실패 | `AuthenticationEntryPoint` + `AccessDeniedHandler` 분리 적용 |
| JWT 클레임 동기화 실패 | 사용자 정보 수정 후 미반영 | `modifyClaim` + Redis 삭제 후 재등록 + 쿠키 재설정 |
| 인증 필터 미작동 | 필터 순서/경로 누락 | `addFilterAt()` + 경로별 SecurityFilterChain 분리 |

---

## 🔐 보안 설계

- `Content-Security-Policy`: 외부 스크립트, iframe 차단
- `X-Content-Type-Options: nosniff`: MIME 스니핑 방지
- `X-Frame-Options: DENY`: iframe 삽입 차단
- `HttpOnly`, `Secure`, `SameSite=Strict`: 쿠키 설정

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
