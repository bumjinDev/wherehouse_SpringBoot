# Redis Sorted Set 구조 이해 및 활용 가이드

## 1. Sorted Set이란?

Redis의 Sorted Set은 중복 없는 고유한 값(Member)들을 점수(Score)와 함께 저장하여 자동으로 정렬된 상태를 유지하는 자료구조입니다.

### 주요 특징

- **Member(멤버)**: 저장되는 값(데이터)입니다. 멤버는 유일해야 합니다.
- **Score(점수)**: 각 멤버에 연결된 부동소수점 숫자 값입니다. Sorted Set은 이 점수를 기준으로 멤버를 정렬합니다.
- **정렬(Sorted)**: 멤버는 항상 점수가 낮은 것부터 높은 순서로 정렬되어 있습니다.
- **고유성(Uniqueness)**: 일반적인 Set처럼 멤버 값은 중복될 수 없습니다. 만약 동일한 멤버를 다시 추가하면, 기존 멤버의 점수만 새로운 점수로 업데이트됩니다.

## 2. 부동산 추천 시스템 적용 사례

### 인덱스 구조 설계

배치 처리 과정에서 매물 원본 데이터(Hash) 외에 검색 최적화를 위한 2개의 Sorted Set 인덱스를 생성한다.

#### 가격 기준 인덱스
- **Key 패턴**: `idx:price:{지역구명}:{임대유형}`
- **구체적 예시**: `idx:price:강남구:전세`
- **Score**: `deposit` (보증금 또는 전세금, 만원 단위)
- **Member**: `propertyId` (매물 고유 식별자)
- **목적**: 특정 지역구 및 임대유형의 매물을 가격 순서로 정렬하여 범위 검색 최적화

#### 면적 기준 인덱스
- **Key 패턴**: `idx:area:{지역구명}:{임대유형}`
- **구체적 예시**: `idx:area:강남구:전세`
- **Score**: `areaInPyeong` (전용면적, 평 단위)
- **Member**: `propertyId` (매물 고유 식별자)
- **목적**: 특정 지역구 및 임대유형의 매물을 면적 순서로 정렬하여 범위 검색 최적화

### 데이터 적재 과정

```java
// 매물 상세 정보 저장
redisTemplate.opsForHash().putAll("property:" + propertyId, propertyData);

// 가격 인덱스 적재
redisTemplate.opsForZSet().add("idx:price:강남구:전세", propertyId, deposit);

// 면적 인덱스 적재
redisTemplate.opsForZSet().add("idx:area:강남구:전세", propertyId, areaInPyeong);
```

## 3. 검색 최적화 메커니즘

### 범위 검색 프로세스

1. **가격 조건 필터링**: `rangeByScore` 연산으로 가격 인덱스에서 예산 범위 내 매물 ID 목록 추출
2. **면적 조건 필터링**: `rangeByScore` 연산으로 면적 인덱스에서 평수 범위 내 매물 ID 목록 추출  
3. **교집합 연산**: 두 조건을 모두 만족하는 매물 ID만 추출(`retainAll`)
4. **상세 정보 조회**: 최종 매물 ID 목록으로 Hash에서 완전한 매물 정보 조회

### 성능상 이점

사전 정렬된 인덱스 구조로 인해 전체 데이터 스캔 없이 O(log N) 시간 복잡도로 범위 검색이 가능하다.

## 4. 내부 저장 구조 분석

### 논리적 저장 형태

```
Key: "idx:price:강남구:전세"
+-------------------+---------------------+
| Score (deposit)   | Member (propertyId) |
|-------------------|---------------------|
| 10000             | "prop-abc"          |
| 12000             | "prop-xyz"          |
| 12000             | "prop-def"          |
| 15000             | "prop-123"          |
+-------------------+---------------------+
```

### 핵심 저장 원칙

- **Score**: 정렬 및 검색 기준값. 매물의 속성값(가격, 면적)을 저장
- **Member**: 실제 검색 결과로 반환될 식별자. 매물 ID를 저장
- **Key**: Sorted Set 인스턴스의 네임스페이스. 지역구와 임대유형으로 구분

## 5. 중복값 처리 규칙

