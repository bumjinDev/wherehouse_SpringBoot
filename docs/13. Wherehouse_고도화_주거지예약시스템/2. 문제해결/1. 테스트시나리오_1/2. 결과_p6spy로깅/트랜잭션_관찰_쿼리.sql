--==============================================================================
-- F004 시나리오 1 — Oracle SQL Developer 수동 트랜잭션 관찰 쿼리 모음
--==============================================================================
-- 사용법:
--   1. SQL Developer 에서 두 세션을 띄운다 (Window → New Worksheet → SCOTT 접속).
--   2. 한 세션에서는 트랜잭션 발행 (createReservation 의 SQL 시퀀스에서 발췌),
--      다른 세션에서는 본 쿼리 모음으로 락·대기·트랜잭션 상태를 폴링한다.
--   3. SQL Developer 의 Auto Commit 은 OFF 로 둘 것 (기본값). COMMIT 은 명시적으로만.
--
-- 학습정리 토대 매핑:
--   6.1  모든 쓰기는 행 X-락을 건다           → § A·B 의 락 폴링으로 확인
--   6.2  Oracle UPDATE 의 내부 동작 (재평가)   → § D 의 시나리오로 재현
--   6.3  readers don't block writers          → § C 로 확인 (락 없는 SELECT 는 폴링에 안 잡힘)
--
-- 작성: 2026-05-28
--==============================================================================


-- ════════════════════════════════════════════════════════════════════════════
-- A. 자기 세션 식별 — 두 세션 각각에서 한 번 실행해서 SID 메모해 둘 것
-- ════════════════════════════════════════════════════════════════════════════
SELECT SYS_CONTEXT('USERENV','SID')    AS MY_SID,
       SYS_CONTEXT('USERENV','SESSIONID') AS AUDSID,
       USER                              AS USERNAME,
       SYSDATE                           AS NOW
  FROM DUAL;


-- ════════════════════════════════════════════════════════════════════════════
-- B. 현재 잡힌 락 (V$LOCK) — 메인 폴링 쿼리 (F006 2단계 테스트 결과 스타일)
-- ════════════════════════════════════════════════════════════════════════════
-- 컬럼: SID / SERIAL# / STATUS / TYPE / LMODE / REQUEST / BLOCK / OBJECT_NAME
--
-- TYPE:    TX = 트랜잭션 락 (활성 트랜잭션이 보유 — 행 X-락은 이 형태로 나타남)
--          TM = 테이블 락 (DML 시 자동, Row-X 모드)
-- LMODE:   0 None  /  1 Null  /  2 Row-S (SS)  /  3 Row-X (SX)
--          4 Share /  5 S/Row-X (SSX)  /  6 Exclusive (X)
-- REQUEST: 0 = 보유 중, >0 = 그 모드로 락을 기다리는 중
-- BLOCK:   1 = 이 락이 다른 세션을 막고 있음
--
-- OBJECT_NAME:
--   TM 락만 직접 매핑 (V$LOCK.ID1 = OBJECT_ID → USER_OBJECTS 조인).
--   TX 락은 NULL — 행 락의 어느 테이블인지는 같은 SID 의 TM 행을 보고 추론.
SELECT l.SID,
       s.SERIAL#,
       s.STATUS,
       l.TYPE,
       l.LMODE,
       l.REQUEST,
       l.BLOCK,
       CASE
           WHEN l.TYPE = 'TM' THEN (SELECT o.OBJECT_NAME
                                      FROM USER_OBJECTS o
                                     WHERE o.OBJECT_ID = l.ID1)
           ELSE NULL
       END                                          AS OBJECT_NAME
  FROM V$LOCK l
  JOIN V$SESSION s ON l.SID = s.SID
 WHERE s.USERNAME = 'SCOTT'
   AND l.TYPE IN ('TX','TM')
   -- 특정 테이블만 보고 싶다면 아래 주석 해제:
   -- AND (l.TYPE = 'TX'
   --   OR (l.TYPE = 'TM' AND EXISTS (SELECT 1 FROM USER_OBJECTS o
   --                                  WHERE o.OBJECT_ID = l.ID1
   --                                    AND o.OBJECT_NAME IN ('VISIT_SLOT','VISIT_RESERVATION'))))
 ORDER BY l.SID, l.TYPE;


