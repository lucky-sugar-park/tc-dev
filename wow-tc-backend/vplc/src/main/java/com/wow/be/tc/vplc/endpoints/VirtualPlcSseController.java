package com.wow.be.tc.vplc.endpoints;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.FilterableSseEmitter;
import com.wow.be.framework.sse.SseEmitterManager;
import com.wow.be.tc.vplc.core.VirtualPlcEventKind;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 상태·메모리 변경 SSE 연결.
 *
 * <p>emitter 관리·전송은 framework sse({@link SseEmitterManager}, {@link FilterableSseEmitter}, {@link EventPusher})를 쓴다.
 * framework 의 {@code ServerSideEventController}(/api/sse/**)는 구독(event/add)을 해야 이벤트를 받는 방식이라
 * 레거시 화면과 맞지 않아 쓰지 않는다. 경로({@code /connect/{userId}}, {@code /count})와 동작(모든 이벤트 수신 "*",
 * 타임아웃 60초)은 레거시 그대로다.
 */
@RestController
@Slf4j
public class VirtualPlcSseController {

	/** 레거시와 같은 60초 */
	private static final long SSE_TIMEOUT_MILLIS=60*1000L;

	/** SSE 연결. 연결 직후 connected 이벤트를 한 번 보낸다(503 방지용 더미 데이터). */
	@GetMapping(value="/connect/{userId}", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> connect(@PathVariable("userId") String userId) {
		log.debug("");
		List<String> acceptable=new ArrayList<>();
		acceptable.add("*");
		FilterableSseEmitter femitter=new FilterableSseEmitter(userId, SSE_TIMEOUT_MILLIS, acceptable);
		SseEmitterManager.getManager().add(userId, femitter);
		try {
			femitter.send(SseEmitter.event()
					.name(VirtualPlcEventKind.CONNECTED.getName())   // 이벤트 이름 지정
					.data("Connected with " + userId + "!"));        // 503에러 방지를 위한 더미 데이터
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return ResponseEntity.ok(femitter.getEmitter());
	}

	/** 테스트용 — 연결된 모든 emitter 에 count 이벤트를 보낸다. */
	@GetMapping("/count")
	public ResponseEntity<Void> countClient() {
		EventPusher.newPusher().count();
		return ResponseEntity.ok().build();
	}
}
