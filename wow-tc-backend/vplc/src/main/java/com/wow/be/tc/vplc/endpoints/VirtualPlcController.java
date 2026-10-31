package com.wow.be.tc.vplc.endpoints;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.Condition;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.MemoryInfo;
import com.wow.be.tc.vplc.management.ConnectionClientHistoryInfo;
import com.wow.be.tc.vplc.management.ConnectionInfo;
import com.wow.be.tc.vplc.management.PortUpdateInfo;
import com.wow.be.tc.vplc.management.VirtualPlcHistoryInfo;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.manipulation.MemoryClearInfo;
import com.wow.be.tc.vplc.manipulation.MemoryReadInfo;
import com.wow.be.tc.vplc.manipulation.MemoryWriteInfo;
import com.wow.be.tc.vplc.manipulation.VirtualPlcManipulationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 관리(management)·제어(control)·메모리 조작(manipulation) API.
 *
 * <p>경로는 레거시 그대로다. 레거시는 {@code @Version1RestController} 메타 어노테이션으로
 * {@code /api/v1/vplc*} 를 붙였는데, 같은 매핑을 여기에 직접 적었다. 상태를 바꾸는 GET(publish·delete 등)도 그대로 둔다.
 */
@RestController
@RequestMapping("/api/v1/vplc*")
@Slf4j
public class VirtualPlcController {

	@Autowired
	private VirtualPlcManagementService virtualPlcManagementService;

	@Autowired
	private VirtualPlcManipulationService virtualPlcManipulationService;

	/** 가상 PLC 를 등록한다. published=true 면 PUBLISHED, 아니면 REGISTERED 로 저장한다. */
	@PostMapping("/management/register")
	public void registerVirtualPlc(@RequestBody VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("VirtualPlcController.registerVirtualPlc (@RequestBody VirtualPlcInfo vplcInfo)...");
		virtualPlcManagementService.registerVirtualPlc(vplcInfo);
	}

	/** 가상 PLC 정보를 고친다. */
	@PostMapping("/management/update")
	public void updateVirtualPlc(@RequestBody VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.updateVirtualPlc(vplcInfo);
	}

	/** 가상 PLC 의 시작 포트·포트 수·포트당 연결 수를 바꾼다. vplcId 가 없으면 vplcName 으로 찾는다. */
	@PostMapping("/management/update/port")
	public void updateVirtualPlcPort(@RequestBody PortUpdateInfo portUpdateInfo) throws VirtualPlcException {
		log.debug("");
		String vplcId=portUpdateInfo.getVplcId();
		String vplcName=portUpdateInfo.getVplcName();
		int startPort=portUpdateInfo.getStartPort();
		int portCount=portUpdateInfo.getPortCount();
		int portConnLimit=portUpdateInfo.getPortConnLimit();
		if(vplcId!=null) {
			virtualPlcManagementService.updateVirtualPlcPort(vplcId, startPort, portCount, portConnLimit);
		} else if(vplcName!=null) {
			virtualPlcManagementService.updateVirtualPlcPortByName(vplcName, startPort, portCount, portConnLimit);
		} else {
			log.warn("");
			throw idOrNameRequired();
		}
	}

