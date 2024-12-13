<img src="https://github.com/user-attachments/assets/f521acdb-4507-4aee-8abd-ac88f80318bb" width="100" height="100"/>| <h1>거주지 추천 서비스 README</h1>|
---| ---|

---

![2](https://github.com/user-attachments/assets/84dc3382-ae6f-4856-a8f0-2a21242319d3)

+ 배포 URL : http://43.202.178.156:8181/wherehouse/

<br><br>

## 프로젝트 소개
---
서울시 1인 MZ 세대의 1인 가구 비율 증가에 따른 거주지 추천 서비스입니다.
+ 거주지 추천은 사용자 선택에 따라 각 구별 안전성 비중, 편의성 비중, 그리고 전세 및 월세 금액 기준으로 주거지 추천 지역구 3곳을 선정하여 제공합니다.
+ 지역구 지도에서는 서울시 내 각 구를 선택하여 해당 구의 전세 또는 월세 정보를 제공하며, 핫플레이스 정보도 함께 제공합니다.
+ 상세 지도 페이지에서는 클릭 이벤트 발생 지점 기준 반경 500m 내의 CCTV 위치를 마커로 표시하고, 가장 가까운 파출소 거리 및 편의 시설 정보를 선택적으로 확인할 수 있습니다.
+ 게시판 기능은 회원들 간 정보 공유를 위해 작성되었습니다.

<br><br>



## 팀원 구성

| 정범진 | 이재서 |
| --- | --- |
| <img src="https://github.com/user-attachments/assets/946612ee-7f7f-41ce-8d8c-85b578f18d2d" width="100" height="100" alt="jung"/> | <img src="https://github.com/user-attachments/assets/8b4a2dd0-166a-4e04-93eb-38482a2828fe" width="100" height="100" alt="lee"/> |
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
### 개발 기간
	2023.11.13~2024.03.08
<br>

### 작업 관리
	- GitHub를 사용해서 진행 상황을 공유하였습니다.
	- 회의를 진행하며 작업 내용 및 방향성에 대한 고민을 나누고 실제 작업 계획서를 작성하여 공유하였습니다.
<br>

## 페이지별 기능
---
### [초기화면]
	- 서비스에 접속하면 가장 먼저 보이는 초기 화면입니다. 이 화면에서는 각 서비스 페이지로의 접근과 로그인/회원가입 페이지로의 이동이 가능합니다.
 	- 로그인된 경우, 페이지 내에 로그인 세션 정보(사용자 닉네임 등)가 포함된 페이지가 제공됩니다.
  
  <img src="https://github.com/user-attachments/assets/255d06a4-7c9b-463b-a053-f3cd8354eecf" />

### [거주지 추천]
	- 사용자가 안전성과 편의성 비중을 선택하고 전세 및 월세 금액을 설정하면, 서울시 내에서 추천할 지역구 3곳을 추천합니다.
  <img src="https://github.com/user-attachments/assets/6de5e888-cd4c-4145-a128-ae27b3a21203" />
 	
### [상세지도]
	- 사용자가 지도 위에 핀포인트를 지정하면, 반경 500m 내의 CCTV 위치를 마커로 표시하고, 가장 가까운 파출소 거리 및 편의시설 정보를 제공합니다.
  <img src="https://github.com/user-attachments/assets/9af6ac87-f5a7-4ec1-8c37-1033e7797be2" />
  	
### [게시판]
	- 사용자 간의 원활한 정보 공유를 위해 마련된 공간입니다.
 	- 로그인 하지 않았을 시 게시판 글 작성이 불가능 합니다.
  <img src="https://github.com/user-attachments/assets/bb22056b-d08c-42b6-8872-f381ecc18cfc" />
  
### [회원 가입]
	- 회원가입 기능으로 로그인 시 게시판 이용이 가능합니다.
 	- 유효성 검사 로직을 통해 필수 정보를 누락하거나 비밀번호 확인이 올바르지 않은 경우 회원가입이 진행되지 않습니다.
 <img src="https://github.com/user-attachments/assets/a0320fc9-9d55-413b-8c05-174e7a69f0bc" />

### [로그인]
	- 로그인이 완료되면 세션 정보를 포함한 페이지로 리디렉션됩니다.
 <img src="https://github.com/user-attachments/assets/38030b0e-45b3-49ba-b6fa-095464379e9c" />
 
 ### [회원 수정]
	- 회원 정보 수정 기능 입니다.
 	- 회원 수정 시 기존 아이디는 고유 값으로 수정할 수 없습니다. 그러나 비밀번호, 닉네임, 전화번호, 이메일 정보는 수정 가능합니다.
 <img src="https://github.com/user-attachments/assets/4edf769a-f30f-469e-b315-a54b813d6f7a" />
