package com.wow.be.tc.vplc.manipulation.impl;

import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.MemoryInfo;
import com.wow.be.tc.vplc.machine.VirtualProgrammableController;
import com.wow.be.tc.vplc.machine.VirtualProgrammableControllerBuilder;
import com.wow.be.tc.vplc.machine.VirtualProgrammableControllerManager;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.manipulation.VirtualPlcManipulationService;

import lombok.extern.slf4j.Slf4j;

/**
 * 메모리에 올라온 가상 PLC 의 시작·정지·일시정지·재개와 메모리 읽기·쓰기·초기화.
 * 상태 변경은 {@link VirtualPlcManagementService} 로 DB 에도 남긴다.
 */
@Service
@Slf4j
public class DefaultVirtualPlcManipulationService implements VirtualPlcManipulationService {

	private VirtualProgrammableControllerManager vplcManager;
	
	@Autowired
	private VirtualPlcManagementService vplcManagementService;
	
	public DefaultVirtualPlcManipulationService() {
		vplcManager=VirtualProgrammableControllerManager.getManager();
	}
	
	private void addVirtualProgrammableController(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.addVirtualProgrammableController(VirtualPlcInfo vplcInfo)...");

		VirtualProgrammableController vplc=new VirtualProgrammableControllerBuilder()
				.withId(vplcInfo.getId())
				.withName(vplcInfo.getName())
				.withIpAddress(vplcInfo.getIpAddress())
				.withStatus(PlcStatus.PUBLISHED)
				.withStartPort(vplcInfo.getStartPort())
				.withFrameFormat(vplcInfo.getFrameFormat())
				.withPortCount(vplcInfo.getPortCount())
				.withConnLimit(vplcInfo.getPortConnLimit())
				.withMemoryTypes(vplcInfo.getMemInfos())
				.withManufacturer(vplcInfo.getManufacturer())
				.withDescription(vplcInfo.getDescription())
				.build();
		vplcManager.addVirtualProgrammableController(vplc.getId(), vplc);
	}
	
	@Override
	@Transactional
	public void publishVirtualPlc(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.publishVirtualPlc(VirtualPlcInfo vplcInfo)...");
		this.addVirtualProgrammableController(vplcInfo);
	}
	
