#!/usr/bin/env python3
"""
Oracle SQL_ID Calculator with Auto Bind Conversion

리터럴 SQL을 입력하면 자동으로 바인드 변수 형태로 변환 후 SQL_ID를 계산한다.
"""

import hashlib
import re
from typing import Tuple, Dict, Any

BASE32_ALPHABET = "0123456789abcdfghjkmnpqrstuvwxyz"


def _endian_swap_4bytes(hex_str: str) -> str:
    return ''.join([hex_str[i:i+2] for i in range(6, -1, -2)])


def calculate_sql_id(sql_text: str) -> str:
    """SQL 텍스트로부터 Oracle SQL_ID를 계산한다."""
    input_bytes = sql_text.encode('utf-8') + b'\x00'
    md5_hex = hashlib.md5(input_bytes).hexdigest()
    
    h1 = md5_hex[16:24]
    h2 = md5_hex[24:32]
    
    hn1 = _endian_swap_4bytes(h1)
    hn2 = _endian_swap_4bytes(h2)
    
    n1 = int(hn1, 16)
    n2 = int(hn2, 16)
    hash_value_64 = n1 * 4294967296 + n2
    
    sql_id = ""
    temp = hash_value_64
    for _ in range(13):
        sql_id = BASE32_ALPHABET[temp % 32] + sql_id
        temp = temp // 32
    
    return sql_id


def calculate_hash_value(sql_text: str) -> int:
    """V$SQL.HASH_VALUE를 계산한다."""
    input_bytes = sql_text.encode('utf-8') + b'\x00'
    md5_hex = hashlib.md5(input_bytes).hexdigest()
    
    h1 = md5_hex[16:24]
    h2 = md5_hex[24:32]
    
    hn1 = _endian_swap_4bytes(h1)
    hn2 = _endian_swap_4bytes(h2)
    
    n1 = int(hn1, 16)
    n2 = int(hn2, 16)
    hash_value_64 = n1 * 4294967296 + n2
    
    return hash_value_64 & 0xFFFFFFFF


def convert_to_bind_sql(sql_text: str) -> Tuple[str, int]:
    """
    리터럴 SQL을 Oracle V$SQL에서 보이는 바인드 변수 형태로 변환한다.
    
    Oracle 형태: in (:1 ,:2 ,:3 ,:4 ... ,:N )
    - 각 바인드 변수 뒤에 공백
    - 쉼표 후 바로 다음 바인드 변수
    - 마지막 바인드 변수 뒤에 공백 후 닫는 괄호
    
    Returns:
        (변환된 SQL, 바인드 변수 개수)
    """
    # IN절 찾기
    in_pattern = r'(in\s*\()([^)]+)(\))'
    
    def convert_in_clause(match):
        prefix = match.group(1)      # 'in ('
        content = match.group(2)     # 리터럴들
        suffix = match.group(3)      # ')'
        
        # 문자열 리터럴 추출
        literals = re.findall(r"'(?:[^']|'')*'", content)
        
        if not literals:
            return match.group(0)  # 리터럴이 없으면 원본 반환
        
        # Oracle 형태로 바인드 변수 생성
        bind_count = len(literals)
        # :1 ,:2 ,:3 ... ,:N  형태
        binds = ' ,'.join([f':{i}' for i in range(1, bind_count + 1)])
        
        return f'{prefix}{binds} {suffix}'
    
    result = re.sub(in_pattern, convert_in_clause, sql_text, flags=re.IGNORECASE)
    
    # 바인드 개수 계산
    bind_count = len(re.findall(r':\d+', result))
    
    return result, bind_count


