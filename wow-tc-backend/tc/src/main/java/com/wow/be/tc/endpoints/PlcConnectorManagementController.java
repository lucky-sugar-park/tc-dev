package com.wow.be.tc.endpoints;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;
import com.wow.be.tc.plc.management.PlcConnectionEventHistInfo;
import com.wow.be.tc.plc.management.PlcConnectionEventHistoryService;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/plc-connector")
@Slf4j
// 권한(설계 §4) — 기본 동작과 다른 메서드(제어·사용 변경 POST, 조회용 POST)는 동작을 명시한다
@Requires("tc.plc")
public class PlcConnectorManagementController {

	@Autowired
	private PlcConnectorManagementService plcConnectorManagementService;
	
	@Autowired
	private PlcConnectionEventHistoryService plcConnectionEventHistoryService;
	
	@RequestMapping(value="/register", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void registerPlcConnector(@RequestBody PlcConnectorInfo plcConnectorInfo) {
		log.debug("PlcConnectorManagementController.registerPlcConnector(PlcConnectorInfo plcConnectorInfo)...");
		plcConnectorManagementService.registerPlcConnector(plcConnectorInfo);
	}
	
	@Requires(action = 'U')
	@RequestMapping(value="/update", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void updatePlcConnector(@RequestBody PlcConnectorInfo plcConnectorInfo) {
		log.debug("PlcConnectorManagementController.updatePlcConnector(PlcConnectorInfo plcConnectorInfo)...");
		plcConnectorManagementService.updatePlcConnector(plcConnectorInfo);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/{plcConnectorId}", method=RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	public void deletePlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) {
		log.debug("PlcConnectorManagementController.deletePlcConnector(long plcConnectorId)...");
		plcConnectorManagementService.deletePlcConnectorById(plcConnectorId);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/byName/{plcConnectorName}", method=RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	public void deletePlcConnector(@PathVariable("plcConnectorName") String plcConnectorName) {
		log.debug("PlcConnectorManagementController.deletePlcConnector(String plcConnectorName)...");
		plcConnectorManagementService.deletePlcConnectorByName(plcConnectorName);
	}
	
	@RequestMapping(value="/find/{plcConnectorId}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PlcConnectorInfo findPlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) {
		log.debug("PlcConnectorManagementController.findPlc(long plcConnectorId)...");
		return plcConnectorManagementService.findPlcConnectorById(plcConnectorId);
	}
	
	@RequestMapping(value="/find/byName/{plcConnectorName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PlcConnectorInfo findPlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) {
		log.debug("PlcConnectorManagementController.findPlcConnectorByName(String plcConnectorName)...");
		return plcConnectorManagementService.findPlcConnectorByName(plcConnectorName);
	}
	
	@RequestMapping(value="/search/all", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<PlcConnectorInfo> searchAllPlcConnectores() {
		log.debug("PlcConnectorManagementController.searchAllPlcConnectores()...");
		return plcConnectorManagementService.searchAllPlcConnectores();
	}
	
	@Requires(action = 'R')
	@RequestMapping(value="/search", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<PlcConnectorInfo> search(@RequestBody Condition cond) {
		log.debug("PlcConnectorManagementController.search(SearchCondition cond)...");
		return plcConnectorManagementService.searchPlcConnectores(cond);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/publish/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> publishPlcConnector(@PathVariable long plcConnectorId) {
		log.debug("PlcConnectorManagementController.publishPlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.publishPlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/publish/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> publishPlcConnectorByName(@PathVariable String plcConnectorName) {
		log.debug("PlcConnectorManagementController.publishPlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.publishPlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/release/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> releasePlcConnector(@PathVariable long plcConnectorId) {
		log.debug("PlcConnectorManagementController.releasePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.releasePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/release/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> releasePlcConnectorByName(@PathVariable String plcConnectorName) {
		log.debug("PlcConnectorManagementController.releasePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.releasePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/pause/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> pausePlcConnector(@PathVariable long plcConnectorId) {
		log.debug("PlcConnectorManagementController.pausePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.pausePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/pause/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> pausePlcConnectorByName(@PathVariable String plcConnectorName) {
		log.debug("PlcConnectorManagementController.pausePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.pausePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/resume/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> resumePlcConnector(@PathVariable long plcConnectorId) {
		log.debug("PlcConnectorManagementController.resumePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.resumePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/resume/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> resumePlcConnectorByName(@PathVariable String plcConnectorName) {
		log.debug("PlcConnectorManagementController.resumePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.resumePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnection(@PathVariable long plcConnectorId) {
		log.debug("PlcConnectorManagementController.testConnection(long plcConnectorId)...");
		return plcConnectorManagementService.testConnection(plcConnectorId);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnectionByName(@PathVariable String plcConnectorName) {
		log.debug("PlcConnectorManagementController.testConnectionByName(String plcConnectorName)...");
		return plcConnectorManagementService.testConnectionByName(plcConnectorName);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/byIpAddress/{ip}/{port}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnectionByIpAddress(@PathVariable String ip, @PathVariable int port) {
		log.debug("PlcConnectorManagementController.testConnectionByName(String plcName)...");
		return plcConnectorManagementService.testConnectionByIpAddress(ip, port);
	}
	
	@Requires(action = 'R')
	@RequestMapping(value="/search/connection-history", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PageResponse<PlcConnectionEventHistInfo> searchPlcConnectionEventHistories(@RequestBody Condition cond) {
		log.debug("");
		return plcConnectionEventHistoryService.searchPlcConnectionEventHist(cond);
	}
}
