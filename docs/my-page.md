# 마이페이지 API 연동

로그인·배포 설정은 기존 구현을 사용합니다. 모든 아래 API는 `Authorization: Bearer {accessToken}`이 필요하며, 사용자 ID는 JWT에서만 가져옵니다. 새 마이페이지 API의 JSON은 camelCase입니다. 기존 API의 snake_case 설정은 유지합니다.

| 메서드 | 경로 | 기능 |
|---|---|---|
| GET | `/api/me` | 지역, 반려견, 마이페이지 통계 |
| GET | `/api/me/stats` | 산책 수·총거리·사진 수·작성 코스 수·좋아요한 코스 수 |
| PUT | `/api/me/dog` | 내 반려견 프로필 등록·수정 |
| GET | `/api/me/walks` | 내 산책일지, 최신순, 사진 포함 |
| GET | `/api/me/walks/{id}` | 내 산책 상세; 다른 사용자의 기록은 404 |
| GET | `/api/me/courses` | 내가 작성한 코스, 최신순 |
| GET | `/api/me/likes` | 좋아요한 코스, 좋아요 ID 최신순 |
| GET | `/api/me/courses/{id}` | 공개 코스 상세 |

`GET /api/me` 예시:

```json
{
  "region": "서울시 성북구",
  "dog": {
    "name": "콩이",
    "breed": "말티즈",
    "size": "SMALL",
    "birthDate": "2024-01-01",
    "profileImage": null
  },
  "stats": {
    "walkCount": 3,
    "totalDistanceMeters": 6800,
    "photoCount": 6,
    "courseCount": 0,
    "likedCourseCount": 0
  }
}
```

위 값은 형식 예시입니다. 데이터가 없으면 카운트는 0, 반려견 미등록이면 `dog: null`입니다.

`PUT /api/me/dog`는 `name`, `breed`, `size` (`SMALL`, `MEDIUM`, `LARGE`), `birthDate` (미래 날짜 불가), 선택 `profileImage` (HTTP(S) URL 또는 null)를 받습니다.

거리 응답은 미터이며 프론트에서 km로 변환합니다. 기존 `/api/walk-logs`와 `/api/courses`의 `distance`는 코드의 기존 km 단위를 유지했습니다. 기존 거리 데이터가 다른 단위라면 담당자와 확인 후 변환해야 합니다.

산책일지 응답의 `date`는 UTC 저장 시각을 KST로 변환한 날짜이며, 앨범은 이 날짜의 월별로 묶습니다. 기존 `walkedAt`이 로컬 시각으로 저장된 데이터는 별도로 확인해야 합니다. 시간 데이터가 없는 기존 산책의 `durationSec`는 null입니다.

## 산책 저장 담당자와의 연결

기존 `POST /api/walk-logs` 요청에 선택 필드 `title`, `duration_sec`, `photos`를 추가했습니다. `photos`는 업로드 완료된 HTTP(S) 사진 URL 배열이며, 순서대로 저장됩니다. 기존 `course_id`, `distance` 요청도 호환됩니다. 새 사진 업로드 API나 GPS 거리 계산은 이번 마이페이지 변경에 포함하지 않습니다.

```json
{
  "course_id": null,
  "distance": 2.4,
  "title": "저녁 산책",
  "duration_sec": 1800,
  "photos": ["https://example.com/walk.jpg"]
}
```

개발 환경의 기존 `ddl-auto: update`가 `walk_log.title`, `walk_log.duration_sec`, `walk_log_photos`를 추가합니다. 운영 서버에서는 `sql/my-page-postgresql.sql`을 검토해 별도로 적용하세요. 이번 작업에서는 서버 DB를 변경하지 않았습니다.

## 프론트 로그인 담당자와의 연결

서버 주소는 기존 `EXPO_PUBLIC_API_URL`을 사용합니다. 로그인 성공 시:

```ts
useUserStore.setState({ accessToken: response.accessToken });
```

서버 로그인 응답이 snake_case라면 `response.access_token`을 사용하세요. 기존 온보딩 완료 처리는 그대로 유지합니다. 마이페이지가 표시되는 동안 토큰이 설정되면 자동으로 서버 데이터로 전환됩니다.

로그아웃 시 토큰과 개인 상태를 지우고 `queryClient.removeQueries({ queryKey: ["my"] })`로 캐시를 제거하세요. 토큰 저장·복원은 로그인 담당 코드에서 처리합니다. 토큰이 없으면 기존 로컬 미리보기이며, 서버 조회 실패 시 미리보기로 대체하지 않고 오류·재시도 화면을 표시합니다.

프로필 수정 성공 후 마이페이지 쿼리를 무효화합니다. 다른 화면에서 산책 저장, 코스 생성, 좋아요 변경 성공 후에도 `queryClient.invalidateQueries({ queryKey: ["my"] })`를 호출하면 관련 목록과 통계가 갱신됩니다. 화면에 다시 진입해도 재조회합니다.

현재 서버 코스 모델에는 산책 당시 견종·나이 스냅샷이 없어서 마이페이지 서버 코스 카드에서는 해당 정보가 표시되지 않습니다. 상세 경로 지도 및 사진 촬영·업로드는 해당 담당 기능과 추후 연결해야 합니다.

MVP 목록은 페이지 구분 없이 조회합니다. 데이터 증가 시 페이지 조회와 DB 집계를 도입하세요.
