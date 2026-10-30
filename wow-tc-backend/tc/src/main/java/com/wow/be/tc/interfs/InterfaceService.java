package com.wow.be.tc.interfs;

import java.util.List;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;

public interface InterfaceService {

	public abstract InterfaceInfo registerInterface(InterfaceInfo info);
	
	public abstract InterfaceInfo updateInterface(InterfaceInfo info);
	
	public abstract InterfaceInfo applyInterfaceUse(long id, boolean use);
	
	public abstract void applyInterfaceUseByBatch(List<Long> ids, boolean use);
	
	public abstract List<InterfaceInfo> searchInterface();
	
	public abstract List<InterfaceInfo> searchInterface(PageInfo pageInfo);
	
	public abstract List<InterfaceInfo> searchInterface(Condition cond);
	
	public abstract List<InterfaceInfo> searchInterface(Condition cond, PageInfo pageInfo);
	
	public abstract InterfaceInfo findInterfaceById(long id);
	
	public abstract InterfaceInfo findInterfaceByName(String name);
	
	public abstract void deleteInterface(long id);
	
	public abstract void deleteInterfaceByName(String name);
	
	public abstract void deleteInterfaceByBatch(List<Long> ids);
	
	public abstract List<HeaderTemplateInfo> searchHeaderTemplateByAll();
	
}
