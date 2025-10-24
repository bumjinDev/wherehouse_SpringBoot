"""
R-01 Report Generator - 9-Block 그리드 계산 성능 보고서 생성

이 스크립트는 R-01 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

입력: r01_parsed_data.json
출력: r01_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (비즈니스 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터 - 선택사항)

실행 방법:
    python r01_report_generator.py

작성자: 정범진
작성일: 2025-01-24
"""

import sys
import os
from pathlib import Path
import pandas as pd

# 공통 유틸리티 import
sys.path.append(str(Path(__file__).parent.parent / 'common'))
from generator_utils import (
    load_parsed_data,
    create_step_summary_sheet,
    create_action_breakdown_sheet,
    create_resultdata_sheet_base,
    create_raw_data_sheet,
    validate_excel_output
)


def create_r01_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-01 전용 ResultData_Analysis 시트 생성
    
    R-01 resultData 구조:
    - Service Layer (calculate9BlockGrid):
        - requestLatitude, requestLongitude, requestRadius
        - centerGeohashId, nineBlockGeohashes
        - totalGridCount (항상 9)
        - errorMessage, success
    
    - Utility Layer (calculate9BlockGeohashes):
        - latitude, longitude, precision
        - centerHash, adjacentHashes
        - errorMessage, success
    
    측정 지표:
    - totalGridCount: 9-Block 개수 (검증용, 항상 9)
    - success_rate: 성공률 (%)
    """
    metrics_config = {
        'totalGridCount': {
            'path': 'totalGridCount',
            'description': '9-Block 그리드 개수 (항상 9)'
        },
        'nineBlockGeohashes_count': {
            'path': 'nineBlockGeohashes',
            'description': 'Geohash 배열 길이',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        'success_rate': {
            'path': 'success',
            'description': '성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'has_error': {
            'path': 'errorMessage',
            'description': '에러 발생 여부 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-01 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = r'E:\devSpace\results'
    
    # 설정
    config = {
        'step': 'R-01',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r01', 'r01_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r01', 'r01_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-01 Report Generator 시작")
    print("=" * 70)
    print(f"입력 파일: {config['input_file']}")
    print(f"출력 파일: {config['output_file']}")
    print("-" * 70)
    
    try:
        # 1. 중간 파일 로드
        print(f"\n[1/4] 중간 파일 로드 중...")
        data = load_parsed_data(config['input_file'])
        df = pd.DataFrame(data['logs'])
        print(f"  ✓ 로드 완료: {len(df)}개 로그")
        
        # 2. DataFrame 확인
        print(f"\n[2/4] 데이터 확인 중...")
        print(f"  ✓ START 로그: {len(df[df['status'] == 'START'])}개")
        print(f"  ✓ END 로그: {len(df[df['status'] == 'END'])}개")
        print(f"  ✓ 레이어 분포:")
        for layer, count in df['layer'].value_counts().items():
            print(f"      - {layer}: {count}개")
        
        # 3. Excel 생성 (4-Sheet)
        print(f"\n[3/4] Excel 보고서 생성 중...")
        with pd.ExcelWriter(config['output_file'], engine='openpyxl') as writer:
            # Sheet 1: Step_Summary
            print(f"  - Sheet 1: Step_Summary")
            create_step_summary_sheet(df, writer, 'Step_Summary')
            
            # Sheet 2: Action_Breakdown
            print(f"  - Sheet 2: Action_Breakdown")
            create_action_breakdown_sheet(df, writer, 'Action_Breakdown')
            
            # Sheet 3: ResultData_Analysis
            print(f"  - Sheet 3: ResultData_Analysis")
            create_r01_resultdata_sheet(df, writer)
            
            # Sheet 4: Raw_Data (선택사항 - JSON 문자열로 저장)
            print(f"  - Sheet 4: Raw_Data")
            create_raw_data_sheet(df, writer, 'Raw_Data', include_json_string=True)
        
        print(f"  ✓ Excel 생성 완료")
        
        # 4. 검증
        print(f"\n[4/4] Excel 파일 검증 중...")
        is_valid = validate_excel_output(config['output_file'])
        if not is_valid:
            raise ValueError("Excel 파일 검증 실패")
        
        print("\n" + "=" * 70)
        print(f"✅ R-01 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70 + "\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r01_data_extractor.py를 실행하세요!")
        print(f"  예상 경로: {config['input_file']}\n")
        sys.exit(1)
        
    except Exception as e:
        print("\n" + "=" * 70)
        print(f"❌ 오류 발생: {e}")
        print("=" * 70)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
