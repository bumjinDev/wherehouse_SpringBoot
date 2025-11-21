"""
R-02 Report Generator - 캐시 조회 성능 보고서 생성

이 스크립트는 R-02 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

병목: B-03 (L2 캐시 N+1 쿼리)
분석 포인트:
- L1 캐시 히트율
- L2 캐시 조회 횟수 및 히트율
- L2 캐시 총 소요 시간
- 9개 geohash별 개별 조회 시간

입력: r02_parsed_data.json
출력: r02_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (캐시 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r02_report_generator.py

작성자: 정범진
작성일: 2025-01-24
"""

import sys
import os
from pathlib import Path
import pandas as pd

# 공통 유틸리티 import
# 공통 유틸리티는 같은 디렉토리에 위치
from generator_utils import (
    load_parsed_data,
    create_step_summary_sheet,
    create_action_breakdown_sheet,
    create_resultdata_sheet_base,
    create_raw_data_sheet,
    validate_excel_output
)


def create_r02_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-02 전용 ResultData_Analysis 시트 생성
    
    R-02 resultData 구조:
    - l1CacheHit: L1 캐시 히트 여부
    - l1CacheResult: L1 캐시 조회 결과 및 시간
    - l2CacheRequired: L2 캐시 조회 필요 여부
    - l2CacheResults: 배열 (9개 geohash별 조회)
    - l2TotalHits: L2 캐시 히트 수
    - l2TotalMisses: L2 캐시 미스 수
    - l2CacheTotalDurationNs: L2 전체 소요 시간
    
    측정 지표:
    - L1 캐시 히트율
    - L2 캐시 조회 횟수
    - L2 캐시 히트/미스 수
    - L2 캐시 평균 소요 시간 (ms)
    
    병목 분석:
    - L2 캐시 9번 조회 → 순차 실행으로 인한 병목 (B-03)
    """
    metrics_config = {
        'l1CacheHitRate': {
            'path': 'l1CacheHit',
            'description': 'L1 캐시 히트율 (%)',
            'allow_none': False,  # None은 조회 실패로 간주하여 제거
            'transform': lambda x: 100.0 if x is True else 0.0  # 명시적 True 비교
        },
        'l1CacheDurationMs': {
            'path': 'l1CacheResult.l1CacheGetDurationNs',
            'description': 'L1 캐시 조회 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x is not None else None
        },
        'l2CacheQueryCount': {
            'path': 'l2CacheResults',
            'description': 'L2 캐시 조회 횟수',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        'l2TotalHits': {
            'path': 'l2TotalHits',
            'description': 'L2 캐시 히트 수'
        },
        'l2TotalMisses': {
            'path': 'l2TotalMisses',
            'description': 'L2 캐시 미스 수'
        },
        # l2HitRate 제거: 단일 필드 추출 구조에서 (hits / (hits + misses)) 계산 불가
        # 올바른 히트율 계산은 Excel에서 수동으로 수행 필요
        'l2CacheTotalDurationMs': {
            'path': 'l2CacheTotalDurationNs',
            'description': 'L2 캐시 총 소요 시간 (ms)',
            'transform': lambda x: round(x / 1_000_000, 3) if x is not None else None
        },
        'l2CacheAvgDurationMs': {
            'path': 'l2CacheTotalDurationNs',
            'description': 'L2 캐시 평균 소요 시간 (ms, 9개)',
            'transform': lambda x: round(x / 1_000_000 / 9, 3) if x is not None else None
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-02 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = r'E:\devSpace\results'
    
    # 설정
    config = {
        'step': 'R-02',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r02', 'r02_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r02', 'r02_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-02 Report Generator 시작")
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
            create_r02_resultdata_sheet(df, writer)
            
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
        print(f"✅ R-02 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 병목 분석:")
        print("  - L2 캐시 9번 조회 (순차 실행)")
        print("  - Sheet 3에서 L2 캐시 평균 소요 시간 확인")
        print("  - 병목 코드: B-03 (L2 캐시 N+1 쿼리)\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r02_data_extractor.py를 실행하세요!")
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