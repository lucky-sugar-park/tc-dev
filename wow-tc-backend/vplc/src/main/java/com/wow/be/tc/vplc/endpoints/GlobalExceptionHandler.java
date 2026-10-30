package com.wow.be.tc.vplc.endpoints;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.wow.be.framework.MesGeneralRuntimeException;
import com.wow.be.tc.vplc.VirtualPlcException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * vplc 전역 예외 처리 — 처리되지 않은 예외를 한 곳에서 잡아 통일 에러 본문
 * {@code errorCode, message, path, timestamp} 로 돌려준다(wow-mes app 의 GlobalExceptionHandler 와 같은 형식).
 *
 * <p>{@link ResponseEntityExceptionHandler} 를 상속해 Spring MVC 표준 예외(400/404/405 등)는 본래 상태코드를 유지한다.
 * {@link VirtualPlcException}·{@link MesGeneralRuntimeException} 은 errorCode 로 상태를 정하고(ERR_400→400, ERR_404→404,
 * ERR_409→409, 그 밖은 500), 나머지 예외는 500 + ERROR 로그다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(VirtualPlcException.class)
	public ResponseEntity<Map<String, Object>> handleVirtualPlc(VirtualPlcException e, HttpServletRequest request) {
		return coded(e.getErrorCode(), e, request);
	}

	@ExceptionHandler(MesGeneralRuntimeException.class)
	public ResponseEntity<Map<String, Object>> handleMesGeneral(MesGeneralRuntimeException e, HttpServletRequest request) {
		return coded(e.getErrorCode(), e, request);
	}

	/** 표준 MVC 예외에 해당하지 않는 모든 예외 → 500 + 로깅. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(errorBody("ERR_500", e, request));
	}

	private ResponseEntity<Map<String, Object>> coded(String code, Exception e, HttpServletRequest request) {
		HttpStatus status = switch (code == null ? "" : code) {
			case VirtualPlcException.ERR_400 -> HttpStatus.BAD_REQUEST;
			case VirtualPlcException.ERR_404 -> HttpStatus.NOT_FOUND;
			case VirtualPlcException.ERR_409 -> HttpStatus.CONFLICT;
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
		if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
			log.error("vplc 처리 실패 (errorCode={}) on {} {}", code, request.getMethod(), request.getRequestURI(), e);
		} else {
			log.warn("vplc 요청 거절 (errorCode={}) on {} {}: {}", code, request.getMethod(), request.getRequestURI(), e.getMessage());
		}
		return ResponseEntity.status(status)
				.body(errorBody(code != null ? code : "ERR_500", e, request));
	}

	private static Map<String, Object> errorBody(String errorCode, Exception e, HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("errorCode", errorCode);
		body.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		body.put("path", request.getRequestURI());
		body.put("timestamp", Instant.now().toString());
		return body;
	}
}
