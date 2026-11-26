# API 입력 검증

업로드 API를 제외한 요청 본문은 최대 2MB(2,097,152바이트)입니다. `Content-Length`가 없어도 실제 수신한 바이트 수를 제한합니다. 상한 초과는 `413 {"message":"요청 본문은 2MB를 넘을 수 없습니다."}`로 반환합니다. `POST /api/uploads`는 기존 multipart 정책(파일 10MB, 전체 요청 11MB)을 유지합니다.

## 필드별 제한

좌표·개수·길이·거리·시간 검증 실패는 저장 전에 `400 {"message": ...}`를 반환합니다. 추천 조회의 쿼리 파라미터 검증 실패도 같은 오류 형식입니다.

| API | 필드 | 제한 |
|---|---|---|
| `POST /api/courses` | `name` | 필수, 공백 불가, 최대 30자 |
| | `distance` | 0 초과 200km 이하 |
| | `startLat`, `startLng` | 필수, 위도 -90~90, 경도 -180~180 |
| | `points` | 필수, 2~5,000개. null 항목 불가, 각 위도·경도 필수 및 범위 검사 |
| | `tags` | 최대 3개, 각 공백 불가·최대 20자 |
| `GET /api/courses` | `lat`, `lng` | 필수, 위도 -90~90, 경도 -180~180. NaN·무한대 불가 |
| `POST /api/me/walks` | `title` | 최대 100자 |
| | `distanceMeters` | 필수, 0~200,000m |
| | `durationSec` | 선택, 0~172,800초(48시간) |
| | `route` | 최대 20,000개. null 항목 불가, 각 위도·경도 필수 및 범위 검사. 미입력·빈 배열 가능 |
| | `photos` | 최대 100개, 각 업로드 URL 최대 2,048자 |
| `PUT /api/users/me/region` | `region` | 필수, 공백 불가, 최대 100자 |
| `POST /api/auth/signup`, `POST /api/auth/login` | `email` | 필수, 이메일 형식, 최대 255자 |

상한은 초과 시 잘라 저장하지 않고 요청을 거절합니다. 앱은 거리를 코스에는 km, 산책일지에는 m로 전달해야 합니다.

## 이미지 주소

`POST /api/uploads`가 반환한 `url`을 그대로 사용합니다. 대상은 코스의 `coverImageUrl`, 반려견의 `profileImage`, 산책의 `photos`·`cover`입니다. 선택 이미지 필드는 null을 허용하지만 빈 문자열은 허용하지 않습니다. 산책 `cover`는 `photos` 안에 있어야 합니다.

- Cloudinary: 설정한 cloud의 `https://res.cloudinary.com/{cloud}/image/upload/v{숫자}/petroad/{walk_photo|course_cover|dog_profile}/{uuid}.{jpg|png|webp}`만 허용합니다.
- 로컬 저장소: 설정한 `app.public-url`(기본 `http://localhost:8080`)의 `/uploads/{walk_photo|course_cover|dog_profile}/{uuid}.{jpg|png|webp}`만 허용합니다.
- 다른 도메인·다른 cloud·`/image/fetch/`·변환 옵션·쿼리·fragment·상위 경로 이동은 거절합니다.
- 주소 형식을 검사하며 실제 파일 존재 여부나 업로드 소유자는 아직 검사하지 않습니다. 소유자 검사는 후속 #24에서 다룹니다.

기존 데이터는 이번 변경에서 수정하지 않습니다. 기존 외부 URL을 포함한 요청을 다시 저장하면 400이므로, 업로드 URL로 교체하거나 선택 사진을 null로 비워야 합니다. 기존 데이터 정리는 #26에서 처리합니다.

## API 경로 정리

다음 경로는 제거했으며, 인증된 요청에서도 404입니다.

| 제거한 API | 사용할 API |
|---|---|
| `GET/PUT /api/dog` | `GET /api/me`의 `dog`, `PUT /api/me/dog` |
| `GET /api/users/me` | `GET /api/me` |
| `GET /api/auth/health` | `GET /api/health` |
| `GET /api/hello` | 상태 확인은 `GET /api/health` |

앱에서 사용하는 `PUT /api/users/me/region`은 유지합니다. 인증 및 기존 소유권 규칙은 그대로입니다.

## 검증

`mvn verify`가 DTO 경계값·이미지 URL 정책·OpenAPI·본문 제한을 검사합니다. `PETROAD_DB_TEST_URL`을 테스트용 PostgreSQL로 지정하면 실제 DB 통합 테스트와 HTTP/1.1 chunked 본문 제한 테스트도 실행합니다. 운영 DB를 지정하지 않습니다.

프론트 전달 사항: 코스 5,000점, 산책 20,000점·200km·48시간 제한을 유지하고 코스 등록에도 200km 상한을 맞춰야 합니다. 동네 100자·태그 20자 제한과 새 413 응답 안내도 확인해야 합니다.
