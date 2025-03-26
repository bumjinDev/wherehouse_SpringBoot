# WHEREHOUSE: 서울시 1인가구 주거지 추천 서비스

![Wherehouse Banner](https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8)

---

## 📌 프로젝트 개요

서울시 1인 가구, 특히 MZ 세대의 주거 선택을 지원하기 위해 전세/월세 가격 조건과 안전성, 편의성 선호도를 반영한 **행정구 단위 추천 시스템**입니다.  
사용자 입력 기반 조건을 분석하여 최적 거주지를 추천하며, 성능 최적화와 보안 설계를 전면에 배치한 아키텍처를 구성하였습니다.

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

## 🔥 주요 기능 및 구현 기여

### 1. JWT 기반 인증 필터 체인 및 보안 구조 설계
- 커스텀 인증 필터 (`LoginFilter`, `JwtAuthenticationFilter`, `RequestAuthenticationFilter`) 직접 구현
- HttpOnly 쿠키 + Redis 서명 키 → 무상태 보안 인증 설계
- `AuthenticationEntryPoint`, `AccessDeniedHandler`로 인증/인가 예외 분리 처리
- CSP, X-Frame-Options 등 보안 응답 헤더 구성

### 2. 사용자 정보 변경 시 JWT 클레임 동기화
- 닉네임 변경 등의 사용자 정보 수정 시 JWT 클레임 내 반영
- Redis 기존 키 삭제 후 토큰 재생성 → 쿠키 재설정

### 3. Redis 캐싱 설계 및 직렬화 안정화
- 지도 데이터 TTL 분리 저장 (전체 24h / 지역별 1h)

### 4. Jenkins 기반 배포 자동화
- GitHub Webhook → Jenkins Build → EC2 배포
- Docker 기반 Oracle DB 운영환경 구성


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

## 🧭 앞으로의 계획

### 1. 코드 리팩토링
- Controller-Service-DAO 분리 강화, 중복 제거, 전역 예외 처리 체계 구성

### 2. 단위/통합 테스트 추가
- JUnit, Mockito 기반 테스트 코드 작성

### 3. CI 연동 테스트 자동화 구성
- Redis 성능 분석 및 고가용성 구성

### 4. Redis Sentinel 도입, SLOWLOG 분석을 통한 병목 해소

### 5. 프론트엔드 React 전환
- 컴포넌트 기반 상태관리 도입, SPA 구조로 재편 예정

### 6. 비동기 처리 및 멀티프로세싱 도입
- 게시판 댓글 작성, CCTV 데이터 마커 처리 등 비핵심 I/O 작업에 대해 @Async 비동기 처리 적용 예정
- Redis TTL 갱신, JWT 재발급 처리에 대해 비동기 큐 기반 후처리 로직 도입 고려
- 향후 Kafka 또는 Spring Event 기반 메시지 큐 연동을 통해 비동기 확장성 확보
- Jenkins 빌드 병렬화 및 배포 후후처리 스크립트에 대해 Bash/ProcessBuilder 기반 멀티프로세스 리팩토링 계획

---
