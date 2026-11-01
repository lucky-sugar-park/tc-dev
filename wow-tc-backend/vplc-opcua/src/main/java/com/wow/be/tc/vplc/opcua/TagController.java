package com.wow.be.tc.vplc.opcua;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * 가상 OPC UA 서버 REST (docs/virtual-devices.md) — 엔드포인트·세션 수, 태그 목록·더하기·지우기, 값 보기·바꾸기. 경로는 {@code Line1.Temp} 처럼.
 */
@RestController
@RequestMapping("/api/v1/opcua")
@RequiredArgsConstructor
public class TagController {

	private final VirtualOpcUaServer server;

	public record Value(Object value) {
	}

	@GetMapping
	public Map<String, Object> info() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("endpointUrl", server.endpointUrl());
		out.put("namespaceUri", VirtualOpcUaServer.NAMESPACE_URI);
		out.put("namespaceIndex", server.namespaceIndex());
		out.put("tags", server.list().size());
		out.put("sessions", server.sessionCount());
		return out;
	}

	@GetMapping("/tags")
	public List<VirtualOpcUaServer.TagView> list() {
		return server.list();
	}

	@PostMapping("/tags")
	@ResponseStatus(HttpStatus.CREATED)
	public VirtualOpcUaServer.TagView add(@RequestBody VirtualOpcUaServer.TagSpec spec) {
		return server.add(spec);
	}

	@GetMapping("/tags/{path}")
	public VirtualOpcUaServer.TagView get(@PathVariable("path") String path) {
		return server.get(path);
	}

	@PutMapping("/tags/{path}")
	public VirtualOpcUaServer.TagView set(@PathVariable("path") String path, @RequestBody Value body) {
		return server.set(path, body.value());
	}

	@DeleteMapping("/tags/{path}")
	public void remove(@PathVariable("path") String path) {
		server.remove(path);
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> notFound(NoSuchElementException e) {
		return Map.of("message", e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> bad(IllegalArgumentException e) {
		return Map.of("message", String.valueOf(e.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflict(IllegalStateException e) {
		return Map.of("message", e.getMessage());
	}
}
