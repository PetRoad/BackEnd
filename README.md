# Walkmate Backend

반려동물 산책 코스 공유 서비스의 Spring Boot 백엔드 MVP입니다.

## 기술 스택

- Java 17, Spring Boot 3.3, Spring Web
- Spring Data JPA, PostgreSQL 16
- JWT(jjwt), BCrypt, springdoc OpenAPI

## 실행

```bash
docker compose up -d db
mvn spring-boot:run
```

기본 DB 접속 정보는 `application.yml`과 `docker-compose.yml`을 따르며, 운영 환경에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 반드시 변경하세요.

Swagger: http://localhost:8080/swagger-ui.html

## API

| 영역 | API |
|---|---|
| 인증 | `POST /api/auth/signup`, `POST /api/auth/login` |
| 반려견 | `GET/PUT /api/dog` |
| 코스 | `POST /api/courses`, `GET /api/courses?lat=&lng=` |
| 좋아요 | `POST/DELETE /api/courses/{id}/like` |
| 산책일지 | `POST/GET /api/walk-logs` |

로그인이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 사용합니다. 추천 코스는 명세대로 시작점 기준 반경 3km 안에서 좋아요 수 내림차순, 같은 경우 거리 오름차순으로 정렬합니다. 거리는 PostgreSQL의 삼각함수 기반 Haversine 계산으로 조회합니다.

이미지 업로드는 MVP에서 S3 또는 Cloudinary의 업로드 URL을 요청 본문으로 받는 방식으로 두었습니다. 실제 파일 업로드가 필요해지는 시점에 presigned URL API를 추가하면 됩니다.

## 브랜치와 커밋

`main`은 배포 가능한 상태로 유지하고, 작업은 `develop` 또는 `feat/<기능명>` 브랜치에서 진행합니다. 커밋은 `feat:`, `fix:`, `docs:`, `refactor:` 접두사를 사용합니다.
