package com.wow.be.tc.delivery.impl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo.DeliverOn;
import com.wow.be.tc.delivery.DeliveryDefinitionService;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.webhook.WebHookManagementService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultDeliveryDefinitionService
		implements DeliveryDefinitionService, PersistentService<DeliveryDefinitionInfo, DeliveryDefinitionEntity> {

	/** 영문 소문자·숫자·밑줄·하이픈, 점(.)으로 계층 */
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");

	private final DeliveryDefinitionRepository repository;

	private final InterfaceService interfaceService;

	private final WebHookManagementService webHookService;

	@Override
	@Transactional
	public DeliveryDefinitionInfo register(DeliveryDefinitionInfo info) throws ToolControlException {
		validate(info);
		if (!StringUtils.hasText(info.getId())) {
			info.setId(generateId(info));
		}
		if (!ID_PATTERN.matcher(info.getId()).matches()) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"전달 정의 ID 는 영문 소문자·숫자와 점(.)으로 씁니다: " + info.getId());
		}
		if (repository.existsById(info.getId())) {
			throw ToolControlException.of("ERR_409", "같은 ID 의 전달 정의가 이미 있습니다: " + info.getId());
		}
		String target = info.getTargetName() == null ? "" : info.getTargetName();
		if (repository.existsByInterfaceNameAndDeliveryTypeAndTargetName(info.getInterfaceName(), info.getDeliveryType(), target)) {
			throw ToolControlException.of("ERR_409", "같은 대상의 전달 정의가 이미 있습니다: "
				+ info.getInterfaceName() + " → " + info.getDeliveryType() + (target.isEmpty() ? "" : ":" + target));
		}

		DeliveryDefinitionEntity entity = new DeliveryDefinitionEntity();
		entity.from(info);
		return repository.save(entity).info();
	}

	@Override
	@Transactional
	public DeliveryDefinitionInfo update(DeliveryDefinitionInfo info) throws ToolControlException {
		DeliveryDefinitionEntity entity = load(info.getId());
		// 인터페이스와 ID 는 그대로 — 바꾸려면 지우고 다시 등록한다
		info.setInterfaceName(entity.getInterfaceName());
		validate(info);

		String target = info.getTargetName() == null ? "" : info.getTargetName();
		boolean targetChanged = entity.getDeliveryType() != info.getDeliveryType() || !entity.getTargetName().equals(target);
		if (targetChanged && repository.existsByInterfaceNameAndDeliveryTypeAndTargetName(
				info.getInterfaceName(), info.getDeliveryType(), target)) {
			throw ToolControlException.of("ERR_409", "같은 대상의 전달 정의가 이미 있습니다: "
				+ info.getInterfaceName() + " → " + info.getDeliveryType() + (target.isEmpty() ? "" : ":" + target));
		}

		entity.from(info);
		return repository.save(entity).info();
	}

	@Override
	@Transactional
	public void delete(String id) throws ToolControlException {
		repository.delete(load(id));
	}

	@Override
	@Transactional(readOnly = true)
	public DeliveryDefinitionInfo findById(String id) throws ToolControlException {
		return load(id).info();
	}

	@Override
	@Transactional(readOnly = true)
	public List<DeliveryDefinitionInfo> searchByInterfaceName(String interfaceName) {
		return infos(repository.findByInterfaceNameOrderByIdAsc(interfaceName));
	}

	@Override
	@Transactional(readOnly = true)
	public List<DeliveryDefinitionInfo> searchActiveByInterfaceName(String interfaceName) {
		return infos(repository.findByInterfaceNameAndUseTrueOrderByIdAsc(interfaceName));
	}

	@Override
	@Transactional(readOnly = true)
	public List<DeliveryDefinitionInfo> searchAll() {
		return infos(repository.findAll());
	}

	private DeliveryDefinitionEntity load(String id) throws ToolControlException {
		if (!StringUtils.hasText(id)) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "전달 정의 ID 가 없습니다.");
		}
		return repository.findById(id).orElseThrow(
			() -> ToolControlException.of("ERR_404", "전달 정의가 없습니다: " + id));
	}

	private void validate(DeliveryDefinitionInfo info) throws ToolControlException {
		if (info == null || !StringUtils.hasText(info.getInterfaceName()) || info.getDeliveryType() == null) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "인터페이스 이름과 전달 유형은 꼭 넣어야 합니다.");
		}
		if (interfaceService.findInterfaceByName(info.getInterfaceName()) == null) {
			throw ToolControlException.of("ERR_404", "인터페이스가 없습니다: " + info.getInterfaceName());
		}
		if (info.getDeliverOn() == null) {
			info.setDeliverOn(DeliverOn.ALL);
		}

		switch (info.getDeliveryType()) {
			case WEBHOOK -> {
				if (!StringUtils.hasText(info.getTargetName())) {
					throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "WEBHOOK 전달에는 WebHook 이름이 필요합니다.");
				}
				if (webHookService.findWebHookByName(info.getTargetName()) == null) {
					throw ToolControlException.of("ERR_404", "WebHook 이 없습니다: " + info.getTargetName());
				}
			}
			case CALLBACK_URL -> info.setTargetName(null);   // 주소는 요청이 준다
		}
	}

	private static String generateId(DeliveryDefinitionInfo info) {
		String raw = info.getInterfaceName() + "." + info.getDeliveryType().name()
			+ (StringUtils.hasText(info.getTargetName()) ? "." + info.getTargetName() : "");
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}
}
