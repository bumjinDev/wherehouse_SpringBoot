"""
Generator Utils - 보고서 생성 계층 공통 함수

이 모듈은 모든 r0X_report_generator.py 스크립트에서 공통으로 사용하는 함수를 제공합니다.

주요 함수:
- load_parsed_data(): 중간 JSON 파일 로드
- create_step_summary_sheet(): Sheet 1 (Step_Summary) 생성
- create_action_breakdown_sheet(): Sheet 2 (Action_Breakdown) 생성
- create_resultdata_sheet_base(): Sheet 3 (ResultData_Analysis) 생성 베이스
- extract_nested_field(): 중첩 딕셔너리 필드 추출

작성자: 정범진
작성일: 2025-01-24

=============================================================================
경로 설정 (Windows 로컬 환경)
=============================================================================
실제 사용 시 아래 경로를 자신의 환경에 맞게 수정하세요:

RESULT_BASE_PATH = r'E:\devSpace\results'

예제:
    input_file = os.path.join(RESULT_BASE_PATH, 'r01', 'r01_parsed_data.json')
    output_file = os.path.join(RESULT_BASE_PATH, 'r01', 'r01_analysis.xlsx')
=============================================================================
"""

import json
import pandas as pd
from pathlib import Path
from typing import Dict, Any, Callable


def load_parsed_data(input_file: str) -> Dict:
    """
    Extractor가 생성한 JSON 파일 로드
    
    Args:
        input_file: 중간 파일 경로 (예: r'E:\devSpace\results\r01\r01_parsed_data.json')
    
    Returns:
        Dict: {'metadata': {...}, 'logs': [...]}
    
    Raises:
        FileNotFoundError: 파일이 없는 경우
        ValueError: JSON 포맷이 잘못된 경우
        
    Example:
        >>> data = load_parsed_data(r'E:\devSpace\results\r01\r01_parsed_data.json')
        >>> data['metadata']['step']
        'R-01'
        >>> len(data['logs'])
        150
    """
    input_path = Path(input_file)
    
    if not input_path.exists():
        raise FileNotFoundError(f"중간 파일을 찾을 수 없습니다: {input_file}")
    
    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        raise ValueError(f"JSON 파일 파싱 실패: {input_file}") from e
    
    # 필수 필드 검증
    if 'metadata' not in data:
        raise ValueError("metadata 필드가 없습니다")
    if 'logs' not in data:
        raise ValueError("logs 필드가 없습니다")
    if not isinstance(data['logs'], list):
        raise ValueError("logs는 배열이어야 합니다")
    
    print(f"  - 메타데이터: {data['metadata']['step']}, "
          f"총 {data['metadata']['total_logs']}개 로그, "
          f"END {data['metadata']['end_logs']}개")
    
    return data


def create_step_summary_sheet(df: pd.DataFrame, writer, sheet_name='Step_Summary'):
    """
    Sheet 1: Step_Summary 생성 (메인 루틴만)
    
    메인 루틴 정의: layer가 'Service'인 로그
    
    Args:
        df: 로그 DataFrame
        writer: pd.ExcelWriter 객체
        sheet_name: 시트 이름 (기본값: 'Step_Summary')
    
    컬럼:
        - step, action, layer, class, method
        - avg_ms, median_ms, p95_ms, min_ms, max_ms, std_ms, count
        
    Example:
        >>> df = pd.DataFrame(data['logs'])
        >>> with pd.ExcelWriter('output.xlsx') as writer:
        ...     create_step_summary_sheet(df, writer)
    """
    # END 로그 + Service 레이어만 필터링
    df_main = df[(df['status'] == 'END') & (df['layer'] == 'Service')].copy()
    
    if len(df_main) == 0:
        print(f"  Warning: {sheet_name} - 메인 루틴 로그가 없습니다 (Service 레이어)")
        # 빈 DataFrame 생성
        summary = pd.DataFrame(columns=[
            'step', 'action', 'layer', 'class', 'method',
            'avg_ms', 'median_ms', 'p95_ms', 'min_ms', 'max_ms', 'std_ms', 'count'
        ])
    else:
        # 통계 계산
        summary = df_main.groupby(['step', 'action', 'layer', 'class', 'method']).agg(
            avg_ms=('duration_ms', 'mean'),
            median_ms=('duration_ms', 'median'),
            p95_ms=('duration_ms', lambda x: x.quantile(0.95)),
            min_ms=('duration_ms', 'min'),
            max_ms=('duration_ms', 'max'),
            std_ms=('duration_ms', 'std'),
            count=('duration_ms', 'count')
        ).round(3).reset_index()
        
        print(f"  - {sheet_name}: {len(summary)}개 메인 루틴")
    
    # Excel 쓰기
    summary.to_excel(writer, sheet_name=sheet_name, index=False)


