package com.wow.be.tc.interfs;

import java.util.List;
import java.util.Map;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;

public interface InterfaceService {

	public abstract InterfaceInfo registerInterface(InterfaceInfo info);
	
	public abstract InterfaceInfo updateInterface(InterfaceInfo info);
	
	public abstract InterfaceInfo applyInterfaceUse(long id, boolean use) throws ToolControlException;
	
	public abstract void applyInterfaceUseByBatch(List<Long> ids, boolean use);
	
	public abstract List<InterfaceInfo> searchInterface();
	
	public abstract List<InterfaceInfo> searchInterface(PageInfo pageInfo);
	
	public abstract List<InterfaceInfo> searchInterface(Condition cond);
	
	public abstract List<InterfaceInfo> searchInterface(Condition cond, PageInfo pageInfo);
	
	public abstract InterfaceInfo findInterfaceById(long id) throws ToolControlException;
	
	public abstract InterfaceInfo findInterfaceByName(String name);
	
	public abstract void deleteInterface(long id);
	
	public abstract void deleteInterfaceByName(String name);
	
	public abstract void deleteInterfaceByBatch(List<Long> ids);
	
	public abstract List<HeaderTemplateInfo> searchHeaderTemplateByAll();

	/**
	 * RAW 인터페이스를 POINTS 로 바꾼다(docs/plc-protocol-design.md §8·§15). 알아볼 수 없으면 IllegalArgumentException(400, 사유).
	 *
	 * @param dryRun true 면 바꾸지 않고 만들 점·경고만 돌려준다
	 */
	public abstract PointsConversionInfo convertToPoints(long id, boolean dryRun) throws ToolControlException;

	public abstract PointsConversionInfo convertToPointsByName(String name, boolean dryRun) throws ToolControlException;

	/**
	 * 요청 미리보기(docs/interface-detail-design.md §7) — 이 정의(저장 전이어도 된다)로 PLC 에 보낼 요청들. 보내지 않는다.
	 * RAW·잘못된 점·없는 커넥터는 IllegalArgumentException(400).
	 *
	 * @param data 쓰기 값(선택) — 빠진 점은 형의 0 값
	 */
	public abstract RequestPlanInfo planRequests(InterfaceInfo info, Map<String, Object> data);
	
}
