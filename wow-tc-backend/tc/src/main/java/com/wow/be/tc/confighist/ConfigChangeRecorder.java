package com.wow.be.tc.confighist;

import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;

/**
 * 설정 변경을 이력에 남긴다 (docs/auth-and-audit-design.md §5.2).
 *
 * <ul>
 *   <li>관리 서비스의 저장·삭제·제어 지점에서 부른다(컨트롤러가 아니라) — 일괄 처리·내부 호출로 바뀐 것도 빠지지 않게.</li>
 *   <li>부른 쪽의 트랜잭션에 함께 들어간다 — 변경과 이력이 같이 커밋되거나 같이 롤백된다.</li>
 *   <li>행위자는 지금 스레드의 인증(사용자·API 클라이언트), 없으면 SYSTEM 이다. IP 는 지금 HTTP 요청에서 읽는다.</li>
 *   <li>변경 전·후 값은 JSON 으로 남기되 비밀값(비밀번호·키 해시 등)은 가린다. UPDATE 인데 전·후가 같으면 남기지 않는다.</li>
 * </ul>
 */
public interface ConfigChangeRecorder {

	void record(TargetType targetType, String targetId, String targetName, Action action, String detail,
			Object before, Object after);

	default void created(TargetType targetType, String targetId, String targetName, Object after) {
		record(targetType, targetId, targetName, Action.CREATE, null, null, after);
	}

	default void updated(TargetType targetType, String targetId, String targetName, Object before, Object after) {
		record(targetType, targetId, targetName, Action.UPDATE, null, before, after);
	}

	default void deleted(TargetType targetType, String targetId, String targetName, Object before) {
		record(targetType, targetId, targetName, Action.DELETE, null, before, null);
	}

	default void controlled(TargetType targetType, String targetId, String targetName, String detail) {
		record(targetType, targetId, targetName, Action.CONTROL, detail, null, null);
	}
}
