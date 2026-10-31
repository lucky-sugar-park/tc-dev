package com.wow.be.tc.vplc.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.Types.MemoryType;

/** 가상 PLC 메모리 — 워드는 리틀엔디안 바이트로 읽고 쓴다, 바이트로 쓰면 감시자에게 알린다(다른 스레드) (#18) */
class MemoryTest {

	@Test
	void 바이트로_쓰고_읽기() {
		Memory mem = new Memory(MemoryType.D, 32);
		mem.write(new byte[] { 0x34, 0x12, (byte) 0xFF, (byte) 0xFF }, 3, 2);
		assertThat(mem.getWords()[3]).isEqualTo((short) 0x1234);
		assertThat(mem.getWords()[4]).isEqualTo((short) -1);
		assertThat(mem.read(3, 2)).containsExactly(0x34, 0x12, 0xFF, 0xFF);
		mem.writeWord(10, (byte) 0x07, (byte) 0x00);
		assertThat(mem.readWords(10, 1)[0].getWord()).isEqualTo((short) 7);
		mem.clear();
		assertThat(mem.read(3, 1)).containsExactly(0, 0);
	}

	@Test
	void 바이트로_쓰면_감시자에게() throws InterruptedException {
		Memory mem = new Memory(MemoryType.D, 16);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Memory.MemoryChangedEvent> got = new AtomicReference<>();
		mem.addWatcher(e -> {
			got.set(e);
			done.countDown();
		});
		mem.write(new byte[] { 1, 0, 2, 0 }, 2, 2);
		assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(got.get().getStartAddress()).isEqualTo(2);
		assertThat(got.get().getChangedWords()).hasSize(2);
	}
}
