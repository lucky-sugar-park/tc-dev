package com.wow.be.tc.delivery.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.wow.be.tc.delivery.DeliveryConfig;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo;
import com.wow.be.tc.delivery.DeliveryDefinitionService;
import com.wow.be.tc.delivery.DeliveryProperties;
import com.wow.be.tc.delivery.DeliveryTarget;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.delivery.ResultDeliveryChannel;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;
import com.wow.be.tc.security.RequestPrincipals;
import com.wow.be.tc.webhook.WebHook;
import com.wow.be.tc.webhook.WebHookManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 결과 전달. 대상마다 전달 전용 스레드 풀에서 보내고, 결과를 감사 로그(messageType=DELIVERY)에 남긴다.
 *
 * <p>대상 결정
 * <ul>
 *   <li>요청 인터페이스에 사용 중인 전달 정의(INTERFACE_DELIVERY)가 있으면 그 정의만 쓴다. CALLBACK_URL 정의가 있어야
 *       요청의 callback 주소로 보낸다.</li>
 *   <li>정의가 하나도 없으면 레거시 방식 — 요청의 callback 주소 + (reply=true 일 때) 응답 인터페이스의 WebHook 목록.</li>
 * </ul>
 * 정의의 조건(성공·실패, 값이 바뀔 때만)은 응답을 보고 {@link #deliver} 에서 거른다.
 *
 * <p>재시도와 실패 이력 테이블(outbox)은 아직 없다 — 실패하면 감사 로그와 경고 로그만 남는다.
 */
@Slf4j
@Service
public class DefaultResultDeliveryService implements ResultDeliveryService {

	private final Map<DeliveryType, ResultDeliveryChannel> channels = new EnumMap<>(DeliveryType.class);

	private final Executor executor;

	private final DeliveryProperties properties;

	private final InterfaceService interfaceService;

	private final ExchangedMessageAuditService auditService;

	private final DeliveryDefinitionService definitionService;

	public DefaultResultDeliveryService(
			List<ResultDeliveryChannel> channels,
			@Qualifier(DeliveryConfig.DELIVERY_TASK_EXECUTOR) Executor executor,
			DeliveryProperties properties,
			InterfaceService interfaceService,
			ExchangedMessageAuditService auditService,
			DeliveryDefinitionService definitionService) {
		channels.forEach(channel -> this.channels.put(channel.type(), channel));
		this.executor = executor;
		this.properties = properties;
		this.interfaceService = interfaceService;
		this.auditService = auditService;
		this.definitionService = definitionService;

		if (properties.getAllowedUrlPrefixes().isEmpty()) {
			log.warn("tc.delivery.allowed-url-prefixes 가 비어 있어 모든 주소로 결과를 보냅니다. 운영에서는 허용 주소를 지정하세요.");
		}
	}

	@Override
	public List<DeliveryTarget> targetsOf(TcMessageRequest request, boolean includeCallback) {
		List<DeliveryDefinitionInfo> definitions = definitionService.searchActiveByInterfaceName(request.getOpcode());
		if (!definitions.isEmpty()) {
			return targetsOf(definitions, request, includeCallback);
		}
		return legacyTargetsOf(request, includeCallback);
	}

	private List<DeliveryTarget> targetsOf(List<DeliveryDefinitionInfo> definitions, TcMessageRequest request, boolean includeCallback) {
		List<DeliveryTarget> targets = new ArrayList<>();
		for (DeliveryDefinitionInfo def : definitions) {
			switch (def.getDeliveryType()) {
				case CALLBACK_URL -> {
					if (includeCallback && request.wantsCallback()) {
						targets.add(new DeliveryTarget(DeliveryType.CALLBACK_URL, "callback", request.getPushUrl(),
							def.getDeliverOn(), def.isOnChangeOnly()));
					}
				}
				case WEBHOOK -> {
					WebHook webHook = WebHookManager.getManager().getWebHook(def.getTargetName());
					if (webHook == null) {
						log.warn("전달 정의의 WebHook 을 찾을 수 없어 건너뜁니다: {} ({})", def.getTargetName(), def.getId());
						continue;
					}
					targets.add(new DeliveryTarget(DeliveryType.WEBHOOK, webHook.getName(), webHook.getUrl(),
						def.getDeliverOn(), def.isOnChangeOnly()));
				}
				case DB_TABLE -> targets.add(new DeliveryTarget(DeliveryType.DB_TABLE, def.getTargetName(), null,
					def.getDeliverOn(), def.isOnChangeOnly(), def.getTableName()));
			}
		}
		if (includeCallback && request.wantsCallback()
				&& targets.stream().noneMatch(t -> t.type() == DeliveryType.CALLBACK_URL)
				&& definitions.stream().noneMatch(d -> d.getDeliveryType() == DeliveryType.CALLBACK_URL)) {
			log.warn("요청이 callback 주소를 줬지만 인터페이스에 CALLBACK_URL 전달 정의가 없어 보내지 않습니다: opcode={}, transactionId={}",
				request.getOpcode(), request.getTransactionId());
		}
		return targets;
	}

	/** 전달 정의가 없는 인터페이스 — 레거시와 같은 대상 */
	private List<DeliveryTarget> legacyTargetsOf(TcMessageRequest request, boolean includeCallback) {
		List<DeliveryTarget> targets = new ArrayList<>();
		if (includeCallback && request.wantsCallback()) {
			targets.add(DeliveryTarget.always(DeliveryType.CALLBACK_URL, "callback", request.getPushUrl()));
		}

		InterfaceInfo iInfo = interfaceService.findInterfaceByName(request.getOpcode());
		if (iInfo == null || !iInfo.isReply() || !StringUtils.hasText(iInfo.getReplyName())) {
			return targets;
		}
		InterfaceInfo rInfo = interfaceService.findInterfaceByName(iInfo.getReplyName());
		if (rInfo == null || rInfo.getWebHookNameList() == null) {
			return targets;
		}
		for (String webHookName : rInfo.getWebHookNameList()) {
			WebHook webHook = WebHookManager.getManager().getWebHook(webHookName);
			if (webHook == null) {
				log.warn("응답 인터페이스에 등록된 WebHook 을 찾을 수 없어 건너뜁니다: {} ({})", webHookName, rInfo.getName());
				continue;
			}
			targets.add(DeliveryTarget.always(DeliveryType.WEBHOOK, webHook.getName(), webHook.getUrl()));
		}
		return targets;
	}

	@Override
	public CompletableFuture<Void> deliver(List<DeliveryTarget> targets, TcMessageRequest request, TcMessageResponse response) {
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (DeliveryTarget target : targets) {
			if (!target.accepts(response)) {
				log.debug("전달 조건에 맞지 않아 보내지 않습니다: {} {}, success={}, changed={}",
					target.type(), target.name(), response.getSuccess(), response.isChanged());
				continue;
			}
			CompletableFuture<Void> future = new CompletableFuture<>();
			futures.add(future);
			try {
				executor.execute(() -> {
					try {
						deliverOne(target, request, response);
					} finally {
						future.complete(null);
					}
				});
			} catch (RejectedExecutionException e) {
				record(target, request, response, "요청이 많아 전달하지 못했습니다(전달 대기열 포화).");
				future.complete(null);
			}
		}
		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
	}

	private void deliverOne(DeliveryTarget target, TcMessageRequest request, TcMessageResponse response) {
		// 주소 허용 목록은 HTTP 전달에만 — DB_TABLE 은 관리자가 등록한 DB 연결만 쓸 수 있다
		if (target.type() != DeliveryType.DB_TABLE && !isAllowed(target.url())) {
			record(target, request, response, "허용되지 않은 주소라 보내지 않았습니다: " + target.url());
			return;
		}
		ResultDeliveryChannel channel = channels.get(target.type());
		if (channel == null) {
			record(target, request, response, "전달 채널이 없습니다: " + target.type());
			return;
		}
		try {
			channel.deliver(target, request, response);
			record(target, request, response, null);
		} catch (RuntimeException e) {
			record(target, request, response, "전달하지 못했습니다: " + e.getMessage());
		}
	}

	private boolean isAllowed(String url) {
		if (!StringUtils.hasText(url)) {
			return false;
		}
		List<String> prefixes = properties.getAllowedUrlPrefixes();
		return prefixes.isEmpty() || prefixes.stream().anyMatch(url::startsWith);
	}

	/** @param failure 실패 사유, 성공이면 null */
	private void record(DeliveryTarget target, TcMessageRequest request, TcMessageResponse response, String failure) {
		if (failure == null) {
			log.info("결과 전달 완료: {} {} → {}, opcode={}, transactionId={}",
				target.type(), target.name(), target.destination(), request.getOpcode(), request.getTransactionId());
		} else {
			log.warn("결과 전달 실패: {} {} → {}, opcode={}, transactionId={} — {}",
				target.type(), target.name(), target.destination(), request.getOpcode(), request.getTransactionId(), failure);
		}

		try {
			ExchangedMessageAuditInfo info = new ExchangedMessageAuditInfo();
			info.setTransactionId(response.getTransactionId());
			info.setMessageId(response.getMessageId());
			info.setName(response.getOpcode());
			info.setMessageType("DELIVERY");
			info.setTimestamp(new Date());
			info.setSender(response.getSender());
			info.setReceiver(target.type() + ":" + target.name() + ":" + target.destination());
			info.setDataMap(Map.of());
			info.setDescription(failure == null ? "OK" : failure);
			info.setPrincipal(request.getPrincipal() != null ? request.getPrincipal() : RequestPrincipals.SYSTEM);
			auditService.writeAudit(info);
		} catch (RuntimeException e) {
			log.warn("결과 전달 감사 로그를 남기지 못했습니다: transactionId={}", request.getTransactionId(), e);
		}
	}
}
