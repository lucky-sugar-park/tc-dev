package com.wow.be.tc.endpoints;

import com.wow.be.tc.ToolControlException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.MesGeneralRuntimeException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리 — 처리되지 않은 예외를 한 곳에서 잡아 로그를 남기고 통일된 에러 본문
 * {@code {errorCode, message, path, timestamp}} 을 돌려준다(wow-mes app 의 GlobalExceptionHandler 와 같은 형식).
 *
 * <p>HTTP 상태는 레거시와 같게 둔다.
 * <ul>
 *   <li>Spring MVC 표준 예외(400/404/405 등)는 {@link ResponseEntityExceptionHandler} 가 본래 상태로 처리한다.</li>
 *   <li>로그인 실패 등 인증 예외는 401, 권한 예외는 403 (레거시는 보안 필터의 EntryPoint/AccessDeniedHandler 가 냈다).</li>
 *   <li>{@link MesGeneralException}·{@link MesGeneralRuntimeException}(tc 의 ToolControlException 포함)은 errorCode 앞의
 *       숫자를 상태로 쓴다(ERR_404… → 404, ERR_503… → 503). 숫자가 없으면 500.</li>
 *   <li>그 밖의 모든 예외는 500.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** 사용 중지된 계정(비밀번호는 맞음) → 401. framework 는 이메일 인증 문구를 쓰므로 tc 문구로 바꾼다 */
	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException e, HttpServletRequest request) {
		log.info("Disabled account login on {} {}", request.getMethod(), request.getRequestURI());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("ERR_401", "사용이 중지된 계정입니다.", request));
	}

	/** 인증 실패(로그인 실패·재발급 실패 등) → 401 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
		log.info("Authentication failed on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("ERR_401", e, request));
	}

	/** 권한 없음 → 403 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
		log.info("Access denied on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("ERR_403", "권한이 없습니다.", request));
	}

	/** tc 모듈 예외(ToolControlException) 등 framework 검사 예외 → errorCode 기준 상태 */
	@ExceptionHandler(MesGeneralException.class)
	public ResponseEntity<Map<String, Object>> handleMesGeneral(MesGeneralException e, HttpServletRequest request) {
		return toResponse(e.getErrorCode(), e, request);
	}

	/** framework 실행 예외 → errorCode 기준 상태 */
	@ExceptionHandler(MesGeneralRuntimeException.class)
	public ResponseEntity<Map<String, Object>> handleMesGeneralRuntime(MesGeneralRuntimeException e, HttpServletRequest request) {
		return toResponse(e.getErrorCode(), e, request);
	}

	/** 서비스가 직접 던진 {@link ResponseStatusException} → 그 상태 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e, HttpServletRequest request) {
		HttpStatusCode status = e.getStatusCode();
		String message = e.getReason() != null ? e.getReason() : status.toString();
		return ResponseEntity.status(status).body(errorBody("ERR_" + status.value(), message, request));
	}

	/** 그 밖의 모든 예외 → 500 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("ERR_500", e, request));
	}

	private ResponseEntity<Map<String, Object>> toResponse(String code, Exception e, HttpServletRequest request) {
		// errorCode 앞의 숫자가 HTTP 상태다 (ERR_404_TC_PLC → 404). 숫자가 없으면(ERR_TC 등) 500
		HttpStatus status = HttpStatus.valueOf(ToolControlException.httpStatusOf(code));
		if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
			log.error("Tool Control exception (errorCode={}) on {} {}", code, request.getMethod(), request.getRequestURI(), e);
		}
		return ResponseEntity.status(status).body(errorBody(code != null && !code.isEmpty() ? code : "ERR_500", e, request));
	}

	private static Map<String, Object> errorBody(String errorCode, Exception e, HttpServletRequest request) {
		return errorBody(errorCode, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), request);
	}

	private static Map<String, Object> errorBody(String errorCode, String message, HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("errorCode", errorCode);
		body.put("message", message);
		body.put("path", request.getRequestURI());
		body.put("timestamp", Instant.now().toString());
		return body;
	}
}
