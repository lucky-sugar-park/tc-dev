package com.wow.be.tc.schedule.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultScheduleJobExecHistoryService 
	implements ScheduleJobExecHistoryService, PersistentService<ScheduleJobExecHistoryInfo, ScheduleJobExecHistoryEntity> {

	@Autowired
	private ScheduleJobExecHistoryRepository scheduleJobExecHistoryRepository;

	@Override
	public void saveScheduleJobExecHistory(ScheduleJobExecHistoryInfo scheduleJobExecHistoryInfo) {
		log.debug("");
		ScheduleJobExecHistoryEntity entity=new ScheduleJobExecHistoryEntity();
		entity.from(scheduleJobExecHistoryInfo);
		scheduleJobExecHistoryRepository.save(entity);
	}

	@Override
	public void deleteScheduleJobExecHistory(long seqNo) {
		log.debug("");
		scheduleJobExecHistoryRepository.deleteById(seqNo);
	}

	@Override
	public void deleteScheduleJobExecHistory(String scheduleJobName) {
		log.debug("");
		scheduleJobExecHistoryRepository.deleteByScheduleJobName(scheduleJobName);
	}
	
	public void deleteScheduleJobExecHistoryBetweenExecTime(Date from, Date to) {
		log.debug("");
		scheduleJobExecHistoryRepository.deleteScheduleJobExecHistoryBetweenExecTime(from, to);
	}

	@Override
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory() {
		log.debug("");
		return infos(scheduleJobExecHistoryRepository.findAll());
	}

	@Override
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(PageInfo pageInfo) {
		log.debug("");
		Sort.Direction dir=pageInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
		PageRequest prequest=PageRequest.of(pageInfo.getPage(), pageInfo.getSize(), dir, pageInfo.getSortBy());
		return infos(scheduleJobExecHistoryRepository.findAll(prequest).getContent());
	}

	@Override
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond) {
		log.debug("");
		ScheduleJobExecHistorySpec specs=new ScheduleJobExecHistorySpec();
		List<ScheduleJobExecHistoryEntity> jobExecHistEntityList=null;
		Specification<ScheduleJobExecHistoryEntity> specification=specs.createSpecification(cond);

		if(cond.getPageInfo()==null) {
			jobExecHistEntityList=scheduleJobExecHistoryRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			jobExecHistEntityList=scheduleJobExecHistoryRepository.findAll(specification, prequest).getContent();
		}

		return infos(jobExecHistEntityList);
	}

	@Override
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond, PageInfo pageInfo) {
		log.debug("");
		cond.setPageInfo(pageInfo);
		return searchScheduleJobExecHistory(cond);
	}
}
