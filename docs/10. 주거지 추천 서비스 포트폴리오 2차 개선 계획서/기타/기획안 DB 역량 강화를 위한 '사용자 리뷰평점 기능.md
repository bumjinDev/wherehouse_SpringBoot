네, 알겠습니다. 논의했던 내용이 사라졌다고 하시니, 포트폴리오의 DB 역량을 강화하기 위해 제안드렸던 **'사용자 리뷰/평점 기능'**의 전체 기획안을 처음부터 다시 온전하게 정리해 드리겠습니다.

기획안: DB 역량 강화를 위한 '사용자 리뷰/평점 기능'
1. 기능 목표 및 기획 의도
현재 Redis 중심의 빠른 조회(Read) 아키텍처를 보완하고, 관계형 데이터베이스(RDB)의 핵심 역량을 명확하게 보여주는 기능을 추가한다. 이 기능은 복잡한 데이터 관계, 트랜잭션 처리, 동적 집계 쿼리 등 백엔드 개발자의 필수 DB 역량을 증명하는 것을 목표로 한다.

특히, 과거에 받았던 아키텍처 피드백 중 **"JPA 연관관계 매핑 부재"**와 **"단순 CRUD 수준"**이라는 지적을 정면으로 해결하기 위한 최적의 솔루션이다.

2. 왜 이 기능은 RDB(JPA)로 구현해야 하는가?
1. 명확한 데이터 관계(Relationship) 표현이 필수적이다.
리뷰 기능은 여러 데이터(사용자, 건물, 리뷰)가 복잡한 관계를 맺는다.

한 명의 사용자(User)는 여러 개의 리뷰(Review)를 작성할 수 있다. (1:N 관계)

하나의 건물(Building)은 여러 개의 리뷰(Review)를 가질 수 있다. (1:N 관계)

하나의 리뷰(Review)는 반드시 한 명의 사용자와 하나의 건물에 속한다. (N:1 관계)

이러한 명확한 관계를 표현하는 데는 **JPA의 연관관계 매핑(@ManyToOne, @OneToMany)**이 가장 적합하다. 이를 통해 객체 그래프 탐색이 가능해지고, 코드의 가독성과 유지보수성이 크게 향상된다.

2. 데이터 정합성, 즉 트랜잭션(Transaction) 처리가 매우 중요하다.
사용자가 리뷰를 남기거나 삭제할 때, 여러 데이터 변경이 **'하나의 원자적 단위(Atomic Unit)'**처럼 동시에 성공하거나 실패해야 데이터가 꼬이지 않는다.

리뷰 삭제 시나리오:

Review 테이블에서 해당 리뷰 데이터가 삭제되어야 한다.

Building 테이블의 '총 리뷰 수'가 1 감소해야 한다.

Building 테이블의 '평균 별점'이 재계산되어 업데이트되어야 한다.

만약 1번만 성공하고 2, 3번이 실패하면 데이터 정합성이 깨진다. RDB의 **'트랜잭션'**과 Spring의 @Transactional 어노테이션은 이러한 문제를 완벽하게 해결해준다.

3. 복잡한 조회 및 동적 집계(Aggregation) 쿼리가 필요하다.
단순히 키(Key)로 값을 찾는 Redis와 달리, RDB는 여러 조건으로 데이터를 집계하고 분석하는 데 강력한 성능을 보인다.

"이 건물의 평균 평점과 리뷰 수를 함께 조회하기" (JOIN, GROUP BY, AVG(), COUNT() 필요)

"내가 쓴 모든 리뷰 목록을 최신순으로 보기" (특정 사용자로 필터링, ORDER BY 필요)

이러한 요구사항은 JPQL이나 QueryDSL을 사용하여 복잡한 쿼리를 작성하는 능력을 보여줄 절호의 기회이며, 단순 findById를 넘어선 DB 활용 능력을 증명한다.

3. 3단계 구현 계획
1단계: 데이터 모델링 및 엔티티 설계

User, Building, Review 엔티티 간의 관계를 정의한 **ERD(Entity-Relationship Diagram)**를 작성한다.

각 엔티티 클래스를 생성하고, @ManyToOne, @OneToMany 등의 어노테이션을 사용하여 JPA 연관관계를 명확하게 매핑한다.

피드백을 반영하여 엔티티에는 @Data 어노테이션 사용을 지양하고, @Getter, @Builder 등 필요한 어노테이션만 선별적으로 사용한다.

2단계: Repository 및 JPQL 쿼리 작성

ReviewRepository 인터페이스를 생성한다.

기본적인 CRUD 메서드 외에, @Query 어노테이션을 이용한 JPQL로 다음과 같은 커스텀 쿼리를 작성한다.

"SELECT AVG(r.rating) FROM Review r WHERE r.building.id = :buildingId" (특정 건물의 평균 평점 계산)

"SELECT r FROM Review r WHERE r.building.id = :buildingId ORDER BY r.createdAt DESC" (특정 건물의 리뷰 목록 최신순 조회)

3단계: Service 및 API 구현

ReviewService 클래스를 생성한다.

리뷰 작성(createReview), 삭제(deleteReview) 등의 메서드에 @Transactional 어노테이션을 적용하여 데이터 정합성을 보장하는 로직을 구현한다.

ReviewController를 생성하고, 아래와 같은 RESTful API 엔드포인트를 설계하여 구현한다.

리뷰 작성: POST /api/buildings/{buildingId}/reviews

리뷰 조회: GET /api/buildings/{buildingId}/reviews

리뷰 삭제: DELETE /api/reviews/{reviewId}

4. 기대 효과 및 포트폴리오 어필 포인트
이 '리뷰 기능'을 구현함으로써, 포트폴리오에서 다음과 같은 DB 관련 핵심 역량을 명확하게 증명할 수 있다.

RDB 데이터 모델링 및 JPA 연관관계 매핑 능력

트랜잭션을 이용한 데이터 정합성 보장 능력

JPQL을 이용한 복잡한 집계 및 조회 쿼리 작성 능력

조회(Read) 중심의 NoSQL(Redis)과 쓰기(Write) 중심의 RDB(JPA)를 역할에 맞게 모두 활용하는 균형 잡힌 아키텍처 설계 능력