-- ════════════════════════════════════════════════════════════════════════════
-- C. 누가 누구를 블록하는가 (대기-보유 관계, 양 세션의 영향 테이블 포함)
-- ════════════════════════════════════════════════════════════════════════════
-- 후착 세션이 선착의 행 X-락에서 대기 중일 때 한 줄이 잡힌다.
-- WAIT_EVENT 는 보통 'enq: TX - row lock contention'.
-- WAITING_ROW_TABLE: 대기 세션이 어느 테이블의 행에서 막혔는지 (ROW_WAIT_OBJ# 풀이).
-- HOLDING_TABLES:    보유 세션이 어떤 테이블들에 TM 락을 갖고 있는지.
SELECT w.SID                       AS WAITING_SID,
       w.USERNAME                  AS WAITING_USER,
       w.BLOCKING_SESSION          AS HOLDING_SID,
       h.USERNAME                  AS HOLDING_USER,
       w.EVENT                     AS WAIT_EVENT,
       w.SECONDS_IN_WAIT           AS WAIT_SECONDS,
       w.BLOCKING_SESSION_STATUS,
       w.SQL_ID                    AS WAITING_SQL_ID,
       (SELECT o.OBJECT_NAME FROM USER_OBJECTS o
         WHERE o.OBJECT_ID = w.ROW_WAIT_OBJ#)        AS WAITING_ROW_TABLE,
       (SELECT LISTAGG(o.OBJECT_NAME, ', ')
                      WITHIN GROUP (ORDER BY o.OBJECT_NAME)
          FROM V$LOCK l
          JOIN USER_OBJECTS o ON o.OBJECT_ID = l.ID1
         WHERE l.SID = h.SID AND l.TYPE = 'TM')      AS HOLDING_TABLES
  FROM V$SESSION w
  LEFT JOIN V$SESSION h ON w.BLOCKING_SESSION = h.SID
 WHERE w.BLOCKING_SESSION IS NOT NULL
   AND (w.USERNAME = 'SCOTT' OR h.USERNAME = 'SCOTT');


-- ════════════════════════════════════════════════════════════════════════════
-- D. 어떤 행에서 대기하고 있는가 (ROW_WAIT_* 컬럼 → 실제 객체·행 매핑)
-- ════════════════════════════════════════════════════════════════════════════
-- 후착 세션이 대기 중일 때 그 세션의 ROW_WAIT_OBJ# 가 잡혀 있다.
-- ALL_OBJECTS 사용 — DBA_OBJECTS 는 권한 문제 가능, SCOTT 환경에서 더 안전.
SELECT s.SID,
       s.USERNAME,
       s.STATUS,
       s.EVENT,
       o.OBJECT_NAME,
       o.OBJECT_TYPE,
       s.ROW_WAIT_OBJ#,
       s.ROW_WAIT_FILE#,
       s.ROW_WAIT_BLOCK#,
       s.ROW_WAIT_ROW#
  FROM V$SESSION s
  LEFT JOIN ALL_OBJECTS o ON s.ROW_WAIT_OBJ# = o.OBJECT_ID
 WHERE s.USERNAME = 'SCOTT'
   AND (s.ROW_WAIT_OBJ# > 0 OR s.BLOCKING_SESSION IS NOT NULL)
   -- 특정 테이블만: AND o.OBJECT_NAME IN ('VISIT_SLOT','VISIT_RESERVATION')
;


-- ════════════════════════════════════════════════════════════════════════════
-- D2. 객체별 잠긴 세션 (V$LOCKED_OBJECT) — 테이블 관점 폴링
-- ════════════════════════════════════════════════════════════════════════════
-- V$LOCKED_OBJECT 는 TM 락이 잡힌 객체 한 줄. "이 테이블에 어떤 세션이 락을 잡고 있는가" 에 답함.
-- B 는 "세션이 잡은 락" 시점, D2 는 "테이블이 잠긴" 시점.
SELECT o.OBJECT_NAME                              AS LOCKED_TABLE,
       o.OBJECT_TYPE,
       lo.SESSION_ID                              AS SID,
       s.SERIAL#,
       s.USERNAME,
       DECODE(lo.LOCKED_MODE,
              0,'None', 1,'Null', 2,'Row-S(SS)', 3,'Row-X(SX)',
              4,'Share', 5,'S/Row-X(SSX)', 6,'Exclusive(X)',
              TO_CHAR(lo.LOCKED_MODE))            AS LOCKED_MODE_DESC,
       s.STATUS,
       s.PROGRAM
  FROM V$LOCKED_OBJECT lo
  JOIN ALL_OBJECTS o ON lo.OBJECT_ID = o.OBJECT_ID
  JOIN V$SESSION s ON lo.SESSION_ID = s.SID
 WHERE s.USERNAME = 'SCOTT'
   -- 특정 테이블만: AND o.OBJECT_NAME IN ('VISIT_SLOT','VISIT_RESERVATION')
 ORDER BY o.OBJECT_NAME, lo.SESSION_ID;


-- ════════════════════════════════════════════════════════════════════════════
-- E. 활성 트랜잭션 (V$TRANSACTION)
-- ════════════════════════════════════════════════════════════════════════════
-- 트랜잭션이 시작되면 한 줄이 잡힌다. COMMIT / ROLLBACK 으로 사라짐.
-- 한 트랜잭션이 살아 있다는 점을 시각적으로 확인할 수 있는 가장 단순한 폴링.
SELECT t.ADDR,
       t.XIDUSN || '.' || t.XIDSLOT || '.' || t.XIDSQN  AS TXN_ID,
       t.STATUS,
       t.START_TIME,
       t.USED_UBLK   AS UNDO_BLOCKS,
       t.USED_UREC   AS UNDO_RECORDS,
       s.SID, s.SERIAL#, s.USERNAME, s.PROGRAM
  FROM V$TRANSACTION t
  JOIN V$SESSION s ON t.SES_ADDR = s.SADDR
 WHERE s.USERNAME = 'SCOTT'
 ORDER BY t.START_TIME;


-- ════════════════════════════════════════════════════════════════════════════
-- F. 현재 실행 중·직전 SQL (V$SQL)
-- ════════════════════════════════════════════════════════════════════════════
SELECT s.SID, s.STATUS, s.USERNAME, s.LAST_CALL_ET,
       sq.SQL_ID, sq.SQL_TEXT
  FROM V$SESSION s
  LEFT JOIN V$SQL sq ON s.SQL_ID = sq.SQL_ID
 WHERE s.USERNAME = 'SCOTT'
   AND s.STATUS = 'ACTIVE';


-- ════════════════════════════════════════════════════════════════════════════
-- G. 데이터 상태 확인 — 락 없이 일반 SELECT (학습정리 6.3 readers don't block writers)
-- ════════════════════════════════════════════════════════════════════════════
-- 폴링 세션이 본 쿼리를 발행해도 작업 세션은 영향받지 않는다.
SELECT SLOT_ID, WINDOW_ID, STATUS, START_TIME, END_TIME, CREATED_AT
  FROM VISIT_SLOT
 WHERE SLOT_ID = 401;

SELECT RESERVATION_ID, SLOT_ID, SEARCHER_USER_ID, STATUS, CONFIRMED_AT,
       CANCELLED_AT, INVALIDATED_AT
  FROM VISIT_RESERVATION
 WHERE SLOT_ID = 401
 ORDER BY CONFIRMED_AT;


-- ════════════════════════════════════════════════════════════════════════════
-- H. 마무리 — 관찰 종료 시 양 세션 모두에서 실행 (절대 COMMIT 하지 말 것)
-- ════════════════════════════════════════════════════════════════════════════
-- 본 시리즈의 모든 수동 관찰은 ROLLBACK 으로 끝낸다.
-- COMMIT 하면 DB 에 실제 변경이 반영되어 다음 측정 데이터가 오염된다.
ROLLBACK;


--==============================================================================
-- ▮ 시나리오 1 — 5.B (FOR UPDATE 비관적 락) 수동 재현
--==============================================================================
-- (Session 1 = "T1", Session 2 = "T2")
--
-- 1) [T1] 행 X-락 획득
--      SELECT * FROM VISIT_SLOT WHERE SLOT_ID = 401 FOR UPDATE;
--
-- 2) [폴링] § B 의 락 쿼리 실행
--      → T1 의 SID 에 TX / TM 락이 잡혀 있어야 함 (LMODE=6/3, REQUEST=0, BLOCK=0 또는 1).
--      → § E 의 V$TRANSACTION 에도 T1 트랜잭션 한 줄.
--
-- 3) [T2] 같은 행에 FOR UPDATE 시도 — 락 대기
--      SELECT * FROM VISIT_SLOT WHERE SLOT_ID = 401 FOR UPDATE;
--      → T2 의 SQL Developer 가 "Executing..." 으로 멈춤. 응답 안 돌아옴.
--
-- 4) [폴링] § B 와 § C 와 § D 실행
--      → § B: T1 의 TX 락 BLOCK=1, T2 의 TX 락 REQUEST>0 (대기).
--      → § C: WAITING_SID=T2, HOLDING_SID=T1, EVENT='enq: TX - row lock contention'.
--      → § D: T2 의 ROW_WAIT_OBJ# = VISIT_SLOT 의 OBJECT_ID.
--
-- 5) [T1] ROLLBACK 또는 COMMIT
--      ROLLBACK;
--      → T2 가 자동으로 깨어남. SELECT 가 완료되어 결과가 반환된다.
--      → T2 는 RESERVED 상태를 본다 (T1 이 COMMIT 했다면) 또는 AVAILABLE 그대로 (ROLLBACK 했다면).
--
-- 6) [T2] ROLLBACK
--      ROLLBACK;
--
-- ★ 관찰 포인트: race 차단이 SELECT 단계에서 일어난다. 학습정리 5.B 의 메커니즘.


