package com.wow.be.tc.plc.connector.melsec;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.PlcReqAndResHolder;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcRequestCallback;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.Payload;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.plc.connector.PlcConnectorEvent.EventType;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.utils.ByteUtil;
import com.wow.be.tc.utils.ByteUtil.Endian;
import com.wow.be.tc.utils.TimerUtil;

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
 * MELSEC MC 프로토콜(3E 프레임) 어댑터. PLC 하나에 하나이고, 연결을 유지하며 요청마다 그 연결을 쓴다.
 *
 * <p>docs/plc-connection-design.md §5.
 * <ul>
 *   <li><b>채널 단위 처리</b> — {@code min(포트 수, 로컬 VIP 수)} 개의 연결({@link MelsecChannel})을 열고, 유휴 채널 대기열로
 *       빌려 쓴다. 3E 프레임에는 짝지을 번호가 없어 연결 하나에 진행 중인 요청은 하나뿐이다. 채널이 N개면 N개를 동시에 처리한다.</li>
 *   <li><b>시간 초과면 그 연결을 닫는다</b> — 늦게 온 응답이 다음 요청의 결과로 처리되지 않게 한다.</li>
 *   <li><b>재연결</b> — 연결이 닫히면 그 채널만 전용 스케줄러에서 간격을 늘려 가며 다시 연결한다. 어댑터가 멈추지 않는 한 계속한다.
 *       Netty 이벤트 루프에서는 재연결도, DB 기록(이벤트)도 하지 않는다.</li>
 * </ul>
 */
@Slf4j
public class MelsecProgrammableControllerAdapter implements ProgrammableControllerAdapter {

	private static final int ASSIGN_DYNAMIC_PORT = 0;

	private static final int CONNECT_TIMEOUT_MS = 5000;

	private static final long MAX_RECONNECT_INTERVAL_MS = 30_000;

	private static final long DEFAULT_REQUEST_TIMEOUT_MS = 20_000;

	private long id;

	private String name;

	private String hostIp;

	private List<Integer> tports;

	private List<String> localVips;

	private volatile boolean asyncUse = true;

	private long asyncTimerStartDelay;

	private long asyncTimerRunInterval;

	private String messageFrameFormat;

	private PlcConnectionEventListener pceListener;

	private long retryIntervalRate;

	private boolean retryIntervalIncrement;

	private long requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;

	/** 기동(앱 재기동) 때는 PLC 가 아직 안 떠 있어도 어댑터를 올리고 뒤에서 연결한다. 게시 API 는 바로 실패를 알린다. */
	private boolean startEvenIfDisconnected;

	private volatile PlcConnectorOperStatus operStatus = PlcConnectorOperStatus.UNKNOWN;

	/** 멈추는 중이면 false — 닫힌 연결을 다시 잇지 않는다 */
	private volatile boolean running;

	/** 앱 종료 중이면 true — 이벤트(DB 기록)를 내지 않는다 */
	private volatile boolean quiet;

	private final List<MelsecChannel> channels = new ArrayList<>();

	private final BlockingQueue<MelsecChannel> idle = new LinkedBlockingQueue<>();

	private EventLoopGroup workerGroup;

	private ScheduledExecutorService maintenance;

	// ── 비동기 요청(타이머 큐) — 레거시 기능 그대로 ──

	private final Deque<PlcReqAndResHolder> q = new ConcurrentLinkedDeque<>();

	private final Map<String, PlcRequestCallback> reqCallbacks = new ConcurrentHashMap<>();

	private Timer timer;

	private TimerTask qworker;

	public MelsecProgrammableControllerAdapter() {
		this("BINARY");
	}

	public MelsecProgrammableControllerAdapter(String messageFrameFormat) {
		this.messageFrameFormat = messageFrameFormat;
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void pauseAsyncUse() {
		this.asyncUse = false;
	}

	@Override
	public void resumeAsyncUse() {
		this.asyncUse = true;
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
		this.asyncUse = (Boolean) props.get("asyncUse");
		this.messageFrameFormat = props.getProperty("messageFrameFormat");
		this.asyncTimerStartDelay = (Long) props.get("asyncTimerStartDelay");
		this.asyncTimerRunInterval = (Long) props.get("asyncTimerRunInterval");
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
				channels.add(new MelsecChannel(name, tports.get(i), localVips.get(i)));
			}
		}

