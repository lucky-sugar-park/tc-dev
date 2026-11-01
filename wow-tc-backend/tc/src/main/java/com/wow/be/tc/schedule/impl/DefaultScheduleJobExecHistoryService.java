package com.wow.be.tc.schedule.impl;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;

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

	@Override
	public Page<ScheduleJobExecHistoryInfo> query(String jobName, Date from, Date to, String result, int page, int size) {
		Specification<ScheduleJobExecHistoryEntity> spec = (root, q, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (StringUtils.hasText(jobName)) where.add(cb.equal(root.get("jobName"), jobName));
			if (from != null) where.add(cb.greaterThanOrEqualTo(root.get("executionTime"), from));
			if (to != null) where.add(cb.lessThan(root.get("executionTime"), to));
			if (StringUtils.hasText(result)) where.add(cb.equal(root.get("executionResult"), result));
			return cb.and(where.toArray(Predicate[]::new));
		};
		PageRequest req = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.DESC, "seqNo"));
		return scheduleJobExecHistoryRepository.findAll(spec, req).map(ScheduleJobExecHistoryEntity::info);
	}

	@Override
	public Map<String, ScheduleJobExecHistoryInfo> latestByJob() {
		Map<String, ScheduleJobExecHistoryInfo> latest = new HashMap<>();
		scheduleJobExecHistoryRepository.findLatestPerJob().forEach(e -> latest.put(e.getJobName(), e.info()));
		return latest;
	}

	@Override
	public Map<String, Long> failCountsSince(Date since) {
		Map<String, Long> counts = new HashMap<>();
		for (Object[] row : scheduleJobExecHistoryRepository.countFailuresSince(since)) {
			counts.put((String) row[0], ((Number) row[1]).longValue());
		}
		return counts;
	}
}
