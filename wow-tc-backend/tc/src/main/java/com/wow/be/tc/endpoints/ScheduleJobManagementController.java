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
import com.wow.be.tc.schedule.JobDeletionInfo;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryService;
import com.wow.be.tc.schedule.ScheduleJobInfo;
import com.wow.be.tc.schedule.ScheduleJobService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/schedule-job/management")
@Slf4j
public class ScheduleJobManagementController {

	@Autowired
	private ScheduleJobService scheduleJobService;
	
	@Autowired
	private ScheduleJobExecHistoryService scheduleJobExecHistoryService;
	
	@RequestMapping(value="/register", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo registerScheduleJob(@RequestBody ScheduleJobInfo jobAdditionInfo) {
		log.debug("");
		scheduleJobService.registerScheduleJob(jobAdditionInfo);
		return scheduleJobService.findScheduleJobByName(jobAdditionInfo.getName());
	}
	
	@RequestMapping(value="/delete/{id}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public void deleteScheduleJobById(@PathVariable("id") String id) {
		log.debug("");
		scheduleJobService.deleteScheduleJobById(id);
	}
	
	@RequestMapping(value="/delete/byName/{name}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public void deleteScheduleJobByName(@PathVariable("name") String name) {
		log.debug("");
		scheduleJobService.deleteScheduleJobByName(name);
	}
	
	@RequestMapping(value="/delete", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> deleteScheduleJob(@RequestBody JobDeletionInfo jobDeletionInfo) {
		log.debug("");
		scheduleJobService.deleteScheduleJobByName(jobDeletionInfo.getDeleteJobName());
		return scheduleJobService.searchScheduleJobs(jobDeletionInfo.getSearchCondition());
	}
	
	@RequestMapping(value="/delete/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteScheduleJobByBatch(@RequestBody List<String> scheduleJobIds) {
		log.debug("");
		scheduleJobService.deleteScheduldJobsByBatch(scheduleJobIds);
	}
	
	@RequestMapping(value="/update", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo updateScheduleJob(@RequestBody ScheduleJobInfo jobUpdateInfo) {
		log.debug("");
		scheduleJobService.updateScheduleJob(jobUpdateInfo);
		return scheduleJobService.findScheduleJobByName(jobUpdateInfo.getName());
	}
	
	@RequestMapping(value="/search", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> searchScheduleJobs(@RequestBody Condition condition) {
		log.debug("");
		return scheduleJobService.searchScheduleJobs(condition);
	}
	
	@RequestMapping(value="/find/byName/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo findScheduleJob(@PathVariable("jobName") String jobName) {
		log.debug("");
		return scheduleJobService.findScheduleJobByName(jobName);
	}
	
	@RequestMapping(value="/search/job-details/byJobId/{jobId}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobDetailInfo> searchScheduleJobDetailsByJobId(@PathVariable("jobId") String jobId) {
		log.debug("");
		return scheduleJobService.searchScheduleJobDetails(jobId);
	}
	
	@RequestMapping(value="/search/job-details/byName/{jobName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobDetailInfo> searchScheduleJobDetailsByJobName(@PathVariable(value="jobName") String jobName) {
		log.debug("");
		return scheduleJobService.searchScheduleJobDetailsByJobName(jobName);
	}

	@RequestMapping(value="/search/job-exec-histories", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(@RequestBody Condition condition) {
		log.debug("");
		return scheduleJobExecHistoryService.searchScheduleJobExecHistory(condition);
	}
}