--==============================================================================
-- ▮ 시나리오 2 — 5.E (SERIALIZABLE) 수동 재현
--==============================================================================
-- 1) [T1, T2] 격리 수준 SERIALIZABLE 로 시작 (각 세션에서)
--      SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
--
-- 2) [T1, T2] 각자 일반 SELECT — 둘 다 같은 스냅샷에서 AVAILABLE 을 본다
--      SELECT STATUS FROM VISIT_SLOT WHERE SLOT_ID = 401;
--
-- 3) [T1] UPDATE 발행 → 행 X-락 획득, T1 만 변경
--      UPDATE VISIT_SLOT SET STATUS = 'RESERVED' WHERE SLOT_ID = 401;
--
-- 4) [폴링] § B 실행
--      → T1 의 TX 락이 잡혀 있다. § E 에도 T1 트랜잭션.
--
-- 5) [T2] 같은 UPDATE 시도 — 대기
--      UPDATE VISIT_SLOT SET STATUS = 'RESERVED' WHERE SLOT_ID = 401;
--      → SQL Developer 가 "Executing..." 으로 멈춤.
--
-- 6) [폴링] § B, § C — T2 가 T1 을 대기 (학습정리 5.E 의 단계 3).
--
-- 7) [T1] COMMIT
--      COMMIT;       -- (수동 관찰에선 보통 ROLLBACK 하지만, ORA-08177 을 보려면 COMMIT 필요)
--      → T2 의 UPDATE 가 깨어나고 SCN 충돌 검출 → "ORA-08177: 직렬화할 수 없습니다" 발생.
--
-- 8) [T2] ROLLBACK
--      ROLLBACK;
--
-- 9) [T1] 변경 되돌리기 (COMMIT 했었으므로 수동 복원 필요)
--      UPDATE VISIT_SLOT SET STATUS = 'AVAILABLE' WHERE SLOT_ID = 401;
--      COMMIT;
--
-- ★ 관찰 포인트: race 차단이 UPDATE 단계에서 SCN 충돌로 일어남. 학습정리 5.E.


--==============================================================================
-- ▮ 시나리오 3 — 현재 baseline 의 N=2 재현 (제어 없음, JPA dirty-checking 효과 확인)
--==============================================================================
-- 1) [T1, T2] 같은 슬롯 UPDATE 발행 (각자 다른 시각에)
--      UPDATE VISIT_SLOT SET STATUS = 'RESERVED' WHERE SLOT_ID = 401;
--      → T2 는 대기.
--
-- 2) [T1] COMMIT
--      COMMIT;
--
-- 3) [T2] UPDATE 깨어남 — restart 후 같은 WHERE 평가 (SLOT_ID=401 그대로 참)
--      → affected = 1, 그대로 RESERVED 로 다시 덮어쓰기 성공.
--      → COMMIT 까지 가면 두 트랜잭션 모두 commit 완료, 학습정리 6.2 의 재평가 흐름 그대로.
--
-- 4) [T2] ROLLBACK
--      ROLLBACK;
--
-- ★ 관찰 포인트: WHERE 가 PK 만이면 재평가가 항상 참 → 후착 UPDATE 도 통과.
--   이게 baseline 에서 5건이 모두 commit 되는 이유. 학습정리 6.2 + § D (상태 조건부 UPDATE) 대비.
