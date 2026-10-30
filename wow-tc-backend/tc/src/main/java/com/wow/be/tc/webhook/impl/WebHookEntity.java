package com.wow.be.tc.webhook.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.webhook.WebHookInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Builder
@Entity
@Table(name="WEBHOOK")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class WebHookEntity extends PersistentEntity<WebHookInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Column(name="NAME", unique=true)
	private String name;
	
	@Column(name="URL")
	private String url;
	
	@Column(name="STATUS")
	private String status;
	
	@Lob
	@Column(name="SAMPLE_DATA", columnDefinition="LONGTEXT")
	private String sampleData;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	public WebHookEntity() {
		super();
	}

	@Override
	public WebHookInfo info() {
		WebHookInfo wInfo=new WebHookInfo();
		wInfo.setId(id);
		wInfo.setName(name);
		wInfo.setUrl(url);
		wInfo.setStatus(status==null?"NONE":status);
		wInfo.setSampleData(sampleData);
		wInfo.setDescription(description);
		wInfo.setCreatedBy(createdBy);
		wInfo.setCreatedDate(createdDate);
		wInfo.setUpdatedBy(updatedBy);
		wInfo.setUpdatedDate(updatedDate);

		return wInfo;
	}

	@Override
	public void from(WebHookInfo info) {
		// 등록 요청의 id 는 0(long 기본값)이다. 0 을 넣으면 Hibernate 가 없는 행을 merge 하려다 실패하므로 새 행은 null 로 둔다.
		this.setId(info.getId()>0?info.getId():null);
		this.setName(info.getName());
		this.setUrl(info.getUrl());
		this.setStatus(info.getStatus()==null?"NONE":info.getStatus());
		this.setSampleData(info.getSampleData());
		this.setDescription(info.getDescription());
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		return new StringBuffer().toString();
	}
}
