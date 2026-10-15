# 주변 추천 코스 API

`GET /api/courses?lat=37.5836&lng=127.0100`

로그인 API가 발급한 토큰을 `Authorization: Bearer <token>`으로 전달한다.
토큰 누락·만료·오류는 401, 잘못된 좌표는 400이다.

## 응답 변경 (프론트 연동 시 함께 적용)

기존 배열 응답을 `{ radiusKm, courses }` 객체로 바꾼다.
카드 응답의 `distance`는 단위가 명확한 `distanceKm`로, `dogSize`는 `dogAge`로 바뀐다.
`breed`, `tags`, `distanceFromKm`도 함께 반환한다. 응답의 거리와 반경은 모두 km이다.

```json
{
  "radiusKm": 3.0,
  "courses": [
    {
      "id": 1,
      "name": "성북천 따라 걷기",
      "coverImageUrl": null,
      "distanceKm": 1.39,
      "difficulty": "EASY",
      "likeCount": 75,
      "startLat": 37.5886,
      "startLng": 127.0068,
      "distanceFromKm": 0.623389,
      "tags": ["물가", "평지"],
      "breed": "포메라니안",
      "dogAge": 3
    }
  ]
}
```

위 거리 값은 설명용 근사치이며 실제 응답은 소수점 값을 반환한다.
`distanceKm`는 산책 경로 길이, `distanceFromKm`는 요청 위치와 코스 시작점 사이의 직선 거리다.
후자는 DB에 저장하지 않는다. DB의 코스 길이는 기존과 같이 km이며 그대로 반환한다.
하버사인 계산과 반경 필터는 내부적으로 m를 사용하고 응답에서만 km로 변환한다.

반경은 1.5km 이하에 3개 이상이면 1.5km, 그렇지 않으면 3km이다.
선택 반경 이하의 코스만 좋아요 내림차순, 시작점 거리 오름차순으로 정렬한다.
두 값이 같으면 ID 오름차순으로 반환 순서를 고정한다. 결과가 없어도 반경은 3km이다.

PostgreSQL 하버사인 쿼리로 먼저 3km 이내 코스만 조회한 뒤, Java에서 최종 반경과 정렬을 결정한다.
반환할 코스의 태그는 코스 ID 목록으로 한 번에 조회하고, 반려견 프로필도 소유자 ID 목록으로 일괄 조회한다.
추천 코스가 있는 요청은 코스·반려견·태그 조회 총 3회이며, 결과가 없으면 코스 조회 1회만 실행한다.

견종·나이는 코스 등록자의 현재 반려견 프로필에서 가져온다.
`dogAge`는 서울 날짜 기준 생일이 지난 횟수(만 나이)이며 1살 미만은 0이다.
반려견이 없으면 `breed`·`dogAge`는 null, 생년월일이 없거나 미래이면 `dogAge`는 null이다.

`POST /api/courses`의 요청 DTO 이름은 `CourseCreateRequest`다.
`points`는 필수이며 좌표가 최소 2개여야 한다. 누락·null·2개 미만이면 저장 전에 400으로 거절한다.
등록 요청의 `distance`는 기존과 같이 km이고, `dogSize`도 등록 메타데이터로 유지한다.
등록·좋아요·좋아요 취소 응답도 새 `CourseSummary`를 사용하며,
이 요청들은 기준 위치를 받지 않으므로 `distanceFromKm`는 null이다.
`/api/me` 계열은 이번 변경 대상이 아니다.

## 로컬 목업 실행

공유 서버가 아닌 빈 로컬 개발 DB를 지정한다. IntelliJ 실행 구성에 다음을 설정한다.

- 활성화된 프로파일: `local-mock`
- 환경 변수: `DB_URL=jdbc:postgresql://127.0.0.1:5433/petroad_course_mock`
- `DB_USERNAME`, `DB_PASSWORD`: 로컬 DB 사용자 정보 (기본 petroad)
- `JWT_SECRET`: 로컬용 무작위 키 (32바이트 이상)

로컬 `petroad-postgres` 컨테이너가 실행 중일 때 DB 생성:

```powershell
docker exec petroad-postgres psql -U petroad -d postgres -c "CREATE DATABASE petroad_course_mock;"
```

백엔드를 실행하면 Flyway V1·V2 적용 후 목업 3개와 각 소유자 반려견 프로필을 생성한다.
기존에 같은 목업 이메일이 있으면 다시 생성하거나 사용자 변경을 덮어쓰지 않는다.
목업 프로파일은 기본으로 비활성화되어 있으며 배포 서버에서 켜지 않는다.

로컬 Swagger: http://localhost:8080/swagger-ui/index.html

`POST /api/auth/login`:

```json
{"email":"mock-river@petroad.local","password":"Petroad123!"}
```

발급 토큰으로 인증 후 추천 API를 호출한다. 목업만 있는 DB에서 기대 결과:

- `radiusKm`: 3.0
- 성북천 따라 걷기(75), 낙산공원 성곽길(42) 순서
- 북악 스카이웨이(210)는 3km 밖이라 제외
- 시작점까지 거리 약 0.623km, 약 0.426km / 견종과 나이 3살, 2살
- points는 요청에 지정한 전체 좌표를 DB에 저장하되 추천 응답에는 포함하지 않는다.

## 자동 검증

CI의 PostgreSQL 연결 변수 `PETROAD_DB_TEST_URL`이 있으면 전체 `mvn verify`에서
실제 DB와 JWT 인증을 사용하는 추천 API 테스트도 실행한다.
목업은 `course_api_test` 스키마에만 생성하며, 기존 마이그레이션 테스트의 public 스키마와 분리한다.
API 검증 결과는 `target/course-recommendations-verified.json`에 저장된다.
근처 10개·반경 밖 100개 조건에서 코스 10개만 로딩되는지 검증하고,
3개·10개 반환 모두 SQL 3회와 컬렉션 추가 조회 0회를 검증한다.
