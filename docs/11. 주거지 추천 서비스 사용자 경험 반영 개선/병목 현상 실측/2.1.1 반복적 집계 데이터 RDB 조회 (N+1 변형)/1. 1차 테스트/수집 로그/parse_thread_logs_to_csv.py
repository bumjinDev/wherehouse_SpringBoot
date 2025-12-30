#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CharterRecommendationService 스레드별 로그 파싱 스크립트
- 입력: 로그 파일 (.txt)
- 출력: CSV 파일 (스레드별 이벤트 요약)

사용법:
    python parse_thread_logs_to_csv.py <입력_로그_파일> [출력_CSV_파일]
    
예시:
    py -3 parse_thread_logs_to_csv.py 1차_테스트_50개_스레드_테스트_결과_로그.txt
    py -3 parse_thread_logs_to_csv.py 1차_테스트_50개_스레드_테스트_결과_로그.txt 1차_테스트_50개_스레드_테스트_결과_로그.csv
"""

import re
import csv
import sys
import os
from collections import defaultdict

# ============================================
# 설정 영역 - 명령줄 인자 또는 기본값 사용
# ============================================
DEFAULT_INPUT_FILE = '로그파일.txt'
DEFAULT_OUTPUT_FILE = '스레드별_이벤트_분석결과.csv'

# ============================================
# 메인 파싱 로직
# ============================================

def parse_log_file(input_file):
    """로그 파일을 읽고 스레드별로 이벤트를 파싱"""
    
    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # CharterRecommendationService 및 RecommendationController 관련 로그만 필터링
    service_logs = []
    for line in lines:
        if 'CharterRecommendationService' in line or 'RecommendationController' in line:
            service_logs.append(line.strip())

    # 스레드별로 로그 그룹화
    thread_logs = defaultdict(list)
    thread_pattern = re.compile(r'\[http-nio-8185-exec-(\d+)\]')

    for log in service_logs:
        match = thread_pattern.search(log)
        if match:
            thread_id = int(match.group(1))
            thread_logs[thread_id].append(log)

    # 각 스레드별 이벤트 정보 추출
    thread_events = {}

    for thread_id, logs in sorted(thread_logs.items()):
        events = {
            'thread_id': thread_id,
            'total_duration_ms': None,
            'rdb_time_ms': None,
            'rdb_percentage': None,
            'rdb_call_count': None,
            'property_count': None,
            'status': None,
            'first_timestamp': None,
            'last_timestamp': None,
            'log_sequence': []
        }
        
        for log in logs:
            # 타임스탬프 추출
            time_match = re.match(r'(\d{2}:\d{2}:\d{2}\.\d{3})', log)
            if time_match:
                timestamp = time_match.group(1)
                if events['first_timestamp'] is None:
                    events['first_timestamp'] = timestamp
                events['last_timestamp'] = timestamp
            
            # 각 이벤트 파싱
            if '총 소요 시간:' in log:
                match = re.search(r'총 소요 시간: (\d+)ms', log)
                if match:
                    events['total_duration_ms'] = int(match.group(1))
                    events['log_sequence'].append(f"[{timestamp}] 총 소요 시간: {match.group(1)}ms")
            
            elif 'RDB 조회 시간' in log:
                match = re.search(r'RDB 조회 시간 \(Sum\): (\d+)ms \(전체의 ([\d.]+)%\)', log)
                if match:
                    events['rdb_time_ms'] = int(match.group(1))
                    events['rdb_percentage'] = float(match.group(2))
                    events['log_sequence'].append(f"[{timestamp}] RDB 조회 시간: {match.group(1)}ms ({match.group(2)}%)")
            
            elif 'RDB 호출 횟수:' in log:
                match = re.search(r'RDB 호출 횟수: (\d+)회', log)
                if match:
                    events['rdb_call_count'] = int(match.group(1))
                    events['log_sequence'].append(f"[{timestamp}] RDB 호출 횟수: {match.group(1)}회")
            
            elif '총 매물 수:' in log:
                match = re.search(r'총 매물 수: (\d+)건', log)
                if match:
                    events['property_count'] = int(match.group(1))
                    events['log_sequence'].append(f"[{timestamp}] 총 매물 수: {match.group(1)}건")
            
            elif 'Bottleneck Point' in log:
                events['log_sequence'].append(f"[{timestamp}] === Bottleneck Point 시작 ===")
            
            elif '=====' in log and 'Bottleneck' not in log:
                events['log_sequence'].append(f"[{timestamp}] === Bottleneck Point 종료 ===")
            
            elif 'S-05' in log:
                events['log_sequence'].append(f"[{timestamp}] S-05: 지역구 점수 계산 및 정렬 시작")
            
            elif 'S-06' in log:
                events['log_sequence'].append(f"[{timestamp}] S-06: 전세 최종 응답 생성 시작")
            
            elif '전세 지역구 추천 서비스 완료' in log:
                events['log_sequence'].append(f"[{timestamp}] 전세 지역구 추천 서비스 완료")
            
            elif '전세 지역구 추천 요청 완료' in log:
                match = re.search(r'상태: (\w+)', log)
                if match:
                    events['status'] = match.group(1)
                    events['log_sequence'].append(f"[{timestamp}] 추천 요청 완료 - 상태: {match.group(1)}")
        
        thread_events[thread_id] = events
    
    return thread_events


def export_to_csv(thread_events, output_file):
    """파싱된 이벤트 데이터를 CSV로 출력"""
    
    # CSV 헤더 정의
    headers = [
        'thread_id',
        'first_timestamp',
        'last_timestamp',
        'total_duration_ms',
        'rdb_time_ms',
        'rdb_percentage',
        'rdb_call_count',
        'property_count',
        'status',
        'event_sequence'
    ]
    
    with open(output_file, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=headers)
        writer.writeheader()
        
        for thread_id in sorted(thread_events.keys()):
            events = thread_events[thread_id]
            row = {
                'thread_id': f"exec-{events['thread_id']}",
                'first_timestamp': events['first_timestamp'],
                'last_timestamp': events['last_timestamp'],
                'total_duration_ms': events['total_duration_ms'],
                'rdb_time_ms': events['rdb_time_ms'],
                'rdb_percentage': events['rdb_percentage'],
                'rdb_call_count': events['rdb_call_count'],
                'property_count': events['property_count'],
                'status': events['status'],
                'event_sequence': ' → '.join(events['log_sequence'])
            }
            writer.writerow(row)
    
    print(f"CSV 파일 생성 완료: {output_file}")


def print_summary(thread_events):
    """콘솔에 통계 요약 출력"""
    
    print("=" * 70)
    print("전체 통계 요약")
    print("=" * 70)
    
    total_durations = [e['total_duration_ms'] for e in thread_events.values() if e['total_duration_ms']]
    rdb_times = [e['rdb_time_ms'] for e in thread_events.values() if e['rdb_time_ms']]
    rdb_percentages = [e['rdb_percentage'] for e in thread_events.values() if e['rdb_percentage']]
    
    if total_durations:
        print(f"\n총 소요 시간:")
        print(f"  최소: {min(total_durations)}ms")
        print(f"  최대: {max(total_durations)}ms")
        print(f"  평균: {sum(total_durations)/len(total_durations):.1f}ms")
    
    if rdb_times:
        print(f"\nRDB 조회 시간:")
        print(f"  최소: {min(rdb_times)}ms")
        print(f"  최대: {max(rdb_times)}ms")
        print(f"  평균: {sum(rdb_times)/len(rdb_times):.1f}ms")
    
    if rdb_percentages:
        print(f"\nRDB 비율:")
        print(f"  최소: {min(rdb_percentages):.1f}%")
        print(f"  최대: {max(rdb_percentages):.1f}%")
        print(f"  평균: {sum(rdb_percentages)/len(rdb_percentages):.1f}%")
    
    # 상태별 카운트
    status_count = defaultdict(int)
    for events in thread_events.values():
        if events['status']:
            status_count[events['status']] += 1
    
    print(f"\n상태별 스레드 수:")
    for status, count in status_count.items():
        print(f"  {status}: {count}개")
    
    print(f"\n총 파싱된 스레드 수: {len(thread_events)}개")


# ============================================
# 실행 진입점
# ============================================
if __name__ == '__main__':
    # 명령줄 인자 처리
    if len(sys.argv) < 2:
        print("사용법: python parse_thread_logs_to_csv.py <입력_로그_파일> [출력_CSV_파일]")
        print("예시: python parse_thread_logs_to_csv.py test_log.txt result.csv")
        sys.exit(1)
    
    INPUT_LOG_FILE = sys.argv[1]
    
    if len(sys.argv) >= 3:
        OUTPUT_CSV_FILE = sys.argv[2]
    else:
        # 입력 파일명에서 출력 파일명 자동 생성
        base_name = os.path.splitext(os.path.basename(INPUT_LOG_FILE))[0]
        OUTPUT_CSV_FILE = f"{base_name}_분석결과.csv"
    
    if not os.path.exists(INPUT_LOG_FILE):
        print(f"오류: 입력 파일을 찾을 수 없습니다 - {INPUT_LOG_FILE}")
        sys.exit(1)
    
    print(f"로그 파일 파싱 시작: {INPUT_LOG_FILE}")
    
    # 1. 로그 파싱
    thread_events = parse_log_file(INPUT_LOG_FILE)
    
    # 2. CSV 출력
    export_to_csv(thread_events, OUTPUT_CSV_FILE)
    
    # 3. 콘솔 요약 출력
    print_summary(thread_events)
    
    print("\n" + "=" * 70)
    print("처리 완료")
    print("=" * 70)
