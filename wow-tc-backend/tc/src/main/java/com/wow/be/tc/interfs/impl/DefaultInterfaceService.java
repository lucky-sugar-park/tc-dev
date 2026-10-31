package com.wow.be.tc.interfs.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.interfs.HeaderTemplateInfo;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceMode;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.management.PlcConnectorManagementService;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DefaultInterfaceService implements InterfaceService, PersistentService<InterfaceInfo, InterfaceEntity> {
	
	@Autowired
	private InterfaceRepository interfaceRepository;
	
	@Autowired
	private DataPropRepository dataPropRepository;
	
	@Autowired
	private HeaderPropRepository headerPropRepository;

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
		if(info.getMode()==null) {
			info.setMode(InterfaceMode.RAW);
		}
		validatePoints(info);
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
		savePoints(entity.getId(), info);
		
		info.getHeaderProps().forEach((headerProp)->{
			headerProp.setInterfaceId(entity.getId());
			HeaderPropEntity hentity=new HeaderPropEntity();
			hentity.setInterfs(entity);
			hentity.from(headerProp);
			headerPropRepository.save(hentity);
		});
		
		info.getDataProps().forEach((dataProp)->{
			dataProp.setInterfaceId(entity.getId());
			DataPropEntity dentity=new DataPropEntity();
			dentity.setInterfs(entity);
			dentity.from(dataProp);
			dataPropRepository.save(dentity);
		});

		changeRecorder.created(TargetType.INTERFACE, String.valueOf(entity.getId()), entity.getName(), info);
		return entity.info();
	}

	@Override
	@Transactional
	public InterfaceInfo updateInterface(InterfaceInfo info) {
		log.debug("");
		InterfaceInfo before=interfaceRepository.findById(info.getId()).map(InterfaceEntity::info).orElse(null);
		if(info.getMode()==null) {
			info.setMode(before!=null?before.getMode():InterfaceMode.RAW);
		}
		if(info.getMode()==InterfaceMode.POINTS && info.getPoints()==null && before!=null) {
			info.setPoints(before.getPoints());
		}
		validatePoints(info);
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
		interfacePointRepository.deleteByInterfaceId(info.getId());
		savePoints(info.getId(), info);
		// 헤더·데이터 항목의 변경(oper=N/U/D)도 이 한 건의 변경 후 값(요청 그대로)에 들어 있다
		changeRecorder.updated(TargetType.INTERFACE, String.valueOf(info.getId()), info.getName(), before, info);
		info.getHeaderProps().forEach(header->{
			if("D".equals(header.getOper())) {
				headerPropRepository.deleteById(new PropEntityId(info.getId(), header.getName()));
			} else if("U".equals(header.getOper()) || "N".equals(header.getOper())) {
				header.setInterfaceId(info.getId());
				HeaderPropEntity hpentity=new HeaderPropEntity();
				hpentity.from(header);
				headerPropRepository.save(hpentity);
			}
		});
		
		info.getDataProps().forEach(data->{
			if("D".equals(data.getOper())) {
				dataPropRepository.deleteById(new PropEntityId(info.getId(), data.getName()));
			} else if("U".equals(data.getOper()) || "N".equals(data.getOper())) {
				data.setInterfaceId(info.getId());
				DataPropEntity dpentity=new DataPropEntity();
				dpentity.from(data);
				dataPropRepository.save(dpentity);
			}
		});
		
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
			// 엔티티로 지워야 헤더·데이터 속성·점이 함께 지워진다 — JPQL 일괄 delete 는 cascade 를 타지 않아 외래 키 오류(500)였다
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
	
	@Override
	public List<HeaderTemplateInfo> searchHeaderTemplateByAll() {
		log.debug("");
		Reader reader=new InputStreamReader(getClass().getResourceAsStream("/templates/header_props/list.json"));

		Type listType=new TypeToken<ArrayList<HeaderTemplateInfo>>() {}.getType();
		Gson gson=new GsonBuilder().setPrettyPrinting().create();
		List<HeaderTemplateInfo> templateList=gson.fromJson(reader, listType);

		Type propsListType=new TypeToken<ArrayList<PropInfo>>() {}.getType();
		templateList.forEach(template->{
			Reader r=new InputStreamReader(getClass().getResourceAsStream("/templates/header_props/"+template.getLocation()));
			template.setHeaders(gson.fromJson(r, propsListType));
		});

		return templateList;
	}

	/** POINTS 인터페이스의 점을 저장한다(목록 순서가 점 순서) */
	private void savePoints(Long interfaceId, InterfaceInfo info) {
		if(info.getMode()!=InterfaceMode.POINTS || info.getPoints()==null) {
			return;
		}
		for(PointInfo point:info.getPoints()) {
			interfacePointRepository.save(InterfacePointEntity.of(interfaceId, point));
		}
	}

	/**
	 * POINTS 인터페이스 검증 — 연결된 커넥터의 프로토콜 드라이버가 점마다 주소·형을 본다(docs/plc-protocol-design.md §4.2).
	 * 잘못되면 IllegalArgumentException(400).
	 */
	void validatePoints(InterfaceInfo info) {
		if(info.getMode()!=InterfaceMode.POINTS) {
			rejectRawOnNonMelsec(info);
			return;
		}
		if(info.getInterfaceType()!=InterfaceType.READ && info.getInterfaceType()!=InterfaceType.WRITE) {
			throw new IllegalArgumentException("POINTS 인터페이스는 READ 또는 WRITE 입니다: " + info.getInterfaceType());
		}
		if(info.getPlcName()==null || info.getPlcName().isBlank()) {
			throw new IllegalArgumentException("POINTS 인터페이스는 PLC 커넥터(plcName)가 필요합니다");
		}
		PlcConnectorInfo connector=plcConnectorService.findPlcConnectorByName(info.getPlcName());
		if(connector==null) {
			throw new IllegalArgumentException("PLC 커넥터가 없습니다: " + info.getPlcName() + " — 커넥터를 먼저 등록하세요(점 주소는 커넥터의 프로토콜로 검증한다)");
		}
		List<PointInfo> points=info.getPoints();
		if(points==null || points.isEmpty()) {
			throw new IllegalArgumentException("POINTS 인터페이스에 점(points)이 없습니다");
		}
		if(points.size()>MAX_POINTS) {
			throw new IllegalArgumentException("점은 인터페이스마다 " + MAX_POINTS + "개까지입니다: " + points.size());
		}
		PlcDriver driver=PlcDrivers.of(connector.getProtocol());
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

	/** RAW(MC 헤더·데이터 바이트 정의)는 MELSEC 전용 — 다른 프로토콜 커넥터의 READ·WRITE 인터페이스면 400 */
	private void rejectRawOnNonMelsec(InterfaceInfo info) {
		if((info.getInterfaceType()!=InterfaceType.READ && info.getInterfaceType()!=InterfaceType.WRITE)
				|| info.getPlcName()==null || info.getPlcName().isBlank()) {
			return;
		}
		PlcConnectorInfo connector=plcConnectorService.findPlcConnectorByName(info.getPlcName());
		if(connector!=null && !MelsecDriver.PROTOCOL.equals(connector.getProtocol())) {
			throw new IllegalArgumentException("RAW 인터페이스는 MELSEC 커넥터에만 씁니다 — " + info.getPlcName() + " 는 "
				+ connector.getProtocol() + " 이므로 mode=POINTS 와 points 로 정의하세요");
		}
	}

	static final int MAX_POINTS=500;
}
