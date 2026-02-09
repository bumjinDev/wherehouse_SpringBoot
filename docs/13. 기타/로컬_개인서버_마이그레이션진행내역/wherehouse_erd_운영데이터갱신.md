# Wherehouse ERD — 회원 관리 & 리뷰 도메인

> Entity 코드(`@Entity`, `@Table`)에서 역추출한 실제 테이블 구조

---

## 전체 테이블 목록 (회원 + 리뷰 관련)

| # | 테이블명 | Entity 클래스 | 도메인 |
|---|---------|--------------|--------|
| 1 | `MEMBERTBL` | `MembersEntity` | 회원 관리 |
| 2 | `USERENTITY` | `AuthenticationEntity` | 인증/인가 (JWT) |
| 3 | `USERENTITY_ROLES` | `@ElementCollection` (하위 테이블) | 인증/인가 (JWT) |
| 4 | `WHEREBOARD` | `BoardEntity` | 게시판 |
| 5 | `COMMENTTBL` | `CommentEntity` | 게시판 댓글 |
| 6 | `REVIEWS` | `Review` | 리뷰 |
| 7 | `REVIEW_STATISTICS` | `ReviewStatistics` | 리뷰 통계 |
| 8 | `REVIEW_KEYWORDS` | `ReviewKeyword` | 리뷰 키워드 |

---

## ERD (Mermaid)

```mermaid
erDiagram

    %% ============================================================
    %% 1. 회원 관리 도메인
    %% ============================================================

    MEMBERTBL {
        VARCHAR2 id PK "사용자 아이디 (4~20자)"
        VARCHAR2 pw "비밀번호 (8자 이상)"
        VARCHAR2 nick_name "닉네임 (최대 20자)"
        VARCHAR2 tel "전화번호 (010-XXXX-XXXX)"
        VARCHAR2 email "이메일"
        DATE join_date "가입일"
    }

    USERENTITY {
        VARCHAR2 userid PK "사용자 아이디"
        VARCHAR2 username "사용자 닉네임"
        VARCHAR2 password "암호화된 비밀번호"
    }

    USERENTITY_ROLES {
        VARCHAR2 userid FK "USERENTITY.userid"
        VARCHAR2 roles "권한 (ROLE_USER 등)"
    }

    %% ============================================================
    %% 2. 게시판 도메인
    %% ============================================================

    WHEREBOARD {
        NUMBER connum PK "게시글 번호 (SEQ: whereboarder_seq)"
        VARCHAR2 userid "작성자 아이디"
        VARCHAR2 title "제목"
        CLOB boardcontent "본문"
        VARCHAR2 region "지역"
        NUMBER hit "조회수"
        DATE bdate "작성일"
    }

    COMMENTTBL {
        NUMBER commentprimary PK "댓글 PK (SEQ: commenttbl_seq)"
        NUMBER board_id "게시글 번호"
        VARCHAR2 user_id "작성자 아이디"
        VARCHAR2 user_name "작성자 닉네임"
        VARCHAR2 reply_content "댓글 내용"
    }

    %% ============================================================
    %% 3. 리뷰 도메인
    %% ============================================================

    REVIEWS {
        NUMBER review_id PK "리뷰 ID (SEQ: SEQ_REVIEW_ID)"
        VARCHAR2 property_id "매물 ID (32자, 전세/월세 공용)"
        VARCHAR2 user_id "작성자 아이디"
        NUMBER rating "평점"
        CLOB content "리뷰 본문"
        TIMESTAMP created_at "작성일시"
        TIMESTAMP updated_at "수정일시"
    }

    REVIEW_STATISTICS {
        VARCHAR2 property_id PK "매물 ID (1:1)"
        NUMBER review_count "리뷰 수"
        NUMBER avg_rating "평균 평점 (DECIMAL 3,2)"
        NUMBER positive_keyword_count "긍정 키워드 수"
        NUMBER negative_keyword_count "부정 키워드 수"
        TIMESTAMP last_calced "최종 산출 일시"
    }

    REVIEW_KEYWORDS {
        NUMBER keyword_id PK "키워드 ID (SEQ: SEQ_KEYWORD_ID)"
        NUMBER review_id FK "리뷰 ID"
        VARCHAR2 keyword "키워드 (50자)"
        NUMBER score "감성점수 (+1 긍정 / -1 부정)"
    }

    %% ============================================================
    %% 관계 정의
    %% ============================================================

    %% 회원 → 인증 (회원가입 시 동시 생성, 논리적 1:1)
    MEMBERTBL ||--|| USERENTITY : "회원가입 시 동기 생성"

    %% 인증 → 권한 (1:N, ElementCollection)
    USERENTITY ||--|{ USERENTITY_ROLES : "userid FK, ON DELETE CASCADE"

    %% 회원 → 게시판 (논리적 FK, 물리적 제약 없음)
    MEMBERTBL ||--o{ WHEREBOARD : "userid (논리적 참조)"

    %% 게시판 → 댓글 (논리적 FK)
    WHEREBOARD ||--o{ COMMENTTBL : "board_id → connum"

    %% 회원 → 댓글 (논리적 FK)
    MEMBERTBL ||--o{ COMMENTTBL : "user_id (논리적 참조)"

    %% 회원 → 리뷰 (논리적 FK)
    MEMBERTBL ||--o{ REVIEWS : "user_id (논리적 참조)"

    %% 리뷰 → 리뷰 키워드 (1:N, ON DELETE CASCADE)
    REVIEWS ||--o{ REVIEW_KEYWORDS : "review_id FK"

    %% 매물 → 리뷰 통계 (1:1, 동일 PK)
    REVIEWS }o--|| REVIEW_STATISTICS : "property_id 기준 집계"

    %% 리뷰 UK 제약: (PROPERTY_ID, USER_ID) 유니크
```

