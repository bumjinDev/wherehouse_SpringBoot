#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
================================================================================
[TASK_1] 실시간 전수 집계 병목 분석 - 로그 파싱 및 CSV 추출 스크립트
================================================================================

목적:
    ReviewWriteService.recalculateAndUpdateReviewStatistics() 메서드에서
    출력된 성능 측정 로그를 파싱하여 CSV 파일로 추출한다.

대상 로그 패턴:
    [TASK_1][V1_FULL_SCAN][QUERY]   - 집계 쿼리 실행 시간
    [TASK_1][V1_FULL_SCAN][COMPUTE] - 애플리케이션 연산 시간 (ns 단위)
    [TASK_1][V1_FULL_SCAN][UPDATE]  - 엔티티 메모리 갱신 시간
    [TASK_1][V1_FULL_SCAN][SUMMARY] - 전체 요약 (비율 포함)

출력 파일:
    1. task1_1_query_log.csv      - QUERY 구간 상세 데이터
    2. task1_2_compute_log.csv    - COMPUTE 구간 상세 데이터
    3. task1_3_update_log.csv     - UPDATE 구간 상세 데이터
    4. task1_4_summary_log.csv    - SUMMARY 전체 요약 데이터 (주요 분석용)

사용법:
    python task1_log_parser.py <로그파일경로> [출력디렉토리]
    
    예시:
    python task1_log_parser.py wherehouse.log ./output
    python task1_log_parser.py /path/to/app.log

================================================================================
[변경 이력]
================================================================================
v2.1 - compute_time 단위를 ms → ns로 변경
     - 컬럼명을 직관적이고 구체적으로 명확화
