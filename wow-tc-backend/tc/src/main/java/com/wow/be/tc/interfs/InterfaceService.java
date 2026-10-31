package com.wow.be.tc.interfs;

import java.util.List;

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
	
}
