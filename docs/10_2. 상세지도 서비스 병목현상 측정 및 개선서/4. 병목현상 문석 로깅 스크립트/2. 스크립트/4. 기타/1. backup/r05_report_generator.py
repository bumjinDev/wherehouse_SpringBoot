"""
R-05 Report Generator - 데이터 통합 및 필터링 성능 보고서 생성

이 스크립트는 R-05 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

분석 포인트:
- 파출소 조회 병목 (B-01): 221ms (전체의 96%)
- DB 전체 스캔으로 인한 성능 저하
- Spatial 인덱스 필요

입력: r05_parsed_data.json
출력: r05_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (10개 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r05_report_generator.py

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


def create_r05_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-05 전용 ResultData_Analysis 시트 생성
    
    R-05 resultData 구조:
    - requestRadius: 요청 반경 (500m)
    - cctvFilter: CCTV 필터링 결과
        - totalCctvBeforeFilter, totalCctvAfterFilter
        - totalCameraCount, filterRate, filterExecutionTimeNs
    - policeQuery: 파출소 조회 결과 (B-01 병목!)
        - queryDurationNs, found, nearestAddress, nearestDistance
    - amenityFilter: 편의시설 필터링 결과
        - totalBeforeFilter, totalAfterFilter
        - filterRate, filterExecutionTimeNs
    
    측정 지표 (10개):
    - CCTV 필터링: 4개
    - 파출소 조회: 3개
    - 편의시설 필터링: 3개
    """
    metrics_config = {
        # ===== CCTV 필터링 (4개) =====
        'cctvBeforeFilter': {
            'path': 'cctvFilter.totalCctvBeforeFilter',
            'description': 'CCTV 필터링 전 개수'
        },
        'cctvAfterFilter': {
            'path': 'cctvFilter.totalCctvAfterFilter',
            'description': 'CCTV 필터링 후 개수'
        },
        'cctvFilterRate': {
            'path': 'cctvFilter.filterRate',
            'description': 'CCTV 필터 통과율 (%)',
            'transform': lambda x: round(x * 100, 2) if x else None
        },
        'cctvFilterMs': {
            'path': 'cctvFilter.filterExecutionTimeNs',
            'description': 'CCTV 필터링 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        
        # ===== 파출소 조회 (3개) - B-01 병목! =====
        'policeQueryMs': {
            'path': 'policeQuery.queryDurationNs',
            'description': '파출소 조회 실행 시간 (ms) [B-01 병목]',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'policeFound': {
            'path': 'policeQuery.found',
            'description': '파출소 발견 성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'policeDistance': {
            'path': 'policeQuery.nearestDistance',
            'description': '가장 가까운 파출소 거리 (m)',
            'transform': lambda x: round(x, 2) if x else None
        },
        
        # ===== 편의시설 필터링 (3개) =====
        'amenityBeforeFilter': {
            'path': 'amenityFilter.totalBeforeFilter',
            'description': '편의시설 필터링 전 개수'
        },
        'amenityAfterFilter': {
            'path': 'amenityFilter.totalAfterFilter',
            'description': '편의시설 필터링 후 개수'
        },
        'amenityFilterMs': {
            'path': 'amenityFilter.filterExecutionTimeNs',
            'description': '편의시설 필터링 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-05 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = '/home/claude'
    
    # 설정
    config = {
        'step': 'R-05',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r05', 'r05_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r05', 'r05_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-05 Report Generator 시작")
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
            create_r05_resultdata_sheet(df, writer)
            
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
        print(f"✅ R-05 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - Sheet 3에서 파출소 조회 시간 확인 (221ms)")
        print("  - 전체의 96%가 파출소 조회 (B-01 병목)")
        print("  - Spatial 인덱스 필요\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r05_data_extractor.py를 실행하세요!")
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
