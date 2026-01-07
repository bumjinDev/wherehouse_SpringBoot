#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P6Spy SQL 로그 추출 스크립트
- 로그 파일에서 P6Spy가 기록한 SQL 쿼리를 추출하여 CSV 파일로 저장
- Prepared Statement(원본)와 Executed SQL(바인딩된 값) 분리 추출
"""

import re
import csv
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, List, Tuple


def extract_table_name(sql: str) -> str:
    """SQL에서 주 테이블명 추출"""
    sql_upper = sql.upper().strip()
    
    # SELECT ... FROM table
    match = re.search(r'\bFROM\s+([A-Z_][A-Z0-9_]*)', sql_upper)
    if match:
        return match.group(1)
    
    # INSERT INTO table
    match = re.search(r'\bINSERT\s+INTO\s+([A-Z_][A-Z0-9_]*)', sql_upper)
    if match:
        return match.group(1)
    
    # UPDATE table
    match = re.search(r'\bUPDATE\s+([A-Z_][A-Z0-9_]*)', sql_upper)
    if match:
        return match.group(1)
    
    # DELETE FROM table
    match = re.search(r'\bDELETE\s+FROM\s+([A-Z_][A-Z0-9_]*)', sql_upper)
    if match:
        return match.group(1)
    
    return ''


def detect_sql_type(sql: str) -> str:
    """SQL 유형 감지"""
    sql_upper = sql.upper().strip()
    
    if sql_upper.startswith('SELECT'):
        return 'SELECT'
    elif sql_upper.startswith('INSERT'):
        return 'INSERT'
    elif sql_upper.startswith('UPDATE'):
        return 'UPDATE'
    elif sql_upper.startswith('DELETE'):
        return 'DELETE'
    elif sql_upper.startswith('MERGE'):
        return 'MERGE'
    elif sql_upper.startswith('CREATE'):
        return 'DDL'
    elif sql_upper.startswith('ALTER'):
        return 'DDL'
    elif sql_upper.startswith('DROP'):
        return 'DDL'
    else:
        return 'OTHER'


def count_parameters(sql: str) -> int:
    """SQL에서 ? 파라미터 개수 카운트"""
    # 문자열 리터럴 내부의 ?는 제외해야 하지만, 
    # prepared statement에서는 일반적으로 문자열 리터럴이 없으므로 단순 카운트
    return sql.count('?')


def extract_in_clause_values(sql: str) -> Tuple[int, List[str]]:
    """
    IN 절의 값들을 추출
    Returns: (값 개수, 값 리스트)
    """
    # IN (...) 패턴 찾기
    match = re.search(r'\bIN\s*\(([^)]+)\)', sql, re.IGNORECASE)
    if not match:
        return (0, [])
    
    in_content = match.group(1)
    # 쉼표로 분리
    raw_values = [v.strip() for v in in_content.split(',')]
    # 따옴표 제거
    values = [v.strip("'\"") for v in raw_values]
    
    return (len(values), values)


def extract_bound_parameters(prepared_sql: str, executed_sql: str) -> List[str]:
    """
    Prepared SQL과 Executed SQL을 비교하여 바인딩된 파라미터 값 추출
    ? 위치에 대응하는 실제 값들을 추출
    """
    if not prepared_sql or not executed_sql:
        return []
    
    # ? 파라미터가 없으면 빈 리스트
    if '?' not in prepared_sql:
        return []
    
    # IN 절이 있는 경우 IN 절 값 추출
    in_count, in_values = extract_in_clause_values(executed_sql)
    if in_values:
        return in_values
    
    # 단일 파라미터의 경우: prepared와 executed 비교하여 차이점 추출
    # 간단한 케이스만 처리 (복잡한 경우는 executed_sql 전체 참조)
    params = []
    
    # WHERE 절에서 = ? 패턴의 값 추출
    # executed_sql에서 리터럴 값 추출 시도
    literal_pattern = re.compile(r"=\s*'([^']*)'|=\s*(\d+(?:\.\d+)?)")
    for match in literal_pattern.finditer(executed_sql):
        val = match.group(1) if match.group(1) is not None else match.group(2)
        if val:
            params.append(val)
    
    return params


def parse_p6spy_logs(input_file: str, output_csv: str) -> dict:
    """
    P6Spy 로그를 파싱하여 CSV로 저장
    
    Args:
        input_file: 입력 로그 파일 경로
        output_csv: 출력 CSV 파일 경로
    
    Returns:
        통계 정보 딕셔너리
    """
    
    # P6Spy 메타데이터 라인 패턴
    # 예: 2026-01-06 15:40:57.901 [http-nio-8185-exec-11] INFO  p6spy - #1767681657901 | took 3ms | statement | connection 1| url jdbc:p6spy:oracle:thin:@127.0.0.1:1521:xe
    meta_pattern = re.compile(
        r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+'  # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                                      # 스레드명 (그룹 2)
        r'INFO\s+p6spy\s+-\s+'                                  # p6spy 마커
        r'#(\d+)\s*\|\s*'                                       # P6Spy ID (그룹 3)
        r'took\s+(\d+)ms\s*\|\s*'                              # 실행시간 (그룹 4)
        r'(\w+)\s*\|\s*'                                        # 타입: statement/commit/rollback (그룹 5)
        r'connection\s+(\d+)\s*\|\s*'                          # 커넥션 ID (그룹 6)
        r'url\s+(.+)$'                                          # JDBC URL (그룹 7)
    )
    
    sql_logs = []
    stats = {
        'total_lines': 0,
        'p6spy_entries': 0,
        'statement_count': 0,
        'commit_count': 0,
        'rollback_count': 0,
        'select_count': 0,
        'insert_count': 0,
        'update_count': 0,
        'delete_count': 0,
        'other_count': 0
    }
    
    current_entry = None
    line_buffer = []
    
    with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()
    
    i = 0
    while i < len(lines):
        stats['total_lines'] += 1
        line = lines[i].rstrip('\r\n')
        
        # P6Spy 메타데이터 라인 확인
        meta_match = meta_pattern.match(line)
        
        if meta_match:
            # 이전 엔트리가 있으면 저장
            if current_entry:
                sql_logs.append(current_entry)
            
            stats['p6spy_entries'] += 1
            
            timestamp = meta_match.group(1)
            thread = meta_match.group(2)
            p6spy_id = meta_match.group(3)
            exec_time = meta_match.group(4)
            stmt_type = meta_match.group(5)
            conn_id = meta_match.group(6)
            jdbc_url = meta_match.group(7).strip()
            
            # 통계 업데이트
            if stmt_type == 'statement':
                stats['statement_count'] += 1
            elif stmt_type == 'commit':
                stats['commit_count'] += 1
            elif stmt_type == 'rollback':
                stats['rollback_count'] += 1
            
            current_entry = {
                'line_number': i + 1,
                'timestamp': timestamp,
                'thread': thread,
                'p6spy_id': p6spy_id,
                'execution_time_ms': exec_time,
                'statement_type': stmt_type,
                'connection_id': conn_id,
                'jdbc_url': jdbc_url,
                'prepared_sql': '',
                'executed_sql': '',
                'parameter_count': 0,
                'parameter_values': '',
                'in_clause_count': 0,
                'sql_type': '',
                'table_name': '',
                'raw_log': line
            }
            
            # statement 타입이면 다음 줄들에서 SQL 추출
            if stmt_type == 'statement':
                prepared_sql = ''
                executed_sql = ''
                
                # 다음 줄들 확인 (최대 2줄: prepared SQL, executed SQL)
                look_ahead = 1
                while i + look_ahead < len(lines) and look_ahead <= 2:
                    next_line = lines[i + look_ahead].rstrip('\r\n').strip()
                    
                    # 빈 줄이면 스킵
                    if not next_line:
                        look_ahead += 1
                        continue
                    
                    # 새로운 p6spy 엔트리면 중단
                    if meta_pattern.match(next_line):
                        break
                    
                    # HikariCP나 다른 로그면 스킵
                    if re.match(r'^\d{4}-\d{2}-\d{2}', next_line):
                        break
                    
                    # SQL 줄로 처리
                    if not prepared_sql:
                        # 첫 번째 SQL 줄 = prepared (? 파라미터 포함 가능)
                        prepared_sql = next_line
                    else:
                        # 두 번째 SQL 줄 = executed (값 바인딩됨, 세미콜론으로 끝남)
                        executed_sql = next_line.rstrip(';')
                        break
                    
                    look_ahead += 1
                
                # prepared와 executed가 동일하면 (파라미터 없는 경우)
                if prepared_sql and not executed_sql:
                    executed_sql = prepared_sql.rstrip(';')
                    prepared_sql = prepared_sql.rstrip(';')
                
                current_entry['prepared_sql'] = prepared_sql
                current_entry['executed_sql'] = executed_sql
                current_entry['parameter_count'] = count_parameters(prepared_sql)
                
                # IN 절 분석 및 파라미터 값 추출
                in_count, in_values = extract_in_clause_values(executed_sql)
                current_entry['in_clause_count'] = in_count
                
                # 파라미터 값 추출
                if in_values:
                    # IN 절 값들을 파이프(|)로 구분하여 저장
                    current_entry['parameter_values'] = '|'.join(in_values)
                else:
                    # 단일 파라미터 추출 시도
                    params = extract_bound_parameters(prepared_sql, executed_sql)
                    current_entry['parameter_values'] = '|'.join(params) if params else ''
                
                # SQL 유형 및 테이블명 추출
                sql_for_analysis = prepared_sql or executed_sql
                current_entry['sql_type'] = detect_sql_type(sql_for_analysis)
                current_entry['table_name'] = extract_table_name(sql_for_analysis)
                
                # SQL 유형별 통계
                sql_type = current_entry['sql_type']
                if sql_type == 'SELECT':
                    stats['select_count'] += 1
                elif sql_type == 'INSERT':
                    stats['insert_count'] += 1
                elif sql_type == 'UPDATE':
                    stats['update_count'] += 1
                elif sql_type == 'DELETE':
                    stats['delete_count'] += 1
                else:
                    stats['other_count'] += 1
                
                # SQL 줄들 스킵
                i += look_ahead - 1
        
        i += 1
    
    # 마지막 엔트리 저장
    if current_entry:
        sql_logs.append(current_entry)
    
    # p6spy URL 중복 제거 (jdbc:p6spy:... 와 jdbc:oracle:... 중 p6spy만)
    filtered_logs = []
    for entry in sql_logs:
        # jdbc:oracle:thin (p6spy가 아닌) URL은 중복이므로 제외
        if 'p6spy' in entry['jdbc_url'] or entry['statement_type'] != 'statement':
            filtered_logs.append(entry)
        elif entry['statement_type'] in ('commit', 'rollback') and 'p6spy' in entry['jdbc_url']:
            filtered_logs.append(entry)
    
    # CSV 파일로 저장
    fieldnames = [
        'line_number',
        'timestamp',
        'thread',
        'p6spy_id',
        'execution_time_ms',
        'statement_type',
        'connection_id',
        'sql_type',
        'table_name',
        'parameter_count',
        'in_clause_count',
        'parameter_values',
        'prepared_sql',
        'executed_sql',
        'jdbc_url',
        'raw_log'
    ]
    
    with open(output_csv, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(filtered_logs)
    
    return stats


def generate_output_filename(input_file: str, output_dir: str = None) -> str:
    """
    입력 파일명을 기반으로 출력 CSV 파일명 생성
    """
    input_path = Path(input_file)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    
    if output_dir is None:
        output_dir = Path.cwd() / 'p6spy_output'
    else:
        output_dir = Path(output_dir)
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    stem = input_path.stem
    cleaned_stem = re.sub(r'^\d+_', '', stem)
    
    if cleaned_stem:
        output_name = f'p6spy_{cleaned_stem}_{timestamp}.csv'
    else:
        output_name = f'p6spy_analysis_{timestamp}.csv'
    
    return str(output_dir / output_name)


def main():
    if len(sys.argv) >= 2:
        input_file = sys.argv[1]
    else:
        input_file = '/mnt/user-data/uploads/1767681917816_wherehouse.log'
    
    if len(sys.argv) >= 3:
        output_csv = sys.argv[2]
    else:
        output_csv = generate_output_filename(input_file)
    
    print(f'입력 파일: {input_file}')
    print(f'출력 파일: {output_csv}')
    print('-' * 60)
    
    stats = parse_p6spy_logs(input_file, output_csv)
    
    print(f'파싱 완료!')
    print(f'-' * 60)
    print(f'총 로그 라인 수: {stats["total_lines"]:,}')
    print(f'P6Spy 엔트리 수: {stats["p6spy_entries"]:,}')
    print(f'-' * 60)
    print(f'Statement 유형별:')
    print(f'  - statement: {stats["statement_count"]:,}')
    print(f'  - commit: {stats["commit_count"]:,}')
    print(f'  - rollback: {stats["rollback_count"]:,}')
    print(f'-' * 60)
    print(f'SQL 유형별:')
    print(f'  - SELECT: {stats["select_count"]:,}')
    print(f'  - INSERT: {stats["insert_count"]:,}')
    print(f'  - UPDATE: {stats["update_count"]:,}')
    print(f'  - DELETE: {stats["delete_count"]:,}')
    print(f'  - OTHER: {stats["other_count"]:,}')
    print(f'-' * 60)
    print(f'CSV 파일 저장 완료: {output_csv}')


if __name__ == '__main__':
    main()
