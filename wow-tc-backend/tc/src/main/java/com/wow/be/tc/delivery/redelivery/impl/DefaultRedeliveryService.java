package com.wow.be.tc.delivery.redelivery.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.dbconn.DbConnectionService;
import com.wow.be.tc.delivery.DeliveryConfig;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo.DeliverOn;
import com.wow.be.tc.delivery.DeliveryProperties;
import com.wow.be.tc.delivery.DeliveryTarget;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.delivery.impl.DefaultResultDeliveryService;
import com.wow.be.tc.delivery.redelivery.AuditResponses;
import com.wow.be.tc.delivery.redelivery.AuditResponses.Receiver;
import com.wow.be.tc.delivery.redelivery.RedeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;
import com.wow.be.tc.security.RequestPrincipals;
import com.wow.be.tc.webhook.WebHook;
import com.wow.be.tc.webhook.WebHookManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DefaultRedeliveryService implements RedeliveryService {

	private final ExchangedMessageAuditService auditService;

	private final ResultDeliveryService deliveryService;

	private final DbConnectionService dbConnectionService;

	private final DeliveryProperties properties;

	private final Executor executor;

	/** 원본|대상 → 이 시각까지 같은 재전송을 건너뛴다. 인스턴스마다 따로라 중복 클릭만 막는다(R11) */
	private final Map<String, Long> recent = new ConcurrentHashMap<>();

	public DefaultRedeliveryService(
			ExchangedMessageAuditService auditService,
			ResultDeliveryService deliveryService,
			DbConnectionService dbConnectionService,
			DeliveryProperties properties,
			@Qualifier(DeliveryConfig.DELIVERY_TASK_EXECUTOR) Executor executor) {
		this.auditService = auditService;
		this.deliveryService = deliveryService;
		this.dbConnectionService = dbConnectionService;
		this.properties = properties;
		this.executor = executor;
	}

	@Override
	public Result redeliverDeliveries(List<Long> auditIds) throws ToolControlException {
		List<Long> ids = distinct(auditIds);
		checkBatch(ids.size());

		List<Rejected> rejected = new ArrayList<>();
		List<Item> items = new ArrayList<>();
		Map<Long, ExchangedMessageAuditInfo> found = load(ids);
		for (Long id : ids) {
			ExchangedMessageAuditInfo info = found.get(id);
			if (info == null) {
				rejected.add(new Rejected(id, null, "감사 기록이 없습니다."));
				continue;
			}
			if (!AuditResponses.DELIVERY.equals(info.getMessageType())) {
				rejected.add(new Rejected(id, null, "전달(DELIVERY) 기록이 아닙니다: " + info.getMessageType()));
				continue;
			}
			Receiver receiver = AuditResponses.parseReceiver(info.getReceiver());
			if (receiver == null) {
				rejected.add(new Rejected(id, info.getReceiver(), "전달 대상을 읽을 수 없습니다."));
				continue;
			}
			DeliveryTarget target;
			try {
				target = currentTarget(receiver);
			} catch (ToolControlException e) {
				rejected.add(new Rejected(id, info.getReceiver(), e.getMessage()));
				continue;
			}
			TcMessageResponse response = responseOf(info);
			if (response == null) {
				rejected.add(new Rejected(id, info.getReceiver(), "원본 응답 기록을 찾을 수 없습니다."));
				continue;
			}
			items.add(new Item(id, target.redeliveryOf(id), response));
		}
		return submit(items, rejected);
	}

	@Override
	public Result redeliverResponses(List<Long> auditIds, List<TargetRef> targets) throws ToolControlException {
		List<Long> ids = distinct(auditIds);
		if (targets == null || targets.isEmpty()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "보낼 대상(targets)이 없습니다.");
		}
		checkBatch(ids.size() * targets.size());

		List<Rejected> rejected = new ArrayList<>();
		List<Item> items = new ArrayList<>();
		Map<Long, ExchangedMessageAuditInfo> found = load(ids);
		Map<String, List<DeliveryTarget>> candidatesByInterface = new HashMap<>();
		for (Long id : ids) {
			ExchangedMessageAuditInfo info = found.get(id);
			if (info == null) {
				rejected.add(new Rejected(id, null, "감사 기록이 없습니다."));
				continue;
			}
			TcMessageResponse response;
			if (AuditResponses.RESPONSE.equals(info.getMessageType())) {
				response = AuditResponses.fromResponseAudit(info);
			} else if (AuditResponses.ERROR.equals(info.getMessageType())) {
				response = AuditResponses.fromErrorAudit(info);
			} else {
				rejected.add(new Rejected(id, null, "응답(RESPONSE)·오류(ERROR) 기록이 아닙니다: " + info.getMessageType()));
				continue;
			}
			List<DeliveryTarget> candidates = candidatesByInterface.computeIfAbsent(info.getName(), this::candidatesOf);
			for (TargetRef ref : targets) {
				if (ref == null || ref.deliveryType() == null) {
					rejected.add(new Rejected(id, null, "대상 유형(deliveryType)이 없습니다."));
					continue;
				}
				if (ref.deliveryType() == DeliveryType.CALLBACK_URL) {
					rejected.add(new Rejected(id, ref.label(), "콜백 주소는 기록되지 않아 응답 기록으로 다시 보낼 수 없습니다. 전달 기록으로 보내세요."));
					continue;
				}
				DeliveryTarget target = candidates.stream()
					.filter(c -> c.sameAs(ref.deliveryType(), ref.targetName(), ref.tableName()))
					.findFirst().orElse(null);
				if (target == null) {
					rejected.add(new Rejected(id, ref.label(), "이 인터페이스(" + info.getName() + ")의 전달 대상이 아닙니다."));
					continue;
				}
				String unusable = unusable(target);
				if (unusable != null) {
					rejected.add(new Rejected(id, ref.label(), unusable));
					continue;
				}
				items.add(new Item(id, target.redeliveryOf(id), response));
			}
		}
		return submit(items, rejected);
	}

	/** 인터페이스의 지금 전달 대상(정의가 있으면 정의, 없으면 레거시 WebHook 목록). callback 은 뺀다 */
	private List<DeliveryTarget> candidatesOf(String interfaceName) {
		TcMessageRequest probe = new TcMessageRequest();
		probe.setOpcode(interfaceName);
		return deliveryService.targetsOf(probe, false);
	}

	/** 기록된 대상을 지금 설정으로 — WebHook 은 지금 주소로 */
	private DeliveryTarget currentTarget(Receiver receiver) throws ToolControlException {
		DeliveryTarget target = switch (receiver.type()) {
			case CALLBACK_URL -> DeliveryTarget.always(DeliveryType.CALLBACK_URL, receiver.name(), receiver.destination());
			case WEBHOOK -> {
				WebHook webHook = WebHookManager.getManager().getWebHook(receiver.name());
				if (webHook == null) {
					throw ToolControlException.of(ToolControlException.NOT_FOUND, "WebHook 이 없습니다: " + receiver.name());
				}
				yield DeliveryTarget.always(DeliveryType.WEBHOOK, webHook.getName(), webHook.getUrl());
			}
			case DB_TABLE -> new DeliveryTarget(DeliveryType.DB_TABLE, receiver.name(), null, DeliverOn.ALL, false, receiver.destination());
		};
		String unusable = unusable(target);
		if (unusable != null) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, unusable);
		}
		return target;
	}

	/** 지금 보낼 수 없는 대상이면 사유 */
	private String unusable(DeliveryTarget target) {
		if (target.type() == DeliveryType.DB_TABLE) {
			try {
				dbConnectionService.dataSource(target.name());
				return null;
			} catch (ToolControlException e) {
				return e.getMessage();
			}
		}
		return deliveryService.allowsUrl(target.url()) ? null : "허용되지 않은 주소입니다: " + target.url();
	}

	/** 전달 기록이 보낸 응답. 보완 전 기록이면 원본 RESPONSE(MSG_ID 같음) 또는 ERROR(거래 ID·인터페이스)에서 만든다 */
	private TcMessageResponse responseOf(ExchangedMessageAuditInfo delivery) {
		TcMessageResponse response = AuditResponses.fromDeliveryBody(delivery.getDataMap(), DefaultResultDeliveryService.AUDIT_RESPONSE);
		if (response != null) {
			return response;
		}
		if (delivery.getMessageId() != null) {
			List<ExchangedMessageAuditInfo> responses = auditService.searchByMessageId(delivery.getMessageId(), AuditResponses.RESPONSE);
			if (!responses.isEmpty()) {
				return AuditResponses.fromResponseAudit(responses.get(0));
			}
		}
		if (delivery.getTransactionId() == null) {
			return null;
		}
		// 같은 거래 ID 를 다시 쓴 호출자가 있을 수 있어 전달 시각 이전의 가장 가까운 오류를 고른다
		long deliveredAt = delivery.getTimestamp() != null ? delivery.getTimestamp().getTime() : Long.MAX_VALUE;
		return auditService.searchByTransactionId(delivery.getTransactionId(), delivery.getName(), AuditResponses.ERROR).stream()
			.filter(e -> e.getTimestamp() != null && e.getTimestamp().getTime() <= deliveredAt)
			.max(Comparator.comparing(ExchangedMessageAuditInfo::getTimestamp))
			.map(AuditResponses::fromErrorAudit)
			.orElse(null);
	}

	/** 대상마다 원래 시각 순으로 하나씩 보낸다(R10). 대상끼리는 따로 돈다 */
	private Result submit(List<Item> items, List<Rejected> rejected) {
		String principal = RequestPrincipals.current();
		long now = System.currentTimeMillis();
		recent.values().removeIf(until -> until <= now);

		Map<String, List<Item>> byTarget = new LinkedHashMap<>();
		for (Item item : items) {
			String key = item.sourceId() + "|" + targetKey(item.target());
			Long until = recent.putIfAbsent(key, now + properties.getRedeliveryDuplicateWindow().toMillis());
			if (until != null && until > now) {
				rejected.add(new Rejected(item.sourceId(), targetKey(item.target()), "같은 재전송을 방금 요청했습니다. 잠시 뒤에 다시 하세요."));
				continue;
			}
			byTarget.computeIfAbsent(targetKey(item.target()), k -> new ArrayList<>()).add(item);
		}

		int accepted = 0;
		for (Map.Entry<String, List<Item>> group : byTarget.entrySet()) {
			List<Item> ordered = new ArrayList<>(group.getValue());
			ordered.sort(Comparator.comparing(i -> i.response().getTimestamp(), Comparator.nullsLast(Comparator.naturalOrder())));
			try {
				executor.execute(() -> ordered.forEach(item -> send(item, principal)));
				accepted += ordered.size();
			} catch (RejectedExecutionException e) {
				ordered.forEach(item -> {
					recent.remove(item.sourceId() + "|" + group.getKey());
					rejected.add(new Rejected(item.sourceId(), group.getKey(), "전달 대기열이 가득 찼습니다. 잠시 뒤에 다시 하세요."));
				});
			}
		}
		log.info("결과 재전송 요청: 보냄 {}건, 거절 {}건, 요청자 {}", accepted, rejected.size(), principal);
		return new Result(accepted, rejected);
	}

	private void send(Item item, String principal) {
		TcMessageResponse response = item.response();
		TcMessageRequest request = new TcMessageRequest();
		request.setTransactionId(response.getTransactionId());
		request.setMessageId(response.getMessageId());
		request.setOpcode(response.getOpcode());
		request.setPrincipal(principal);
		try {
			deliveryService.deliverNow(item.target(), request, response);
		} catch (RuntimeException e) {
			// deliverNow 는 실패를 감사에 남기고 던지지 않는다 — 여기는 남은 건을 계속 보내기 위한 방어
			log.warn("재전송하지 못했습니다: 원본 #{} → {}", item.sourceId(), targetKey(item.target()), e);
		}
	}

	private static String targetKey(DeliveryTarget target) {
		return target.type() + ":" + target.name() + ":" + target.destination();
	}

	private Map<Long, ExchangedMessageAuditInfo> load(List<Long> ids) {
		Map<Long, ExchangedMessageAuditInfo> found = new HashMap<>();
		auditService.findMessageAuditsByIds(ids).forEach(info -> found.put(info.getId(), info));
		return found;
	}

	private void checkBatch(int count) throws ToolControlException {
		if (count == 0) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "다시 보낼 감사 기록(auditIds)이 없습니다.");
		}
		if (count > properties.getRedeliveryMaxBatch()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"한 번에 " + properties.getRedeliveryMaxBatch() + "건까지 다시 보낼 수 있습니다(요청 " + count + "건, 대상 × 기록).");
		}
	}

	private static List<Long> distinct(List<Long> ids) {
		if (ids == null) {
			return List.of();
		}
		return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(id -> id != null).toList()));
	}

	/** @param sourceId 원본 감사 ID */
	private record Item(long sourceId, DeliveryTarget target, TcMessageResponse response) {
	}
}
