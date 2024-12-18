<p style="display: flex; align-items: center; gap: 10px;">
   <span style="font-size: 1.5rem; font-weight: bold; white-space: nowrap;"><h1>서울 시 1인 세대 위한 거주지 추천 서비스</h1></span>
</p>
<img src="https://github.com/user-attachments/assets/eca1f421-5684-4a0e-8273-31b827b5b1f8" alt="1" width="1200" height="500">
<br><br>

---
# 프로젝트 소개
<br>
서울시 1인 MZ 세대의 1인 가구 비율 증가 추세에 따른 거주지 추천 서비스입니다.<br>
WHERE HOUSE는 사용자가 설정한 안전성 비중, 편의성 비중, 그리고 전세 및 월세 금액 정보를 바탕으로 주거지 추천 정보를 제공합니다.
<br><br>

---
# Project Architecture

![2](https://github.com/user-attachments/assets/08b983ad-854b-4f39-8a6f-e514a9014ebf)

+ 배포 URL : https://wherehouse.servehttp.com/wherehouse/
<br><br>

---


<br><br>

---
# 팀원 구성

| 정범진 | 이재서 |
| --- | --- |
| <img src="https://github.com/user-attachments/assets/4f66f287-8799-49fb-88f4-b67582db7b39" alt="MyIcon"  width="100" height="100" alt="jung"/> | <img src="https://github.com/user-attachments/assets/7be184e0-f8f4-4548-8fa6-f084c69f4f0b" width="100" height="100"  alt="LeeJaeseo" /> |
| [@bumjinDev](https://github.com/bumjinDev/wherehouse) | [@N0WST4NDUP](https://github.com/N0WST4NDUP) |

<br><br>

## 프로젝트 구조
---
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
<br><br>

## 개발 기간 및 작업 관리
---
# 개발 기간
	2023.11.13~2024.03.08
<br>

# 작업 관리
	- GitHub를 사용해서 진행 상황을 공유하였습니다.
	- 회의를 진행하며 작업 내용 및 방향성에 대한 고민을 나누고 실제 작업 계획서를 작성하여 공유하였습니다.
<br>

# 페이지별 기능
---
## [메인 페이지]
	- 서비스에 접속하면 가장 먼저 보이는 초기 화면입니다. 이 화면에서는 각 서비스 페이지로의 접근과 로그인/회원가입 페이지로의 이동이 가능합니다.
 	- 로그인된 경우, 페이지 내에 로그인 세션 정보(사용자 닉네임 등)가 포함된 페이지가 제공됩니다.
  <img src="https://github.com/user-attachments/assets/8e2c3413-97a5-4380-884b-32c4bce70275" alt="MainPage"/>

## [거주지 추천]
	- 사용자가 안전성과 편의성 비중을 선택하고 전세 및 월세 금액을 설정하면, 서울시 내에서 추천할 지역구 3곳을 추천합니다.
  <img src="https://github.com/user-attachments/assets/cc102f19-21ea-45ee-a1d4-69e3e6ba4c37" alt="RecommandPage">

## [상세지도]
	- 사용자가 지도 위에 핀포인트를 지정하면, 반경 500m 내의 CCTV 위치를 마커로 표시하고, 가장 가까운 파출소 거리 및 편의시설 정보를 제공합니다.
  <img src="https://github.com/user-attachments/assets/ba07bf7d-f11b-4355-b81d-42c6b8ad9376" alt="DetailMapPage">
  	
## [게시판]
	- 사용자 간의 원활한 정보 공유를 위해 마련된 공간입니다.
 	- 로그인 하지 않았을 시 게시판 글 작성이 불가능 합니다.
  <img src="https://github.com/user-attachments/assets/bb22056b-d08c-42b6-8872-f381ecc18cfc" />
  
## [회원 가입]
	- 회원가입 기능으로 로그인 시 게시판 이용이 가능합니다.
 	- 유효성 검사 로직을 통해 필수 정보를 누락하거나 비밀번호 확인이 올바르지 않은 경우 회원가입이 진행되지 않습니다.
 <img src="https://github.com/user-attachments/assets/db61d203-b238-4293-ad6f-be41ddc75d5e" alt="JoinPage">

## [로그인]
	- 로그인이 완료되면 세션 정보를 포함한 페이지로 리디렉션됩니다.
 <img src="https://github.com/user-attachments/assets/38030b0e-45b3-49ba-b6fa-095464379e9c" />
 
## [회원 수정]
	- 회원 정보 수정 기능 입니다.
 	- 회원 수정 시 기존 아이디는 고유 값으로 수정할 수 없습니다. 그러나 비밀번호, 닉네임, 전화번호, 이메일 정보는 수정 가능합니다.
 <img src="https://github.com/user-attachments/assets/311b4ea7-de69-46f5-9b7b-f5d7e221f0be" alt="MemberEditPage">

