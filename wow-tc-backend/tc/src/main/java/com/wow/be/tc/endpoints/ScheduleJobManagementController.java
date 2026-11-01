package com.wow.be.tc.endpoints;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.schedule.JobDeletionInfo;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryQuery;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryService;
import com.wow.be.tc.schedule.ScheduleJobInfo;
import com.wow.be.tc.utils.RangeQueries;
import com.wow.be.tc.schedule.ScheduleJobKind;
import com.wow.be.tc.schedule.ScheduleJobService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/schedule-job/management")
@Slf4j
// 권한(설계 §4) — 기본 동작과 다른 메서드(제어·사용 변경 POST, 조회용 POST)는 동작을 명시한다
@Requires("tc.schedule")
public class ScheduleJobManagementController {

	@Autowired
	private ScheduleJobService scheduleJobService;
	
	@Autowired
	private ScheduleJobExecHistoryService scheduleJobExecHistoryService;
	
	@RequestMapping(value="/register", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo registerScheduleJob(@RequestBody ScheduleJobInfo jobAdditionInfo) throws ToolControlException {
		log.debug("");
		scheduleJobService.registerScheduleJob(jobAdditionInfo);
		return scheduleJobService.findScheduleJobByName(jobAdditionInfo.getName());
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/{id}", method=RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public void deleteScheduleJobById(@PathVariable("id") String id) {
		log.debug("");
		scheduleJobService.deleteScheduleJobById(id);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/byName/{name}", method=RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public void deleteScheduleJobByName(@PathVariable("name") String name) {
		log.debug("");
		scheduleJobService.deleteScheduleJobByName(name);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> deleteScheduleJob(@RequestBody JobDeletionInfo jobDeletionInfo) {
		log.debug("");
		scheduleJobService.deleteScheduleJobByName(jobDeletionInfo.getDeleteJobName());
		return scheduleJobService.searchScheduleJobs(jobDeletionInfo.getSearchCondition());
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteScheduleJobByBatch(@RequestBody List<String> scheduleJobIds) {
		log.debug("");
		scheduleJobService.deleteScheduldJobsByBatch(scheduleJobIds);
	}
	
	@Requires(action = 'U')
	@RequestMapping(value="/update", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ScheduleJobInfo updateScheduleJob(@RequestBody ScheduleJobInfo jobUpdateInfo) throws ToolControlException {
		log.debug("");
		scheduleJobService.updateScheduleJob(jobUpdateInfo);
		return scheduleJobService.findScheduleJobByName(jobUpdateInfo.getName());
	}
	
	@Requires(action = 'R')
	@RequestMapping(value="/search", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobInfo> searchScheduleJobs(@RequestBody Condition condition) {
		log.debug("");
		return scheduleJobService.searchScheduleJobsWithStatus(condition);
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

	@Requires(action = 'R')
	@RequestMapping(value="/search/job-exec-histories", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(@RequestBody Condition condition) {
		log.debug("");
		return scheduleJobExecHistoryService.searchScheduleJobExecHistory(condition);
	}

	/** 실행 이력 한 쪽 */
	public record ExecHistoryPage(List<ScheduleJobExecHistoryInfo> items, long total, int page, int size) {
	}

	@Operation(summary = "등록할 수 있는 Job 종류", description = "종류마다 Job 클래스와 속성 정의(필수·값 형·기본값·고를 값). 화면의 속성 표 틀.")
	@Requires(action = 'R')
	@GetMapping("/job-kinds")
	public List<ScheduleJobKind.Info> jobKinds() {
		return Arrays.stream(ScheduleJobKind.values()).map(ScheduleJobKind::info).toList();
	}

	@Operation(summary = "실행 이력 검색", description = "Job 이름(같음)·Job 이름·설명 글·Job 상태·기간·결과로 찾는다. 최신순, 쪽 크기 최대 500.")
	@Requires(action = 'R')
	@PostMapping("/exec-histories/query")
	public ExecHistoryPage queryExecHistories(@RequestBody ScheduleJobExecHistoryQuery q) {
		Page<ScheduleJobExecHistoryInfo> page = scheduleJobExecHistoryService.query(q);
		return new ExecHistoryPage(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
	}

	@Operation(summary = "실행 이력 구간", description = "화면의 구간 스크롤 표·엑셀(docs/range-grid-design.md §4). 조건은 검색과 같고 page·size 는 쓰지 않는다. 최신순, 스냅샷 asOf(SEQ_NO), 건수는 withTotal 일 때만.")
	@Requires(action = 'R')
	@PostMapping("/exec-histories/range")
	public RangeQueries.Result<ScheduleJobExecHistoryInfo> rangeExecHistories(@RequestBody RangeQueries.Request<ScheduleJobExecHistoryQuery> req) {
		ScheduleJobExecHistoryQuery q = req.filter() != null ? req.filter() : new ScheduleJobExecHistoryQuery(null, null, null, null, null, null, null, null);
		return scheduleJobExecHistoryService.range(q, req.rangeOrDefault());
	}
}
