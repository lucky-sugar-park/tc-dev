package com.wow.be.tc.vplc.management.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.framework.Condition;
import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.VirtualDevice;
import com.wow.be.tc.vplc.machine.VirtualDeviceFactory;
import com.wow.be.tc.vplc.machine.VirtualProgrammableControllerManager;
import com.wow.be.tc.vplc.management.ConnectionClientHistoryInfo;
import com.wow.be.tc.vplc.management.ConnectionClientInfo;
import com.wow.be.tc.vplc.management.ConnectionInfo;
import com.wow.be.tc.vplc.management.VirtualPlcHistoryInfo;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.opcua.OpcUaTagRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 PLC 등록·수정·게시(publish)·해제(release)·조회와 연결(포트·접속 클라이언트) 이력 저장.
 */
@Slf4j
@Service
public class DefaultVirtualPlcManagementService implements VirtualPlcManagementService, PersistentService<VirtualPlcInfo, VirtualPlcEntity> {
	
	@Autowired
	private VirtualPlcRepository virtualPlcRepository;
	
	@Autowired
	private VirtualPlcHistoryRepository virtualPlcHistoryRepository;
	
	@Autowired
	private ConnectionRepository connectionRepository;
	
	@Autowired
	private ConnectionClientRepository connectionClientRepository;
	
	@Autowired
	private ConnectionClientHistoryRepository connectionClientHistoryRepository;

	@Autowired
	private VirtualDeviceFactory deviceFactory;

	@Autowired
	private OpcUaTagRepository opcUaTagRepository;
	
	private VirtualProgrammableControllerManager virtualPlcManager;
	
	public DefaultVirtualPlcManagementService () {
		virtualPlcManager=VirtualProgrammableControllerManager.getManager();
	}
	
	@Override
	@Transactional
	public void registerVirtualPlc(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManagementService.registerVirtualPlc(VirtualPlcInfo vplcInfo)...");
		if(vplcInfo.getId()==null || "".equals(vplcInfo.getId())) {
			vplcInfo.setId(UUID.randomUUID().toString());
		}
		VirtualDeviceFactory.normalize(vplcInfo);
		if(vplcInfo.isPublished()==true) {
			vplcInfo.setStatus(PlcStatus.PUBLISHED);
		} else {
			vplcInfo.setStatus(PlcStatus.REGISTERED);
		}

		VirtualPlcEntity entity=new VirtualPlcEntity();
		entity.from(vplcInfo);

		virtualPlcRepository.save(entity);
		
		VirtualPlcHistoryEntity hentity=new VirtualPlcHistoryEntity();
		hentity.setPlcId(vplcInfo.getId());
		hentity.setPlcName(vplcInfo.getName());
		hentity.setStatus(vplcInfo.getStatus().name());
		hentity.setHistoryContents("First Created.\n"+ vplcInfo.toJson(true));
		hentity.setCreatedBy("SYSTEM");
		hentity.setCreatedDate(new Date());
		hentity.setUpdatedBy("SYSTEM");
		hentity.setUpdatedDate(new Date());

		virtualPlcHistoryRepository.save(hentity);
	}

