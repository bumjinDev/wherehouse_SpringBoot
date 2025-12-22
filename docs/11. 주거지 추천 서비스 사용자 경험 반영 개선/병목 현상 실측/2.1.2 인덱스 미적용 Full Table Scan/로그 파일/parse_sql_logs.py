#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P6Spy SQL 로그 파싱 스크립트

로그 형식:
    2025-12-17 19:20:20.044 [http-nio-8185-exec-37] INFO  p6spy - #1765966820044 | took 14ms | statement | connection 3| url jdbc:p6spy:oracle:thin:@127.0.0.1:1521:xe
    SELECT property_id FROM properties_charter WHERE apt_nm LIKE ?
    SELECT property_id FROM properties_charter WHERE apt_nm LIKE '%관악산%';

출력:
    - SQL 실행 통계 리포트
    - 쿼리별 소요시간 분석
    - 슬로우 쿼리 식별
    - CSV 내보내기
"""

import re
import sys
import os
from datetime import datetime
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, field
from statistics import mean, median, stdev
from collections import defaultdict
import csv


@dataclass
class SqlEntry:
    """단일 SQL 실행 결과"""
    timestamp: str
    thread: str
    epoch_ms: int
    took_ms: int
    operation: str  # statement, commit, rollback
    connection_id: str
    jdbc_url: str
    prepared_sql: str  # ? 파라미터 포함
    executed_sql: str  # 실제 바인딩된 SQL
    
    @property
    def sql_type(self) -> str:
        """SQL 타입 추출 (SELECT, INSERT, UPDATE, DELETE 등)"""
        sql = self.executed_sql.upper().strip()
        for keyword in ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER', 'TRUNCATE']:
            if sql.startswith(keyword):
                return keyword
        return 'OTHER'
    
    @property
    def table_names(self) -> List[str]:
        """SQL에서 테이블명 추출"""
        sql = self.executed_sql.upper()
        tables = set()
        
        # FROM 절
        from_pattern = re.findall(r'FROM\s+([A-Z_][A-Z0-9_]*)', sql)
        tables.update(from_pattern)
        
        # JOIN 절
        join_pattern = re.findall(r'JOIN\s+([A-Z_][A-Z0-9_]*)', sql)
        tables.update(join_pattern)
        
        # INSERT INTO
        insert_pattern = re.findall(r'INSERT\s+INTO\s+([A-Z_][A-Z0-9_]*)', sql)
        tables.update(insert_pattern)
        
        # UPDATE
        update_pattern = re.findall(r'UPDATE\s+([A-Z_][A-Z0-9_]*)', sql)
        tables.update(update_pattern)
        
        # DELETE FROM
        delete_pattern = re.findall(r'DELETE\s+FROM\s+([A-Z_][A-Z0-9_]*)', sql)
        tables.update(delete_pattern)
        
        return sorted(tables)
    
    @property
    def normalized_sql(self) -> str:
        """정규화된 SQL (파라미터 제거, 비교용)"""
        # 문자열 리터럴 제거
        sql = re.sub(r"'[^']*'", "'?'", self.executed_sql)
        # 숫자 리터럴 제거
        sql = re.sub(r'\b\d+\b', '?', sql)
        # 공백 정규화
        sql = ' '.join(sql.split())
        return sql.upper()


def parse_sql_logs(filepath: str, verbose: bool = False) -> List[SqlEntry]:
    """
    P6Spy 로그 파일 파싱
    
    Returns:
        List[SqlEntry]: 파싱된 SQL 엔트리 목록
    """
    
    # P6Spy 메타 로그 패턴
    # 2025-12-17 19:20:20.044 [http-nio-8185-exec-37] INFO  p6spy - #1765966820044 | took 14ms | statement | connection 3| url jdbc:...
    meta_pattern = re.compile(
        r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+'  # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                                      # 스레드명 (그룹 2)
        r'INFO\s+p6spy\s+-\s+'
        r'#(\d+)\s*\|\s*'                                       # epoch_ms (그룹 3)
        r'took\s+(\d+)ms\s*\|\s*'                               # took_ms (그룹 4)
        r'(\w+)\s*\|\s*'                                        # operation (그룹 5)
        r'connection\s+(\d+)\s*\|\s*'                           # connection_id (그룹 6)
        r'url\s+(.+)$'                                          # jdbc_url (그룹 7)
    )
    
    results: List[SqlEntry] = []
    pending_meta: Optional[dict] = None
    pending_prepared: Optional[str] = None
    
    line_count = 0
    meta_match_count = 0
    sql_entry_count = 0
    
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            line_count += 1
            line = line.rstrip('\r\n')
            
            # P6Spy 메타 라인 매칭
            meta_match = meta_pattern.match(line)
            if meta_match:
                meta_match_count += 1
                
                # 이전 pending이 있으면 처리 (commit/rollback 같은 SQL 없는 케이스)
                if pending_meta and pending_meta['operation'] in ('commit', 'rollback'):
                    entry = SqlEntry(
                        timestamp=pending_meta['timestamp'],
                        thread=pending_meta['thread'],
                        epoch_ms=pending_meta['epoch_ms'],
                        took_ms=pending_meta['took_ms'],
                        operation=pending_meta['operation'],
                        connection_id=pending_meta['connection_id'],
                        jdbc_url=pending_meta['jdbc_url'],
                        prepared_sql='',
                        executed_sql=''
                    )
                    results.append(entry)
                    sql_entry_count += 1
                
                pending_meta = {
                    'timestamp': meta_match.group(1),
                    'thread': meta_match.group(2),
                    'epoch_ms': int(meta_match.group(3)),
                    'took_ms': int(meta_match.group(4)),
                    'operation': meta_match.group(5),
                    'connection_id': meta_match.group(6),
                    'jdbc_url': meta_match.group(7).strip()
                }
                pending_prepared = None
                continue
            
            # SQL 라인 처리 (메타 라인 이후)
            if pending_meta:
                # 빈 줄이나 세미콜론만 있는 줄 무시
                stripped = line.strip()
                if not stripped or stripped == ';':
                    continue
                
                # 다른 로그 라인 시작 감지 (날짜 형식)
                if re.match(r'^\d{4}-\d{2}-\d{2}', stripped):
                    # 이전 pending 처리
                    if pending_meta['operation'] in ('commit', 'rollback'):
                        entry = SqlEntry(
                            timestamp=pending_meta['timestamp'],
                            thread=pending_meta['thread'],
                            epoch_ms=pending_meta['epoch_ms'],
                            took_ms=pending_meta['took_ms'],
                            operation=pending_meta['operation'],
                            connection_id=pending_meta['connection_id'],
                            jdbc_url=pending_meta['jdbc_url'],
                            prepared_sql='',
                            executed_sql=''
                        )
                        results.append(entry)
                        sql_entry_count += 1
                    pending_meta = None
                    pending_prepared = None
                    continue
                
                # statement 타입인 경우 SQL 처리
                if pending_meta['operation'] == 'statement':
                    if pending_prepared is None:
                        # 첫 번째 SQL 라인 = prepared statement
                        pending_prepared = stripped.rstrip(';')
                    else:
                        # 두 번째 SQL 라인 = executed statement
                        executed_sql = stripped.rstrip(';')
                        
                        entry = SqlEntry(
                            timestamp=pending_meta['timestamp'],
                            thread=pending_meta['thread'],
                            epoch_ms=pending_meta['epoch_ms'],
                            took_ms=pending_meta['took_ms'],
                            operation=pending_meta['operation'],
                            connection_id=pending_meta['connection_id'],
                            jdbc_url=pending_meta['jdbc_url'],
                            prepared_sql=pending_prepared,
                            executed_sql=executed_sql
                        )
                        results.append(entry)
                        sql_entry_count += 1
                        
                        pending_meta = None
                        pending_prepared = None
    
    if verbose:
        print(f"[파싱 결과] 총 라인: {line_count}, P6Spy 메타: {meta_match_count}, SQL 엔트리: {sql_entry_count}")
    
    return results


def calculate_statistics(entries: List[SqlEntry]) -> dict:
    """통계 계산"""
    
    # statement만 필터링 (commit/rollback 제외)
    statements = [e for e in entries if e.operation == 'statement']
    
    if not statements:
        return {}
    
    took_times = [e.took_ms for e in statements]
    
    # SQL 타입별 통계
    type_stats = defaultdict(lambda: {'count': 0, 'total_ms': 0, 'times': []})
    for e in statements:
        type_stats[e.sql_type]['count'] += 1
        type_stats[e.sql_type]['total_ms'] += e.took_ms
        type_stats[e.sql_type]['times'].append(e.took_ms)
    
    # 테이블별 통계
    table_stats = defaultdict(lambda: {'count': 0, 'total_ms': 0, 'times': []})
    for e in statements:
        for table in e.table_names:
            table_stats[table]['count'] += 1
            table_stats[table]['total_ms'] += e.took_ms
            table_stats[table]['times'].append(e.took_ms)
    
    # 정규화된 SQL별 통계 (동일 쿼리 패턴 그룹핑)
    query_stats = defaultdict(lambda: {'count': 0, 'total_ms': 0, 'times': [], 'sample': ''})
    for e in statements:
        key = e.normalized_sql[:200]  # 처음 200자로 그룹핑
        query_stats[key]['count'] += 1
        query_stats[key]['total_ms'] += e.took_ms
        query_stats[key]['times'].append(e.took_ms)
        if not query_stats[key]['sample']:
            query_stats[key]['sample'] = e.executed_sql[:500]
    
    stats = {
        'total_statements': len(statements),
        'total_commits': len([e for e in entries if e.operation == 'commit']),
        'total_rollbacks': len([e for e in entries if e.operation == 'rollback']),
        'execution_time': {
            'min': min(took_times),
            'max': max(took_times),
            'avg': mean(took_times),
            'median': median(took_times),
            'stdev': stdev(took_times) if len(took_times) > 1 else 0,
            'total': sum(took_times)
        },
        'by_type': {},
        'by_table': {},
        'by_query': {},
        'slow_queries': []
    }
    
    # SQL 타입별 집계
    for sql_type, data in type_stats.items():
        stats['by_type'][sql_type] = {
            'count': data['count'],
            'total_ms': data['total_ms'],
            'avg_ms': mean(data['times']) if data['times'] else 0,
            'max_ms': max(data['times']) if data['times'] else 0
        }
    
    # 테이블별 집계
    for table, data in sorted(table_stats.items(), key=lambda x: x[1]['total_ms'], reverse=True)[:20]:
        stats['by_table'][table] = {
            'count': data['count'],
            'total_ms': data['total_ms'],
            'avg_ms': mean(data['times']) if data['times'] else 0,
            'max_ms': max(data['times']) if data['times'] else 0
        }
    
    # 쿼리 패턴별 집계 (상위 20개)
    for key, data in sorted(query_stats.items(), key=lambda x: x[1]['total_ms'], reverse=True)[:20]:
        stats['by_query'][key] = {
            'count': data['count'],
            'total_ms': data['total_ms'],
            'avg_ms': mean(data['times']) if data['times'] else 0,
            'max_ms': max(data['times']) if data['times'] else 0,
            'sample': data['sample']
        }
    
    # 슬로우 쿼리 (상위 10개)
    slow = sorted(statements, key=lambda x: x.took_ms, reverse=True)[:10]
    stats['slow_queries'] = [
        {
            'timestamp': e.timestamp,
            'thread': e.thread,
            'took_ms': e.took_ms,
            'sql': e.executed_sql[:300],
            'tables': e.table_names
        }
        for e in slow
    ]
    
    return stats


def generate_report(entries: List[SqlEntry], stats: dict, output_path: str = None) -> str:
    """분석 리포트 생성"""
    
    lines = []
    lines.append("=" * 100)
    lines.append("P6Spy SQL 실행 분석 리포트")
    lines.append(f"생성 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("=" * 100)
    lines.append("")
    
    # 전체 요약
    lines.append("[1] 전체 요약")
    lines.append("-" * 50)
    lines.append(f"  총 SQL 실행 수: {stats['total_statements']}건")
    lines.append(f"  총 COMMIT: {stats['total_commits']}건")
    lines.append(f"  총 ROLLBACK: {stats['total_rollbacks']}건")
    lines.append("")
    
    # 실행 시간 통계
    lines.append("[2] 실행 시간 통계")
    lines.append("-" * 50)
    exec_time = stats['execution_time']
    lines.append(f"  최소: {exec_time['min']}ms")
    lines.append(f"  최대: {exec_time['max']}ms")
    lines.append(f"  평균: {exec_time['avg']:.1f}ms")
    lines.append(f"  중앙값: {exec_time['median']:.1f}ms")
    lines.append(f"  표준편차: {exec_time['stdev']:.1f}ms")
    lines.append(f"  총 소요시간: {exec_time['total']}ms ({exec_time['total']/1000:.2f}초)")
    lines.append("")
    
    # SQL 타입별 통계
    lines.append("[3] SQL 타입별 통계")
    lines.append("-" * 50)
    lines.append(f"  {'타입':<10} | {'건수':>8} | {'총시간(ms)':>12} | {'평균(ms)':>10} | {'최대(ms)':>10}")
    lines.append("  " + "-" * 60)
    for sql_type, data in sorted(stats['by_type'].items(), key=lambda x: x[1]['total_ms'], reverse=True):
        lines.append(f"  {sql_type:<10} | {data['count']:>8} | {data['total_ms']:>12} | {data['avg_ms']:>10.1f} | {data['max_ms']:>10}")
    lines.append("")
    
    # 테이블별 통계
    lines.append("[4] 테이블별 통계 (상위 20개)")
    lines.append("-" * 50)
    lines.append(f"  {'테이블명':<35} | {'건수':>6} | {'총시간(ms)':>10} | {'평균(ms)':>8}")
    lines.append("  " + "-" * 70)
    for table, data in stats['by_table'].items():
        lines.append(f"  {table:<35} | {data['count']:>6} | {data['total_ms']:>10} | {data['avg_ms']:>8.1f}")
    lines.append("")
    
    # 슬로우 쿼리
    lines.append("[5] 슬로우 쿼리 TOP 10")
    lines.append("-" * 50)
    for i, sq in enumerate(stats['slow_queries'], 1):
        lines.append(f"  [{i}] {sq['took_ms']}ms @ {sq['timestamp']}")
        lines.append(f"      Thread: {sq['thread']}")
        lines.append(f"      Tables: {', '.join(sq['tables'])}")
        lines.append(f"      SQL: {sq['sql'][:150]}...")
        lines.append("")
    
    # 쿼리 패턴별 통계
    lines.append("[6] 쿼리 패턴별 통계 (상위 10개)")
    lines.append("-" * 50)
    for i, (pattern, data) in enumerate(list(stats['by_query'].items())[:10], 1):
        lines.append(f"  [{i}] 실행횟수: {data['count']}회, 총시간: {data['total_ms']}ms, 평균: {data['avg_ms']:.1f}ms")
        lines.append(f"      Sample: {data['sample'][:120]}...")
        lines.append("")
    
    lines.append("=" * 100)
    
    report = '\n'.join(lines)
    
    if output_path:
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"리포트 저장됨: {output_path}")
    
    return report


def export_csv(entries: List[SqlEntry], output_path: str) -> None:
    """CSV 파일로 내보내기"""
    
    # statement만 필터링
    statements = [e for e in entries if e.operation == 'statement']
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        writer.writerow([
            '번호', '시각', '스레드', '소요시간(ms)', 'SQL타입', 
            '커넥션ID', '테이블', 'SQL(200자)'
        ])
        
        for i, entry in enumerate(statements, 1):
            writer.writerow([
                i,
                entry.timestamp,
                entry.thread,
                entry.took_ms,
                entry.sql_type,
                entry.connection_id,
                ', '.join(entry.table_names),
                entry.executed_sql[:200]
            ])
    
    print(f"CSV 저장됨: {output_path}")


def export_slow_queries(entries: List[SqlEntry], output_path: str, threshold_ms: int = 100) -> None:
    """슬로우 쿼리 상세 내보내기"""
    
    statements = [e for e in entries if e.operation == 'statement']
    slow = [e for e in statements if e.took_ms >= threshold_ms]
    slow = sorted(slow, key=lambda x: x.took_ms, reverse=True)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(f"슬로우 쿼리 목록 (>= {threshold_ms}ms)\n")
        f.write(f"생성 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"총 {len(slow)}건\n")
        f.write("=" * 100 + "\n\n")
        
        for i, entry in enumerate(slow, 1):
            f.write(f"[{i}] 소요시간: {entry.took_ms}ms\n")
            f.write(f"시각: {entry.timestamp}\n")
            f.write(f"스레드: {entry.thread}\n")
            f.write(f"커넥션: {entry.connection_id}\n")
            f.write(f"테이블: {', '.join(entry.table_names)}\n")
            f.write(f"Prepared SQL:\n{entry.prepared_sql}\n")
            f.write(f"Executed SQL:\n{entry.executed_sql}\n")
            f.write("-" * 100 + "\n\n")
    
    print(f"슬로우 쿼리 저장됨: {output_path} ({len(slow)}건)")


def main():
    if len(sys.argv) < 2:
        print("사용법: python parse_sql_logs.py <로그파일경로> [출력디렉토리] [슬로우쿼리임계값ms]")
        print("")
        print("예시:")
        print("  python parse_sql_logs.py wherehouse.log")
        print("  python parse_sql_logs.py wherehouse.log ./output")
        print("  python parse_sql_logs.py wherehouse.log ./output 50")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else '.'
    slow_threshold = int(sys.argv[3]) if len(sys.argv) > 3 else 100
    
    if not os.path.exists(input_file):
        print(f"오류: 파일을 찾을 수 없습니다: {input_file}")
        sys.exit(1)
    
    os.makedirs(output_dir, exist_ok=True)
    
    base_name = os.path.splitext(os.path.basename(input_file))[0]
    
    print(f"파싱 중: {input_file}")
    print("-" * 50)
    
    # 파싱 실행
    entries = parse_sql_logs(input_file, verbose=True)
    
    if not entries:
        print("경고: SQL 로그를 찾을 수 없습니다.")
        print("P6Spy 로그 형식 확인:")
        print("  INFO  p6spy - #epoch | took Xms | statement | connection N| url ...")
        sys.exit(1)
    
    print(f"파싱된 SQL 엔트리: {len(entries)}건")
    print("-" * 50)
    
    # 통계 계산
    stats = calculate_statistics(entries)
    
    if not stats:
        print("경고: statement 타입 SQL이 없습니다.")
        sys.exit(1)
    
    # 출력 파일 경로
    report_path = os.path.join(output_dir, f"{base_name}_SQL분석리포트.txt")
    csv_path = os.path.join(output_dir, f"{base_name}_SQL목록.csv")
    slow_path = os.path.join(output_dir, f"{base_name}_슬로우쿼리.txt")
    
    # 리포트 생성 및 출력
    report = generate_report(entries, stats, report_path)
    print(report)
    
    # CSV 내보내기
    export_csv(entries, csv_path)
    
    # 슬로우 쿼리 내보내기
    export_slow_queries(entries, slow_path, slow_threshold)
    
    print("-" * 50)
    print("완료!")
    print(f"  - 분석 리포트: {report_path}")
    print(f"  - SQL 목록 CSV: {csv_path}")
    print(f"  - 슬로우 쿼리: {slow_path}")


if __name__ == '__main__':
    main()