### Member 고유성 보장

동일한 Sorted Set 내에서 Member는 중복될 수 없다. 동일한 Member 재삽입 시 기존 Score 값이 갱신된다.

### Score 중복 허용 및 정렬 규칙

Score 값이 동일한 경우 Member를 사전식 순서(lexicographical order)로 정렬한다.

**예시**:
```
Score 12000인 경우:
| 12000  | "prop-abc-uuid" |  ← 'a'가 먼저 정렬됨
| 12000  | "prop-xyz-uuid" |  ← 'x'가 나중에 정렬됨
```

이러한 규칙을 통해 Score 기준 정렬과 Member 기준 추가 정렬이 일관성 있게 유지된다.

---

## 6. 심화 학습: 내부 구현 메커니즘 (Advanced)

### 6.1 Dual Data Structure 아키텍처

Redis Sorted Set은 단일 자료구조가 아닌 두 개의 서로 다른 자료구조를 동시에 유지한다:

```c
typedef struct zset {
    dict *dict;         // Hash Table: Member → Score 매핑 (O(1) 조회)
    zskiplist *zsl;     // Skip List: Score 기준 정렬 (O(log N) 범위검색)
} zset;
```

**설계 목적**: 서로 다른 접근 패턴에 대한 성능 최적화
- ZSCORE 연산: Hash Table 활용으로 O(1) 성능
- ZRANGEBYSCORE 연산: Skip List 활용으로 O(log N + M) 성능
- ZRANK 연산: Skip List의 span 메커니즘으로 O(log N) 성능

### 6.2 Skip List의 확률적 균형 메커니즘

**노드 구조**:
```c
typedef struct zskiplistNode {
    robj *obj;              // Member 값
    double score;           // Score 값
    struct zskiplistNode *backward;  // 역방향 탐색용 포인터
    struct zskiplistLevel {
        struct zskiplistNode *forward;  // 다음 노드 포인터
        unsigned int span;              // 건너뛰는 노드 수
    } level[];  // 가변 길이 레벨 배열
} zskiplistNode;
```

**레벨 결정 알고리즘**:
```c
int zslRandomLevel(void) {
    static const int threshold = ZSKIPLIST_P * RAND_MAX;  // 보통 0.25
    int level = 1;
    while (random() < threshold)
        level += 1;
    return (level < ZSKIPLIST_MAXLEVEL) ? level : ZSKIPLIST_MAXLEVEL;
}
```

이 확률적 접근법으로 평균 O(log N) 성능을 보장하면서도 AVL Tree나 Red-Black Tree 같은 복잡한 회전 연산을 피할 수 있다.

### 6.3 Span 기반 순위 계산

Skip List의 각 포인터에는 `span` 필드가 있어 해당 포인터를 따라 이동할 때 건너뛰는 노드 수를 기록한다.

**순위 계산 과정**:
1. 목표 노드까지 탐색하며 경로상의 모든 span 값을 누적
2. 누적된 span 값이 해당 노드의 순위(rank)가 됨
3. 이를 통해 ZRANK 연산을 별도의 카운팅 없이 O(log N)에 처리

### 6.4 메모리 vs 성능 트레이드오프

**메모리 오버헤드**:
- 각 Member가 Hash Table과 Skip List에 중복 저장
- Skip List 노드마다 레벨별 포인터와 span 정보 저장

**성능상 이점**:
- 다양한 연산 패턴에 대해 각각 최적화된 자료구조 활용
- 단일 자료구조로는 불가능한 종합적 성능 달성

### 6.5 대안 자료구조 대비 선택 이유

**Balanced Binary Tree 대신 Skip List 선택 근거**:
1. **구현 단순성**: 복잡한 회전 연산 불필요
2. **메모리 지역성**: 순차 접근 시 캐시 효율성
3. **확장성**: 새로운 기능(ZRANK) 추가 용이성
4. **성능 일관성**: 최악의 경우도 확률적으로 제한됨

이러한 내부 메커니즘에 대한 이해는 대용량 데이터 처리, 성능 튜닝, 그리고 Redis 기반 시스템의 장애 분석 시 핵심적인 배경 지식이 된다.