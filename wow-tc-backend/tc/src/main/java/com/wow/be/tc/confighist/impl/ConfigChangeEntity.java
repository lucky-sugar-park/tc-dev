package com.wow.be.tc.confighist.impl;

import java.util.Date;

import com.wow.be.tc.confighist.ConfigChangeInfo;
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.ActorType;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 설정 변경 이력 (CONFIG_CHANGE_HIST). 이력 자체는 바꾸지 않으므로 PersistentEntity(생성·수정자 칼럼)를 쓰지 않는다 —
 * 행위자는 ACTOR_* 칼럼이 나타낸다.
 */
@Entity
@Table(name = "CONFIG_CHANGE_HIST", indexes = {
	@Index(name = "IX_CONFIG_CHANGE_TARGET", columnList = "TARGET_TYPE, TARGET_ID, CHANGED_AT"),
	@Index(name = "IX_CONFIG_CHANGE_ACTOR", columnList = "ACTOR_ID, CHANGED_AT"),
	@Index(name = "IX_CONFIG_CHANGE_AT", columnList = "CHANGED_AT")
})
@Data
public class ConfigChangeEntity {

	@Id
	@Column(name = "ID", length = 36)
	private String id;

	@Column(name = "CHANGED_AT", nullable = false)
	private Date changedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "ACTOR_TYPE", nullable = false, length = 20)
	private ActorType actorType;

	@Column(name = "ACTOR_ID", nullable = false, length = 200)
	private String actorId;

	@Column(name = "ACTOR_IP", length = 64)
	private String actorIp;

	@Enumerated(EnumType.STRING)
	@Column(name = "TARGET_TYPE", nullable = false, length = 30)
	private TargetType targetType;

	@Column(name = "TARGET_ID", nullable = false, length = 200)
	private String targetId;

	@Column(name = "TARGET_NAME", length = 200)
	private String targetName;

	@Enumerated(EnumType.STRING)
	@Column(name = "ACTION", nullable = false, length = 20)
	private Action action;

	@Column(name = "DETAIL", length = 100)
	private String detail;

	@Lob
	@Column(name = "BEFORE_JSON", columnDefinition = "LONGTEXT")
	private String beforeJson;

	@Lob
	@Column(name = "AFTER_JSON", columnDefinition = "LONGTEXT")
	private String afterJson;

	@Column(name = "TRANSACTION_ID", length = 100)
	private String transactionId;

	public ConfigChangeInfo info() {
		ConfigChangeInfo info = new ConfigChangeInfo();
		info.setId(id);
		info.setChangedAt(changedAt);
		info.setActorType(actorType);
		info.setActorId(actorId);
		info.setActorIp(actorIp);
		info.setTargetType(targetType);
		info.setTargetId(targetId);
		info.setTargetName(targetName);
		info.setAction(action);
		info.setDetail(detail);
		info.setBeforeJson(beforeJson);
		info.setAfterJson(afterJson);
		info.setTransactionId(transactionId);
		return info;
	}
}
