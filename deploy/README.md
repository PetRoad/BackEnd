# 오라클 개발 서버 배포

개발 서버는 `https://petroad-api.hrxlou.com`입니다. `develop` 변경은 CI 성공 후 ARM64 이미지로 빌드되어 오라클 A1에 배포됩니다. PR은 테스트만 실행합니다. 최초 설치와 명시적인 재배포는 GitHub Actions의 `Backend CI`를 수동 실행할 수 있습니다.

## 서버 구성

- 기존 오라클 A1 Flex VM, Ubuntu 24.04 ARM64, Docker Engine와 Compose.
- PostgreSQL 16의 `petroad_postgres-data` 볼륨에 데이터를 유지합니다. DB 호스트 포트는 없습니다.
- 백엔드는 UID 10001, 읽기 전용 파일시스템으로 실행하며 임시 파일만 `/tmp`에 씁니다.
- 백엔드의 18080 포트는 서버의 `127.0.0.1`에만 연결됩니다.
- Cloudflare Tunnel은 전용 Docker 네트워크에서 백엔드에 접근합니다. `172.30.7.2`만 클라이언트 IP 전달을 신뢰합니다.
- DB의 `petroad` 계정은 DB·public 스키마 소유자로 마이그레이션과 앱 쿼리를 실행합니다. PostgreSQL 서버 관리자·역할 생성 권한은 없습니다.
- Swagger는 개발 서버에서 공개합니다. `SWAGGER_ENABLED=false`로 문서와 UI를 끌 수 있습니다. 토큰을 `Authorize`에 입력해야 보호된 API를 실행할 수 있습니다.
- API 응답은 `Cache-Control: no-store`로 브라우저·프록시 캐싱을 금지합니다. Cloudflare에 이를 무시하는 캐시 규칙을 추가하지 마세요.

## 비밀값과 GitHub 설정

서버의 `/opt/petroad/secrets/server.env`는 0600이며 DB 관리자 비밀번호·앱 DB 비밀번호·JWT 키를 보관합니다. `server.env.example`은 값 없는 예시입니다. 비밀값은 서버에서 생성하고 이미지·저장소·Actions 로그에 넣지 않습니다.

Tunnel 전용 인증 파일은 `/opt/petroad/secrets/tunnel.json`에 0600으로 보관하고 컨테이너의 UID 65532만 읽도록 소유자를 설정합니다. 계정 전체 인증용 `cert.pem`은 서버에 복사하지 않습니다. `/opt/petroad/tunnel.yml`은 `tunnel.yml.example`을 참고해 실제 Tunnel UUID로 설정합니다.

GitHub repository 설정:

| 이름 | 종류 | 용도 |
|---|---|---|
| `PETROAD_DEPLOY_SSH_KEY` | Secret | 전용 `petroad-deploy` 사용자 SSH 키 |
| `PETROAD_DEPLOY_KNOWN_HOSTS` | Secret | 확인한 서버 호스트 키; 호스트 키 검증을 끄지 않습니다 |
| `PETROAD_DEPLOY_HOST` | Variable | 오라클 서버 주소 |

이미지 저장·조회에는 작업별 `GITHUB_TOKEN`을 사용합니다. 서버가 받은 토큰은 임시 Docker 인증 디렉터리에만 보관하고 작업 종료 시 삭제합니다. 서버 JWT 키와 DB 비밀번호는 GitHub에 전달하지 않습니다.

배포용 SSH 키는 `deploy COMMIT RUN_ID IMAGE_DIGEST REGISTRY_USER`만 허용합니다. 일반 셸·파일 복사·포트 포워딩은 허용하지 않습니다. 서버 관리용 키는 별도로 관리합니다.

관리자는 `deploy.sh`, `ssh-command.sh`, `backup.sh`를 root 소유·0755로 각각 `/usr/local/sbin/petroad-deploy`, `petroad-ssh-command`, `petroad-backup`에 설치합니다. 서버 스크립트가 바뀌면 관리자 키로 해당 파일도 갱신해야 합니다. 배포 사용자는 `/usr/local/sbin/petroad-deploy`에만 sudo 실행 권한이 있습니다.

## 배포 순서

1. Java 17에서 인증 테스트와 실제 PostgreSQL 마이그레이션 테스트를 실행합니다.
2. ARM64 이미지를 `ghcr.io/petroad/backend:sha-<커밋>`으로 발행합니다.
3. 배포는 변경 불가능한 이미지 digest를 사용합니다. 서버에서 ARM64 여부와 커밋 라벨도 검사합니다.
4. 서버 파일 잠금과 Actions 동시 실행 제어를 사용하며, 이전 run ID가 나중에 도착해 최신 배포를 덮어쓰지 못하도록 합니다.
5. DB 준비 상태를 확인하고 변경 전 `pg_dump -Fc` 백업을 저장합니다.
6. 새 백엔드가 Flyway 마이그레이션을 실행한 뒤 Hibernate로 엔티티·스키마 일치를 검증합니다.
7. 내부·공개 HTTPS 상태 확인 API의 정상 응답과 커밋을 검사합니다. 실패하면 이전 이미지로 복구를 시도하고 배포는 실패로 처리합니다.

