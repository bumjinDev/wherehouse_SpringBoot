-- ============================================================
-- Wherehouse Phase 2: 리뷰 데이터 대량 생성 프로시저
-- ============================================================
-- 대상: REVIEWS (100,000+건), REVIEW_KEYWORDS (~350,000건), REVIEW_STATISTICS
-- 전제: PROPERTIES_CHARTER(58,660), PROPERTIES_MONTHLY(56,272), MEMBERTBL(2,500)
-- 실행: SQL Developer에서 스크립트 전체 실행 (F5)
-- 예상 소요: 15~30분
-- ============================================================

-- ============================================================
-- STEP 0: 기존 데이터 초기화 (필요시)
-- ============================================================
-- 이미 데이터가 있으면 아래 주석 해제 후 실행
-- DELETE FROM REVIEW_KEYWORDS;
-- DELETE FROM REVIEWS;
-- DELETE FROM REVIEW_STATISTICS;
-- COMMIT;

-- ============================================================
-- Wherehouse Phase 2: 리뷰 데이터 대량 생성 프로시저 (v3)
-- ============================================================
-- v3 수정사항:
--   1. UK 위반 시 DUP_VAL_ON_INDEX → 해당 건만 스킵
--   2. 시작 시 기존 데이터 존재하면 자동 감지/계속
--   3. 이미 리뷰 있는 매물은 분배에서 제외
-- ============================================================

CREATE OR REPLACE PROCEDURE GENERATE_PHASE2_REVIEWS
AS
    C_TOTAL_REVIEWS     CONSTANT NUMBER := 100000;

    TYPE t_property_rec IS RECORD (
        property_id   VARCHAR2(100),
        apt_nm        VARCHAR2(1000),
        district_name VARCHAR2(500)
    );
    TYPE t_property_tab IS TABLE OF t_property_rec INDEX BY PLS_INTEGER;
    TYPE t_user_tab IS TABLE OF VARCHAR2(100) INDEX BY PLS_INTEGER;

    TYPE t_keyword_rec IS RECORD (
        keyword VARCHAR2(100),
        score   NUMBER
    );
    TYPE t_keyword_tab IS TABLE OF t_keyword_rec INDEX BY PLS_INTEGER;

    TYPE t_alloc_rec IS RECORD (
        property_idx  PLS_INTEGER,
        review_count  PLS_INTEGER
    );
    TYPE t_alloc_tab IS TABLE OF t_alloc_rec INDEX BY PLS_INTEGER;
    TYPE t_num_tab IS TABLE OF PLS_INTEGER INDEX BY PLS_INTEGER;
    TYPE t_str_tab IS TABLE OF VARCHAR2(500) INDEX BY PLS_INTEGER;

    -- 이미 리뷰가 있는 매물+유저 조합 체크용
    TYPE t_exist_tab IS TABLE OF NUMBER INDEX BY VARCHAR2(200);
    v_existing_pairs   t_exist_tab;
    v_pair_key         VARCHAR2(200);

    v_properties       t_property_tab;
    v_users            t_user_tab;
    v_pos_keywords     t_keyword_tab;
    v_neg_keywords     t_keyword_tab;
    v_allocations      t_alloc_tab;

    v_prop_count       PLS_INTEGER := 0;
    v_user_count       PLS_INTEGER := 0;
    v_pos_kw_count     PLS_INTEGER := 0;
    v_neg_kw_count     PLS_INTEGER := 0;

    v_review_id        NUMBER;
    v_keyword_id       NUMBER;
    v_total_reviews    NUMBER := 0;
    v_total_keywords   NUMBER := 0;
    v_total_stats      NUMBER := 0;
    v_skip_count       NUMBER := 0;

    v_rating           NUMBER;
    v_rand             NUMBER;
    v_content          VARCHAR2(4000);
    v_created_at       TIMESTAMP;
    v_num_keywords     PLS_INTEGER;
    v_kw_idx           PLS_INTEGER;
    v_used_user_idx    PLS_INTEGER;

    v_alloc_count      PLS_INTEGER := 0;
    v_allocated_total  NUMBER := 0;
    v_review_num       PLS_INTEGER;

    v_user_indices     t_num_tab;
    v_swap_idx         PLS_INTEGER;
    v_temp_idx         PLS_INTEGER;

    v_templates_pos    t_str_tab;
    v_templates_neg    t_str_tab;
    v_templates_neu    t_str_tab;
    v_tpl_pos_cnt      PLS_INTEGER;
    v_tpl_neg_cnt      PLS_INTEGER;
    v_tpl_neu_cnt      PLS_INTEGER;

    v_batch_commit     CONSTANT NUMBER := 5000;
    v_start_ts         TIMESTAMP;

    v_remaining        NUMBER;
    v_cold_count       PLS_INTEGER := 0;
    v_cold_target      PLS_INTEGER;
    v_tpl_idx          PLS_INTEGER;

    -- 이미 리뷰 달린 매물 세트
    TYPE t_prop_set IS TABLE OF NUMBER INDEX BY VARCHAR2(100);
    v_reviewed_props   t_prop_set;
    v_existing_review_cnt NUMBER := 0;

