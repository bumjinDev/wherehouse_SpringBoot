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

## 🔐 보안 강화 (Security Hardening)

애플리케이션의 안정성과 사용자 정보 보호를 위해, 다양한 웹 취약점을 고려한 다층적인 보안 아키텍처를 설계하고 구현했습니다.

### 1.1. HMAC 기반 JWT 서명 적용 (토큰 위변조 방지)
JWT의 페이로드는 디코딩이 가능하여 누구나 내용을 볼 수 있으므로, 토큰의 **무결성 보장**이 매우 중요합니다. 이를 위해 **HMAC-SHA256** 알고리즘을 사용하여 JWT에 전자서명을 추가했습니다. 서버만이 알고 있는 비밀 키로 서명을 생성하므로, 제3자가 토큰 내용을 변경하면 서명 검증에 실패하게 되어 위변조된 토큰을 즉시 식별하고 차단할 수 있습니다.
- **관련 소스:** `JWTUtil.java`, `LoginFilter.java`

### 1.2. 전송 계층 암호화 (HTTPS 적용)
HTTP 통신은 평문으로 이루어지므로, 중간자 공격(MITM) 시 JWT가 그대로 노출될 수 있습니다. 이를 방지하기 위해 **HTTPS 프로토콜을 적용**하여 서버와 클라이언트 간의 모든 통신 구간을 암호화했습니다. 이를 통해 제3자가 네트워크 스니핑을 통해 토큰을 탈취하는 것을 원천적으로 차단합니다.

### 1.3. XSS 방어를 위한 HttpOnly 쿠키 사용
악성 스크립트가 브라우저에 주입되어 사용자의 쿠키를 탈취하는 XSS(Cross-Site Scripting) 공격을 방어하기 위해, JWT를 `HttpOnly` 속성이 부여된 쿠키에 저장하여 클라이언트에 전달했습니다. 이 속성은 JavaScript의 쿠키 접근을 차단하므로, XSS 공격으로부터 토큰을 안전하게 보호합니다.
- **관련 소스:** `LoginFilter.java`

### 1.4. CSRF 방어를 위한 SameSite 쿠키 속성
사용자가 자신의 의지와 무관하게 공격자가 의도한 요청을 보내도록 만드는 CSRF(Cross-Site Request Forgery) 공격을 방어하기 위해, 쿠키에 `SameSite` 속성을 설정했습니다. 이를 통해 외부 도메인에서 시작된 요청에는 인증 쿠키가 전송되지 않도록 브라우저 수준에서 제한하여, 다른 사이트에서 우리 서비스로 보내는 위조된 요청을 효과적으로 차단합니다.

### 1.5. 콘텐츠 보안 정책(CSP) 설정
신뢰할 수 없는 외부 스크립트나 리소스가 페이지에 로드되어 발생하는 보안 위협을 막기 위해 **콘텐츠 보안 정책(CSP)** 헤더를 적용했습니다. `script-src`, `style-src` 등을 통해 사전에 허용한 도메인의 리소스만 로드되도록 화이트리스트 기반으로 관리하여, 악의적인 외부 리소스 실행을 차단하고 XSS 등의 공격 경로를 줄여 보안을 한층 더 강화했습니다.
- **관련 소스:** `SecurityConfig.java`

### 1.6. 안전한 비밀번호 저장을 위한 BCrypt 해싱
데이터베이스가 유출되더라도 사용자의 비밀번호를 안전하게 보호하기 위해, 단방향 해시 함수인 **`BCryptPasswordEncoder`**를 사용했습니다. BCrypt는 내부적으로 Salt를 자동으로 생성하고 여러 번의 해싱을 반복하여, 동일한 비밀번호라도 매번 다른 해시 결과가 생성됩니다. 이를 통해 Rainbow Table 공격 등 무차별 대입 공격에 대한 방어력을 크게 높였습니다.
- **관련 소스:** `SecurityConfig.java`, `UserAuthenticationProvider.java`, `MemberService.java`

