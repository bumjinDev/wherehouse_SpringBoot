"""
Extractor Utils - 데이터 추출 계층 공통 함수

이 모듈은 모든 r0X_data_extractor.py 스크립트에서 공통으로 사용하는 함수를 제공합니다.

주요 함수:
- parse_ndjson_log(): NDJSON 로그 파일 파싱
- clean_log_data(): 로그 데이터 정제 (중복 제거, 타입 변환)
- extract_result_data(): resultData 필드 무손실 보존
- create_metadata(): 메타데이터 생성
- save_to_json(): JSON 파일로 저장

작성자: 정범진
작성일: 2025-01-24

=============================================================================
경로 설정 (Windows 로컬 환경)
=============================================================================
실제 사용 시 아래 경로를 자신의 환경에 맞게 수정하세요:

LOG_BASE_PATH = r'E:\devSpace\SpringBootProjects\wherehouse_SpringBoot-master\wherehouse\log'
RESULT_BASE_PATH = r'E:\devSpace\results'

예제:
    log_file = os.path.join(LOG_BASE_PATH, 'wherehouse.log')
    output_dir = os.path.join(RESULT_BASE_PATH, 'r01')
=============================================================================
"""

import json
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any


def parse_ndjson_log(log_file: str, step_filter: str) -> List[Dict]:
    """
    NDJSON 로그 파일을 파싱하여 특정 step의 로그만 추출
    
    Args:
        log_file: 로그 파일 경로 (예: 'logs/wherehouse.log')
        step_filter: 필터링할 step (예: 'R-01')
    
    Returns:
        List[Dict]: 파싱된 로그 리스트
    
    Raises:
        FileNotFoundError: 로그 파일이 존재하지 않는 경우
    
    Example:
        >>> logs = parse_ndjson_log(r'E:\devSpace\SpringBootProjects\wherehouse_SpringBoot-master\wherehouse\log\wherehouse.log', 'R-01')
        >>> len(logs)
        150
        >>> logs[0]['step']
        'R-01'
    """
    log_path = Path(log_file)
    if not log_path.exists():
        raise FileNotFoundError(f"로그 파일을 찾을 수 없습니다: {log_file}")
    
    logs = []
    parse_errors = 0
    
    print(f"  - 로그 파일 읽는 중: {log_file}")
    
    with open(log_file, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            
            try:
                log = json.loads(line)
                
                # PERFORMANCE 이벤트이고 해당 step인 경우만 추가
                if (log.get('eventType') == 'PERFORMANCE' and 
                    log.get('step') == step_filter):
                    logs.append(log)
                    
            except json.JSONDecodeError as e:
                parse_errors += 1
                if parse_errors <= 5:  # 처음 5개만 출력
                    print(f"  Warning: JSON 파싱 실패 (Line {line_num}): {e}")
                continue
    
    if parse_errors > 5:
        print(f"  Warning: 총 {parse_errors}개 라인 파싱 실패 (처음 5개만 표시)")
    
    return logs


def clean_log_data(logs: List[Dict]) -> List[Dict]:
    """
    로그 데이터 정제
    
    처리 내용:
    1. duration_ms가 없으면 duration_ns에서 계산
    2. 중복 제거 (동일 traceId + action + status)
    3. 타입 변환 (duration을 float로)
    
    Args:
        logs: 파싱된 로그 리스트
    
    Returns:
        List[Dict]: 정제된 로그 리스트
    
    Example:
        >>> logs = [
        ...     {'traceId': 'a', 'action': 'test', 'status': 'END', 'duration_ns': 1000000},
        ...     {'traceId': 'a', 'action': 'test', 'status': 'END', 'duration_ns': 1000000}  # 중복
        ... ]
        >>> cleaned = clean_log_data(logs)
        >>> len(cleaned)
        1
        >>> cleaned[0]['duration_ms']
        1.0
    """
    cleaned = []
    seen = set()
    
    for log in logs:
        # duration_ms 계산 (없는 경우에만)
        if 'duration_ms' not in log and 'duration_ns' in log:
            log['duration_ms'] = round(log['duration_ns'] / 1_000_000, 3)
        
        # 중복 제거 키 생성
        dedup_key = (
            log.get('traceId'),
            log.get('action'),
            log.get('status')
        )
        
        if dedup_key not in seen:
            seen.add(dedup_key)
            cleaned.append(log)
    
    original_count = len(logs)
    cleaned_count = len(cleaned)
    duplicates = original_count - cleaned_count
    
    if duplicates > 0:
        print(f"  - 중복 제거: {duplicates}개 로그 제거됨")
    
    return cleaned


def extract_result_data(logs: List[Dict], step: str) -> List[Dict]:
    """
    resultData 필드 추출 (무손실 보존 원칙)
    
    Args:
        logs: 정제된 로그 리스트
        step: 단계 (예: 'R-01')
    
    Returns:
        List[Dict]: resultData가 원본 그대로 유지된 로그 리스트
    
    CRITICAL 원칙:
        - resultData의 모든 필드를 있는 그대로 보존
        - 평탄화, 필터링, 변환 일체 금지
        - R-04처럼 amenityResults에 수백 개 데이터가 있어도 전체 보존
        - Generator에서 필요한 것만 선택적으로 추출하도록 위임
    
    이유:
        - 중간 파일은 원본 데이터의 완전한 복사본 역할
        - 나중에 다른 분석이 필요하면 중간 파일에서 재추출 가능
        - 데이터 손실은 복구 불가능하므로 절대 필터링 금지
    """
    # 아무것도 하지 않음 - 원본 그대로 반환
    # 이 함수는 명시적으로 "무손실 보존"을 선언하기 위해 존재
    return logs


def create_metadata(config: Dict, logs: List[Dict]) -> Dict:
    """
    메타데이터 생성
    
    Args:
        config: 설정 딕셔너리 (step, log_file 포함)
        logs: 로그 리스트
    
    Returns:
        Dict: 메타데이터
        
    Example:
        >>> config = {'step': 'R-01', 'log_file': 'logs/wherehouse.log'}
        >>> logs = [{'status': 'START'}, {'status': 'END'}]
        >>> meta = create_metadata(config, logs)
        >>> meta['step']
        'R-01'
        >>> meta['total_logs']
        2
        >>> meta['end_logs']
        1
    """
    end_logs = [log for log in logs if log.get('status') == 'END']
    
    return {
        'step': config['step'],
        'log_file': config['log_file'],
        'extraction_time': datetime.now().isoformat(),
        'total_logs': len(logs),
        'end_logs': len(end_logs),
        'extractor_version': '1.0'
    }


def save_to_json(data: Dict, output_path: Path):
    """
    데이터를 JSON 파일로 저장
    
    Args:
        data: 저장할 데이터 ({'metadata': {...}, 'logs': [...]})
        output_path: 출력 파일 경로 (Path 객체)
    
    Raises:
        IOError: 파일 쓰기 실패 시
        
    Example:
        >>> data = {'metadata': {...}, 'logs': [...]}
        >>> save_to_json(data, Path('results/r01/r01_parsed_data.json'))
    """
    # 디렉토리 생성 (없으면)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        file_size = output_path.stat().st_size
        file_size_mb = file_size / (1024 * 1024)
        print(f"  - 파일 크기: {file_size_mb:.2f} MB")
        
    except IOError as e:
        raise IOError(f"JSON 파일 저장 실패: {output_path}") from e


def validate_parsed_data(data: Dict) -> bool:
    """
    파싱된 데이터 유효성 검증
    
    Args:
        data: 파싱된 데이터 ({'metadata': {...}, 'logs': [...]})
    
    Returns:
        bool: 유효하면 True, 아니면 False
    
    검증 항목:
        - metadata 필드 존재
        - logs 필드 존재 및 배열 타입
        - logs 배열이 비어있지 않음
        - 필수 필드 (step, action, status) 존재
    """
    if 'metadata' not in data:
        print("  Error: metadata 필드가 없습니다")
        return False
    
    if 'logs' not in data:
        print("  Error: logs 필드가 없습니다")
        return False
    
    if not isinstance(data['logs'], list):
        print("  Error: logs는 배열이어야 합니다")
        return False
    
    if len(data['logs']) == 0:
        print("  Warning: logs 배열이 비어있습니다")
        return True  # 경고지만 치명적 오류는 아님
    
    # 첫 번째 로그 샘플 검증
    first_log = data['logs'][0]
    required_fields = ['step', 'action', 'status']
    
    for field in required_fields:
        if field not in first_log:
            print(f"  Error: 필수 필드 '{field}'가 로그에 없습니다")
            return False
    
    return True
