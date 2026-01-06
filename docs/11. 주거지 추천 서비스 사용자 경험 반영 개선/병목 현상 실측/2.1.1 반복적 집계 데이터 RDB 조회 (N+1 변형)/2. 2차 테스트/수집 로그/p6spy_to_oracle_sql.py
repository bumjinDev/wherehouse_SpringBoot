#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P6Spy CSV 로그 → Oracle 실행 가능 SQL 변환기

이 스크립트는 P6Spy가 캡처한 CSV 로그 파일을 읽어서
Oracle SQL*Plus 또는 SQL Developer에서 직접 실행 가능한 형태로 변환한다.

출력:
1. oracle_queries.csv - 메타데이터와 함께 정렬된 SQL 목록
2. oracle_queries.sql - 즉시 실행 가능한 SQL 스크립트 파일
"""

import csv
import argparse
import sys
from pathlib import Path
from typing import List, Dict, Optional
from dataclasses import dataclass
from collections import OrderedDict


@dataclass
class SqlRecord:
    """단일 SQL 레코드를 표현하는 데이터 클래스"""
    line_number: int
    timestamp: str
    thread: str
    execution_time_ms: int
    statement_type: str
    connection_id: str
    sql_type: str
    table_name: str
    parameter_count: int
    in_clause_count: int
    prepared_sql: str
    executed_sql: str
    
    def to_oracle_sql(self) -> str:
        """Oracle 실행 가능한 SQL 문자열로 변환"""
        sql = self.executed_sql.strip()
        if sql and not sql.endswith(';'):
            sql += ';'
        return sql


class P6SpyToOracleConverter:
    """P6Spy CSV 로그를 Oracle SQL로 변환하는 메인 클래스"""
    
    # P6Spy CSV 컬럼 매핑 (0-indexed)
    COL_LINE_NUMBER = 0
    COL_TIMESTAMP = 1
    COL_THREAD = 2
    COL_P6SPY_ID = 3
    COL_EXECUTION_TIME_MS = 4
    COL_STATEMENT_TYPE = 5
    COL_CONNECTION_ID = 6
    COL_SQL_TYPE = 7
    COL_TABLE_NAME = 8
    COL_PARAMETER_COUNT = 9
    COL_IN_CLAUSE_COUNT = 10
    COL_PARAMETER_VALUES = 11
    COL_PREPARED_SQL = 12
    COL_EXECUTED_SQL = 13
    
    def __init__(self, input_path: str, output_dir: Optional[str] = None):
        self.input_path = Path(input_path)
        self.output_dir = Path(output_dir) if output_dir else self.input_path.parent
        self.records: List[SqlRecord] = []
        self.stats = {
            'total_rows': 0,
            'sql_statements': 0,
            'commits': 0,
            'other': 0,
            'unique_sqls': 0,
            'by_sql_type': {},
            'by_table': {}
        }
    
    def parse_csv(self) -> None:
        """CSV 파일을 파싱하여 SqlRecord 리스트로 변환"""
        print(f"[INFO] CSV 파일 읽는 중: {self.input_path}")
        
        with open(self.input_path, 'r', encoding='utf-8-sig') as f:
            reader = csv.reader(f)
            header = next(reader)  # 헤더 스킵
            
            for row in reader:
                self.stats['total_rows'] += 1
                
                if len(row) < 14:
                    continue
                
                statement_type = row[self.COL_STATEMENT_TYPE].strip().lower()
                executed_sql = row[self.COL_EXECUTED_SQL].strip() if len(row) > self.COL_EXECUTED_SQL else ''
                
                # commit, rollback 등 SQL이 없는 statement는 통계만 기록
                if statement_type == 'commit':
                    self.stats['commits'] += 1
                    continue
                elif not executed_sql:
                    self.stats['other'] += 1
                    continue
                
                self.stats['sql_statements'] += 1
                
                # SQL 타입별 통계
                sql_type = row[self.COL_SQL_TYPE].strip().upper()
                self.stats['by_sql_type'][sql_type] = self.stats['by_sql_type'].get(sql_type, 0) + 1
                
                # 테이블별 통계
                table_name = row[self.COL_TABLE_NAME].strip()
                if table_name:
                    self.stats['by_table'][table_name] = self.stats['by_table'].get(table_name, 0) + 1
                
                record = SqlRecord(
                    line_number=int(row[self.COL_LINE_NUMBER]) if row[self.COL_LINE_NUMBER].isdigit() else 0,
                    timestamp=row[self.COL_TIMESTAMP],
                    thread=row[self.COL_THREAD],
                    execution_time_ms=int(row[self.COL_EXECUTION_TIME_MS]) if row[self.COL_EXECUTION_TIME_MS].isdigit() else 0,
                    statement_type=statement_type,
                    connection_id=row[self.COL_CONNECTION_ID],
                    sql_type=sql_type,
                    table_name=table_name,
                    parameter_count=int(row[self.COL_PARAMETER_COUNT]) if row[self.COL_PARAMETER_COUNT].isdigit() else 0,
                    in_clause_count=int(row[self.COL_IN_CLAUSE_COUNT]) if row[self.COL_IN_CLAUSE_COUNT].isdigit() else 0,
                    prepared_sql=row[self.COL_PREPARED_SQL] if len(row) > self.COL_PREPARED_SQL else '',
                    executed_sql=executed_sql
                )
                self.records.append(record)
        
        print(f"[INFO] 파싱 완료: {len(self.records)}개 SQL 문 추출")
    
    def export_to_csv(self, include_duplicates: bool = True, dedupe_key: str = 'executed_sql') -> str:
        """Oracle SQL을 CSV 파일로 내보내기
        
        Args:
            include_duplicates: True면 모든 SQL 포함, False면 중복 제거
            dedupe_key: 중복 제거 기준 ('executed_sql' 또는 'prepared_sql')
        
        Returns:
            생성된 CSV 파일 경로
        """
        output_csv = self.output_dir / f"{self.input_path.stem}_oracle_queries.csv"
        
        records_to_export = self.records
        if not include_duplicates:
            seen = OrderedDict()
            for r in self.records:
                key = r.executed_sql if dedupe_key == 'executed_sql' else r.prepared_sql
                if key not in seen:
                    seen[key] = r
            records_to_export = list(seen.values())
            self.stats['unique_sqls'] = len(records_to_export)
        
        print(f"[INFO] CSV 내보내기: {len(records_to_export)}개 SQL")
        
        with open(output_csv, 'w', encoding='utf-8', newline='') as f:
            writer = csv.writer(f)
            
            # 헤더 작성
            writer.writerow([
                'seq',
                'original_line',
                'timestamp',
                'thread',
                'execution_time_ms',
                'sql_type',
                'table_name',
                'parameter_count',
                'in_clause_count',
                'oracle_sql'
            ])
            
            # 데이터 작성
            for idx, record in enumerate(records_to_export, 1):
                writer.writerow([
                    idx,
                    record.line_number,
                    record.timestamp,
                    record.thread,
                    record.execution_time_ms,
                    record.sql_type,
                    record.table_name,
                    record.parameter_count,
                    record.in_clause_count,
                    record.to_oracle_sql()
                ])
        
        print(f"[OK] CSV 저장 완료: {output_csv}")
        return str(output_csv)
    
    def export_to_sql_script(self, include_duplicates: bool = True, 
                             add_timing: bool = True,
                             add_comments: bool = True) -> str:
        """Oracle SQL*Plus 실행 가능한 .sql 스크립트 파일로 내보내기
        
        Args:
            include_duplicates: True면 모든 SQL 포함, False면 중복 제거
            add_timing: SET TIMING ON 포함 여부
            add_comments: 각 SQL 앞에 메타데이터 주석 추가 여부
        
        Returns:
            생성된 SQL 파일 경로
        """
        output_sql = self.output_dir / f"{self.input_path.stem}_oracle_queries.sql"
        
        records_to_export = self.records
        if not include_duplicates:
            seen = OrderedDict()
            for r in self.records:
                if r.executed_sql not in seen:
                    seen[r.executed_sql] = r
            records_to_export = list(seen.values())
        
        print(f"[INFO] SQL 스크립트 내보내기: {len(records_to_export)}개 SQL")
        
        with open(output_sql, 'w', encoding='utf-8') as f:
            # SQL*Plus 헤더
            f.write("-- ============================================================\n")
            f.write(f"-- P6Spy 로그에서 추출한 Oracle SQL 스크립트\n")
            f.write(f"-- 원본 파일: {self.input_path.name}\n")
            f.write(f"-- 생성 시각: {self.records[0].timestamp if self.records else 'N/A'}\n")
            f.write(f"-- 총 SQL 수: {len(records_to_export)}\n")
            f.write("-- ============================================================\n\n")
            
            if add_timing:
                f.write("SET TIMING ON;\n")
                f.write("SET SERVEROUTPUT ON;\n")
                f.write("SET LINESIZE 200;\n")
                f.write("SET PAGESIZE 50000;\n\n")
            
            # 각 SQL 작성
            for idx, record in enumerate(records_to_export, 1):
                if add_comments:
                    f.write(f"-- [{idx}] Line: {record.line_number} | ")
                    f.write(f"Time: {record.timestamp} | ")
                    f.write(f"Thread: {record.thread} | ")
                    f.write(f"Exec: {record.execution_time_ms}ms | ")
                    f.write(f"Table: {record.table_name} | ")
                    f.write(f"IN Count: {record.in_clause_count}\n")
                
                f.write(record.to_oracle_sql())
                f.write("\n\n")
            
            # 푸터
            f.write("-- ============================================================\n")
            f.write("-- END OF SCRIPT\n")
            f.write("-- ============================================================\n")
        
        print(f"[OK] SQL 스크립트 저장 완료: {output_sql}")
        return str(output_sql)
    
    def print_stats(self) -> None:
        """파싱 통계 출력"""
        print("\n" + "=" * 60)
        print("파싱 통계")
        print("=" * 60)
        print(f"총 로그 행 수:      {self.stats['total_rows']}")
        print(f"SQL 문장 수:        {self.stats['sql_statements']}")
        print(f"COMMIT 수:          {self.stats['commits']}")
        print(f"기타 (SQL 없음):    {self.stats['other']}")
        
        if self.stats['by_sql_type']:
            print("\n[SQL 타입별 분포]")
            for sql_type, count in sorted(self.stats['by_sql_type'].items(), 
                                         key=lambda x: -x[1]):
                print(f"  {sql_type:15s}: {count:5d}")
        
        if self.stats['by_table']:
            print("\n[테이블별 분포]")
            for table, count in sorted(self.stats['by_table'].items(), 
                                       key=lambda x: -x[1])[:10]:  # 상위 10개만
                print(f"  {table:30s}: {count:5d}")
        
        print("=" * 60 + "\n")


def main():
    parser = argparse.ArgumentParser(
        description='P6Spy CSV 로그를 Oracle 실행 가능한 SQL로 변환',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예제:
  python p6spy_to_oracle_sql.py input.csv
  python p6spy_to_oracle_sql.py input.csv --no-duplicates
  python p6spy_to_oracle_sql.py input.csv --output-dir ./output --no-timing
        """
    )
    
    parser.add_argument('input_csv', 
                        help='입력 P6Spy CSV 파일 경로')
    parser.add_argument('--output-dir', '-o', 
                        help='출력 디렉토리 (기본: 입력 파일과 같은 디렉토리)')
    parser.add_argument('--no-duplicates', '-d', 
                        action='store_true',
                        help='중복 SQL 제거 (executed_sql 기준)')
    parser.add_argument('--dedupe-by-prepared', 
                        action='store_true',
                        help='중복 제거 시 prepared_sql 기준으로 (기본: executed_sql)')
    parser.add_argument('--no-timing', 
                        action='store_true',
                        help='SQL 스크립트에서 SET TIMING ON 제외')
    parser.add_argument('--no-comments', 
                        action='store_true',
                        help='SQL 스크립트에서 메타데이터 주석 제외')
    parser.add_argument('--csv-only', 
                        action='store_true',
                        help='CSV만 생성 (.sql 파일 생성 안 함)')
    parser.add_argument('--sql-only', 
                        action='store_true',
                        help='.sql 파일만 생성 (CSV 생성 안 함)')
    
    args = parser.parse_args()
    
    # 입력 파일 검증
    input_path = Path(args.input_csv)
    if not input_path.exists():
        print(f"[ERROR] 입력 파일을 찾을 수 없음: {input_path}")
        sys.exit(1)
    
    # 출력 디렉토리 생성
    if args.output_dir:
        output_dir = Path(args.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
    else:
        output_dir = None
    
    # 변환 실행
    converter = P6SpyToOracleConverter(str(input_path), str(output_dir) if output_dir else None)
    converter.parse_csv()
    converter.print_stats()
    
    include_duplicates = not args.no_duplicates
    dedupe_key = 'prepared_sql' if args.dedupe_by_prepared else 'executed_sql'
    
    generated_files = []
    
    if not args.sql_only:
        csv_path = converter.export_to_csv(
            include_duplicates=include_duplicates,
            dedupe_key=dedupe_key
        )
        generated_files.append(csv_path)
    
    if not args.csv_only:
        sql_path = converter.export_to_sql_script(
            include_duplicates=include_duplicates,
            add_timing=not args.no_timing,
            add_comments=not args.no_comments
        )
        generated_files.append(sql_path)
    
    print("\n[완료] 생성된 파일:")
    for f in generated_files:
        print(f"  - {f}")


if __name__ == '__main__':
    main()
