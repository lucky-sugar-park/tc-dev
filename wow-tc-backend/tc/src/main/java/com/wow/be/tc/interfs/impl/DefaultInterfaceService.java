package com.wow.be.tc.interfs.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.RequestPlanInfo;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;
import com.wow.be.tc.plc.protocol.PlannedRequest;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;
import com.wow.be.tc.plc.protocol.modbus.ModbusDriver;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultInterfaceService implements InterfaceService, PersistentService<InterfaceInfo, InterfaceEntity> {
	
	@Autowired
	private InterfaceRepository interfaceRepository;
	
	@Autowired
	private ConfigChangeRecorder changeRecorder;

	@Autowired
	private InterfacePointRepository interfacePointRepository;

	@Autowired
	private PlcConnectorManagementService plcConnectorService;

	@Override
	@Transactional
	public InterfaceInfo registerInterface(InterfaceInfo info) {
		log.debug("");
		validatePoints(info);
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
		savePoints(entity.getId(), info);
		
		changeRecorder.created(TargetType.INTERFACE, String.valueOf(entity.getId()), entity.getName(), info);
		return entity.info();
	}

	@Override
	@Transactional
	public InterfaceInfo updateInterface(InterfaceInfo info) {
		log.debug("");
		InterfaceInfo before=interfaceRepository.findById(info.getId()).map(InterfaceEntity::info).orElse(null);
		if(info.getPoints()==null && before!=null) {
			info.setPoints(before.getPoints());
		}
		// settings 를 보내지 않은 수정은 저장된 값을 그대로 쓴다
		if(info.getSettings()==null && before!=null) {
			info.setSettings(before.getSettings());
		}
		validatePoints(info);
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
		interfacePointRepository.deleteByInterfaceId(info.getId());
		savePoints(info.getId(), info);
		changeRecorder.updated(TargetType.INTERFACE, String.valueOf(info.getId()), info.getName(), before, info);
		return entity.info();
	}
	
	@Override
	@Transactional
	public InterfaceInfo applyInterfaceUse(long id, boolean use) throws ToolControlException {
		log.debug("");
		InterfaceEntity entity=requireInterfaceEntity(id);
		entity.setUse(use);
		interfaceRepository.save(entity);
		changeRecorder.record(TargetType.INTERFACE, String.valueOf(id), entity.getName(), use ? Action.ENABLE : Action.DISABLE, null, null, null);
		return entity.info();
	}
	

	@Override
	@Transactional
	public void applyInterfaceUseByBatch(List<Long> ids, boolean use) {
		log.debug("");
		interfaceRepository.findAllById(ids).forEach(e->
			changeRecorder.record(TargetType.INTERFACE, String.valueOf(e.getId()), e.getName(), use ? Action.ENABLE : Action.DISABLE, null, null, null));
		interfaceRepository.updateApplyUseByBatch(ids, use);
	}
	
	@Override
	public List<InterfaceInfo> searchInterface() {
		log.debug("");
		return infos(interfaceRepository.findAll());
	}

	@Override
	public List<InterfaceInfo> searchInterface(PageInfo pageInfo) {
		log.debug("");
		Sort.Direction dir=pageInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
		PageRequest prequest=PageRequest.of(pageInfo.getPage(), pageInfo.getSize(), dir, pageInfo.getSortBy());
		return infos(interfaceRepository.findAll(prequest).getContent());
	}

	@Override
	public List<InterfaceInfo> searchInterface(Condition cond) {
		log.debug("");
		InterfaceSpec specs=new InterfaceSpec();
		List<InterfaceEntity> interfaceEntityList=null;
		Specification<InterfaceEntity> specification=specs.createSpecification(cond);

		if(cond.getPageInfo()==null) {
			interfaceEntityList=interfaceRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Sort.Direction dir=pInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			PageRequest prequest=PageRequest.of(pInfo.getPage(), pInfo.getSize(), dir, pInfo.getSortBy());
			interfaceEntityList=interfaceRepository.findAll(specification, prequest).getContent();
		}

		return infos(interfaceEntityList);
	}
	
	@Override
	public List<InterfaceInfo> searchInterface(Condition cond, PageInfo pageInfo) {
		log.debug("");
		cond.setPageInfo(pageInfo);
		return searchInterface(cond);
	}

	@Override
	@Transactional
	public InterfaceInfo findInterfaceById(long id) throws ToolControlException {
		log.debug("");
		return requireInterfaceEntity(id).info();
	}

	private InterfaceEntity requireInterfaceEntity(long id) throws ToolControlException {
		return interfaceRepository.findById(id).orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "인터페이스가 없습니다: " + id));
	}

	@Override
	@Transactional
	public InterfaceInfo findInterfaceByName(String name) {
		log.debug("");
		if(name==null) return null;

		InterfaceEntity probe=new InterfaceEntity();
		probe.setName(name);
		
		ExampleMatcher matcher = ExampleMatcher.matchingAny();
		matcher=matcher.withMatcher("name", ExampleMatcher.GenericPropertyMatchers.exact());
		Example<InterfaceEntity> example=Example.of(probe, matcher);

		// 없으면 null — 레거시는 빈 Optional 에 get() 을 불러 NoSuchElementException 이 났다
		Optional<InterfaceEntity> result=interfaceRepository.findOne(example);
		return result.map(InterfaceEntity::info).orElse(null);
	}

	@Override
	@Transactional
	public void deleteInterface(long id) {
		log.debug("");
		interfaceRepository.findById(id).ifPresent(this::recordDeleted);
		interfaceRepository.deleteById(id);
	}

	@Override
	@Transactional
	public void deleteInterfaceByName(String name) {
		log.debug("");
		InterfaceInfo found=this.findInterfaceByName(name);
		if(found!=null) {
			changeRecorder.deleted(TargetType.INTERFACE, String.valueOf(found.getId()), found.getName(), found);
			// 엔티티로 지워야 점이 함께 지워진다 — JPQL 일괄 delete 는 cascade 를 타지 않아 외래 키 오류(500)였다
			interfaceRepository.deleteById(found.getId());
		}
	}
	
	@Override
	@Transactional
	public void deleteInterfaceByBatch(List<Long> ids) {
		log.debug("");
		// 엔티티로 지운다(cascade) — deleteInterfaceByName 과 같은 이유
		interfaceRepository.findAllById(ids).forEach(e->{
			recordDeleted(e);
			interfaceRepository.delete(e);
		});
	}

	private void recordDeleted(InterfaceEntity e) {
		changeRecorder.deleted(TargetType.INTERFACE, String.valueOf(e.getId()), e.getName(), e.info());
	}
	
	/** 인터페이스의 점을 저장한다(목록 순서가 점 순서) */
	private void savePoints(Long interfaceId, InterfaceInfo info) {
		if(info.getPoints()==null) {
			return;
		}
		for(PointInfo point:info.getPoints()) {
			interfacePointRepository.save(InterfacePointEntity.of(interfaceId, point));
		}
	}

	/**
	 * 인터페이스 검증 — READ·WRITE 이고, 연결된 커넥터의 프로토콜 드라이버가 점마다 주소·형을 본다(docs/plc-protocol-design.md §4.2).
	 * 잘못되면 IllegalArgumentException(400).
	 */
	void validatePoints(InterfaceInfo info) {
		if(info.getInterfaceType()!=InterfaceType.READ && info.getInterfaceType()!=InterfaceType.WRITE) {
			throw new IllegalArgumentException("인터페이스는 READ 또는 WRITE 입니다: " + info.getInterfaceType());
		}
		if(info.getPlcName()==null || info.getPlcName().isBlank()) {
			throw new IllegalArgumentException("인터페이스는 PLC 커넥터(plcName)가 필요합니다");
		}
		PlcConnectorInfo connector=plcConnectorService.findPlcConnectorByName(info.getPlcName());
		if(connector==null) {
			throw new IllegalArgumentException("PLC 커넥터가 없습니다: " + info.getPlcName() + " — 커넥터를 먼저 등록하세요(점 주소는 커넥터의 프로토콜로 검증한다)");
		}
		List<PointInfo> points=info.getPoints();
		if(points==null || points.isEmpty()) {
			throw new IllegalArgumentException("인터페이스에 점(points)이 없습니다");
		}
		if(points.size()>MAX_POINTS) {
			throw new IllegalArgumentException("점은 인터페이스마다 " + MAX_POINTS + "개까지입니다: " + points.size());
		}
		PlcDriver driver=PlcDrivers.of(connector.getProtocol());
		info.setSettings(driver.normalizeInterfaceSettings(info.getSettings()));
		Set<String> names=new HashSet<>();
		for(int i=0;i<points.size();i++) {
			PointInfo point=points.get(i);
			if(point==null) {
				throw new IllegalArgumentException("점 " + i + " 이 비었습니다");
			}
			driver.validatePoint(point, info.getInterfaceType());
			if(!names.add(point.getName())) {
				throw new IllegalArgumentException("점 이름이 겹칩니다: " + point.getName());
			}
			point.setOrder(i);
		}
	}

	/**
	 * 요청 미리보기(docs/interface-detail-design.md §7) — 실행과 같은 드라이버 묶기·프레임으로, 보내지 않는다. 커넥터가 게시되지 않아도 된다.
	 * 쓰기 값이 빠진 점은 형의 0 값으로 채운다.
	 */
	@Override
	public RequestPlanInfo planRequests(InterfaceInfo info, Map<String, Object> data) {
		if(info==null) {
			throw new IllegalArgumentException("인터페이스 정의(interfaceInfo)가 없습니다");
		}
		validatePoints(info);
		PlcConnectorInfo connector=plcConnectorService.findPlcConnectorByName(info.getPlcName());
		PlcDriver driver=PlcDrivers.of(connector.getProtocol());
		// 유효 설정 = 커넥터 설정 ⊕ 인터페이스 settings(validatePoints 가 정규화했다) — 실행(PlcInterfaceCommand)과 같다
		Map<String, Object> settings=driver.effectiveSettings(driver.normalizeSettings(connector.getSettings(),
			connector.getMessageFrameFormat()!=null ? connector.getMessageFrameFormat().name() : null), info.getSettings());
		boolean write=info.getInterfaceType()==InterfaceType.WRITE;
		List<String> notes=new ArrayList<>();
		Map<String, Object> values=Map.of();
		if(write) {
			values=PlannedRequest.withSampleValues(info.getPoints(), data);
			List<String> sampled=info.getPoints().stream()
				.filter(p->data==null || data.get(p.getName())==null).map(PointInfo::getName).toList();
			if(!sampled.isEmpty()) {
				notes.add("쓰기 값이 없는 점은 0(빈 값)으로 채워 보였습니다: " + String.join(", ", sampled));
			}
		}
		PlcOperation op=new PlcOperation(connector.getProtocol(), settings, write ? PlcOperation.Kind.WRITE : PlcOperation.Kind.READ,
			info.getPoints(), values, "plan");
		List<PlannedRequest> requests=driver.plan(op);
		if(MelsecDriver.PROTOCOL.equals(connector.getProtocol()) && String.valueOf(settings.get("frame")).startsWith("4E")) {
			notes.add("4E 일련번호는 보낼 때 채웁니다(미리보기는 0)");
		}
		if(ModbusDriver.PROTOCOL.equals(connector.getProtocol())) {
			notes.add("대상 번호는 전선의 0부터 번호입니다(addressBase 를 뺀 값). 트랜잭션 ID 는 보낼 때 채웁니다(미리보기는 0)");
		}
		return new RequestPlanInfo(connector.getProtocol(), connector.getName(), settings, requests, notes);
	}

	static final int MAX_POINTS=500;
}
