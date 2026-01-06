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
    
    # HikariCP 로그 매칭 패턴 (날짜+시간 형식 지원)
    # 예시: 2026-01-06 15:35:33.215 [main] DEBUG com.zaxxer.hikari.HikariConfig - Driver class...
    hikari_pattern = re.compile(
        r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+'  # 타임스탬프 (그룹 1)
        r'\[([^\]]+)\]\s+'                                      # 스레드명 (그룹 2)
        r'(DEBUG|INFO|WARN|ERROR)\s+'                           # 로그 레벨 (그룹 3)
        r'(com\.zaxxer\.hikari[^\s]+)\s+-\s+'                   # 클래스명 (그룹 4)
        r'(.*)$'                                                # 메시지 (그룹 5)
    )
    
    # Pool stats 파싱 패턴
    stats_pattern = re.compile(
        r'stats\s*\(total=(\d+),\s*active=(\d+),\s*idle=(\d+),\s*waiting=(\d+)\)'
    )
    
    # Connection closing 파싱 패턴
    closing_pattern = re.compile(
        r'Closing connection\s+([^:]+):\s*\(([^)]+)\)'
    )
    
    # HikariConfig 설정값 파싱 패턴 (key.....value 형식)
    config_pattern = re.compile(
        r'^([a-zA-Z]+)\.{2,}(.+)$'
    )
    
    hikari_logs = []
    stats = {
        'total_lines': 0,
        'hikari_lines': 0,
        'stats_logs': 0,
        'closing_logs': 0,
        'shutdown_logs': 0,
        'config_logs': 0,
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
                    'total': '',
                    'active': '',
                    'idle': '',
                    'waiting': '',
                    'connection_wrapper': '',
                    'close_reason': '',
                    'config_key': '',
                    'config_value': '',
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
                'total': '',
                'active': '',
                'idle': '',
                'waiting': '',
                'connection_wrapper': '',
                'close_reason': '',
                'config_key': '',
                'config_value': '',
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
            
            # HikariConfig 설정값 로그 (key.....value 형식)
            elif 'HikariConfig' in class_name:
                config_match = config_pattern.match(message)
                if config_match:
                    log_entry['log_type'] = 'CONFIG_SETTING'
                    log_entry['config_key'] = config_match.group(1)
                    log_entry['config_value'] = config_match.group(2)
                    stats['config_logs'] += 1
                elif 'configuration' in message.lower():
                    log_entry['log_type'] = 'CONFIG_HEADER'
                    stats['config_logs'] += 1
                elif 'keepaliveTime' in message or 'idleTimeout' in message:
                    log_entry['log_type'] = 'CONFIG_WARNING'
                    stats['config_logs'] += 1
                elif 'Driver class' in message:
                    log_entry['log_type'] = 'DRIVER_LOADED'
                    stats['other_hikari_logs'] += 1
                else:
                    log_entry['log_type'] = 'CONFIG_OTHER'
                    stats['config_logs'] += 1
            
            # 기타 HikariCP 로그
            else:
                log_entry['log_type'] = 'OTHER'
                stats['other_hikari_logs'] += 1
            
            hikari_logs.append(log_entry)
    
    # CSV 파일로 저장 (message 컬럼 제거됨)
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
        'config_key',
        'config_value',
        'raw_log'
    ]
    
    with open(output_csv, 'w', newline='', encoding='utf-8-sig') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(hikari_logs)
    
    return stats


def generate_output_filename(input_file: str, output_dir: str = None) -> str:
    """
    입력 파일명을 기반으로 출력 CSV 파일명 생성
    형식: hikaricp_[원본파일명]_YYYYMMDD_HHMMSS.csv
    출력 디렉토리: 스크립트 실행 위치 기준 ./hikaricp_output/
    """
    input_path = Path(input_file)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    
    # 출력 디렉토리 설정: 현재 작업 디렉토리 기준 hikaricp_output 폴더
    if output_dir is None:
        output_dir = Path.cwd() / 'hikaricp_output'
    else:
        output_dir = Path(output_dir)
    
    # 출력 디렉토리가 없으면 생성
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 입력 파일명에서 의미 있는 부분 추출 (숫자 prefix 제거)
    stem = input_path.stem
    # 숫자와 언더스코어로 시작하는 prefix 제거 (예: 1767681917816_)
    cleaned_stem = re.sub(r'^\d+_', '', stem)
    
    if cleaned_stem:
        output_name = f'hikaricp_{cleaned_stem}_{timestamp}.csv'
    else:
        output_name = f'hikaricp_analysis_{timestamp}.csv'
    
    return str(output_dir / output_name)


def main():
    # 입력/출력 파일 경로 설정
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
    print(f'  - Config 로그: {stats["config_logs"]:,}')
    print(f'  - 기타 HikariCP 로그: {stats["other_hikari_logs"]:,}')
    print(f'-' * 60)
    print(f'CSV 파일 저장 완료: {output_csv}')


if __name__ == '__main__':
    main()
