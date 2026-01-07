/* 세션 목록 확인 */
SELECT USERNAME, PROGRAM, STATUS
FROM V$SESSION
WHERE USERNAME IS NOT NULL;

/* SGA 초기화 : 실행 계획 및 버퍼 캐시 */
ALTER System flush shared_pool;
ALTER System flush buffer_cache;

/* v$sysstat before / AFTER 측정 */
select name, value
from gv$sysstat
where name in(
    'parse count (total)',
    'parse count (hard)',
    'execute count'
    );