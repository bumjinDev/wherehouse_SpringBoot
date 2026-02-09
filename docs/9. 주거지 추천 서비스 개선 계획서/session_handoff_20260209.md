# 세션 인수인계 문서: 가공 데이터 원격 DB 마이그레이션

## 프로젝트 경로
```
E:\devSpace\SpringBootProjects\wherehouse_SpringBoot-master\
```

---

## 완료된 작업

### 1단계: 통합 DDL 스크립트 생성 ✅
- **파일 위치**: `docs/9. 주거지 추천 서비스 개선 계획서/3. 안전성 수치 계산 가공 데이터 ERD 설계 및 저장 명세서/create_analysis_tables.sql`
- **내용**: 18개 ANALYSIS_* 테이블 + 18개 시퀀스 CREATE문
- **근거**: AnalyzeSafetyScore 프로젝트의 JPA Entity 소스코드 기반 (명세서가 아닌 실제 코드 기준)

---

## 남은 작업

### 2단계: 로컬 → 원격 데이터 마이그레이션
- 로컬 Oracle(`127.0.0.1:1521:xe`, SCOTT/tiger)의 ANALYSIS_* 18개 테이블 데이터를 원격 Oracle 서버로 이관
- 방법 선택 필요: Data Pump, DB Link, 또는 애플리케이션 레벨 dual datasource 복사

### 3단계: wherehouse 본 프로젝트 datasource 변경
- wherehouse 프로젝트의 `application.yml` datasource URL을 원격 Oracle 서버로 변경
- 변경 대상: 배치 스케줄러(BatchScheduler)가 안전성 점수 계산 시 참조하는 DB 연결

---

## 핵심 아키텍처 요약

### 프로젝트 3개 구조
| 프로젝트 | 역할 | 데이터 흐름 |
|---------|------|-----------|
| **AnalysisStaticData** | CSV → 로컬 Oracle 적재 | 19종 CSV 파일 → `*_STATISTICS` 원천 테이블 (DataLoader/Roader) |
| **AnalyzeSafetyScore** | 로컬 Oracle → 통계 분석 | `ANALYSIS_*` 가공 테이블 READ → 피어슨/회귀분석 → 콘솔 출력 |
| **wherehouse (본 프로젝트)** | 실시간 추천 서비스 | DB에서 3개 테이블 READ → 안전성 점수 계산 → Redis 적재 → API 응답 |

### wherehouse 배치에서 실제 사용하는 핵심 3개 테이블
1. **ANALYSIS_CRIME_STATISTICS** → `crimeRepository.findCrimeCount()`
2. **ANALYSIS_POPULATION_DENSITY** → `populationRepository.findPopulationCountByDistrict()`
3. **ANALYSIS_ENTERTAINMENT_STATISTICS** → `entertainmentRepository.findActiveEntertainmentCountByDistrict()`

나머지 15개 테이블은 AnalyzeSafetyScore의 피어슨 상관분석/다중회귀분석에서 사용하며, 향후 공식 고도화 시 활용 가능하므로 전부 생성함.

### 안전성 점수 계산 공식 (BatchScheduler 내부)
```
범죄율 = (범죄발생건수 / 인구수) × 100,000
위험도 = 1.0229 × 정규화된_유흥주점밀도 - 0.0034 × 정규화된_인구밀도
원본점수 = 100 - (위험도 × 10)
최종점수 = 0~100 재정규화
```

---

## DB 설정 현황

### 현재 (로컬)
```yaml
# AnalyzeSafetyScore/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:oracle:thin:@127.0.0.1:1521:xe
    username: SCOTT
    password: tiger
```

### 원격 서버 정보
- Redis: `43.202.178.156:6379` (이미 사용 중)
- Oracle 원격: 사용자에게 확인 필요 (아직 미확인)

---

## 관련 문서 위치
- 원천 데이터 ERD: `docs/9. 주거지 추천 서비스 개선 계획서/2. 안전성 수치 계산 원천 데이터 ERD 설계 및 데이터 저장 명세서/` (18개 md)
- 가공 데이터 ERD: `docs/9. 주거지 추천 서비스 개선 계획서/3. 안전성 수치 계산 가공 데이터 ERD 설계 및 저장 명세서/` (18개 md + create_analysis_tables.sql)
- 비즈니스 로직 설계서: `docs/9. 주거지 추천 서비스 개선 계획서/5. 실제 비즈니스 로직 설계서/부동산 추천 시스템 비즈니스 로직 설계 명세서.md`

## Entity 소스코드 위치
```
AnalyzeSafetyScore/src/main/java/com/wherehouse/AnalysisData/
├── crime/entity/AnalysisCrimeStatistics.java
├── cctv/entity/AnalysisCctvStatistics.java
├── pcbang/entity/AnalysisPcBangStatistics.java
├── streetlight/entity/AnalysisStreetlightStatistics.java
├── hospital/entity/AnalysisHospitalData.java
├── police/entity/AnalysisPoliceFacility.java
├── karaoke/entity/AnalysisKaraokeRooms.java
├── danran/entity/AnalysisDanranBars.java
├── school/entity/AnalysisSchoolStatistics.java
├── mart/entity/AnalysisMartStatistics.java
├── residentcenter/entity/AnalysisResidentCenterStatistics.java
├── lodging/entity/AnalysisLodgingStatistics.java
├── cinema/entity/AnalysisCinemaStatistics.java
├── entertainment/entity/AnalysisEntertainmentStatistics.java
├── subway/entity/AnalysisSubwayStation.java
├── university/entity/AnalysisUniversityStatistics.java
├── convenience/entity/AnalysisConvenienceStoreData.java
└── population/entity/AnalysisPopulationDensity.java
```

## wherehouse 본 프로젝트 배치 관련 코드 위치 (추정)
```
wherehouse/src/main/java/com/wherehouse/recommand/batch/BatchScheduler/BatchScheduler.java
```
- 의존성: RedisHandler, AnalysisEntertainmentRepository, AnalysisPopulationDensityRepository, AnalysisCrimeRepository
