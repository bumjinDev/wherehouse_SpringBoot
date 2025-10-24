"""
R-07 Report Generator - 최종 응답 생성 성능 보고서 생성

이 스크립트는 R-07 중간 데이터를 읽어 4-Sheet Excel 보고서를 생성합니다.

R-07 단계 분석:
- 최종 응답 생성 시간 분석
- L1 캐시 쓰기 성능 분석
- 응답 크기 분석
- 추천/경고사항 생성 분석

입력: r07_parsed_data.json
출력: r07_analysis.xlsx (4 Sheets)

Sheet 구조:
- Sheet 1: Step_Summary (메인 루틴 통계)
- Sheet 2: Action_Breakdown (모든 Action 통계)
- Sheet 3: ResultData_Analysis (응답 생성 지표)
- Sheet 4: Raw_Data (원본 JSON 데이터)

실행 방법:
    python r07_report_generator.py

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


def create_r07_resultdata_sheet(df: pd.DataFrame, writer):
    """
    R-07 전용 ResultData_Analysis 시트 생성
    
    R-07 resultData 구조:
    - analysisStatus: 분석 상태 ("SUCCESS")
    - hasAddress: 주소 정보 존재 여부
    - hasSafetyScore: 안전성 점수 존재 여부
    - hasConvenienceScore: 편의성 점수 존재 여부
    - recommendations: 추천사항 리스트
    - warnings: 경고사항 리스트
    - cacheWrite: L1 캐시 쓰기 결과
        - cacheKey: 캐시 키 (예: "dto:wydm9qw")
        - dataSize: 데이터 크기 (bytes)
        - ttlSeconds: TTL (초, 300 = 5분)
        - success: 캐시 쓰기 성공 여부
    - responseSizeBytes: 응답 크기 (bytes)
    - success: R-07 전체 성공 여부
    
    측정 지표:
    - 응답 생성 성공률
    - L1 캐시 쓰기 성공률
    - 응답 크기 통계
    - 추천/경고사항 개수
    - 캐시 쓰기 시간 (전체 duration에서 추정)
    """
    metrics_config = {
        # ============================================
        # 응답 생성 지표
        # ============================================
        'response_size_bytes': {
            'path': 'responseSizeBytes',
            'description': '응답 크기 (bytes)'
        },
        'response_size_kb': {
            'path': 'responseSizeBytes',
            'description': '응답 크기 (KB)',
            'transform': lambda x: round(x / 1024, 2) if x else 0
        },
        
        # ============================================
        # 추천/경고사항
        # ============================================
        'recommendations_count': {
            'path': 'recommendations',
            'description': '추천사항 개수',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        'warnings_count': {
            'path': 'warnings',
            'description': '경고사항 개수',
            'transform': lambda x: len(x) if isinstance(x, list) else 0
        },
        
        # ============================================
        # 데이터 존재 여부
        # ============================================
        'has_address_rate': {
            'path': 'hasAddress',
            'description': '주소 정보 존재율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'has_safety_score_rate': {
            'path': 'hasSafetyScore',
            'description': '안전성 점수 존재율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'has_convenience_score_rate': {
            'path': 'hasConvenienceScore',
            'description': '편의성 점수 존재율 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        
        # ============================================
        # L1 캐시 쓰기
        # ============================================
        'cache_write_success_rate': {
            'path': 'cacheWrite.success',
            'description': 'L1 캐시 쓰기 성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        },
        'cache_data_size_bytes': {
            'path': 'cacheWrite.dataSize',
            'description': '캐시 데이터 크기 (bytes)'
        },
        'cache_data_size_kb': {
            'path': 'cacheWrite.dataSize',
            'description': '캐시 데이터 크기 (KB)',
            'transform': lambda x: round(x / 1024, 2) if x else 0
        },
        'cache_ttl_seconds': {
            'path': 'cacheWrite.ttlSeconds',
            'description': '캐시 TTL (초)'
        },
        'cache_ttl_minutes': {
            'path': 'cacheWrite.ttlSeconds',
            'description': '캐시 TTL (분)',
            'transform': lambda x: round(x / 60, 1) if x else 0
        },
        
        # ============================================
        # 성공률
        # ============================================
        'overall_success_rate': {
            'path': 'success',
            'description': 'R-07 전체 성공률 (%)',
            'transform': lambda x: 100.0 if x else 0.0
        }
    }
    
    create_resultdata_sheet_base(df, writer, metrics_config, 'ResultData_Analysis')


def main():
    """R-07 보고서 생성 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    RESULT_BASE_PATH = '/home/claude/results'
    
    # 설정
    config = {
        'step': 'R-07',
        'input_file': os.path.join(RESULT_BASE_PATH, 'r07', 'r07_parsed_data.json'),
        'output_file': os.path.join(RESULT_BASE_PATH, 'r07', 'r07_analysis.xlsx')
    }
    
    print("\n" + "=" * 70)
    print(f"R-07 Report Generator 시작")
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
        
        # 응답 생성 샘플 출력
        df_end = df[df['status'] == 'END']
        if len(df_end) > 0 and 'resultData' in df_end.iloc[0]:
            sample = df_end.iloc[0]['resultData']
            if isinstance(sample, dict):
                print(f"\n  ✓ 응답 생성 샘플:")
                print(f"      - 분석 상태: {sample.get('analysisStatus', 'N/A')}")
                print(f"      - 응답 크기: {sample.get('responseSizeBytes', 0):,} bytes")
                print(f"      - 추천사항: {len(sample.get('recommendations', []))}개")
                print(f"      - 경고사항: {len(sample.get('warnings', []))}개")
                
                cache_write = sample.get('cacheWrite', {})
                if cache_write:
                    print(f"      - 캐시 쓰기: {'성공' if cache_write.get('success') else '실패'}")
        
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
            create_r07_resultdata_sheet(df, writer)
            
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
        print(f"✅ R-07 보고서 생성 완료!")
        print(f"✅ 출력 파일: {config['output_file']}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - Sheet 3에서 응답 생성 성능 지표 확인")
        print("  - L1 캐시 쓰기 성공률 및 데이터 크기")
        print("  - 추천/경고사항 생성 패턴")
        print("  - 전체 응답 생성 소요 시간 (duration_ms)\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n먼저 r07_data_extractor.py를 실행하세요!")
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