---

## 핵심 관계 분석

### A. 회원 관리 → 인증 (이중 테이블 구조)

| 관계 | 설명 |
|------|------|
| `MEMBERTBL` ↔ `USERENTITY` | **논리적 1:1**, 물리적 FK 없음 |
| 동기화 시점 | `AuthenticationEntityConverter.toEntity()`로 회원가입 시 동시 생성 |
| 매핑 | `MEMBERTBL.id` = `USERENTITY.userid`, `MEMBERTBL.nickName` = `USERENTITY.username`, `MEMBERTBL.pw` = `USERENTITY.password` |
| 역할 분리 | `MEMBERTBL` = 회원 프로필 정보, `USERENTITY` = Spring Security 인증 전용 |

### B. 리뷰 도메인 3개 테이블 관계

```
REVIEWS (원본) ──1:N──→ REVIEW_KEYWORDS (키워드 태그)
    │
    └── property_id 기준 ──N:1──→ REVIEW_STATISTICS (집계 캐시)
```

- **REVIEWS → REVIEW_KEYWORDS**: `review_id` FK, CASCADE DELETE
- **REVIEWS → REVIEW_STATISTICS**: `property_id` 기준 집계. REVIEW_STATISTICS의 PK가 `property_id` 자체 (1:1)
- **UK 제약**: `REVIEWS(PROPERTY_ID, USER_ID)` 유니크 → 한 매물에 한 유저 하나의 리뷰만 가능

### C. 시퀀스 객체 목록

| 시퀀스 | 대상 테이블 | 컬럼 |
|--------|-----------|------|
| `SEQ_REVIEW_ID` | `REVIEWS` | `REVIEW_ID` |
| `SEQ_KEYWORD_ID` | `REVIEW_KEYWORDS` | `KEYWORD_ID` |
| `whereboarder_seq` | `WHEREBOARD` | `CONNUM` |
| `commenttbl_seq` | `COMMENTTBL` | `COMMENTPRIMARY` |

---

## 테스트 데이터 삽입 시 주의사항

### 1. 삽입 순서 (FK 의존성)

```
── 현재 삽입 대상 ──────────────────────────
① MEMBERTBL (회원)
② USERENTITY + USERENTITY_ROLES (인증 — 반드시 ①과 동시)
③ REVIEWS (리뷰 — user_id, property_id 참조)
④ REVIEW_KEYWORDS (키워드 — review_id 참조)
⑤ REVIEW_STATISTICS (통계 — property_id 기준 집계)

── 최후순위 (현재 미진행) ──────────────────
⑥ WHEREBOARD (게시판 — userid 참조)
⑦ COMMENTTBL (댓글 — board_id, user_id 참조)
```

### 2. 시퀀스 동기화 필수

Review Entity 주석에 명시된 대로, 직접 INSERT 후 시퀀스 Current Value를 반드시 동기화해야 한다:

```sql
-- 예: REVIEWS 테이블 직접 INSERT 후
SELECT MAX(REVIEW_ID) FROM REVIEWS;  -- 결과: 150
-- 시퀀스를 151 이상으로 조정
ALTER SEQUENCE SEQ_REVIEW_ID INCREMENT BY 151;
SELECT SEQ_REVIEW_ID.NEXTVAL FROM DUAL;
ALTER SEQUENCE SEQ_REVIEW_ID INCREMENT BY 1;
```

### 3. MEMBERTBL ↔ USERENTITY 정합성

회원 데이터 직접 INSERT 시 두 테이블 모두에 삽입해야 JWT 인증이 동작한다. `USERENTITY_ROLES`에 최소 하나의 권한(`ROLE_USER`)도 필수.

### 4. REVIEWS 유니크 제약

`UK_REVIEWS_PROPERTY_USER(PROPERTY_ID, USER_ID)` — 동일 매물에 동일 유저의 중복 리뷰 INSERT 시 제약 위반 에러 발생.
