"""
R-03 Report Generator - DB 조회 성능 보고서 생성

이 스크립트는 R-03 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

분석 포인트:
- DB 쿼리 실행 시간 (queryExecutionTimeNs)
- 격자별 조회 행 수 분포 (rowsPerGrid)
- 캐시 쓰기 성공률 및 데이터 크기
- 9개 격자 중 데이터가 있는 격자 비율

입력: r03_parsed_data.json
출력: r03_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (DB 조회 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r03_report_generator.py

작성자: 정범진
작성일: 2025-01-24
"""

import sys
import os
from pathlib import Path
import pandas as pd

# 공통 유틸리티 import - 절대 경로 방식
sys.path.insert(0, '/home/claude/common')
from generator_utils import (
    load_parsed_data,
    create_step_summary_sheet,
    create_action_breakdown_sheet,
    create_resultdata_sheet_base,
    create_raw_data_sheet,
    validate_excel_output
)


def create_r03_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-03 전용 ResultData_Analysis 시트 생성
    
    R-03 resultData 구조:
    - inputCctvMissGrids: R-02에서 캐시 미스된 격자 ID 목록
    - cctvQueryResult: CCTV DB 쿼리 실행 결과
        - queryGeohashIds: 쿼리 대상 격자 ID
        - totalRowsReturned: 조회된 총 행 수
        - rowsPerGrid: 격자별 행 수 분포
        - queryExecutionTimeNs: 쿼리 실행 시간 (나노초)
    - cctvCacheWrites: L2 캐시 쓰기 결과 배열
        - dataCount, dataSize, success
    
    측정 지표:
    - DB 쿼리 실행 시간 (ms)
    - 조회된 총 CCTV 행 수
    - 캐시 쓰기 성공 개수
    - 캐시 쓰기 총 데이터 크기 (bytes)
    """
    metrics_config = {
        'queryGridCount': {
            'path': 'inputCctvMissGrids',
            'description': 'DB 쿼리 대상 격자 개수',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        'queryExecutionMs': {
            'path': 'cctvQueryResult.queryExecutionTimeNs',
            'description': 'DB 쿼리 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'totalRowsReturned': {
            'path': 'cctvQueryResult.totalRowsReturned',
            'description': 'DB에서 조회된 총 CCTV 행 수'
        },
        'avgRowsPerGrid': {
            'path': 'cctvQueryResult.totalRowsReturned',
            'description': '격자당 평균 CCTV 행 수',
            'transform': lambda x: round(x / 9, 2) if x else 0  # 9개 격자
        },
        'cacheWriteCount': {
            'path': 'cctvCacheWrites',
            'description': 'L2 캐시 쓰기 시도 개수',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        'cacheWriteSuccessCount': {
            'path': 'cctvCacheWrites',
            'description': 'L2 캐시 쓰기 성공 개수',
            'transform': lambda writes: sum(1 for w in writes if w.get('success', False)) if isinstance(writes, list) else 0
        },
        'cacheWriteSuccessRate': {
            'path': 'cctvCacheWrites',
            'description': 'L2 캐시 쓰기 성공률 (%)',
            'transform': lambda writes: round(sum(1 for w in writes if w.get('success', False)) / len(writes) * 100, 2) if isinstance(writes, list) and len(writes) > 0 else 0
        },
        'totalCacheDataSize': {
            'path': 'cctvCacheWrites',
            'description': 'L2 캐시 총 데이터 크기 (bytes)',
            'transform': lambda writes: sum(w.get('dataSize', 0) for w in writes) if isinstance(writes, list) else 0
        },
        'avgCacheDataSize': {
            'path': 'cctvCacheWrites',
            'description': 'L2 캐시 격자당 평균 데이터 크기 (bytes)',
            'transform': lambda writes: round(sum(w.get('dataSize', 0) for w in writes) / len(writes), 2) if isinstance(writes, list) and len(writes) > 0 else 0
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-03 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = '/home/claude'
    
    # 설정
    config = {
        'step': 'R-03',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r03', 'r03_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r03', 'r03_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-03 Report Generator 시작")
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
            create_r03_resultdata_sheet(df, writer)
            
            # Sheet 4: Raw_Data
            print(f"  - Sheet 4: Raw_Data")
            create_raw_data_sheet(df, writer, 'Raw_Data', include_json_string=True)
        
        print(f"  ✓ Excel 생성 완료")
        
        # 4. 검증
        print(f"\n[4/4] Excel 파일 검증 중...")
        is_valid = validate_excel_output(config['output_file'])
        if not is_valid:
            raise ValueError("Excel 파일 검증 실패")
        
        print("\n" + "=" * 70)
        print(f"✅ R-03 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - Sheet 3에서 DB 쿼리 실행 시간 확인")
        print("  - 격자당 평균 CCTV 행 수 확인")
        print("  - L2 캐시 쓰기 성공률 확인\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r03_data_extractor.py를 실행하세요!")
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
