#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
스레드별 성능 로그 파싱 및 CSV 변환 스크립트 (v2.0 - 로컬 버전)
- Java 코드 변경사항 반영 (Chunking 버전)
- Bottleneck Resolved: 2.1.1 (Chunking) 패턴 대응
- RDB Chunk Sum 및 동적 호출 횟수 처리
- 로컬 환경 실행용 (상대 경로 사용)
"""

import re
import csv
import os
import sys
from collections import defaultdict

def parse_log_file(file_path):
    """
    로그 파일을 파싱하여 스레드별 성능 데이터 추출
    Java 코드 변경사항 반영 버전
    """
    
    # 스레드별 데이터 저장
    thread_data = defaultdict(list)
    
    # ===== 업데이트된 정규 표현식 패턴들 =====
    thread_pattern = r'(\d{2}:\d{2}:\d{2}\.\d{3})\s+\[([^\]]+)\]\s+(\w+)'
    
    # Bottleneck 패턴 - (Chunking) 포함 여부 옵션처리
    bottleneck_pattern = r'=== \[Bottleneck Resolved: ([\d.]+)(?:\s+\([^)]+\))?\] ==='
    
    # 기본 패턴들
    total_time_pattern = r'1\. 총 소요 시간: (\d+)ms'
    
    # RDB 패턴 - Bulk 또는 Chunk Sum 모두 대응
    rdb_time_pattern = r'2\. RDB 조회 시간 \((?:Bulk|Chunk Sum)\): (\d+)ms \(전체의 ([\d.]+)%\)'
    
    # RDB 호출 횟수 - 다양한 형태 대응
    rdb_count_pattern = r'3\. RDB 호출 횟수: (\d+)회'
    
    # 상태 패턴
    status_pattern = r'전세 지역구 추천 요청 완료 - 상태: (\w+)'
    
    # 현재 처리 중인 스레드 정보
    current_thread_info = {}
    current_thread = None
    current_timestamp = None
    current_log_level = None
    
    print(f"📂 파싱 중: {os.path.basename(file_path)}")
    
    # 파일 읽기
    with open(file_path, 'r', encoding='utf-8') as file:
        for line_num, line in enumerate(file, 1):
            line = line.strip()
            
            # 빈 줄 또는 중간 생략 표시 건너뛰기
            if not line or '[중간 생략]' in line:
                continue
            
            # 스레드 정보 추출
            thread_match = re.match(thread_pattern, line)
            if thread_match:
                current_timestamp = thread_match.group(1)
                current_thread = thread_match.group(2)
                current_log_level = thread_match.group(3)
            
            # Bottleneck Resolved 섹션 시작 (Chunking 포함 버전도 처리)
            if 'Bottleneck Resolved' in line and current_thread:
                bottleneck_match = re.search(bottleneck_pattern, line)
                if bottleneck_match:
                    version = bottleneck_match.group(1)
                    
                    # (Chunking) 등의 추가 정보 감지
                    if '(Chunking)' in line:
                        version_suffix = 'Chunking'
                    else:
                        version_suffix = 'Standard'
                    
                    # 새로운 성능 측정 시작
                    current_thread_info[current_thread] = {
                        'timestamp': current_timestamp,
                        'log_level': current_log_level,
                        'version': version,
                        'version_type': version_suffix,
                        'line_number': line_num
                    }
            
            # 총 소요 시간
            elif '총 소요 시간' in line and current_thread:
                total_match = re.search(total_time_pattern, line)
                if total_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['total_time_ms'] = int(total_match.group(1))
            
            # RDB 조회 시간 (Bulk 또는 Chunk Sum)
            elif 'RDB 조회 시간' in line and current_thread:
                rdb_match = re.search(rdb_time_pattern, line)
                if rdb_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['rdb_time_ms'] = int(rdb_match.group(1))
                    current_thread_info[current_thread]['rdb_percentage'] = float(rdb_match.group(2))
                    
                    # RDB 타입 구분
                    if 'Chunk Sum' in line:
                        current_thread_info[current_thread]['rdb_type'] = 'Chunk Sum'
                    else:
                        current_thread_info[current_thread]['rdb_type'] = 'Bulk'
            
            # RDB 호출 횟수
            elif 'RDB 호출 횟수' in line and current_thread:
                count_match = re.search(rdb_count_pattern, line)
                if count_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['rdb_call_count'] = int(count_match.group(1))
                    
                    # 추가 설명 정보 파싱
                    if 'Chunk 단위 실행' in line:
                        current_thread_info[current_thread]['execution_type'] = 'Chunk'
                    elif '기존 25회 -> 1회 개선' in line:
                        current_thread_info[current_thread]['execution_type'] = 'Optimized'
                    else:
                        current_thread_info[current_thread]['execution_type'] = 'Standard'
                    
                    # 이 시점에서 하나의 완성된 데이터 세트
                    thread_data[current_thread].append(dict(current_thread_info[current_thread]))
            
            # 상태 정보
            elif '전세 지역구 추천 요청 완료' in line and current_thread:
                status_match = re.search(status_pattern, line)
                if status_match and current_thread in current_thread_info:
                    # 가장 최근 레코드에 상태 추가
                    if thread_data[current_thread]:
                        thread_data[current_thread][-1]['status'] = status_match.group(1)
                        thread_data[current_thread][-1]['completion_timestamp'] = current_timestamp
    
    return thread_data

def save_to_csv(thread_data, output_file):
    """
    파싱된 데이터를 CSV 파일로 저장 (확장된 필드 포함)
    """
    
    # CSV 헤더 (새로운 필드 추가)
    headers = [
        'thread_name',
        'timestamp',
        'log_level',
        'version',
        'version_type',        # 추가: Standard/Chunking
        'total_time_ms',
        'rdb_time_ms',
        'rdb_percentage',
        'rdb_type',           # 추가: Bulk/Chunk Sum
        'rdb_call_count',
        'execution_type',     # 추가: Standard/Optimized/Chunk
        'status',
        'completion_timestamp',
        'line_number'
    ]
    
    # 모든 데이터를 하나의 리스트로 평탄화
    all_records = []
    
    for thread_name, records in thread_data.items():
        for record in records:
            row = {
                'thread_name': thread_name,
                'timestamp': record.get('timestamp', ''),
                'log_level': record.get('log_level', ''),
                'version': record.get('version', ''),
                'version_type': record.get('version_type', ''),
                'total_time_ms': record.get('total_time_ms', ''),
                'rdb_time_ms': record.get('rdb_time_ms', ''),
                'rdb_percentage': record.get('rdb_percentage', ''),
                'rdb_type': record.get('rdb_type', ''),
                'rdb_call_count': record.get('rdb_call_count', ''),
                'execution_type': record.get('execution_type', ''),
                'status': record.get('status', ''),
                'completion_timestamp': record.get('completion_timestamp', ''),
                'line_number': record.get('line_number', '')
            }
            all_records.append(row)
    
    # 타임스탬프 기준으로 정렬
    all_records.sort(key=lambda x: (x['timestamp'], x['thread_name']))
    
    # CSV 파일 작성
    with open(output_file, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=headers)
        writer.writeheader()
        writer.writerows(all_records)
    
    print(f"   💾 저장: {output_file}")
    return len(all_records)

def generate_summary_statistics(thread_data, summary_file):
    """
    스레드별 요약 통계를 생성하여 별도 CSV로 저장
    """
    
    summary_headers = [
        'thread_name',
        'execution_count',
        'version_types',      # 추가: 사용된 버전 타입들
        'avg_total_time_ms',
        'min_total_time_ms',
        'max_total_time_ms',
        'avg_rdb_time_ms',
        'min_rdb_time_ms',
        'max_rdb_time_ms',
        'avg_rdb_percentage',
        'min_rdb_percentage',
        'max_rdb_percentage',
        'avg_rdb_call_count',
        'min_rdb_call_count',
        'max_rdb_call_count',
        'rdb_types_used'      # 추가: 사용된 RDB 타입들
    ]
    
    summary_data = []
    
    for thread_name, records in thread_data.items():
        if not records:
            continue
            
        # 유효한 데이터만 필터링
        valid_records = [r for r in records if 'total_time_ms' in r and 'rdb_time_ms' in r]
        
        if not valid_records:
            continue
        
        total_times = [r['total_time_ms'] for r in valid_records]
        rdb_times = [r['rdb_time_ms'] for r in valid_records]
        rdb_percentages = [r['rdb_percentage'] for r in valid_records]
        rdb_call_counts = [r.get('rdb_call_count', 0) for r in valid_records]
        
        # 고유한 타입 정보 수집
        version_types = list(set(r.get('version_type', 'Unknown') for r in valid_records))
        rdb_types = list(set(r.get('rdb_type', 'Unknown') for r in valid_records))
        
        summary = {
            'thread_name': thread_name,
            'execution_count': len(valid_records),
            'version_types': ', '.join(version_types),
            'avg_total_time_ms': round(sum(total_times) / len(total_times), 2),
            'min_total_time_ms': min(total_times),
            'max_total_time_ms': max(total_times),
            'avg_rdb_time_ms': round(sum(rdb_times) / len(rdb_times), 2),
            'min_rdb_time_ms': min(rdb_times),
            'max_rdb_time_ms': max(rdb_times),
            'avg_rdb_percentage': round(sum(rdb_percentages) / len(rdb_percentages), 2),
            'min_rdb_percentage': min(rdb_percentages),
            'max_rdb_percentage': max(rdb_percentages),
            'avg_rdb_call_count': round(sum(rdb_call_counts) / len(rdb_call_counts), 2) if rdb_call_counts else 0,
            'min_rdb_call_count': min(rdb_call_counts) if rdb_call_counts else 0,
            'max_rdb_call_count': max(rdb_call_counts) if rdb_call_counts else 0,
            'rdb_types_used': ', '.join(rdb_types)
        }
        
        summary_data.append(summary)
    
    # 스레드 이름으로 정렬
    summary_data.sort(key=lambda x: x['thread_name'])
    
    # CSV 파일 작성
    with open(summary_file, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=summary_headers)
        writer.writeheader()
        writer.writerows(summary_data)
    
    print(f"   📊 요약: {summary_file}")
    return len(summary_data)

def find_log_files(directory="."):
    """
    현재 디렉토리에서 로그 파일 찾기
    """
    log_files = []
    patterns = [
        "*스레드*로그*.txt",
        "*thread*log*.txt",
        "*.log",
        "*.txt"
    ]
    
    # 현재 디렉토리의 모든 txt 파일 찾기
    for file in os.listdir(directory):
        if file.endswith('.txt') or file.endswith('.log'):
            # 스레드 또는 thread 키워드가 포함된 파일 우선
            if '스레드' in file or 'thread' in file.lower() or '로그' in file or 'log' in file.lower():
                log_files.append(os.path.join(directory, file))
    
    return log_files

def main():
    """
    메인 실행 함수 - 로컬 환경용
    """
    
    # 스크립트 실행 위치
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    
    print("=" * 80)
    print("스레드 성능 로그 파싱 도구 v2.0 (로컬 버전)")
    print("=" * 80)
    print(f"📁 작업 디렉토리: {os.getcwd()}")
    print("-" * 80)
    
    # 로그 파일 찾기
    log_files = []
    
    # 1. 명령행 인자로 파일이 지정된 경우
    if len(sys.argv) > 1:
        for arg in sys.argv[1:]:
            if os.path.exists(arg):
                log_files.append(arg)
            else:
                print(f"⚠️  파일을 찾을 수 없음: {arg}")
    
    # 2. 인자가 없으면 현재 디렉토리에서 자동 탐색
    if not log_files:
        log_files = find_log_files()
        
        if not log_files:
            print("❌ 로그 파일을 찾을 수 없습니다!")
            print("\n사용법:")
            print("  1. 방법 1: python extract_thread_logs_v2_local.py [로그파일명]")
            print("  2. 방법 2: 스크립트와 같은 폴더에 .txt 로그 파일을 두고 실행")
            print("\n예시:")
            print("  python extract_thread_logs_v2_local.py 3차_테스트_50개_스레드_테스트_결과_로그.txt")
            return
        
        print(f"\n🔍 발견된 로그 파일:")
        for i, file in enumerate(log_files, 1):
            print(f"   {i}. {os.path.basename(file)}")
        
        # 사용자 확인
        if len(log_files) > 1:
            print(f"\n총 {len(log_files)}개 파일을 모두 처리하시겠습니까? (Y/n): ", end='')
            response = input().strip().lower()
            if response and response not in ['y', 'yes', '']:
                print("처리 취소")
                return
    
    print("\n" + "=" * 80)
    
    # 각 로그 파일 처리
    for log_file_path in log_files:
        if not os.path.exists(log_file_path):
            print(f"⚠️  파일 없음: {log_file_path}")
            continue
        
        # 파일명에서 출력 이름 생성
        base_name = os.path.basename(log_file_path)
        name_without_ext = os.path.splitext(base_name)[0]
        output_csv_path = f'{name_without_ext}_v2_data.csv'
        summary_csv_path = f'{name_without_ext}_v2_summary.csv'
        
        print(f"\n📋 처리 중: {base_name}")
        print("-" * 40)
        
        try:
            # 로그 파일 파싱
            thread_data = parse_log_file(log_file_path)
            
            # 파싱 결과 출력
            total_threads = len(thread_data)
            total_records = sum(len(records) for records in thread_data.values())
            
            if total_records == 0:
                print("   ⚠️  파싱된 데이터가 없습니다.")
                continue
            
            print(f"\n✅ 파싱 완료:")
            print(f"   - 스레드 수: {total_threads}")
            print(f"   - 총 레코드 수: {total_records}")
            
            # 버전 타입 통계
            all_version_types = set()
            all_rdb_types = set()
            for records in thread_data.values():
                for record in records:
                    all_version_types.add(record.get('version_type', 'Unknown'))
                    all_rdb_types.add(record.get('rdb_type', 'Unknown'))
            
            print(f"   - 감지된 버전: {', '.join(all_version_types)}")
            print(f"   - RDB 타입: {', '.join(all_rdb_types)}")
            
            # CSV 저장
            print(f"\n📁 파일 생성:")
            records_saved = save_to_csv(thread_data, output_csv_path)
            summary_count = generate_summary_statistics(thread_data, summary_csv_path)
            
            # 간단한 통계
            all_total_times = []
            all_rdb_times = []
            all_rdb_percentages = []
            all_rdb_call_counts = []
            
            for records in thread_data.values():
                for record in records:
                    if 'total_time_ms' in record:
                        all_total_times.append(record['total_time_ms'])
                    if 'rdb_time_ms' in record:
                        all_rdb_times.append(record['rdb_time_ms'])
                    if 'rdb_percentage' in record:
                        all_rdb_percentages.append(record['rdb_percentage'])
                    if 'rdb_call_count' in record:
                        all_rdb_call_counts.append(record['rdb_call_count'])
            
            print(f"\n📊 통계 요약:")
            if all_total_times:
                print(f"   총 처리 시간: 평균 {sum(all_total_times)/len(all_total_times):.2f}ms")
                print(f"                 (최소 {min(all_total_times)}ms / 최대 {max(all_total_times)}ms)")
            
            if all_rdb_times:
                print(f"   RDB 조회 시간: 평균 {sum(all_rdb_times)/len(all_rdb_times):.2f}ms")
                print(f"                  (최소 {min(all_rdb_times)}ms / 최대 {max(all_rdb_times)}ms)")
            
            if all_rdb_percentages:
                print(f"   RDB 시간 비율: 평균 {sum(all_rdb_percentages)/len(all_rdb_percentages):.2f}%")
            
            if all_rdb_call_counts:
                print(f"   RDB 호출 횟수: 평균 {sum(all_rdb_call_counts)/len(all_rdb_call_counts):.2f}회")
                print(f"                  (최소 {min(all_rdb_call_counts)}회 / 최대 {max(all_rdb_call_counts)}회)")
        
        except Exception as e:
            print(f"❌ 오류 발생: {str(e)}")
            import traceback
            traceback.print_exc()
    
    print("\n" + "=" * 80)
    print("✅ 모든 작업 완료!")
    print("=" * 80)

if __name__ == "__main__":
    main()
