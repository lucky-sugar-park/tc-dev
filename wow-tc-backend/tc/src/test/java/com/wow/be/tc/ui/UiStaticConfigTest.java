package com.wow.be.tc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

class UiStaticConfigTest {

	@TempDir
	Path root;

	private Resource location() {
		return new FileSystemResource(root.toString().replace('\\', '/') + "/");
	}

	@Test
	void 있는_파일은_그대로_없는_경로는_index_로() throws IOException {
		Files.writeString(root.resolve("index.html"), "<html>");
		Files.writeString(root.resolve("tc-config.json"), "{}");
		Resource index = new FileSystemResource(root.resolve("index.html"));
		UiStaticConfig.SpaResolver resolver = new UiStaticConfig.SpaResolver(index);

		assertEquals("tc-config.json", resolver.getResource("tc-config.json", location()).getFilename());
		assertEquals(index, resolver.getResource("plc/connectors", location()));
	}

	@Test
	void API_내부_문서_자산_경로는_index_로_넘기지_않는다() throws IOException {
		Files.writeString(root.resolve("index.html"), "<html>");
		UiStaticConfig.SpaResolver resolver = new UiStaticConfig.SpaResolver(new FileSystemResource(root.resolve("index.html")));

		for (String path : new String[] { "api/v1/none", "internal/plc/x", "swagger-ui/index.html", "api-docs/x", "assets/none.js" }) {
			assertNull(resolver.getResource(path, location()), path);
		}
	}

	@Test
	void 화면이_없으면_넘기지_않는다() throws IOException {
		UiStaticConfig.SpaResolver resolver = new UiStaticConfig.SpaResolver(new FileSystemResource(root.resolve("index.html")));

		assertNull(resolver.getResource("plc/connectors", location()));
	}
}
