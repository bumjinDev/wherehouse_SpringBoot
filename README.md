# WHEREHOUSE: 서울시 1인가구 주거지 추천 서비스

![Wherehouse Banner](https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5f1f8)

## 📌 1. 프로젝트 소개

서울 내 1인 가구의 주거지 선택 문제를 해결하기 위해, 사용자의 **예산과 안전/편의 선호도**를 기반으로 최적의 행정구를 추천하는 서비스입니다.

백엔드 총괄 담당자로서 Spring Boot 기반의 전체 시스템을 주도적으로 설계하고 구현했습니다. 특히 **보안, 성능 최적화, 아키텍처 설계**에 중점을 두어 안정적이고 확장 가능한 시스템을 구축하는 경험을 쌓았습니다.

- **프로젝트 기간**: 2023.09 ~ 2025.03
- **참여 인원**: 3명
- **배포 URL**: 현재 임시 중단

---

## ✨ 2. 주요 기능

- **맞춤형 주거지 추천**: 예산(전/월세) 및 생활 패턴(안전/편의) 선호도에 따른 상위 3개 지역 추천
- **상세 지도 정보**: 선택 지역 반경 500m 내 CCTV, 편의점 등 주요 시설 위치 시각화
- **커뮤니티 게시판**: JWT 인증 기반의 안전한 게시판 및 댓글 기능
- **회원 관리**: 보안을 고려한 회원가입, 로그인, 정보 수정 기능

---

## 💻 3. 주요 기능 화면

