package com.wow.be.tc.schedule.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;
import com.wow.be.tc.schedule.ScheduleJobDetailService;
import com.wow.be.tc.schedule.ScheduleJobInfo;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultScheduleJobDetailService implements ScheduleJobDetailService, PersistentService<ScheduleJobDetailInfo, ScheduleJobDetailEntity> {

	@Autowired
	private ScheduleJobDetailRepository scheduleJobDetailRepository;
	
	@Override
	@Transactional
	public void createScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail) {
		log.debug("");

		ScheduleJobDetailEntity entity=new ScheduleJobDetailEntity();
		entity.from(scheduleJobDetail);
		scheduleJobDetailRepository.save(entity);
	}

	@Override
	public List<ScheduleJobDetailInfo> searchScheduleJobDetails(ScheduleJobInfo scheduleJob) {
		log.debug("");
		return this.searchScheduleJobDetails(scheduleJob.getId());
	}

	@Override
	public List<ScheduleJobDetailInfo> searchScheduleJobDetails(String scheduleJobId) {
		log.debug("");
		return infos(scheduleJobDetailRepository.findAllByScheduleJobId(scheduleJobId));
	}

	@Override
	@Transactional
	public void updateScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail) {
		log.debug("");
		ScheduleJobDetailEntity entity=
				scheduleJobDetailRepository.getReferenceById(new ScheduleJobDetailEntityId(scheduleJobDetail.getScheduleJobId(), scheduleJobDetail.getEntryKey()));
		entity.from(scheduleJobDetail);
		scheduleJobDetailRepository.save(entity);
	}

	@Override
	public void deleteScheduleJobDetails(ScheduleJobInfo scheduleJob) {
		log.debug("");
		deleteScheduleJobDetails(scheduleJob.getId());
	}

	@Override
	@Transactional
	public void deleteScheduleJobDetails(String scheduleJobId) {
		log.debug("");
		scheduleJobDetailRepository.deleteByScheduleJobId(scheduleJobId);
	}

	@Override
	public void deleteScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail) {
		log.debug("");
		this.deleteScheduleJobDetail(scheduleJobDetail.getScheduleJobId(), scheduleJobDetail.getEntryKey());
	}

	@Override
	public void deleteScheduleJobDetail(String scheduleJobId, String entryKey) {
		log.debug("");
		scheduleJobDetailRepository.deleteById(new ScheduleJobDetailEntityId(scheduleJobId, entryKey));
	}
}
