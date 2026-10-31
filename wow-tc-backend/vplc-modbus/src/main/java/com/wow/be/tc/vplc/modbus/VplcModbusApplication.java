package com.wow.be.tc.vplc.modbus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 가상 Modbus TCP 장치 (docs/virtual-devices.md) — REST :8091, 장치는 application.yml {@code vplc-modbus.devices} */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VplcModbusApplication {

	public static void main(String[] args) {
		SpringApplication.run(VplcModbusApplication.class, args);
	}
}