| 메인 페이지 | 거주지 추천 | 상세 지도 |
| :---: | :---: | :---: |
| ![메인 페이지](https://github.com/user-attachments/assets/8e2c3413-97a5-4380-884b-32c4bce7ce75) | ![추천 페이지](https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37) | ![상세지도 페이지](https://github.com/user-attachments/assets/ba07bf7d-f11b-4355-b81d-42c6b8ad9376) |

---

## 🛠️ 4. 아키텍처 및 기술 스택

### Architecture

![Architecture](https://github.com/user-attachments/assets/3f642b20-0713-453b-8be0-b3a10f56a950)

### Tech Stack

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

## 👨‍💻 5. 저의 역할 및 기여 (Backend Lead)

**Spring Boot 기반의 전체 백엔드 시스템 설계 및 구현을 총괄했습니다.**

- **아키텍처 설계**: 유지보수성과 확장성을 고려한 **계층형 아키텍처(Layered Architecture)**를 설계하고, DTO-Entity 분리를 통해 계층 간 독립성을 확보했습니다.
- **인증/인가 시스템 구축**: **Spring Security와 JWT, Redis**를 결합하여 상태 관리형 인증 시스템을 설계했습니다. URL 패턴별 `SecurityFilterChain`을 모듈화하여 복잡도를 낮추고 보안 요구사항을 유연하게 적용했습니다.
- **성능 최적화**: **Redis 이원화 캐시 전략**을 도입하여 **평균 응답 속도를 35% 단축**하고, **DB 쿼리 호출을 99% 감소**시켰습니다.
- **API 설계 및 안정성 확보**: RESTful 원칙에 기반한 API를 설계하고, `@Valid`와 커스텀 어노테이션(`@RegionValid`)을 통한 서버 측 검증, `Optional` 패턴을 적용하여 Null-Safety를 확보했습니다.
- **보안 강화 (Security Hardening)**: HMAC 서명, HTTPS, `HttpOnly` 쿠키, CSP 헤더, BCrypt 해싱 등 다층적 보안 장치를 적용하여 JWT 탈취 및 위변조, XSS, CSRF 등의 공격을 방어했습니다.
- **CI/CD 파이프라인 구축**: Jenkins와 Docker를 활용하여 빌드 및 배포 자동화 환경을 구성했습니다.

---

## 🎯 6. 핵심 문제 해결 및 기술 구현 내용

### 가. 고성능 캐시 전략 설계 및 적용

- **문제점**: 지도 데이터 등 반복적인 DB 조회로 인한 응답 지연 발생.
- **해결 방안**: **Cache-Aside 패턴**을 적용하여 Redis 캐시를 우선 조회하고, Cache-Miss 발생 시에만 DB에 접근하도록 설계했습니다.
- **핵심 구현**:
    - **이원화 TTL 전략**: 변경 빈도가 낮은 전체 지도 데이터(24시간 TTL)와 사용자 요청 기반의 지역 데이터(1시간 TTL)를 분리하여 데이터 정합성과 캐시 효율을 동시에 확보했습니다. (`MapDataService.java`)
- **성과**: **평균 응답 속도 35% 단축**, **SQL 쿼리 호출 99% 감소**

### 나. Spring Security 기반의 모듈화된 인증/인가 시스템

- **문제점**: 단일 `SecurityFilterChain`으로 모든 URL을 관리하여 설정이 복잡하고, 경로별 다른 보안 정책 적용이 어려움.
- **해결 방안**: URL 패턴에 따라 독립적인 `SecurityFilterChain`을 `@Bean`으로 등록하여 역할을 분리하고, 각 서비스에 최적화된 보안 정책을 적용했습니다.
- **핵심 구현**:
    - **FilterChain 분리**: `/login`, `/logout`, `/members/**`, `/boards/**` 등 주요 경로별로 `securityMatcher`를 사용하여 독립적인 필터 체인을 구성했습니다. (`SecurityConfig.java`)
    - **인증/인가 예외 처리 분리**: 인증 실패(401) 시 `AuthenticationEntryPoint`, 인가 실패(403) 시 `AccessDeniedHandler`를 커스텀하여 시나리오별 명확한 응답을 제공하도록 구현했습니다. (`JwtAuthenticationFailureHandler.java`, `JwtAccessDeniedHandler.java`)

### 다. 다층적 보안 강화를 통한 안정성 확보

- **문제점**: JWT 탈취, XSS, CSRF, 무차별 대입 공격 등 다양한 보안 위협에 노출될 가능성.
- **해결 방안**: 각 위협 시나리오에 맞는 방어 메커니즘을 체계적으로 적용했습니다.
- **핵심 구현**:
    - **JWT 무결성 보장**: **HMAC-SHA256** 알고리즘으로 토큰을 서명하여 위변조를 방지했습니다. (`JWTUtil.java`)
    - **쿠키 탈취 방어**: **`HttpOnly`** 속성으로 JavaScript의 쿠키 접근을 차단하고, **CSP(콘텐츠 보안 정책)** 헤더를 설정하여 신뢰된 스크립트만 실행되도록 제한했습니다.
    - **비밀번호 보호**: **BCrypt**를 사용하여 Salt와 반복 해싱으로 안전하게 비밀번호를 저장했습니다. (`UserAuthenticationProvider.java`)

### 라. 유지보수성을 고려한 계층형 아키텍처 설계

- **문제점**: 각 계층의 책임이 불분명하고 결합도가 높아 DB 스키마 변경 등이 다른 계층에 영향을 미치는 구조.
- **해결 방안**: **관심사의 분리(SoC)** 원칙에 따라 각 계층을 독립적으로 설계하고, 프레임워크 의존성을 최소화했습니다.
- **핵심 구현**:
    - **DTO-Entity 분리**: 전용 `Converter` 클래스(`BoardConverter`, `MemberConverter`)를 두어 계층 간 데이터 변환을 명확히 하고, 영속성 계층의 변경이 프레젠테이션 계층으로 전파되는 것을 차단했습니다.
    - **도메인-보안 모델 분리**: 핵심 비즈니스 로직을 담는 도메인 엔티티(`MembersEntity`)와 Spring Security 인증을 위한 엔티티(`AuthenticationEntity`)를 분리하여 프레임워크 의존성을 낮췄습니다.

---

## 👥 7. 팀원 구성

| 정범진 | 이재서 |
|---|---|
| <img src="https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39" width="100" height="100"> | <img src="https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b" width="100" height="100"> |
| [@bumjinDev](https://github.com/bumjinDev) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |
