#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P6Spy CSV 필터링 스크립트
- p6spy_sql_parser.py 결과 CSV에서 Tomcat HTTP 스레드만 추출
- JMeter 등으로 요청한 HTTP 요청 처리 스레드 (http-nio-*-exec-*) 필터링
- 스레드 이름별로 정렬하여 CSV로 출력
"""

import re
import csv
import sys
import glob
from datetime import datetime
from pathlib import Path
from typing import List, Optional


def find_p6spy_csv(search_dir: str = '.') -> Optional[str]:
    """
    지정된 디렉토리에서 p6spy CSV 파일 자동 탐색
    
    Args:
        search_dir: 탐색 디렉토리 (기본: 현재 디렉토리)
    
    Returns:
        찾은 CSV 파일 경로 또는 None
    """
    search_path = Path(search_dir)
    
    # 1. 현재 디렉토리에서 p6spy_*.csv 탐색
    patterns = [
        search_path / 'p6spy_*.csv',
        search_path / 'p6spy_output' / 'p6spy_*.csv',
        search_path / '**/p6spy_*.csv'
    ]
    
    for pattern in patterns:
        files = glob.glob(str(pattern), recursive=True)
        if files:
            # 가장 최근 파일 반환
            files.sort(key=lambda x: Path(x).stat().st_mtime, reverse=True)
            return files[0]
    
    return None


def extract_thread_number(thread_name: str) -> int:
    """
    스레드 이름에서 숫자 추출 (자연 정렬용)
    예: http-nio-8185-exec-11 -> 11
    """
    match = re.search(r'exec-(\d+)$', thread_name)
    if match:
        return int(match.group(1))
    return 0


def filter_tomcat_threads(input_csv: str, output_csv: str) -> dict:
    """
    CSV에서 Tomcat HTTP 스레드만 필터링하여 새 CSV 생성
    
    Args:
        input_csv: 입력 CSV 파일 경로
        output_csv: 출력 CSV 파일 경로
    
    Returns:
        통계 정보 딕셔너리
    """
    
    # Tomcat HTTP 스레드 패턴 (http-nio-PORT-exec-N)
    tomcat_pattern = re.compile(r'^http-nio-\d+-exec-\d+$')
    
    stats = {
        'total_rows': 0,
        'filtered_rows': 0,
        'excluded_rows': 0,
        'unique_threads': set(),
        'excluded_threads': set()
    }
    
    filtered_rows = []
    fieldnames = None
    
    # CSV 읽기
    with open(input_csv, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        
        for row in reader:
            stats['total_rows'] += 1
            thread = row.get('thread', '').strip()
            
            if tomcat_pattern.match(thread):
                filtered_rows.append(row)
                stats['filtered_rows'] += 1
                stats['unique_threads'].add(thread)
            else:
                stats['excluded_rows'] += 1
                if thread:
                    stats['excluded_threads'].add(thread)
    
    # 스레드 이름별 정렬 (자연 정렬: exec-1, exec-2, ... exec-10, exec-11)
    filtered_rows.sort(key=lambda x: (
        extract_thread_number(x.get('thread', '')),
        x.get('timestamp', '')
    ))
    
    # CSV 쓰기
    with open(output_csv, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(filtered_rows)
    
    # set을 정렬된 리스트로 변환
    stats['unique_threads'] = sorted(stats['unique_threads'], key=extract_thread_number)
    stats['excluded_threads'] = sorted(stats['excluded_threads'])
    
    return stats


def generate_output_filename(input_file: str, output_dir: str = None) -> str:
    """
    출력 CSV 파일명 생성
    """
    input_path = Path(input_file)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    
    if output_dir is None:
        output_dir = Path.cwd() / 'p6spy_tomcat_output'
    else:
        output_dir = Path(output_dir)
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 입력 파일명에서 출력 파일명 생성
    stem = input_path.stem
    output_name = f'{stem}_tomcat_threads_{timestamp}.csv'
    
    return str(output_dir / output_name)


def print_usage():
    """사용법 출력"""
    print("사용법: python3 p6spy_tomcat_filter.py [옵션]")
    print()
    print("옵션:")
    print("  -i, --input FILE    입력 CSV 파일 경로 (p6spy_sql_parser.py 결과)")
    print("  -o, --output FILE   출력 CSV 파일 경로 (선택)")
    print("  -d, --dir DIR       CSV 파일 탐색 디렉토리 (기본: 현재 디렉토리)")
    print("  -h, --help          도움말 출력")
    print()
    print("예시:")
    print("  python3 p6spy_tomcat_filter.py")
    print("  python3 p6spy_tomcat_filter.py -i ./p6spy_output/p6spy_wherehouse_20260106.csv")
    print("  python3 p6spy_tomcat_filter.py -d ./logs")
    print()


def parse_args(args: List[str]) -> dict:
    """명령행 인자 파싱"""
    result = {
        'input': None,
        'output': None,
        'search_dir': '.'
    }
    
    i = 0
    while i < len(args):
        arg = args[i]
        
        if arg in ('-h', '--help'):
            print_usage()
            sys.exit(0)
        elif arg in ('-i', '--input'):
            if i + 1 < len(args):
                result['input'] = args[i + 1]
                i += 2
            else:
                print("오류: -i 옵션에 파일 경로가 필요합니다.")
                sys.exit(1)
        elif arg in ('-o', '--output'):
            if i + 1 < len(args):
                result['output'] = args[i + 1]
                i += 2
            else:
                print("오류: -o 옵션에 파일 경로가 필요합니다.")
                sys.exit(1)
        elif arg in ('-d', '--dir'):
            if i + 1 < len(args):
                result['search_dir'] = args[i + 1]
                i += 2
            else:
                print("오류: -d 옵션에 디렉토리 경로가 필요합니다.")
                sys.exit(1)
        else:
            # 위치 인자로 입력 파일 처리
            if result['input'] is None:
                result['input'] = arg
            i += 1
    
    return result


def main():
    args = parse_args(sys.argv[1:])
    
    # 입력 파일 결정
    input_csv = args['input']
    if input_csv is None:
        print(f"입력 파일이 지정되지 않음. '{args['search_dir']}' 에서 p6spy CSV 파일 탐색 중...")
        input_csv = find_p6spy_csv(args['search_dir'])
        
        if input_csv is None:
            print("오류: p6spy CSV 파일을 찾을 수 없습니다.")
            print("  -i 옵션으로 파일을 직접 지정하거나")
            print("  -d 옵션으로 탐색 디렉토리를 지정하세요.")
            sys.exit(1)
        
        print(f"발견된 파일: {input_csv}")
    
    # 입력 파일 존재 확인
    if not Path(input_csv).exists():
        print(f"오류: 입력 파일이 존재하지 않습니다: {input_csv}")
        sys.exit(1)
    
    # 출력 파일 결정
    output_csv = args['output']
    if output_csv is None:
        output_csv = generate_output_filename(input_csv)
    
    print(f'입력 파일: {input_csv}')
    print(f'출력 파일: {output_csv}')
    print('-' * 60)
    
    # 필터링 실행
    stats = filter_tomcat_threads(input_csv, output_csv)
    
    # 결과 출력
    print(f'필터링 완료!')
    print(f'-' * 60)
    print(f'총 로그 수: {stats["total_rows"]:,}')
    print(f'Tomcat 스레드 로그: {stats["filtered_rows"]:,}')
    print(f'제외된 로그: {stats["excluded_rows"]:,}')
    print(f'-' * 60)
    print(f'추출된 Tomcat 스레드 ({len(stats["unique_threads"])}개):')
    for thread in stats['unique_threads']:
        print(f'  - {thread}')
    print(f'-' * 60)
    if stats['excluded_threads']:
        print(f'제외된 스레드 유형 ({len(stats["excluded_threads"])}개):')
        for thread in stats['excluded_threads'][:10]:  # 최대 10개만 표시
            print(f'  - {thread}')
        if len(stats['excluded_threads']) > 10:
            print(f'  ... 외 {len(stats["excluded_threads"]) - 10}개')
        print(f'-' * 60)
    print(f'CSV 파일 저장 완료: {output_csv}')


if __name__ == '__main__':
    main()
