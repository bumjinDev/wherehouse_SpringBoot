#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
[PERF] 로그 파싱 스크립트 (수정본)
- findPropertyIdsByName 병목 측정용
- 쿼리 소요 시간, 전체 메서드 소요 시간, 병목 비중 계산

[수정사항]
- 타임스탬프 패턴: HH:MM:SS.mmm → YYYY-MM-DD HH:MM:SS.mmm 형식 지원
"""

import re
import sys
import os
from datetime import datetime
from typing import List, Dict, Optional
from dataclasses import dataclass
from statistics import mean, median, stdev


@dataclass
class PerfEntry:
    """단일 요청의 성능 측정 결과"""
    timestamp: str
    thread: str
    query_time_ms: int
    input_param: str
    result_count: int
    total_time_ms: int
    bottleneck_ratio: float  # query_time / total_time


def parse_perf_logs(filepath: str) -> List[PerfEntry]:
    """
    로그 파일에서 [PERF] 패턴을 파싱하여 PerfEntry 리스트 반환
    
    실제 로그 형식:
    2025-12-17 19:20:20.269 [http-nio-8185-exec-13] INFO  c.w.r.service.ReviewQueryService - [PERF] findPropertyIdsByName | 소요=847ms | 입력='삼성' | 결과건수=2341
    2025-12-17 19:20:20.987 [http-nio-8185-exec-13] INFO  c.w.r.service.ReviewQueryService - [PERF] getReviews 총소요=1031ms
    """
    
    # [수정됨] 타임스탬프 패턴: 날짜 + 시간 형식
    # 2025-12-17 19:20:20.269 형식
    timestamp_pattern = r'(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})'
    
    # findPropertyIdsByName 로그 패턴
    query_pattern = re.compile(
        timestamp_pattern + r'\s+'               # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                        # 스레드명 (그룹 2)
        r'.*\[PERF\]\s+findPropertyIdsByName\s*\|\s*'
        r'소요=(\d+)ms\s*\|\s*'                   # 소요시간 (그룹 3)
        r"입력='([^']*)'\s*\|\s*"                 # 입력값 (그룹 4)
        r'결과건수=(\d+)'                         # 결과건수 (그룹 5)
    )
    
    # getReviews 총소요 로그 패턴
    total_pattern = re.compile(
        timestamp_pattern + r'\s+'               # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                        # 스레드명 (그룹 2)
        r'.*\[PERF\]\s+getReviews\s+총소요=(\d+)ms'  # 총소요시간 (그룹 3)
    )
    
    # 스레드별 임시 저장소 (query 로그가 먼저 오고, total 로그가 나중에 옴)
    pending: Dict[str, dict] = {}
    results: List[PerfEntry] = []
    
    # 디버깅용: 매칭된 로그 수 카운트
    query_match_count = 0
    total_match_count = 0
    
    with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            
            # findPropertyIdsByName 로그 매칭
            query_match = query_pattern.search(line)
            if query_match:
                query_match_count += 1
                thread = query_match.group(2)
                pending[thread] = {
                    'timestamp': query_match.group(1),
                    'thread': thread,
                    'query_time_ms': int(query_match.group(3)),
                    'input_param': query_match.group(4),
                    'result_count': int(query_match.group(5))
                }
                print(f"  [DEBUG] Line {line_num}: findPropertyIdsByName 매칭 - thread={thread}, 소요={query_match.group(3)}ms")
                continue
            
            # getReviews 총소요 로그 매칭
            total_match = total_pattern.search(line)
            if total_match:
                total_match_count += 1
                thread = total_match.group(2)
                total_time = int(total_match.group(3))
                
                print(f"  [DEBUG] Line {line_num}: getReviews 총소요 매칭 - thread={thread}, 총소요={total_time}ms")
                
                # 해당 스레드의 pending 데이터가 있으면 결합
                if thread in pending:
                    data = pending.pop(thread)
                    query_time = data['query_time_ms']
                    
                    # 병목 비중 계산 (0 division 방지)
                    ratio = (query_time / total_time * 100) if total_time > 0 else 0.0
                    
                    entry = PerfEntry(
                        timestamp=data['timestamp'],
                        thread=data['thread'],
                        query_time_ms=query_time,
                        input_param=data['input_param'],
                        result_count=data['result_count'],
                        total_time_ms=total_time,
                        bottleneck_ratio=ratio
                    )
                    results.append(entry)
                    print(f"  [DEBUG] → 병목분석 엔트리 생성: 쿼리={query_time}ms, 전체={total_time}ms, 비중={ratio:.1f}%")
                else:
                    print(f"  [DEBUG] → 대응하는 findPropertyIdsByName 로그 없음 (propertyId 직접 조회로 추정)")
    
    print(f"\n[매칭 결과] findPropertyIdsByName: {query_match_count}건, getReviews: {total_match_count}건")
    
    return results


def calculate_statistics(entries: List[PerfEntry]) -> dict:
    """통계 계산"""
    if not entries:
        return {}
    
    query_times = [e.query_time_ms for e in entries]
    total_times = [e.total_time_ms for e in entries]
    ratios = [e.bottleneck_ratio for e in entries]
    result_counts = [e.result_count for e in entries]
    
    stats = {
        'sample_count': len(entries),
        'query_time': {
            'min': min(query_times),
            'max': max(query_times),
            'avg': mean(query_times),
            'median': median(query_times),
            'stdev': stdev(query_times) if len(query_times) > 1 else 0
        },
        'total_time': {
            'min': min(total_times),
            'max': max(total_times),
            'avg': mean(total_times),
            'median': median(total_times),
            'stdev': stdev(total_times) if len(total_times) > 1 else 0
        },
        'bottleneck_ratio': {
            'min': min(ratios),
            'max': max(ratios),
            'avg': mean(ratios),
            'median': median(ratios)
        },
        'result_count': {
            'min': min(result_counts),
            'max': max(result_counts),
            'avg': mean(result_counts)
        }
    }
    
    return stats


def generate_report(entries: List[PerfEntry], stats: dict, output_path: str = None) -> str:
    """분석 리포트 생성"""
    
    lines = []
    lines.append("=" * 80)
    lines.append("findPropertyIdsByName 병목 분석 리포트")
    lines.append(f"생성 시각: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("=" * 80)
    lines.append("")
    
    # 통계 요약
    lines.append("[1] 통계 요약")
    lines.append("-" * 40)
    lines.append(f"  샘플 수: {stats['sample_count']}건")
    lines.append("")
    
    lines.append("  [쿼리 소요 시간 (findPropertyIdsByName)]")
    lines.append(f"    최소: {stats['query_time']['min']}ms")
    lines.append(f"    최대: {stats['query_time']['max']}ms")
    lines.append(f"    평균: {stats['query_time']['avg']:.1f}ms")
    lines.append(f"    중앙값: {stats['query_time']['median']:.1f}ms")
    lines.append(f"    표준편차: {stats['query_time']['stdev']:.1f}ms")
    lines.append("")
    
    lines.append("  [전체 메서드 소요 시간 (getReviews)]")
    lines.append(f"    최소: {stats['total_time']['min']}ms")
    lines.append(f"    최대: {stats['total_time']['max']}ms")
    lines.append(f"    평균: {stats['total_time']['avg']:.1f}ms")
    lines.append(f"    중앙값: {stats['total_time']['median']:.1f}ms")
    lines.append(f"    표준편차: {stats['total_time']['stdev']:.1f}ms")
    lines.append("")
    
    lines.append("  [병목 비중 (쿼리시간 / 전체시간 × 100)]")
    lines.append(f"    최소: {stats['bottleneck_ratio']['min']:.1f}%")
    lines.append(f"    최대: {stats['bottleneck_ratio']['max']:.1f}%")
    lines.append(f"    평균: {stats['bottleneck_ratio']['avg']:.1f}%")
    lines.append(f"    중앙값: {stats['bottleneck_ratio']['median']:.1f}%")
    lines.append("")
    
    lines.append("  [결과 건수]")
    lines.append(f"    최소: {stats['result_count']['min']}건")
    lines.append(f"    최대: {stats['result_count']['max']}건")
    lines.append(f"    평균: {stats['result_count']['avg']:.1f}건")
    lines.append("")
    
    # 개별 측정 결과
    lines.append("[2] 개별 측정 결과")
    lines.append("-" * 40)
    lines.append(f"{'번호':>4} | {'시각':>20} | {'쿼리(ms)':>10} | {'전체(ms)':>10} | {'비중':>7} | {'결과건수':>8} | 입력값")
    lines.append("-" * 100)
    
    for i, entry in enumerate(entries, 1):
        lines.append(
            f"{i:>4} | {entry.timestamp:>20} | {entry.query_time_ms:>10} | "
            f"{entry.total_time_ms:>10} | {entry.bottleneck_ratio:>6.1f}% | "
            f"{entry.result_count:>8} | {entry.input_param}"
        )
    
    lines.append("")
    lines.append("=" * 80)
    lines.append("[결론]")
    lines.append(f"findPropertyIdsByName 쿼리가 전체 응답 시간의 평균 {stats['bottleneck_ratio']['avg']:.1f}%를 차지함.")
    lines.append(f"LIKE '%keyword%' 패턴으로 인한 Full Table Scan이 주요 병목으로 추정됨.")
    lines.append("=" * 80)
    
    report = '\n'.join(lines)
    
    if output_path:
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report)
        print(f"리포트 저장됨: {output_path}")
    
    return report


def export_csv(entries: List[PerfEntry], output_path: str) -> None:
    """CSV 파일로 내보내기"""
    import csv
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        writer.writerow([
            '번호', '시각', '스레드', '쿼리소요(ms)', '전체소요(ms)', 
            '병목비중(%)', '입력값', '결과건수'
        ])
        
        for i, entry in enumerate(entries, 1):
            writer.writerow([
                i,
                entry.timestamp,
                entry.thread,
                entry.query_time_ms,
                entry.total_time_ms,
                f"{entry.bottleneck_ratio:.1f}",
                entry.input_param,
                entry.result_count
            ])
    
    print(f"CSV 저장됨: {output_path}")


def main():
    if len(sys.argv) < 2:
        print("사용법: python parse_perf_logs_fixed.py <로그파일경로> [출력디렉토리]")
        print("")
        print("예시:")
        print("  python parse_perf_logs_fixed.py wherehouse.log")
        print("  python parse_perf_logs_fixed.py wherehouse.log ./output")
        sys.exit(1)
    
    input_file = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else '.'
    
    if not os.path.exists(input_file):
        print(f"오류: 파일을 찾을 수 없습니다: {input_file}")
        sys.exit(1)
    
    os.makedirs(output_dir, exist_ok=True)
    
    base_name = os.path.splitext(os.path.basename(input_file))[0]
    
    print(f"파싱 중: {input_file}")
    print("-" * 40)
    
    # 파싱 실행
    entries = parse_perf_logs(input_file)
    
    if not entries:
        print("\n" + "=" * 60)
        print("경고: 병목 분석 데이터를 생성할 수 없습니다.")
        print("=" * 60)
        print("")
        print("[원인 분석]")
        print("  1. findPropertyIdsByName 로그가 없음")
        print("     → propertyName 파라미터로 검색해야 해당 분기 실행됨")
        print("     → 현재 로그는 propertyId 직접 조회만 수행한 것으로 보임")
        print("")
        print("[테스트 방법]")
        print("  propertyName 파라미터를 사용하여 API 호출:")
        print("  GET /api/reviews?propertyName=삼성")
        print("  GET /api/reviews?propertyName=래미안")
        print("")
        print("[필요한 로그 형식]")
        print("  [PERF] findPropertyIdsByName | 소요=XXXms | 입력='YYY' | 결과건수=ZZZ")
        print("  [PERF] getReviews 총소요=XXXms")
        sys.exit(1)
    
    print(f"\n파싱된 측정 결과: {len(entries)}건")
    print("-" * 40)
    
    # 통계 계산
    stats = calculate_statistics(entries)
    
    # 출력 파일 경로
    report_path = os.path.join(output_dir, f"{base_name}_병목분석리포트.txt")
    csv_path = os.path.join(output_dir, f"{base_name}_병목분석데이터.csv")
    
    # 리포트 생성 및 출력
    report = generate_report(entries, stats, report_path)
    print(report)
    
    # CSV 내보내기
    export_csv(entries, csv_path)
    
    print("-" * 40)
    print("완료!")
    print(f"  - 분석 리포트: {report_path}")
    print(f"  - CSV 데이터: {csv_path}")


if __name__ == '__main__':
    main()
