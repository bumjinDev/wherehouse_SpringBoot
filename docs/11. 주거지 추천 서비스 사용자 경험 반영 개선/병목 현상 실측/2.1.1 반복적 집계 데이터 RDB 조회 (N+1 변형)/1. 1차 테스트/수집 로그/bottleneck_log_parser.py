#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bottleneck Point 성능 로그 추출 스크립트
- calculateCharterPropertyScores() 등에서 직접 작성한 병목점 분석 로그 추출
- 각 Bottleneck Point 블록을 파싱하여 CSV로 저장
"""

import re
import csv
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional


def parse_bottleneck_logs(input_file: str, output_csv: str) -> dict:
    """
    Bottleneck Point 로그를 파싱하여 CSV로 저장
    
    Args:
        input_file: 입력 로그 파일 경로
        output_csv: 출력 CSV 파일 경로
    
    Returns:
        통계 정보 딕셔너리
    """
    
    # 로그 라인 기본 패턴
    log_line_pattern = re.compile(
        r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+'  # 타임스탬프
        r'\[([^\]]+)\]\s+'                                      # 스레드명
        r'(DEBUG|INFO|WARN|ERROR)\s+'                           # 로그 레벨
        r'(\S+)\s+-\s+'                                         # 클래스명 (약어)
        r'(.*)$'                                                # 메시지
    )
    
    # Bottleneck Point 헤더 패턴
    # === [Bottleneck Point: 2.1.1 (Origin N+1)] ===
    header_pattern = re.compile(
        r'===\s*\[Bottleneck Point:\s*([^\]]+)\]\s*==='
    )
    
    # 메트릭 패턴들
    total_duration_pattern = re.compile(r'1\.\s*총 소요 시간:\s*(\d+)ms')
    rdb_time_pattern = re.compile(r'2\.\s*RDB 조회 시간.*?:\s*(\d+)ms\s*\(전체의\s*([\d.]+)%\)')
    rdb_call_pattern = re.compile(r'3\.\s*RDB 호출 횟수:\s*(\d+)회')
    property_count_pattern = re.compile(r'4\.\s*총 매물 수:\s*(\d+)건')
    
    # 종료 구분선 패턴
    footer_pattern = re.compile(r'^={10,}$')
    
    bottleneck_logs = []
    stats = {
        'total_lines': 0,
        'bottleneck_blocks': 0,
        'complete_blocks': 0,
        'incomplete_blocks': 0
    }
    
    current_block = None
    
    with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
        for line_num, line in enumerate(f, 1):
            stats['total_lines'] += 1
            line = line.rstrip('\r\n')
            
            # 로그 라인 파싱
            log_match = log_line_pattern.match(line)
            if not log_match:
                continue
            
            timestamp = log_match.group(1)
            thread = log_match.group(2)
            log_level = log_match.group(3)
            class_name = log_match.group(4)
            message = log_match.group(5)
            
            # Bottleneck Point 헤더 확인
            header_match = header_pattern.search(message)
            if header_match:
                # 이전 블록이 있으면 저장
                if current_block:
                    if current_block.get('total_duration_ms'):
                        stats['complete_blocks'] += 1
                    else:
                        stats['incomplete_blocks'] += 1
                    bottleneck_logs.append(current_block)
                
                stats['bottleneck_blocks'] += 1
                
                # 새 블록 시작
                bottleneck_point_id = header_match.group(1).strip()
                
                # Bottleneck Point ID 파싱 (예: "2.1.1 (Origin N+1)")
                id_match = re.match(r'([\d.]+)\s*(?:\(([^)]+)\))?', bottleneck_point_id)
                point_number = id_match.group(1) if id_match else bottleneck_point_id
                point_description = id_match.group(2) if id_match and id_match.group(2) else ''
                
                current_block = {
                    'line_number': line_num,
                    'timestamp': timestamp,
                    'thread': thread,
                    'log_level': log_level,
                    'class_name': class_name,
                    'bottleneck_point': point_number,
                    'point_description': point_description,
                    'total_duration_ms': '',
                    'rdb_time_ms': '',
                    'rdb_time_percent': '',
                    'rdb_call_count': '',
                    'total_property_count': '',
                    'raw_header': line
                }
                continue
            
            # 현재 블록이 없으면 스킵
            if not current_block:
                continue
            
            # 동일 스레드의 로그만 처리
            if thread != current_block['thread']:
                continue
            
            # 총 소요 시간
            duration_match = total_duration_pattern.search(message)
            if duration_match:
                current_block['total_duration_ms'] = duration_match.group(1)
                continue
            
            # RDB 조회 시간
            rdb_time_match = rdb_time_pattern.search(message)
            if rdb_time_match:
                current_block['rdb_time_ms'] = rdb_time_match.group(1)
                current_block['rdb_time_percent'] = rdb_time_match.group(2)
                continue
            
            # RDB 호출 횟수
            rdb_call_match = rdb_call_pattern.search(message)
            if rdb_call_match:
                current_block['rdb_call_count'] = rdb_call_match.group(1)
                continue
            
            # 총 매물 수
            property_match = property_count_pattern.search(message)
            if property_match:
                current_block['total_property_count'] = property_match.group(1)
                continue
            
            # 종료 구분선
            if footer_pattern.search(message):
                # 블록 완료
                if current_block.get('total_duration_ms'):
                    stats['complete_blocks'] += 1
                else:
                    stats['incomplete_blocks'] += 1
                bottleneck_logs.append(current_block)
                current_block = None
    
    # 마지막 블록 처리
    if current_block:
        if current_block.get('total_duration_ms'):
            stats['complete_blocks'] += 1
        else:
            stats['incomplete_blocks'] += 1
        bottleneck_logs.append(current_block)
    
    # CSV 파일로 저장
    fieldnames = [
        'line_number',
        'timestamp',
        'thread',
        'log_level',
        'class_name',
        'bottleneck_point',
        'point_description',
        'total_duration_ms',
        'rdb_time_ms',
        'rdb_time_percent',
        'rdb_call_count',
        'total_property_count',
        'raw_header'
    ]
    
    with open(output_csv, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(bottleneck_logs)
    
    return stats


def generate_output_filename(input_file: str, output_dir: str = None) -> str:
    """
    입력 파일명을 기반으로 출력 CSV 파일명 생성
    """
    input_path = Path(input_file)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    
    if output_dir is None:
        output_dir = Path.cwd() / 'bottleneck_output'
    else:
        output_dir = Path(output_dir)
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    stem = input_path.stem
    cleaned_stem = re.sub(r'^\d+_', '', stem)
    
    if cleaned_stem:
        output_name = f'bottleneck_{cleaned_stem}_{timestamp}.csv'
    else:
        output_name = f'bottleneck_analysis_{timestamp}.csv'
    
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
    
    stats = parse_bottleneck_logs(input_file, output_csv)
    
    print(f'파싱 완료!')
    print(f'-' * 60)
    print(f'총 로그 라인 수: {stats["total_lines"]:,}')
    print(f'Bottleneck 블록 수: {stats["bottleneck_blocks"]:,}')
    print(f'  - 완전한 블록: {stats["complete_blocks"]:,}')
    print(f'  - 불완전 블록: {stats["incomplete_blocks"]:,}')
    print(f'-' * 60)
    print(f'CSV 파일 저장 완료: {output_csv}')


if __name__ == '__main__':
    main()
