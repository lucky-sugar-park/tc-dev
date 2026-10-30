package com.wow.be.tc.interfs;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class HeaderTemplateInfo {
	
	private long id;
	
	private String name;

	private String plcManufacturer;
	
	private String plcModel;
	
	private String protocol;
	
	private String description;
	
	private String location;
	
	private List<PropInfo> headers;
	
	public HeaderTemplateInfo() {
		headers=new ArrayList<>();
	}
	
	public void addProp(PropInfo header) {
		headers.add(header);
	}
}
