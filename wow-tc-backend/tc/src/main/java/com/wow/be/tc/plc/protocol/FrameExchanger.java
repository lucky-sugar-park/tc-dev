package com.wow.be.tc.plc.protocol;

import com.wow.be.tc.ToolControlException;

/**
 * 요청 프레임 하나를 PLC 와 주고받는다 — 로컬 어댑터는 자기 채널로, 원격 어댑터는 주인 인스턴스로.
 * 드라이버는 이것만 알고 연결을 모른다.
 */
@FunctionalInterface
public interface FrameExchanger {

	byte[] exchange(String reqId, byte[] frame) throws ToolControlException;
}
