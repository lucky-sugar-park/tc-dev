package com.wow.be.tc.plc.management.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.plc.management.PlcConnectionEventHistInfo;
import com.wow.be.tc.plc.management.PlcConnectionEventHistoryService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service("plcConnectionEventHistoryServiceBean")
@Slf4j
public class DefaultPlcConnectionEventHistoryService 
	implements PlcConnectionEventHistoryService, 
			   PersistentService<PlcConnectionEventHistInfo, PlcConnectionEventHistEntity> {
	
	@Autowired
	private PlcConnectionEventHistRepository plcConnectionEventHistRepository;

	@Override
	public void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo) {
		log.debug("");
		PlcConnectionEventHistEntity entity=new PlcConnectionEventHistEntity();
		entity.from(pcehInfo);
		plcConnectionEventHistRepository.save(entity);
	}

	@Override
	public PageResponse<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond) {
		log.debug("");
		PlcConnectionEventHistSpec specs=new PlcConnectionEventHistSpec();
		List<PlcConnectionEventHistEntity> plcConnectionEventHistEntityList=null;
		Specification<PlcConnectionEventHistEntity> specification=specs.createSpecification(cond);
		
		long count=plcConnectionEventHistRepository.count(specification);
		if(cond.getPageInfo()==null) {
			plcConnectionEventHistEntityList=plcConnectionEventHistRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			plcConnectionEventHistEntityList=plcConnectionEventHistRepository.findAll(specification, prequest).getContent();
		}
		
		List<PlcConnectionEventHistInfo> infoList=new ArrayList<>();
		plcConnectionEventHistEntityList.forEach(entity->{
			infoList.add(entity.info());
		});
		PageResponse<PlcConnectionEventHistInfo> response = new PageResponse<>();
		response.setPageInfo(cond.getPageInfo());
		response.setTotalRowsCount(count);
		response.setRows(infoList);
		return response;
	}
}