	@Override
	@Transactional
	public void publishVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.publishVirtualPlc(String vplcId)...");
		if(vplcManager.getVirtualProgrammableController(vplcId)!=null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "이미 게시된 가상 PLC 입니다: " + vplcId);
		}
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(vplcId, null, PlcStatus.PUBLISHED);
		this.addVirtualProgrammableController(vplcInfo);
	}

	@Override
	@Transactional
	public void publishVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.publishVirtualPlcByName(String vplcName)...");
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(null, vplcName, PlcStatus.PUBLISHED);
		this.addVirtualProgrammableController(vplcInfo);
	}
	
	private void startVirtualProgrammableController(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.startVirtualProgrammableController(String vplcId)...");
		vplcManager.getVirtualProgrammableController(vplcId).start();
	}
	
	@Override
	@Transactional
	public void startVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.startVirtualPlc(String vplcId)...");
		updateVirtualPlcStatus(vplcId, null, PlcStatus.RUNNING);
		this.startVirtualProgrammableController(vplcId);
	}

	@Override
	@Transactional
	public void startVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.startVirtualPlcByName(String vplcName)...");
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(null, vplcName, PlcStatus.RUNNING);
		this.startVirtualProgrammableController(vplcInfo.getId());
	}

	private void stopVirtualProgrammableController(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.stopVirtualProgrammableController(String vplcId)...");
		vplcManager.getVirtualProgrammableController(vplcId).stop();
	}
	
	@Override
	@Transactional
	public void stopVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.stopVirtualPlc(String vplcId)...");
		updateVirtualPlcStatus(vplcId, null, PlcStatus.STOPPED);
		this.stopVirtualProgrammableController(vplcId);
	}	

	@Override
	@Transactional
	public void stopVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.stopVirtualPlcByName(String vplcName)...");
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(null, vplcName, PlcStatus.STOPPED);
		this.stopVirtualProgrammableController(vplcInfo.getId());
	}
	
	private void pauseVirtualProgrammableController(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.pauseVirtualProgrammableController(String vplcId)...");
		vplcManager.getVirtualProgrammableController(vplcId).pause();
	}

	@Override
	@Transactional
	public void pauseVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.pauseVirtualPlc(String vplcId)...");
		updateVirtualPlcStatus(vplcId, null, PlcStatus.PAUSED);
		this.pauseVirtualProgrammableController(vplcId);
	}

	@Override
	@Transactional
	public void pauseVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.pauseVirtualPlcByName(String vplcName)...");
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(null, vplcName, PlcStatus.PAUSED);
		this.pauseVirtualProgrammableController(vplcInfo.getId());
	}
	
	private void resumeVirtualProgrammableController(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.resumeVirtualProgrammableController(String vplcId)...");
		vplcManager.getVirtualProgrammableController(vplcId).resume();
	}

	@Override
	@Transactional
	public void resumeVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.resumeVirtualPlc(String vplcId)...");
		updateVirtualPlcStatus(vplcId, null, PlcStatus.RUNNING);
		this.resumeVirtualProgrammableController(vplcId);
	}

	@Override
	@Transactional
	public void resumeVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.resumeVirtualPlcByName(String vplcName)...");
		VirtualPlcInfo vplcInfo=updateVirtualPlcStatus(null, vplcName, PlcStatus.RUNNING);
		this.resumeVirtualProgrammableController(vplcInfo.getId());
	}
	
	public void releaseVirtualPlc(String plcId) throws VirtualPlcException {
		log.debug("");
		vplcManager.releaseVirtualProgrammableController(plcId);
	}
	
	@Transactional
	private VirtualPlcInfo updateVirtualPlcStatus(String vplcId, String vplcName, PlcStatus status) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.updateVirtualPlc(String vplcId, String vplcName, PlcStatus status)...");
		VirtualPlcInfo vplcInfo=null;
		try {
			if(vplcId!=null) {
				vplcInfo=vplcManagementService.findVirtualPlc(vplcId);
			} else if(vplcName!=null) {
				vplcInfo=vplcManagementService.findVirtualPlcByName(vplcName);
			} else {
				throw new VirtualPlcException(VirtualPlcException.ERR_400, "가상 PLC 아이디 또는 이름은 필수입니다.");
			}

			vplcInfo.setStatus(status);
			// 이름으로 찾은 경우에도 찾은 ID 로 갱신한다 — 예전에는 vplcId(null)를 넘겨 이름으로 하는 제어가 늘 500 이었다
			vplcManagementService.updateVirtualPlcStatus(vplcInfo.getId(), vplcName, status, true);
			
			return vplcInfo;
		} catch(NoSuchElementException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404,
					"가상 PLC 를 찾을 수 없습니다: " + (vplcId!=null ? vplcId : vplcName), e);
		}
	}

	@Override
	public MemoryInfo read(String vplcId, String memoryType, String frameFormat, int startAddress, int wordCount) throws VirtualPlcException {
		log.debug("");
		MemoryType memType=MemoryType.toMemoryType(memoryType);
		byte[] bytes=published(vplcId).read(startAddress, wordCount, memType);
		MemoryInfo mInfo=new MemoryInfo();
		mInfo.setVplcId(vplcId);
		mInfo.setFrameFormat(FrameFormat.valueOf(frameFormat));
		mInfo.setStartAddress(startAddress);
		mInfo.setMemoryType(memType);
		mInfo.setCapacity(200000);
		mInfo.setWords(bytes);

		return mInfo;
	}

	@Override
	public MemoryInfo readByVplcName(String vplcName, String memoryType, String frameFormat, int startAddress, int wordCount) throws VirtualPlcException {
		return read(idOf(vplcName), memoryType, frameFormat, startAddress, wordCount);
	}

	@Override
	public MemoryInfo write(String vplcId, String memoryType, String frameFormat, int startAddress, int wordCount, String data) throws VirtualPlcException {
		log.debug("");
		MemoryType memType=MemoryType.toMemoryType(memoryType);
		return writeBytes(vplcId, memType, frameFormat, startAddress, wordCount, data.getBytes());
	}

	@Override
	public MemoryInfo writeWords(String vplcId, String memoryType, String frameFormat, int startAddress, int[] words) throws VirtualPlcException {
		log.debug("");
		byte[] bytes=new byte[words.length<<1];
		for(int i=0; i<words.length; i++) {
			int w=words[i];
			if(w<Short.MIN_VALUE || w>0xFFFF) {
				throw new VirtualPlcException(VirtualPlcException.ERR_400, "워드 값은 -32768~65535 입니다: " + w);
			}
			bytes[i<<1]=(byte)(w & 0xFF);
			bytes[(i<<1)+1]=(byte)((w>>8) & 0xFF);
		}
		return writeBytes(vplcId, MemoryType.toMemoryType(memoryType), frameFormat, startAddress, words.length, bytes);
	}

	private MemoryInfo writeBytes(String vplcId, MemoryType memType, String frameFormat, int startAddress, int wordCount, byte[] data)
			throws VirtualPlcException {
		VirtualProgrammableController vplc=published(vplcId);
		vplc.write(startAddress, wordCount, data, memType);
		byte[] bytes=vplc.read(startAddress, 20, memType);
		MemoryInfo mInfo=new MemoryInfo();
		mInfo.setVplcId(vplcId);
		mInfo.setFrameFormat(FrameFormat.valueOf(frameFormat));
		mInfo.setStartAddress(startAddress);
		mInfo.setMemoryType(memType);
		mInfo.setCapacity(200000);
		mInfo.setWords(bytes);
		
		return mInfo;
	}

	@Override
	public MemoryInfo writeByVplcName(String vplcName, String memoryType, String frameFormat, int startAddress, int wordCount, String data) throws VirtualPlcException {
		return write(idOf(vplcName), memoryType, frameFormat, startAddress, wordCount, data);
	}

	@Override
	public MemoryInfo clearAll(String vplcId, String memoryType, String frameFormat, int currentPosition) throws VirtualPlcException {
		log.debug("");
		MemoryType memType=MemoryType.toMemoryType(memoryType);
		VirtualProgrammableController vplc=published(vplcId);
		vplc.clear(memType);
		byte[] bytes=vplc.read(currentPosition, 20, memType);
		MemoryInfo mInfo=new MemoryInfo();
		mInfo.setVplcId(vplcId);
		mInfo.setFrameFormat(FrameFormat.valueOf(frameFormat));
		mInfo.setStartAddress(currentPosition);
		mInfo.setMemoryType(memType);
		mInfo.setCapacity(200000);
		mInfo.setWords(bytes);
		
		return mInfo;
	}

	@Override
	public MemoryInfo clearAllByVplcName(String vplcName, String memoryType, String frameFormat, int currentPosition) throws VirtualPlcException {
		return clearAll(idOf(vplcName), memoryType, frameFormat, currentPosition);
	}

	/** 게시(메모리에 올라온)된 가상 PLC — 아니면 409. 예전에는 NPE 로 500 이었다 */
	private VirtualProgrammableController published(String vplcId) throws VirtualPlcException {
		VirtualProgrammableController vplc=vplcManager.getVirtualProgrammableController(vplcId);
		if(vplc==null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다 — 먼저 게시하세요: " + vplcId);
		}
		return vplc;
	}

	/** 이름 → 아이디. 예전 이름으로 하는 읽기·쓰기·지우기는 null 을 돌려줬다 */
	private String idOf(String vplcName) throws VirtualPlcException {
		try {
			VirtualPlcInfo info=vplcManagementService.findVirtualPlcByName(vplcName);
			if(info==null) {
				throw new VirtualPlcException(VirtualPlcException.ERR_404, "가상 PLC 를 찾을 수 없습니다: " + vplcName);
			}
			return info.getId();
		} catch(NoSuchElementException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404, "가상 PLC 를 찾을 수 없습니다: " + vplcName, e);
		}
	}
}