	@Override
	@Transactional
	public void updateVirtualPlc(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManagementService.updateVirtualPlc(VirtualPlcInfo vplcInfo)...");
		// 종류는 바꿀 수 없다 — 비어 있으면 저장된 종류
		VirtualPlcInfo stored=findVirtualPlc(vplcInfo.getId());
		if(vplcInfo.getProtocol()==null) {
			vplcInfo.setProtocol(stored.getProtocol());
		} else if(vplcInfo.getProtocol()!=stored.getProtocol()) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "가상 PLC 종류는 바꿀 수 없습니다 — 지우고 다시 등록하세요: " + stored.getProtocol() + " → " + vplcInfo.getProtocol());
		}
		VirtualDeviceFactory.normalize(vplcInfo);
		if(vplcInfo.isPublished() && (PlcStatus.REGISTERED==vplcInfo.getStatus()) || PlcStatus.RELEASED==vplcInfo.getStatus()) {
			vplcInfo.setStatus(PlcStatus.PUBLISHED);
		} else if(vplcInfo.isPublished()!=true) {
			vplcInfo.setStatus(PlcStatus.RELEASED);
		}

		VirtualPlcEntity entity=requireVirtualPlcEntity(vplcInfo.getId());
		entity.from(vplcInfo);
		entity.setUpdatedBy("SYSTEM");
		entity.setUpdatedDate(new Date());
		
		virtualPlcRepository.save(entity);
		
		VirtualPlcHistoryEntity hentity=new VirtualPlcHistoryEntity();
		hentity.setPlcId(vplcInfo.getId());
		hentity.setPlcName(vplcInfo.getName());
		hentity.setStatus(vplcInfo.getStatus().name());
		hentity.setHistoryContents(vplcInfo.toJson(true));
		hentity.setCreatedBy("SYSTEM");
		hentity.setCreatedDate(new Date());
		hentity.setUpdatedBy("SYSTEM");
		hentity.setUpdatedDate(new Date());

		virtualPlcHistoryRepository.save(hentity);
		if(vplcInfo.isPublished() && (PlcStatus.REGISTERED==vplcInfo.getStatus()) || PlcStatus.RELEASED==vplcInfo.getStatus()) {
			publishVirtualProgrammableController(vplcInfo);
		} else if((PlcStatus.REGISTERED==vplcInfo.getStatus() || PlcStatus.RELEASED==vplcInfo.getStatus()) && vplcInfo.isPublished()!=true) {
			vplcInfo.setStatus(PlcStatus.RELEASED);
			virtualPlcManager.releaseVirtualProgrammableController(vplcInfo.getId());
		}
	}

	@Override
	@Transactional
	public void updateVirtualPlcPort(String vplcId, int startPort, int portCount, int portConnLimit) throws VirtualPlcException {
		log.debug("");
		VirtualPlcEntity entity=requireVirtualPlcEntity(vplcId);
		entity.setStartPort(startPort);
		entity.setPortCount(portCount);
		entity.setPortConnLimit(portConnLimit);
		
		entity.setUpdatedBy("");
		entity.setUpdatedDate(new Date());
		
		virtualPlcRepository.save(entity);
	}

	@Override
	@Transactional
	public void updateVirtualPlcPortByName(String vplcName, int startPort, int portCount, int portConnLimit) throws VirtualPlcException {
		log.debug("");
		VirtualPlcEntity entity=this.getVirtualPlcByName(vplcName);
		entity.setStartPort(startPort);
		entity.setPortCount(portCount);
		entity.setPortConnLimit(portConnLimit);
		
		entity.setUpdatedBy("SYSTEM");
		entity.setUpdatedDate(new Date());
		
		virtualPlcRepository.save(entity);
	}
	
	@Override
	@Transactional
	public VirtualPlcInfo updateVirtualPlcStatus(String vplcId, String vplcName, PlcStatus plcStatus, boolean isPublished) throws VirtualPlcException {
		log.debug("");
		VirtualPlcInfo vplcInfo=findVirtualPlc(vplcId);
		PlcStatus beforeStatus=vplcInfo.getStatus();
		virtualPlcRepository.updateVirtualPlcStatus(vplcId, vplcName, plcStatus.name(), isPublished);
		vplcInfo.setStatus(plcStatus);

		VirtualPlcHistoryEntity hentity=new VirtualPlcHistoryEntity();
		hentity.setPlcId(vplcId);
		hentity.setPlcName(vplcInfo.getName());
		hentity.setStatus(plcStatus.name());
		hentity.setHistoryContents("virtual plc status Changed, From: " + beforeStatus.name() + ", To:" + plcStatus.name());
		hentity.setCreatedBy("SYSTEM");
		hentity.setCreatedDate(new Date());
		hentity.setUpdatedBy("SYSTEM");
		hentity.setUpdatedDate(new Date());

		virtualPlcHistoryRepository.save(hentity);
		return vplcInfo;
	}
	
	@Transactional
	private void publishVirtualProgrammableController(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManipulationService.addVirtualProgrammableController(VirtualPlcInfo vplcInfo)...");

		// Connection 정보생성
		if(vplcInfo.getConnections()==null || vplcInfo.getConnections().size()<=0) {
			int startPort=vplcInfo.getStartPort();
			int portCount=vplcInfo.getPortCount();
			for(int i=startPort;i<startPort+portCount;i++) {
				ConnectionInfo conn=new ConnectionInfo();
				conn.setVplcId(vplcInfo.getId());
				conn.setPort(i);
				conn.setConnected(false);
				conn.setConnectedCount(0);
				vplcInfo.addConnectionInfo(conn);
				
				ConnectionEntity connEntity=new ConnectionEntity();
				connEntity.from(conn);
				connectionRepository.save(connEntity);
			}
		}
		// Virtual PLC 정보 메모리로 Load — 종류별 객체(VirtualDeviceFactory)
		VirtualDevice vplc=deviceFactory.create(vplcInfo);
		vplc.setStatus(PlcStatus.PUBLISHED);
		virtualPlcManager.addVirtualProgrammableController(vplc.getId(), vplc);
	}
	
	@Transactional
	private void releaseVirtualProgrammableController(VirtualPlcInfo vplcInfo) {
		log.debug("");
		// Connection 정보삭제
		vplcInfo.getConnections().forEach(conn->{
			ConnectionEntity connEntity=new ConnectionEntity();
			connEntity.from(conn);
			connectionRepository.delete(connEntity);
			
			conn.getConnectedClientList().forEach(client->{
				ConnectionClientHistoryEntity chEntity=new ConnectionClientHistoryEntity();
				chEntity.setVplcId(client.getVplcId());
				chEntity.setPort(client.getPort());
				chEntity.setConnOrDisconn("VPLC_RELEASED_BY_FORCED");
				chEntity.setClientChannelId(client.getClientChannelId());
				chEntity.setClientIp(client.getClientIp());
				chEntity.setTimestamp(new Date());
				chEntity.setDescription("Connected client was disconnected by force with administrator's virtual plc release command");
				chEntity.setCreatedBy("SYSTEM");
				chEntity.setCreatedDate(new Date());
				chEntity.setUpdatedBy("SYSTEM");
				chEntity.setUpdatedDate(new Date());
				connectionClientHistoryRepository.save(chEntity);
			});
		});
		// 메모리에서 해당 Virtual PLC 제거
		virtualPlcManager.releaseVirtualProgrammableController(vplcInfo.getId());
	}
	
	@Override
	@Transactional
	public void publishVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("");
		VirtualPlcInfo vInfo=this.findVirtualPlc(vplcId);
		if(vInfo.getStatus()==PlcStatus.PUBLISHED || (vInfo.getStatus()!=PlcStatus.REGISTERED && vInfo.getStatus()!=PlcStatus.RELEASED)) {
			throw publishNotAllowed(vInfo);
		}
		VirtualPlcInfo vplcInfo = updateVirtualPlcStatus(vplcId, "", PlcStatus.PUBLISHED, true);
		this.publishVirtualProgrammableController(vplcInfo);
	}

	@Override
	@Transactional
	public void publishVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("");
		VirtualPlcInfo vInfo=this.findVirtualPlcByName(vplcName);
		if(vInfo.getStatus()==PlcStatus.PUBLISHED || (vInfo.getStatus()!=PlcStatus.REGISTERED && vInfo.getStatus()!=PlcStatus.RELEASED)) {
			throw publishNotAllowed(vInfo);
		}
		VirtualPlcInfo vplcInfo = updateVirtualPlcStatus("", vplcName, PlcStatus.PUBLISHED, true);
		this.publishVirtualProgrammableController(vplcInfo);
	}

	private VirtualPlcException publishNotAllowed(VirtualPlcInfo vInfo) {
		return new VirtualPlcException(VirtualPlcException.ERR_409,
				"등록(REGISTERED)·해제(RELEASED) 상태의 가상 PLC 만 게시할 수 있습니다: " + vInfo.getName() + " (" + vInfo.getStatus() + ")");
	}

	private VirtualPlcException releaseNotAllowed(VirtualPlcInfo vInfo) {
		return new VirtualPlcException(VirtualPlcException.ERR_409,
				"이미 해제됐거나 실행 중인 가상 PLC 는 해제할 수 없습니다: " + vInfo.getName() + " (" + vInfo.getStatus() + ")");
	}

	@Override
	@Transactional
	public void releaseVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("");
		VirtualPlcInfo vInfo=this.findVirtualPlc(vplcId);
		if(vInfo.getStatus()==PlcStatus.RELEASED || vInfo.getStatus()==PlcStatus.RUNNING) {
			throw releaseNotAllowed(vInfo);
		}
		VirtualPlcInfo vplcInfo = updateVirtualPlcStatus(vplcId, "", PlcStatus.RELEASED, false);
		this.releaseVirtualProgrammableController(vplcInfo);
	}

	@Override
	@Transactional
	public void releaseVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("");
		VirtualPlcInfo vInfo=this.findVirtualPlcByName(vplcName);
		if(vInfo.getStatus()==PlcStatus.RELEASED || vInfo.getStatus()==PlcStatus.RUNNING) {
			throw releaseNotAllowed(vInfo);
		}
		VirtualPlcInfo vplcInfo = updateVirtualPlcStatus("", vplcName, PlcStatus.RELEASED, false);
		this.releaseVirtualProgrammableController(vplcInfo);
	}

	@Override
	@Transactional
	public void deleteVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("");
		// 게시된 채로 지우면 포트가 열린 채 남았다 — 먼저 메모리에서 내린다. OPC UA 태그도 함께 지운다
		virtualPlcManager.releaseVirtualProgrammableController(vplcId);
		opcUaTagRepository.deleteByVplcId(vplcId);
		virtualPlcRepository.deleteById(vplcId);
	}

	@Override
	@Transactional
	public void deleteVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("");
		// 없는 이름은 그대로 200(DELETE 는 몇 번 해도 같다 — test_vplc_http_methods)
		VirtualPlcEntity probe=new VirtualPlcEntity();
		probe.setName(vplcName);
		Example<VirtualPlcEntity> example=Example.of(probe, ExampleMatcher.matchingAny().withMatcher("name", ExampleMatcher.GenericPropertyMatchers.exact()));
		java.util.Optional<VirtualPlcEntity> found=virtualPlcRepository.findOne(example);
		if(found.isPresent()) {
			deleteVirtualPlc(found.get().getId());
		}
	}

	@Override
	@Transactional
	public VirtualPlcInfo findVirtualPlc(String vplcId) throws VirtualPlcException {
		log.debug("");
		return requireVirtualPlcEntity(vplcId).info();
	}

	private VirtualPlcEntity requireVirtualPlcEntity(String vplcId) throws VirtualPlcException {
		return virtualPlcRepository.findById(vplcId).orElseThrow(() -> notFound(vplcId));
	}

	private static VirtualPlcException notFound(String idOrName) {
		return new VirtualPlcException(VirtualPlcException.ERR_404, "가상 PLC 가 없습니다: " + idOrName);
	}
	
	private VirtualPlcEntity getVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("");
		VirtualPlcEntity probe=new VirtualPlcEntity();
		probe.setName(vplcName);
		
		ExampleMatcher matcher = ExampleMatcher.matchingAny();
		matcher=matcher.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.exact());
		Example<VirtualPlcEntity> example=Example.of(probe, matcher);
		
		return virtualPlcRepository.findOne(example).orElseThrow(() -> notFound(vplcName));
	}

	@Override
	@Transactional
	public VirtualPlcInfo findVirtualPlcByName(String vplcName) throws VirtualPlcException {
		log.debug("");
		return this.getVirtualPlcByName(vplcName).info();
	}

	@Override
	@Transactional
	public List<VirtualPlcInfo> searchAllVirtualPlc() throws VirtualPlcException {
		log.debug("");
		return infos(virtualPlcRepository.findAll());
	}

	@Override
	@Transactional
	public List<VirtualPlcInfo> searchVirtualPlc(Condition cond) throws VirtualPlcException {
		log.debug("");

		VirtualPlcSpec specs=new VirtualPlcSpec();
		List<VirtualPlcEntity> virtualPlcEntityList=null;
		Specification<VirtualPlcEntity> specification=specs.createSpecification(cond);

		if(cond.getPageInfo()==null) {
			virtualPlcEntityList=virtualPlcRepository.findAll(orUnrestricted(specification));
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			virtualPlcEntityList=virtualPlcRepository.findAll(orUnrestricted(specification), prequest).getContent();
		}

		return infos(virtualPlcEntityList);
	}
	
	@Override
	@Transactional
	public List<VirtualPlcHistoryInfo> searchVirtualPlcHistory(Condition cond) throws VirtualPlcException {
		log.debug("");

		VirtualPlcHistorySpec specs=new VirtualPlcHistorySpec();
		List<VirtualPlcHistoryEntity> virtualPlcHistEntityList=null;
		Specification<VirtualPlcHistoryEntity> specification=specs.createSpecification(cond);
		if(cond.getPageInfo()==null) {
			virtualPlcHistEntityList=virtualPlcHistoryRepository.findAll();
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			virtualPlcHistEntityList=virtualPlcHistoryRepository.findAll(orUnrestricted(specification), prequest).getContent();
		}
		EntityConverter<VirtualPlcHistoryEntity, VirtualPlcHistoryInfo> converter=
				new EntityConverter<VirtualPlcHistoryEntity, VirtualPlcHistoryInfo>(){};
		return converter.entitiesToInfos(virtualPlcHistEntityList);
	}

	@Override
	@Transactional
	public List<ConnectionClientHistoryInfo> searchConnectionClientHistory(Condition cond) throws VirtualPlcException {
		log.debug("");

		ConnectionClientHistorySpec specs=new ConnectionClientHistorySpec();
		List<ConnectionClientHistoryEntity> connectionClientHistoryHistEntityList=null;
		Specification<ConnectionClientHistoryEntity> specification=specs.createSpecification(cond);
		if(cond.getPageInfo()==null) {
			connectionClientHistoryHistEntityList=connectionClientHistoryRepository.findAll();
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			connectionClientHistoryHistEntityList=connectionClientHistoryRepository.findAll(orUnrestricted(specification), prequest).getContent();
		}
		EntityConverter<ConnectionClientHistoryEntity, ConnectionClientHistoryInfo> converter=
				new EntityConverter<ConnectionClientHistoryEntity, ConnectionClientHistoryInfo>(){};

		return converter.entitiesToInfos(connectionClientHistoryHistEntityList);
	}

	@Override
	@Transactional
	public List<ConnectionInfo> searchConnections(String vplcId) throws VirtualPlcException {
		log.debug("");

		List<ConnectionEntity> connectionEntityList=virtualPlcRepository.searchConnectionsByPlcId(vplcId);
		EntityConverter<ConnectionEntity, ConnectionInfo> converter=new EntityConverter<ConnectionEntity, ConnectionInfo>(){};		
		
		return converter.entitiesToInfos(connectionEntityList);
	}

	@Override
	@Transactional
	public List<ConnectionInfo> searchConnectionsByVplcName(String vplcName) throws VirtualPlcException {
		log.debug("");
		String vplcId=this.findVirtualPlcByName(vplcName).getId();
		List<ConnectionEntity> connectionEntityList=virtualPlcRepository.searchConnectionsByPlcId(vplcId);
		EntityConverter<ConnectionEntity, ConnectionInfo> converter=new EntityConverter<ConnectionEntity, ConnectionInfo>(){};		
		
		return converter.entitiesToInfos(connectionEntityList);
	}
	
	/**
	 * 조건이 하나도 없으면 SearchSpec 이 null 을 돌려준다. Spring Data JPA 4 는 null Specification 을 받지 않으므로
	 * "조건 없음"(unrestricted)으로 바꿔 넘긴다 — 레거시(전체 조회)와 결과가 같다.
	 */
	private static <T> Specification<T> orUnrestricted(Specification<T> spec) {
		return spec != null ? spec : Specification.unrestricted();
	}

	public interface EntityConverter<T extends PersistentEntity<K>, K extends AbstractInfo> {

		public default List<K> entitiesToInfos(List<T> entityList) {
			List<K> iList=new ArrayList<>();
			for(T entity:entityList) {
				iList.add(entity.info());
			}
			return iList;
		}
	}
	
	@Override
	@Transactional
	public void saveConnectionClient(ConnectionClientInfo ccInfo) throws VirtualPlcException {
		log.debug("");
		ConnectionEntity centity=this.findConnectionEntity(ccInfo.getVplcId(), ccInfo.getPort());
		// 등록되지 않은 connection (IP, PORT) -> 이런 경우가 발생하면 안됨
		if(centity==null) return;
		
		centity.setUpdatedBy("SYSTEM");
		centity.setUpdatedDate(new Date());
		
		ConnectionClientEntity ccentity=null;

		ConnectionClientEntityId cceId=new ConnectionClientEntityId();
		cceId.setVplcId(ccInfo.getVplcId());
		cceId.setClientIp(ccInfo.getClientIp());
		cceId.setPort(ccInfo.getPort());

		switch(ccInfo.getConnOrDisconn()) {
		case "CONNECTED":
			centity.setConnected(true);
			centity.setConnectedCount(centity.getConnectedCount()+1);
			
			ccentity=new ConnectionClientEntity();
			cceId.setVplcId(ccInfo.getVplcId());
			cceId.setClientIp(ccInfo.getClientIp());
			cceId.setPort(ccInfo.getPort());
			ccentity.setConnectionClientEntityId(cceId);

			ccentity.setClientChannelId(ccInfo.getClientChannelId());
			ccentity.setConnedTimestamp(ccInfo.getConnTimestamp());
			ccentity.setCreatedBy("SYSTEM");
			ccentity.setCreatedDate(new Date());
			ccentity.setUpdatedBy("SYSTEM");
			ccentity.setUpdatedDate(new Date());
			connectionClientRepository.save(ccentity);
			break;
		case "DISCONNECTED":
			centity.setConnectedCount(centity.getConnectedCount()-1);
			if(centity.getConnectedCount()==0) {
				centity.setConnected(false);
			}
			ConnectionClientEntity delOne=findConnectionClientEntity(ccInfo.getVplcId(), ccInfo.getPort(), ccInfo.getClientIp());
			if(delOne!=null) connectionClientRepository.delete(delOne);
			break;
		default:
			log.warn("");
			return;
		}

		ConnectionClientHistoryEntity cchEntity=new ConnectionClientHistoryEntity();
		cchEntity.from(ccInfo.getConnectionClientHistoryList().get(0));
		connectionClientHistoryRepository.save(cchEntity);
		
		connectionRepository.save(centity);
	}
	
	private ConnectionEntity findConnectionEntity (String vplcId, int port) {
		ConnectionEntityId id=new ConnectionEntityId();
		id.setVplcId(vplcId);
		id.setPort(port);
		
		return connectionRepository.findById(id).get();
	}
	
	private ConnectionClientEntity findConnectionClientEntity(String vplcId, int port, String clientIp) {
		ConnectionClientEntityId id=new ConnectionClientEntityId();
		id.setVplcId(vplcId);
		id.setPort(port);
		id.setClientIp(clientIp);

		return connectionClientRepository.findById(id).get();
	}
}