def analyze_sql(sql_text: str, auto_convert: bool = True) -> Dict[str, Any]:
    """
    SQL을 분석하고 SQL_ID를 계산한다.
    
    Args:
        sql_text: SQL 문장
        auto_convert: True면 리터럴을 바인드 변수로 자동 변환
    """
    original_sql = sql_text
    bind_count = 0
    converted = False
    
    if auto_convert:
        # 리터럴이 있는지 확인 (IN절에 문자열/숫자가 있는지)
        if re.search(r"in\s*\([^)]*'", sql_text, re.IGNORECASE):
            converted_sql, bind_count = convert_to_bind_sql(sql_text)
            if bind_count > 0:
                sql_text = converted_sql
                converted = True
    
    input_bytes = sql_text.encode('utf-8') + b'\x00'
    md5_hex = hashlib.md5(input_bytes).hexdigest()
    
    h1 = md5_hex[16:24]
    h2 = md5_hex[24:32]
    
    hn1 = _endian_swap_4bytes(h1)
    hn2 = _endian_swap_4bytes(h2)
    
    n1 = int(hn1, 16)
    n2 = int(hn2, 16)
    hash_value_64 = n1 * 4294967296 + n2
    
    sql_id = ""
    temp = hash_value_64
    for _ in range(13):
        sql_id = BASE32_ALPHABET[temp % 32] + sql_id
        temp = temp // 32
    
    return {
        'sql_id': sql_id,
        'hash_value': hash_value_64 & 0xFFFFFFFF,
        'bind_count': bind_count,
        'converted': converted,
        'converted_sql': sql_text if converted else None,
        'original_length': len(original_sql),
        'converted_length': len(sql_text) if converted else None,
    }


def main():
    import sys
    
    # 도움말
    if len(sys.argv) > 1 and sys.argv[1] in ['-h', '--help']:
        print("""
Oracle SQL_ID Calculator

사용법:
  python oracle_sql_id_calculator.py "SQL문장"
  python oracle_sql_id_calculator.py -r "SQL문장"   # 원본 그대로 (변환 없이)
  python oracle_sql_id_calculator.py -f 파일경로    # 파일에서 SQL 읽기

옵션:
  -r, --raw     리터럴을 바인드로 변환하지 않고 원본 그대로 계산
  -f, --file    파일에서 SQL 읽기
  -h, --help    도움말

예시:
  python oracle_sql_id_calculator.py "select * from dual"
  python oracle_sql_id_calculator.py "select * from t where id in ('a','b','c')"
  python oracle_sql_id_calculator.py -f query.sql
""")
        return
    
    # 옵션 파싱
    raw_mode = False
    file_mode = False
    sql_text = None
    
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] in ['-r', '--raw']:
            raw_mode = True
            i += 1
        elif args[i] in ['-f', '--file']:
            file_mode = True
            i += 1
            if i < len(args):
                with open(args[i], 'r', encoding='utf-8') as f:
                    sql_text = f.read().strip()
                # 끝의 세미콜론 제거 (Oracle V$SQL에는 세미콜론이 없음)
                sql_text = sql_text.rstrip(';').strip()
                i += 1
        else:
            if sql_text is None:
                sql_text = args[i]
            else:
                sql_text += ' ' + args[i]
            i += 1
    
    if sql_text is None:
        print("SQL을 입력하세요. (도움말: -h)")
        return
    
    # 끝의 세미콜론 제거 (Oracle V$SQL에는 세미콜론이 없음)
    sql_text = sql_text.rstrip(';').strip()
    
    # 분석 실행
    result = analyze_sql(sql_text, auto_convert=not raw_mode)
    
    print(f"SQL_ID:     {result['sql_id']}")
    print(f"HASH_VALUE: {result['hash_value']}")
    
    if result['converted']:
        print(f"BIND_COUNT: {result['bind_count']}")
        print(f"변환됨:     리터럴 → 바인드 변수")
        print(f"원본 길이:  {result['original_length']} chars")
        print(f"변환 길이:  {result['converted_length']} chars")
        
        # 변환된 SQL 미리보기 (처음 200자)
        preview = result['converted_sql']
        if len(preview) > 200:
            preview = preview[:200] + '...'
        print(f"\n[변환된 SQL 미리보기]")
        print(preview)


if __name__ == "__main__":
    main()
