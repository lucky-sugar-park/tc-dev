package com.wow.be.tc.plc.connector.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.subscriptions.OpcUaSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.eclipse.milo.opcua.stack.core.types.structured.DataChangeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.protocol.PlcStatus;
import com.wow.be.tc.plc.protocol.PointScaling;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.opcua.OpcUaDriver;
import com.wow.be.tc.security.RequestPrincipals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OPC UA 구독(docs/opcua-design.md O8·O9) — 이 인스턴스의 로컬 OPC UA 어댑터마다 SUBSCRIBE 인터페이스의 구독을 맞추고,
 * 값이 바뀌어 알림이 오면 그 인터페이스의 최근 값 전체를 전달 정의로 보내고 감사(RESPONSE)를 남긴다(결정 I4).
 *
 * <ul>
 *   <li>맞추기는 {@value #RECONCILE_MS}ms 마다 — 인터페이스가 생기거나 바뀌면(정의 서명이 다르면) 그 구독만 다시 만든다.</li>
 *   <li>세션을 새로 만들면({@link OpcUaAdapter#sessionGeneration()} 이 바뀜) 그 어댑터의 구독을 모두 다시 만든다.</li>
 *   <li>원격 어댑터(다른 인스턴스가 주인)는 구독하지 않는다 — 주인이 바뀌면 새 주인이 만든다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpcUaSubscriptionService {

	static final long RECONCILE_MS = 10_000;

	public static final String SENDER = "opcua-subscription";

	private final InterfaceService interfaceService;

	private final ResultDeliveryService resultDeliveryService;

	private final ExchangedMessageAuditService auditService;

	/** 커넥터 이름 → 그 어댑터의 구독들 */
	private final Map<String, AdapterSubs> state = new ConcurrentHashMap<>();

	private static final class AdapterSubs {

		int generation;

		final Map<String, Sub> byInterface = new HashMap<>();
	}

	/** 인터페이스 하나의 구독 — 정의가 바뀌었는지는 signature 로 본다 */
	private static final class Sub {

		final String signature;

		final OpcUaSubscription subscription;

		/** 점 이름 → 최근 값(응답 값, 배율 전) */
		final Map<String, Object> latest = new LinkedHashMap<>();

		Sub(String signature, OpcUaSubscription subscription) {
			this.signature = signature;
			this.subscription = subscription;
		}
	}

	@Scheduled(fixedDelay = RECONCILE_MS, initialDelay = RECONCILE_MS)
	public void reconcile() {
		Map<String, List<InterfaceInfo>> wanted = new HashMap<>();
		boolean any = false;
		for (ProgrammableControllerAdapter a : PlcAdapterManager.getPlcManager().adapters()) {
			if (a instanceof OpcUaAdapter) {
				any = true;
			}
		}
		if (!any && state.isEmpty()) {
			return;
		}
		for (InterfaceInfo i : interfaceService.searchInterface()) {
			if (i.isUse() && i.getInterfaceType() == InterfaceType.READ && i.getSettings() != null
					&& OpcUaDriver.ACCESS_SUBSCRIBE.equals(String.valueOf(i.getSettings().get("accessMode")))) {
				wanted.computeIfAbsent(i.getPlcName(), k -> new ArrayList<>()).add(i);
			}
		}
		List<String> seen = new ArrayList<>();
		for (ProgrammableControllerAdapter a : PlcAdapterManager.getPlcManager().adapters()) {
			if (!(a instanceof OpcUaAdapter ua) || !a.isLocal()) {
				continue;
			}
			seen.add(ua.getName());
			try {
				reconcile(ua, wanted.getOrDefault(ua.getName(), List.of()));
			} catch (RuntimeException e) {
				log.warn("OPC UA 구독을 맞추지 못했습니다: {} — {}", ua.getName(), e.getMessage());
			}
		}
		// 내려간 어댑터의 구독은 세션과 함께 사라졌다 — 기록만 지운다
		state.keySet().removeIf(name -> !seen.contains(name));
	}

	private void reconcile(OpcUaAdapter adapter, List<InterfaceInfo> wanted) {
		OpcUaClient client = adapter.client();
		AdapterSubs subs = state.computeIfAbsent(adapter.getName(), k -> new AdapterSubs());
		if (client == null) {
			subs.byInterface.clear();
			return;
		}
		if (subs.generation != adapter.sessionGeneration()) {
			// 새 세션 — 옛 구독은 서버에 없다
			subs.byInterface.clear();
			subs.generation = adapter.sessionGeneration();
		}
		Map<String, InterfaceInfo> byName = new HashMap<>();
		wanted.forEach(i -> byName.put(i.getName(), i));
		for (String name : new ArrayList<>(subs.byInterface.keySet())) {
			InterfaceInfo i = byName.get(name);
			if (i == null || !signature(i, adapter.settings()).equals(subs.byInterface.get(name).signature)) {
				delete(subs.byInterface.remove(name));
				log.info("OPC UA 구독 지움: {} / {}", adapter.getName(), name);
			}
		}
		for (InterfaceInfo i : wanted) {
			if (!subs.byInterface.containsKey(i.getName())) {
				Sub sub = create(client, i, adapter.settings());
				if (sub != null) {
					subs.byInterface.put(i.getName(), sub);
					log.info("OPC UA 구독 만듦: {} / {} ({}점)", adapter.getName(), i.getName(), i.getPoints().size());
				}
			}
		}
	}

	/** 구독을 다시 만들어야 하는 정의 — 점·인터페이스 settings·커넥터 구독 주기 */
	static String signature(InterfaceInfo i, Map<String, Object> connectorSettings) {
		return Objects.hash(i.getPoints(), i.getSettings(), connectorSettings.get("publishingIntervalMs"),
			connectorSettings.get("samplingIntervalMs")) + "";
	}

	private Sub create(OpcUaClient client, InterfaceInfo iface, Map<String, Object> connectorSettings) {
		Map<String, Object> s = new HashMap<>(connectorSettings);
		s.putAll(iface.getSettings());
		double publishing = number(s.get("publishingIntervalMs"), 1000);
		double sampling = number(s.get("samplingIntervalMs"), 500);
		Object deadband = s.get("deadband");
		try {
			OpcUaSubscription subscription = new OpcUaSubscription(client, publishing);
			Sub sub = new Sub(signature(iface, connectorSettings), subscription);
			List<OpcUaMonitoredItem> items = new ArrayList<>();
			for (PointInfo p : iface.getPoints()) {
				OpcUaMonitoredItem item = OpcUaMonitoredItem.newDataItem(OpcUaDriver.nodeId(p, client.getNamespaceTable()));
				item.setSamplingInterval(sampling);
				if (deadband instanceof Number d && d.doubleValue() > 0 && numeric(p.getType())) {
					item.setFilter(new DataChangeFilter(DataChangeTrigger.StatusValue, uint(DeadbandType.Absolute.getValue()), d.doubleValue()));
				}
				item.setUserObject(p);
				items.add(item);
			}
			subscription.setSubscriptionListener(new OpcUaSubscription.SubscriptionListener() {
				@Override
				public void onDataReceived(OpcUaSubscription subscription, List<OpcUaMonitoredItem> changed, List<DataValue> values) {
					onChange(iface, sub, changed, values);
				}
			});
			subscription.create();
			subscription.addMonitoredItems(items);
			subscription.synchronizeMonitoredItems();
			return sub;
		} catch (Exception e) {
			log.warn("OPC UA 구독을 만들지 못했습니다: {} — {}", iface.getName(), e.getMessage());
			return null;
		}
	}

	private static boolean numeric(PointType t) {
		return t != PointType.BOOL && !t.hasLength();
	}

	private static double number(Object v, double def) {
		return v instanceof Number n ? n.doubleValue() : def;
	}

	private void delete(Sub sub) {
		if (sub == null) {
			return;
		}
		try {
			sub.subscription.delete();
		} catch (Exception e) {
			log.debug("OPC UA 구독 지우기 실패(무시): {}", e.getMessage());
		}
	}

	/** 알림 — 최근 값을 고치고 인터페이스의 값 전체를 전달·감사 */
	void onChange(InterfaceInfo iface, Sub sub, List<OpcUaMonitoredItem> changed, List<DataValue> values) {
		PlcStatus bad = null;
		synchronized (sub) {
			for (int k = 0; k < changed.size() && k < values.size(); k++) {
				PointInfo p = (PointInfo) changed.get(k).getUserObject().orElse(null);
				DataValue dv = values.get(k);
				if (p == null) {
					continue;
				}
				if (dv.getStatusCode() != null && dv.getStatusCode().isBad()) {
					bad = bad != null ? bad : OpcUaDriver.status(dv.getStatusCode().getValue(), p.getName());
					sub.latest.remove(p.getName());
					continue;
				}
				try {
					sub.latest.put(p.getName(), OpcUaDriver.fromOpcUa(p, dv.getValue() != null ? dv.getValue().getValue() : null));
				} catch (IllegalArgumentException e) {
					bad = bad != null ? bad : OpcUaDriver.status(0x80740000L, p.getName());   // Bad_TypeMismatch
				}
			}
		}
		Map<String, Object> data;
		synchronized (sub) {
			data = PointScaling.toEngineering(iface.getPoints(), new LinkedHashMap<>(sub.latest));
		}
		TcMessageRequest request = new TcMessageRequest();
		request.setTransactionId(RequestMessage.generateTransactionId());
		request.setMessageId(RequestMessage.generateMessageId());
		request.setOpcode(iface.getName());
		request.setSender(SENDER);
		request.setReceiver("tc");
		TcMessageResponse response = new TcMessageResponse().fillReplyHeader(request);
		response.getData().putAll(data);
		response.setChanged(true);
		PlcStatus status = bad != null ? bad : PlcStatus.ok(OpcUaDriver.PROTOCOL);
		response.setPlcStatus(status);
		response.setResultCode((int) status.code());
		response.setResultDescription(status.ok() ? "SUCCESS" : "ERROR");
		if (!status.ok()) {
			response.fail(com.wow.be.tc.ToolControlException.PLC_ERROR, status.text() + " (" + iface.getName() + ")");
		}
		audit(response, data, status);
		try {
			resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, false), request, response);
		} catch (RuntimeException e) {
			log.warn("OPC UA 구독 결과를 전달하지 못했습니다: {} — {}", iface.getName(), e.getMessage());
		}
	}

	private void audit(TcMessageResponse response, Map<String, Object> data, PlcStatus status) {
		ExchangedMessageAuditInfo info = new ExchangedMessageAuditInfo();
		info.setTransactionId(response.getTransactionId());
		info.setMessageId(response.getMessageId());
		info.setName(response.getOpcode());
		info.setMessageType("RESPONSE");
		info.setTimestamp(new java.util.Date());
		info.setSender(SENDER);
		info.setReceiver("tc");
		info.setDataMap(data);
		info.setDescription(status.ok() ? "OPC UA 구독 알림" : "OPC UA 구독 알림 — " + status.text());
		info.setPrincipal(RequestPrincipals.SYSTEM);
		try {
			auditService.writeAudit(info);
		} catch (RuntimeException e) {
			log.warn("OPC UA 구독 감사를 남기지 못했습니다: {} — {}", response.getOpcode(), e.getMessage());
		}
	}
}
