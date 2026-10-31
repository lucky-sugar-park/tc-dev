package com.wow.be.tc.plc.connector.tcp;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.connector.AdapterStatus;
import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.plc.connector.PlcConnectorEvent.EventType;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.WireProtocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * TCP 위의 PLC 어댑터 공통 부분 — 연결·재연결·채널 빌려 쓰기·시간 초과·주기적 연결 확인·이벤트. PLC 하나에 하나이고, 연결을
 * 유지하며 요청마다 그 연결을 쓴다. 프로토콜마다 다른 것(응답 자르기·요청 번호·연결 확인 프레임)은 드라이버가 만든
 * {@link WireProtocol} 에 묻는다. 하위 클래스는 프로토콜 이름과 RAW 인터페이스 처리만 정한다(docs/plc-protocol-design.md §14).
 *
 * <p>docs/plc-connection-design.md §5.
 * <ul>
 *   <li><b>채널 단위 처리</b> — {@code min(포트 수, 로컬 VIP 수)} 개의 연결({@link TcpChannel})을 열고, 유휴 채널 대기열로
 *       빌려 쓴다. 연결 하나에 진행 중인 요청은 하나뿐이다. 채널이 N개면 N개를 동시에 처리한다.</li>
 *   <li><b>시간 초과</b> — 요청에 번호가 없으면(MELSEC 3E) 그 연결을 닫는다 — 늦게 온 응답이 다음 요청의 결과로 처리되지 않게.
 *       번호가 있으면(MELSEC 4E·Modbus TCP) 연결을 그대로 쓰고 늦은 응답은 번호로 버린다.</li>
 *   <li><b>재연결</b> — 연결이 닫히면 그 채널만 전용 스케줄러에서 간격을 늘려 가며 다시 연결한다. 어댑터가 멈추지 않는 한 계속한다.
 *       Netty 이벤트 루프에서는 재연결도, DB 기록(이벤트)도 하지 않는다.</li>
 * </ul>
 */
@Slf4j
public abstract class TcpPlcAdapter implements ProgrammableControllerAdapter {

	private static final int ASSIGN_DYNAMIC_PORT = 0;

	private static final int CONNECT_TIMEOUT_MS = 5000;

	private static final long MAX_RECONNECT_INTERVAL_MS = 30_000;

	private static final long DEFAULT_REQUEST_TIMEOUT_MS = 20_000;

	private long id;

	private String name;

	private String hostIp;

	private List<Integer> tports;

	private List<String> localVips;

	private PlcConnectionEventListener pceListener;

	private long retryIntervalRate;

	private boolean retryIntervalIncrement;

	/** 이 시각(ms)까지는 재연결을 {@link #fastRetryIntervalMs} 고정 간격으로 — 소유권을 넘겨받은 직후(docs/plc-ha-design.md §9.1 H1a) */
	private long fastRetryUntil;

	private long fastRetryIntervalMs;

	/** 정규화한 커넥터 설정 */
	protected Map<String, Object> settings = Map.of();

	/** 응답 자르기·요청 번호·연결 확인 — 드라이버가 설정으로 만든다 */
	private WireProtocol wire;

	/** 쉬는 채널 연결 확인 주기(초). 0 이면 하지 않는다(#12) */
	private long healthCheckIntervalSec;

	/** 끊김 사유 — 이벤트 cause 로 남는다 */
	public static final String CAUSE_LOOPBACK_FAILED = "LOOPBACK_FAILED";

	public static final String CAUSE_TIMEOUT = "TIMEOUT";

	/** 끊김 사유를 사람이 읽을 말로(마지막 오류) */
	public static String describe(String cause) {
		if (CAUSE_LOOPBACK_FAILED.equals(cause)) {
			return " — 연결 확인(루프백·시험 읽기)에 답이 없음";
		}
		if (CAUSE_TIMEOUT.equals(cause)) {
			return " — 응답 시간 초과";
		}
		return "";
	}

	/** 연결 확인 응답을 기다리는 상한 */
	static final long HEALTH_CHECK_TIMEOUT_MS = 3_000;

	private long requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;

	/** 기동(앱 재기동) 때는 PLC 가 아직 안 떠 있어도 어댑터를 올리고 뒤에서 연결한다. 게시 API 는 바로 실패를 알린다. */
	private boolean startEvenIfDisconnected;

	private volatile PlcConnectorOperStatus operStatus = PlcConnectorOperStatus.UNKNOWN;