BEGIN
    v_start_ts := SYSTIMESTAMP;
    DBMS_OUTPUT.PUT_LINE('============================================');
    DBMS_OUTPUT.PUT_LINE('Phase 2 v3: 리뷰 데이터 대량 생성 시작');
    DBMS_OUTPUT.PUT_LINE('시작: ' || TO_CHAR(v_start_ts, 'YYYY-MM-DD HH24:MI:SS.FF3'));
    DBMS_OUTPUT.PUT_LINE('============================================');

    -- ========================================================
    -- 0. 기존 데이터 감지
    -- ========================================================
    SELECT COUNT(*) INTO v_existing_review_cnt FROM REVIEWS;
    IF v_existing_review_cnt > 0 THEN
        DBMS_OUTPUT.PUT_LINE('[경고] 기존 리뷰 ' || v_existing_review_cnt || '건 감지. 기존 (PROPERTY_ID, USER_ID) 조합을 로드합니다.');
        FOR rec IN (SELECT PROPERTY_ID, USER_ID FROM REVIEWS) LOOP
            v_pair_key := rec.PROPERTY_ID || '|' || rec.USER_ID;
            v_existing_pairs(v_pair_key) := 1;
        END LOOP;
        -- 이미 리뷰 달린 매물 세트
        FOR rec IN (SELECT DISTINCT PROPERTY_ID FROM REVIEWS) LOOP
            v_reviewed_props(rec.PROPERTY_ID) := 1;
        END LOOP;
        DBMS_OUTPUT.PUT_LINE('  기존 페어 로드 완료: ' || v_existing_pairs.COUNT || '건');
    END IF;

    -- ========================================================
    -- 1. 키워드 사전
    -- ========================================================
    v_pos_kw_count := 0;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='조용';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='밝다';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='역세권';     v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='편리';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='깨끗';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='넓은';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='남향';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='채광';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='평지';       v_pos_keywords(v_pos_kw_count).score:=1;
    v_pos_kw_count:=v_pos_kw_count+1; v_pos_keywords(v_pos_kw_count).keyword:='관리비저렴'; v_pos_keywords(v_pos_kw_count).score:=1;

    v_neg_kw_count := 0;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='시끄럽';     v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='어둡';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='멀다';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='불편';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='노후';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='좁은';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='층간소음';   v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='언덕';       v_neg_keywords(v_neg_kw_count).score:=-1;
    v_neg_kw_count:=v_neg_kw_count+1; v_neg_keywords(v_neg_kw_count).keyword:='관리비비쌈'; v_neg_keywords(v_neg_kw_count).score:=-1;

    -- ========================================================
    -- 2. 리뷰 템플릿
    -- ========================================================
    v_tpl_pos_cnt := 0;
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='채광이 좋고 환기가 잘 되는 집입니다. 남향이라 겨울에도 따뜻하고 관리비도 저렴한 편이에요.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='역세권이라 출퇴근이 정말 편리합니다. 주변이 조용하고 깨끗해서 만족스러운 거주 환경이에요.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='넓은 거실과 채광 좋은 방이 마음에 듭니다. 관리비도 저렴하고 관리 상태도 깨끗합니다.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='조용한 주거 환경이 가장 큰 장점입니다. 밝은 실내와 넓은 수납공간 덕분에 생활이 편리해요.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='남향 배치라 채광이 훌륭하고 평지에 위치해서 다니기 편합니다. 깨끗하고 편리한 동네입니다.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='전반적으로 만족스러운 아파트입니다. 관리비가 저렴하고 조용하며 밝은 실내가 좋습니다.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='교통이 편리하고 주변 인프라가 잘 갖춰져 있습니다. 넓은 평수에 채광도 좋아서 만족합니다.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='깨끗한 단지 관리와 조용한 환경이 마음에 듭니다. 역세권이고 평지라서 접근성이 뛰어나요.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='실거주 만족하며 살고 있습니다. 채광 좋고 조용하며 관리비도 합리적입니다. 역세권이라 편리해요.';
    v_tpl_pos_cnt:=v_tpl_pos_cnt+1; v_templates_pos(v_tpl_pos_cnt):='넓고 밝은 거실이 가장 큰 장점이에요. 남향이라 햇빛이 잘 들어오고 주변이 깨끗하고 편리합니다.';

    v_tpl_neg_cnt := 0;
    v_tpl_neg_cnt:=v_tpl_neg_cnt+1; v_templates_neg(v_tpl_neg_cnt):='층간소음이 심해서 스트레스를 많이 받습니다. 건물도 노후되어 관리비도 비싼 편이에요.';
    v_tpl_neg_cnt:=v_tpl_neg_cnt+1; v_templates_neg(v_tpl_neg_cnt):='어둡고 좁은 방이 단점입니다. 언덕 위에 있어서 오르내리기 불편하고 시끄러운 도로변입니다.';
    v_tpl_neg_cnt:=v_tpl_neg_cnt+1; v_templates_neg(v_tpl_neg_cnt):='관리비가 비싸고 건물이 노후되었습니다. 층간소음도 심하고 주변 편의시설까지 멀어 불편합니다.';
    v_tpl_neg_cnt:=v_tpl_neg_cnt+1; v_templates_neg(v_tpl_neg_cnt):='역에서 너무 멀고 언덕길이라 출퇴근이 불편합니다. 방이 좁고 어두워서 답답한 느낌이 들어요.';
    v_tpl_neg_cnt:=v_tpl_neg_cnt+1; v_templates_neg(v_tpl_neg_cnt):='노후 건물이라 하자가 많습니다. 좁은 공간에 층간소음까지 심해서 추천하기 어렵습니다.';

    v_tpl_neu_cnt := 0;
    v_tpl_neu_cnt:=v_tpl_neu_cnt+1; v_templates_neu(v_tpl_neu_cnt):='전반적으로 무난한 아파트입니다. 가격 대비 적당한 수준이라 생각합니다. 교통은 보통입니다.';
    v_tpl_neu_cnt:=v_tpl_neu_cnt+1; v_templates_neu(v_tpl_neu_cnt):='채광은 좋은데 층간소음이 조금 있습니다. 역세권이지만 관리비가 약간 비싼 편이에요.';
    v_tpl_neu_cnt:=v_tpl_neu_cnt+1; v_templates_neu(v_tpl_neu_cnt):='위치는 편리한데 건물이 좀 오래되었습니다. 넓은 편이지만 어두운 방이 하나 있어요.';
    v_tpl_neu_cnt:=v_tpl_neu_cnt+1; v_templates_neu(v_tpl_neu_cnt):='조용한 편이지만 역에서 조금 멀어요. 깨끗하게 관리되고 있지만 좁은 베란다가 아쉽습니다.';
    v_tpl_neu_cnt:=v_tpl_neu_cnt+1; v_templates_neu(v_tpl_neu_cnt):='평지라 이동은 편한데 주변이 약간 시끄럽습니다. 밝고 깨끗한 실내는 좋지만 관리비가 나옵니다.';

    -- ========================================================
    -- 3. 매물 로드 (셔플)
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('[STEP 1] 매물 데이터 로드 중...');

    FOR rec IN (
        SELECT PROPERTY_ID, APT_NM, DISTRICT_NAME FROM PROPERTIES_CHARTER ORDER BY DBMS_RANDOM.VALUE
    ) LOOP
        v_prop_count := v_prop_count + 1;
        v_properties(v_prop_count).property_id   := rec.property_id;
        v_properties(v_prop_count).apt_nm        := rec.apt_nm;
        v_properties(v_prop_count).district_name := rec.district_name;
    END LOOP;

    FOR rec IN (
        SELECT PROPERTY_ID, APT_NM, DISTRICT_NAME FROM PROPERTIES_MONTHLY ORDER BY DBMS_RANDOM.VALUE
    ) LOOP
        v_prop_count := v_prop_count + 1;
        v_properties(v_prop_count).property_id   := rec.property_id;
        v_properties(v_prop_count).apt_nm        := rec.apt_nm;
        v_properties(v_prop_count).district_name := rec.district_name;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('  매물 로드 완료: ' || v_prop_count || '건');

    -- ========================================================
    -- 4. 유저 로드
    -- ========================================================
    FOR rec IN (SELECT ID FROM MEMBERTBL WHERE ID LIKE 'user%' ORDER BY ID) LOOP
        v_user_count := v_user_count + 1;
        v_users(v_user_count) := rec.id;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('  유저 로드 완료: ' || v_user_count || '명');

    -- ========================================================
    -- 5. 리뷰 분배
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('[STEP 2] 리뷰 분배 계획...');

    v_alloc_count := 0;

    FOR i IN 1..LEAST(2500, v_prop_count) LOOP
        v_alloc_count := v_alloc_count + 1;
        v_allocations(v_alloc_count).property_idx := i;
        v_allocations(v_alloc_count).review_count := TRUNC(DBMS_RANDOM.VALUE(8, 21));
        v_allocated_total := v_allocated_total + v_allocations(v_alloc_count).review_count;
    END LOOP;

    FOR i IN 2501..LEAST(3700, v_prop_count) LOOP
        v_alloc_count := v_alloc_count + 1;
        v_allocations(v_alloc_count).property_idx := i;
        v_allocations(v_alloc_count).review_count := TRUNC(DBMS_RANDOM.VALUE(8, 16));
        v_allocated_total := v_allocated_total + v_allocations(v_alloc_count).review_count;
    END LOOP;

    FOR i IN 3701..LEAST(6700, v_prop_count) LOOP
        v_alloc_count := v_alloc_count + 1;
        v_allocations(v_alloc_count).property_idx := i;
        v_allocations(v_alloc_count).review_count := TRUNC(DBMS_RANDOM.VALUE(5, 8));
        v_allocated_total := v_allocated_total + v_allocations(v_alloc_count).review_count;
    END LOOP;

    v_remaining := C_TOTAL_REVIEWS - v_allocated_total;
    v_cold_target := LEAST(TRUNC(v_remaining / 2), v_prop_count - 6700);

    FOR i IN 6701..LEAST(6700 + v_cold_target, v_prop_count) LOOP
        v_alloc_count := v_alloc_count + 1;
        v_allocations(v_alloc_count).property_idx := i;
        v_allocations(v_alloc_count).review_count := TRUNC(DBMS_RANDOM.VALUE(1, 4));
        v_allocated_total := v_allocated_total + v_allocations(v_alloc_count).review_count;
        v_cold_count := v_cold_count + 1;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('  Hot2=2500, Hot=1200, Warm=3000, Cold=' || v_cold_count);
    DBMS_OUTPUT.PUT_LINE('  예상 총 리뷰: ' || v_allocated_total || '건');

    -- ========================================================
    -- 6. ID 시작값
    -- ========================================================
    SELECT NVL(MAX(REVIEW_ID), 0) INTO v_review_id FROM REVIEWS;
    SELECT NVL(MAX(KEYWORD_ID), 0) INTO v_keyword_id FROM REVIEW_KEYWORDS;
    DBMS_OUTPUT.PUT_LINE('  시작 REVIEW_ID: ' || (v_review_id+1) || ', KEYWORD_ID: ' || (v_keyword_id+1));

    -- ========================================================
    -- 7. 메인 INSERT 루프
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('[STEP 3] 리뷰 INSERT 시작...');

    FOR a IN 1..v_alloc_count LOOP

        v_review_num := v_allocations(a).review_count;

        -- 유저 셔플
        FOR u IN 1..v_user_count LOOP
            v_user_indices(u) := u;
        END LOOP;
        FOR u IN 1..LEAST(v_review_num, v_user_count) LOOP
            v_swap_idx := TRUNC(DBMS_RANDOM.VALUE(u, v_user_count + 1));
            IF v_swap_idx > v_user_count THEN v_swap_idx := v_user_count; END IF;
            v_temp_idx := v_user_indices(u);
            v_user_indices(u) := v_user_indices(v_swap_idx);
            v_user_indices(v_swap_idx) := v_temp_idx;
        END LOOP;

        FOR r IN 1..LEAST(v_review_num, v_user_count) LOOP
            v_used_user_idx := v_user_indices(r);

            -- UK 중복 체크 (메모리에서 먼저 확인)
            v_pair_key := v_properties(v_allocations(a).property_idx).property_id || '|' || v_users(v_used_user_idx);
            IF v_existing_pairs.EXISTS(v_pair_key) THEN
                v_skip_count := v_skip_count + 1;
                CONTINUE;  -- 이미 존재하면 스킵
            END IF;

            v_review_id := v_review_id + 1;
            v_total_reviews := v_total_reviews + 1;

            -- 별점
            v_rand := ROUND(3.5 + DBMS_RANDOM.NORMAL * 0.9);
            IF v_rand < 1 THEN v_rand := 1; END IF;
            IF v_rand > 5 THEN v_rand := 5; END IF;
            v_rating := v_rand;

            -- 리뷰 내용
            IF v_rating >= 4 THEN
                v_tpl_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_tpl_pos_cnt) + 1;
                v_content := v_templates_pos(v_tpl_idx);
            ELSIF v_rating <= 2 THEN
                v_tpl_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_tpl_neg_cnt) + 1;
                v_content := v_templates_neg(v_tpl_idx);
            ELSE
                v_tpl_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_tpl_neu_cnt) + 1;
                v_content := v_templates_neu(v_tpl_idx);
            END IF;

            v_content := SUBSTR(NVL(v_properties(v_allocations(a).property_idx).apt_nm, '아파트'), 1, 50) || ' ' ||
                         SUBSTR(NVL(v_properties(v_allocations(a).property_idx).district_name, '서울'), 1, 20) || ' 거주 후기. ' ||
                         v_content;

            v_created_at := SYSTIMESTAMP - DBMS_RANDOM.VALUE(1, 365);

            -- INSERT (UK 위반 방어)
            BEGIN
                INSERT INTO REVIEWS (REVIEW_ID, PROPERTY_ID, USER_ID, RATING, CONTENT, CREATED_AT, UPDATED_AT)
                VALUES (
                    v_review_id,
                    v_properties(v_allocations(a).property_idx).property_id,
                    v_users(v_used_user_idx),
                    v_rating,
                    TO_CLOB(v_content),
                    v_created_at,
                    NULL
                );

                -- 성공 시 페어 등록
                v_existing_pairs(v_pair_key) := 1;

                -- 키워드 (2~5개)
                v_num_keywords := TRUNC(DBMS_RANDOM.VALUE(2, 6));

                FOR k IN 1..v_num_keywords LOOP
                    v_keyword_id := v_keyword_id + 1;
                    v_total_keywords := v_total_keywords + 1;

                    IF v_rating >= 4 THEN
                        IF DBMS_RANDOM.VALUE < 0.8 THEN
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_pos_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_pos_keywords(v_kw_idx).keyword, v_pos_keywords(v_kw_idx).score);
                        ELSE
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_neg_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_neg_keywords(v_kw_idx).keyword, v_neg_keywords(v_kw_idx).score);
                        END IF;
                    ELSIF v_rating <= 2 THEN
                        IF DBMS_RANDOM.VALUE < 0.8 THEN
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_neg_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_neg_keywords(v_kw_idx).keyword, v_neg_keywords(v_kw_idx).score);
                        ELSE
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_pos_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_pos_keywords(v_kw_idx).keyword, v_pos_keywords(v_kw_idx).score);
                        END IF;
                    ELSE
                        IF DBMS_RANDOM.VALUE < 0.5 THEN
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_pos_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_pos_keywords(v_kw_idx).keyword, v_pos_keywords(v_kw_idx).score);
                        ELSE
                            v_kw_idx := MOD(ABS(DBMS_RANDOM.RANDOM), v_neg_kw_count) + 1;
                            INSERT INTO REVIEW_KEYWORDS (KEYWORD_ID, REVIEW_ID, KEYWORD, SCORE)
                            VALUES (v_keyword_id, v_review_id, v_neg_keywords(v_kw_idx).keyword, v_neg_keywords(v_kw_idx).score);
                        END IF;
                    END IF;
                END LOOP;

            EXCEPTION
                WHEN DUP_VAL_ON_INDEX THEN
                    v_skip_count := v_skip_count + 1;
                    v_total_reviews := v_total_reviews - 1;  -- 카운터 복구
            END;

            -- 배치 커밋
            IF MOD(v_total_reviews, v_batch_commit) = 0 AND v_total_reviews > 0 THEN
                COMMIT;
                DBMS_OUTPUT.PUT_LINE('  진행: ' || v_total_reviews || ' 리뷰 / ' || v_total_keywords || ' 키워드 / 스킵: ' || v_skip_count || ' (' || TO_CHAR(SYSTIMESTAMP, 'HH24:MI:SS') || ')');
            END IF;

        END LOOP;
    END LOOP;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('[STEP 3] 완료 - 리뷰: ' || v_total_reviews || ', 키워드: ' || v_total_keywords || ', 스킵: ' || v_skip_count);

    -- ========================================================
    -- 8. REVIEW_STATISTICS 집계
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('[STEP 4] REVIEW_STATISTICS 집계 중...');

    -- 기존 통계 삭제 후 재생성
    DELETE FROM REVIEW_STATISTICS;

    INSERT INTO REVIEW_STATISTICS (
        PROPERTY_ID, REVIEW_COUNT, AVG_RATING,
        POSITIVE_KEYWORD_COUNT, NEGATIVE_KEYWORD_COUNT, LAST_CALCED
    )
    SELECT
        r.PROPERTY_ID,
        COUNT(DISTINCT r.REVIEW_ID),
        ROUND(AVG(r.RATING), 2),
        NVL(SUM(CASE WHEN rk.SCORE > 0 THEN 1 ELSE 0 END), 0),
        NVL(SUM(CASE WHEN rk.SCORE < 0 THEN 1 ELSE 0 END), 0),
        SYSTIMESTAMP
    FROM REVIEWS r
    LEFT JOIN REVIEW_KEYWORDS rk ON r.REVIEW_ID = rk.REVIEW_ID
    GROUP BY r.PROPERTY_ID;

    v_total_stats := SQL%ROWCOUNT;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('[STEP 4] 완료 - REVIEW_STATISTICS: ' || v_total_stats || '건');

    -- ========================================================
    -- 9. 리포트
    -- ========================================================
    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('============================================');
    DBMS_OUTPUT.PUT_LINE('Phase 2 v3 완료 리포트');
    DBMS_OUTPUT.PUT_LINE('============================================');
    DBMS_OUTPUT.PUT_LINE('종료: ' || TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.FF3'));
    DBMS_OUTPUT.PUT_LINE('REVIEWS:           ' || v_total_reviews || '건');
    DBMS_OUTPUT.PUT_LINE('REVIEW_KEYWORDS:   ' || v_total_keywords || '건');
    DBMS_OUTPUT.PUT_LINE('REVIEW_STATISTICS: ' || v_total_stats || '건');
    DBMS_OUTPUT.PUT_LINE('스킵 (UK 중복):    ' || v_skip_count || '건');
    DBMS_OUTPUT.PUT_LINE('리뷰당 평균 키워드: ' || ROUND(v_total_keywords / NULLIF(v_total_reviews, 0), 2));
    DBMS_OUTPUT.PUT_LINE('최종 REVIEW_ID:    ' || v_review_id);
    DBMS_OUTPUT.PUT_LINE('최종 KEYWORD_ID:   ' || v_keyword_id);
    DBMS_OUTPUT.PUT_LINE('============================================');

EXCEPTION
    WHEN OTHERS THEN
        COMMIT;  -- 지금까지 커밋된 건은 보존
        DBMS_OUTPUT.PUT_LINE('!!! 오류 (커밋된 데이터는 보존됨) !!!');
        DBMS_OUTPUT.PUT_LINE('SQLCODE: ' || SQLCODE);
        DBMS_OUTPUT.PUT_LINE('SQLERRM: ' || SQLERRM);
        DBMS_OUTPUT.PUT_LINE('리뷰 진행: ' || v_total_reviews || ', 키워드: ' || v_total_keywords);
        DBMS_OUTPUT.PUT_LINE('BACKTRACE: ' || DBMS_UTILITY.FORMAT_ERROR_BACKTRACE);
        RAISE;
END GENERATE_PHASE2_REVIEWS;
/



-- ============================================================
-- STEP 2: 프로시저 실행
-- ============================================================
-- DBMS_OUTPUT 버퍼 설정 (필수)
SET SERVEROUTPUT ON SIZE UNLIMITED;

BEGIN
    GENERATE_PHASE2_REVIEWS;
END;
/


-- ============================================================
-- STEP 3: 결과 검증 쿼리
-- ============================================================
-- 3-1. 테이블별 건수
SELECT 'REVIEWS' AS TBL, COUNT(*) AS CNT FROM REVIEWS
UNION ALL
SELECT 'REVIEW_KEYWORDS', COUNT(*) FROM REVIEW_KEYWORDS
UNION ALL
SELECT 'REVIEW_STATISTICS', COUNT(*) FROM REVIEW_STATISTICS;

-- 3-2. 리뷰 분포 확인 (Hot/Warm/Cold)
SELECT 
    CASE 
        WHEN review_count >= 8  THEN 'HOT (8+)'
        WHEN review_count >= 5  THEN 'WARM (5-7)'
        WHEN review_count >= 1  THEN 'COLD (1-4)'
    END AS category,
    COUNT(*) AS property_count,
    SUM(review_count) AS total_reviews,
    ROUND(AVG(avg_rating), 2) AS avg_of_avg_rating
FROM REVIEW_STATISTICS
GROUP BY 
    CASE 
        WHEN review_count >= 8  THEN 'HOT (8+)'
        WHEN review_count >= 5  THEN 'WARM (5-7)'
        WHEN review_count >= 1  THEN 'COLD (1-4)'
    END
ORDER BY 1;

-- 3-3. 별점 분포
SELECT RATING, COUNT(*) AS CNT, 
       ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM REVIEWS), 1) AS PCT
