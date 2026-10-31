package com.wow.be.tc.delivery.redelivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.impl.ExchangedMessageAuditEntity;

/**
 * 감사 기록 → 재전송할 응답·대상 (docs/redelivery-design.md §2, §4).
 */
class AuditResponsesTest {

	private static ExchangedMessageAuditInfo audit(String type, String description, Map<String, Object> data) {
		ExchangedMessageAuditInfo info = new ExchangedMessageAuditInfo();
		info.setTransactionId("t-1");
		info.setMessageId("MSG-1");
		info.setName("line1.temp.read");
		info.setMessageType(type);
		info.setTimestamp(new Date(1_700_000_000_123L));
		info.setSender("tc");
		info.setReceiver("mes");
		info.setDescription(description);
		info.setDataMap(data);
		return info;
	}

	/** 저장(Gson)과 읽기(Jackson)를 거친 감사 — 실제 DB 를 지난 값과 같다 */
	private static ExchangedMessageAuditInfo stored(ExchangedMessageAuditInfo info) {
		ExchangedMessageAuditEntity entity = new ExchangedMessageAuditEntity();
		entity.from(info);
		entity.setId(1L);
		return entity.info();
	}

	@Test
	void 성공_응답_기록은_data_를_타입_그대로_되살린다() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("d100", 77);
		data.put("temp", 21.5);
		data.put("name", "A-1");

		TcMessageResponse response = AuditResponses.fromResponseAudit(stored(audit("RESPONSE", "", data)));

		assertThat(response.getSuccess()).isTrue();
		assertThat(response.getErrorCode()).isNull();
		assertThat(response.getData()).containsEntry("d100", 77).containsEntry("temp", 21.5).containsEntry("name", "A-1");
		assertThat(response.getTransactionId()).isEqualTo("t-1");
		assertThat(response.getMessageId()).isEqualTo("MSG-1");
		assertThat(response.getOpcode()).isEqualTo("line1.temp.read");
		assertThat(response.getTimestamp()).isEqualTo(1_700_000_000_123L);
		assertThat(response.getResultCode()).isNull();
	}

	@Test
	void 실패_응답_기록은_설명에서_코드와_메시지를_나눈다() {
		TcMessageResponse response = AuditResponses.fromResponseAudit(
			audit("RESPONSE", "ERR_502_TC_PLC_ERROR: PLC 오류: 종료 코드 0xC051", Map.of()));

		assertThat(response.getSuccess()).isFalse();
		assertThat(response.getErrorCode()).isEqualTo("ERR_502_TC_PLC_ERROR");
		assertThat(response.getErrorMessage()).isEqualTo("PLC 오류: 종료 코드 0xC051");
	}

	@Test
	void 메시지가_null_로_남은_실패도_읽는다() {
		TcMessageResponse response = AuditResponses.fromResponseAudit(audit("RESPONSE", "ERR_502_TC_PLC_ERROR: null", Map.of()));

		assertThat(response.getErrorCode()).isEqualTo("ERR_502_TC_PLC_ERROR");
		assertThat(response.getErrorMessage()).isNull();
	}

	@Test
	void 오류_기록은_실패_응답이_된다() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("errorCode", "ERR_503_TC_PLC_UNAVAILABLE");
		data.put("errorMessage", "게시(publish)된 PLC 커넥터가 아닙니다: plc-1");

		TcMessageResponse response = AuditResponses.fromErrorAudit(stored(audit("ERROR", "ERR_503…", data)));

		assertThat(response.getSuccess()).isFalse();
		assertThat(response.getErrorCode()).isEqualTo("ERR_503_TC_PLC_UNAVAILABLE");
		assertThat(response.getErrorMessage()).contains("plc-1");
	}

	@Test
	void 전달_기록의_본문은_보낸_응답_그대로다() {
		TcMessageResponse sent = new TcMessageResponse();
		sent.setTransactionId("t-9");
		sent.setOpcode("line1.temp.read");
		sent.setTimestamp(1_700_000_000_999L);
		sent.setSuccess(true);
		sent.setResultCode(0);
		sent.setResultDescription("정상");
		sent.setChanged(true);
		sent.setData(new LinkedHashMap<>(Map.of("d100", 77)));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("response", new ObjectMapper().convertValue(sent, new TypeReference<Map<String, Object>>() {}));
		body.put("redeliveryOf", null);

		TcMessageResponse response = AuditResponses.fromDeliveryBody(stored(audit("DELIVERY", "OK", body)).getDataMap(), "response");

		assertThat(response).isNotNull();
		assertThat(response.getResultCode()).isZero();
		assertThat(response.getResultDescription()).isEqualTo("정상");
		assertThat(response.isChanged()).isTrue();
		assertThat(response.getTimestamp()).isEqualTo(1_700_000_000_999L);
		assertThat(response.getData()).containsEntry("d100", 77);
	}

	@Test
	void 보완_전_전달_기록은_본문이_없다() {
		assertThat(AuditResponses.fromDeliveryBody(stored(audit("DELIVERY", "OK", Map.of())).getDataMap(), "response")).isNull();
	}

	@Test
	void 대상은_세_조각까지만_나눈다() {
		assertThat(AuditResponses.parseReceiver("WEBHOOK:mes-hook:http://mes:8080/hook"))
			.isEqualTo(new AuditResponses.Receiver(DeliveryType.WEBHOOK, "mes-hook", "http://mes:8080/hook"));
		assertThat(AuditResponses.parseReceiver("DB_TABLE:mes-db:mes.plc_result"))
			.isEqualTo(new AuditResponses.Receiver(DeliveryType.DB_TABLE, "mes-db", "mes.plc_result"));
		assertThat(AuditResponses.parseReceiver("CALLBACK_URL:callback:null")).isNull();
		assertThat(AuditResponses.parseReceiver("KAFKA:x:y")).isNull();
		assertThat(AuditResponses.parseReceiver("tc")).isNull();
		assertThat(AuditResponses.parseReceiver(null)).isNull();
	}
}