	/** 마지막 연결 실패·끊김 — 연결 상태 화면용. 실패마다 이벤트(DB 이력)를 내면 재연결 중에 이력이 쌓이므로 값만 둔다 */
	private volatile LastError lastError;

	private record LastError(String message, Date at) {
	}

	/** 멈추는 중이면 false — 닫힌 연결을 다시 잇지 않는다 */
	private volatile boolean running;

	/** 앱 종료 중이면 true — 이벤트(DB 기록)를 내지 않는다 */
	private volatile boolean quiet;

	private final List<TcpChannel> channels = new ArrayList<>();

	private final BlockingQueue<TcpChannel> idle = new LinkedBlockingQueue<>();

	private EventLoopGroup workerGroup;

	private ScheduledExecutorService maintenance;

	/** {@code MELSEC_MC}, {@code MODBUS_TCP} — 드라이버를 고른다 */
	protected abstract String protocol();

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void ready(long id, String name, Properties props) throws ToolControlException {
		log.info("PLC 어댑터 준비: id={}, name={}", id, name);
		this.id = id;
		this.name = name;
		this.hostIp = props.getProperty("hostIp");
		this.tports = new ArrayList<>((List) props.get("targetPorts"));
		this.localVips = new ArrayList<>((List) props.get("localVips"));
		this.pceListener = (PlcConnectionEventListener) props.get("connectionListener");
		this.retryIntervalRate = (Long) props.get("plcRetryIntervalRate");
		this.retryIntervalIncrement = (Boolean) props.get("plcRetryIntervalIncrement");
		if (this.retryIntervalRate <= 0) {
			this.retryIntervalRate = 1000;
		}
		Object timeout = props.get("plcRequestTimeoutMs");
		if (timeout instanceof Long t && t > 0) {
			this.requestTimeoutMs = t;
		}
		this.startEvenIfDisconnected = Boolean.TRUE.equals(props.get("startEvenIfDisconnected"));
		if (props.get("settings") instanceof Map<?, ?> s) {
			this.settings = (Map<String, Object>) s;
			if (s.get("healthCheckIntervalSec") instanceof Number n) {
				this.healthCheckIntervalSec = n.longValue();
			}
		}
		this.wire = PlcDrivers.of(protocol()).wire(settings);
		if (props.get("fastRetryUntil") instanceof Long until && props.get("fastRetryIntervalMs") instanceof Long interval && interval > 0) {
			this.fastRetryUntil = until;
			this.fastRetryIntervalMs = interval;
		}
		this.start();
	}

	// ── 연결 ──