def create_action_breakdown_sheet(df: pd.DataFrame, writer, sheet_name='Action_Breakdown'):
    """
    Sheet 2: Action_Breakdown 생성 (모든 Action)
    
    Args:
        df: 로그 DataFrame
        writer: pd.ExcelWriter 객체
        sheet_name: 시트 이름 (기본값: 'Action_Breakdown')
    
    컬럼:
        - step, layer, class, method, action
        - avg_ms, median_ms, p95_ms, min_ms, max_ms, std_ms, count
        
    Example:
        >>> df = pd.DataFrame(data['logs'])
        >>> with pd.ExcelWriter('output.xlsx') as writer:
        ...     create_action_breakdown_sheet(df, writer)
    """
    # END 로그만 필터링
    df_end = df[df['status'] == 'END'].copy()
    
    if len(df_end) == 0:
        print(f"  Warning: {sheet_name} - END 로그가 없습니다")
        # 빈 DataFrame 생성
        breakdown = pd.DataFrame(columns=[
            'step', 'layer', 'class', 'method', 'action',
            'avg_ms', 'median_ms', 'p95_ms', 'min_ms', 'max_ms', 'std_ms', 'count'
        ])
    else:
        # 통계 계산
        breakdown = df_end.groupby(['step', 'layer', 'class', 'method', 'action']).agg(
            avg_ms=('duration_ms', 'mean'),
            median_ms=('duration_ms', 'median'),
            p95_ms=('duration_ms', lambda x: x.quantile(0.95)),
            min_ms=('duration_ms', 'min'),
            max_ms=('duration_ms', 'max'),
            std_ms=('duration_ms', 'std'),
            count=('duration_ms', 'count')
        ).round(3).reset_index()
        
        print(f"  - {sheet_name}: {len(breakdown)}개 Action")
    
    # Excel 쓰기
    breakdown.to_excel(writer, sheet_name=sheet_name, index=False)


def create_resultdata_sheet_base(df: pd.DataFrame, 
                                  writer, 
                                  metrics_config: Dict[str, Dict], 
                                  sheet_name='ResultData_Analysis'):
    """
    Sheet 3: ResultData_Analysis 생성 (단계별 커스터마이징 가능)
    
    Args:
        df: 로그 DataFrame
        writer: pd.ExcelWriter 객체
        metrics_config: 추출할 지표 설정 딕셔너리
        sheet_name: 시트 이름 (기본값: 'ResultData_Analysis')
    
    metrics_config 구조:
        {
            'metric_name': {
                'path': 'resultData 필드 경로 (점으로 구분)',
                'description': '지표 설명',
                'transform': 변환 함수 (선택, lambda x: ...)
            }
        }
    
    Example:
        >>> metrics_config = {
        ...     'totalGridCount': {
        ...         'path': 'totalGridCount',
        ...         'description': '9-Block 개수'
        ...     },
        ...     'success_rate': {
        ...         'path': 'isSuccess',
        ...         'description': '성공률 (%)',
        ...         'transform': lambda x: 100.0 if x else 0.0
        ...     }
        ... }
        >>> create_resultdata_sheet_base(df, writer, metrics_config)
    """
    # END 로그만 필터링
    df_end = df[df['status'] == 'END'].copy()
    
    if len(df_end) == 0:
        print(f"  Warning: {sheet_name} - END 로그가 없습니다")
        return
    
    # resultData 필드가 있는지 확인
    if 'resultData' not in df_end.columns:
        print(f"  Warning: {sheet_name} - resultData 필드가 없습니다")
        return
    
    # 지표 추출
    metrics = []
    for metric_name, config in metrics_config.items():
        path = config['path']
        description = config.get('description', '')
        transform = config.get('transform', None)
        
        # resultData에서 필드 추출 (중첩 지원: 'a.b.c')
        values = df_end['resultData'].apply(
            lambda x: extract_nested_field(x, path) if isinstance(x, dict) else None
        ).dropna()
        
        if len(values) == 0:
            print(f"  Warning: {sheet_name} - '{metric_name}' 필드를 찾을 수 없습니다 (path: {path})")
            continue
        
        # 변환 함수 적용
        if transform:
            try:
                values = values.apply(transform)
            except Exception as e:
                print(f"  Warning: {sheet_name} - '{metric_name}' 변환 실패: {e}")
                continue
        
        # 숫자 타입인 경우에만 통계 계산
        if pd.api.types.is_numeric_dtype(values):
            metrics.append({
                'metric_name': metric_name,
                'avg': round(values.mean(), 3),
                'median': round(values.median(), 3),
                'p95': round(values.quantile(0.95), 3),
                'min': round(values.min(), 3),
                'max': round(values.max(), 3),
                'description': description
            })
        else:
            # 비숫자 타입 (예: 문자열, boolean을 변환한 100%)
            first_value = values.iloc[0] if len(values) > 0 else None
            metrics.append({
                'metric_name': metric_name,
                'avg': first_value,
                'median': None,
                'p95': None,
                'min': None,
                'max': None,
                'description': description
            })
    
    if len(metrics) == 0:
        print(f"  Warning: {sheet_name} - 추출된 지표가 없습니다")
        return
    
    print(f"  - {sheet_name}: {len(metrics)}개 지표")
    
    metrics_df = pd.DataFrame(metrics)
    metrics_df.to_excel(writer, sheet_name=sheet_name, index=False)


