package com.wow.be.tc.plc.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 인스턴스 사이 PLC 교환 서명 (docs/plc-ha-design.md §4.1) */
class InternalSignatureTest {

	private static final byte[] BODY = "{\"frame\":\"UAA=\",\"reqId\":\"r-1\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	void 같은_비밀값이면_통과() {
		InternalSignature a = new InternalSignature("s3cret", "");
		long now = System.currentTimeMillis();
		String sig = a.sign("plc-1", now, BODY);
		assertThat(new InternalSignature("s3cret", "").verify("plc-1", String.valueOf(now), sig, BODY)).isTrue();
	}

	@Test
	void 본문_PLC_이름_비밀값이_다르면_거절() {
		InternalSignature a = new InternalSignature("s3cret", "");
		long now = System.currentTimeMillis();
		String sig = a.sign("plc-1", now, BODY);
		assertThat(a.verify("plc-1", String.valueOf(now), sig, "{}".getBytes(StandardCharsets.UTF_8))).isFalse();
		assertThat(a.verify("plc-2", String.valueOf(now), sig, BODY)).isFalse();
		assertThat(new InternalSignature("other", "").verify("plc-1", String.valueOf(now), sig, BODY)).isFalse();
	}

	@Test
	void 시각이_30초_밖이면_거절() {
		InternalSignature a = new InternalSignature("s3cret", "");
		long old = System.currentTimeMillis() - InternalSignature.MAX_SKEW_MS - 1_000;
		assertThat(a.verify("plc-1", String.valueOf(old), a.sign("plc-1", old, BODY), BODY)).isFalse();
		assertThat(a.verify("plc-1", "not-a-number", a.sign("plc-1", old, BODY), BODY)).isFalse();
	}

	@Test
	void 비밀값을_바꾸는_동안_옛_값도_받는다() {
		long now = System.currentTimeMillis();
		String oldSig = new InternalSignature("old", "").sign("plc-1", now, BODY);
		assertThat(new InternalSignature("new", "old").verify("plc-1", String.valueOf(now), oldSig, BODY)).isTrue();
		assertThat(new InternalSignature("new", "").verify("plc-1", String.valueOf(now), oldSig, BODY)).isFalse();
	}

	@Test
	void 비밀값이_없으면_늘_거절() {
		InternalSignature none = new InternalSignature("", "");
		long now = System.currentTimeMillis();
		assertThat(none.isConfigured()).isFalse();
		assertThat(none.verify("plc-1", String.valueOf(now), none.sign("plc-1", now, BODY), BODY)).isFalse();
	}
}
