package com.wow.be.tc.plc.management.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.protocol.WireProtocol;

/**
 * PLC 연결 시험 (#12 "포트 여러 개 병렬 시험") — 커넥터의 (포트, 로컬 VIP) 쌍마다 동시에 TCP 연결을 열고, 프로토콜의 연결
 * 확인(MELSEC 루프백 0619, Modbus 레지스터 하나 읽기)을 보내 PLC 가 그 프로토콜로 답하는지까지 본다. 연결을 바로 닫는다.
 *
 * <p>게시해 이미 연결해 둔 커넥터는 PLC 가 포트당 연결을 하나만 받으면 시험 연결이 거부된다 — 그때는 커넥터의 {@code connection} 을 본다.
 *
 * <p>결과: {@code result}(모두 되면 CONNECTION_TEST_OK, 아니면 CONNECTION_TEST_ERROR), 포트마다 {@code port:{포트}} = OK·사유.
 */
final class PlcConnectionTester {

	static final int CONNECT_TIMEOUT_MS = 3_000;

	static final int READ_TIMEOUT_MS = 3_000;

	private PlcConnectionTester() {
	}

	/** @param wire 프로토콜 전송 규약 — null 이면 TCP 연결만 본다 */
	static Map<String, String> test(String host, List<Integer> ports, List<String> localVips, WireProtocol wire) {
		List<CompletableFuture<String>> futures = new ArrayList<>();
		for (int i = 0; i < ports.size(); i++) {
			int port = ports.get(i);
			String vip = localVips != null && i < localVips.size() ? localVips.get(i) : null;
			futures.add(CompletableFuture.supplyAsync(() -> one(host, port, vip, wire)));
		}
		Map<String, String> result = new LinkedHashMap<>();
		boolean allOk = !futures.isEmpty();
		for (int i = 0; i < futures.size(); i++) {
			String r = futures.get(i).join();
			allOk &= "OK".equals(r);
			result.put("port:" + ports.get(i), r);
		}
		result.put("result", (allOk ? PlcConnectorOperStatus.CONNECTION_TEST_OK : PlcConnectorOperStatus.CONNECTION_TEST_ERROR).name());
		if (!allOk) {
			result.put("description", "게시해 연결 중인 커넥터는 PLC 가 포트당 연결 하나만 받으면 시험 연결이 거부될 수 있습니다 — 그때는 connection 을 보세요.");
		}
		return result;
	}

	private static String one(String host, int port, String localVip, WireProtocol wire) {
		try (Socket socket = new Socket()) {
			if (localVip != null && !localVip.isBlank()) {
				socket.bind(new InetSocketAddress(localVip, 0));
			}
			socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
			if (wire == null) {
				return "OK";
			}
			socket.setSoTimeout(READ_TIMEOUT_MS);
			OutputStream out = socket.getOutputStream();
			byte[] request = wire.healthCheckRequest();
			if (wire.hasSerial(request)) {
				wire.setSerial(request, 1);
			}
			out.write(request);
			out.flush();
			byte[] response = readFrame(socket.getInputStream(), wire);
			return wire.healthCheckOk(response) ? "OK" : "연결됐지만 연결 확인 응답이 맞지 않습니다";
		} catch (SocketTimeoutException e) {
			return "시간 초과(연결 또는 연결 확인 응답)";
		} catch (IOException e) {
			return "연결 실패: " + e.getMessage();
		}
	}

	private static byte[] readFrame(InputStream in, WireProtocol wire) throws IOException {
		byte[] buf = new byte[4096];
		int n = 0;
		while (true) {
			int r = in.read(buf, n, buf.length - n);
			if (r < 0) {
				throw new IOException("응답 전에 연결이 닫혔습니다");
			}
			n += r;
			int len = wire.responseLength(buf, n);
			if (len < 0) {
				throw new IOException("응답 머리가 아닙니다");
			}
			if (len > 0) {
				byte[] frame = new byte[len];
				System.arraycopy(buf, 0, frame, 0, len);
				return frame;
			}
			if (n == buf.length) {
				throw new IOException("응답이 너무 깁니다");
			}
		}
	}
}