	@Override
	public void start() throws ToolControlException {
		int count = Math.min(tports.size(), localVips.size());
		if (count == 0) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 커넥터에 포트나 로컬 VIP 가 없습니다: " + name);
		}
		running = true;
		quiet = false;
		operStatus = PlcConnectorOperStatus.RUNNING;
		workerGroup = new NioEventLoopGroup(count);
		maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "plc-" + name + "-maint");
			t.setDaemon(true);
			return t;
		});

		synchronized (channels) {
			channels.clear();
			for (int i = 0; i < count; i++) {
				channels.add(new TcpChannel(name, tports.get(i), localVips.get(i), wire));
			}
		}

		// 처음에는 한 번씩만 시도한다 — 호출 스레드(게시 API·기동)를 오래 붙잡지 않는다
		int connected = 0;
		for (TcpChannel ch : channels) {
			if (connect(ch)) {
				connected++;
			} else {
				scheduleReconnect(ch);
			}
		}
		if (connected == 0 && !startEvenIfDisconnected) {
			shutdownInternals();
			operStatus = PlcConnectorOperStatus.STOPPED;
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"PLC 에 연결하지 못했습니다(포트 " + tports.subList(0, count) + "): " + name);
		}
		if (connected == 0) {
			log.warn("PLC 에 아직 연결하지 못했습니다. 뒤에서 계속 연결을 시도합니다: {}", name);
		}

		if (healthCheckIntervalSec > 0) {
			maintenance.scheduleWithFixedDelay(this::healthCheck, healthCheckIntervalSec, healthCheckIntervalSec, TimeUnit.SECONDS);
		}
		emit(EventType.START, null, "START_REQUEST", connected + " EA connections are established.");
	}

	/**
	 * 쉬는 채널마다 연결 확인(MELSEC 루프백, Modbus 레지스터 읽기)을 보내 반쯤 죽은 연결(TCP 는 살아 있는데 PLC 가 답하지 않음)을
	 * 찾는다(#12). 답이 없거나
	 * 틀리면 그 채널을 닫는다 — 재연결 스케줄러가 다시 잇는다. 요청 중인 채널은 건너뛴다.
	 */
	private void healthCheck() {
		if (!running) {
			return;
		}
		List<TcpChannel> snapshot;
		synchronized (channels) {
			snapshot = new ArrayList<>(channels);
		}
		for (TcpChannel ch : snapshot) {
			if (!running || !idle.remove(ch)) {
				continue;  // 요청 중이거나 끊긴 채널
			}
			boolean ok = false;
			try {
				byte[] response = exchangeOn(ch, "health-check", wire.healthCheckRequest(), System.currentTimeMillis() + HEALTH_CHECK_TIMEOUT_MS,
					CAUSE_LOOPBACK_FAILED);
				ok = wire.healthCheckOk(response);
			} catch (ToolControlException e) {
				ok = false;
			}
			if (ok) {
				if (ch.isConnected()) {
					idle.offer(ch);
				}
			} else {
				log.warn("PLC 연결 확인에 답이 없어 연결을 닫고 다시 잇습니다: {}", ch);
				ch.close(CAUSE_LOOPBACK_FAILED);
			}
		}
	}

	/** 연결을 한 번 시도한다. 성공하면 유휴 대기열에 넣는다. */
	private boolean connect(TcpChannel ch) {
		Bootstrap b = new Bootstrap()
			.group(workerGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
			.option(ChannelOption.SO_REUSEADDR, true)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel sc) {
					sc.pipeline().addLast(new FrameEncoder());
					sc.pipeline().addLast(new FrameDecoder(wire));
					sc.pipeline().addLast(new TcpChannel.ResponseHandler(ch));
				}
			});
		try {
			ChannelFuture cf = b.connect(new InetSocketAddress(hostIp, ch.port()),
				new InetSocketAddress(ch.localVip(), ASSIGN_DYNAMIC_PORT));
			if (!cf.awaitUninterruptibly(CONNECT_TIMEOUT_MS + 1000L) || !cf.isSuccess()) {
				String cause = cf.cause() != null ? cf.cause().toString() : "시간 초과";
				log.warn("PLC 연결 실패: {} — {}", ch, cause);
				lastError = new LastError("연결 실패 " + ch + ": " + cause, new Date());
				cf.channel().close();
				return false;
			}
			Channel nc = cf.channel();
			ch.attach(nc);
			nc.closeFuture().addListener(f -> onChannelClosed(ch, nc));
			idle.offer(ch);
			log.info("PLC 연결: {} (local {})", ch, nc.localAddress());
			emit(EventType.CONNECT, nc, "CONNECTED", activeCount() + " EA connections are available.");
			return true;
		} catch (RuntimeException e) {
			log.warn("PLC 연결 실패: {} — {}", ch, e.toString());
			lastError = new LastError("연결 실패 " + ch + ": " + e, new Date());
			return false;
		}
	}

	/** Netty 이벤트 루프에서 불린다 — 일은 재연결 스케줄러로 넘긴다 */
	private void onChannelClosed(TcpChannel ch, Channel nc) {
		if (!ch.detachIf(nc)) {
			return;
		}
		idle.remove(ch);
		ch.failPending(ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 연결이 끊겼습니다: " + ch));
		if (!running) {
			return;
		}
		String cause = ch.takeCloseCause();
		log.warn("PLC 연결 끊김: {}{} — 다시 연결합니다", ch, cause != null ? " (" + cause + ")" : "");
		lastError = new LastError("연결 끊김 " + ch + describe(cause), new Date());
		submit(() -> {
			emit(EventType.DISCONNECT, nc, cause != null ? cause : "DISCONNECTED", activeCount() + " EA connections are available.");
			scheduleReconnect(ch);
		});
	}

	private void scheduleReconnect(TcpChannel ch) {
		if (!running) {
			return;
		}
		int attempt = ch.nextReconnectAttempt();
		long delay = System.currentTimeMillis() < fastRetryUntil ? fastRetryIntervalMs
			: Math.min(retryIntervalIncrement ? retryIntervalRate * attempt : retryIntervalRate, MAX_RECONNECT_INTERVAL_MS);
		try {
			maintenance.schedule(() -> {
				if (!running || ch.isConnected()) {
					return;
				}
				if (!connect(ch)) {
					scheduleReconnect(ch);
				}
			}, delay, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException e) {
			// 멈추는 중
		}
	}

	private void submit(Runnable task) {
		try {
			maintenance.execute(task);
		} catch (RejectedExecutionException e) {
			// 멈추는 중
		}
	}

	@Override
	public AdapterStatus status() {
		LastError e = lastError;
		return new AdapterStatus(operStatus, activeCount(), e != null ? e.message() : null, e != null ? e.at() : null);
	}

	private int activeCount() {
		synchronized (channels) {
			return (int) channels.stream().filter(TcpChannel::isConnected).count();
		}
	}

	// ── 요청 ──

	@Override
	public byte[] exchangeFrame(String reqId, byte[] frame) throws ToolControlException {
		if (operStatus == PlcConnectorOperStatus.PAUSED || operStatus == PlcConnectorOperStatus.UNKNOWN
				|| operStatus == PlcConnectorOperStatus.STOPPED) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "요청할 수 없는 운영 상태입니다: " + operStatus);
		}
		return exchange(reqId, frame);
	}

	/**
	 * 쉬는 채널을 빌려 요청 하나를 주고받는다. 빌리는 데 기다린 시간도 시간 초과에 들어간다.
	 *
	 * @return 응답 프레임
	 */
	private byte[] exchange(String reqId, byte[] reqData) throws ToolControlException {
		long deadline = System.currentTimeMillis() + requestTimeoutMs;
		TcpChannel ch = borrow(deadline);
		boolean returned = false;
		try {
			byte[] response = exchangeOn(ch, reqId, reqData, deadline, CAUSE_TIMEOUT);
			returned = true;
			return response;
		} catch (ToolControlException e) {
			// 번호가 있는 요청의 시간 초과는 연결을 그대로 쓴다(exchangeOn 이 닫지 않음) — 늦은 응답은 번호로 버린다
			returned = ch.isConnected() && e.getErrorCode().equals(ToolControlException.PLC_TIMEOUT) && wire.hasSerial(reqData);
			throw e;
		} finally {
			if (returned && ch.isConnected()) {
				idle.offer(ch);
			}
		}
	}

	/**
	 * 정해진 채널로 요청 하나를 주고받는다. 번호가 없는 요청(3E)이 시간 초과면 그 연결을 닫는다(늦은 응답이 다음 요청의 결과가
	 * 되지 않게). 번호가 있으면(4E·Modbus) 늦은 응답을 번호로 가려내므로 닫지 않는다. 채널을 대기열에 돌려놓는 일은 부르는 쪽이 한다.
	 *
	 * @param timeoutCause 시간 초과로 닫을 때 남길 사유 — 연결 확인이 부르면 LOOPBACK_FAILED. 예전에는 늘 TIMEOUT 으로 닫아, 연결
	 *                     확인이 뒤이어 남긴 사유와 경쟁해 끊김 사유가 "응답 시간 초과"로 남기도 했다
	 */
	private byte[] exchangeOn(TcpChannel ch, String reqId, byte[] reqData, long deadline, String timeoutCause) throws ToolControlException {
		boolean keep = wire.hasSerial(reqData);
		try {
			CompletableFuture<byte[]> future = ch.begin(reqData);
			Payload payload = new Payload();
			payload.setTimestamp(System.currentTimeMillis());
			payload.setData(reqData);

			Channel nc = ch.channel();
			if (nc == null) {
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 연결이 끊겼습니다: " + ch);
			}
			ChannelFuture written = nc.writeAndFlush(payload);
			if (!written.awaitUninterruptibly(remaining(deadline)) || !written.isSuccess()) {
				ch.close();
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 로 요청을 보내지 못했습니다: " + ch);
			}

			return future.get(remaining(deadline), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			if (keep) {
				log.warn("PLC 응답 시간 초과(연결은 그대로, 늦은 응답은 번호로 버림): {}, reqId={}", ch, reqId);
			} else {
				// 늦게 온 응답이 다음 요청의 결과가 되지 않도록 이 연결을 버린다 — 재연결 스케줄러가 새로 잇는다
				log.warn("PLC 응답 시간 초과로 연결을 닫습니다: {}, reqId={}", ch, reqId);
				ch.close(timeoutCause);
			}
			throw ToolControlException.of(ToolControlException.PLC_TIMEOUT,
				"PLC 응답 시간이 초과되었습니다(" + requestTimeoutMs + "ms): " + reqId);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof ToolControlException tce) {
				throw tce;
			}
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 요청이 실패했습니다: " + cause);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			ch.close();
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 요청이 중단되었습니다: " + reqId);
		} finally {
			ch.end();
		}
	}

	private TcpChannel borrow(long deadline) throws ToolControlException {
		while (true) {
			long wait = remaining(deadline);
			if (wait <= 0) {
				break;
			}
			TcpChannel ch;
			try {
				ch = idle.poll(wait, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 요청이 중단되었습니다.");
			}
			if (ch == null) {
				break;
			}
			if (ch.isConnected()) {
				return ch;
			}
			// 대기열에 있는 사이 끊긴 채널 — 버리고 다시 빌린다(재연결 스케줄러가 다시 넣는다)
		}
		if (activeCount() == 0) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 와 연결된 채널이 없습니다: " + name);
		}
		throw ToolControlException.of(ToolControlException.PLC_TIMEOUT,
			"모든 PLC 채널이 사용 중이라 시간 안에 요청하지 못했습니다(" + requestTimeoutMs + "ms): " + name);
	}

	private static long remaining(long deadline) {
		return Math.max(0, deadline - System.currentTimeMillis());
	}

	// ── 상태 ──

	// No use - 현재 connection을 테스트 할 때에 netty로 직접 연결 테스트하도록 되어 있음
	@Override
	public boolean testConnection() throws ToolControlException {
		return true;
	}

	@Override
	public void pause() {
		this.operStatus = PlcConnectorOperStatus.PAUSED;
	}

	@Override
	public void resume() {
		this.operStatus = PlcConnectorOperStatus.RESUMED;
	}

	@Override
	public void stop() {
		this.operStatus = PlcConnectorOperStatus.STOPPED;
		List<String> localAddresses = new ArrayList<>();
		synchronized (channels) {
			channels.forEach(ch -> {
				Channel nc = ch.channel();
				if (nc != null && nc.localAddress() != null) {
					localAddresses.add(nc.localAddress().toString());
				}
			});
		}
		shutdownInternals();
		emit(EventType.SHUTDOWN, null, "STOP_REQUEST", "0 EA connections are available.", localAddresses.toString());
	}

	@Override
	public void shutdownQuietly() {
		quiet = true;
		this.operStatus = PlcConnectorOperStatus.STOPPED;
		shutdownInternals();
		log.info("PLC 연결을 닫았습니다(앱 종료): {}", name);
	}

	/** 재연결을 멈추고 연결을 모두 닫는다 */
	private void shutdownInternals() {
		running = false;
		if (maintenance != null) {
			maintenance.shutdownNow();
		}
		synchronized (channels) {
			channels.forEach(TcpChannel::close);
		}
		idle.clear();
		if (workerGroup != null) {
			// 종료를 기다리지 않는다(예전에 끝나기를 기다리다 커넥터 상태가 꼬인 일이 있음). 조용한 기간 없이 바로 닫기 시작한다.
			workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
		}
	}

	@Override
	public void destroy() {
		synchronized (channels) {
			channels.clear();
		}
	}

	// ── 이벤트(이력 기록) ──

	private void emit(EventType type, Channel nc, String cause, String description) {
		emit(type, nc, cause, description, null);
	}

	private void emit(EventType type, Channel nc, String cause, String description, String localOverride) {
		if (quiet || pceListener == null) {
			return;
		}
		try {
			PlcConnectorEvent event = new PlcConnectorEvent();
			event.setPlcConnectorId(id);
			event.setPlcConnectorName(name);
			event.setOperStatus(operStatus);
			event.setActiveChannelCount(activeCount());
			event.setType(type);
			event.setCause(cause);
			event.setDescription(description);
			event.setTimestamp(new Date());
			if (nc != null) {
				setAddress(event, nc.remoteAddress(), true);
				setAddress(event, nc.localAddress(), false);
			} else {
				event.setRemoteIp(hostIp);
				event.setLocalIp(localOverride != null ? localOverride : localVips.toString());
			}
			pceListener.onEvent(event);
		} catch (RuntimeException e) {
			log.warn("PLC 연결 이벤트를 남기지 못했습니다: {} {}", name, type, e);
		}
	}

	private static void setAddress(PlcConnectorEvent event, SocketAddress address, boolean remote) {
		if (!(address instanceof InetSocketAddress a)) {
			return;
		}
		String ip = a.getAddress() != null ? a.getAddress().getHostAddress() : a.getHostString();
		if (remote) {
			event.setRemoteIp(ip);
			event.setRemotePort(a.getPort());
		} else {
			event.setLocalIp(ip);
			event.setLocalPort(a.getPort());
		}
	}
}
