# 안전성 점수 시스템 설계 및 R 분석 개선 방안

## 1. 현재 R 분석 결과 진단

### 1.1 기존 R 분석의 문제점

#### 통계적 한계
- **Adjusted R² = 0.5097 (50.97%)**: 예측 모델로 사용하기에는 부족한 설명력
- **p-value = 0.0005745**: 통계적으로는 유의미하나 실용성 부족
- **약 50% 미설명 변동**: 현재 독립변수로는 검거율의 절반만 설명 가능

#### 독립변수 선정의 문제
```r
# 현재 회귀 모델
검거율 = 0.8104 + (0.007*시내주요기관수) + (-0.0000003*인구수) + (-0.000007*인구밀집정도)

문제점 분석:
1. 인구수, 인구밀집정도 → 음의 상관관계 (역설적 결과)
2. CCTV → 유의미하지 않아서 최종 모델에서 제거됨
3. 핵심 안전 요소들 (파출소 거리, 조명, 상권활성도 등) 누락
```

#### 종속변수(검거율) 자체의 근본적 한계

| 문제점 | 설명 | 대안 |
|--------|------|------|
| **검거율 ≠ 안전도** | 검거율이 높다고 반드시 안전한 것은 아님 | 복합 안전성 지수 도입 |
| **후행지표 특성** | 범죄 발생 후의 대응 효과만 측정 | 예방 지표 추가 필요 |
| **선행지표 부족** | 범죄 예방 효과 측정 불가 | CCTV, 조명, 순찰 빈도 등 추가 |
| **지역 편향** | 구 단위 평균으로 세부 지역 차이 무시 | 위치 기반 점수 시스템 |

### 1.2 R 분석에서 도출된 유의미한 인사이트

#### 활용 가능한 결과
- **시내 주요 기관 수**: 검거율에 양의 상관관계 (유의미)
- **구별 기초 데이터**: 각 구의 기본 치안 수준 비교 가능
- **상대적 안전도 순위**: 25개 구 간 상대적 비교 지표로 활용

#### 개선이 필요한 부분
- CCTV 효과가 나타나지 않은 이유 분석 필요
- 인구 관련 변수의 비선형 관계 고려
- 추가 독립변수 발굴 및 모델 정교화

---

## 2. 복합 안전성 점수 시스템 설계

### 2.1 새로운 안전성 점수 체계

#### 3단계 복합 지수 구조
```java
public class ComprehensiveSafetyScore {
    
    // 1차: 범죄 예방 요소 (40% 가중치)
    private double preventionScore = 
        (cctvDensity * 0.3) +           // CCTV 밀도 (500m 반경 내)
        (policeStationDistance * 0.4) + // 파출소까지 거리 (역가중치)
        (lightingIndex * 0.3);          // 가로등 밀도/조명 지수
    
    // 2차: 범죄 대응 요소 (30% 가중치)  
    private double responseScore =
        (arrestRate * 0.6) +            // 검거율 (R 분석 결과 활용)
        (responseTime * 0.4);           // 112 신고 평균 응답시간
    
    // 3차: 환경적 안전 요소 (30% 가중치)
    private double environmentScore = 
        (populationDensity * 0.2) +     // 인구밀도 (적정 범위 내)
        (commercialActivity * 0.4) +    // 상권 활성도 (유동인구)
        (publicTransportAccess * 0.4);  // 대중교통 접근성
    
    // 최종 안전성 점수 계산
    public double calculateFinalScore() {
        return (preventionScore * 0.4) + 
               (responseScore * 0.3) + 
               (environmentScore * 0.3);
    }
}
```

### 2.2 각 요소별 상세 계산 방식

#### 1차: 범죄 예방 요소 (40%)
```java
// CCTV 밀도 점수 (30%)
private double calculateCCTVDensity(Location location) {
    int cctvCount = getCCTVCountInRadius(location, 500); // 500m 반경
    double density = cctvCount / (Math.PI * 0.25); // 밀도 계산
    return Math.min(100, density * 10); // 0~100 정규화
}

// 파출소 거리 점수 (40%) - 거리 역산
private double calculatePoliceProximity(Location location) {
    double distance = getNearestPoliceStationDistance(location);
    if (distance <= 300) return 100;      // 300m 이내 만점
    if (distance >= 1500) return 0;       // 1.5km 이상 0점
    return 100 - ((distance - 300) / 12); // 선형 감소
}

// 조명 지수 (30%)
private double calculateLightingIndex(Location location) {
    int streetLights = getStreetLightCount(location, 200); // 200m 반경
    double commercialLighting = getCommercialLightingLevel(location);
    return Math.min(100, (streetLights * 5) + (commercialLighting * 2));
}
```

