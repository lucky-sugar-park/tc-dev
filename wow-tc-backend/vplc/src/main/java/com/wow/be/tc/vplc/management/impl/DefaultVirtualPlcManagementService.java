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
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcProviders;
import com.wow.be.tc.vplc.core.VirtualPlcRegistry;
import com.wow.be.tc.vplc.management.ConnectionClientHistoryInfo;
import com.wow.be.tc.vplc.management.ConnectionClientInfo;
import com.wow.be.tc.vplc.management.ConnectionInfo;
import com.wow.be.tc.vplc.management.VirtualPlcHistoryInfo;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;

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

	/** 종류별 규칙·객체 — 이 서비스는 종류를 모른다(docs/vplc-architecture.md §2.1) */
	@Autowired
	private VirtualPlcProviders providers;
	
	private VirtualPlcRegistry virtualPlcManager;
	
	public DefaultVirtualPlcManagementService () {
		virtualPlcManager=VirtualPlcRegistry.instance();
	}
	
	@Override
	@Transactional
	public void registerVirtualPlc(VirtualPlcInfo vplcInfo) throws VirtualPlcException {
		log.debug("DefaultVirtualPlcManagementService.registerVirtualPlc(VirtualPlcInfo vplcInfo)...");
		if(vplcInfo.getId()==null || "".equals(vplcInfo.getId())) {
			vplcInfo.setId(UUID.randomUUID().toString());
		}
		providers.normalize(vplcInfo);
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
		providers.normalize(vplcInfo);
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
			virtualPlcManager.release(vplcInfo.getId());
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
		log.debug("DefaultVirtualPlcManagementService.publishVirtualProgrammableController(VirtualPlcInfo vplcInfo)...");

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
		// Virtual PLC 정보 메모리로 Load — 종류별 객체(VirtualPlcProvider)
		VirtualPlc vplc=providers.create(vplcInfo);
		virtualPlcManager.register(vplc);
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
		virtualPlcManager.release(vplcInfo.getId());
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
		// 게시된 채로 지우면 포트가 열린 채 남았다 — 먼저 메모리에서 내린다. 종류별 뒷정리(OPC UA 태그)는 Provider 가
		virtualPlcManager.release(vplcId);
		java.util.Optional<VirtualPlcEntity> found=virtualPlcRepository.findById(vplcId);
		if(found.isPresent()) {
			providers.onDelete(found.get().info());
		}
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
	public void resetConnectedClients() {
		connectionClientRepository.deleteAllInBatch();
		connectionRepository.findAll().forEach(c->{
			c.setConnectedCount(0);
			c.setConnected(false);
			connectionRepository.save(c);
		});
	}

	/**
	 * 연결·끊김 이력 — 붙어 있는 연결(CONNECTED_CLIENT)·포트의 연결 수·이력(CONNECTION_CLIENT_HISTORY).
	 * 연결 이벤트 스레드에서 따로 부른다(ClientConnectionEventDispatcher) — 정지·해제 요청의 트랜잭션과 섞이지 않는다.
	 * 이미 지워진 가상 PLC·포트, 이미 없는 연결은 넘어간다(예전에는 NoSuchElementException 으로 요청까지 롤백됐다, docs/vplc-stop-error.md).
	 */
	@Override
	@Transactional
	public void saveConnectionClient(ConnectionClientInfo ccInfo) throws VirtualPlcException {
		ConnectionEntityId cid=new ConnectionEntityId();
		cid.setVplcId(ccInfo.getVplcId());
		cid.setPort(ccInfo.getPort());
		ConnectionEntity centity=connectionRepository.findById(cid).orElse(null);
		if(centity==null) {
			log.debug("연결 이력을 남길 포트가 없습니다(지워진 가상 PLC?): {} {}", ccInfo.getVplcId(), ccInfo.getPort());
			return;
		}
		// 연결 아이디가 없으면(예전 이벤트) IP 로
		String channelId=ccInfo.getClientChannelId()!=null ? ccInfo.getClientChannelId() : ccInfo.getClientIp();
		ConnectionClientEntityId key=new ConnectionClientEntityId(ccInfo.getVplcId(), ccInfo.getPort(), channelId);

		switch(ccInfo.getConnOrDisconn()) {
		case "CONNECTED": {
			ConnectionClientEntity ccentity=connectionClientRepository.findById(key).orElseGet(ConnectionClientEntity::new);
			ccentity.setConnectionClientEntityId(key);
			ccentity.setClientIp(ccInfo.getClientIp());
			ccentity.setConnedTimestamp(ccInfo.getConnTimestamp()!=null ? ccInfo.getConnTimestamp() : new Date());
			if(ccentity.getCreatedDate()==null) {
				ccentity.setCreatedBy("SYSTEM");
				ccentity.setCreatedDate(new Date());
			}
			ccentity.setUpdatedBy("SYSTEM");
			ccentity.setUpdatedDate(new Date());
			connectionClientRepository.save(ccentity);
			break;
		}
		case "DISCONNECTED":
			connectionClientRepository.findById(key).ifPresent(connectionClientRepository::delete);
			break;
		default:
			log.warn("모르는 연결 이벤트입니다: {}", ccInfo.getConnOrDisconn());
			return;
		}
		connectionClientRepository.flush();

		ConnectionClientHistoryEntity cchEntity=new ConnectionClientHistoryEntity();
		cchEntity.from(ccInfo.getConnectionClientHistoryList().get(0));
		connectionClientHistoryRepository.save(cchEntity);

		// 연결 수는 붙어 있는 연결 줄 수로(예전에는 +1·-1 이라 겹치거나 빠지면 어긋났다)
		int count=(int)connectionClientRepository.countByVplcIdAndPort(ccInfo.getVplcId(), ccInfo.getPort());
		centity.setConnectedCount(count);
		centity.setConnected(count>0);
		centity.setUpdatedBy("SYSTEM");
		centity.setUpdatedDate(new Date());
		connectionRepository.save(centity);
	}
}
