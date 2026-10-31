package com.wow.be.tc.confighist;

import java.util.Date;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 설정 변경 이력 한 건 (CONFIG_CHANGE_HIST). 누가·언제·어디서·무엇을·어떻게 바꿨는지와 변경 전·후 값을 남긴다.
 *
 * <p>docs/auth-and-audit-design.md §5. 사용자 자신의 로그인·로그아웃은 USER_HIST 에 남는다.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ConfigChangeInfo extends AbstractInfo {

	private String id;

	private Date changedAt;

	private ActorType actorType;

	private String actorId;

	private String actorIp;

	private TargetType targetType;

	private String targetId;

	/** 대상 이름. 삭제된 뒤에도 무엇이었는지 읽을 수 있게 남긴다 */
	private String targetName;

	private Action action;

	/** CONTROL 의 세부 동작(publish, release, pause, resume, reload, key-issue, key-revoke 등) */
	private String detail;

	private String beforeJson;

	private String afterJson;

	/** 같은 요청에서 생긴 여러 건을 묶는다(일괄 삭제 등) */
	private String transactionId;

	public enum ActorType {
		USER, API_CLIENT, SYSTEM
	}

	public enum TargetType {
		PLC_CONNECTOR, INTERFACE, WEBHOOK, DELIVERY, DB_CONNECTION, SCHEDULE_JOB, USER, API_CLIENT, API_KEY, OPTION, I18N, COMMON_CODE
	}

	public enum Action {
		CREATE, UPDATE, DELETE, ENABLE, DISABLE, CONTROL
	}

	@Override
	public String toPlainText() {
		return changedAt + " " + actorType + ":" + actorId + " " + action + " " + targetType + ":" + targetId
			+ (detail != null ? " (" + detail + ")" : "");
	}
}