def extract_nested_field(data: Dict, path: str) -> Any:
    """
    중첩된 딕셔너리에서 필드 추출
    
    Args:
        data: 딕셔너리
        path: 필드 경로 (점으로 구분, 예: 'a.b.c')
    
    Returns:
        추출된 값 또는 None
    
    Example:
        >>> data = {'a': {'b': {'c': 123}}}
        >>> extract_nested_field(data, 'a.b.c')
        123
        >>> extract_nested_field(data, 'a.b.d')
        None
        >>> extract_nested_field(data, 'x')
        None
    """
    if not isinstance(data, dict):
        return None
    
    keys = path.split('.')
    value = data
    
    for key in keys:
        if isinstance(value, dict) and key in value:
            value = value[key]
        else:
            return None
    
    return value


def validate_excel_output(output_file: str, strict_mode: bool = False) -> bool:
    """
    생성된 Excel 파일 유효성 검증
    
    Args:
        output_file: Excel 파일 경로
        strict_mode: True면 4개 시트 모두 필수, False면 3개만 필수 (기본값)
    
    Returns:
        bool: 유효하면 True, 아니면 False
    
    검증 항목:
        - 파일 존재
        - 필수 시트 존재 (Step_Summary, Action_Breakdown, ResultData_Analysis)
        - 선택 시트 존재 (Raw_Data) - strict_mode=True인 경우만
    """
    output_path = Path(output_file)
    
    if not output_path.exists():
        print(f"  Error: Excel 파일이 생성되지 않았습니다: {output_file}")
        return False
    
    try:
        import openpyxl
        wb = openpyxl.load_workbook(output_file)
        sheet_names = wb.sheetnames
        
        # 필수 시트 (3개)
        required_sheets = ['Step_Summary', 'Action_Breakdown', 'ResultData_Analysis']
        for sheet in required_sheets:
            if sheet not in sheet_names:
                print(f"  Error: 필수 시트 '{sheet}'가 없습니다")
                return False
        
        # 선택 시트 (Sheet 4: Raw_Data)
        if strict_mode and 'Raw_Data' not in sheet_names:
            print(f"  Warning: 선택 시트 'Raw_Data'가 없습니다")
        
        print(f"  ✓ Excel 검증 완료: {len(sheet_names)}개 시트")
        return True
        
    except Exception as e:
        print(f"  Error: Excel 검증 실패: {e}")
        return False


def create_raw_data_sheet(df: pd.DataFrame, 
                          writer, 
                          sheet_name='Raw_Data',
                          include_json_string=True):
    """
    Sheet 4 (선택): Raw_Data 생성 - END 로그의 원본 데이터 저장
    
    Args:
        df: 로그 DataFrame
        writer: pd.ExcelWriter 객체
        sheet_name: 시트 이름 (기본값: 'Raw_Data')
        include_json_string: True면 resultData를 JSON 문자열로, 
                            False면 평탄화하여 저장
    
    컬럼:
        - 기본 필드: traceId, timestamp, step, layer, class, method, action, status
        - 성능 필드: duration_ms
        - resultData: JSON 문자열 또는 평탄화된 개별 컬럼
    
    Example:
        >>> df = pd.DataFrame(data['logs'])
        >>> with pd.ExcelWriter('output.xlsx') as writer:
        ...     create_raw_data_sheet(df, writer, include_json_string=True)
    """
    # END 로그만 필터링
    df_end = df[df['status'] == 'END'].copy()
    
    if len(df_end) == 0:
        print(f"  Warning: {sheet_name} - END 로그가 없습니다")
        return
    
    # 기본 컬럼 선택
    base_columns = [
        'traceId', 'timestamp', 'step', 'layer', 
        'class', 'method', 'action', 'status', 'duration_ms'
    ]
    
    # 존재하는 컬럼만 선택
    available_columns = [col for col in base_columns if col in df_end.columns]
    df_raw = df_end[available_columns].copy()
    
    if include_json_string:
        # 옵션 1: resultData를 JSON 문자열로 저장
        if 'resultData' in df_end.columns:
            df_raw['resultData_JSON'] = df_end['resultData'].apply(
                lambda x: json.dumps(x, ensure_ascii=False) if isinstance(x, dict) else ''
            )
            print(f"  - {sheet_name}: {len(df_raw)}개 로그 (resultData는 JSON 문자열)")
    else:
        # 옵션 2: resultData를 평탄화하여 개별 컬럼으로 저장
        if 'resultData' in df_end.columns:
            # resultData를 DataFrame으로 변환 (평탄화)
            result_df = pd.json_normalize(df_end['resultData'])
            
            # 컬럼명에 'resultData.' 접두사 추가
            result_df.columns = ['resultData.' + col for col in result_df.columns]
            
            # 기존 DataFrame과 결합
            df_raw = pd.concat([df_raw.reset_index(drop=True), 
                               result_df.reset_index(drop=True)], axis=1)
            
            print(f"  - {sheet_name}: {len(df_raw)}개 로그 ({len(result_df.columns)}개 resultData 필드 평탄화)")
    
    # Excel 쓰기
    df_raw.to_excel(writer, sheet_name=sheet_name, index=False)

