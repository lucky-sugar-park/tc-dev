package com.wow.be.tc.schedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScheduleJobsLoader {
	
	@Autowired
	private ScheduleJobService scheduleJobService;
	
	/**
	 * 이 클래스의 객체가 Springframework에 의해서 생성된 이후에 springframework에 의해서 호출되는 함수.
	 * 등록된 모든 ScheduleJob들을 load한다 
	 */
	@PostConstruct
	public void loadScheduleJobs() {
		log.info("ScheduleJobsInit.loadScheduleJobs() ...");
		scheduleJobService.loadSchedules();
		
		// 기본적으로 PLC Connection Spy schedule job에 대한 스케쥴을 건다
		// Quartz가 자동으로 동시성 문제를 해결해 줌
//		scheduleJobService.schedule(new ScheduleJobInfo());
	}
}
