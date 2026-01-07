#!/usr/bin/env python3
"""
bottleneck_chunking_parser.py

Bottleneck Resolved 성능 로그 파싱 스크립트 (3차 테스트 - Chunking 적용 후)

작성일: 2026-01-07 | 버전: 1.0.0

대상 메서드: CharterRecommendationService.calculateCharterPropertyScores()
최적화 내용: Oracle IN절 1000개 제한 준수를 위한 Application-Level Chunking

대상 로그 형식:
    === [Bottleneck Resolved: 2.1.1 (Chunking)] ===
    1. 총 소요 시간: 182ms
    2. RDB 조회 시간 (Chunk Sum): 19ms (전체의 10.4%)
    3. RDB 호출 횟수: 1회 (Chunk 단위 실행)
    ==============================================

설계 원칙:
    - 멀티스레드 환경에서 로그 interleaving 문제 해결을 위해
      스레드별 그룹화 후 순차 파싱 방식 적용
    - 기존 bottleneck_resolved_parser.py와 동일한 아키텍처 유지하여
      1차/3차 테스트 결과 비교 용이하게 설계
"""

import re
import csv
import sys
from datetime import datetime
from pathlib import Path
from dataclasses import dataclass
from typing import Optional, List, Tuple, Dict
from collections import defaultdict


# ==============================================================================
# 데이터 클래스 정의
# ==============================================================================

@dataclass
class ChunkingMetric:
    """단일 Bottleneck Resolved (Chunking) 블록의 측정 데이터"""
    line_number: int
    timestamp: str
    thread: str
    log_level: str
    class_name: str
    bottleneck_version: str
    total_duration_ms: int
    rdb_time_ms: int
    rdb_time_percent: float
    rdb_call_count: int
    raw_header: str


@dataclass
class ParsingStatistics:
    """파싱 결과 통계"""
    total_log_lines: int = 0
    bottleneck_blocks: int = 0
    complete_blocks: int = 0
    incomplete_blocks: int = 0


@dataclass
class LogLine:
    """파싱용 로그 라인 구조체"""
    line_number: int
    timestamp: str
    thread: str
    log_level: str
    class_name: str
    message: str
    raw: str


# ==============================================================================
# 파서 클래스
# ==============================================================================

