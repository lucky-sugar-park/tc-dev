package com.wow.be.tc.delivery;

import java.util.List;

import com.wow.be.tc.ToolControlException;

/**
 * 인터페이스별 결과 전달 정의(INTERFACE_DELIVERY) 관리.
 */
public interface DeliveryDefinitionService {

	/**
	 * 등록한다. ID 가 비어 있으면 만든다.
	 *
	 * @throws ToolControlException 인터페이스·WebHook 이 없거나(ERR_404), 같은 정의가 있거나(ERR_409), 값이 잘못되었을 때(ERR_400)
	 */
	DeliveryDefinitionInfo register(DeliveryDefinitionInfo info) throws ToolControlException;

	/** 수정한다. 인터페이스 이름과 ID 는 바꿀 수 없다. */
	DeliveryDefinitionInfo update(DeliveryDefinitionInfo info) throws ToolControlException;

	void delete(String id) throws ToolControlException;

	DeliveryDefinitionInfo findById(String id) throws ToolControlException;

	/** 인터페이스의 정의 전부 (사용 여부 무관) */
	List<DeliveryDefinitionInfo> searchByInterfaceName(String interfaceName);

	/** 인터페이스의 사용 중인 정의 — 결과 전달에서 쓴다 */
	List<DeliveryDefinitionInfo> searchActiveByInterfaceName(String interfaceName);

	List<DeliveryDefinitionInfo> searchAll();
}
