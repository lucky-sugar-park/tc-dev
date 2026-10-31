package com.wow.be.tc.plc.remote;

import java.io.IOException;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 인스턴스 사이 호출용 별도 포트({@code tc.instance.internal-port}, 결정 H3).
 *
 * <p>값을 주면 Tomcat 커넥터를 하나 더 열고, {@code /internal/**} 는 그 포트로만, 그 포트로는 {@code /internal/**} 만 받는다
 * (다르면 404). 방화벽은 tc 인스턴스끼리만 그 포트를 열어 둔다. 0(기본)이면 같은 포트를 쓰고 LB 에서 {@code /internal/**} 를 막는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPort extends OncePerRequestFilter implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

	static final String PREFIX = "/internal/";

	private final int port;

	public InternalPort(@Value("${tc.instance.internal-port:0}") int port) {
		this.port = port;
	}

	@Override
	public void customize(TomcatServletWebServerFactory factory) {
		if (port > 0) {
			Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
			connector.setPort(port);
			factory.addAdditionalConnectors(connector);
			log.info("인스턴스 사이 호출용 포트를 엽니다: {} (/internal/** 만)", port);
		}
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (port > 0) {
			boolean internalPath = request.getRequestURI().startsWith(PREFIX);
			boolean internalPort = request.getLocalPort() == port;
			if (internalPath != internalPort) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
