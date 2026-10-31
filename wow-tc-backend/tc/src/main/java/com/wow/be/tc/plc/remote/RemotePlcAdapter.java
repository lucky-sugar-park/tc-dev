package com.wow.be.tc.plc.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.AdapterStatus;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * 다른 인스턴스가 연결한 PLC 의 어댑터 — 요청 프레임만 소유 인스턴스로 넘기고 응답 프레임을 받는다(docs/plc-ha-design.md §3.1·§4).
 * 디스패치·감사·결과 전달은 요청을 받은 이 인스턴스가 그대로 한다.
 *
 * <ul>
 *   <li>요청·응답 객체는 그 PLC 종류의 어댑터({@code prototype}, 연결하지 않음)가 만들고 해석한다 — 넘기는 계약은 바이트뿐이다.</li>
 *   <li>소유 인스턴스가 409(주인 아님)면 PLC 에 보내기 전이라 주인을 다시 찾아 <b>한 번만</b> 다시 보낸다. 504·연결 오류는 PLC 에 이미
 *       갔을 수 있으므로 다시 보내지 않는다(WRITE 중복 방지, §4.2).</li>
 *   <li>기다리는 시간은 PLC 시간 초과 + 2초 — 소유 인스턴스의 504 가 그대로 전해지게.</li>
 * </ul>
 */
@Slf4j
public class RemotePlcAdapter implements ProgrammableControllerAdapter {

	public static final String NOT_OWNER = "ERR_409_TC_NOT_OWNER";

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

	private final long id;

	private final String name;

	private final String ownerInstance;

	private final String ownerUrl;

	private final ProgrammableControllerAdapter prototype;

	private final InternalSignature signature;

	private final String myInstance;

	private final long requestTimeoutMs;

	/** 주인을 다시 찾는다(409 뒤) — PLC 이름 → 지금 주인의 내부 주소(없으면 null) */
	private final Function<String, String> ownerUrlLookup;

	public RemotePlcAdapter(long id, String name, String ownerInstance, String ownerUrl, ProgrammableControllerAdapter prototype,
			InternalSignature signature, String myInstance, long requestTimeoutMs, Function<String, String> ownerUrlLookup) {
		this.id = id;
		this.name = name;
		this.ownerInstance = ownerInstance;
		this.ownerUrl = ownerUrl;
		this.prototype = prototype;
		this.signature = signature;
		this.myInstance = myInstance;
		this.requestTimeoutMs = requestTimeoutMs;
		this.ownerUrlLookup = ownerUrlLookup;
	}

	public String ownerInstance() {
		return ownerInstance;
	}

	public String ownerUrl() {
		return ownerUrl;
	}

	@Override
	public boolean isLocal() {
		return false;
	}

	@Override
	public void request(PlcRequest req, PlcResponse res) throws ToolControlException {
		prototype.completeResponse(res, exchangeFrame(req.getReqId(), req.convertRequest()));
	}

	@Override
	public byte[] exchangeFrame(String reqId, byte[] frame) throws ToolControlException {
		try {
			return post(ownerUrl, reqId, frame);
		} catch (NotOwner e) {
			String again = ownerUrlLookup.apply(name);
			if (again == null || again.equals(ownerUrl)) {
				throw ownerChanging();
			}
			log.info("PLC {} 의 주인이 바뀌어 다시 보냅니다: {} → {}", name, ownerUrl, again);
			try {
				return post(again, reqId, frame);
			} catch (NotOwner e2) {
				throw ownerChanging();
			}
		}
	}

	private ToolControlException ownerChanging() {
		return ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
			"PLC 를 연결한 인스턴스가 바뀌는 중입니다. 잠시 뒤에 다시 요청하세요: " + name);
	}

	private byte[] post(String baseUrl, String reqId, byte[] frame) throws ToolControlException, NotOwner {
		if (!signature.isConfigured()) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"TC_INTERNAL_SECRET 이 없어 PLC 를 연결한 다른 인스턴스로 요청을 넘기지 못합니다: " + name);
		}
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("frame", Base64.getEncoder().encodeToString(frame));
		request.put("timeoutMs", requestTimeoutMs);
		request.put("reqId", reqId);
		byte[] body;
		try {
			body = JSON.writeValueAsBytes(request);
		} catch (IOException e) {
			throw new ToolControlException("내부 전달 본문을 만들지 못했습니다", e);
		}
		long now = System.currentTimeMillis();
		String path = "/internal/plc/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/exchange";
		HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(Duration.ofMillis(requestTimeoutMs + 2_000))
			.header("Content-Type", "application/json")
			.header(InternalSignature.HEADER_INSTANCE, myInstance)
			.header(InternalSignature.HEADER_TIMESTAMP, String.valueOf(now))
			.header(InternalSignature.HEADER_SIGNATURE, signature.sign(name, now, body))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();
		HttpResponse<byte[]> response;
		try {
			response = HTTP.send(http, HttpResponse.BodyHandlers.ofByteArray());
		} catch (HttpTimeoutException e) {
			throw ToolControlException.of(ToolControlException.PLC_TIMEOUT,
				"PLC 를 연결한 인스턴스가 시간 안에 답하지 않았습니다(" + baseUrl + "): " + reqId);
		} catch (IOException e) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 를 연결한 인스턴스에 닿지 않습니다(" + baseUrl + "): " + name + " — " + e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 요청이 중단되었습니다: " + reqId);
		}
		Map<String, Object> answer = parse(response.body());
		if (response.statusCode() == 200 && answer.get("frame") instanceof String f) {
			return Base64.getDecoder().decode(f);
		}
		String code = answer.get("errorCode") instanceof String c ? c : null;
		if (response.statusCode() == 409 && NOT_OWNER.equals(code)) {
			throw new NotOwner();
		}
		if (response.statusCode() == 401) {
			// 비밀값이 인스턴스마다 다르다 — 운영 설정 오류
			log.error("PLC 를 연결한 인스턴스가 내부 요청 서명을 받지 않습니다(TC_INTERNAL_SECRET 이 같은지 확인): {}", baseUrl);
		}
		String message = answer.get("message") instanceof String m ? m : "HTTP " + response.statusCode();
		throw ToolControlException.of(code != null ? code : ToolControlException.PLC_UNAVAILABLE, message);
	}

	private static Map<String, Object> parse(byte[] body) {
		try {
			return body == null || body.length == 0 ? Map.of() : JSON.readValue(body, new TypeReference<Map<String, Object>>() { });
		} catch (IOException e) {
			return Map.of();
		}
	}

	/** 409 주인 아님 — PLC 로 보내기 전에 거절됐다 */
	private static final class NotOwner extends Exception {

		private static final long serialVersionUID = 1L;

		NotOwner() {
			super(null, null, false, false);
		}
	}

	@Override
	public void completeResponse(PlcResponse res, byte[] frame) throws ToolControlException {
		prototype.completeResponse(res, frame);
	}

	@Override
	public PlcRequest newPlcRequestInstance() {
		return prototype.newPlcRequestInstance();
	}

	@Override
	public PlcResponse newPlcResponseInstance() {
		return prototype.newPlcResponseInstance();
	}

	@Override
	public AdapterStatus status() {
		return new AdapterStatus(PlcConnectorOperStatus.RUNNING, 0, null, null);
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	// 연결이 없으니 수명 관리는 할 일이 없다

	@Override
	public void ready(long id, String name, Properties props) {
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void destroy() {
	}

	@Override
	public void shutdownQuietly() {
	}

	@Override
	public boolean testConnection() {
		return true;
	}
}