FROM REVIEWS
GROUP BY RATING
ORDER BY RATING;

-- 3-4. 키워드 TOP 10
SELECT KEYWORD, SCORE, COUNT(*) AS CNT
FROM REVIEW_KEYWORDS
GROUP BY KEYWORD, SCORE
ORDER BY CNT DESC
FETCH FIRST 10 ROWS ONLY;

-- 3-5. 유저 활동 분포
SELECT 
    CASE 
        WHEN cnt >= 80  THEN 'HIGH (80+)'
        WHEN cnt >= 5   THEN 'MID (5-79)'
        WHEN cnt >= 1   THEN 'LOW (1-4)'
    END AS activity,
    COUNT(*) AS user_count,
    SUM(cnt) AS total_reviews
FROM (SELECT USER_ID, COUNT(*) AS cnt FROM REVIEWS GROUP BY USER_ID)
GROUP BY 
    CASE 
        WHEN cnt >= 80  THEN 'HIGH (80+)'
        WHEN cnt >= 5   THEN 'MID (5-79)'
        WHEN cnt >= 1   THEN 'LOW (1-4)'
    END
ORDER BY 1;


-- ============================================================
-- STEP 4: 시퀀스 동기화 (반드시 실행)
-- ============================================================
-- 아래는 STEP 3 결과 확인 후 수동 실행

-- REVIEW_ID 시퀀스 동기화
-- SELECT MAX(REVIEW_ID) FROM REVIEWS;  -- 이 값을 확인 후
-- ALTER SEQUENCE SEQ_REVIEW_ID INCREMENT BY {확인된_MAX값};
-- SELECT SEQ_REVIEW_ID.NEXTVAL FROM DUAL;
-- ALTER SEQUENCE SEQ_REVIEW_ID INCREMENT BY 1;

-- KEYWORD_ID 시퀀스 동기화
-- SELECT MAX(KEYWORD_ID) FROM REVIEW_KEYWORDS;  -- 이 값을 확인 후
-- ALTER SEQUENCE SEQ_KEYWORD_ID INCREMENT BY {확인된_MAX값};
-- SELECT SEQ_KEYWORD_ID.NEXTVAL FROM DUAL;
-- ALTER SEQUENCE SEQ_KEYWORD_ID INCREMENT BY 1;