		// 처음에는 한 번씩만 시도한다 — 호출 스레드(게시 API·기동)를 오래 붙잡지 않는다
		int connected = 0;
		for (MelsecChannel ch : channels) {
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

		prepareTimer();
		emit(EventType.START, null, "START_REQUEST", connected + " EA connections are established.");
	}

	/** 연결을 한 번 시도한다. 성공하면 유휴 대기열에 넣는다. */
	private boolean connect(MelsecChannel ch) {
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
					sc.pipeline().addLast(new NettyMessageEncoder());
					sc.pipeline().addLast(new NettyMessageDecoder(messageFrameFormat));
					sc.pipeline().addLast(new MelsecChannel.ResponseHandler(ch));
				}
			});
		try {
			ChannelFuture cf = b.connect(new InetSocketAddress(hostIp, ch.port()),
				new InetSocketAddress(ch.localVip(), ASSIGN_DYNAMIC_PORT));
			if (!cf.awaitUninterruptibly(CONNECT_TIMEOUT_MS + 1000L) || !cf.isSuccess()) {
				log.warn("PLC 연결 실패: {} — {}", ch, cf.cause() != null ? cf.cause().toString() : "시간 초과");
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
			return false;
		}
	}

	/** Netty 이벤트 루프에서 불린다 — 일은 재연결 스케줄러로 넘긴다 */
	private void onChannelClosed(MelsecChannel ch, Channel nc) {
		if (!ch.detachIf(nc)) {
			return;
		}
		idle.remove(ch);
		ch.failPending(ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 연결이 끊겼습니다: " + ch));
		if (!running) {
			return;
		}
		log.warn("PLC 연결 끊김: {} — 다시 연결합니다", ch);
		submit(() -> {
			emit(EventType.DISCONNECT, nc, "DISCONNECTED", activeCount() + " EA connections are available.");
			scheduleReconnect(ch);
		});
	}

	private void scheduleReconnect(MelsecChannel ch) {
		if (!running) {
			return;
		}
		int attempt = ch.nextReconnectAttempt();
		long delay = Math.min(retryIntervalIncrement ? retryIntervalRate * attempt : retryIntervalRate, MAX_RECONNECT_INTERVAL_MS);
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

	private int activeCount() {
		synchronized (channels) {
			return (int) channels.stream().filter(MelsecChannel::isConnected).count();
		}
	}

	// ── 요청 ──

	@Override
	public void request(PlcRequest req, PlcResponse res) throws ToolControlException {
		if (operStatus == PlcConnectorOperStatus.PAUSED || operStatus == PlcConnectorOperStatus.UNKNOWN
				|| operStatus == PlcConnectorOperStatus.STOPPED) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "요청할 수 없는 운영 상태입니다: " + operStatus);
		}
		byte[] response = exchange(req.getReqId(), req.convertRequest());
		res.splitHeaderAndReplyData(response);
		int resultCode = ByteUtil.bytesToInt((byte[]) res.getReplyHeaders().get("resultCode"), Endian.LITTLE);
		res.setResultCode(resultCode);
		res.setResultDescription(resultCode == 0 ? "SUCCESS" : "ERROR");
	}

	/**
	 * 쉬는 채널을 빌려 요청 하나를 주고받는다. 빌리는 데 기다린 시간도 시간 초과에 들어간다.
	 *
	 * @return 응답 프레임(subHeader~resultCode~데이터)
	 */
	private byte[] exchange(String reqId, byte[] reqData) throws ToolControlException {
		long deadline = System.currentTimeMillis() + requestTimeoutMs;
		MelsecChannel ch = borrow(deadline);

		boolean healthy = false;
		try {
			CompletableFuture<byte[]> future = ch.begin();
			Payload payload = new Payload();
			payload.setTimestamp(System.currentTimeMillis());
			payload.setData(reqData);
			payload.setFrameFormat(messageFrameFormat);

			Channel nc = ch.channel();
			if (nc == null) {
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 연결이 끊겼습니다: " + ch);
			}
			ChannelFuture written = nc.writeAndFlush(payload);
			if (!written.awaitUninterruptibly(remaining(deadline)) || !written.isSuccess()) {
				ch.close();
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "PLC 로 요청을 보내지 못했습니다: " + ch);
			}

			byte[] response = future.get(remaining(deadline), TimeUnit.MILLISECONDS);
			healthy = true;
			return response;
		} catch (TimeoutException e) {
			// 늦게 온 응답이 다음 요청의 결과가 되지 않도록 이 연결을 버린다 — 재연결 스케줄러가 새로 잇는다
			log.warn("PLC 응답 시간 초과로 연결을 닫습니다: {}, reqId={}", ch, reqId);
			ch.close();
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
			if (healthy && ch.isConnected()) {
				idle.offer(ch);
			}
		}
	}

	private MelsecChannel borrow(long deadline) throws ToolControlException {
		while (true) {
			long wait = remaining(deadline);
			if (wait <= 0) {
				break;
			}
			MelsecChannel ch;
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

	@Override
	public void request(PlcReqAndResHolder reqAndResHolder, PlcRequestCallback reqCallback) throws ToolControlException {
		if (!asyncUse || operStatus == PlcConnectorOperStatus.PAUSED || operStatus == PlcConnectorOperStatus.UNKNOWN) {
			throw new ToolControlException("비동기 요청을 쓰지 않는 커넥터이거나 요청할 수 없는 운영 상태입니다: 비동기 " + asyncUse + ", 상태 " + operStatus);
		}
		reqCallbacks.put(reqAndResHolder.getRequest().getReqId(), reqCallback);
		q.add(reqAndResHolder);
	}

	private void prepareTimer() {
		this.qworker = new TimerTask() {
			@Override
			public void run() {
				if (q.isEmpty()) {
					return;
				}
				List<PlcReqAndResHolder> reqList = new ArrayList<>();
				while (!q.isEmpty()) {
					reqList.add(q.poll());
				}
				reqList.forEach(holder -> {
					PlcRequest req = holder.getRequest();
					PlcResponse res = holder.getResponse();
					try {
						request(req, res);
						PlcRequestCallback reqCallback = reqCallbacks.remove(req.getReqId());
						if (reqCallback != null) {
							reqCallback.callbackRequest(res);
						}
					} catch (ToolControlException e) {
						log.warn("Fail to execute PLC request: {}", req.getReqId(), e);
					}
				});
				TimerUtil.waitWithSleep(500);
			}
		};
		// 데몬으로 실행함 (Main thread 종료되면 timer도 종료되어야 함)
		this.timer = new Timer("plc-" + name + "-async", true);
		this.timer.scheduleAtFixedRate(qworker, asyncTimerStartDelay, asyncTimerRunInterval);
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
		if (timer != null) {
			timer.cancel();
		}
		if (maintenance != null) {
			maintenance.shutdownNow();
		}
		synchronized (channels) {
			channels.forEach(MelsecChannel::close);
		}
		idle.clear();
		if (workerGroup != null) {
			// 종료를 기다리지 않는다(예전에 끝나기를 기다리다 커넥터 상태가 꼬인 일이 있음). 조용한 기간 없이 바로 닫기 시작한다.
			workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
		}
	}

	@Override
	public PlcRequest newPlcRequestInstance() {
		return new MelsecPlcRequest();
	}

	@Override
	public PlcResponse newPlcResponseInstance() {
		return new MelsecPlcResponse();
	}

	@Override
	public void destroy() {
		q.clear();
		reqCallbacks.clear();
		synchronized (channels) {
			channels.clear();
		}
		if (timer != null) {
			timer.cancel();
		}
		if (qworker != null) {
			qworker.cancel();
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
