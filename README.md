<p align="left" style="display: flex; align-items: center; gap: 10px;">
   <span style="font-size: 2.0rem; font-weight: bold;"><h2>서울시 1인 세대 위한 거주지 추천 서비스</h2></span>
</p>
<p align="center">
   <img src="https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8" alt="Logo" width="1200" height="500"/>
</p>

  
</p>

---

## 🚀 프로젝트 소개  

서울시 1인 MZ 세대의 1인 가구 비율 증가 추세에 따른 거주지 추천 서비스입니다.  
**WHERE HOUSE**는 사용자가 설정한 **안전성 비중**, **편의성 비중**, **전세 및 월세 금액** 정보를 바탕으로 주거지 추천 정보를 제공합니다.  

---

## 🛠 Project Architecture  

<p align="center">
    <img src="https://github.com/user-attachments/assets/08b983ad-854b-4f39-8a6f-e514a9014ebf" alt="Architecture" width="800"/>
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
   - Kakao Map API를 활용해 지도 내 사용자가 클릭한 좌표를 기준으로 해당 지역구 반경 500m 내 CCTV 위치를 마커로 표시하고, CCTV 개수 및 가장 가까운 파출소까지의 거리를 제공하는 상세 지도 페이지 서비스 개발

### **2차 프로젝트: Servlet 기반 개발**  
- **[한준원]**  
   - 주거지 추천 서비스 개발  
- **[이재서]**  
   - 기존 상세지도 서비스를 Servlet으로 변환하고, 사용자의 안전성과 편의성 비중 선택에 따라 맞춤형 추천 정보를 제공.
- **[정범진]**  
   - 게시판 및 로그인/회원 가입 기능 개발  

### **3차 프로젝트: Spring Boot로 전환**  
- **[정범진]**  
   - Servlet 기반 주거지 추천 서비스, 회원 관리 및 게시판을 Spring Boot로 전환  
- **[이재서]**  
   - Servlet 기반 상세지도 서비스를 Spring Boot로 전환  

### **추가 개선 작업**  
- **[정범진]**  
   - Spring Security 도입<br>
   	- JWT 기반 인증 및 CSRF 방지를 위해 Host-Only 쿠키 적용
   	- X-CONTENT 정책 응답 헤더 따른 기존 UI 개선 작업 수행
   	- CSP 정책 설정  
   - HTTPS 적용<br>
   	- WT 토큰 탈취 방지  
   - 데이터 접근 방식 전환<br>
   	- JdbcTemplate → JPA  
   - 데이터베이스 개선<br>
   	- Oracle Database를 Docker 기반으로 전환  
   - CI/CD 구축<br>
   - 	- Jenkins를 활용한 지속적 통합 및 배포 환경 구축  

---

## 🧑‍💻 팀원 구성  

| 정범진 | 이재서 |
|--------|--------|
| <img src="https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39" alt="Jung" width="100" height="100"/> | <img src="https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b" alt="Lee" width="100" height="100"/> |
| [@bumjinDev](https://github.com/bumjinDev/wherehouse) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |

---

## 🗂 프로젝트 구조  

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
서비스 초기 화면으로 각 페이지 접근 및 로그인/회원가입 기능을 제공합니다.  
<p align="left"><img src="https://github.com/user-attachments/assets/8e2c3413-97a5-4380-884b-32c4bce70275" alt="Main Page" /></p>

### **[거주지 추천]**  
사용자 설정 기반으로 추천 지역구 3곳을 제공합니다.  
<p align="left"><img src="https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37" alt="Recommand Page" /></p>

### **[상세지도]**  
반경 500m 내 CCTV 및 주요 시설 정보를 제공합니다.  
<p align="left"><img src="https://github.com/user-attachments/assets/ba07bf7d-f11b-4355-b81d-42c6b8ad9376" alt="Detail Map Page" /></p>
