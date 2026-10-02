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
export JWT_SECRET="$(openssl rand -base64 48)"
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

## 공용 서버 배포

팀원이 함께 사용할 Swagger URL을 만들려면 백엔드와 PostgreSQL이 공용 서버에서 실행되어야 합니다.
서버 배포 시 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` 환경변수를 서버 담당자에게 전달받아 설정하세요.
비밀값은 저장소에 커밋하지 마세요. 서버 배포가 완료되면 서버 담당자가 제공한 주소에
`/swagger-ui/index.html`을 붙여 접속합니다. 예: `https://<서버 주소>/swagger-ui/index.html`.

저장소의 `Dockerfile`은 Docker를 이용해 백엔드를 빌드하고 실행할 때 사용할 수 있습니다.
실제 배포 방식과 DB 접속 정보는 팀의 오라클 서버 구성에 맞춰야 합니다.

## 로그인 보안 설정

`JWT_SECRET`은 필수입니다. 키가 누락되거나 비어 있거나 UTF-8 기준 32바이트 미만이거나
이전에 사용하던 기본값이면 서버가 시작되지 않습니다. `openssl rand -base64 48`로 무작위 키를 만들 수 있습니다.
키는 서버별로 별도 관리하고 저장소·컨테이너 이미지·로그에 포함하지 마세요.
운영에서는 접근 권한을 제한한 서버 환경 설정에 키를 보관하고 실행 시 전달합니다.
위 개발용 명령은 실행할 때마다 새 키를 생성하며, 키를 변경하면 기존 토큰은 무효화되어 다시 로그인해야 합니다.

회원가입의 기존 최소 8글자 조건을 유지합니다. BCrypt의 입력 한계를 넘는 비밀번호를 잘라 저장하거나
비교하지 않도록 회원가입·로그인 모두 **UTF-8 기준 72바이트 초과 시 400**으로 거부합니다.
API 요청 검증을 거치지 않는 내부 호출에서도 초과 입력의 해시 생성을 거부하고, 비밀번호 비교는 실패로 처리합니다.
72바이트 입력은 허용하며, 한글·이모지 등은 글자 수와 바이트 수가 다릅니다.
새로운 최대 글자 수 정책은 이 변경의 범위에 포함하지 않습니다. 기존 정상 BCrypt 해시는 계속 사용할 수 있습니다.
이메일·비밀번호 불일치는 계정 존재 여부와 관계없이 같은 메시지의 401 응답으로 반환합니다.

로그인 시도 제한은 아래 환경변수로 조절합니다.

| 환경변수 | 기본값 | 의미 |
|---|---|---|
| `LOGIN_MAX_FAILURES` | `5` | 계정별 실패 횟수 한도 |
| `LOGIN_FAILURE_WINDOW` | `15m` | 최근 실패를 집계하는 시간 |
| `LOGIN_LOCK_DURATION` | `15m` | 실패 한도 도달 시 계정 제한 시간 |
| `LOGIN_IP_MAX_ATTEMPTS` | `30` | IP별 로그인 요청 한도 (성공·실패 모두 포함) |
| `LOGIN_IP_WINDOW` | `1m` | IP별 요청 집계 시간창 |
| `LOGIN_MAX_TRACKED_ENTRIES` | `10000` | 계정·IP별 추적 항목 최대 개수 (각각 적용) |
| `LOGIN_TRUSTED_PROXY_ADDRESSES` | 없음 | `CF-Connecting-IP`를 신뢰할 프록시의 연결 IP, 쉼표로 구분 |

계정별 제한은 IP를 바꿔도 유지되고, IP별 제한은 계정을 바꿔도 유지됩니다.
기본값에서는 5번째 실패 요청은 401이며, 이후 요청부터 429를 반환합니다.
429 응답은 `message`와 대기 시간(초)을 나타내는 `Retry-After` 헤더를 포함합니다.
제한 시간이 지나면 다시 시도할 수 있고, 제한 전 정상 로그인은 계정 실패 횟수를 초기화합니다.
DB 등 내부 오류는 비밀번호 실패로 집계하지 않습니다.

이 구현은 단일 백엔드 프로세스의 메모리에서 횟수를 관리합니다. 재시작하면 제한 상태가 초기화되며,
여러 인스턴스로 확장할 때는 Redis 등 공유 저장소로 옮겨야 합니다.
메모리 한도에 도달하면 기존 제한 항목을 삭제하지 않고 새 항목에 429를 반환합니다. 만료 항목은 정리됩니다.
같은 공인 IP를 공유하는 사용자에게는 IP 한도가 함께 적용되므로 운영 상황에 맞춰 조정하세요.

기본적으로 연결 IP만 사용하며 `X-Forwarded-For` 등의 헤더를 신뢰하지 않습니다.
Cloudflare Tunnel을 연결할 때는 백엔드에 직접 접근하는 경로를 차단한 뒤,
`LOGIN_TRUSTED_PROXY_ADDRESSES`에 실제 Tunnel 프록시의 연결 IP만 지정해야 사용자별 IP 제한이 동작합니다.
프록시가 요청 헤더를 안전하게 설정하는지 확인하고, 임의 클라이언트가 연결할 수 있는 IP를 신뢰 목록에 넣지 마세요.
서블릿의 연결 IP가 임의 전달 헤더로 바뀌지 않도록 `server.forward-headers-strategy`는 `none`으로 유지합니다.

비밀번호 바이트 제한과 로그인 400·401·429 응답은 `/v3/api-docs` 및 Swagger UI에도 표시됩니다.
보안 관련 테스트는 DB 없이 `mvn -B -ntp verify`로 실행할 수 있습니다.

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

## 이슈부터 PR까지

1. 이슈를 만들고 담당자를 정합니다. 기능·오류·리팩터링·저장소 작업 양식은 `.github/ISSUE_TEMPLATE/`에 있습니다.
2. `develop`에서 이슈 번호가 들어간 작업 브랜치를 만듭니다.
3. 작업 후 `develop` 대상으로 PR을 열고 다른 팀원의 리뷰를 받습니다. PR 본문에 `Closes #이슈번호`를 적습니다.
4. CI의 Java 17 빌드·테스트가 통과하면 병합합니다. 시연·배포할 변경을 모아 `develop`에서 `main`으로 PR을 엽니다.

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c 'feat/walk-log#12'  # 이슈 #12 예시
# 작업 후
git push -u origin 'feat/walk-log#12'
```

브랜치 앞부분은 작업에 따라 `feat/`, `fix/`, `refactor/`, `chore/`를 사용합니다. 커밋은 `feat:`, `fix:`, `refactor:`, `chore:`, `docs:` 접두사를 사용합니다.
