"""
R-06 Data Extractor - 점수 계산 로그 추출

이 스크립트는 R-06 단계의 로그를 파싱하여 중간 JSON 파일로 저장합니다.

R-06 단계: 최종 점수 계산
- 안전성 점수 계산 (파출소 거리 30% + CCTV 40% + 검거율 30%)
- 편의성 점수 계산 (15개 카테고리별 가중치 적용)
- 종합 점수 계산 (안전성 50% + 편의성 50%)

입력: wherehouse.log (NDJSON 형식)
출력: r06_parsed_data.json

실행 방법:
    python r06_data_extractor.py

작성자: 정범진
작성일: 2025-01-24
"""

import sys
import os
from pathlib import Path

# 공통 유틸리티 import
# 공통 유틸리티는 같은 디렉토리에 위치
from extractor_utils import (
    parse_ndjson_log,
    clean_log_data,
    extract_result_data,
    create_metadata,
    save_to_json,
    validate_parsed_data
)


def main():
    """R-06 로그 추출 메인 함수"""
    
    # =========================================================================
    # 경로 설정 - 실제 환경에 맞게 수정하세요
    # =========================================================================
    LOG_BASE_PATH = r'E:\devSpace\SpringBootProjects\wherehouse_SpringBoot-master\wherehouse\log'
    RESULT_BASE_PATH = r'E:\devSpace\results'
    
    # 설정
    config = {
        'step': 'R-06',
        'log_file': os.path.join(LOG_BASE_PATH, 'wherehouse.log'),
        'output_dir': os.path.join(RESULT_BASE_PATH, 'r06'),
        'output_file': 'r06_parsed_data.json'
    }
    
    print("\n" + "=" * 70)
    print(f"R-06 Data Extractor 시작")
    print("=" * 70)
    print(f"로그 파일: {config['log_file']}")
    print(f"출력 디렉토리: {config['output_dir']}")
    print("-" * 70)
    
    try:
        # 1. 로그 파싱
        print(f"\n[1/6] 로그 파싱 중...")
        logs = parse_ndjson_log(config['log_file'], config['step'])
        print(f"  ✓ 파싱 완료: {len(logs)}개 로그")
        
        # 2. 데이터 정제
        print(f"\n[2/6] 데이터 정제 중...")
        logs = clean_log_data(logs)
        print(f"  ✓ 정제 완료: {len(logs)}개 로그")
        
        # 3. resultData 무손실 보존
        print(f"\n[3/6] resultData 추출 중...")
        logs = extract_result_data(logs, config['step'])
        
        # END 로그 개수 확인
        end_logs = [log for log in logs if log.get('status') == 'END']
        print(f"  ✓ END 로그: {len(end_logs)}개")
        
        # resultData 샘플 출력
        if end_logs and 'resultData' in end_logs[0]:
            sample = end_logs[0]['resultData']
            print(f"  ✓ 안전성 점수: {sample.get('safetyScore', {}).get('finalScore', 'N/A')}")
            print(f"  ✓ 편의성 점수: {sample.get('convenienceScore', {}).get('finalScore', 'N/A')}")
            print(f"  ✓ 종합 점수: {sample.get('overallScore', 'N/A')}")
            
            # 카테고리 점수 개수 확인
            category_scores = sample.get('convenienceScore', {}).get('categoryScores', {})
            print(f"  ✓ 편의시설 카테고리: {len(category_scores)}개")
        
        # 4. 메타데이터 생성
        print(f"\n[4/6] 메타데이터 생성 중...")
        metadata = create_metadata(config, logs)
        print(f"  ✓ step: {metadata['step']}")
        print(f"  ✓ total_logs: {metadata['total_logs']}")
        print(f"  ✓ end_logs: {metadata['end_logs']}")
        
        # 5. 데이터 검증
        print(f"\n[5/6] 데이터 검증 중...")
        data = {
            'metadata': metadata,
            'logs': logs
        }
        
        is_valid = validate_parsed_data(data)
        if not is_valid:
            raise ValueError("데이터 검증 실패")
        print(f"  ✓ 검증 통과")
        
        # 6. JSON 파일 저장
        print(f"\n[6/6] JSON 파일 저장 중...")
        output_path = Path(config['output_dir']) / config['output_file']
        save_to_json(data, output_path)
        
        print("\n" + "=" * 70)
        print(f"✅ R-06 추출 완료!")
        print(f"✅ 출력 파일: {output_path}")
        print("=" * 70)
        print("\n💡 분석 포인트:")
        print("  - 안전성 점수: 파출소(30%) + CCTV(40%) + 검거율(30%)")
        print("  - 편의성 점수: 15개 카테고리 평균")
        print("  - 종합 점수: (안전성 + 편의성) / 2\n")
        
    except FileNotFoundError as e:
        print("\n" + "=" * 70)
        print(f"❌ 파일을 찾을 수 없습니다: {e}")
        print("=" * 70)
        print("\n경로를 확인하세요:")
        print(f"  - 로그 파일: {config['log_file']}")
        print("\n스크립트 상단의 경로 설정을 수정하세요:")
        print(f"  LOG_BASE_PATH = r'...'")
        print(f"  RESULT_BASE_PATH = r'...'\n")
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