`/api/health`는 DB 연결도 검사합니다. 정상 상태는 200과 `status`, `commit`만 반환하고 DB 장애는 503입니다. `/api/auth/health`는 단순 연결 확인용이며 배포 성공 판정에는 사용하지 않습니다.

단일 백엔드 컨테이너를 교체하는 방식이라 배포 중 잠깐 요청이 실패할 수 있습니다. 새 컨테이너도 `unless-stopped`이며 Docker 서비스가 서버 부팅 시 자동 실행됩니다.

## DB 마이그레이션

`src/main/resources/db/migration/V1__initial_schema.sql`은 현재 엔티티에 맞는 초기 스키마입니다. 빈 DB에서 자동 적용됩니다. 이후 변경은 `V2__설명.sql`, `V3__설명.sql` 등 새 파일로 추가하세요. 적용한 파일을 수정하면 체크섬 검증이 실패합니다. `ddl-auto`는 `validate`로 두고 마이그레이션 오류를 무시하지 않습니다.

기존 DB는 자동 baseline하지 않습니다. 먼저 백업하고 실제 스키마와 V1의 일치를 확인해야 합니다. V1과 같은 기존 스키마에 대해서만 운영자가 Flyway baseline version 1을 명시적으로 실행한 뒤 정상 설정으로 되돌립니다. 필요한 경우 일회성으로 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`, `SPRING_FLYWAY_BASELINE_VERSION=1`을 사용할 수 있지만, 이후 반드시 제거하세요. 이름만 같은 불완전한 스키마를 baseline하면 안 됩니다.

이전 버전으로 복구할 수 있도록 새 컬럼을 먼저 추가하고 앱을 전환한 뒤 나중에 제거하는 식으로 변경하세요. 이미지 롤백은 DB 스키마나 데이터를 되돌리지 않습니다. 삭제·이름 변경 등 이전 코드와 호환되지 않는 변경은 별도 복구 계획이 필요합니다.

## 백업과 복구

모든 배포 전에 `/opt/petroad/backups/`에 0600 백업을 만듭니다. 관리자 수동 백업:

```bash
sudo /usr/local/sbin/petroad-backup
```

백업은 같은 VM에 있으므로 VM·디스크 손실 복구용 외부 백업은 별도로 준비해야 합니다. 이 구성은 추가 OCI 디스크·스냅샷·로드밸런서를 만들지 않습니다. 이미지·백업이 쌓여 여유 공간이 5 GiB 미만이면 배포를 중단하므로 오래된 파일은 관리자가 검토한 후 정리합니다.

현재 실행 구성:

```bash
release=$(sudo readlink -f /opt/petroad/current)
sudo docker compose -p petroad --env-file /opt/petroad/secrets/server.env \
  --env-file "$release/image.env" -f "$release/deploy/compose.yml" ps
```

DB 복구는 현재 데이터를 덮어쓰므로 관리자가 서비스 중단과 복구 시점을 먼저 결정합니다. 현재 DB를 한 번 더 백업한 뒤 api 서비스를 중지하고, 선택한 덤프를 DB 컨테이너의 `pg_restore --clean --if-exists --exit-on-error`로 복원합니다. 복구 시점과 호환되는 이전 릴리스의 `image.env`·Compose 설정으로 api를 시작하고 `/api/health` 및 핵심 API를 확인한 뒤 `current` 링크를 전환합니다. 새 백업과 실패한 릴리스 자료는 복구 확인까지 유지합니다.

백엔드 이미지 복구만 필요한 경우 `/opt/petroad/previous`가 가리키는 릴리스의 설정으로 api를 시작합니다. 해당 이미지와 DB가 호환되는지 검증한 후 현재 릴리스 링크를 전환합니다.

## 앱 연결과 확인

앱 설정은 `EXPO_PUBLIC_API_URL=https://petroad-api.hrxlou.com`입니다. Expo 환경변수 변경 후 앱을 다시 실행하거나 새 빌드를 배포하세요.

- 상태 확인: `https://petroad-api.hrxlou.com/api/health`
- Swagger: `https://petroad-api.hrxlou.com/swagger-ui/index.html`
- OpenAPI: `https://petroad-api.hrxlou.com/v3/api-docs`

Swagger에서 회원가입·로그인 후 `access_token`을 Authorize에 입력하면 실제 API를 테스트할 수 있습니다. 요청은 실제 개발 서버 DB에 기록되므로 테스트 계정을 구분해서 사용하세요.

마이그레이션 테스트는 전용 빈 PostgreSQL DB에서 실행합니다. 테스트가 기존 데이터·스키마를 추가하므로 개인 개발 DB나 서버 DB를 지정하지 마세요. CI는 전용 PostgreSQL 서비스를 사용하고, 로컬에서 DB 환경변수를 지정하지 않으면 인증 테스트만 실행합니다.