### 1.7. SecurityFilterChain의 모듈화
단일 필터 체인에 모든 보안 규칙을 설정하는 방식의 복잡성을 해결하기 위해, Spring Security의 권장 방식인 `securityMatcher`를 활용하여 URL 패턴별로 `SecurityFilterChain`을 분리했습니다. `/login`, `/members/**`, `/boards/**` 등 각기 다른 보안 요구사항을 가진 경로들을 독립적인 모듈로 구성하여, 설정의 가독성과 확장성을 크게 향상시켰습니다.
- **관련 소스:** `SecurityConfig.java`

---

## 🛠 API 설계 및 안정성 확보

잘못된 요청이나 예외적인 상황에서도 시스템이 안정적으로 동작할 수 있도록, 방어적이고 명확한 API를 설계했습니다.

### 2.1. 데이터 유효성 검증을 통한 방어적 API 설계 (@Valid)
클라이언트로부터 유효하지 않은 데이터(null, 형식 오류 등)가 유입될 경우, `NullPointerException`과 같은 서버 오류가 발생하거나 데이터베이스가 오염될 수 있습니다. 이를 방지하기 위해 컨트롤러 계층에서 DTO를 받을 때 `@Valid` 어노테이션을 적용하여, 서버 로직이 실행되기 전에 **데이터 유효성을 선제적으로 검증**했습니다. 검증에 실패하면 `MethodArgumentNotValidException`이 발생하며, 이는 중앙 예외 핸들러에서 **400 Bad Request**와 함께 어떤 필드가 잘못되었는지 명확한 오류 메시지를 클라이언트에 반환하도록 처리했습니다.
- **관련 소스:** `BoardResourceController.java`, `RecCharterServiceRequestVO.java`, `MemberAPIControllerExceptionHandler.java`

### 2.2. 커스텀 어노테이션을 이용한 도메인 특화 유효성 검증 (@RegionValid)
"서울시 지역구"와 같이 특정 도메인에만 사용되는 유효성 검증 로직이 여러 서비스에 흩어져 있으면 코드 중복과 유지보수성 저하를 유발합니다. 이 문제를 해결하기 위해 `@RegionValid`라는 커스텀 유효성 검증 어노테이션을 직접 구현했습니다. 이를 통해 복잡한 도메인 검증 로직을 하나의 클래스로 모듈화하고, 필요한 DTO 필드에 어노테이션 한 줄만 추가하여 **검증 로직의 재사용성과 코드 가독성**을 크게 향상시켰습니다.
- **관련 소스:** `RegionValid.java`, `RegionValidator.java`

### 2.3. Optional을 활용한 Null-Safety 확보
`findById`와 같이 데이터 조회를 실패할 경우 `null`을 반환하는 메서드는 `NullPointerException`(NPE)을 유발할 수 있습니다. 이를 방지하기 위해, JPA Repository의 반환 타입을 `Optional<T>`로 지정하고, 서비스 로직에서 `.orElseThrow()` 패턴을 일관되게 사용했습니다. 이 패턴을 통해 `if (result == null)`과 같은 지저분한 `null` 체크 로직을 제거하고, "데이터 없음"이라는 상황을 `BoardNotFoundException`과 같은 **명시적인 비즈니스 예외로 즉시 전환**하여 코드의 간결성과 안정성을 높였습니다.
- **관련 소스:** `BoardService.java`, `MemberService.java`

### 2.4. 명시적인 API 엔드포인트 설계
RESTful API의 핵심은 예측 가능하고 명확한 자원(Resource) 표현입니다. 이를 위해 `@PathVariable`과 `@RequestParam`의 용도를 명확히 구분하여 API를 설계했습니다. 특정 자원을 식별할 때는 `boards/{boardId}`와 같이 `@PathVariable`을 사용하고, 정렬·필터링·페이지네이션 등 자원의 표현 방식을 지정할 때는 `?boardId=...` 와 같이 `@RequestParam`을 사용하여 **API의 의도를 명확하게 표현**했습니다.
- **관련 소스:** `BoardResourceController.java`, `BoardPageController.java`

