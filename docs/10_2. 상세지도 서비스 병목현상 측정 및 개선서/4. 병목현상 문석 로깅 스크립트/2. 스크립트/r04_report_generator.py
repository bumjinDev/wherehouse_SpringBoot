"""
R-04 Report Generator - 외부 API 호출 성능 보고서 생성

이 스크립트는 R-04 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

분석 포인트:
- 순차 실행 병목: 3개 API × 평균 894ms = 2,681ms
- 편의시설 API가 전체의 60% 차지 (1,615ms)
- 캐시 히트 시 99% 성능 향상 가능

입력: r04_parsed_data.json
출력: r04_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (15개 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r04_report_generator.py

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


def create_r04_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-04 전용 ResultData_Analysis 시트 생성
    
    R-04 resultData 구조:
    - totalSequentialTasks: 순차 실행 태스크 수 (3개)
    - totalExecutionTimeNs: 전체 실행 시간
    - addressApiResult: 주소 변환 API 결과
        - cached, executionTimeNs, responseSize, success
    - arrestRateResult: 검거율 조회 결과
        - cached, executionTimeNs, arrestRate
    - amenityApiResult: 편의시설 API 결과
        - cached, executionTimeNs, categoryCount, totalPlaces, responseSize
    
    측정 지표 (15개):
    - 전체 통계: 3개
    - 주소 API: 4개
    - 검거율: 3개
    - 편의시설 API: 5개
    """
    metrics_config = {
        # ===== 전체 통계 (3개) =====
        'totalSequentialTasks': {
            'path': 'totalSequentialTasks',
            'description': '순차 실행 태스크 수'
        },
        'totalExecutionMs': {
            'path': 'totalExecutionTimeNs',
            'description': '전체 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'avgTaskMs': {
            'path': 'totalExecutionTimeNs',
            'description': '태스크당 평균 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000 / 3, 3) if x else None
        },
        
        # ===== 주소 변환 API (4개) =====
        'addressCacheHitRate': {
            'path': 'addressApiResult.cached',
            'description': '주소 API 캐시 히트율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'addressExecutionMs': {
            'path': 'addressApiResult.executionTimeNs',
            'description': '주소 API 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'addressResponseSize': {
            'path': 'addressApiResult.responseSize',
            'description': '주소 API 응답 크기 (bytes)'
        },
        'addressSuccessRate': {
            'path': 'addressApiResult.success',
            'description': '주소 API 성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        
        # ===== 검거율 조회 (3개) =====
        'arrestRateCacheHitRate': {
            'path': 'arrestRateResult.cached',
            'description': '검거율 캐시 히트율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'arrestRateExecutionMs': {
            'path': 'arrestRateResult.executionTimeNs',
            'description': '검거율 조회 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'arrestRateValue': {
            'path': 'arrestRateResult.arrestRate',
            'description': '검거율 값',
            'transform': lambda x: round(x, 3) if x else None
        },
        
        # ===== 편의시설 API (5개) =====
        'amenityCacheHitRate': {
            'path': 'amenityApiResult.cached',
            'description': '편의시설 API 캐시 히트율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'amenityExecutionMs': {
            'path': 'amenityApiResult.executionTimeNs',
            'description': '편의시설 API 실행 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x else None
        },
        'amenityCategoryCount': {
            'path': 'amenityApiResult.categoryCount',
            'description': '편의시설 조회 카테고리 수'
        },
        'amenityTotalPlaces': {
            'path': 'amenityApiResult.totalPlaces',
            'description': '편의시설 총 장소 수'
        },
        'amenityResponseSize': {
            'path': 'amenityApiResult.responseSize',
            'description': '편의시설 API 응답 크기 (bytes)'
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-04 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = '/home/claude'
    
    # 설정
    config = {
        'step': 'R-04',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r04', 'r04_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r04', 'r04_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-04 Report Generator 시작")
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
            create_r04_resultdata_sheet(df, writer)
            
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
        print(f"✅ R-04 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - Sheet 3에서 전체 실행 시간 확인 (2,681ms)")
        print("  - 편의시설 API가 60% 차지 (1,615ms)")
        print("  - 순차 실행 병목 → 병렬 실행 시 40% 단축 가능\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r04_data_extractor.py를 실행하세요!")
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
