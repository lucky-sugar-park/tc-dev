package com.wow.be.tc.interfs.impl;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import com.wow.be.tc.confighist.ConfigChangeInfo.Action;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeRecorder;
import com.wow.be.tc.interfs.HeaderTemplateInfo;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;
import com.wow.be.tc.interfs.PropInfo;

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

	@Override
	@Transactional
	public InterfaceInfo registerInterface(InterfaceInfo info) {
		log.debug("");
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
		
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
		InterfaceEntity entity=new InterfaceEntity();
		entity.from(info);
		interfaceRepository.save(entity);
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
	public InterfaceInfo applyInterfaceUse(long id, boolean use) {
		log.debug("");
		InterfaceEntity entity=interfaceRepository.findById(id).get();
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
	public InterfaceInfo findInterfaceById(long id) {
		log.debug("");
		return interfaceRepository.findById(id).get().info();
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
		}
		interfaceRepository.deleteByName(name);
	}
	
	@Override
	@Transactional
	public void deleteInterfaceByBatch(List<Long> ids) {
		log.debug("");
		interfaceRepository.findAllById(ids).forEach(this::recordDeleted);
		interfaceRepository.deleteByBatch(ids);
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
}
