#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
p6spy/Hibernate SQL 로그 파싱 스크립트
- p6spy 메타데이터에서 실제 실행된 SQL만 추출
- 각 SQL 문을 개별 파일 또는 단일 파일로 출력
"""

import re
import sys
import os
from datetime import datetime
from typing import List, Tuple, Optional


def parse_p6spy_log_line(line: str) -> Optional[dict]:
    """
    p6spy 로그 라인을 파싱하여 구조화된 딕셔너리로 반환
    
    입력 포맷 예시:
    16:50:56.882 [http-nio-8185-exec-48] INFO  p6spy - #1765871456882 | took 69ms | statement | connection 68| url jdbc:oracle:thin:@127.0.0.1:1521:xe
    select rs1_0.PROPERTY_ID,rs1_0.AVG_RATING,...
    
    반환:
    {
        'timestamp': '16:50:56.882',
        'thread': 'http-nio-8185-exec-48',
        'p6spy_id': '1765871456882',
        'execution_time_ms': 69,
        'statement_type': 'statement',  # 'statement' | 'commit' | 'rollback' 등
        'connection_id': 68,
        'jdbc_url': 'jdbc:oracle:thin:@127.0.0.1:1521:xe',
        'sql': 'select rs1_0.PROPERTY_ID,...'  # 실제 SQL (commit/rollback의 경우 None)
    }
    """
    
    # 빈 줄 스킵
    line = line.strip()
    if not line:
        return None
    
    result = {
        'timestamp': None,
        'thread': None,
        'p6spy_id': None,
        'execution_time_ms': None,
        'statement_type': None,
        'connection_id': None,
        'jdbc_url': None,
        'sql': None,
        'raw_line': line
    }
    
    # p6spy 로그 헤더 패턴
    # 16:50:56.882 [http-nio-8185-exec-48] INFO  p6spy - #1765871456882 | took 69ms | statement | connection 68| url jdbc:...
    header_pattern = r'^(\d{2}:\d{2}:\d{2}\.\d{3})\s+\[([^\]]+)\]\s+INFO\s+p6spy\s+-\s+#(\d+)\s+\|\s+took\s+(\d+)ms\s+\|\s+(\w+)\s+\|\s+connection\s+(\d+)\|\s+url\s+([^\s]+)'
    
    header_match = re.match(header_pattern, line)
    
    if header_match:
        result['timestamp'] = header_match.group(1)
        result['thread'] = header_match.group(2)
        result['p6spy_id'] = header_match.group(3)
        result['execution_time_ms'] = int(header_match.group(4))
        result['statement_type'] = header_match.group(5)
        result['connection_id'] = int(header_match.group(6))
        result['jdbc_url'] = header_match.group(7)
        
        # 헤더 이후의 SQL 부분 추출
        # 패턴: url jdbc:... 이후 개행 또는 직접 연결된 SQL
        header_end = header_match.end()
        remaining = line[header_end:].strip()
        
        # 가끔 \r\n으로 구분되는 경우
        if '\r\n' in line:
            parts = line.split('\r\n', 1)
            if len(parts) > 1:
                remaining = parts[1].strip()
        elif '\n' in line[header_end:]:
            parts = line[header_end:].split('\n', 1)
            if len(parts) > 1:
                remaining = parts[1].strip()
        
        # statement 타입이면 SQL이 있어야 함
        if result['statement_type'] == 'statement' and remaining:
            result['sql'] = remaining
        elif result['statement_type'] in ('commit', 'rollback'):
            result['sql'] = None  # commit/rollback은 SQL 없음
            
    return result


def parse_p6spy_log_file(filepath: str) -> List[dict]:
    """
    p6spy 로그 파일 전체를 파싱
    """
    results = []
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Windows 개행 문자 처리 - 각 로그 엔트리는 실제로 한 줄
    # 하지만 SQL이 여러 줄에 걸쳐 있을 수 있음
    # p6spy 로그는 타임스탬프로 시작하므로 이를 기준으로 분리
    
    # 먼저 전체 줄로 분리
    lines = content.split('\n')
    
    current_entry = None
    
    for line in lines:
        line = line.rstrip('\r')  # Windows 개행 제거
        
        # 새로운 로그 엔트리 시작 감지 (타임스탬프로 시작)
        if re.match(r'^\d{2}:\d{2}:\d{2}\.\d{3}\s+\[', line):
            # 이전 엔트리가 있으면 파싱하여 저장
            if current_entry:
                parsed = parse_p6spy_log_line(current_entry)
                if parsed:
                    results.append(parsed)
            current_entry = line
        elif current_entry:
            # 기존 엔트리에 이어붙임 (멀티라인 SQL)
            current_entry += '\n' + line
        else:
            # 첫 줄이 타임스탬프로 시작하지 않는 경우 (SQL 계속)
            current_entry = line
    
    # 마지막 엔트리 처리
    if current_entry:
        parsed = parse_p6spy_log_line(current_entry)
        if parsed:
            results.append(parsed)
    
    return results


def extract_actual_sql(parsed_entries: List[dict]) -> List[dict]:
    """
    파싱된 엔트리 중 실제 SQL문만 추출 (commit/rollback 제외)
    """
    return [entry for entry in parsed_entries if entry.get('sql')]


def format_sql_readable(sql: str, max_in_clause_items: int = 10) -> str:
    """
    SQL을 읽기 좋게 포맷팅
    - IN 절의 ?를 축약
    - 키워드별 줄바꿈
    """
    formatted = sql
    
    # IN 절의 많은 ?를 축약
    # (?,?,?,?,?,...) -> (? x N개)
    def replace_in_clause(match):
        params = match.group(1)
        count = params.count('?')
        if count > max_in_clause_items:
            return f'(? x {count}개)'
        return match.group(0)
    
    formatted = re.sub(r'\((\?(?:,\?)+)\)', replace_in_clause, formatted)
    
    # SQL 키워드 앞에 줄바꿈 추가 (가독성)
    keywords = ['SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'JOIN', 'LEFT JOIN', 
                'RIGHT JOIN', 'INNER JOIN', 'ORDER BY', 'GROUP BY', 'HAVING']
    for kw in keywords:
        # 키워드 앞에 줄바꿈 (대소문자 무관)
        formatted = re.sub(rf'(?i)\s+({kw})\s+', rf'\n{kw} ', formatted)
    
    return formatted.strip()


def generate_report(parsed_entries: List[dict], output_path: str = None) -> str:
    """
    파싱 결과 리포트 생성
    """
    sql_entries = extract_actual_sql(parsed_entries)
    
    report_lines = []
    report_lines.append("=" * 80)
    report_lines.append("p6spy/Hibernate SQL 로그 파싱 결과")
    report_lines.append(f"생성 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report_lines.append("=" * 80)
    report_lines.append("")
    report_lines.append(f"총 로그 엔트리 수: {len(parsed_entries)}")
    report_lines.append(f"실제 SQL 문 수: {len(sql_entries)}")
    report_lines.append(f"commit/rollback 수: {len(parsed_entries) - len(sql_entries)}")
    report_lines.append("")
    
    # 각 SQL 상세
    for i, entry in enumerate(sql_entries, 1):
        report_lines.append("-" * 80)
        report_lines.append(f"[SQL #{i}]")
        report_lines.append(f"  시간: {entry['timestamp']}")
        report_lines.append(f"  스레드: {entry['thread']}")
        report_lines.append(f"  실행시간: {entry['execution_time_ms']}ms")
        report_lines.append(f"  Connection ID: {entry['connection_id']}")
        report_lines.append(f"  JDBC URL: {entry['jdbc_url']}")
        report_lines.append("")
        report_lines.append("  [원본 SQL]")
        report_lines.append(f"  {entry['sql'][:500]}..." if len(entry['sql']) > 500 else f"  {entry['sql']}")
        report_lines.append("")
        report_lines.append("  [포맷팅된 SQL (IN절 축약)]")
        formatted = format_sql_readable(entry['sql'])
        for line in formatted.split('\n'):
            report_lines.append(f"  {line}")
        report_lines.append("")
    
    report_content = '\n'.join(report_lines)
    
    if output_path:
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report_content)
        print(f"리포트 저장됨: {output_path}")
    
    return report_content


def export_sql_only(parsed_entries: List[dict], output_path: str) -> None:
    """
    SQL만 별도 파일로 추출 (원본 그대로)
    """
    sql_entries = extract_actual_sql(parsed_entries)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        for i, entry in enumerate(sql_entries, 1):
            f.write(f"-- SQL #{i} | {entry['timestamp']} | {entry['thread']} | {entry['execution_time_ms']}ms\n")
            f.write(entry['sql'])
            f.write('\n\n')
    
    print(f"SQL 추출 완료: {output_path} ({len(sql_entries)}개 SQL)")


def export_sql_formatted(parsed_entries: List[dict], output_path: str) -> None:
    """
    SQL을 포맷팅하여 별도 파일로 추출 (IN절 축약, 줄바꿈 적용)
    """
    sql_entries = extract_actual_sql(parsed_entries)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        for i, entry in enumerate(sql_entries, 1):
            f.write(f"-- SQL #{i} | {entry['timestamp']} | {entry['thread']} | {entry['execution_time_ms']}ms\n")
            formatted = format_sql_readable(entry['sql'])
            f.write(formatted)
            f.write('\n\n')
    
    print(f"포맷팅된 SQL 추출 완료: {output_path} ({len(sql_entries)}개 SQL)")


def main():
    """
    메인 실행 함수
    """
    if len(sys.argv) < 2:
        print("사용법: python parse_hibernate_sql.py <로그파일경로> [출력디렉토리]")
        print("")
        print("예시:")
        print("  python parse_hibernate_sql.py my_log.txt")
        print("  python parse_hibernate_sql.py my_log.txt ./output")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else '.'
    
    if not os.path.exists(input_file):
        print(f"오류: 파일을 찾을 수 없습니다: {input_file}")
        sys.exit(1)
    
    # 출력 디렉토리 생성
    os.makedirs(output_dir, exist_ok=True)
    
    # 파일명 기반으로 출력 파일명 생성
    base_name = os.path.splitext(os.path.basename(input_file))[0]
    
    print(f"파싱 중: {input_file}")
    print("-" * 40)
    
    # 파싱 실행
    parsed = parse_p6spy_log_file(input_file)
    
    print(f"총 파싱된 엔트리: {len(parsed)}")
    print(f"실제 SQL 문: {len(extract_actual_sql(parsed))}")
    print("-" * 40)
    
    # 출력 파일 생성
    report_path = os.path.join(output_dir, f"{base_name}_분석리포트.txt")
    sql_raw_path = os.path.join(output_dir, f"{base_name}_SQL원본.sql")
    sql_formatted_path = os.path.join(output_dir, f"{base_name}_SQL포맷팅.sql")
    
    # 리포트 생성
    generate_report(parsed, report_path)
    
    # SQL 원본 추출
    export_sql_only(parsed, sql_raw_path)
    
    # SQL 포맷팅 추출
    export_sql_formatted(parsed, sql_formatted_path)
    
    print("-" * 40)
    print("완료!")
    print(f"  - 분석 리포트: {report_path}")
    print(f"  - SQL 원본: {sql_raw_path}")
    print(f"  - SQL 포맷팅: {sql_formatted_path}")


if __name__ == '__main__':
    main()
