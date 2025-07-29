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

## 💻 기술 구현 내용

### **핵심 기술 스택**
| **분야** | **기술** | **구현 내용** |
|---------|---------|-------------|
| **Framework** | Spring Boot 3.x, Spring Security 6.x | RESTful API, JWT 인증, SecurityFilterChain 모듈화 |
| **Database** | Oracle DB, Spring Data JPA, JdbcTemplate | 전략적 혼용, 네이티브 쿼리 최적화 |
| **Cache** | Redis | Cache-Aside 패턴, 이원화 TTL 전략 (24h/1h) |
| **Authentication** | JWT, HttpOnly Cookie | Stateless 인증, Redis 서명키 관리 |
| **DevOps** | Jenkins, Docker, AWS EC2 | GitHub Webhook 연동, 자동 빌드/배포 |

### **주요 구현 특징**

| **기능** | **구현 방식** | **핵심 기술** |
|---------|-------------|-------------|
| **인증 시스템** | JWT + Redis 하이브리드 | `JwtAuthProcessorFilter`, `LoginFilter` |
| **예외 처리** | 중앙 집중식 핸들러 | `@RestControllerAdvice`, `@ControllerAdvice` |
| **데이터 검증** | 커스텀 어노테이션 | `@RegionValid`, `@Valid` |
| **캐시 전략** | 데이터별 차등 TTL | 전체(24h) vs 지역별(1h) |
| **보안 정책** | 다층 보안 아키텍처 | CSP, XSS 방어, CSRF 차단 |
| **API 설계** | RESTful 원칙 준수 | `@PathVariable` vs `@RequestParam` 구분 |

### **성능 최적화 결과**
| **항목** | **개선 전** | **개선 후** | **개선율** |
|---------|------------|------------|-----------|
| **평균 응답 속도** | 기준값 | 단축됨 | **35% ↓** |
| **SQL 쿼리 호출** | 기준값 | 감소함 | **99% ↓** |
| **캐시 적중률** | 0% | 높음 | **대폭 향상** |

---

## 🔧 문제 해결 방안

### **1. 보안 강화**
| **문제** | **해결책** | **구현 기술** |
|---------|----------|-------------|
| JWT 위변조 위험 | HMAC-SHA256 서명 적용 | `JWTUtil.java` |
| XSS 공격 취약점 | HttpOnly 쿠키 + CSP 설정 | `SecurityConfig.java` |
| 세션 무상태 관리 | Redis 서명키 저장소 | `RedisHandler` |

### **2. 데이터 정합성**
| **문제** | **해결책** | **구현 기술** |
|---------|----------|-------------|
| 다중 테이블 동시 업데이트 | `@Transactional` 적용 | `UserEntityDetailService.java` |
| NULL 참조 예외 | `Optional` 패턴 도입 | `BoardService.java` |
| 도메인 검증 중복 | 커스텀 어노테이션 | `@RegionValid` |

### **3. 성능 최적화**
| **문제** | **해결책** | **구현 기술** |
|---------|----------|-------------|
| 반복적 DB 조회 | Redis 캐싱 도입 | `MapDataService.java` |
| 캐시 효율성 저하 | 이원화 TTL 전략 | 24h(전체) / 1h(지역별) |
| 복잡한 동적 쿼리 | JPA + JdbcTemplate 혼용 | `RecServiceEmpRepository.java` |

### **4. 시스템 안정성**
| **문제** | **해결책** | **구현 기술** |
|---------|----------|-------------|
| 예외 처리 분산 | 중앙 집중식 핸들러 | `@RestControllerAdvice` |
| 인증/인가 혼재 | 401/403 명확 분리 | `AuthenticationEntryPoint` |
| JWT 클레임 동기화 | 토큰 재발급 전략 | `MemberService.java` |

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
