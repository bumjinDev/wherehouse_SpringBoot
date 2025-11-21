@echo off
chcp 65001 > nul
echo.
echo ======================================================================
echo 성능 로그 분석 스크립트 - 전체 실행
echo ======================================================================
echo.
echo 실행 순서: R-01 -^> R-02 -^> R-03 -^> R-04 -^> R-05 -^> R-06 -^> R-07
echo 각 단계마다 Extractor (로그 파싱) -^> Generator (Excel 생성)
echo.
echo ======================================================================
echo.

set start_time=%time%

echo [R-01] 9-Block 그리드 계산 분석 시작...
python r01_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-01 Extractor 실패!
    pause
    exit /b 1
)

python r01_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-01 Generator 실패!
    pause
    exit /b 1
)
echo [R-01] 완료!
echo.

echo [R-02] 캐시 조회 분석 시작...
python r02_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-02 Extractor 실패!
    pause
    exit /b 1
)

python r02_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-02 Generator 실패!
    pause
    exit /b 1
)
echo [R-02] 완료!
echo.

echo [R-03] DB 조회 분석 시작...
python r03_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-03 Extractor 실패!
    pause
    exit /b 1
)

python r03_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-03 Generator 실패!
    pause
    exit /b 1
)
echo [R-03] 완료!
echo.

echo [R-04] 외부 API 호출 분석 시작...
python r04_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-04 Extractor 실패!
    pause
    exit /b 1
)

python r04_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-04 Generator 실패!
    pause
    exit /b 1
)
echo [R-04] 완료!
echo.

echo [R-05] 데이터 통합 및 필터링 분석 시작...
python r05_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-05 Extractor 실패!
    pause
    exit /b 1
)

python r05_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-05 Generator 실패!
    pause
    exit /b 1
)
echo [R-05] 완료!
echo.

echo [R-06] 점수 계산 분석 시작...
python r06_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-06 Extractor 실패!
    pause
    exit /b 1
)

python r06_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-06 Generator 실패!
    pause
    exit /b 1
)
echo [R-06] 완료!
echo.

echo [R-07] 최종 응답 생성 분석 시작...
python r07_data_extractor.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-07 Extractor 실패!
    pause
    exit /b 1
)

python r07_report_generator.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [오류] R-07 Generator 실패!
    pause
    exit /b 1
)
echo [R-07] 완료!
echo.

set end_time=%time%

echo ======================================================================
echo ✅ 전체 분석 완료!
echo ======================================================================
echo.
echo 시작 시간: %start_time%
echo 종료 시간: %end_time%
echo.
echo 생성된 파일:
echo   - E:\devSpace\results\r01\r01_analysis.xlsx
echo   - E:\devSpace\results\r02\r02_analysis.xlsx
echo   - E:\devSpace\results\r03\r03_analysis.xlsx
echo   - E:\devSpace\results\r04\r04_analysis.xlsx
echo   - E:\devSpace\results\r05\r05_analysis.xlsx
echo   - E:\devSpace\results\r06\r06_analysis.xlsx
echo   - E:\devSpace\results\r07\r07_analysis.xlsx
echo.
echo ======================================================================
echo.
pause
