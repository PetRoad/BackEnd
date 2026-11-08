# 마이페이지 API 연동

아래 API는 `Authorization: Bearer {accessToken}`이 필요합니다. 사용자 ID는 JWT에서만 가져오며 JSON 필드명은 camelCase입니다. 인증 흐름은 [auth.md](auth.md)를 참고하세요.

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | `/api/me` | 지역, 내 반려견, 통계 |
| GET | `/api/me/stats` | 산책 수·총거리·사진 수·작성 코스 수·좋아요한 코스 수 |
| PUT | `/api/me/dog` | 내 반려견 프로필 등록·수정 |
| GET | `/api/me/walks` | 내 산책일지, 최신순, 사진 포함 |
| GET | `/api/me/walks/{id}` | 내 산책일지 상세; 다른 사용자의 기록은 404 |
| DELETE | `/api/me/walks/{id}` | 본인 산책일지 삭제; 성공 204, 다른 사용자·없는 기록은 404 |
| GET | `/api/me/courses` | 내가 작성·공유한 코스, 최신순 |
| GET | `/api/me/likes` | 좋아요한 코스, 좋아요 ID 최신순 |
| GET | `/api/me/courses/{id}` | 공개 코스 상세 |

## 내 반려견과 코스 작성자의 반려견

`GET /api/me`의 `dog.breed`, `PUT /api/me/dog`의 `breed`는 **내 반려견**의 견종입니다. 코스 `CourseSummary`의 `creatorDogBreed`·`creatorDogAge`는 **코스 작성자의 반려견** 정보입니다. 기존 코스 응답의 `breed`·`dogAge`는 제거했으므로 프론트도 함께 변경해야 합니다. 나이는 서울 날짜 기준 만 나이이며 반려견이 없으면 null입니다.

`PUT /api/me/dog` 하나로 등록과 수정을 처리합니다. 필수 입력은 `name`, `breed`, `size` (`SMALL`, `MEDIUM`, `LARGE`), `birthDate` (`YYYY-MM-DD`)입니다. `profileImage`는 선택 HTTP(S) URL 또는 null입니다. 미래 생일은 서울 기준으로 판단해 400을 반환하며 DB를 변경하지 않습니다. 레거시 `/api/dog`도 미래 생일을 400으로 거절하며 기존 선택 필드 정책은 유지합니다.

## 카운트의 의미

| 실제 API 필드 | 의미 |
|---|---|
| `walkCount` | 내가 저장한 산책일지 수 |
| `courseCount` | 내가 작성·공유한 코스 수 |
| `likedCourseCount` | 내가 좋아요한 코스 수 |
| `photoCount` | 내 산책일지에 연결된 사진 URL 수 |
| `totalDistanceMeters` | 내 산책일지 거리 합계(m) |

현재 백엔드에는 `myCourseCount`라는 응답 필드가 없습니다. 화면에서 이 이름을 사용한다면 “내가 만든 코스 수”에 해당하는 `courseCount`와 연결합니다. 산책일지 수는 `walkCount`입니다. 산책일지 삭제는 산책 수·총거리·사진 수에만 영향을 주며 작성한 코스와 좋아요 수는 유지됩니다.

## 산책일지와 코스 응답 구분

`/api/me/walks`·`/api/me/walks/{id}`는 `WalkDetail`을 반환합니다.

```json
{
  "id": "10",
  "title": "저녁 산책",
  "date": "2026-11-05",
  "distanceMeters": 2400,
  "durationSec": 1800,
  "photos": ["https://example.com/walk.jpg"],
  "courseId": null
}
```

`/api/me/courses`·`/api/me/likes`·`/api/me/courses/{id}`는 별도의 `CourseDetail`을 반환합니다. `name`, `cover`, `likes`, `distanceMeters`, `difficulty`, `tags`, `points`, `mine`, `liked`를 가지며 산책일지의 `date`, `durationSec`, `photos`와 구분됩니다.

`mine`은 현재 사용자가 작성자인지, `liked`는 현재 사용자가 좋아요했는지 나타내는 boolean입니다. `likes`는 좋아요 개수이며 추천 코스의 같은 개념 필드는 현재 `likeCount`입니다.

목록이 비어 있으면 `[]`, 없는 상세 또는 다른 사용자의 산책일지는 404입니다. `courseId: null`은 코스 없이 자유 산책한 기록입니다. `durationSec: null`은 소요 시간이 입력되지 않았다는 의미이며 0초와 구분합니다.

## 산책 저장 요청과 응답

`POST /api/walk-logs` 요청:

```json
{
  "courseId": null,
  "distance": 2.4,
  "title": "저녁 산책",
  "durationSec": 1800,
  "photos": ["https://example.com/walk.jpg"]
}
```

`durationSec`는 **실제 산책 시간(초)**입니다. 1800은 30분입니다. 현재는 선택 입력이며 입력한다면 0 이상이어야 합니다. 신규 저장 시 필수화하려면 요청에 `@NotNull`을 추가하고 모든 클라이언트가 측정 시간을 보내도록 먼저 맞춰야 합니다. 과거 기록의 시간을 임의로 0으로 채우지는 않습니다.

등록 및 `GET /api/walk-logs` 목록 응답은 `WalkLogResponse`입니다. 기존 `id`, `courseId`, `distance`, `walkedAt`에 `date`, `title`, `durationSec`, `photos`를 추가했습니다. 기존 필드의 단위와 형식은 유지합니다.

- `walkedAt`: UTC 기준 저장 시각
- `date`: 같은 시각을 서울 날짜로 변환한 `YYYY-MM-DD`
- `distance`: km; `/api/me/walks`의 `distanceMeters`는 m
- `durationSec`: 초; 미입력·기존 기록은 null

프론트는 생성 응답만으로 날짜와 소요 시간을 표시할 수 있습니다. 날짜는 현재 저장 시각에서 계산하며 사용자가 과거 산책 날짜를 지정하는 기능은 없습니다.
이전 데이터에 저장 시각 자체가 없으면 `date`는 null입니다. 신규 저장은 서버가 저장 시각을 기록합니다.

삭제는 API만 구현했습니다. `DELETE /api/me/walks/{id}`는 기록과 사진 URL 연결을 삭제하고 공유 코스·좋아요·업로드된 이미지 파일은 유지합니다. 삭제 버튼과 화면 이동은 별도 협의 대상입니다.

DB 변경은 Flyway V1~V4가 적용하며 `ddl-auto: validate`를 유지합니다. 수동 SQL을 중복 적용하지 않습니다. 프론트는 저장·삭제 후 관련 목록과 통계 캐시를 갱신해야 합니다.
