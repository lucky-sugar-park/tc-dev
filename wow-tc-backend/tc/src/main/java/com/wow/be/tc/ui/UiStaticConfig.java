package com.wow.be.tc.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 화면 서빙 (docs/frontend-design.md §4.3, 결정 U6). {@code tc.ui.dir} 폴더(빌드한 화면)를 {@code /} 에서 내준다 — 화면을 바꿀 때
 * 폴더만 바꾸면 되고 tc 를 재기동하지 않는다. 비우면 서빙하지 않는다.
 *
 * <ul>
 *   <li>모르는 경로는 {@code index.html}(SPA 경로) — 단 {@code /api}·{@code /internal}·문서({@code /swagger-ui}·{@code /api-docs})는 아니다.</li>
 *   <li>{@code /assets/**}(빌드가 해시를 붙인 파일)는 1년 캐시, 나머지({@code index.html}·{@code tc-config.json})는 늘 다시 확인.</li>
 * </ul>
 * 컨트롤러가 먼저 받으므로 API 경로와 겹치지 않는다. 인증은 framework 가 {@code /api} 밖의 GET 을 열어 둔다.
 */
@Slf4j
@Configuration
public class UiStaticConfig implements WebMvcConfigurer {

	private final String dir;

	public UiStaticConfig(@Value("${tc.ui.dir:}") String dir) {
		this.dir = dir == null ? "" : dir.trim();
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		if (dir.isEmpty()) {
			return;
		}
		Path root = Path.of(dir).toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			log.warn("화면 폴더가 없습니다 — 폴더를 만들고 화면을 넣으면 재기동 없이 서빙됩니다: {}", root);
		} else {
			log.info("화면을 서빙합니다: {}", root);
		}
		// 폴더가 기동 뒤에 생겨도 되게 끝 / 를 직접 붙인다(Path.toUri 는 폴더가 있을 때만 붙인다)
		String location = "file:" + root.toString().replace('\\', '/') + "/";
		registry.addResourceHandler("/assets/**")
			.addResourceLocations(location + "assets/")
			.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
		registry.addResourceHandler("/**")
			.addResourceLocations(location)
			.setCacheControl(CacheControl.noCache())
			.resourceChain(false)
			.addResolver(new SpaResolver(root));
	}

	/** {@code /} — 자원 처리기는 빈 경로를 받지 않으므로 index.html 로 넘긴다 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		if (!dir.isEmpty()) {
			registry.addViewController("/").setViewName("forward:/index.html");
		}
	}

	/** 있는 파일은 그대로, 없으면 index.html — API·내부·문서 경로는 아니다 */
	static final class SpaResolver extends PathResourceResolver {

		private final Path root;

		SpaResolver(Path root) {
			this.root = root;
		}

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource found = super.getResource(resourcePath, location);
			if (found != null) {
				return found;
			}
			if (resourcePath.startsWith("api/") || resourcePath.startsWith("internal/") || resourcePath.startsWith("swagger-ui")
					|| resourcePath.startsWith("api-docs") || resourcePath.startsWith("assets/")) {
				return null;
			}
			FileSystemResource index = new FileSystemResource(root.resolve("index.html"));
			return index.exists() ? index : null;
		}
	}
}
