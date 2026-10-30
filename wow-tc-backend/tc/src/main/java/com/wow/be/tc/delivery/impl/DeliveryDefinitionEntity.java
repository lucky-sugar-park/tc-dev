package com.wow.be.tc.delivery.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo.DeliverOn;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 인터페이스별 결과 전달 정의. 인터페이스는 연관관계 대신 이름(문자열)으로 참조한다.
 */
@Entity
@Table(
	name = "INTERFACE_DELIVERY",
	indexes = @Index(name = "IX_INTERFACE_DELIVERY_IF", columnList = "INTERFACE_NAME"),
	uniqueConstraints = @UniqueConstraint(name = "UK_INTERFACE_DELIVERY_TARGET",
		columnNames = { "INTERFACE_NAME", "DELIVERY_TYPE", "TARGET_NAME" })
)
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class DeliveryDefinitionEntity extends PersistentEntity<DeliveryDefinitionInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "ID", length = 200)
	private String id;

	@Column(name = "INTERFACE_NAME", nullable = false, length = 200)
	private String interfaceName;

	@Enumerated(EnumType.STRING)
	@Column(name = "DELIVERY_TYPE", nullable = false, length = 30)
	private DeliveryType deliveryType;

	/** CALLBACK_URL 은 빈 문자열 — 유니크 제약에서 NULL 이 서로 다르게 취급되지 않게 한다 */
	@Column(name = "TARGET_NAME", nullable = false, length = 200)
	private String targetName;

	@Enumerated(EnumType.STRING)
	@Column(name = "DELIVER_ON", nullable = false, length = 20)
	private DeliverOn deliverOn;

	@Column(name = "ON_CHANGE_ONLY", nullable = false)
	private boolean onChangeOnly;

	@Column(name = "IS_USE", nullable = false)
	private boolean use;

	@Column(name = "DESCRIPTION", length = 1024)
	private String description;

	@Override
	public DeliveryDefinitionInfo info() {
		DeliveryDefinitionInfo info = new DeliveryDefinitionInfo();
		info.setId(id);
		info.setInterfaceName(interfaceName);
		info.setDeliveryType(deliveryType);
		info.setTargetName(targetName == null || targetName.isEmpty() ? null : targetName);
		info.setDeliverOn(deliverOn);
		info.setOnChangeOnly(onChangeOnly);
		info.setUse(use);
		info.setDescription(description);
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	@Override
	public void from(DeliveryDefinitionInfo info) {
		this.id = info.getId();
		this.interfaceName = info.getInterfaceName();
		this.deliveryType = info.getDeliveryType();
		this.targetName = info.getTargetName() == null ? "" : info.getTargetName();
		this.deliverOn = info.getDeliverOn() == null ? DeliverOn.ALL : info.getDeliverOn();
		this.onChangeOnly = info.isOnChangeOnly();
		this.use = info.isUse();
		this.description = info.getDescription();
	}

	@Override
	public String toPlainText() {
		return id;
	}
}
