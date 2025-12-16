#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
스레드별 성능 로그 파싱 및 CSV 변환 스크립트 (로컬 환경용)
- 50개 스레드(http-nio-8185-exec-1 ~ exec-50)의 성능 데이터 추출
- Bottleneck Resolved 2.1.1 메트릭 파싱
- 로컬 환경에서 실행 가능하도록 경로 수정
"""

import re
import csv
import os
from collections import defaultdict

def parse_log_file(file_path):
    """
    로그 파일을 파싱하여 스레드별 성능 데이터 추출
    """
    
    # 스레드별 데이터 저장
    thread_data = defaultdict(list)
    
    # 정규 표현식 패턴들
    thread_pattern = r'(\d{2}:\d{2}:\d{2}\.\d{3})\s+\[([^\]]+)\]\s+(\w+)'
    bottleneck_pattern = r'=== \[Bottleneck Resolved: ([\d.]+)\] ==='
    total_time_pattern = r'1\. 총 소요 시간: (\d+)ms'
    rdb_time_pattern = r'2\. RDB 조회 시간 \(Bulk\): (\d+)ms \(전체의 ([\d.]+)%\)'
    rdb_count_pattern = r'3\. RDB 호출 횟수: (\d+)회'
    status_pattern = r'전세 지역구 추천 요청 완료 - 상태: (\w+)'
    
    # 현재 처리 중인 스레드 정보
    current_thread_info = {}
    current_thread = None
    current_timestamp = None
    current_log_level = None
    
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
            
            # Bottleneck Resolved 섹션 시작
            if 'Bottleneck Resolved' in line and current_thread:
                bottleneck_match = re.search(bottleneck_pattern, line)
                if bottleneck_match:
                    version = bottleneck_match.group(1)
                    # 새로운 성능 측정 시작
                    current_thread_info[current_thread] = {
                        'timestamp': current_timestamp,
                        'log_level': current_log_level,
                        'version': version,
                        'line_number': line_num
                    }
            
            # 총 소요 시간
            elif '총 소요 시간' in line and current_thread:
                total_match = re.search(total_time_pattern, line)
                if total_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['total_time_ms'] = int(total_match.group(1))
            
            # RDB 조회 시간
            elif 'RDB 조회 시간' in line and current_thread:
                rdb_match = re.search(rdb_time_pattern, line)
                if rdb_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['rdb_time_ms'] = int(rdb_match.group(1))
                    current_thread_info[current_thread]['rdb_percentage'] = float(rdb_match.group(2))
            
            # RDB 호출 횟수
            elif 'RDB 호출 횟수' in line and current_thread:
                count_match = re.search(rdb_count_pattern, line)
                if count_match and current_thread in current_thread_info:
                    current_thread_info[current_thread]['rdb_call_count'] = int(count_match.group(1))
                    
                    # 이 시점에서 하나의 완성된 데이터 세트
                    # thread_data에 저장
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
    파싱된 데이터를 CSV 파일로 저장
    """
    
    # CSV 헤더
    headers = [
        'thread_name',
        'timestamp',
        'log_level',
        'version',
        'total_time_ms',
        'rdb_time_ms',
        'rdb_percentage',
        'rdb_call_count',
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
                'total_time_ms': record.get('total_time_ms', ''),
                'rdb_time_ms': record.get('rdb_time_ms', ''),
                'rdb_percentage': record.get('rdb_percentage', ''),
                'rdb_call_count': record.get('rdb_call_count', ''),
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
    
    return len(all_records)

def generate_summary_statistics(thread_data, summary_file):
    """
    스레드별 요약 통계를 생성하여 별도 CSV로 저장
    """
    
    summary_headers = [
        'thread_name',
        'execution_count',
        'avg_total_time_ms',
        'min_total_time_ms',
        'max_total_time_ms',
        'avg_rdb_time_ms',
        'min_rdb_time_ms',
        'max_rdb_time_ms',
        'avg_rdb_percentage',
        'min_rdb_percentage',
        'max_rdb_percentage'
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
        
        summary = {
            'thread_name': thread_name,
            'execution_count': len(valid_records),
            'avg_total_time_ms': round(sum(total_times) / len(total_times), 2),
            'min_total_time_ms': min(total_times),
            'max_total_time_ms': max(total_times),
            'avg_rdb_time_ms': round(sum(rdb_times) / len(rdb_times), 2),
            'min_rdb_time_ms': min(rdb_times),
            'max_rdb_time_ms': max(rdb_times),
            'avg_rdb_percentage': round(sum(rdb_percentages) / len(rdb_percentages), 2),
            'min_rdb_percentage': min(rdb_percentages),
            'max_rdb_percentage': max(rdb_percentages)
        }
        
        summary_data.append(summary)
    
    # 스레드 이름으로 정렬
    summary_data.sort(key=lambda x: x['thread_name'])
    
    # CSV 파일 작성
    with open(summary_file, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=summary_headers)
        writer.writeheader()
        writer.writerows(summary_data)
    
    return len(summary_data)

def main():
    # 로컬 환경용 파일 경로 설정
    # 현재 스크립트 위치 기준
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # 입력 파일 - 스크립트와 같은 폴더에 있다고 가정
    log_file_name = '2차 테스트_50개 스레드 테스트 결과 로그.txt'
    log_file_path = os.path.join(script_dir, log_file_name)
    
    # 입력 파일 존재 확인
    if not os.path.exists(log_file_path):
        print(f"❌ 에러: 로그 파일을 찾을 수 없습니다!")
        print(f"   경로: {log_file_path}")
        print(f"\n📝 해결 방법:")
        print(f"   1. 로그 파일명을 '{log_file_name}'로 변경하세요")
        print(f"   2. 파일을 다음 위치에 놓으세요: {script_dir}")
        print(f"   3. 또는 이 스크립트의 log_file_name 변수를 실제 파일명으로 수정하세요")
        return
    
    # 출력 파일 경로 - 스크립트와 같은 폴더에 생성
    output_csv_path = os.path.join(script_dir, 'thread_performance_data.csv')
    summary_csv_path = os.path.join(script_dir, 'thread_performance_summary.csv')
    
    print("=" * 80)
    print("스레드 성능 로그 파싱 시작")
    print("=" * 80)
    print(f"📁 입력 파일: {log_file_path}")
    print(f"📊 출력 파일 1: {output_csv_path}")
    print(f"📊 출력 파일 2: {summary_csv_path}")
    print("-" * 80)
    
    # 로그 파일 파싱
    print("🔍 로그 파일 파싱 중...")
    thread_data = parse_log_file(log_file_path)
    
    # 파싱 결과 출력
    total_threads = len(thread_data)
    total_records = sum(len(records) for records in thread_data.values())
    
    print(f"✅ 파싱 완료:")
    print(f"   - 스레드 수: {total_threads}")
    print(f"   - 총 레코드 수: {total_records}")
    
    # CSV 저장
    print("\n💾 CSV 파일 저장 중...")
    records_saved = save_to_csv(thread_data, output_csv_path)
    print(f"✅ 상세 데이터 CSV 저장 완료: {records_saved}개 레코드")
    
    # 요약 통계 생성
    print("\n📈 요약 통계 생성 중...")
    summary_count = generate_summary_statistics(thread_data, summary_csv_path)
    print(f"✅ 요약 통계 CSV 저장 완료: {summary_count}개 스레드")
    
    # 간단한 통계 출력
    if thread_data:
        print("\n📊 기본 통계:")
        all_total_times = []
        all_rdb_times = []
        all_rdb_percentages = []
        
        for records in thread_data.values():
            for record in records:
                if 'total_time_ms' in record:
                    all_total_times.append(record['total_time_ms'])
                if 'rdb_time_ms' in record:
                    all_rdb_times.append(record['rdb_time_ms'])
                if 'rdb_percentage' in record:
                    all_rdb_percentages.append(record['rdb_percentage'])
        
        if all_total_times:
            print(f"   - 평균 총 처리 시간: {sum(all_total_times)/len(all_total_times):.2f}ms")
            print(f"   - 최소/최대 총 처리 시간: {min(all_total_times)}ms / {max(all_total_times)}ms")
        
        if all_rdb_times:
            print(f"   - 평균 RDB 조회 시간: {sum(all_rdb_times)/len(all_rdb_times):.2f}ms")
            print(f"   - 최소/최대 RDB 조회 시간: {min(all_rdb_times)}ms / {max(all_rdb_times)}ms")
        
        if all_rdb_percentages:
            print(f"   - 평균 RDB 시간 비율: {sum(all_rdb_percentages)/len(all_rdb_percentages):.2f}%")
    
    print("\n" + "=" * 80)
    print("✅ 모든 작업 완료!")
    print("=" * 80)

if __name__ == "__main__":
    main()
