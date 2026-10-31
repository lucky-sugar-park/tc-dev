package com.wow.be.tc.endpoints;

import java.util.Collections;
import java.util.LinkedHashMap;
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
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.ProtocolDescriptor;
import com.wow.be.tc.plc.management.PlcConnectionEventHistInfo;
import com.wow.be.tc.plc.management.PlcConnectionEventHistoryService;
import com.wow.be.tc.plc.management.PlcConnectionState;
import com.wow.be.tc.plc.management.PlcConnectionStateStore;
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

	@Autowired
	private PlcConnectionStateStore connectionStateStore;
	
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
		return withConnection(plcConnectorManagementService.findPlcConnectorById(plcConnectorId));
	}
	
	@RequestMapping(value="/find/byName/{plcConnectorName}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PlcConnectorInfo findPlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) {
		log.debug("PlcConnectorManagementController.findPlcConnectorByName(String plcConnectorName)...");
		return withConnection(plcConnectorManagementService.findPlcConnectorByName(plcConnectorName));
	}
	
	@RequestMapping(value="/search/all", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<PlcConnectorInfo> searchAllPlcConnectores() {
		log.debug("PlcConnectorManagementController.searchAllPlcConnectores()...");
		return withConnection(plcConnectorManagementService.searchAllPlcConnectores());
	}

	/** 커넥터마다 지금 연결 상태(Redis). 아무 인스턴스도 연결하지 않은 커넥터는 빠진다 — 화면이 주기적으로 읽는다 */
	@RequestMapping(value="/connection-states", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<PlcConnectionState> connectionStates() {
		List<String> names=plcConnectorManagementService.searchAllPlcConnectores().stream().map(PlcConnectorInfo::getName).toList();
		return List.copyOf(connectionStateStore.find(names).values());
	}
	
	@Requires(action = 'R')
	@RequestMapping(value="/search", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<PlcConnectorInfo> search(@RequestBody Condition cond) {
		log.debug("PlcConnectorManagementController.search(SearchCondition cond)...");
		return withConnection(plcConnectorManagementService.searchPlcConnectores(cond));
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/publish/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> publishPlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) throws ToolControlException {
		log.debug("PlcConnectorManagementController.publishPlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.publishPlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/publish/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> publishPlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) throws ToolControlException {
		log.debug("PlcConnectorManagementController.publishPlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.publishPlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/release/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> releasePlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) throws ToolControlException {
		log.debug("PlcConnectorManagementController.releasePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.releasePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/release/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> releasePlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) throws ToolControlException {
		log.debug("PlcConnectorManagementController.releasePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.releasePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/pause/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> pausePlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) throws ToolControlException {
		log.debug("PlcConnectorManagementController.pausePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.pausePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/pause/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> pausePlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) throws ToolControlException {
		log.debug("PlcConnectorManagementController.pausePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.pausePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/resume/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> resumePlcConnector(@PathVariable("plcConnectorId") long plcConnectorId) throws ToolControlException {
		log.debug("PlcConnectorManagementController.resumePlcConnector(long plcConnectorId)...");
		boolean result=plcConnectorManagementService.resumePlcConnector(plcConnectorId);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/control/resume/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Boolean> resumePlcConnectorByName(@PathVariable("plcConnectorName") String plcConnectorName) throws ToolControlException {
		log.debug("PlcConnectorManagementController.resumePlcConnectorByName(String plcConnectorName)...");
		boolean result=plcConnectorManagementService.resumePlcConnectorByName(plcConnectorName);
		return Collections.singletonMap("success", result);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/{plcConnectorId}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnection(@PathVariable("plcConnectorId") long plcConnectorId) throws ToolControlException {
		log.debug("PlcConnectorManagementController.testConnection(long plcConnectorId)...");
		return plcConnectorManagementService.testConnection(plcConnectorId);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnectionByName(@PathVariable("plcConnectorName") String plcConnectorName) throws ToolControlException {
		log.debug("PlcConnectorManagementController.testConnectionByName(String plcConnectorName)...");
		return plcConnectorManagementService.testConnectionByName(plcConnectorName);
	}
	
	@Requires(action = 'X')
	@RequestMapping(value="/test-connection/byIpAddress/{ip}/{port}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, String> testConnectionByIpAddress(@PathVariable("ip") String ip, @PathVariable("port") int port) {
		log.debug("PlcConnectorManagementController.testConnectionByName(String plcName)...");
		return plcConnectorManagementService.testConnectionByIpAddress(ip, port);
	}
	
	/**
	 * 인터페이스 없이 점을 바로 읽어 본다 — 커넥터 화면의 시험 읽기(docs/plc-protocol-design.md §16). 게시된 커넥터만.
	 * 본문 {@code { "points": [ { "name", "address", "type", "count", "length" } ] }} → {@code { "values", "plcStatus", "elapsedMs" }}.
	 * PLC 가 오류로 답하면 200 + plcStatus(code ≠ 0).
	 */
	/** 본문 {@code { "nodeId": "ns=2;s=Line1" }}(비우면 Objects 폴더) → 자식 노드 — OPC UA 커넥터만(docs/opcua-design.md O10) */
	public record BrowseRequest(String nodeId) {
	}

	@Requires(action = 'R')
	@RequestMapping(value="/browse/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public List<com.wow.be.tc.plc.connector.opcua.OpcUaTools.BrowseNode> browse(@PathVariable("plcConnectorName") String plcConnectorName,
			@RequestBody(required=false) BrowseRequest request) throws ToolControlException {
		return plcConnectorManagementService.browse(plcConnectorName, request!=null ? request.nodeId() : null);
	}

	@Requires(action = 'X')
	@RequestMapping(value="/test-read/byName/{plcConnectorName}", method=RequestMethod.POST)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public Map<String, Object> testRead(@PathVariable("plcConnectorName") String plcConnectorName, @RequestBody TestReadRequest request)
			throws ToolControlException {
		long t0=System.currentTimeMillis();
		PlcResult result=plcConnectorManagementService.testRead(plcConnectorName, request!=null ? request.points() : null);
		Map<String, Object> body=new LinkedHashMap<>();
		body.put("values", result.values());
		body.put("plcStatus", result.status());
		body.put("elapsedMs", System.currentTimeMillis()-t0);
		return body;
	}

	/** 지원하는 프로토콜과 설정 항목·주소 영역(화면의 커넥터·점 폼용, docs/plc-protocol-design.md §16) */
	@RequestMapping(value="/protocols", method=RequestMethod.GET)
	@ResponseStatus(value=HttpStatus.OK)
	@ResponseBody
	public List<ProtocolDescriptor> protocols() {
		return PlcDrivers.describeAll();
	}

	/** 시험 읽기 본문 */
	public record TestReadRequest(List<PointInfo> points) {
	}

	@Requires(action = 'R')
	@RequestMapping(value="/search/connection-history", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PageResponse<PlcConnectionEventHistInfo> searchPlcConnectionEventHistories(@RequestBody Condition cond) {
		log.debug("");
		return plcConnectionEventHistoryService.searchPlcConnectionEventHist(cond);
	}

	private PlcConnectorInfo withConnection(PlcConnectorInfo info) {
		if(info!=null) {
			withConnection(List.of(info));
		}
		return info;
	}

	private List<PlcConnectorInfo> withConnection(List<PlcConnectorInfo> infos) {
		Map<String, PlcConnectionState> states=connectionStateStore.find(infos.stream().map(PlcConnectorInfo::getName).toList());
		infos.forEach(info->info.setConnection(states.get(info.getName())));
		return infos;
	}
}