	/** 가상 PLC 를 게시(메모리에 올림)한다. */
	@PostMapping("/management/publish/{vplcId}")
	public void publishVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.publishVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 게시한다. */
	@PostMapping("/management/publish/byName/{vplcName}")
	public void publishVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.publishVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 를 해제(메모리에서 내림)한다. */
	@PostMapping("/management/release/{vplcId}")
	public void releaseVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.releaseVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 해제한다. */
	@PostMapping("/management/release/byName/{vplcName}")
	public void releaseVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.releaseVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 를 삭제한다. */
	@DeleteMapping("/management/delete/{vplcId}")
	public void deleteVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.deleteVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 삭제한다. */
	@DeleteMapping("/management/delete/byName/{vplcName}")
	public void deleteVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManagementService.deleteVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 한 건을 조회한다. */
	@GetMapping("/management/find/{vplcId}")
	public VirtualPlcInfo findVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.findVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 한 건을 조회한다. */
	@GetMapping("/management/find/byName/{vplcName}")
	public VirtualPlcInfo findVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.findVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 전체 목록. */
	@GetMapping("/management/search")
	public List<VirtualPlcInfo> searchAllVirtualPlcs() throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchAllVirtualPlc();
	}

	/** 조건으로 가상 PLC 를 검색한다. */
	@PostMapping("/management/search")
	public List<VirtualPlcInfo> searchVirtualPlc(@RequestBody Condition cond) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchVirtualPlc(cond);
	}

	/** 가상 PLC 변경 이력을 검색한다. */
	@PostMapping("/management/search/histories")
	public List<VirtualPlcHistoryInfo> searchVirtualPlcHistory(@RequestBody Condition cond) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchVirtualPlcHistory(cond);
	}

	/** 클라이언트 접속·끊김 이력을 검색한다. */
	@PostMapping("/management/search/connection/client/histories")
	public List<ConnectionClientHistoryInfo> searchConnectionClientHistory(@RequestBody Condition cond) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchConnectionClientHistory(cond);
	}

	/** 가상 PLC 의 포트별 연결 상태. */
	@GetMapping("/management/search/connection/{vplcId}")
	public List<ConnectionInfo> searchConnections(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchConnections(vplcId);
	}

	/** 이름으로 가상 PLC 의 포트별 연결 상태를 조회한다. */
	@GetMapping("/management/search/connection/byPlcName/{vplcName}")
	public List<ConnectionInfo> searchConnectionsByVplcName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		return virtualPlcManagementService.searchConnectionsByVplcName(vplcName);
	}

	//======================= for manipulation =========================

	/** 가상 PLC 를 시작한다(포트를 열고 요청을 받는다). */
	@PostMapping("/control/start/{vplcId}")
	public VirtualPlcInfo startVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.startVirtualPlc(vplcId);
		VirtualPlcInfo vplcInfo=virtualPlcManagementService.findVirtualPlc(vplcId);
		return vplcInfo;
	}

	/** 이름으로 가상 PLC 를 시작한다. */
	@PostMapping("/control/start/byName/{vplcName}")
	public VirtualPlcInfo startVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.startVirtualPlcByName(vplcName);
		return virtualPlcManagementService.findVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 를 정지한다(포트를 닫는다). */
	@PostMapping("/control/stop/{vplcId}")
	public VirtualPlcInfo stopVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.stopVirtualPlc(vplcId);
		return virtualPlcManagementService.findVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 정지한다. */
	@PostMapping("/control/stop/byName/{vplcName}")
	public VirtualPlcInfo stopVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.stopVirtualPlcByName(vplcName);
		return virtualPlcManagementService.findVirtualPlcByName(vplcName);
	}

	/** 가상 PLC 를 일시정지한다(요청 처리를 멈춘다). */
	@PostMapping("/control/pause/{vplcId}")
	public VirtualPlcInfo pauseVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.pauseVirtualPlc(vplcId);
		return virtualPlcManagementService.findVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 일시정지한다. */
	@PostMapping("/control/pause/byName/{vplcName}")
	public VirtualPlcInfo pauseVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.pauseVirtualPlcByName(vplcName);
		return virtualPlcManagementService.findVirtualPlcByName(vplcName);
	}

	/** 일시정지한 가상 PLC 를 재개한다. */
	@PostMapping("/control/resume/{vplcId}")
	public VirtualPlcInfo resumeVirtualPlc(@PathVariable("vplcId") String vplcId) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.resumeVirtualPlc(vplcId);
		return virtualPlcManagementService.findVirtualPlc(vplcId);
	}

	/** 이름으로 가상 PLC 를 재개한다. */
	@PostMapping("/control/resume/byName/{vplcName}")
	public VirtualPlcInfo resumeVirtualPlcByName(@PathVariable("vplcName") String vplcName) throws VirtualPlcException {
		log.debug("");
		virtualPlcManipulationService.resumeVirtualPlcByName(vplcName);
		return virtualPlcManagementService.findVirtualPlcByName(vplcName);
	}

	/** 메모리를 읽는다. vplcId 가 없으면 vplcName 으로 찾는다. */
	@PostMapping("/manipulation/read")
	public MemoryInfo read(@RequestBody MemoryReadInfo readReq) throws VirtualPlcException {
		log.debug("");

		String vplcId=readReq.getVplcId();
		String vplcName=readReq.getVplcName();
		String memoryType=readReq.getMemoryType();
		String frameFormat=readReq.getFrameFormat();
		int startAddress=readReq.getStartAddress();
		int wordCount=readReq.getWordCount();

		if(vplcId!=null) {
			return virtualPlcManipulationService.read(vplcId, memoryType, frameFormat, startAddress, wordCount);
		} else if(vplcName!=null) {
			return virtualPlcManipulationService.readByVplcName(vplcName, memoryType, frameFormat, startAddress, wordCount);
		} else {
			throw idOrNameRequired();
		}
	}

	/** 메모리에 쓴다. vplcId 가 없으면 vplcName 으로 찾는다. */
	@PostMapping("/manipulation/write")
	public MemoryInfo write(@RequestBody MemoryWriteInfo writeReq) throws VirtualPlcException {
		log.debug("");

		String vplcId=writeReq.getVplcId();
		String vplcName=writeReq.getVplcName();
		String memoryType=writeReq.getMemoryType();
		String frameFormat=writeReq.getFrameFormat();
		int startAddress=writeReq.getStartAddress();
		int wordCount=writeReq.getWordCount();
		String data=writeReq.getData();

		if(writeReq.getWords()!=null) {
			if(vplcId==null && vplcName==null) {
				throw idOrNameRequired();
			}
			String id=vplcId!=null ? vplcId : virtualPlcManagementService.findVirtualPlcByName(vplcName).getId();
			return virtualPlcManipulationService.writeWords(id, memoryType, frameFormat, startAddress, writeReq.getWords());
		}
		if(vplcId!=null) {
			return virtualPlcManipulationService.write(vplcId, memoryType, frameFormat, startAddress, wordCount, data);
		} else if(vplcName!=null) {
			return virtualPlcManipulationService.writeByVplcName(vplcName, memoryType, frameFormat, startAddress, wordCount, data);
		} else {
			throw idOrNameRequired();
		}
	}

	/** 메모리 한 종류를 모두 0 으로 지운다. vplcId 가 없으면 vplcName 으로 찾는다. */
	@PostMapping("/manipulation/clear/all")
	public MemoryInfo clearAll(@RequestBody MemoryClearInfo clearAllReq) throws VirtualPlcException {
		log.debug("");
		String vplcId=clearAllReq.getVplcId();
		String vplcName=clearAllReq.getVplcName();
		String memoryType=clearAllReq.getMemoryType();
		String frameFormat=clearAllReq.getFrameFormat();
		int currentPosition=clearAllReq.getCurrentPosition();

		if(vplcId!=null) {
			return virtualPlcManipulationService.clearAll(vplcId, memoryType, frameFormat, currentPosition);
		} else if(vplcName!=null) {
			return virtualPlcManipulationService.clearAllByVplcName(vplcName, memoryType, frameFormat, currentPosition);
		} else {
			throw idOrNameRequired();
		}
	}

	private static VirtualPlcException idOrNameRequired() {
		return new VirtualPlcException(VirtualPlcException.ERR_400, "가상 PLC 아이디(vplcId) 또는 이름(vplcName)은 필수입니다.");
	}
}