================================================================================
"""

import re
import csv
import sys
import os
from datetime import datetime
from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict
from pathlib import Path


# =============================================================================
# 데이터 클래스 정의
# =============================================================================

@dataclass
class QueryLog:
    """[QUERY] 구간 로그 데이터"""
    timestamp: str                      # 로그 타임스탬프
    thread: str                         # 실행 스레드명
    task: str                           # 작업 식별자 (TASK_1)
    version: str                        # 로직 버전 (V1_FULL_SCAN)
    property_id: str                    # 매물 ID (Oracle CHAR(32), MD5 해시 문자열)
    scanned_review_count: int           # 스캔된 리뷰 개수 (= N, O(N) 복잡도 증명용)
    raw_avg_rating: float               # DB 반환 원시 별점 평균값
    db_query_elapsed_ms: float          # DB 집계 쿼리 실행 시간 (밀리초)


@dataclass
class ComputeLog:
    """[COMPUTE] 구간 로그 데이터"""
    timestamp: str
    thread: str
    task: str
    version: str
    property_id: str                    # 매물 ID (Oracle CHAR(32), MD5 해시 문자열)
    raw_avg_rating: float               # 반올림 전 별점 평균
    rounded_avg_rating: float           # 반올림 후 별점 평균
    compute_elapsed_ns: int             # 연산 시간 (나노초) - 정수


@dataclass
class UpdateLog:
    """[UPDATE] 구간 로그 데이터"""
    timestamp: str
    thread: str
    task: str
    version: str
    property_id: str                    # 매물 ID (Oracle CHAR(32), MD5 해시 문자열)
    updated_review_count: int           # 갱신된 리뷰 개수
    updated_avg_rating: float           # 갱신된 별점 평균
    entity_update_elapsed_ms: float     # 메모리 갱신 시간 (밀리초)


@dataclass
class SummaryLog:
    """[SUMMARY] 구간 로그 데이터 - 주요 분석용"""
    timestamp: str
    thread: str
    task: str
    version: str
    property_id: str                    # 매물 ID (Oracle CHAR(32), MD5 해시 문자열)
    scanned_review_count: int           # 스캔된 리뷰 개수 (N)
    final_avg_rating: float             # 최종 별점 평균
    method_total_elapsed_ms: float      # 전체 메서드 실행 시간
    db_query_elapsed_ms: float          # DB 쿼리 실행 시간
    db_query_ratio_percent: float       # 쿼리 비율 (%)
    compute_elapsed_ns: int             # 연산 시간 (나노초) - 정수
    compute_ratio_percent: float        # 연산 비율 (%)
    entity_update_elapsed_ms: float     # 갱신 시간
    entity_update_ratio_percent: float  # 갱신 비율 (%)


# =============================================================================
# 정규식 패턴 정의
# =============================================================================

# 기본 로그 라인 패턴: 타임스탬프 [스레드] 레벨 로거 - 메시지
BASE_PATTERN = r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\[([^\]]+)\].*?-\s+(.+)$'

# [TASK_1][VERSION][PHASE] 패턴
TASK_HEADER_PATTERN = r'\[(\w+)\]\[(\w+)\]\[(\w+)\]'

# QUERY 로그 패턴
# [TASK_1][V1_FULL_SCAN][QUERY] propertyId=xxx, scannedRows=159, rawAvg=3.245, queryTime=38.66ms
QUERY_PATTERN = r'propertyId=([^,]+),\s*scannedRows=(\d+),\s*rawAvg=([^,]+),\s*queryTime=([0-9.]+)ms'

# COMPUTE 로그 패턴 (ns 단위로 변경)
# [TASK_1][V1_FULL_SCAN][COMPUTE] propertyId=xxx, rawAvg=3.245 -> roundedAvg=3.25, computeTime=5000ns
COMPUTE_PATTERN = r'propertyId=([^,]+),\s*rawAvg=([^\s]+)\s*->\s*roundedAvg=([^,]+),\s*computeTime=(\d+)ns'

# UPDATE 로그 패턴
# [TASK_1][V1_FULL_SCAN][UPDATE] propertyId=xxx, newCount=159, newAvg=3.25, updateTime=0.02ms
UPDATE_PATTERN = r'propertyId=([^,]+),\s*newCount=(\d+),\s*newAvg=([^,]+),\s*updateTime=([0-9.]+)ms'

# SUMMARY 로그 패턴 (compute는 ns 단위로 변경)
# [TASK_1][V1_FULL_SCAN][SUMMARY] propertyId=xxx, N=159, finalAvg=3.25, total=40.01ms | query=38.66ms (96.6%) | compute=5000ns (0.1%) | update=0.02ms (0.1%)
SUMMARY_PATTERN = (
    r'propertyId=([^,]+),\s*N=(\d+),\s*finalAvg=([^,]+),\s*'
    r'total=([0-9.]+)ms\s*\|\s*'
    r'query=([0-9.]+)ms\s*\(([0-9.]+)%\)\s*\|\s*'
    r'compute=(\d+)ns\s*\(([0-9.]+)%\)\s*\|\s*'
    r'update=([0-9.]+)ms\s*\(([0-9.]+)%\)'
)


# =============================================================================
# 파서 클래스
# =============================================================================

class Task1LogParser:
    """
    TASK_1 로그 파싱 클래스
    
    Spring Boot 애플리케이션에서 출력된 성능 측정 로그를 파싱하여
    구조화된 데이터로 변환한다.
    """
    
    def __init__(self):
        self.query_logs: List[QueryLog] = []
        self.compute_logs: List[ComputeLog] = []
        self.update_logs: List[UpdateLog] = []
        self.summary_logs: List[SummaryLog] = []
        
        # 파싱 통계
        self.total_lines = 0
        self.matched_lines = 0
        self.error_lines = 0
    
    def parse_file(self, filepath: str) -> None:
        """
        로그 파일을 파싱한다.
        
        Args:
            filepath: 로그 파일 경로
        """
        print(f"\n{'='*70}")
        print(f"[TASK_1 로그 파서] 파일 분석 시작")
        print(f"{'='*70}")
        print(f"입력 파일: {filepath}")
        
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                self.total_lines += 1
                self._parse_line(line.strip())
        
        print(f"\n[파싱 완료]")
        print(f"  - 전체 라인 수: {self.total_lines:,}")
        print(f"  - TASK_1 매칭: {self.matched_lines:,}")
        print(f"  - 파싱 오류: {self.error_lines:,}")
    
    def _parse_line(self, line: str) -> None:
        """단일 로그 라인을 파싱한다."""
        
        # TASK_1 로그만 필터링
        if '[TASK_1]' not in line:
            return
        
        # 기본 로그 구조 파싱
        base_match = re.match(BASE_PATTERN, line)
        if not base_match:
            return
        
        timestamp = base_match.group(1)
        thread = base_match.group(2)
        message = base_match.group(3)
        
        # TASK/VERSION/PHASE 추출
        header_match = re.search(TASK_HEADER_PATTERN, message)
        if not header_match:
            return
        
        task = header_match.group(1)
        version = header_match.group(2)
        phase = header_match.group(3)
        
        # PHASE별 파싱
        try:
            if phase == 'QUERY':
                self._parse_query(timestamp, thread, task, version, message)
            elif phase == 'COMPUTE':
                self._parse_compute(timestamp, thread, task, version, message)
            elif phase == 'UPDATE':
                self._parse_update(timestamp, thread, task, version, message)
            elif phase == 'SUMMARY':
                self._parse_summary(timestamp, thread, task, version, message)
            
            self.matched_lines += 1
            
        except Exception as e:
            self.error_lines += 1
            print(f"[파싱 오류] {e}")
            print(f"  라인: {line[:100]}...")
    
    def _parse_query(self, timestamp: str, thread: str, task: str, version: str, message: str) -> None:
        """QUERY 로그 파싱"""
        match = re.search(QUERY_PATTERN, message)
        if match:
            log = QueryLog(
                timestamp=timestamp,
                thread=thread,
                task=task,
                version=version,
                property_id=match.group(1),
                scanned_review_count=int(match.group(2)),
                raw_avg_rating=float(match.group(3)),
                db_query_elapsed_ms=float(match.group(4))
            )
            self.query_logs.append(log)
    
    def _parse_compute(self, timestamp: str, thread: str, task: str, version: str, message: str) -> None:
        """COMPUTE 로그 파싱"""
        match = re.search(COMPUTE_PATTERN, message)
        if match:
            log = ComputeLog(
                timestamp=timestamp,
                thread=thread,
                task=task,
                version=version,
                property_id=match.group(1),
                raw_avg_rating=float(match.group(2)),
                rounded_avg_rating=float(match.group(3)),
                compute_elapsed_ns=int(match.group(4))  # 정수로 파싱 (ns)
            )
            self.compute_logs.append(log)
    
    def _parse_update(self, timestamp: str, thread: str, task: str, version: str, message: str) -> None:
        """UPDATE 로그 파싱"""
        match = re.search(UPDATE_PATTERN, message)
        if match:
            log = UpdateLog(
                timestamp=timestamp,
                thread=thread,
                task=task,
                version=version,
                property_id=match.group(1),
                updated_review_count=int(match.group(2)),
                updated_avg_rating=float(match.group(3)),
                entity_update_elapsed_ms=float(match.group(4))
            )
            self.update_logs.append(log)
    
    def _parse_summary(self, timestamp: str, thread: str, task: str, version: str, message: str) -> None:
        """SUMMARY 로그 파싱"""
        match = re.search(SUMMARY_PATTERN, message)
        if match:
            log = SummaryLog(
                timestamp=timestamp,
                thread=thread,
                task=task,
                version=version,
                property_id=match.group(1),
                scanned_review_count=int(match.group(2)),
                final_avg_rating=float(match.group(3)),
                method_total_elapsed_ms=float(match.group(4)),
                db_query_elapsed_ms=float(match.group(5)),
                db_query_ratio_percent=float(match.group(6)),
                compute_elapsed_ns=int(match.group(7)),  # 정수로 파싱 (ns)
                compute_ratio_percent=float(match.group(8)),
                entity_update_elapsed_ms=float(match.group(9)),
                entity_update_ratio_percent=float(match.group(10))
            )
            self.summary_logs.append(log)
    
    def export_to_csv(self, output_dir: str) -> Dict[str, str]:
        """
        파싱된 데이터를 CSV 파일로 내보낸다.
        
        Args:
            output_dir: 출력 디렉토리 경로
            
        Returns:
            생성된 파일 경로 딕셔너리
        """
        os.makedirs(output_dir, exist_ok=True)
        
        generated_files = {}
        
        print(f"\n{'='*70}")
        print(f"[CSV 파일 생성]")
        print(f"{'='*70}")
        print(f"출력 디렉토리: {output_dir}")
        
        # 구간 1: QUERY 로그 CSV
        if self.query_logs:
            filepath = os.path.join(output_dir, 'task1_1_query_log.csv')
            self._write_csv(filepath, self.query_logs)
            generated_files['query'] = filepath
            print(f"  ✓ task1_1_query_log.csv ({len(self.query_logs):,}건)")
        
        # 구간 2: COMPUTE 로그 CSV
        if self.compute_logs:
            filepath = os.path.join(output_dir, 'task1_2_compute_log.csv')
            self._write_csv(filepath, self.compute_logs)
            generated_files['compute'] = filepath
            print(f"  ✓ task1_2_compute_log.csv ({len(self.compute_logs):,}건)")
        
        # 구간 3: UPDATE 로그 CSV
        if self.update_logs:
            filepath = os.path.join(output_dir, 'task1_3_update_log.csv')
            self._write_csv(filepath, self.update_logs)
            generated_files['update'] = filepath
            print(f"  ✓ task1_3_update_log.csv ({len(self.update_logs):,}건)")
        
        # 구간 4: SUMMARY 로그 CSV (주요 분석용)
        if self.summary_logs:
            filepath = os.path.join(output_dir, 'task1_4_summary_log.csv')
            self._write_csv(filepath, self.summary_logs)
            generated_files['summary'] = filepath
            print(f"  ✓ task1_4_summary_log.csv ({len(self.summary_logs):,}건) [주요 분석용]")
        
        return generated_files
    
    def _write_csv(self, filepath: str, data_list: list) -> None:
        """데이터클래스 리스트를 CSV로 저장한다."""
        if not data_list:
            return
        
        with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
            # 헤더: 데이터클래스 필드명 그대로 사용
            fieldnames = list(asdict(data_list[0]).keys())
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            
            writer.writeheader()
            for item in data_list:
                writer.writerow(asdict(item))
    
    def print_statistics(self) -> None:
        """파싱 결과 통계를 출력한다."""
        
        print(f"\n{'='*70}")
        print(f"[파싱 결과 통계]")
        print(f"{'='*70}")
        
        print(f"\n▶ 추출된 로그 건수:")
        print(f"  - QUERY   : {len(self.query_logs):,}건")
        print(f"  - COMPUTE : {len(self.compute_logs):,}건")
        print(f"  - UPDATE  : {len(self.update_logs):,}건")
        print(f"  - SUMMARY : {len(self.summary_logs):,}건")
        
        # SUMMARY 로그 기반 통계 (주요 분석 대상)
        if self.summary_logs:
            print(f"\n▶ SUMMARY 로그 기반 성능 통계:")
            
            # N (스캔된 리뷰 수) 통계
            n_values = [log.scanned_review_count for log in self.summary_logs]
            print(f"  - N (스캔 리뷰 수): min={min(n_values):,}, max={max(n_values):,}, avg={sum(n_values)/len(n_values):,.1f}")
            
            # 쿼리 시간 통계
            query_times = [log.db_query_elapsed_ms for log in self.summary_logs]
            print(f"  - DB 쿼리 시간(ms): min={min(query_times):.2f}, max={max(query_times):.2f}, avg={sum(query_times)/len(query_times):.2f}")
            
            # COMPUTE 시간 통계 (ns 단위)
            compute_times = [log.compute_elapsed_ns for log in self.summary_logs]
            print(f"  - COMPUTE 시간(ns): min={min(compute_times):,}, max={max(compute_times):,}, avg={sum(compute_times)//len(compute_times):,}")
            
            # 전체 시간 통계
            total_times = [log.method_total_elapsed_ms for log in self.summary_logs]
            print(f"  - 전체 시간(ms): min={min(total_times):.2f}, max={max(total_times):.2f}, avg={sum(total_times)/len(total_times):.2f}")
            
            # 쿼리 비율 통계
            query_ratios = [log.db_query_ratio_percent for log in self.summary_logs]
            print(f"  - DB 쿼리 비율(%): min={min(query_ratios):.1f}, max={max(query_ratios):.1f}, avg={sum(query_ratios)/len(query_ratios):.1f}")
            
            # 버전별 분포
            versions = {}
            for log in self.summary_logs:
                versions[log.version] = versions.get(log.version, 0) + 1
            print(f"\n▶ 버전별 분포:")
            for ver, count in versions.items():
                print(f"  - {ver}: {count:,}건")


# =============================================================================
# 메인 실행부
# =============================================================================

def main():
    """메인 함수"""
    
    # 인자 처리
    if len(sys.argv) < 2:
        print("사용법: python task1_log_parser.py <로그파일경로> [출력디렉토리]")
        print("예시: python task1_log_parser.py wherehouse.log ./output")
        sys.exit(1)
    
    log_filepath = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else './task1_output'
    
    # 파일 존재 확인
    if not os.path.exists(log_filepath):
        print(f"[오류] 파일을 찾을 수 없습니다: {log_filepath}")
        sys.exit(1)
    
    # 파서 실행
    parser = Task1LogParser()
    parser.parse_file(log_filepath)
    parser.print_statistics()
    
    # CSV 내보내기
    generated_files = parser.export_to_csv(output_dir)
    
    print(f"\n{'='*70}")
    print(f"[완료] 총 {len(generated_files)}개 CSV 파일 생성")
    print(f"{'='*70}")
    
    # SUMMARY 파일이 주요 분석 대상임을 안내
    if 'summary' in generated_files:
        print(f"\n※ 주요 분석 파일: {generated_files['summary']}")
        print(f"   → N과 db_query_elapsed_ms의 상관관계로 O(N) 복잡도 증명 가능")


if __name__ == '__main__':
    main()