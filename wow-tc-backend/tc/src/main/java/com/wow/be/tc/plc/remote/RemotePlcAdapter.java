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
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PlcStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 다른 인스턴스가 연결한 PLC 의 어댑터 — 작업을 소유 인스턴스로 넘기고 결과를 받는다(docs/plc-ha-design.md §3.1·§4,
 * docs/plc-protocol-design.md §7·§15). 디스패치·감사·결과 전달은 요청을 받은 이 인스턴스가 그대로 한다.
 *
 * <ul>
 *   <li>POINTS 는 작업 단위({@code /execute} — 점·값·설정)로 넘기고 값과 PLC 상태를 받는다. 점 묶기·응답 검증은 주인이 한다.</li>
 *   <li>RAW 는 요청 프레임을 넘기고 응답 프레임을 받는다({@code /execute} op RAW). 요청·응답 객체는 그 PLC 종류의 어댑터
 *       ({@code prototype}, 연결하지 않음)가 만들고 해석한다.</li>
 *   <li>주인이 옛 버전이라 {@code /execute} 가 없으면(404) 3단계까지의 {@code /exchange}(프레임 바이트)로 넘긴다 — 순차 재기동 중.</li>
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

	/**
	 * 점 작업을 주인에게 작업 단위로 넘긴다({@code /execute}) — 점 묶기·응답 검증은 주인이 한다. 주인이 옛 버전이라 {@code /execute} 가
	 * 없으면(404, 순차 재기동 중) 여기서 드라이버로 프레임을 만들어 프레임마다 {@code /exchange} 로 넘긴다.
	 */
	@Override
	public PlcResult execute(PlcOperation op) throws ToolControlException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("op", op.kind().name());
		body.put("protocol", op.protocol());
		body.put("settings", op.settings());
		body.put("points", op.points());
		body.put("values", op.values());
		body.put("reqId", op.reqId());
		body.put("timeoutMs", requestTimeoutMs);
		// 주인은 묶은 프레임마다 시간 초과를 따로 센다 — 점이 많으면 그만큼 더 기다린다(상한 4배)
		long wait = requestTimeoutMs * Math.max(1, Math.min(op.points().size(), 4)) + 2_000;
		Map<String, Object> answer;
		try {
			answer = send("execute", body, wait);
		} catch (NoExecute e) {
			return PlcDrivers.of(op.protocol()).execute(op, this::exchangeLegacy);
		}
		Map<String, Object> values = JSON.convertValue(answer.get("values"), new TypeReference<Map<String, Object>>() { });
		PlcStatus status = JSON.convertValue(answer.get("status"), PlcStatus.class);
		if (status == null) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 를 연결한 인스턴스의 답에 상태가 없습니다: " + name);
		}
		return new PlcResult(values != null ? values : Map.of(), status);
	}

	/** RAW 프레임 하나 — {@code /execute}(op RAW), 주인이 옛 버전이면 {@code /exchange} */
	@Override
	public byte[] exchangeFrame(String reqId, byte[] frame) throws ToolControlException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("op", "RAW");
		body.put("frame", Base64.getEncoder().encodeToString(frame));
		body.put("reqId", reqId);
		body.put("timeoutMs", requestTimeoutMs);
		try {
			return frameOf(send("execute", body, requestTimeoutMs + 2_000));
		} catch (NoExecute e) {
			return exchangeLegacy(reqId, frame);
		}
	}

	/** 3단계까지의 계약 — 프레임 바이트 */
	private byte[] exchangeLegacy(String reqId, byte[] frame) throws ToolControlException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("frame", Base64.getEncoder().encodeToString(frame));
		body.put("timeoutMs", requestTimeoutMs);
		body.put("reqId", reqId);
		try {
			return frameOf(send("exchange", body, requestTimeoutMs + 2_000));
		} catch (NoExecute e) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 를 연결한 인스턴스가 내부 요청을 받지 않습니다(소유권 관리 꺼짐?): " + name);
		}
	}

	private byte[] frameOf(Map<String, Object> answer) throws ToolControlException {
		if (answer.get("frame") instanceof String f) {
			return Base64.getDecoder().decode(f);
		}
		throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 를 연결한 인스턴스의 답에 frame 이 없습니다: " + name);
	}

	/** 주인에게 보낸다. 409(주인 아님)면 주인을 다시 찾아 한 번만 다시 보낸다 — PLC 로 보내기 전에 거절된 것이라 WRITE 도 안전하다 */
	private Map<String, Object> send(String endpoint, Map<String, Object> body, long waitMs) throws ToolControlException, NoExecute {
		try {
			return post(ownerUrl, endpoint, body, waitMs);
		} catch (NotOwner e) {
			String again = ownerUrlLookup.apply(name);
			if (again == null || again.equals(ownerUrl)) {
				throw ownerChanging();
			}
			log.info("PLC {} 의 주인이 바뀌어 다시 보냅니다: {} → {}", name, ownerUrl, again);
			try {
				return post(again, endpoint, body, waitMs);
			} catch (NotOwner e2) {
				throw ownerChanging();
			}
		}
	}

	private ToolControlException ownerChanging() {
		return ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
			"PLC 를 연결한 인스턴스가 바뀌는 중입니다. 잠시 뒤에 다시 요청하세요: " + name);
	}

	/** @return 200 의 본문 */
	private Map<String, Object> post(String baseUrl, String endpoint, Map<String, Object> request, long waitMs)
			throws ToolControlException, NotOwner, NoExecute {
		if (!signature.isConfigured()) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"TC_INTERNAL_SECRET 이 없어 PLC 를 연결한 다른 인스턴스로 요청을 넘기지 못합니다: " + name);
		}
		byte[] body;
		try {
			body = JSON.writeValueAsBytes(request);
		} catch (IOException e) {
			throw new ToolControlException("내부 전달 본문을 만들지 못했습니다", e);
		}
		long now = System.currentTimeMillis();
		String path = "/internal/plc/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/" + endpoint;
		HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(Duration.ofMillis(waitMs))
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
				"PLC 를 연결한 인스턴스가 시간 안에 답하지 않았습니다(" + baseUrl + "): " + request.get("reqId"));
		} catch (IOException e) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 를 연결한 인스턴스에 닿지 않습니다(" + baseUrl + "): " + name + " — " + e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 요청이 중단되었습니다: " + request.get("reqId"));
		}
		Map<String, Object> answer = parse(response.body());
		if (response.statusCode() == 200) {
			return answer;
		}
		String code = answer.get("errorCode") instanceof String c ? c : null;
		if (response.statusCode() == 409 && NOT_OWNER.equals(code)) {
			throw new NotOwner();
		}
		if (response.statusCode() == 404) {
			// /execute 가 없는 옛 인스턴스(순차 재기동 중) 또는 소유권 관리를 끈 인스턴스 — PLC 에 보내기 전이다
			throw new NoExecute();
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

	/** 404 — 받는 쪽에 그 엔드포인트가 없다(옛 버전) 또는 소유권 관리가 꺼졌다. PLC 로 보내기 전이다 */
	private static final class NoExecute extends Exception {

		private static final long serialVersionUID = 1L;

		NoExecute() {
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
