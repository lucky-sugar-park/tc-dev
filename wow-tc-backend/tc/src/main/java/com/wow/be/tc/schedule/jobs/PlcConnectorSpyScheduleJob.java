package com.wow.be.tc.schedule.jobs;

import org.quartz.JobExecutionContext;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.schedule.ScheduleJob;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlcConnectorSpyScheduleJob extends ScheduleJob {
	
	/**
	 * PLC Connector들의 상태를 실시간으로 체크함
	 */
	@Override
	public void executeJob(JobExecutionContext context) throws ToolControlException {
		log.debug("");
		// 등록된 PLC 목록을 얻어온 후에 각각의 PLC Connector에 대한 상태를 체크한다
		// 체크 결과를 context에 담아준다
		PlcAdapterManager.getPlcManager();
	}
}
