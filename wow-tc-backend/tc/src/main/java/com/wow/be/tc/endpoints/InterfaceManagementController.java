package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.Condition;
import com.wow.be.tc.interfs.HeaderTemplateInfo;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/ifsvc/management")
@Slf4j
public class InterfaceManagementController {

	@Autowired
	private InterfaceService interfaceService;
	
	@RequestMapping(value="/register", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void registerInterface(@RequestBody InterfaceInfo iInfo) {
		log.debug("");
		interfaceService.registerInterface(iInfo);
	}
	
	@RequestMapping(value="/update", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void updateInterface(@RequestBody InterfaceInfo info) {
		log.debug("");
		interfaceService.updateInterface(info);
	}
	
	@RequestMapping(value="/update/apply-use/{id}/{use}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	public InterfaceInfo applyInterfaceUse(@PathVariable("id") long id, @PathVariable("use") boolean use) {
		log.debug("");
		return interfaceService.applyInterfaceUse(id, use);
	}
	
	@RequestMapping(value="/update/apply-use/byBatch/{use}", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void applyInterfaceUseByBatch(@RequestBody List<Long> ids, @PathVariable("use") boolean use) {
		log.debug("");
		interfaceService.applyInterfaceUseByBatch(ids, use);
	}
	
	@RequestMapping(value="/delete/{id}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteInterface(@PathVariable("id") long id) {
		log.debug("");
		interfaceService.deleteInterface(id);
	}
	
	@RequestMapping(value="/delete/byName/{name}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteInterfaceByName(@PathVariable("name") String name) {
		log.debug("");
		interfaceService.deleteInterfaceByName(name);
	}
	
	@RequestMapping(value="/delete/byBatch", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteInterfaceByBatch(@RequestBody List<Long> ids) {
		log.debug("");
		interfaceService.deleteInterfaceByBatch(ids);
	}
	
	@RequestMapping(value="/find/{id}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public InterfaceInfo findInterface(@PathVariable(value="id") long id) {
		log.debug("");
		return interfaceService.findInterfaceById(id);
	}
	
	@RequestMapping(value="/find/byName/{name}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public InterfaceInfo findInterface(@PathVariable("name") String name) {
		log.debug("");
		return interfaceService.findInterfaceByName(name);
	}
	
	@RequestMapping(value="/search", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<InterfaceInfo> searchInterface() {
		log.debug("");
		return interfaceService.searchInterface();
	}
	
	@RequestMapping(value="/search", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<InterfaceInfo> searchInterface(@RequestBody Condition cond) {
		log.debug("");
		return interfaceService.searchInterface(cond);
	}
	
	@RequestMapping(value="/search/header-templates/by-all", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<HeaderTemplateInfo> searchHeaderTemplates() {
		log.debug("");
		return interfaceService.searchHeaderTemplateByAll();
	}
}
