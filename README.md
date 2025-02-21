# 서울시 1인 세대를 위한 거주지 추천 서비스

<p align="center">
   <img src="https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8" alt="Logo" width="1200" height="500"/>
</p>

---

## 🚀 프로젝트 소개  

서울시 1인 MZ 세대의 1인 가구 비율 증가 추세에 따른 거주지 추천 서비스입니다.  
**WHERE HOUSE**는 사용자가 설정한 **안전성 비중**, **편의성 비중**, **전세 및 월세 금액** 정보를 바탕으로 주거지 추천 정보를 제공합니다.  

---

## 🛠 Project Architecture  

<p align="center">
    <img src="https://github.com/user-attachments/assets/3f642b20-0713-453b-8be0-b3a10f56a950" alt="Architecture" width="800"/>
</p>

- **배포 URL**: [https://wherehouse.servehttp.com/wherehouse/](https://wherehouse.servehttp.com/wherehouse/)

---

## 👥 역할 분담  

### **1차 프로젝트: UI 개발**  
- **[한준원]**  
   - 전체 UI 설계 및 제작  
   - 거주지 추천 페이지 및 지역구 정보 페이지 개발  
- **[이재서]**  
   - 행정동별 정보 페이지 개발  
- **[정범진]**  
   - 상세 지도 페이지 내 사용자 클릭 이벤트 기반 좌표 생성 따른 Kakao Map API를 활용한 반경 500m 내 CCTV 마커 표시  

### **2차 프로젝트: Servlet 기반 개발**  
- **[한준원]**  
   - 주거지 추천 서비스 개발  
- **[이재서]**  
   - 기존 상세지도 서비스를 Servlet으로 변환  
- **[정범진]**  
   - 게시판 및 로그인/회원 가입 기능 개발  

### **3차 프로젝트: Spring Boot로 전환**  
- **[정범진]**  
   - Servlet 기반 주거지 추천 서비스, 회원 관리 및 게시판을 Spring Boot로 전환  
- **[이재서]**  
   - Servlet 기반 상세지도 서비스를 Spring Boot로 전환  

---

## 🔥 추가 개선 작업

### **1. Spring Security 도입**
- **JWT 기반 인증 및 CSRF 방지**를 위해 **Host-Only 쿠키와 Redis 활용**하여 보안 강화  
- **X-Content-Type-Options** 응답 헤더 정책에 따른 **기존 UI 개선 작업 수행**  
- **CSP(Content Security Policy) 정책 설정**을 통한 보안 강화  

### **2. 보안 설계**
- 기존 **HttpSession 기반 인증 방식**을 **JWT 토큰 방식**으로 전환  
- **JWT 스푸핑 방지**를 위해 **HTTPS 적용** 및 보안 정책 강화  

### **3. 데이터 접근 방식 전환**
- 기존 **JdbcTemplate**을 **JPA 기반으로 전환**하여 유지보수성 및 확장성 개선  

### **4. 데이터베이스 개선**
- 기존 **Oracle Database**를 **Docker 기반 환경으로 전환**하여 운영 효율성 향상  

### **5. CI/CD 구축**
- **Jenkins 기반 지속적 통합 및 배포 환경 구축**  
- 자동 빌드 및 배포 프로세스를 최적화하여 안정적인 운영 환경 제공  

### **6. Redis 기반 캐싱 적용**
#### ✅ JWT 토큰 저장 방식 변경
- 기존 **Oracle RDBMS에 저장하던 JWT 토큰을 Redis 기반 저장 방식으로 전환**  
- 기존 `.json` 파일을 직접 요청하는 방식에서 **Rest API 요청 방식으로 변경**  

#### ✅ 주거지 추천 페이지 및 지역구 지도 최적화
- 지역구별 좌표 데이터를 **Rest API 요청 방식으로 전환**  
- 각 데이터에 **TTL(Time-To-Live) 설정 적용**  
- **LRU(Least Recently Used) 정책 적용**을 통한 메모리 관리 최적화  

#### ✅ Redis 고가용성(HA) 및 Sentinel 구성
- **Failover 대비**를 위해 **Redis Sentinel을 이용한 고가용성 아키텍처 구축**  
- 장애 발생 시 **자동 복구 메커니즘 적용**  

---

## 🧑‍💻 팀원 구성  

| 정범진 | 이재서 |
|--------|--------|
| <img src="https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39" alt="Jung" width="100" height="100"/> | <img src="https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b" alt="Lee" width="100" height="100"/> |
| [@bumjinDev](https://github.com/bumjinDev/wherehouse) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |

---

## 📂 프로젝트 구조  

<pre>
WhereHouse
   └─ src
 	├─ board
	│     ├─ controller
	│     ├─ dao
	|     ├─ model
	│     ├─ service
 	├─ information
	│     ├─ controller
	│     ├─ dao
	│     ├─ model
 	│     ├─ service
 	├─ mainpage
 	│     ├─ controller
 	├─ members
 	│     ├─ controller
 	│     ├─ dao
 	│     ├─ model
 	│     └─ service
 	└─ recommand
	      ├─ controller
	      ├─ dao
	      ├─ model
	      └─ service
</pre>

---

## 🛠 개발 환경  

<p align="left">
    <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white" alt="Java"/>
    <img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black" alt="JavaScript"/>
    <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot"/>
    <img src="https://img.shields.io/badge/Oracle-F80000?style=for-the-badge&logo=oracle&logoColor=white" alt="Oracle"/>
    <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker"/>
    <img src="https://img.shields.io/badge/Jenkins-D24939?style=for-the-badge&logo=jenkins&logoColor=white" alt="Jenkins"/>
    <img src="https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonaws&logoColor=white" alt="AWS"/>
    <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JWT"/>
</p>

---

## 📋 작업 관리  

- GitHub를 사용해서 진행 상황을 공유하였습니다.  
- 회의를 통해 작업 내용 및 방향성을 논의하고, 계획서를 작성해 공유하였습니다.  

---

## 📄 페이지별 기능

### **[메인 페이지]**  
- **기능**: 서비스의 첫 화면으로, 사용자 로그인/회원가입 기능 및 주요 페이지로의 이동 경로를 제공합니다.  
- **주요 구성 요소**:  
  - 상단 네비게이션 바: 사용자가 로그인, 회원가입, 주요 서비스 접근.  
  - 사용자 친화적 UI 설계로 편리한 초기 서비스 접근 제공.  

<p align="left">
    <img src="https://github.com/user-attachments/assets/8e2c3413-97a5-4380-884b-32c4bce70275" alt="Main Page"/>
</p>

---

### **[거주지 추천]**  
- **기능**: 사용자가 입력한 기준(안전성, 편의성, 가격대)을 기반으로 서울시 내 추천 거주지 3곳을 제공합니다.  
- **주요 구성 요소**:  
  - 안전성 및 편의성: 사용자 우선 비중 설정.  
  - 가격대 필터: 전세 및 월세 조건 필터링.  
  - 추천 결과: 상위 3개의 추천 지역구 및 주요 정보 표시.  

<p align="left">
    <img src="https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37" alt="Recommand Page"/>
</p>

---

### **[상세지도]**  
- **기능**: 사용자가 선택한 지역구의 상세 정보를 제공하며, 반경 500m 내 CCTV와 주요 시설 정보를 시각적으로 표시합니다.  
- **주요 구성 요소**:  
  - CCTV 위치 표시: 선택 지역 기준 반경 500m 내 CCTV 마커 표시.  
  - 주요 시설 정보: 편의점, 약국, 병원 등 주요 시설의 위치 제공.  
  - Kakao Map API 활용: 지도 기반의 직관적 정보 표시.  

<p align="left">
    <img src="https://github.com/user-attachments/assets/ba07bf7d-f11b-4355-b81d-42c6b8ad9376" alt="Detail Map Page"/>
</p>

---

## 🛠 앞으로의 계획

프로젝트의 안정성과 확장성을 확보하기 위해 다음과 같은 작업을 진행할 계획입니다.

### 1. 코드 리팩토링 및 유지보수성 향상
- 컨트롤러, 서비스, DAO의 역할을 개선하고, 불필요한 의존성을 제거하여 코드의 가독성과 유지보수성을 높이는 작업을 진행할 예정입니다.
- 코드 전반에 걸쳐 클린 코드 원칙을 적용하고, 중복된 로직을 개선할 계획입니다.
- 전역적인 예외 처리 방식을 도입하고, 로깅 체계를 정비할 예정입니다.

### 2. 테스트 코드 작성 및 품질 관리 강화
- JUnit과 Mockito를 활용하여 핵심 기능의 단위 테스트를 추가하고, 통합 테스트를 통해 전체 시스템의 안정성을 검증할 계획입니다.
- CI/CD 환경과 연계하여 테스트 자동화를 도입하고, 코드 커버리지를 분석하여 품질을 높이는 데 집중할 예정입니다.

### 3. Redis 심화 및 성능 최적화
- 성능 모니터링 도구를 활용해 Redis의 작업 부하를 분석하고, 클러스터링을 통해 확장성을 확보할 예정입니다.
- SLOWLOG와 같은 명령어를 적극 활용하여 성능 병목 구간을 파악하고 최적화를 진행할 계획입니다.

### 4. 프론트엔드 React 적용 및 UI 개선
- React로 전환함으로써 컴포넌트 기반 아키텍처를 도입해 재사용성을 높이고, 상태 관리를 효율적으로 처리하여 사용자 경험을 향상할 계획입니다.

---
