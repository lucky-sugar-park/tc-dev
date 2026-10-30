package com.wow.be.tc.endpoints;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wow.be.framework.security.authz.AuthenticatedOnly;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.FilterableSseEmitter;
import com.wow.be.framework.sse.PushEventPayload.PushEventKind;
import com.wow.be.framework.sse.SseEmitterManager;

import lombok.extern.slf4j.Slf4j;

/**
 * SSE 연결. 감사 메시지(audit_message) 등 서버 이벤트를 화면으로 보낸다.
 *
 * <p>framework 의 {@code ServerSideEventController}(/api/sse/**, 구독 등록 후 수신)와 달리 tc 화면은 연결하면 모든 이벤트("*")를
 * 받는 방식이라 레거시 경로와 동작을 그대로 둔다. 연결 객체·관리자·푸셔는 framework {@code sse} 것을 쓴다.
 */
@RestController
@Slf4j
@AuthenticatedOnly
public class ServerSentEventController {

	/** 연결 타임아웃(ms) — 이 시간 동안 이벤트가 없으면 연결이 끊기고 브라우저가 다시 연결한다. */
	private static final long SSE_TIMEOUT = 60*1000L;

	@GetMapping(value="/connect/{userId}", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> connect(@PathVariable("userId") String userId) {
		log.debug("");
		List<String> acceptable=new ArrayList<>();
		acceptable.add("*");
		FilterableSseEmitter femitter = new FilterableSseEmitter(userId, SSE_TIMEOUT, acceptable);
		SseEmitterManager.getManager().add(userId, femitter);
		try {
			femitter.send(SseEmitter.event()
					.name(PushEventKind.CONNECTED.getName())   // 이벤트 이름 지정
					.data("Connected with " + userId + "!"));  // 503에러 방지를 위한 더미 데이터
		} catch (IOException e) {
			throw new IllegalStateException("SSE 연결 이벤트를 보내지 못했습니다: " + userId, e);
		}
		return ResponseEntity.ok(femitter.getEmitter());
	}

	@GetMapping("/count")
	public ResponseEntity<Void> countClient() {
		EventPusher.newPusher().count();
		return ResponseEntity.ok().build();
	}
}