---

## 📈 코드 품질 및 유지보수성 향상

향후 기능 확장을 고려하여, 코드의 결합도를 낮추고 각 계층의 역할을 명확히 하는 설계를 적용했습니다.

### 3.1. 도메인 모델과 보안 모델의 분리
핵심 도메인 객체(`MembersEntity`)가 Spring Security의 `UserDetails` 인터페이스를 직접 구현하면, 도메인 모델이 특정 프레임워크에 강하게 종속되어 유연성이 떨어집니다. 이 문제를 해결하기 위해, 인증 및 보안 정보만을 담는 `AuthenticationEntity`와 `UserEntityDetails`를 별도로 생성했습니다. 이를 통해 **핵심 비즈니스 도메인과 보안 도메인을 완벽히 분리**하여, 서로의 변경에 영향을 받지 않는 낮은 결합도의 유연한 구조를 완성했습니다.
- **관련 소스:** `MembersEntity.java`, `AuthenticationEntity.java`, `UserEntityDetails.java`

---

## ⚡ 예외 처리 및 흐름 제어

예상치 못한 오류가 발생하더라도 시스템이 중단되지 않고, 사용자에게 일관된 경험을 제공하기 위한 체계적인 예외 처리 구조를 설계했습니다.

### 4.1. @RestControllerAdvice를 이용한 중앙 집중식 예외 처리
서비스나 컨트롤러의 비즈니스 로직 코드 내에 `try-catch` 블록이 흩어져 있으면 코드가 복잡해지고 유지보수가 어렵습니다. 이 문제를 해결하기 위해 `@RestControllerAdvice`와 `@ControllerAdvice`를 사용하여 예외 처리 로직을 중앙으로 집중시켰습니다. 이를 통해 비즈니스 로직은 핵심 기능에만 집중하고, 모든 예외 상황은 전역 예외 핸들러에서 일관된 방식으로 처리하여 **코드의 분리도와 가독성을 크게 향상**시켰습니다.
- **관련 소스:** `BoardAPIControllerExceptionHandler.java`, `MemberAPIControllerExceptionHandler.java`, `BoardViewControllerExceptionHandler.java`

### 4.2. 인증(401)과 인가(403) 예외 처리의 명확한 분리
인증 실패(로그인 필요)와 인가 실패(권한 없음)를 동일하게 처리하면 사용자에게 혼란을 줍니다. 이를 명확히 구분하기 위해 Spring Security의 예외 처리 책임을 분리했습니다. 인증되지 않은 사용자가 보호된 리소스에 접근하면 `AuthenticationEntryPoint`(401)가, 인증은 되었으나 접근 권한이 없으면 `AccessDeniedHandler`(403)가 동작하도록 커스텀 핸들러를 구현하고 등록했습니다. 이를 통해 **시나리오별로 명확한 상태 코드와 메시지를 반환**하여 API의 신뢰도와 사용자 경험을 개선했습니다.
- **관련 소스:** `SecurityConfig.java`, `JwtAuthenticationFailureHandler.java`, `JwtAccessDeniedHandler.java`

### 4.3. JWT 클레임 불일치 문제 해결
사용자가 닉네임 등 개인 정보를 수정해도, 기존에 발급된 JWT에는 이전 정보가 남아있어 UI에 즉시 반영되지 않는 문제가 있었습니다. 이를 해결하기 위해 **'토큰 재발급 및 강제 만료'** 전략을 도입했습니다. 정보 수정 성공 시, **(1) Redis에서 기존 토큰을 즉시 삭제하여 무효화**하고, **(2) 변경된 정보로 새로운 토큰을 재발급**하여 클라이언트의 쿠키를 갱신했습니다. 이를 통해 사용자는 재로그인 없이도 즉시 동기화된 정보를 확인할 수 있습니다.
- **관련 소스:** `MemberService.java`

