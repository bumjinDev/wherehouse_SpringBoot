#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HikariCP 로그 추출 스크립트
- 로그 파일에서 HikariCP 관련 로그만 발췌하여 CSV 파일로 저장
"""

import re
import csv
import sys
from datetime import datetime
from pathlib import Path


def parse_hikaricp_logs(input_file: str, output_csv: str) -> dict:
    """
    HikariCP 관련 로그를 파싱하여 CSV로 저장
    
    Args:
        input_file: 입력 로그 파일 경로
        output_csv: 출력 CSV 파일 경로
    
    Returns:
        통계 정보 딕셔너리
    """
    
    # HikariCP 로그 매칭 패턴
    # 예시: 15:01:10.965 [HikariPool-1 connection adder] DEBUG com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Connection not added, stats (total=10, active=10, idle=0, waiting=1)
    hikari_pattern = re.compile(
        r'^(\d{2}:\d{2}:\d{2}\.\d{3})\s+'  # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                  # 스레드명 (그룹 2)
        r'(DEBUG|INFO|WARN|ERROR)\s+'       # 로그 레벨 (그룹 3)
        r'(com\.zaxxer\.hikari[^\s]+)\s+-\s+'  # 클래스명 (그룹 4)
        r'(.+)$'                            # 메시지 (그룹 5)
    )
    
    # Pool stats 파싱 패턴
    stats_pattern = re.compile(
        r'stats\s*\(total=(\d+),\s*active=(\d+),\s*idle=(\d+),\s*waiting=(\d+)\)'
    )
    
    # Connection closing 파싱 패턴
    closing_pattern = re.compile(
        r'Closing connection\s+([^:]+):\s*\(([^)]+)\)'
    )
    
    hikari_logs = []
    stats = {
        'total_lines': 0,
        'hikari_lines': 0,
        'stats_logs': 0,
        'closing_logs': 0,
        'shutdown_logs': 0,
        'other_hikari_logs': 0
    }
    
    with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
        for line_num, line in enumerate(f, 1):
            stats['total_lines'] += 1
            line = line.strip()
            
            # HikariCP 관련 로그인지 확인 (대소문자 무시)
            if 'hikari' not in line.lower():
                continue
            
            match = hikari_pattern.match(line)
            if not match:
                # 패턴에 맞지 않지만 hikari가 포함된 경우 원본 그대로 저장
                hikari_logs.append({
                    'line_number': line_num,
                    'timestamp': '',
                    'thread': '',
                    'log_level': '',
                    'class_name': '',
                    'pool_name': '',
                    'log_type': 'UNPARSED',
                    'message': line,
                    'total': '',
                    'active': '',
                    'idle': '',
                    'waiting': '',
                    'connection_wrapper': '',
                    'close_reason': '',
                    'raw_log': line
                })
                stats['hikari_lines'] += 1
                stats['other_hikari_logs'] += 1
                continue
            
            stats['hikari_lines'] += 1
            
            timestamp = match.group(1)
            thread = match.group(2)
            log_level = match.group(3)
            class_name = match.group(4)
            message = match.group(5)
            
            # Pool 이름 추출 (HikariPool-1 등)
            pool_match = re.search(r'(HikariPool-\d+)', message)
            pool_name = pool_match.group(1) if pool_match else ''
            
            # 로그 유형 분류 및 상세 정보 추출
            log_entry = {
                'line_number': line_num,
                'timestamp': timestamp,
                'thread': thread,
                'log_level': log_level,
                'class_name': class_name,
                'pool_name': pool_name,
                'log_type': '',
                'message': message,
                'total': '',
                'active': '',
                'idle': '',
                'waiting': '',
                'connection_wrapper': '',
                'close_reason': '',
                'raw_log': line
            }
            
            # Stats 로그 파싱
            stats_match = stats_pattern.search(message)
            if stats_match:
                log_entry['total'] = stats_match.group(1)
                log_entry['active'] = stats_match.group(2)
                log_entry['idle'] = stats_match.group(3)
                log_entry['waiting'] = stats_match.group(4)
                
                if 'Connection not added' in message:
                    log_entry['log_type'] = 'CONNECTION_NOT_ADDED'
                elif 'Before shutdown' in message:
                    log_entry['log_type'] = 'BEFORE_SHUTDOWN_STATS'
                    stats['shutdown_logs'] += 1
                elif 'After shutdown' in message:
                    log_entry['log_type'] = 'AFTER_SHUTDOWN_STATS'
                    stats['shutdown_logs'] += 1
                elif 'Pool stats' in message:
                    log_entry['log_type'] = 'POOL_STATS'
                else:
                    log_entry['log_type'] = 'STATS_OTHER'
                
                stats['stats_logs'] += 1
            
            # Connection closing 로그 파싱
            elif 'Closing connection' in message:
                closing_match = closing_pattern.search(message)
                if closing_match:
                    log_entry['connection_wrapper'] = closing_match.group(1)
                    log_entry['close_reason'] = closing_match.group(2)
                log_entry['log_type'] = 'CONNECTION_CLOSING'
                stats['closing_logs'] += 1
            
            # Shutdown 관련 로그
            elif 'Shutdown initiated' in message:
                log_entry['log_type'] = 'SHUTDOWN_INITIATED'
                stats['shutdown_logs'] += 1
            elif 'Shutdown completed' in message:
                log_entry['log_type'] = 'SHUTDOWN_COMPLETED'
                stats['shutdown_logs'] += 1
            
            # Fill pool 관련 로그
            elif 'Fill pool' in message:
                log_entry['log_type'] = 'FILL_POOL'
                stats['other_hikari_logs'] += 1
            
            # Added connection 로그
            elif 'Added connection' in message:
                log_entry['log_type'] = 'CONNECTION_ADDED'
                stats['other_hikari_logs'] += 1
            
            # 기타 HikariCP 로그
            else:
                log_entry['log_type'] = 'OTHER'
                stats['other_hikari_logs'] += 1
            
            hikari_logs.append(log_entry)
    
    # CSV 파일로 저장
    fieldnames = [
        'line_number',
        'timestamp',
        'thread',
        'log_level',
        'class_name',
        'pool_name',
        'log_type',
        'total',
        'active',
        'idle',
        'waiting',
        'connection_wrapper',
        'close_reason',
        'message',
        'raw_log'
    ]
    
    with open(output_csv, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(hikari_logs)
    
    return stats


def main():
    # 입력/출력 파일 경로 설정
    if len(sys.argv) >= 2:
        input_file = sys.argv[1]
    else:
        input_file = '/mnt/user-data/uploads/1765867650241_1차_테스트_50개_스레드_테스트_결과_로그.txt'
    
    if len(sys.argv) >= 3:
        output_csv = sys.argv[2]
    else:
        # 입력 파일명에서 출력 파일명 생성 (출력은 /mnt/user-data/outputs 디렉토리에)
        input_path = Path(input_file)
        output_csv = f'/mnt/user-data/outputs/{input_path.stem}_hikaricp_logs.csv'
    
    print(f'입력 파일: {input_file}')
    print(f'출력 파일: {output_csv}')
    print('-' * 60)
    
    # 로그 파싱 실행
    stats = parse_hikaricp_logs(input_file, output_csv)
    
    # 결과 출력
    print(f'파싱 완료!')
    print(f'-' * 60)
    print(f'총 로그 라인 수: {stats["total_lines"]:,}')
    print(f'HikariCP 로그 라인 수: {stats["hikari_lines"]:,}')
    print(f'  - Stats 로그: {stats["stats_logs"]:,}')
    print(f'  - Connection Closing 로그: {stats["closing_logs"]:,}')
    print(f'  - Shutdown 로그: {stats["shutdown_logs"]:,}')
    print(f'  - 기타 HikariCP 로그: {stats["other_hikari_logs"]:,}')
    print(f'-' * 60)
    print(f'CSV 파일 저장 완료: {output_csv}')


if __name__ == '__main__':
    main()
