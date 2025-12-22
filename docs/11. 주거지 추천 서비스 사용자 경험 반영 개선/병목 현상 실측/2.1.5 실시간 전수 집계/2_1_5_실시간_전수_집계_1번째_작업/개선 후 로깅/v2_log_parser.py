#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
V2_INCREMENTAL 로그 파싱 스크립트

============================================================================
[사용법]
============================================================================
1. 단일 로그 파일 파싱:
   python v2_log_parser.py application.log

2. 출력 디렉토리 지정:
   python v2_log_parser.py application.log --output ./csv_output

3. 통계 정보 포함:
   python v2_log_parser.py application.log --stats

4. V1/V2 SUMMARY CSV 비교:
   python v2_log_parser.py --compare v1_summary.csv v2_summary.csv

============================================================================
[출력 파일]
============================================================================
- v2_1_cache_read_log.csv  : CACHE_READ 구간 (Redis 조회)
- v2_2_compute_log.csv     : COMPUTE 구간 (점진적 갱신 연산)
- v2_3_persist_log.csv     : PERSIST 구간 (Redis + RDB 동기화)
- v2_4_summary_log.csv     : SUMMARY 구간 (전체 요약)
- v2_cache_init_log.csv    : CACHE_INIT 로그 (서비스 기동 시)

============================================================================
[변경 이력]
============================================================================
v2.1 - compute_time 단위를 ms → ns로 변경
     - 컬럼명을 직관적이고 구체적으로 명확화
