package com.wow.be.tc;

import com.wow.be.framework.MesGeneralException;

/**
 * Tool Control 모듈 예외. framework 의 {@link MesGeneralException} 을 상속해 errorCode 를 함께 싣는다.
 *
 * <p>errorCode 를 주지 않으면 {@link #ERR_TC} 다. 전역 예외 처리기({@code TcGlobalExceptionHandler})는
 * errorCode 가 ERR_400 / ERR_404 / ERR_409 이면 그 상태로, 그 밖에는 500 으로 응답한다(레거시와 같은 500).
 */
public class ToolControlException extends MesGeneralException {

	private static final long serialVersionUID = 1L;

	/** 기본 오류 코드 — PLC 통신·인터페이스 처리 등 서버 쪽 처리 실패. */
	public static final String ERR_TC = "ERR_TC";

	// ── dispatch 오류 코드. "ERR_{HTTP 상태}_TC_…" — 전역 예외 처리기와 디스패처 컨트롤러가 앞의 숫자로 HTTP 상태를 정한다 ──

	/** 잘못된 요청 */
	public static final String INVALID_REQUEST = "ERR_400";

	/** 인터페이스를 쓰지 않음(use=false) */
	public static final String INTERFACE_DISABLED = "ERR_409_TC_INTERFACE_DISABLED";

	/** 인터페이스 설정 오류(응답 인터페이스 없음, 커맨드 클래스 미지정 등) */
	public static final String INTERFACE_MISCONFIGURED = "ERR_409_TC_INTERFACE_CONFIG";

	/** 인터페이스에 연결된 PLC 커넥터가 등록되어 있지 않음 */
	public static final String PLC_NOT_FOUND = "ERR_404_TC_PLC";

	/** PLC 커넥터가 게시(publish)되지 않았거나, 연결된 채널이 없거나, 요청할 수 없는 운영 상태 */
	public static final String PLC_UNAVAILABLE = "ERR_503_TC_PLC_UNAVAILABLE";

	/** PLC 응답 시간 초과 */
	public static final String PLC_TIMEOUT = "ERR_504_TC_PLC_TIMEOUT";

	/** PLC 가 오류 종료 코드를 돌려줌 */
	public static final String PLC_ERROR = "ERR_502_TC_PLC_ERROR";

	// ── 인증·권한 오류 코드 (docs/auth-and-audit-design.md §6.3, §6.8) ──

	/** API 키가 없거나 틀림(형식·없는 키·해시 불일치·폐기·만료·클라이언트 중지 모두 같은 코드와 문구) */
	public static final String API_KEY_INVALID = "ERR_401_TC_API_KEY";

	/** 권한 없음(관리 API 를 관리자가 아닌 사용자가 부름 등) */
	public static final String FORBIDDEN = "ERR_403";

	/** API 클라이언트에 허용되지 않은 IP 에서 호출 */
	public static final String API_CLIENT_IP_FORBIDDEN = "ERR_403_TC_API_CLIENT_IP";

	/** API 클라이언트에 허용되지 않은 opcode */
	public static final String OPCODE_FORBIDDEN = "ERR_403_TC_OPCODE_FORBIDDEN";

	/** 같은 자원이 이미 있거나, 지금 상태에서 할 수 없는 변경 */
	public static final String CONFLICT = "ERR_409";

	/** API 클라이언트의 초당 요청 한도 초과, 또는 API 키 대입 시도 차단 */
	public static final String RATE_LIMITED = "ERR_429_TC_RATE_LIMIT";

	/** API 클라이언트의 동시 처리 한도 초과 */
	public static final String CONCURRENCY_LIMITED = "ERR_429_TC_CONCURRENCY";

	/** 없는 자원 */
	public static final String NOT_FOUND = "ERR_404";

	/** 비밀번호를 바꿔야 다른 API 를 쓸 수 있음(관리자 초기화·최초 관리자) */
	public static final String PASSWORD_CHANGE_REQUIRED = "ERR_403_TC_PASSWORD_CHANGE_REQUIRED";

	/** 로그인 실패가 많아 잠시 로그인을 받지 않음(계정 잠금 아님) */
	public static final String LOGIN_THROTTLED = "ERR_429_TC_LOGIN";

	public ToolControlException() {
		super(ERR_TC, "Tool Control 처리 중 오류가 발생했습니다.", null);
	}

	public ToolControlException(String message) {
		super(ERR_TC, message, null);
	}

	public ToolControlException(Throwable th) {
		super(ERR_TC, th != null ? th.getMessage() : null, th);
	}

	public ToolControlException(String message, Throwable th) {
		super(ERR_TC, message, th);
	}

	public ToolControlException(String errorCode, String message, Throwable th) {
		super(errorCode, message, th);
	}

	/** errorCode 를 지정하고 원인 없이 만든다. {@code ToolControlException(String message)} 와 헷갈리지 않게 이름을 달리한다. */
	public static ToolControlException of(String errorCode, String message) {
		return new ToolControlException(errorCode, message, null);
	}

	/**
	 * errorCode 의 앞 숫자로 HTTP 상태를 정한다 ({@code ERR_404_…} → 404). 숫자가 없거나 4xx·5xx 가 아니면 500.
	 */
	public static int httpStatusOf(String errorCode) {
		if (errorCode != null && errorCode.length() >= 7 && errorCode.startsWith("ERR_")) {
			try {
				int status = Integer.parseInt(errorCode.substring(4, 7));
				if (status >= 400 && status <= 599) {
					return status;
				}
			} catch (NumberFormatException e) {
				// 숫자가 아닌 코드(ERR_TC 등) — 500
			}
		}
		return 500;
	}
}
