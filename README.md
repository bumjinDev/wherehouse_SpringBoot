# WHEREHOUSE: 서울시 1인가구 주거지 추천 서비스

![Wherehouse Banner](https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8)

---

## 📌 프로젝트 개요

서울시 1인 가구, 특히 MZ 세대의 주거 선택을 지원하기 위해 전세/월세 가격 조건과 안전성, 편의성 선호도를 반영한 **행정구 단위 추천 시스템**입니다. 
사용자 입력 기반 조건을 분석하여 최적 거주지를 추천하며, 성능 최적화와 보안 설계를 전면에 배치한 실무 수준 아키텍처를 구성하였습니다.

- **배포 URL**: [https://wherehouse.servehttp.com/wherehouse/](https://wherehouse.servehttp.com/wherehouse/)

---

## 👥 역할 분담

### **1차 프로젝트: UI 개발**
- **한준원**: 전체 UI 설계 및 제작, 주거지 추천/지역구 정보 페이지 개발
- **이재서**: 행정동별 정보 페이지 개발
- **정범진**: 상세지도 클릭 이벤트 기반 좌표 처리, CCTV 마커 표시 구현

### **2차 프로젝트: Servlet 기반 개발**
- **한준원**: 주거지 추천 알고리즘  구현
- **이재서**: 상세지도 서비스 Servlet 전환
- **정범진**: 로그인/회원가입 및 게시판 기능 구현

### **3차 프로젝트: Spring Boot 전환 및 인프라 구성**
- **정범진**: 전체 백엔드 구조(Spring Boot), 추천 알고리즘, 인증 시스템, 배포 자동화 담당
- **이재서**: 상세지도 기능 Spring Boot 전환

---

## 🧩 설계 아키텍처 요약

![Architecture](https://github.com/user-attachments/assets/3f642b20-0713-453b-8be0-b3a10f56a950)

- JWT 무상태 인증 구조 (HttpOnly 쿠키, Redis 서명 키)
- 전세/월세 조건 기반 추천 알고리즘 (ROWNUM + 동적 정렬)
- 지도 좌표 TTL 분리 캐싱 구조 (24시간/1시간)
- Spring Security 보안 정책 헤더 적용 (CSP, X-Content-Type-Options 등)
- Jenkins 기반 무중단 CI/CD (Webhook → Build → EC2 배포)

---

## 🔥 주요 기능 및 구현 기여

- 인증 필터 체인 구성: `LoginFilter`, `JwtAuthenticationFilter`, `RequestAuthenticationFilter`
- 사용자 정보 변경 시 JWT 클레임 재발급 + Redis 재갱신으로 동기화
- 전략 패턴 기반 추천 알고리즘 구현 (전세/월세 분기 처리)
- RedisTemplate 이원화 및 JSON 직렬화 적용

---

## 📄 페이지별 주요 기능

### **[메인 페이지]**
- 로그인/회원가입 진입점 제공, 네비게이션 구성

### **[거주지 추천 페이지]**
- 사용자 입력 기반 (안전/편의 점수 + 가격 필터) 추천 결과 제공 (Top 3)

### **[상세 지도 페이지]**
- 클릭 위치 기반 500m 내 CCTV 및 편의시설 시각화 (KakaoMap API 활용)

### **[게시판]**
- 게시글 CRUD, 댓글 작성/삭제, 작성자 인증 처리

### **[회원 관리]**
- 회원가입, 로그인, 정보 수정 시 JWT 클레임 수정/동기화

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

## 🧑‍💻 팀원 구성

| 정범진 | 이재서 |
|--------|--------|
| ![정범진](https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39) | ![이재서](https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b) |
| [@bumjinDev](https://github.com/bumjinDev) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |

---

## 🧭 앞으로의 계획

1. **코드 리팩토링**
   - Controller-Service-DAO 분리 강화, 중복 제거, 전역 예외 처리 체계 구성

2. **단위/통합 테스트 추가**
   - JUnit, Mockito 기반 테스트 코드 작성
   - CI 연동 테스트 자동화 구성

3. **Redis 성능 분석 및 고가용성 구성**
   - Redis Sentinel 도입, SLOWLOG 분석을 통한 병목 해소

4. **프론트엔드 React 전환**
   - 컴포넌트 기반 상태관리 도입, SPA 구조로 재편 예정

---