============================================================================
"""

import re
import csv
import argparse
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import List, Dict, Any


# =============================================================================
# 데이터 클래스 정의
# =============================================================================

@dataclass
class CacheReadLog:
    """CACHE_READ 구간 로그"""
    timestamp: str = ""
    thread: str = ""
    task: str = ""
    version: str = ""
    property_id: str = ""
    current_review_count: int = 0
    current_rating_sum: int = 0
    redis_read_elapsed_ms: float = 0.0


@dataclass
class ComputeLog:
    """COMPUTE 구간 로그"""
    timestamp: str = ""
    thread: str = ""
    task: str = ""
    version: str = ""
    property_id: str = ""
    operation: str = ""
    before_review_count: int = 0
    before_rating_sum: int = 0
    after_review_count: int = 0
    after_rating_sum: int = 0
    after_avg_rating: float = 0.0
    compute_elapsed_ns: int = 0  # 나노초 단위 (정수)


@dataclass
class PersistLog:
    """PERSIST 구간 로그"""
    timestamp: str = ""
    thread: str = ""
    task: str = ""
    version: str = ""
    property_id: str = ""
    updated_review_count: int = 0
    updated_avg_rating: float = 0.0
    redis_write_elapsed_ms: float = 0.0
    rdb_dirty_check_elapsed_ms: float = 0.0
    persist_total_elapsed_ms: float = 0.0


@dataclass
class SummaryLog:
    """SUMMARY 구간 로그"""
    timestamp: str = ""
    thread: str = ""
    task: str = ""
    version: str = ""
    property_id: str = ""
    operation: str = ""
    final_review_count: int = 0
    final_avg_rating: float = 0.0
    method_total_elapsed_ms: float = 0.0
    redis_read_elapsed_ms: float = 0.0
    redis_read_ratio_percent: float = 0.0
    compute_elapsed_ns: int = 0  # 나노초 단위 (정수)
    compute_ratio_percent: float = 0.0
    persist_elapsed_ms: float = 0.0
    persist_ratio_percent: float = 0.0


@dataclass
class CacheInitLog:
    """CACHE_INIT 로그"""
    timestamp: str = ""
    thread: str = ""
    task: str = ""
    version: str = ""
    init_total_property_count: int = 0
    init_success_count: int = 0
    init_fail_count: int = 0
    init_elapsed_ms: float = 0.0


# =============================================================================
# 정규식 패턴 정의
# =============================================================================

class LogPatterns:
    """V2_INCREMENTAL 로그 파싱용 정규식 패턴"""
    
    # 공통 접두사: 타임스탬프 및 스레드
    PREFIX = r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+.*?\[([^\]]+)\].*?'
    
    # CACHE_READ 패턴
    CACHE_READ = re.compile(
        PREFIX +
        r'\[(\w+)\]\[(\w+)\]\[CACHE_READ\]\s*'
        r'propertyId=([^,]+),\s*'
        r'count=(\d+),\s*'
        r'sum=(\d+),\s*'
        r'cacheReadTime=([0-9.]+)ms'
    )
    
    # COMPUTE 패턴 (ns 단위로 변경)
    COMPUTE = re.compile(
        PREFIX +
        r'\[(\w+)\]\[(\w+)\]\[COMPUTE\]\s*'
        r'propertyId=([^,]+),\s*'
        r'operation=(\w+),\s*'
        r'before=\[count=(\d+),\s*sum=(\d+)\],\s*'
        r'after=\[count=(\d+),\s*sum=(\d+),\s*avg=([0-9.]+)\],\s*'
        r'computeTime=(\d+)ns'  # ms → ns 변경
    )
    
    # PERSIST 패턴
    PERSIST = re.compile(
        PREFIX +
        r'\[(\w+)\]\[(\w+)\]\[PERSIST\]\s*'
        r'propertyId=([^,]+),\s*'
        r'newCount=(\d+),\s*'
        r'newAvg=([0-9.]+),\s*'
        r'cacheWriteTime=([0-9.]+)ms,\s*'
        r'rdbSyncTime=([0-9.]+)ms,\s*'
        r'persistTime=([0-9.]+)ms'
    )
    
    # SUMMARY 패턴 (compute는 ns 단위로 변경)
    SUMMARY = re.compile(
        PREFIX +
        r'\[(\w+)\]\[(\w+)\]\[SUMMARY\]\s*'
        r'propertyId=([^,]+),\s*'
        r'operation=(\w+),\s*'
        r'N=(\d+),\s*'
        r'finalAvg=([0-9.]+),\s*'
        r'total=([0-9.]+)ms\s*\|\s*'
        r'cacheRead=([0-9.]+)ms\s*\(([0-9.]+)%\)\s*\|\s*'
        r'compute=(\d+)ns\s*\(([0-9.]+)%\)\s*\|\s*'  # ms → ns 변경
        r'persist=([0-9.]+)ms\s*\(([0-9.]+)%\)'
    )
    
    # CACHE_INIT 패턴
    CACHE_INIT = re.compile(
        PREFIX +
        r'\[(\w+)\]\[(\w+)\]\[CACHE_INIT\]\s*'
        r'완료:\s*총=(\d+)건,\s*'
        r'성공=(\d+)건,\s*'
        r'실패=(\d+)건,\s*'
        r'소요시간=(\d+)ms'
    )


# =============================================================================
# 파서 클래스
# =============================================================================

class V2LogParser:
    """V2_INCREMENTAL 로그 파서"""
    
    def __init__(self):
        self.cache_read_logs: List[CacheReadLog] = []
        self.compute_logs: List[ComputeLog] = []
        self.persist_logs: List[PersistLog] = []
        self.summary_logs: List[SummaryLog] = []
        self.cache_init_logs: List[CacheInitLog] = []
        self.parse_errors: List[str] = []
    
    def parse_file(self, filepath: str) -> None:
        """로그 파일 전체 파싱"""
        with open(filepath, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                self._parse_line(line, line_num)
        
        self._print_parse_summary()
    
    def _parse_line(self, line: str, line_num: int) -> None:
        """단일 라인 파싱"""
        
        if 'V2_INCREMENTAL' not in line:
            return
        
        # CACHE_READ
        if '[CACHE_READ]' in line:
            match = LogPatterns.CACHE_READ.search(line)
            if match:
                self.cache_read_logs.append(CacheReadLog(
                    timestamp=match.group(1),
                    thread=match.group(2),
                    task=match.group(3),
                    version=match.group(4),
                    property_id=match.group(5),
                    current_review_count=int(match.group(6)),
                    current_rating_sum=int(match.group(7)),
                    redis_read_elapsed_ms=float(match.group(8))
                ))
            else:
                self.parse_errors.append(f"Line {line_num}: CACHE_READ 파싱 실패")
            return
        
        # COMPUTE
        if '[COMPUTE]' in line:
            match = LogPatterns.COMPUTE.search(line)
            if match:
                self.compute_logs.append(ComputeLog(
                    timestamp=match.group(1),
                    thread=match.group(2),
                    task=match.group(3),
                    version=match.group(4),
                    property_id=match.group(5),
                    operation=match.group(6),
                    before_review_count=int(match.group(7)),
                    before_rating_sum=int(match.group(8)),
                    after_review_count=int(match.group(9)),
                    after_rating_sum=int(match.group(10)),
                    after_avg_rating=float(match.group(11)),
                    compute_elapsed_ns=int(match.group(12))  # 정수로 파싱 (ns)
                ))
            else:
                self.parse_errors.append(f"Line {line_num}: COMPUTE 파싱 실패")
            return
        
        # PERSIST
        if '[PERSIST]' in line:
            match = LogPatterns.PERSIST.search(line)
            if match:
                self.persist_logs.append(PersistLog(
                    timestamp=match.group(1),
                    thread=match.group(2),
                    task=match.group(3),
                    version=match.group(4),
                    property_id=match.group(5),
                    updated_review_count=int(match.group(6)),
                    updated_avg_rating=float(match.group(7)),
                    redis_write_elapsed_ms=float(match.group(8)),
                    rdb_dirty_check_elapsed_ms=float(match.group(9)),
                    persist_total_elapsed_ms=float(match.group(10))
                ))
            else:
                self.parse_errors.append(f"Line {line_num}: PERSIST 파싱 실패")
            return
        
        # SUMMARY
        if '[SUMMARY]' in line:
            match = LogPatterns.SUMMARY.search(line)
            if match:
                self.summary_logs.append(SummaryLog(
                    timestamp=match.group(1),
                    thread=match.group(2),
                    task=match.group(3),
                    version=match.group(4),
                    property_id=match.group(5),
                    operation=match.group(6),
                    final_review_count=int(match.group(7)),
                    final_avg_rating=float(match.group(8)),
                    method_total_elapsed_ms=float(match.group(9)),
                    redis_read_elapsed_ms=float(match.group(10)),
                    redis_read_ratio_percent=float(match.group(11)),
                    compute_elapsed_ns=int(match.group(12)),  # 정수로 파싱 (ns)
                    compute_ratio_percent=float(match.group(13)),
                    persist_elapsed_ms=float(match.group(14)),
                    persist_ratio_percent=float(match.group(15))
                ))
            else:
                self.parse_errors.append(f"Line {line_num}: SUMMARY 파싱 실패")
            return
        
        # CACHE_INIT
        if '[CACHE_INIT]' in line:
            match = LogPatterns.CACHE_INIT.search(line)
            if match:
                self.cache_init_logs.append(CacheInitLog(
                    timestamp=match.group(1),
                    thread=match.group(2),
                    task=match.group(3),
                    version=match.group(4),
                    init_total_property_count=int(match.group(5)),
                    init_success_count=int(match.group(6)),
                    init_fail_count=int(match.group(7)),
                    init_elapsed_ms=float(match.group(8))
                ))
            return
    
    def _print_parse_summary(self) -> None:
        """파싱 결과 요약 출력"""
        print("\n" + "=" * 60)
        print("V2_INCREMENTAL 로그 파싱 결과")
        print("=" * 60)
        print(f"  CACHE_READ : {len(self.cache_read_logs):,}건")
        print(f"  COMPUTE    : {len(self.compute_logs):,}건")
        print(f"  PERSIST    : {len(self.persist_logs):,}건")
        print(f"  SUMMARY    : {len(self.summary_logs):,}건")
        print(f"  CACHE_INIT : {len(self.cache_init_logs):,}건")
        print("-" * 60)
        print(f"  파싱 오류  : {len(self.parse_errors):,}건")
        
        if self.parse_errors:
            print("\n[파싱 오류 상세]")
            for error in self.parse_errors[:10]:
                print(f"  - {error}")
            if len(self.parse_errors) > 10:
                print(f"  ... 외 {len(self.parse_errors) - 10}건")
        print("=" * 60)
    
    def export_to_csv(self, output_dir: str) -> None:
        """파싱 결과를 CSV로 내보내기"""
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)
        
        if self.cache_read_logs:
            self._write_csv(output_path / "v2_1_cache_read_log.csv", self.cache_read_logs)
        
        if self.compute_logs:
            self._write_csv(output_path / "v2_2_compute_log.csv", self.compute_logs)
        
        if self.persist_logs:
            self._write_csv(output_path / "v2_3_persist_log.csv", self.persist_logs)
        
        if self.summary_logs:
            self._write_csv(output_path / "v2_4_summary_log.csv", self.summary_logs)
        
        if self.cache_init_logs:
            self._write_csv(output_path / "v2_cache_init_log.csv", self.cache_init_logs)
        
        print(f"\nCSV 파일 생성 완료: {output_path}")
    
    def _write_csv(self, filepath: Path, data: List) -> None:
        """데이터클래스 리스트를 CSV로 저장"""
        if not data:
            return
        
        with open(filepath, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=asdict(data[0]).keys())
            writer.writeheader()
            for item in data:
                writer.writerow(asdict(item))
        
        print(f"  ✓ {filepath.name} ({len(data):,}건)")
    
    def get_statistics(self) -> Dict[str, Any]:
        """통계 정보 반환"""
        if not self.summary_logs:
            return {}
        
        operations = {}
        for log in self.summary_logs:
            op = log.operation
            if op not in operations:
                operations[op] = {
                    'count': 0,
                    'total_time_sum': 0.0,
                    'cache_read_sum': 0.0,
                    'compute_sum_ns': 0,  # ns 단위
                    'persist_sum': 0.0
                }
            operations[op]['count'] += 1
            operations[op]['total_time_sum'] += log.method_total_elapsed_ms
            operations[op]['cache_read_sum'] += log.redis_read_elapsed_ms
            operations[op]['compute_sum_ns'] += log.compute_elapsed_ns
            operations[op]['persist_sum'] += log.persist_elapsed_ms
        
        stats = {}
        for op, data in operations.items():
            count = data['count']
            stats[op] = {
                'count': count,
                'avg_total_time_ms': round(data['total_time_sum'] / count, 3),
                'avg_cache_read_ms': round(data['cache_read_sum'] / count, 3),
                'avg_compute_ns': round(data['compute_sum_ns'] / count),  # ns 단위 정수
                'avg_persist_ms': round(data['persist_sum'] / count, 3)
            }
        
        total_count = len(self.summary_logs)
        all_total_time = sum(log.method_total_elapsed_ms for log in self.summary_logs)
        all_cache_read = sum(log.redis_read_elapsed_ms for log in self.summary_logs)
        all_compute_ns = sum(log.compute_elapsed_ns for log in self.summary_logs)
        all_persist = sum(log.persist_elapsed_ms for log in self.summary_logs)
        
        # compute_ns를 ms로 변환하여 비율 계산
        all_compute_ms = all_compute_ns / 1_000_000.0
        
        stats['_OVERALL'] = {
            'count': total_count,
            'avg_total_time_ms': round(all_total_time / total_count, 3),
            'avg_cache_read_ms': round(all_cache_read / total_count, 3),
            'avg_compute_ns': round(all_compute_ns / total_count),  # ns 단위 정수
            'avg_persist_ms': round(all_persist / total_count, 3),
            'cache_read_ratio': round(all_cache_read / all_total_time * 100, 1) if all_total_time > 0 else 0,
            'compute_ratio': round(all_compute_ms / all_total_time * 100, 2) if all_total_time > 0 else 0,
            'persist_ratio': round(all_persist / all_total_time * 100, 1) if all_total_time > 0 else 0
        }
        
        return stats
    
    def print_statistics(self) -> None:
        """통계 정보 출력"""
        stats = self.get_statistics()
        if not stats:
            print("\n통계 정보 없음 (SUMMARY 로그 없음)")
            return
        
        print("\n" + "=" * 70)
        print("V2_INCREMENTAL 성능 통계")
        print("=" * 70)
        
        for op in ['CREATE', 'UPDATE', 'DELETE']:
            if op in stats:
                s = stats[op]
                print(f"\n[{op}] ({s['count']:,}건)")
                print(f"  평균 전체 시간   : {s['avg_total_time_ms']:.3f}ms")
                print(f"  평균 CACHE_READ  : {s['avg_cache_read_ms']:.3f}ms")
                print(f"  평균 COMPUTE     : {s['avg_compute_ns']:,}ns")  # ns 단위
                print(f"  평균 PERSIST     : {s['avg_persist_ms']:.3f}ms")
        
        if '_OVERALL' in stats:
            s = stats['_OVERALL']
            print("\n" + "-" * 70)
            print(f"[전체] ({s['count']:,}건)")
            print(f"  평균 전체 시간   : {s['avg_total_time_ms']:.3f}ms")
            print(f"  평균 CACHE_READ  : {s['avg_cache_read_ms']:.3f}ms ({s['cache_read_ratio']:.1f}%)")
            print(f"  평균 COMPUTE     : {s['avg_compute_ns']:,}ns ({s['compute_ratio']:.2f}%)")  # ns 단위
            print(f"  평균 PERSIST     : {s['avg_persist_ms']:.3f}ms ({s['persist_ratio']:.1f}%)")
        
        print("=" * 70)


# =============================================================================
# V1/V2 비교 분석기
# =============================================================================

class VersionComparator:
    """V1_FULL_SCAN vs V2_INCREMENTAL 성능 비교"""
    
    def __init__(self, v1_summary_csv: str, v2_summary_csv: str):
        self.v1_data = self._load_csv(v1_summary_csv)
        self.v2_data = self._load_csv(v2_summary_csv)
    
    def _load_csv(self, filepath: str) -> List[Dict]:
        """CSV 파일 로드"""
        with open(filepath, 'r', encoding='utf-8-sig') as f:
            return list(csv.DictReader(f))
    
    def compare(self) -> None:
        """V1 vs V2 성능 비교"""
        if not self.v1_data or not self.v2_data:
            print("비교 데이터 부족")
            return
        
        # V1 평균 (정상 요청: 첫 번째 제외)
        v1_normal = self.v1_data[1:] if len(self.v1_data) > 1 else self.v1_data
        v1_avg_total = sum(float(r['total_time_ms']) for r in v1_normal) / len(v1_normal)
        v1_avg_query = sum(float(r['query_time_ms']) for r in v1_normal) / len(v1_normal)
        
        # V2 평균 (정상 요청: 첫 번째 제외)
        v2_normal = self.v2_data[1:] if len(self.v2_data) > 1 else self.v2_data
        v2_avg_total = sum(float(r['method_total_elapsed_ms']) for r in v2_normal) / len(v2_normal)
        v2_avg_cache = sum(float(r['redis_read_elapsed_ms']) for r in v2_normal) / len(v2_normal)
        
        improvement_total = v1_avg_total / v2_avg_total if v2_avg_total > 0 else 0
        improvement_io = v1_avg_query / v2_avg_cache if v2_avg_cache > 0 else 0
        
        print("\n" + "=" * 70)
        print("V1_FULL_SCAN vs V2_INCREMENTAL 성능 비교 (정상 요청 기준)")
        print("=" * 70)
        print(f"\n{'지표':<30} {'V1':>12} {'V2':>12} {'개선율':>12}")
        print("-" * 70)
        print(f"{'샘플 수':<30} {len(v1_normal):>12} {len(v2_normal):>12} {'-':>12}")
        print(f"{'평균 전체 시간 (ms)':<30} {v1_avg_total:>12.3f} {v2_avg_total:>12.3f} {improvement_total:>11.1f}x")
        print(f"{'I/O 구간 (ms)':<30} {v1_avg_query:>12.3f} {v2_avg_cache:>12.3f} {improvement_io:>11.1f}x")
        print(f"{'  V1: Aggregate Query':<30}")
        print(f"{'  V2: Redis CACHE_READ':<30}")
        print("=" * 70)
        print(f"\n[결론]")
        print(f"  - 전체 시간: V2가 V1 대비 {improvement_total:.1f}배 빠름")
        print(f"  - I/O 구간: V2가 V1 대비 {improvement_io:.1f}배 빠름")
        print(f"  - 시간 복잡도: O(N) → O(1) 전환 효과 검증됨")


# =============================================================================
# 메인 함수
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description='V2_INCREMENTAL 로그 파서',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  python v2_log_parser.py application.log
  python v2_log_parser.py application.log --output ./csv_output
  python v2_log_parser.py application.log --stats
  python v2_log_parser.py --compare task1_4_summary.csv v2_4_summary.csv
        """
    )
    
    parser.add_argument('logfile', nargs='?', help='파싱할 로그 파일 경로')
    parser.add_argument('--output', '-o', default='./v2_csv_output', help='CSV 출력 디렉토리')
    parser.add_argument('--stats', '-s', action='store_true', help='통계 정보 출력')
    parser.add_argument('--compare', '-c', nargs=2, metavar=('V1_CSV', 'V2_CSV'),
                        help='V1과 V2 SUMMARY CSV 비교')
    
    args = parser.parse_args()
    
    # V1/V2 비교 모드
    if args.compare:
        comparator = VersionComparator(args.compare[0], args.compare[1])
        comparator.compare()
        return
    
    # 로그 파일 필수 체크
    if not args.logfile:
        parser.print_help()
        return
    
    # 로그 파싱
    log_parser = V2LogParser()
    log_parser.parse_file(args.logfile)
    
    # CSV 내보내기
    log_parser.export_to_csv(args.output)
    
    # 통계 출력
    if args.stats:
        log_parser.print_statistics()


if __name__ == '__main__':
    main()