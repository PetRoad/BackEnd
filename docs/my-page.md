# 마이페이지 API 연동

아래 API는 `Authorization: Bearer {accessToken}`이 필요합니다. 사용자 ID는 JWT에서만 가져오며 JSON 필드명은 camelCase입니다. 인증 흐름은 [auth.md](auth.md)를 참고하세요.

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | `/api/me` | 지역, 내 반려견, 통계 |
| GET | `/api/me/stats` | 산책 수·총거리·사진 수·작성 코스 수·좋아요한 코스 수 |
| PUT | `/api/me/dog` | 내 반려견 프로필 등록·수정 |
| GET | `/api/me/walks` | 내 산책일지, 최신순, 사진 포함 |
| POST | `/api/me/walks` | 산책일지 저장, 상세와 같은 응답 |
| GET | `/api/me/walks/{id}` | 내 산책일지 상세. 다른 사용자의 기록은 404 |
| DELETE | `/api/me/walks/{id}` | 본인 산책일지 삭제. 성공 204, 다른 사용자·없는 기록은 404 |
| GET | `/api/me/courses` | 내가 작성·공유한 코스, 최신순 |
| GET | `/api/me/likes` | 좋아요한 코스, 좋아요 ID 최신순 |
| GET | `/api/courses/{id}` | 로그인 사용자가 조회할 수 있는 코스 상세 |

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

`POST /api/me/walks`, 산책 목록의 각 항목과 산책 상세는 같은 `WalkDetail` 형식을 사용합니다.

```json
{
  "id": "10",
  "title": "저녁 산책",
  "date": "2026-11-05",
  "distanceMeters": 2400,
  "durationSec": 1800,
  "photos": ["https://example.com/walk.jpg"],
  "courseId": "12",
  "route": [{"lat": 37.5891, "lng": 127.0187}],
  "completion": 85,
  "cover": "https://example.com/walk.jpg"
}
```

`/api/me/courses`, `/api/me/likes`, `/api/courses/{id}`는 `CourseDetail`을 반환합니다. `name`, `cover`, `likes`, `distanceMeters`, `difficulty`, `tags`, `points`, `mine`, `liked`, `creatorDogBreed`, `creatorDogAge`가 포함됩니다.

`mine`은 현재 사용자가 작성자인지, `liked`는 현재 사용자가 좋아요했는지 나타내는 boolean입니다. `likes`는 좋아요 개수이며 추천 코스의 같은 개념 필드는 현재 `likeCount`입니다.

목록이 비어 있으면 `[]`, 없는 상세 또는 다른 사용자의 산책일지는 404입니다. `courseId: null`은 자유 산책이거나 연결된 코스가 삭제된 기록입니다. `durationSec: null`은 소요 시간이 입력되지 않았다는 의미이며 0초와 구분합니다.

## 산책 저장 요청과 응답

`POST /api/me/walks`에 다음 형식으로 요청합니다. 기존 `/api/walk-logs`의 등록·조회 경로는 제거했습니다.

```json
{
  "title": "성북천 산책",
  "courseId": "12",
  "distanceMeters": 3200,
  "durationSec": 2880,
  "photos": ["https://example.com/a.jpg", "https://example.com/b.jpg"],
  "cover": "https://example.com/b.jpg",
  "completion": 85,
  "route": [
    {"lat": 37.5891, "lng": 127.0187},
    {"lat": 37.5893, "lng": 127.0191}
  ]
}
```

- `distanceMeters`는 필수이며 단위는 미터입니다. 산책 ID와 코스 ID는 응답에서 문자열로 반환합니다.
- `durationSec`는 실제 산책 시간(초)입니다. 선택 입력이며 0 이상이어야 합니다.
- `route`는 저장·조회 시 입력 순서를 유지합니다. 빈 배열이나 미입력을 허용합니다.
- `completion`은 코스 산책의 완주율이며 0~100 정수 또는 null입니다. 자유 산책이면 null입니다.
- `cover`는 `photos`에 포함된 URL 또는 null입니다.
- 없는 코스 ID는 404입니다. 필드 검증 실패는 400입니다.
- `date`는 서버 저장 시각을 한국 기준 `YYYY-MM-DD`로 변환한 값입니다. 저장 시각이 없는 과거 기록에서는 null입니다.

이전 산책일지는 새 GPS 경로가 `[]`, 대표 사진과 완주율은 null로 조회됩니다. 코스를 삭제해도 기존 산책 경로와 완주율은 유지되며 `courseId`는 null이 됩니다.

삭제는 API만 구현했습니다. `DELETE /api/me/walks/{id}`는 기록과 사진 URL 연결을 삭제하고 공유 코스·좋아요·업로드된 이미지 파일은 유지합니다. 삭제 버튼과 화면 이동은 별도 협의 대상입니다.

Flyway V5가 GPS 경로와 대표 사진, 완주율을 저장할 스키마를 추가합니다. 저장·삭제 후에는 관련 목록과 통계 캐시를 갱신합니다.
