"""
extractor_utils.py - 성능 로그 파싱 공통 유틸리티

Logback 형식의 PERFORMANCE 로그를 파싱하여 분석 가능한 JSON 형태로 변환
"""
import json
from datetime import datetime
from pathlib import Path


def parse_ndjson_log(log_file_path, target_step=None):
    """
    Logback 형식의 로그에서 JSON 부분만 추출하여 파싱
    
    로그 형식:
    2025-11-21 14:05:41.897 [http-nio-8185-exec-1] INFO  PERFORMANCE - {"timestamp":...}
    
    Args:
        log_file_path: 로그 파일 경로
        target_step: 필터링할 step (예: "R-01"), None이면 모든 PERFORMANCE 로그
    
    Returns:
        파싱된 로그 딕셔너리 리스트
    """
    logs = []
    parse_errors = 0
    
    print(f"  로그 파일 읽는 중: {log_file_path}")
    
    with open(log_file_path, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            
            # PERFORMANCE 로그가 아니면 스킵
            if 'PERFORMANCE' not in line:
                continue
            
            # JSON 부분 추출: " - {" 이후부터 끝까지
            json_start = line.find(' - {')
            if json_start == -1:
                continue
            
            json_str = line[json_start + 3:].strip()  # " - " 이후 부분
            
            try:
                log_entry = json.loads(json_str)
                
                # target_step 필터링
                if target_step and log_entry.get('step') != target_step:
                    continue
                
                logs.append(log_entry)
                
            except json.JSONDecodeError as e:
                parse_errors += 1
                if parse_errors <= 5:  # 처음 5개만 출력
                    print(f"  Warning: JSON 파싱 실패 (Line {line_num}): {str(e)}")
                    print(f"    문제 라인: {json_str[:100]}...")
    
    if parse_errors > 5:
        print(f"  ... 총 {parse_errors - 5}개 추가 파싱 오류 발생")
    
    print(f"  ✓ 총 {len(logs)}개 로그 파싱 완료")
    
    return logs


def clean_log_data(logs):
    """
    파싱된 로그 데이터 정제
    
    - 중복 제거
    - 필수 필드 검증
    - 타입 변환
    
    Args:
        logs: 파싱된 로그 리스트
    
    Returns:
        정제된 로그 리스트
    """
    cleaned = []
    seen_traces = set()
    
    for log in logs:
        # 필수 필드 검증
        if not all(k in log for k in ['timestamp', 'step', 'status']):
            continue
        
        # 중복 제거 (traceId + layer + status 조합)
        # 같은 요청 내에서 여러 레이어의 로그를 모두 보존
        trace_key = f"{log.get('traceId', '')}_{log.get('layer', '')}_{log.get('status', '')}"
        if trace_key in seen_traces:
            continue
        seen_traces.add(trace_key)
        
        cleaned.append(log)
    
    return cleaned


def extract_result_data(logs, step):
    """
    resultData 필드를 무손실로 추출
    
    Args:
        logs: 정제된 로그 리스트
        step: 대상 step (예: "R-01")
    
    Returns:
        resultData가 추출된 로그 리스트
    """
    # resultData는 이미 로그 안에 있으므로 별도 처리 불필요
    # 필요시 여기서 추가 변환 가능
    return logs


def create_metadata(config, logs):
    """
    메타데이터 생성
    
    Args:
        config: 설정 딕셔너리
        logs: 로그 리스트
    
    Returns:
        메타데이터 딕셔너리
    """
    end_logs = [log for log in logs if log.get('status') == 'END']
    
    return {
        'step': config['step'],
        'extracted_at': datetime.now().isoformat(),
        'source_file': config['log_file'],
        'total_logs': len(logs),
        'end_logs': len(end_logs),
        'output_file': config['output_file']
    }


def save_to_json(data, output_path):
    """
    JSON 파일로 저장
    
    Args:
        data: 저장할 데이터
        output_path: 출력 파일 경로
    """
    # 디렉토리 생성
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    # JSON 저장
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    # 파일 크기 확인
    file_size = output_path.stat().st_size / (1024 * 1024)  # MB
    print(f"  - 파일 크기: {file_size:.2f} MB")


def validate_parsed_data(data):
    """
    파싱된 데이터 검증
    
    Args:
        data: 검증할 데이터 딕셔너리
    
    Returns:
        검증 성공 여부
    """
    # 메타데이터 존재 확인
    if 'metadata' not in data:
        print("  ✗ 메타데이터 누락")
        return False
    
    # 로그 존재 확인
    if 'logs' not in data or not data['logs']:
        print("  ✗ 로그 데이터 누락")
        return False
    
    # END 로그 확인
    end_logs = [log for log in data['logs'] if log.get('status') == 'END']
    if not end_logs:
        print("  ⚠ 경고: END 로그가 없습니다")
    
    return True