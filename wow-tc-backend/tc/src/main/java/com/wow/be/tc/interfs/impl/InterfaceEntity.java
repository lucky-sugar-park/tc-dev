package com.wow.be.tc.interfs.impl;

import java.util.ArrayList;
import java.util.List;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.InterfaceInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Builder
@Entity
@Table(name="INTERFACE")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class InterfaceEntity extends PersistentEntity<InterfaceInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Column(name="NAME", unique=true)
	private String name;
	
	@Column(name="CMD_CLASS_NAME")
	private String commandClassName;
	
	@Column(name="USE_GENERIC_CMD_CLASS")
	private Boolean useGenericCommandClass;
	
	@Column(name="REPLY_NAME")
	private String replyName;
	
	@Column(name="PLC_NAME")
	private String plcName;
	
	@Column(name="IF_TYPE")
	private String interfaceType;
	
	@OneToMany(fetch=FetchType.LAZY, mappedBy="interfs", cascade={ CascadeType.ALL } )
//	@JoinColumn(name="INTERFACE_ID")
	private List<HeaderPropEntity> headerProps;
	
	@OneToMany(fetch=FetchType.LAZY, mappedBy="interfs", cascade={ CascadeType.ALL } )
//	@JoinColumn(name="INTERFACE_ID")
	private List<DataPropEntity> dataProps;
	
	@Column(name="DATA_LEN")
	private Integer dataLength;
	
	@Column(name="IN_USE")
	private Boolean use;
	
	@Column(name="SYNC")
	private Boolean sync;
	
	@Column(name="REPLY")
	private Boolean reply;
	
	@Column(name="WEB_HOOK_LIST")
	private String webHookNameList;
	
	@Column(name="DESCRIPTION", length=2048)
	private String description;
	
	public InterfaceEntity() {
		super();
		headerProps=new ArrayList<>();
		dataProps=new ArrayList<>();
	}

	@Override
	public InterfaceInfo info() {
		InterfaceInfo info=new InterfaceInfo();
		info.setId(this.getId()!=null && this.getId()>0?this.getId():-1);
		info.setName(this.getName());
		info.setCommandClassName(this.getCommandClassName());
		info.setUseGenericCommandClass(this.getUseGenericCommandClass()==null?false:this.getUseGenericCommandClass());
		info.setReply(this.getReply()==null?false:this.getReply());
		info.setReplyName(this.getReplyName());
		info.setPlcName(this.getPlcName());
		info.setInterfaceType(InterfaceType.valueOf(this.getInterfaceType()));
		info.setDataLength(this.getDataLength()==null?0:this.getDataLength());
		info.setSync(this.getSync()==null?false:this.getSync());
		info.setUse(this.getUse()==null?false:this.getUse());
		if(webHookNameList!=null) {
			String[] webHookNameArr=this.webHookNameList.split(",");
			for(String webHookName:webHookNameArr) {
				info.addWebHookName(webHookName.trim());
			}			
		}
		info.setDescription(this.getDescription());
		
//		headerProps.sort((a,b)->{
//			return a.getOrder()-b.getOrder();
//		});
		for(HeaderPropEntity hentity:headerProps) {
			info.addHeaderProp(hentity.info());
		}

//		dataProps.sort((a,b)->{
//			return a.getOrder()-b.getOrder();
//		});
		for(DataPropEntity dentity:dataProps) {
			info.addDataProp(dentity.info());
		}
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());
		
		return info;
	}

	@Override
	public void from(InterfaceInfo info) {
		this.setId(info.getId()>0?info.getId():null);
		
		if(info.getName()!=null && !"".equals(info.getName())) {
			this.setName(info.getName());
		}
		if(info.getCommandClassName()!=null && !"".equals(info.getCommandClassName())) {
			this.setCommandClassName(info.getCommandClassName());
		}
		
		this.setUseGenericCommandClass(info.isUseGenericCommandClass());
		this.setReply(info.isReply());
		
		if(info.getReplyName()!=null && !"".equals(info.getReplyName())) {
			this.setReplyName(info.getReplyName());
		}
		if(info.getPlcName()!=null && !"".equals(info.getPlcName())) {
			this.setPlcName(info.getPlcName());
		}
		if(info.getInterfaceType()!=null) {
			this.setInterfaceType(info.getInterfaceType().name());
		}

		this.setDataLength(info.getDataLength());
		this.setUse(info.isUse());
		this.setSync(info.isSync());

		// WebHook 이 없는 인터페이스(빈 목록)도 등록할 수 있어야 한다. 빈 목록이면 substring(0,-1) 로 예외가 났다.
		if(info.getWebHookNameList()!=null && !info.getWebHookNameList().isEmpty()) {
			StringBuilder nameList=new StringBuilder();
			for(String webHookName:info.getWebHookNameList()) {
				nameList.append(webHookName+",");
			}
			this.setWebHookNameList(nameList.substring(0, nameList.length()-1));
		} else {
			this.setWebHookNameList(null);
		}
		
		if(info.getDescription()!=null && !"".equals(info.getDescription())) {
			this.setDescription(info.getDescription());
		}
		
//		for(PropInfo pInfo : info.getHeaderProps()) {
//			HeaderPropEntity entity=new HeaderPropEntity();
//			entity.setPropId(null);
//			entity.from(pInfo);
////			entity.setInterfs(this);
//			headerProps.add(entity);
//		}
//		
//		for(PropInfo pInfo : info.getDataProps()) {
//			DataPropEntity entity=new DataPropEntity();
//			entity.setPropId(null);
//			entity.from(pInfo);
////			entity.setInterfs(this);
//			dataProps.add(entity);
//		}
		
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return new StringBuffer().toString();
	}
}
