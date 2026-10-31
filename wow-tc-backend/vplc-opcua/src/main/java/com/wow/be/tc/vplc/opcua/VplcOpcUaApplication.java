package com.wow.be.tc.vplc.opcua;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/** 가상 OPC UA 서버 (docs/virtual-devices.md) — REST :8092, OPC UA :4840, 태그는 application.yml {@code vplc-opcua.tags} */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VplcOpcUaApplication {

	/** 설정 — 엔드포인트와 처음 태그 */
	@ConfigurationProperties(prefix = "vplc-opcua")
	public record Settings(String bindAddress, String hostname, Integer port, String path, List<VirtualOpcUaServer.TagSpec> tags) {

		public Settings {
			bindAddress = bindAddress != null ? bindAddress : "127.0.0.1";
			hostname = hostname != null ? hostname : "127.0.0.1";
			port = port != null ? port : 4840;
			path = path != null ? path : "/vplc";
			tags = tags != null ? tags : List.of();
		}
	}

	@Bean(destroyMethod = "close")
	VirtualOpcUaServer virtualOpcUaServer(Settings s) throws Exception {
		return new VirtualOpcUaServer(s.bindAddress(), s.hostname(), s.port(), s.path(), s.tags());
	}

	public static void main(String[] args) {
		SpringApplication.run(VplcOpcUaApplication.class, args);
	}
}
