# PetRoad Backend

반려동물 산책 코스 공유 서비스의 Spring Boot 백엔드 MVP입니다.

## 기술 스택

- Java 17, Spring Boot 3.3, Spring Web
- Spring Data JPA, PostgreSQL 16
- JWT(jjwt), BCrypt, springdoc OpenAPI

## 실행

### 개발 환경

- Java 17 이상
- Maven 3.9 이상
- Docker Desktop (PostgreSQL 실행용)

설치 여부는 아래 명령으로 확인할 수 있습니다.

```bash
java -version
mvn -version
docker --version
```

```bash
docker compose up -d db
mvn spring-boot:run
```

Windows에서는 Docker Desktop을 먼저 실행한 후, 이 디렉터리(`BackEnd`)에서 위 명령을 실행하세요.

경로에 한글이 포함된 Windows 환경에서 `spring-boot:run` 실행 시
`ClassNotFoundException: com.petroad.backend.PetRoadApplication`이 발생할 수 있습니다.
이 경우 프로젝트를 `C:\dev\PetRoad`처럼 영문 경로로 이동한 뒤 다시 실행하거나,
아래처럼 JAR로 실행하세요.

```bash
mvn clean package -DskipTests
java -jar target/petroad-backend-0.0.1-SNAPSHOT.jar
```

기본 DB 접속 정보는 `application.yml`과 `docker-compose.yml`을 따르며, 운영 환경에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 반드시 변경하세요.

Swagger: http://localhost:8080/swagger-ui.html

개발 환경 확인용 Hello World API는 인증 없이 호출할 수 있습니다.

```http
GET http://localhost:8080/api/hello
```

응답:

```text
Hello World
```

Swagger UI에서 `Hello World 확인` 항목의 `GET /api/hello`를 선택한 뒤
`Try it out` → `Execute` 순서로 호출할 수 있습니다. OpenAPI 문서는
`http://localhost:8080/v3/api-docs`에서 확인할 수 있습니다.

## 무료 공유용 배포 (Render + Neon)

팀원이 브라우저에서 접속할 수 있는 Swagger URL을 만들려면 백엔드와 PostgreSQL을 외부에 배포해야 합니다.
아래 구성은 테스트·데모용이며, 무료 서비스는 유휴 시 잠들거나 사용량 제한이 있을 수 있습니다.

1. Neon에서 PostgreSQL 프로젝트를 만들고 접속 정보를 준비합니다.
2. 이 브랜치의 `Dockerfile`과 `render.yaml`을 GitHub에 push합니다.
3. Render에서 **New → Blueprint**를 선택하고 `PetRoad/BackEnd` 저장소 및 `feat/hello-world-swagger` 브랜치를 연결합니다.
4. Blueprint가 요청하는 DB 환경변수에 Neon 접속 정보를 입력합니다. 비밀값은 Git에 커밋하지 마세요.

   - `DB_URL`: Neon의 JDBC 주소. 형식은 `jdbc:postgresql://<Neon 호스트>/<DB 이름>?sslmode=require`
   - `DB_USERNAME`: Neon에서 제공한 사용자 이름
   - `DB_PASSWORD`: Neon에서 제공한 비밀번호

   `JWT_SECRET`은 Blueprint가 자동 생성합니다. Render가 제공하는 `PORT` 값은 애플리케이션에서 자동으로 사용합니다.
5. 배포가 Live가 되면 Render가 발급한 주소에 `/swagger-ui/index.html`을 붙여 팀원과 공유합니다.
   예: `https://<Render 서비스 이름>.onrender.com/swagger-ui/index.html`

무료 Render 웹 서비스는 일정 시간 요청이 없으면 잠들 수 있어 첫 접속에 시간이 걸릴 수 있습니다.
Neon 무료 DB도 유휴 후 다시 활성화될 수 있습니다. 무료 티어는 데모 용도로 사용하고 실제 서비스 데이터는 저장하지 마세요.

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