---

## 💾 데이터베이스 상호작용 및 최적화

데이터의 정합성을 보장하고, 각기 다른 도메인의 요구사항에 맞는 최적의 데이터 접근 방식을 선택하여 적용했습니다.

### 5.1. @Transactional을 이용한 데이터 정합성 보장
회원가입과 같이 여러 테이블에 걸쳐 데이터가 동시에 저장되어야 하는 작업에서, 일부 작업만 성공하고 일부는 실패할 경우 데이터가 불일치 상태에 빠질 수 있습니다. 이를 방지하기 위해, 여러 DB 작업을 포함하는 서비스 메서드에 `@Transactional` 어노테이션을 적용했습니다. 이를 통해 해당 메서드 내의 모든 DB 작업이 **하나의 원자적(Atomic) 단위**로 묶여, 중간에 오류가 발생하면 모든 작업이 롤백(Rollback)되도록 하여 **데이터의 정합성을 보장**했습니다.
- **관련 소스:** `UserEntityDetailService.java`, `BoardEntityRepository.java`

### 5.2. JPA와 JdbcTemplate의 전략적 혼용
각 도메인의 특성에 맞춰 데이터 접근 기술을 다르게 선택했습니다. 일반적인 CRUD 작업이 주를 이루는 '게시판'이나 '회원' 도메인에서는 **Spring Data JPA**를 사용하여 높은 생산성을 확보했습니다. 반면, 사용자의 입력에 따라 정렬 기준이 동적으로 변경되는 등 복잡한 쿼리 생성이 필요한 '추천' 도메인에서는 **JdbcTemplate**을 사용하여 유연하게 SQL을 직접 구성하고 실행했습니다. 이러한 **전략적 혼용**을 통해 생산성과 유연성을 모두 확보했습니다.
- **관련 소스:** `BoardEntityRepository.java` (JPA), `RecServiceEmpRepository.java` (JdbcTemplate)

### 5.3. 네이티브 쿼리를 이용한 페이징 처리
JPA 메서드 쿼리나 JPQL로 구현하기 복잡한 데이터베이스별 최적화 구문을 사용하기 위해, `@Query(nativeQuery = true)` 어노테이션을 활용했습니다. 게시판 목록 조회 시, Oracle DB에 최적화된 `OFFSET-FETCH` 페이징 구문을 네이티브 쿼리로 직접 작성하여, JPQL 변환 과정을 거치지 않고 가장 효율적인 방식으로 DB 페이징을 구현했습니다.
- **관련 소스:** `BoardEntityRepository.java`

---

## ⚡ 성능 최적화

반복적인 데이터 조회로 인한 시스템 부하를 줄이고, 사용자에게 빠른 응답 속도를 제공하기 위해 캐시를 활용한 성능 최적화를 진행했습니다.

### 6.1. 이원화된 TTL 캐시 전략
지도 데이터는 '전체 지역 데이터'와 '사용자가 선택한 특정 지역 데이터'라는 두 가지 다른 접근 패턴을 가집니다. 단일 캐시 전략은 이러한 다른 패턴에 대응하기 비효율적이므로, **데이터의 성격에 따라 TTL(Time-To-Live)을 다르게 설정하는 이원화 캐시 전략**을 도입했습니다. 변경 빈도가 매우 낮은 전체 지도 데이터는 **24시간의 긴 TTL**을, 사용자의 선택에 따라 단시간에 반복적으로 조회될 수 있는 특정 지역 데이터는 **1시간의 짧은 TTL**을 적용하여 캐시 효율을 극대화했습니다. 이 전략을 통해 **평균 응답 속도를 35% 단축**하고, 관련 **SQL 쿼리 호출 수를 99% 감소**시켰습니다.
- **관련 소스:** `MapDataService.java`

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
