package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.Condition;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;
import com.wow.be.tc.schedule.ScheduleJobInfo;
import com.wow.be.tc.schedule.ScheduleJobService;
import com.wow.be.tc.schedule.TriggerStatusInfo;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/schedule-job/monitoring")
@Slf4j
public class ScheduleJobMonitoringController {

	@Autowired
	private ScheduleJobService scheduleJobService;
	
	@RequestMapping(value="/searchJobExecutionHistory", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(@RequestBody Condition condition) {
		log.debug("");
		return scheduleJobService.searchScheduleJobExecHistory(condition);
	}
	
	@RequestMapping(value="/searchRunningScheduleJobs", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<TriggerStatusInfo> searchRunningScheduleJobs() {
		log.debug("");
		return scheduleJobService.getRunningScheduleJobs();
	}
	
	private List<ScheduleJobInfo> searchScheduleJobs () {
		log.debug("");
		return scheduleJobService.searchScheduleJobs();
	}
	
	@RequestMapping(value="/reloadAllScheduleJobs", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> reloadScheduleJobs() {
		log.debug("");
		scheduleJobService.reloadSchedules();
		return this.searchScheduleJobs();
	}
	
	@RequestMapping(value="/pauseAllScheduleJobs", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> pauseAllScheduleJob() {
		log.debug("");
		scheduleJobService.pauseAll();
		return this.searchScheduleJobs();
	}
	
	@RequestMapping(value="/resumeAllScheduleJobs", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> resumeAllScheduleJob() {
		log.debug("");
		scheduleJobService.resumeAll();
		return this.searchScheduleJobs();
	}
	
	@RequestMapping(value="/pauseScheduleJob/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo pauseScheduleJob(@PathVariable(value="jobName") String jobName) {
		log.debug("");
		scheduleJobService.pause(jobName);
		return scheduleJobService.findScheduleJobByName(jobName);
	}
	
	@RequestMapping(value="/pauseScheduleJob/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void pauseScheduleJobByBatch(@RequestBody List<String> scheduleJobNames) {
		log.debug("");
		scheduleJobService.pauseByBatch(scheduleJobNames);
	}

	@RequestMapping(value="/resumeScheduleJob/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo resumeScheduleJob(@PathVariable(value="jobName") String jobName) {
		log.debug("");
		scheduleJobService.resume(jobName);
		return scheduleJobService.findScheduleJobByName(jobName);
	}
	
	@RequestMapping(value="/resumeScheduleJob/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void resumeJobsByBatch(@RequestBody List<String> scheduleJobNames) {
		log.debug("");
		scheduleJobService.resumeByBatch(scheduleJobNames);
	}
	
	@RequestMapping(value="/unscheduleJob/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo unscheduleJob(@PathVariable(value="jobName") String jobName) {
		log.debug("");
		scheduleJobService.unschedule(jobName);
		return scheduleJobService.findScheduleJobByName(jobName);
	}
	
	@RequestMapping(value="/unscheduleJob/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void unscheduleJobsByBatch(@RequestBody List<String> scheduleJobNames) {
		log.debug("");
		scheduleJobService.unscheduleByBatch(scheduleJobNames);
	}
	
	@RequestMapping(value="/rescheduleJob/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo rescheduleJob(@PathVariable(value="jobName") String jobName) {
		log.debug("");
		scheduleJobService.reschedule(jobName);
		return scheduleJobService.findScheduleJobByName(jobName);
	}
	
	@RequestMapping(value="/rescheduleJob/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void rescheduleJobsByBatch(@RequestBody List<String> scheduleJobNames) {
		log.debug("");
		scheduleJobService.rescheduleByBatch(scheduleJobNames);
	}
}