#### 2차: 범죄 대응 요소 (30%)
```java
// 검거율 점수 (60%) - R 분석 결과 활용
private double calculateArrestRateScore(String district) {
    // R 분석에서 도출한 구별 검거율 사용
    double districtArrestRate = rAnalysisResults.getArrestRate(district);
    return districtArrestRate * 100; // 이미 0~1 범위이므로 100 곱하기
}

// 응답시간 점수 (40%)
private double calculateResponseTimeScore(String district) {
    double avgResponseTime = get112ResponseTime(district); // 분 단위
    if (avgResponseTime <= 3) return 100;      // 3분 이내 만점
    if (avgResponseTime >= 10) return 0;       // 10분 이상 0점
    return 100 - ((avgResponseTime - 3) * 14.3); // 선형 감소
}
```

#### 3차: 환경적 안전 요소 (30%)
```java
// 인구밀도 점수 (20%) - 적정 범위 모델
private double calculatePopulationDensityScore(Location location) {
    double density = getPopulationDensity(location); // 명/km²
    // 너무 적거나 너무 많으면 안전도 감소
    if (density >= 15000 && density <= 25000) return 100; // 적정 범위
    if (density < 5000 || density > 40000) return 0;      // 극값 0점
    // 적정 범위 외 선형 감소
    return Math.max(0, 100 - Math.abs(density - 20000) / 200);
}

// 상권 활성도 점수 (40%)
private double calculateCommercialActivityScore(Location location) {
    int restaurants = getRestaurantCount(location, 500);
    int stores = getStoreCount(location, 500);
    int lateNightBusiness = getLateNightBusinessCount(location, 500);
    
    double activityScore = (restaurants * 2) + (stores * 1.5) + (lateNightBusiness * 3);
    return Math.min(100, activityScore / 2); // 정규화
}

// 대중교통 접근성 점수 (40%)
private double calculatePublicTransportScore(Location location) {
    double subwayDistance = getNearestSubwayDistance(location);
    int busStops = getBusStopCount(location, 300);
    
    double subwayScore = Math.max(0, 100 - (subwayDistance / 10)); // 1km당 10점 감소
    double busScore = Math.min(100, busStops * 25); // 버스정류장 1개당 25점
    
    return (subwayScore * 0.7) + (busScore * 0.3);
}
```

---

## 3. R 분석 개선 방안

### 3.1 추가 독립변수 발굴

#### 현재 누락된 핵심 변수들
```r
# 개선된 회귀 모델에 추가할 변수들
improved_safety_model <- lm(
    복합안전지수 ~ 
        파출소평균거리 +           # 각 구의 평균 파출소 접근성
        CCTV밀도 +               # 면적당 CCTV 수 (개/km²)
        가로등밀도 +             # 면적당 가로등 수
        상권활성도지수 +          # 유동인구, 심야영업점 수
        지하철역접근성 +          # 평균 지하철역 거리
        범죄발생률 +             # 인구 대비 범죄 발생률 (현재 검거율 대신)
        응답시간평균 +           # 112 신고 평균 응답시간
        유흥업소밀도 +           # 주변 환경 리스크 요소
        주거상업비율,            # 주거지역 vs 상업지역 비율
    data = seoul_comprehensive_data
)
```

#### 종속변수 재정의
```r
# 현재: 단순 검거율
arrest_rate = 검거수 / 범죄발생수

# 개선: 복합 안전성 지수  
comprehensive_safety_index = 
    (crime_prevention_score * 0.4) +     # 예방 점수
    (crime_response_score * 0.3) +       # 대응 점수  
    (environmental_safety_score * 0.3)   # 환경 점수

# 또는 범죄 발생률 기반 모델
crime_incidence_rate = (범죄발생수 / 인구수) * 1000  # 인구 천명당 범죄율
safety_effectiveness = 검거율 / crime_incidence_rate  # 효율성 지수
```

