# tc 실행 환경 — 설치 폴더에 env.ps1 로 복사해 값을 채운다. env.ps1 은 설치한 PC 에만 두고 저장소·꾸러미에 넣지 않는다.
# tc-ctl.ps1 이 tc 를 띄울 때만 이 값을 환경 변수로 넘기고, 띄운 뒤 지운다. 모든 설정·기본값은 application.yml(docs/operations.md §2.1).
#
# JWT 서명 키는 한 번 만들어 고정한다(바꾸면 모든 사용자가 다시 로그인한다):
#   $b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
# 값을 파일에 적지 않으려면 사용자 환경 변수에서 읽어도 된다(예: TC_DB_PASSWORD = $env:LOCAL_DB_PASSWORD).
$TcEnv = @{
	TC_DB_URL       = 'jdbc:mariadb://localhost:3306/tc'
	TC_DB_USERNAME  = 'root'
	TC_DB_PASSWORD  = '<DB 비밀번호>'
	TC_JWT_SECRET   = '<Base64 48바이트>'
	TC_SERVER_PORT  = '8087'
	TC_REDIS_HOST   = 'localhost'
	TC_REDIS_PORT   = '6379'
	# 결과(callback·WebHook)를 보내도 되는 주소 앞부분(쉼표) — 운영 권장
	# TC_DELIVERY_ALLOWED_URL_PREFIXES = 'http://mes.example.local/'
	# DB_TABLE 전달을 쓰면(Base64 32바이트, 잃으면 저장된 외부 DB 비밀번호를 다시 넣어야 한다)
	# TC_SECRET_KEY = '<Base64 32바이트>'
}

# JVM 옵션(선택)
$TcJavaOpts = @('-Xms256m', '-Xmx1g')

# 앱 인자(선택) — 예: 비밀값을 환경 변수 대신 설치한 PC 의 설정 파일로 주려면
#   $TcAppArgs = @('--spring.config.import=optional:file:C:/wow-tc/application-prod.yml')
$TcAppArgs = @()

# Windows 서비스로 띄우는 경우 그 서비스 이름 — 비우면 tc-ctl.ps1 이 프로세스로 띄운다
$TcServiceName = ''
