"""
R-06 Report Generator - 점수 계산 성능 보고서 생성

이 스크립트는 R-06 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

R-06 단계 분석:
- 안전성 점수 세부 분석 (파출소, CCTV, 검거율)
- 편의성 점수 세부 분석 (15개 카테고리별)
- 종합 점수 분석
- 점수 계산 소요 시간 분석

입력: r06_parsed_data.json
출력: r06_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (점수 지표 분석)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r06_report_generator.py

작성자: 정범진
작성일: 2025-01-24
"""

import sys
import os
from pathlib import Path
import pandas as pd

# 공통 유틸리티 import
sys.path.insert(0, '/home/claude/common')
from generator_utils import (
    load_parsed_data,
    create_step_summary_sheet,
    create_action_breakdown_sheet,
    create_resultdata_sheet_base,
    create_raw_data_sheet,
    validate_excel_output
)


def create_r06_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-06 전용 ResultData_Analysis 시트 생성
    
    R-06 resultData 구조:
    - safetyScore: 안전성 점수 상세
        - policeDistanceScore: 파출소 거리 점수 (0-100)
        - policeDistance: 실제 파출소 거리 (m)
        - cctvScore: CCTV 점수 (0-100)
        - cctvCount: CCTV 개수
        - arrestRateScore: 검거율 점수 (0-100)
        - arrestRate: 실제 검거율 (0.0-1.0)
        - finalScore: 안전성 최종 점수 (0-100)
    
    - convenienceScore: 편의성 점수 상세
        - categoryScores: 카테고리별 점수 (Map)
        - currentGu: 현재 구 이름
        - guPopulation: 구 인구수
        - finalScore: 편의성 최종 점수 (0-100)
    
    - overallScore: 종합 점수 (0-100)
    
    측정 지표:
    - 안전성 3요소 점수 (파출소, CCTV, 검거율)
    - 편의성 점수
    - 종합 점수
    - 성공률
    """
    metrics_config = {
        # ============================================
        # 안전성 점수
        # ============================================
        'safety_final_score': {
            'path': 'safetyScore.finalScore',
            'description': '안전성 최종 점수 (0-100)'
        },
        'safety_police_distance_score': {
            'path': 'safetyScore.policeDistanceScore',
            'description': '파출소 거리 점수 (0-100)'
        },
        'safety_police_distance': {
            'path': 'safetyScore.policeDistance',
            'description': '실제 파출소 거리 (m)'
        },
        'safety_cctv_score': {
            'path': 'safetyScore.cctvScore',
            'description': 'CCTV 점수 (0-100)'
        },
        'safety_cctv_count': {
            'path': 'safetyScore.cctvCount',
            'description': 'CCTV 개수'
        },
        'safety_arrest_rate_score': {
            'path': 'safetyScore.arrestRateScore',
            'description': '검거율 점수 (0-100)'
        },
        'safety_arrest_rate': {
            'path': 'safetyScore.arrestRate',
            'description': '실제 검거율 (0.0-1.0)'
        },
        
        # ============================================
        # 편의성 점수
        # ============================================
        'convenience_final_score': {
            'path': 'convenienceScore.finalScore',
            'description': '편의성 최종 점수 (0-100)'
        },
        'convenience_gu_population': {
            'path': 'convenienceScore.guPopulation',
            'description': '구 인구수'
        },
        
        # 카테고리별 개별 점수 (주요 5개만 샘플)
        'convenience_cs2_score': {
            'path': 'convenienceScore.categoryScores.CS2',
            'description': '편의점 점수 (CS2)'
        },
        'convenience_fd6_score': {
            'path': 'convenienceScore.categoryScores.FD6',
            'description': '음식점 점수 (FD6)'
        },
        'convenience_ce7_score': {
            'path': 'convenienceScore.categoryScores.CE7',
            'description': '카페 점수 (CE7)'
        },
        'convenience_sw8_score': {
            'path': 'convenienceScore.categoryScores.SW8',
            'description': '지하철역 점수 (SW8)'
        },
        'convenience_hp8_score': {
            'path': 'convenienceScore.categoryScores.HP8',
            'description': '병원 점수 (HP8)'
        },
        
        # ============================================
        # 종합 점수
        # ============================================
        'overall_score': {
            'path': 'overallScore',
            'description': '종합 점수 (0-100) - (안전성+편의성)/2'
        },
        
        # ============================================
        # 성공률
        # ============================================
        'success_rate': {
            'path': 'success',
            'description': '성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-06 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = '/home/claude/results'
    
    # 설정
    config = {
        'step': 'R-06',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r06', 'r06_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r06', 'r06_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-06 Report Generator 시작")
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
        
        # 점수 샘플 출력
        df_end = df[df['status'] == 'END']
        if len(df_end) > 0 and 'resultData' in df_end.iloc[0]:
            sample = df_end.iloc[0]['resultData']
            if isinstance(sample, dict):
                print(f"\n  ✓ 점수 샘플:")
                print(f"      - 안전성: {sample.get('safetyScore', {}).get('finalScore', 'N/A')}")
                print(f"      - 편의성: {sample.get('convenienceScore', {}).get('finalScore', 'N/A')}")
                print(f"      - 종합: {sample.get('overallScore', 'N/A')}")
        
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
            create_r06_resultdata_sheet(df, writer)
            
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
        print(f"✅ R-06 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - Sheet 3에서 안전성/편의성/종합 점수 통계 확인")
        print("  - 평균 점수, 최소/최대 점수 분석")
        print("  - 카테고리별 점수 분포 확인")
        print("  - 점수 계산 소요 시간 (duration_ms) 확인\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r06_data_extractor.py를 실행하세요!")
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
