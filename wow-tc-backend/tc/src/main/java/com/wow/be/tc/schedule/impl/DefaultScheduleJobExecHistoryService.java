package com.wow.be.tc.schedule.impl;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryQuery;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryService;
import com.wow.be.tc.utils.RangeQueries;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultScheduleJobExecHistoryService 
	implements ScheduleJobExecHistoryService, PersistentService<ScheduleJobExecHistoryInfo, ScheduleJobExecHistoryEntity> {

	@Autowired
	private ScheduleJobExecHistoryRepository scheduleJobExecHistoryRepository;

	@Autowired
	private ScheduleJobRepository scheduleJobRepository;

	@Autowired
	private ScheduleJobExecHistoryRangeableRepository rangeRepository;

	@Autowired
	private RangeQueries rangeQueries;

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
	public Page<ScheduleJobExecHistoryInfo> query(ScheduleJobExecHistoryQuery q) {
		int page = q.page() != null ? Math.max(q.page(), 0) : 0;
		int size = q.size() != null ? Math.min(Math.max(q.size(), 1), 500) : 50;
		PageRequest req = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "seqNo"));
		return scheduleJobExecHistoryRepository.findAll(specOf(q), req).map(ScheduleJobExecHistoryEntity::info);
	}

	@Override
	@Transactional(readOnly = true)
	public RangeQueries.Result<ScheduleJobExecHistoryInfo> range(ScheduleJobExecHistoryQuery q, RangeQueries.Params range) {
		return rangeQueries.byKey(ScheduleJobExecHistoryEntity.class, specOf(q), "seqNo", range, Sort.by(Sort.Direction.DESC, "seqNo"),
			scheduleJobExecHistoryRepository, rangeRepository, ScheduleJobExecHistoryEntity::info);
	}

	private Specification<ScheduleJobExecHistoryEntity> specOf(ScheduleJobExecHistoryQuery q) {
		// Job 이름·설명·상태 조건 → 그 Job 들의 이름(이력에는 이름만 있다). 상태를 고르면 지금 있는 Job 의 이력만,
		// 글만 주면 지운 Job 의 이력도 이름으로 찾는다
		String search = StringUtils.hasText(q.jobSearch()) ? q.jobSearch().trim().toLowerCase() : null;
		String state = StringUtils.hasText(q.jobState()) ? q.jobState().trim() : null;
		Set<String> jobNames = null;
		if (search != null || state != null) {
			jobNames = new HashSet<>();
			for (ScheduleJobEntity job : scheduleJobRepository.findAll()) {
				boolean text = search == null || contains(job.getName(), search) || contains(job.getDescription(), search);
				if (text && (state == null || state.equals(job.getTriggerState()))) jobNames.add(job.getName());
			}
		}
		Set<String> names = jobNames;
		Specification<ScheduleJobExecHistoryEntity> spec = (root, cq, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (StringUtils.hasText(q.jobName())) where.add(cb.equal(root.get("jobName"), q.jobName()));
			if (names != null) {
				Predicate inJobs = names.isEmpty() ? cb.disjunction() : root.get("jobName").in(names);
				where.add(state == null ? cb.or(inJobs, cb.like(cb.lower(root.get("jobName")), "%" + likeEscape(search) + "%", '!')) : inJobs);
			}
			if (q.from() != null) where.add(cb.greaterThanOrEqualTo(root.get("executionTime"), q.from()));
			if (q.to() != null) where.add(cb.lessThan(root.get("executionTime"), q.to()));
			if (StringUtils.hasText(q.result())) where.add(cb.equal(root.get("executionResult"), q.result()));
			return cb.and(where.toArray(Predicate[]::new));
		};
		return spec;
	}

	private static boolean contains(String value, String lowerNeedle) {
		return value != null && value.toLowerCase().contains(lowerNeedle);
	}

	/** LIKE 의 % _ 를 글자 그대로 — 탈출 문자는 '!' */
	private static String likeEscape(String s) {
		return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