### 3.2 데이터 수집 방향

#### 추가 수집 필요 데이터
| 데이터 유형 | 구체적 내용 | 출처 | 활용 방안 |
|------------|------------|------|----------|
| **112 응답시간** | 구별 평균 응답시간 | 서울시 소방재난본부 | 대응 효율성 측정 |
| **가로등 현황** | 구별 가로등 설치 수 | 서울 열린데이터 광장 | 조명 안전도 계산 |
| **유흥업소 분포** | 구별 유흥업소 수 | 서울시 상권정보 | 환경 리스크 평가 |
| **지하철 접근성** | 구별 평균 지하철역 거리 | 서울교통공사 | 대중교통 안전성 |
| **유동인구 데이터** | 시간대별 유동인구 | KT 빅데이터 | 상권 활성도 측정 |
| **심야영업점** | 24시간 운영 업체 수 | 서울시 상권정보 | 심야 안전도 평가 |

#### 데이터 전처리 개선
```r
# 기존 문제점 해결
# 1. 콤마 문제 해결
clean_numeric_data <- function(data_column) {
    as.numeric(gsub(",", "", data_column))
}

# 2. 결측치 처리 개선
handle_missing_values <- function(dataset) {
    # 0으로 일괄 치환 대신 구별 특성 고려
    dataset$police_stations[is.na(dataset$police_stations)] <- 
        median(dataset$police_stations, na.rm = TRUE)
    return(dataset)
}

# 3. 이상치 탐지 및 처리
detect_outliers <- function(data_column) {
    Q1 <- quantile(data_column, 0.25, na.rm = TRUE)
    Q3 <- quantile(data_column, 0.75, na.rm = TRUE)
    IQR <- Q3 - Q1
    outliers <- which(data_column < Q1 - 1.5*IQR | data_column > Q3 + 1.5*IQR)
    return(outliers)
}
```

---

## 4. 2주 내 현실적 구현 전략

### 4.1 1단계: 기본 안전성 점수 시스템 (1주차)

#### 즉시 구현 가능한 요소들
```java
// 확보 가능한 데이터로 기본 점수 계산
public class BasicSafetyScore {
    
    public double calculateBasicScore(Location location, String district) {
        
        // 1. 파출소 거리 (Google Maps API)
        double policeProximity = calculatePoliceDistance(location);
        
        // 2. CCTV 수 (서울 열린데이터 광장 - 구별 데이터)
        double cctvScore = getCCTVScoreByDistrict(district);
        
        // 3. 구별 범죄율 (기존 R 분석 결과 활용)
        double crimeScore = getRAnalysisScore(district);
        
        // 4. 인구밀도 (기존 수집 데이터)
        double densityScore = getPopulationDensityScore(district);
        
        // 검증된 가중치 적용
        return (policeProximity * 0.4) +     // 파출소 거리 40%
               (cctvScore * 0.25) +          // CCTV 25%
               (crimeScore * 0.25) +         // 범죄율 25%
               (densityScore * 0.1);         // 인구밀도 10%
    }
}
```

#### R 분석 결과 부분 활용
```java
// 기존 R 분석의 검거율을 구별 기본 안전도로 활용
public class DistrictSafetyBase {
    
    private Map<String, Double> districtArrestRates = Map.of(
        "강남구", 0.82,
        "서초구", 0.85,
        "종로구", 0.78,
        // ... 25개 구 데이터
    );
    
    public double getDistrictBaseSafety(String district) {
        return districtArrestRates.getOrDefault(district, 0.75); // 기본값
    }
}
```

### 4.2 2단계: 정교화 및 확장 (2주차)

#### 추가 요소 반영
```java
// 고도화된 안전성 점수 시스템
public class AdvancedSafetyScore extends BasicSafetyScore {
    
    @Override
    public double calculateAdvancedScore(Location location, String district) {
        
        double basicScore = calculateBasicScore(location, district);
        
        // 추가 보정 요소들
        double subwayAccess = calculateSubwayAccessibility(location);
        double commercialActivity = calculateCommercialActivity(location);
        double timeBasedSafety = calculateTimeBasedSafety(location);
        
        // 고도화된 가중치 적용
        return (basicScore * 0.7) +              // 기본 점수 70%
               (subwayAccess * 0.15) +           // 지하철 접근성 15%
               (commercialActivity * 0.1) +      // 상권 활성도 10%  
               (timeBasedSafety * 0.05);         // 시간대별 보정 5%
    }
}
```

