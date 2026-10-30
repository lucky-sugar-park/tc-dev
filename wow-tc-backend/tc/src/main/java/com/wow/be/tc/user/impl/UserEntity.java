package com.wow.be.tc.user.impl;

import java.util.List;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.user.UserInfo;
import com.wow.be.tc.user.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name="USER")
@Data
@EqualsAndHashCode(callSuper=false)
public class UserEntity extends PersistentEntity<UserInfo>{

	private static final long serialVersionUID = 1L;
	
	@Id
	@Column(name="ID")
	private String id;
	
	@Column(name="password")
	private String password;
	
	@Column(name="name")
	private String name;
	
	@Column(name="email")
	private String email;
	
	@Column(name="roles")
	private String roles;
	
	@Transient
	private List<UserRole> userRoles;
	
	@Column(name="phone")
	private String phone;
	
	@Column(name="extra_info")
	private String extraInfo;
	
	@Column(name="logined")
	private Boolean logined;
	
	@Column(name="logined_ip")
	private String loginedIp;
	
	@Column(name="enabled")
	private Boolean enabled;

	@Override
	public UserInfo info() {
		UserInfo uInfo=new UserInfo();
		uInfo.setId(this.getId());
		uInfo.setPassword(this.getPassword());
		uInfo.setEmail(this.getEmail());
		uInfo.setName(this.getName());
		
		String[] roleList=this.roles.split(",");
		for(String role:roleList) {
			uInfo.addRole(role);
		}

		uInfo.setPhone(this.getPhone());
		uInfo.setExtraInfo(this.getExtraInfo());
		uInfo.setEnabled(this.getEnabled()==null?false:this.getEnabled());
		
		uInfo.setCreatedBy(super.createdBy);
		uInfo.setCreatedDate(super.getCreatedDate());
		uInfo.setUpdatedBy(super.getUpdatedBy());
		uInfo.setUpdatedDate(super.getUpdatedDate());

		return uInfo;
	}

	@Override
	public void from(UserInfo info) {
		this.setId(info.getId());
		this.setPassword(info.getPassword());
		this.setName(info.getName());
		this.setEmail(info.getEmail());
		this.setPhone(info.getPhone());
		this.setExtraInfo(info.getExtraInfo());
		this.setLogined(info.getLogined());
		this.setLoginedIp(info.getLoginedIp());
		this.setEnabled(info.getEnabled()==null?false:info.getEnabled());
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