class BottleneckChunkingParser:
    """
    Bottleneck Resolved (Chunking) 로그 블록을 파싱하는 클래스.
    
    멀티스레드 환경에서 로그가 interleaving되는 문제를 해결하기 위해
    스레드별로 로그를 그룹화한 후 각 스레드 내에서 순차 파싱한다.
    
    파싱 대상:
        - 헤더: === [Bottleneck Resolved: 2.1.1 (Chunking)] ===
        - 1번 라인: 총 소요 시간
        - 2번 라인: RDB 조회 시간 (Chunk Sum)
        - 3번 라인: RDB 호출 횟수 (Chunk 단위 실행)
        - 푸터: ==============================================
    """
    
    # 로그 라인 기본 패턴 (Logback 표준 포맷)
    # 예시: 2026-01-07 21:05:09.376 [http-nio-8185-exec-3] INFO  c.w.r.s.CharterRecommendationService - ...
    LOG_LINE_PATTERN = re.compile(
        r'^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+'  # timestamp
        r'\[([^\]]+)\]\s+'                                    # thread
        r'(\w+)\s+'                                           # log level
        r'(\S+)\s+-\s+'                                       # class name
        r'(.*)$'                                              # message
    )
    
    # Bottleneck 블록 메시지 패턴 (Chunking 버전)
    HEADER_MSG_PATTERN = re.compile(
        r'===\s*\[Bottleneck Resolved:\s*([\d.]+)\s*\(Chunking\)\]\s*==='
    )
    TOTAL_DURATION_PATTERN = re.compile(
        r'1\.\s*총 소요 시간:\s*(\d+)ms'
    )
    RDB_QUERY_PATTERN = re.compile(
        r'2\.\s*RDB 조회 시간\s*\(Chunk Sum\):\s*(\d+)ms\s*\(전체의\s*([\d.]+)%\)'
    )
    RDB_CALL_COUNT_PATTERN = re.compile(
        r'3\.\s*RDB 호출 횟수:\s*(\d+)회\s*\(Chunk 단위 실행\)'
    )
    
    def __init__(self, log_path: str):
        self.log_path = Path(log_path)
        self.metrics: List[ChunkingMetric] = []
        self.stats = ParsingStatistics()
        
    def parse(self) -> List[ChunkingMetric]:
        """
        로그 파일을 파싱하여 측정 데이터 리스트 반환
        
        처리 흐름:
            1. 전체 로그 라인 읽기 및 구조화
            2. 스레드별 로그 그룹화 (interleaving 방지)
            3. 각 스레드 내에서 Bottleneck 블록 순차 파싱
            4. timestamp 기준 정렬 후 반환
        """
        if not self.log_path.exists():
            raise FileNotFoundError(f"로그 파일을 찾을 수 없음: {self.log_path}")
        
        with open(self.log_path, 'r', encoding='utf-8') as f:
            raw_lines = f.readlines()
        
        self.stats.total_log_lines = len(raw_lines)
        
        # 1단계: 모든 로그 라인 파싱 및 스레드별 그룹화
        thread_logs: Dict[str, List[LogLine]] = defaultdict(list)
        
        for idx, raw in enumerate(raw_lines):
            raw = raw.strip()
            match = self.LOG_LINE_PATTERN.match(raw)
            if match:
                log_line = LogLine(
                    line_number=idx + 1,
                    timestamp=match.group(1),
                    thread=match.group(2),
                    log_level=match.group(3),
                    class_name=match.group(4),
                    message=match.group(5),
                    raw=raw
                )
                thread_logs[log_line.thread].append(log_line)
        
        # 2단계: 각 스레드별로 Bottleneck 블록 파싱
        for thread, logs in thread_logs.items():
            self._parse_thread_logs(logs)
        
        # 3단계: timestamp 기준 정렬
        self.metrics.sort(key=lambda m: m.timestamp)
        
        return self.metrics
    
    def _parse_thread_logs(self, logs: List[LogLine]):
        """
        단일 스레드의 로그에서 Bottleneck 블록 추출
        
        동일 스레드 내에서는 로그가 순차적으로 기록되므로
        헤더 발견 시 다음 3개 라인을 연속으로 파싱한다.
        """
        i = 0
        while i < len(logs):
            log = logs[i]
            header_match = self.HEADER_MSG_PATTERN.search(log.message)
            
            if header_match:
                self.stats.bottleneck_blocks += 1
                metric = self._parse_block_from_thread(logs, i, header_match, log)
                
                if metric:
                    self.metrics.append(metric)
                    self.stats.complete_blocks += 1
                    i += 4  # 블록 크기(헤더 + 3개 데이터 라인)만큼 스킵
                else:
                    self.stats.incomplete_blocks += 1
                    i += 1
            else:
                i += 1
    
    def _parse_block_from_thread(
        self, 
        logs: List[LogLine], 
        start_idx: int,
        header_match: re.Match, 
        header_log: LogLine
    ) -> Optional[ChunkingMetric]:
        """
        스레드 내에서 연속된 4개 로그로 블록 파싱
        
        블록 구조:
            [start_idx]     : 헤더 (=== [Bottleneck Resolved: ...] ===)
            [start_idx + 1] : 1. 총 소요 시간
            [start_idx + 2] : 2. RDB 조회 시간 (Chunk Sum)
            [start_idx + 3] : 3. RDB 호출 횟수
        """
        
        # 경계 검사: 블록 완성에 필요한 라인 수 확인
        if start_idx + 3 >= len(logs):
            return None
        
        log1 = logs[start_idx + 1]
        log2 = logs[start_idx + 2]
        log3 = logs[start_idx + 3]
        
        # 1. 총 소요 시간 파싱
        duration_match = self.TOTAL_DURATION_PATTERN.search(log1.message)
        if not duration_match:
            return None
        total_duration_ms = int(duration_match.group(1))
        
        # 2. RDB 조회 시간 (Chunk Sum) 파싱
        rdb_query_match = self.RDB_QUERY_PATTERN.search(log2.message)
        if not rdb_query_match:
            return None
        rdb_time_ms = int(rdb_query_match.group(1))
        rdb_time_percent = float(rdb_query_match.group(2))
        
        # 3. RDB 호출 횟수 파싱
        rdb_call_match = self.RDB_CALL_COUNT_PATTERN.search(log3.message)
        if not rdb_call_match:
            return None
        rdb_call_count = int(rdb_call_match.group(1))
        
        return ChunkingMetric(
            line_number=header_log.line_number,
            timestamp=header_log.timestamp,
            thread=header_log.thread,
            log_level=header_log.log_level,
            class_name=header_log.class_name,
            bottleneck_version=header_match.group(1),
            total_duration_ms=total_duration_ms,
            rdb_time_ms=rdb_time_ms,
            rdb_time_percent=rdb_time_percent,
            rdb_call_count=rdb_call_count,
            raw_header=header_log.raw
        )
    
    def get_statistics(self) -> ParsingStatistics:
        """파싱 통계 반환"""
        return self.stats