#### R 분석 재수행 (선택사항)
```r
# 추가 데이터 확보 시 모델 개선
if (additional_data_available) {
    improved_model <- lm(
        safety_composite_score ~ 
            police_distance + 
            cctv_density + 
            lighting_index + 
            commercial_activity +
            subway_accessibility +
            crime_rate,
        data = enhanced_seoul_data
    )
    
    # 모델 성능 검증
    if (summary(improved_model)$adj.r.squared > 0.7) {
        # R² > 0.7이면 실제 적용
        use_improved_r_model()
    } else {
        # 여전히 낮으면 기본 가중치 시스템 유지
        use_weighted_system()
    }
}
```

---

## 5. 최종 추천 구현 방향

### 5.1 하이브리드 접근법 채택

#### 복합 안전성 점수 체계
```java
// 최종 안전성 점수 = 위치 기반 + 구별 기반 + 시간 기반
public class HybridSafetyScore {
    
    public double calculateFinalScore(Location location, String district, LocalTime time) {
        
        // 1. 위치 기반 점수 (실시간 계산)
        double locationScore = calculateLocationBasedScore(location);
        
        // 2. 구별 기준 점수 (R 분석 결과 활용)
        double districtScore = getDistrictSafetyFromRAnalysis(district);
        
        // 3. 시간대별 보정
        double timeAdjustment = getTimeBasedAdjustment(time);
        
        // 최종 가중치 적용
        double finalScore = (locationScore * 0.6) +      // 위치 기반 60%
                           (districtScore * 0.3) +       // 구별 기반 30%  
                           (timeAdjustment * 0.1);       // 시간 보정 10%
                           
        return Math.max(0, Math.min(100, finalScore)); // 0~100 범위 보장
    }
}
```

### 5.2 R 분석 결과 활용 방안

#### 활용할 부분
- **구별 검거율**: 전체 안전성 점수의 30% 가중치로 활용
- **상대적 순위**: 25개 구 간 상대적 안전도 비교 기준
- **기준 보정**: 위치 기반 점수의 구별 보정 요소

#### 개선할 부분  
- **CCTV 효과**: 구별 평균 대신 반경 기반 밀도로 재계산
- **인구 관련 변수**: 선형 관계 대신 적정 범위 모델 적용
- **추가 변수**: 파출소 거리, 조명, 상권 등 핵심 요소 보강

### 5.3 성공 기준 및 검증 방법

#### 정량적 지표
- **예측 정확도**: 실제 범죄 발생률과의 상관관계 > 0.7
- **사용자 만족도**: 추천 지역 선택률 > 70%
- **시스템 신뢰도**: 동일 조건 재검색 시 일관된 결과

#### 정성적 평가
- **직관적 결과**: 일반적으로 안전하다고 알려진 지역의 높은 점수
- **차별화 효과**: 유사한 지역 간에도 세밀한 점수 차이
- **실용성**: 실제 거주지 선택에 도움이 되는 수준의 정보 제공

---

## 6. 결론

### 6.1 현재 R 분석의 가치

기존 R 분석은 완전한 예측 모델로는 부족하지만, **구별 기준 안전도 비교**와 **상대적 순위 매기기**에는 충분히 활용 가능하다. 특히 검거율이라는 객관적 지표를 통해 각 구의 치안 대응 능력을 수치화한 점은 의미가 있어 보인다.

### 6.2 통합 솔루션 방향

**단일 지표에 의존하지 않고 복합적 접근**이 필요하다:
- **위치 기반**: 파출소 거리, CCTV 밀도 등 즉석 계산
- **구별 기반**: R 분석 검거율, 구별 통계 데이터 활용  
- **환경 기반**: 상권, 교통, 조명 등 주변 환경 고려

### 6.3 2주 내 달성 목표

1. **1주차**: 기본 복합 점수 시스템 구현 (R 분석 결과 30% 활용)
2. **2주차**: 추가 요소 반영 및 정교화
3. **검증**: 실제 서울 지역 테스트를 통한 직관적 결과 확인