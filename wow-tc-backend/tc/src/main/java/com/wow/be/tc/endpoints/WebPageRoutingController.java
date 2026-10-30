package com.wow.be.tc.endpoints;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/")
@Slf4j
public class WebPageRoutingController {
	
	@GetMapping("/interface")
	public RedirectView interfs(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/interface");
	}

	@GetMapping("/plc")
	public RedirectView plc(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/plc");
	}
	
	@GetMapping("/plc-controller/list")
	public RedirectView plcControllerList(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/plc-controller/list");
	}
	
	@GetMapping("/webhook")
	public RedirectView webhook(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/webhook");
	}
	
	@GetMapping("/schedule")
	public RedirectView schedule(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/schedule");
	}
	
	@GetMapping("/schedule/management")
	public RedirectView scheduleManagement(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/schedule/#/management");
	}
	
	@GetMapping("/schedule/monitoring")
	public RedirectView scheduleMonitoring(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/schedule/#/monitoring");
	}
	
	@GetMapping("/schedule/monitoring/control")
	public RedirectView scheduleMonitoringControl(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/schedule/#/monitoring/#/control");
	}
	
	@GetMapping("/schedule/monitoring/executionHistory")
	public RedirectView scheduleMonitoringExecutionHistoryl(ModelMap model) {
		log.debug("");
		return new RedirectView("redirect:#/schedule/#/monitoring/#/executionHistory");
	}
}