# ==============================================================================
# 출력 관련 함수
# ==============================================================================

def generate_output_path(input_path: str, output_dir: str = None) -> Tuple[Path, Path]:
    """
    출력 파일 경로 생성
    
    명명 규칙: bottleneck_chunking_[원본파일명]_[YYYYMMDD_HHMMSS].csv
    """
    input_file = Path(input_path)
    
    if output_dir:
        out_dir = Path(output_dir)
    else:
        out_dir = Path.cwd() / "bottleneck_output"
    
    out_dir.mkdir(parents=True, exist_ok=True)
    
    # 업로드 파일명에서 prefix 숫자 제거 (예: 1767787529121_wherehouse.log -> wherehouse)
    original_name = input_file.stem
    cleaned_name = re.sub(r'^\d+_', '', original_name)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_filename = f"bottleneck_chunking_{cleaned_name}_{timestamp}.csv"
    output_path = out_dir / output_filename
    
    return out_dir, output_path


def write_csv(metrics: List[ChunkingMetric], output_path: Path):
    """
    측정 데이터를 CSV 파일로 저장
    
    컬럼 구조는 기존 bottleneck_resolved_parser.py와 동일하게 유지하여
    1차/3차 테스트 결과 비교 시 호환성 확보
    """
    
    columns = [
        'line_number',
        'timestamp',
        'thread',
        'log_level',
        'class_name',
        'bottleneck_version',
        'total_duration_ms',
        'rdb_time_ms',
        'rdb_time_percent',
        'rdb_call_count',
        'raw_header'
    ]
    
    with open(output_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.writer(f)
        writer.writerow(columns)
        
        for m in metrics:
            writer.writerow([
                m.line_number,
                m.timestamp,
                m.thread,
                m.log_level,
                m.class_name,
                m.bottleneck_version,
                m.total_duration_ms,
                m.rdb_time_ms,
                m.rdb_time_percent,
                m.rdb_call_count,
                m.raw_header
            ])


def print_statistics(stats: ParsingStatistics, metrics: List[ChunkingMetric]):
    """파싱 결과 및 성능 통계 출력"""
    
    print(f"\n{'='*60}")
    print("파싱 통계")
    print(f"{'='*60}")
    print(f"  총 로그 라인 수: {stats.total_log_lines:,}")
    print(f"  Bottleneck 블록 수: {stats.bottleneck_blocks}")
    print(f"    - 완전한 블록: {stats.complete_blocks}")
    print(f"    - 불완전 블록: {stats.incomplete_blocks}")
    
    if not metrics:
        print("\n  추출된 데이터 없음")
        print(f"{'='*60}\n")
        return
    
    total_durations = [m.total_duration_ms for m in metrics]
    rdb_times = [m.rdb_time_ms for m in metrics]
    rdb_percents = [m.rdb_time_percent for m in metrics]
    rdb_call_counts = [m.rdb_call_count for m in metrics]
    
    print(f"\n{'='*60}")
    print("성능 측정 요약 (Chunking 적용 후)")
    print(f"{'='*60}")
    
    print(f"\n  [총 소요 시간 (ms)]")
    print(f"    평균: {sum(total_durations)/len(total_durations):.1f}")
    print(f"    최소: {min(total_durations)}")
    print(f"    최대: {max(total_durations)}")
    print(f"    중앙값: {sorted(total_durations)[len(total_durations)//2]}")
    
    print(f"\n  [RDB 조회 시간 - Chunk Sum (ms)]")
    print(f"    평균: {sum(rdb_times)/len(rdb_times):.1f}")
    print(f"    최소: {min(rdb_times)}")
    print(f"    최대: {max(rdb_times)}")
    
    print(f"\n  [RDB 조회 비율 (%)]")
    print(f"    평균: {sum(rdb_percents)/len(rdb_percents):.1f}%")
    print(f"    최소: {min(rdb_percents):.1f}%")
    print(f"    최대: {max(rdb_percents):.1f}%")
    
    print(f"\n  [RDB 호출 횟수 (Chunk 단위)]")
    print(f"    평균: {sum(rdb_call_counts)/len(rdb_call_counts):.1f}회")
    print(f"    최소: {min(rdb_call_counts)}회")
    print(f"    최대: {max(rdb_call_counts)}회")
    
    print(f"{'='*60}\n")


def print_usage_guide():
    """활용 가이드 출력"""
    guide = """
================================================================================
활용 가이드 (3차 테스트 - Chunking 최적화)
================================================================================

1. 1차 테스트(N+1) vs 3차 테스트(Chunking) 비교 분석
   - total_duration_ms 평균값 비교로 전체 성능 개선율 산출
   - rdb_time_percent 평균값 비교로 RDB 병목 비율 변화 확인
   - rdb_call_count로 호출 횟수 감소 정량화

2. Chunk 단위 실행 효과 분석
   - rdb_call_count가 1~2회로 고정되는지 확인
   - 매물 수 대비 Chunk 분할 효율성 검증

3. 성능 추이 모니터링
   - timestamp 기준 시계열 분석
   - 특정 시점 성능 이상 탐지

4. 스레드별 부하 분석
   - thread 컬럼으로 그룹화하여 스레드별 처리 시간 비교
   - 특정 스레드에 부하 집중 여부 확인

5. CSV 데이터 활용 예시 (Python/Pandas)
   ```python
   import pandas as pd
   
   # 3차 테스트 결과 로드
   df_chunking = pd.read_csv('bottleneck_chunking_*.csv')
   
   # 1차 테스트 결과 로드 (기존 파서 출력)
   df_n_plus_1 = pd.read_csv('bottleneck_original_*.csv')
   
   # 성능 개선율 산출
   avg_before = df_n_plus_1['total_duration_ms'].mean()
   avg_after = df_chunking['total_duration_ms'].mean()
   improvement = (avg_before - avg_after) / avg_before * 100
   print(f"성능 개선율: {improvement:.1f}%")
   ```

================================================================================
"""
    print(guide)


# ==============================================================================
# 메인 함수
# ==============================================================================

def main():
    if len(sys.argv) < 2:
        print("사용법: python bottleneck_chunking_parser.py <로그파일경로> [출력CSV경로]")
        print("\n예시:")
        print("  python bottleneck_chunking_parser.py wherehouse.log")
        print("  python bottleneck_chunking_parser.py wherehouse.log ./output/result.csv")
        print("\n출력 파일 경로를 지정하지 않으면 ./bottleneck_output/ 폴더에 자동 생성됩니다.")
        print("명명 규칙: bottleneck_chunking_[원본파일명]_[YYYYMMDD_HHMMSS].csv")
        sys.exit(1)
    
    log_path = sys.argv[1]
    
    if len(sys.argv) > 2:
        output_path = Path(sys.argv[2])
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_dir = output_path.parent
    else:
        output_dir, output_path = generate_output_path(log_path)
    
    print(f"\n입력 로그 파일: {log_path}")
    print(f"출력 디렉토리: {output_dir}")
    print(f"출력 CSV 파일: {output_path}")
    
    parser = BottleneckChunkingParser(log_path)
    metrics = parser.parse()
    stats = parser.get_statistics()
    
    write_csv(metrics, output_path)
    print(f"\nCSV 파일 생성 완료: {output_path}")
    
    print_statistics(stats, metrics)
    print_usage_guide()


if __name__ == "__main__":
    main()
