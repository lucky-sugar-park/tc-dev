package com.wow.be.tc.vplc.modbus;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import lombok.extern.slf4j.Slf4j;

/**
 * Modbus TCP 서버 — 포트 하나, 연결마다 가상 스레드. MBAP(트랜잭션·프로토콜 0·길이·단위 ID) + PDU 를 받아 그 단위 ID 의 장치로 답한다.
 * 장치가 없으면 게이트웨이처럼 예외 0x0B. 장치가 {@code silent} 면 답하지 않고, {@code delayMs} 만큼 늦게 답한다.
 */
@Slf4j
public final class ModbusTcpServer implements AutoCloseable {

	private final int port;

	private final ServerSocket socket;

	private final IntFunction<ModbusDevice> devices;

	private final Set<Socket> clients = ConcurrentHashMap.newKeySet();

	private final AtomicInteger connections = new AtomicInteger();

	private volatile boolean running = true;

	/** @param devices 단위 ID → 장치(없으면 null) */
	public ModbusTcpServer(String bindAddress, int port, IntFunction<ModbusDevice> devices) throws IOException {
		this.port = port;
		this.devices = devices;
		this.socket = new ServerSocket(port, 50, InetAddress.getByName(bindAddress));
		Thread.ofVirtual().name("modbus-accept-" + port).start(this::accept);
		log.info("가상 Modbus TCP 서버: {}:{}", bindAddress, port);
	}

	public int port() {
		return port;
	}

	public int connections() {
		return connections.get();
	}

	private void accept() {
		while (running) {
			try {
				Socket s = socket.accept();
				s.setTcpNoDelay(true);
				clients.add(s);
				connections.incrementAndGet();
				Thread.ofVirtual().name("modbus-" + port + "-" + s.getPort()).start(() -> serve(s));
			} catch (IOException e) {
				if (running) {
					log.warn("Modbus 연결 받기 실패: {} — {}", port, e.getMessage());
				}
			}
		}
	}

	private void serve(Socket s) {
		try (s; DataInputStream in = new DataInputStream(s.getInputStream()); OutputStream out = s.getOutputStream()) {
			byte[] head = new byte[7];
			while (running) {
				in.readFully(head);
				int length = (head[4] & 0xFF) << 8 | (head[5] & 0xFF);
				if ((head[2] | head[3]) != 0 || length < 2 || length > 260) {
					log.warn("Modbus 머리가 틀려 연결을 닫습니다: {} (protocol={}{}, length={})", port, head[2], head[3], length);
					return;
				}
				byte[] pdu = new byte[length - 1];
				in.readFully(pdu);
				int unit = head[6] & 0xFF;
				ModbusDevice d = devices.apply(unit);
				byte[] reply;
				if (d == null) {
					reply = ModbusPdu.exception(pdu.length > 0 ? pdu[0] & 0xFF : 0, ModbusPdu.GATEWAY_TARGET_FAILED);
				} else {
					if (d.silent()) {
						continue;
					}
					if (d.delayMs() > 0) {
						Thread.sleep(d.delayMs());
					}
					reply = ModbusPdu.handle(d, pdu);
				}
				byte[] frame = new byte[7 + reply.length];
				System.arraycopy(head, 0, frame, 0, 4);
				frame[4] = (byte) ((reply.length + 1) >> 8);
				frame[5] = (byte) (reply.length + 1);
				frame[6] = (byte) unit;
				System.arraycopy(reply, 0, frame, 7, reply.length);
				out.write(frame);
				out.flush();
			}
		} catch (EOFException | SocketException e) {
			// 상대가 닫음
		} catch (IOException e) {
			log.debug("Modbus 연결 오류: {} — {}", port, e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			clients.remove(s);
			connections.decrementAndGet();
		}
	}

	/** 연결을 모두 끊는다(포트는 계속 받는다) — tc 재연결 시험용 */
	public int dropConnections() {
		int n = 0;
		for (Socket c : clients) {
			try {
				c.close();
				n++;
			} catch (IOException e) {
				// 무시
			}
		}
		return n;
	}

	@Override
	public void close() {
		running = false;
		dropConnections();
		try {
			socket.close();
		} catch (IOException e) {
			// 무시
		}
	}

	/** 장치 표를 함수로 — 포트의 단위 ID 표 */
	public static IntFunction<ModbusDevice> lookup(Map<Integer, ModbusDevice> byUnit) {
		return byUnit::get;
	}
}
