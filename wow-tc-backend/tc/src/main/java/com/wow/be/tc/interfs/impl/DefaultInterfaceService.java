package com.wow.be.tc.interfs.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
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
import com.wow.be.tc.interfs.PointsConversionInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PropInfo;
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

	/**
	 * 요청 미리보기(docs/interface-detail-design.md §7) — 실행과 같은 드라이버 묶기·프레임으로, 보내지 않는다. 커넥터가 게시되지 않아도 된다.
	 * 쓰기 값이 빠진 점은 형의 0 값으로 채운다.
	 */
	@Override
	public RequestPlanInfo planRequests(InterfaceInfo info, Map<String, Object> data) {
		if(info==null) {
			throw new IllegalArgumentException("인터페이스 정의(interfaceInfo)가 없습니다");
		}
		if(info.getMode()!=InterfaceMode.POINTS) {
			throw new IllegalArgumentException("RAW 인터페이스는 RAW 보기에서 헤더·데이터를 봅니다 — 요청 미리보기는 POINTS 만입니다");
		}
		validatePoints(info);
		PlcConnectorInfo connector=plcConnectorService.findPlcConnectorByName(info.getPlcName());
		PlcDriver driver=PlcDrivers.of(connector.getProtocol());
		Map<String, Object> settings=driver.normalizeSettings(connector.getSettings(),
			connector.getMessageFrameFormat()!=null ? connector.getMessageFrameFormat().name() : null);
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

	@Override
	@Transactional
	public PointsConversionInfo convertToPoints(long id, boolean dryRun) throws ToolControlException {
		return convert(requireInterfaceEntity(id).info(), dryRun);
	}

	@Override
	@Transactional
	public PointsConversionInfo convertToPointsByName(String name, boolean dryRun) throws ToolControlException {
		InterfaceInfo info=findInterfaceByName(name);
		if(info==null) {
			throw ToolControlException.of(ToolControlException.NOT_FOUND, "인터페이스가 없습니다: " + name);
		}
		return convert(info, dryRun);
	}

	/**
	 * 헤더(3E BINARY)와 데이터 속성으로 점을 만들고, 미리보기가 아니면 mode=POINTS 로 저장한다. 헤더·데이터 속성과 응답 인터페이스 이름은
	 * 그대로 둔다 — mode 를 RAW 로 되돌리면 예전대로 돈다. 변경 이력에 남는다(updateInterface).
	 */
	private PointsConversionInfo convert(InterfaceInfo info, boolean dryRun) {
		if(info.getMode()==InterfaceMode.POINTS) {
			throw new IllegalArgumentException("이미 POINTS 인터페이스입니다: " + info.getName());
		}
		PlcConnectorInfo connector=info.getPlcName()!=null ? plcConnectorService.findPlcConnectorByName(info.getPlcName()) : null;
		if(connector==null) {
			throw new IllegalArgumentException("인터페이스에 연결된 PLC 커넥터가 없습니다: " + info.getPlcName());
		}
		if(!MelsecDriver.PROTOCOL.equals(connector.getProtocol())) {
			throw new IllegalArgumentException("MELSEC 커넥터의 RAW 인터페이스만 바꿉니다: " + connector.getProtocol());
		}
		String legacy=connector.getMessageFrameFormat()!=null ? connector.getMessageFrameFormat().name() : null;
		Map<String, Object> settings=PlcDrivers.of(connector.getProtocol()).normalizeSettings(connector.getSettings(), legacy);
		InterfaceInfo reply=info.getInterfaceType()==InterfaceType.READ ? findInterfaceByName(info.getReplyName()) : null;
		RawToPointsConverter.Result result=RawToPointsConverter.convert(info, reply, settings);
		PlcDriver driver=PlcDrivers.of(connector.getProtocol());
		for(PointInfo point: result.points()) {
			driver.validatePoint(point, info.getInterfaceType());
		}
		String replyName=info.getReplyName();
		List<String> usedBy=replyName==null || replyName.isBlank() ? List.of() : interfaceRepository.findAll().stream()
			.filter(e->replyName.equals(e.getReplyName()) && !e.getName().equals(info.getName()))
			.map(InterfaceEntity::getName).sorted().toList();
		if(!dryRun) {
			info.setMode(InterfaceMode.POINTS);
			info.setPoints(result.points());
			// 헤더·데이터 속성은 바꾸지 않는다(oper 없음)
			info.getHeaderProps().forEach(p->p.setOper(null));
			info.getDataProps().forEach(p->p.setOper(null));
			updateInterface(info);
			log.info("RAW 인터페이스를 POINTS 로 바꿨습니다: {} (점 {}개)", info.getName(), result.points().size());
		}
		return new PointsConversionInfo(info.getName(), dryRun, !dryRun, result.points(), result.warnings(), replyName, usedBy);
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
