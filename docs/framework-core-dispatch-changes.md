# framework-core dispatch 수정 명세

- 작성일: 2026-10-30
- 대상: `mes-dev/wow-mes-backend/wow-mes-backend-framework-core`의 `com.wow.be.framework.dispatch`
- 작업자: 사용자가 직접 수정, 빌드, Nexus 배포. 배포가 끝나면 tc를 이 버전으로 올린다.
- 배경과 tc 쪽 계획: [dispatch-redesign.md](dispatch-redesign.md)
- mes-dev 영향: `com.wow.be.framework.dispatch`를 참조하는 곳이 framework-core 밖에는 없다(2026-10-30 grep 확인). 하위 호환을 지키지 않아도 되며, 이 명세도 호환을 지키지 않는다.

## 1. 목표

1. **커맨드는 Spring 빈**이다. DI를 받을 수 있고, 테스트에서 빈으로 바꿔 끼울 수 있다.
2. **커맨드 찾기를 앱이 바꿀 수 있다.** `CommandResolver` 빈을 추가하면 된다. tc는 이 방식으로 "인터페이스 DB에 등록된 커맨드"를 먼저 찾는다.
3. **Spring Boot 실행 jar에서도 동작한다.** 클래스패스를 직접 뒤지지 않는다.
4. **static 싱글턴과 `System.exit`를 쓰지 않는다.** 스레드 풀은 주입받는다.
5. **실패를 숨기지 않는다.** 동기는 예외로, 비동기는 실패한 `CompletableFuture`로, callback은 `onError`로 전한다.
6. **메시지 값의 타입을 지킨다.** data는 `Map<String,Object>`다.

## 2. 파일별 변경 요약

| 파일 | 조치 |
|---|---|
| `RequestMessage` | 수정: 표준 헤더 필드 + `data` |
| `ResponseMessage` | 수정: 표준 헤더 필드 + 결과 필드 + `data`, `replyTo()` 추가. `transactionid` 오타 제거 |
| `MessageCommand` | 수정: 제네릭 인터페이스, 응답을 반환 |
| `GenericMessageCommand` | **삭제** → `AbstractMessageCommand`로 대체 |
| `AbstractMessageCommand` | **신규**: 템플릿 메서드 기반 클래스 |
| `Command` | 수정: `@Component` 메타 어노테이션 추가 |
| `CommandResolver` | **신규**: 커맨드 찾기 확장 지점 |
| `AnnotatedCommandResolver` | **신규**: `@Command` 빈에서 찾기 |
| `ConfiguredCommandResolver` | **신규**: `app.dispatch.commands` 설정에서 찾기 |
| `MessageDispatcher` | 다시 작성: 리졸버 체인, 동기·비동기·callback, 인터셉터 |
| `MessageCallback` | 수정: `onError(RequestMessage, Throwable)` |
| `DispatchInterceptor` | **신규**: 디스패치 앞·뒤·오류 시점 |
| `DispatchException` | **신규**: `MesGeneralException` 상속, 오류 코드 상수 |
| `DispatchProperties` | **신규**: `@ConfigurationProperties("app.dispatch")` |
| `DispatchConfig` | **신규**: 설정값과 스레드 풀 빈 등록 (`DispatcherConfig` 대체) |
| `DispatcherConfig` | **삭제** (static map, 이상한 생성자) |
| `CommandInfo` | **삭제** → `DispatchProperties.CommandMapping`으로 대체 |
| `MessageCommandFactory` | **삭제** (static 싱글턴, 클래스패스 탐색) |
| `DispatcherCommandLoader` | **삭제** (`System.exit(0)`) |
| `DispatchTestCommand` | **삭제** → 테스트 소스로 옮김 |

## 3. 클래스 명세

### 3.1 `RequestMessage`

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestMessage implements Message {
	private String transactionId;
	private String messageId;
	private String opcode;
	private long timestamp;
	private String sender;
	private String receiver;
	private Map<String, Object> data = new HashMap<>();

	public void addData(String name, Object value) { data.put(name, value); }

	public static String generateTransactionId() { return generateId("TXR"); }
	public static String generateMessageId()     { return generateId("MSG"); }
	public static String generateId(String prefix) { return prefix + "-" + UUID.randomUUID(); }

	@Override public String toPlainText() { ... }   // 지금처럼 null 반환 금지 — 필드 나열
	@Override public String toJson()      { ... }   // Gson
}
```

- 필드 이름은 tc의 `com.wow.be.tc.msg.Message`와 같게 한다. tc 외부 시스템이 보내는 JSON 형식이 그대로 유지된다.
- 기존 `payload`(Object)는 없앤다.
- 상속해서 필드를 더할 수 있게 `final`로 만들지 않는다. tc는 `TcMessageRequest extends RequestMessage`로 `callbackUrl` 등을 더한다.

### 3.2 `ResponseMessage`

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseMessage implements Message {
	private String transactionId;      // ← 기존 transactionid 오타 수정
	private String messageId;
	private String opcode;             // ← 기존 reqOpcode
	private long timestamp;
	private String sender;
	private String receiver;

	private boolean success = true;
	private String errorCode;          // 실패 시. mes 오류 코드 규칙(ERR_404 …)을 따른다
	private String errorMessage;       // 실패 시. 한국어

	private Map<String, Object> data = new HashMap<>();   // ← 기존 Map<String,String>

	/** 요청에 대한 응답 골격: transactionId·opcode 복사, sender↔receiver 교환, 새 messageId·timestamp. */
	public static ResponseMessage replyTo(RequestMessage request) { ... }

	/** 하위 클래스용: 이미 만든 응답 객체에 같은 골격을 채운다. */
	public <R extends ResponseMessage> R fillReplyHeader(RequestMessage request) { ... }

	public void fail(String errorCode, String errorMessage) { success=false; ... }
}
```

- `resultComment`, `result`(Boolean)는 `success`, `errorCode`, `errorMessage`로 바꾼다. mes의 오류 본문 `{errorCode, message}`와 맞춘다.

#### 응답 골격 만들기 (`fillReplyHeader`, `replyTo`)

필드별 규칙:

| 응답 필드 | 값 | 이유 |
|---|---|---|
| `transactionId` | 요청 값 그대로 | 요청과 응답을 묶는 키. 감사 로그와 비동기 결과를 이것으로 찾는다 |
| `messageId` | **새로 생성** (`MSG-…`) | 메시지 하나하나의 ID라 요청과 달라야 한다 |
| `opcode` | 요청 값 그대로 | 어떤 인터페이스의 결과인지 |
| `timestamp` | **현재 시각** | 응답을 만든 시각 |
| `sender` | 요청의 `receiver` | 방향이 바뀐다 |
| `receiver` | 요청의 `sender` | 방향이 바뀐다 |
| `success` | `true` | 실패는 `fail()`로 바꾼다 |
| `errorCode`, `errorMessage` | null | |
| `data` | **비움** (복사하지 않음) | 요청 데이터를 응답에 그대로 싣지 않는다. 결과는 커맨드가 채운다 |

```java
/**
 * 요청에 대한 응답 머리(header)를 이 객체에 채운다. 하위 클래스 응답(예: TcMessageResponse)도 같은 규칙을 쓴다.
 * 받는 쪽 변수의 타입으로 반환 타입이 정해지므로, new TcMessageResponse().fillReplyHeader(req) 를
 * TcMessageResponse 변수에 바로 담을 수 있다.
 */
@SuppressWarnings("unchecked")
public <R extends ResponseMessage> R fillReplyHeader(RequestMessage request) {
	Objects.requireNonNull(request, "request");
	this.transactionId = request.getTransactionId();
	this.messageId     = RequestMessage.generateMessageId();
	this.opcode        = request.getOpcode();
	this.timestamp     = System.currentTimeMillis();
	this.sender        = request.getReceiver();
	this.receiver      = request.getSender();
	this.success       = true;
	this.errorCode     = null;
	this.errorMessage  = null;
	return (R) this;
}

/** 기본 ResponseMessage 로 충분할 때 쓰는 편의 메서드. */
public static ResponseMessage replyTo(RequestMessage request) {
	return new ResponseMessage().fillReplyHeader(request);
}

/** 실패로 표시한다. 메시지는 한국어. */
public <R extends ResponseMessage> R fail(String errorCode, String errorMessage) {
	this.success      = false;
	this.errorCode    = errorCode;
	this.errorMessage = errorMessage;
	return (R) this;
}
```

`AbstractMessageCommand`의 하위 클래스에서 쓰는 예:

```java
@Override
protected TcMessageResponse createResponse(TcMessageRequest request) {
	return new TcMessageResponse().fillReplyHeader(request);
}
```

- 하위 클래스 응답이 있으므로 static `replyTo` 하나로는 부족하다. 그래서 인스턴스 메서드 `fillReplyHeader`가 실제 규칙을 갖고, `replyTo`는 기본 타입용 편의 메서드다.
- 제네릭 반환은 타입 검사가 컴파일러가 아니라 사용하는 쪽에 맡겨진다. 다른 응답 타입의 변수에 담으면 `ClassCastException`이 난다. 안전을 우선하면 `void`로 두고 `response.fillReplyHeader(request); return response;`처럼 두 줄로 써도 된다.
- 하위 클래스에 Lombok `@Builder`를 쓰면 부모 필드가 빌더에 들어가지 않는다. tc의 `TcMessageResponse`는 `@Builder`를 빼거나 부모·자식 모두 `@SuperBuilder`로 바꿔야 한다.

### 3.3 `MessageCommand`, `AbstractMessageCommand`

```java
public interface MessageCommand<Q extends RequestMessage, R extends ResponseMessage> {
	R execute(Q request) throws MesGeneralException;
}

public abstract class AbstractMessageCommand<Q extends RequestMessage, R extends ResponseMessage>
		implements MessageCommand<Q, R> {

	@Override
	public final R execute(Q request) throws MesGeneralException {
		validate(request);
		R response = createResponse(request);   // replyTo 골격이 채워진 응답
		doExecute(request, response);
		return response;
	}

	/** 기본은 아무것도 하지 않음. 잘못된 요청이면 DispatchException(ERR_400) */
	protected void validate(Q request) throws MesGeneralException { }

	protected abstract R createResponse(Q request);

	protected abstract void doExecute(Q request, R response) throws MesGeneralException;
}
```

### 3.4 `@Command`

```java
package com.wow.be.framework.dispatch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 이 클래스가 처리하는 opcode 를 선언한다. {@code @Component} 를 메타 어노테이션으로 가지므로
 * 컴포넌트 스캔 범위 안에 있으면 따로 {@code @Component} 를 붙이지 않아도 빈이 된다.
 *
 * <p>붙인 클래스는 {@link MessageCommand} 를 구현해야 한다. {@link AnnotatedCommandResolver} 가 기동할 때 모은다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Command {

	/** 처리할 opcode 목록. 비어 있으면 기동 실패. */
	String[] opcodes();
}
```

- `@Component`를 메타 어노테이션으로 가진다. 컴포넌트 스캔 범위 안에 있으면 따로 `@Component`를 붙이지 않아도 빈이 되고 DI를 받는다.

### 3.5 `CommandResolver`와 기본 구현 2개

커맨드를 찾는 확장 지점이다. `MessageDispatcher`가 순서 값이 작은 것부터 돌려, 처음 찾은 커맨드를 쓴다.

| 리졸버 | 순서 | 어디서 찾나 | 비고 |
|---|---|---|---|
| (앱) 예: tc `InterfaceCommandResolver` | `50` | 앱이 정함 (tc는 인터페이스 DB) | 앱이 빈으로 추가 |
| `ConfiguredCommandResolver` | `100` | `app.dispatch.commands` (yml) | 설치처마다 코드 수정 없이 커맨드를 바꿔 끼운다 |
| `AnnotatedCommandResolver` | `200` | `@Command`가 붙은 빈 | 기본 경로 |

- **처음 명세와 순서가 바뀌었다.** 설정이 `@Command`보다 먼저다. 같은 opcode가 양쪽에 있으면 설정이 이긴다. mes-dev의 "설치처별 차이는 기존 코드 수정 없이 Overriding" 원칙에 맞추기 위해서다.
- **두 기본 리졸버의 공통 동작**
  - 모든 싱글턴이 만들어진 뒤(`SmartInitializingSingleton`) opcode → 커맨드 맵을 한 번 만들고, 그 뒤로 바꾸지 않는다(`Map.copyOf`, `volatile`).
  - 잘못된 매핑이면 **기동을 멈춘다**(`BeanInitializationException`). 메시지 앞에 `[오류코드]`를 붙인다. 운영 중에 엉뚱한 커맨드가 실행되는 것보다 낫다.
  - 맵은 기동이 끝난 뒤에 만들어진다. 다른 빈의 `@PostConstruct`처럼 기동 도중에 디스패치하면 이 리졸버는 아무것도 찾지 못한다.

#### `CommandResolver`

```java
package com.wow.be.framework.dispatch;

import java.util.Optional;

import org.springframework.core.Ordered;

public interface CommandResolver extends Ordered {
	Optional<MessageCommand<?, ?>> resolve(RequestMessage request);
}
```

- 이 리졸버가 처리하지 않는 opcode면 `Optional.empty()`를 돌려준다. 그러면 다음 리졸버로 넘어간다.

#### `AnnotatedCommandResolver`

```java
package com.wow.be.framework.dispatch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link Command} 가 붙은 빈에서 커맨드를 찾는다.
 *
 * <p>모든 싱글턴 빈이 만들어진 뒤({@link SmartInitializingSingleton}) opcode → 커맨드 맵을 한 번 만들고, 그 뒤로는 바꾸지 않는다.
 * 다음 경우에는 기동을 멈춘다 — 잘못된 매핑으로 운영 중에 엉뚱한 커맨드가 실행되는 것보다 낫다.
 * <ul>
 *   <li>같은 opcode 를 두 빈이 선언함</li>
 *   <li>{@code @Command} 를 붙인 빈이 {@link MessageCommand} 를 구현하지 않음</li>
 *   <li>opcode 가 비어 있음</li>
 * </ul>
 *
 * <p>맵은 기동이 끝난 뒤에 만들어진다. 다른 빈의 {@code @PostConstruct} 처럼 기동 도중에 디스패치하면 이 리졸버는
 * 아무것도 찾지 못한다.
 *
 * <p>순서는 {@link #ORDER}(200). 설정 매핑({@link ConfiguredCommandResolver}, 100)이 먼저다 — 설치처마다 코드 수정 없이
 * yml 로 커맨드를 바꿔 끼울 수 있게 하기 위해서다.
 */
@Slf4j
@Component
public class AnnotatedCommandResolver implements CommandResolver, SmartInitializingSingleton {

	public static final int ORDER = 200;

	private final ApplicationContext applicationContext;

	private volatile Map<String, MessageCommand<?, ?>> commands = Map.of();

	public AnnotatedCommandResolver(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterSingletonsInstantiated() {
		Map<String, MessageCommand<?, ?>> found = new HashMap<>();
		Map<String, String> ownerBeanNames = new HashMap<>();

		for (String beanName : applicationContext.getBeanNamesForAnnotation(Command.class)) {
			// AOP 프록시여도 원래 클래스의 어노테이션을 읽는다
			Command annotation = applicationContext.findAnnotationOnBean(beanName, Command.class);
			Object bean = applicationContext.getBean(beanName);

			if (!(bean instanceof MessageCommand<?, ?> command)) {
				throw new BeanInitializationException(String.format(
					"[%s] @Command 를 붙인 빈이 MessageCommand 를 구현하지 않습니다: %s (%s)",
					DispatchException.EXECUTION_FAILED, beanName, bean.getClass().getName()));
			}
			if (annotation == null || annotation.opcodes().length == 0) {
				throw new BeanInitializationException(String.format(
					"[%s] @Command 에 opcode 가 없습니다: %s",
					DispatchException.EXECUTION_FAILED, beanName));
			}

			for (String opcode : annotation.opcodes()) {
				if (!StringUtils.hasText(opcode)) {
					throw new BeanInitializationException(String.format(
						"[%s] @Command 에 빈 opcode 가 있습니다: %s",
						DispatchException.EXECUTION_FAILED, beanName));
				}
				String previous = ownerBeanNames.putIfAbsent(opcode, beanName);
				if (previous != null) {
					throw new BeanInitializationException(String.format(
						"[%s] 같은 opcode 를 처리하는 커맨드가 둘 이상입니다: %s (%s, %s)",
						DispatchException.DUPLICATE_OPCODE, opcode, previous, beanName));
				}
				found.put(opcode, command);
			}
		}

		this.commands = Map.copyOf(found);
		log.info("@Command 커맨드 {}개를 등록했습니다: {}", found.size(), ownerBeanNames);
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		return Optional.ofNullable(commands.get(request.getOpcode()));
	}

	@Override
	public int getOrder() {
		return ORDER;
	}
}
```

- `getBeanNamesForAnnotation`과 `findAnnotationOnBean`을 쓴다. 실행 jar에서도 동작하고, AOP 프록시가 씌워진 빈도 원래 클래스의 어노테이션을 읽는다.
- 기동을 멈추는 경우: 같은 opcode를 두 빈이 선언함(`ERR_500_DISPATCH_DUPLICATE`), `MessageCommand`를 구현하지 않음, opcode가 비어 있음

#### `ConfiguredCommandResolver`

```java
package com.wow.be.framework.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 설정({@code app.dispatch.commands})에서 커맨드를 찾는다. {@code @Command} 를 붙일 수 없는 커맨드
 * (다른 jar 의 클래스 등)나, 설치처마다 커맨드를 바꿔 끼울 때 쓴다.
 *
 * <pre>
 * app:
 *   dispatch:
 *     commands:
 *       - opcode: some.opcode
 *         command: someCommandBean          # 빈 이름
 *       - opcode: other.opcode
 *         command: com.acme.OtherCommand    # 클래스 FQN
 * </pre>
 *
 * <p>{@code command} 해석 규칙
 * <ol>
 *   <li>그 이름의 빈이 있으면 그 빈</li>
 *   <li>아니면 클래스 FQN 으로 보고, 그 타입의 빈이 하나 있으면 그 빈 (둘 이상이면 기동 실패)</li>
 *   <li>그 타입의 빈이 없으면 {@link AutowireCapableBeanFactory#createBean(Class)} 로 DI·초기화된 인스턴스를 만든다.
 *       이 인스턴스는 컨텍스트가 닫힐 때 이 리졸버가 정리한다.</li>
 * </ol>
 *
 * <p>모든 매핑은 기동할 때 한 번 해석한다. 클래스를 찾을 수 없거나, MessageCommand 가 아니거나, 같은 opcode 가 두 번
 * 나오면 기동을 멈춘다.
 *
 * <p>순서는 {@link #ORDER}(100) — {@link AnnotatedCommandResolver}(200)보다 먼저다. 같은 opcode 가 {@code @Command} 에도
 * 있으면 설정이 이긴다.
 */
@Slf4j
@Component
public class ConfiguredCommandResolver implements CommandResolver, SmartInitializingSingleton, DisposableBean {

	public static final int ORDER = 100;

	private final ApplicationContext applicationContext;

	private final DispatchProperties properties;

	private volatile Map<String, MessageCommand<?, ?>> commands = Map.of();

	/** createBean 으로 직접 만든 인스턴스 — 컨텍스트가 관리하지 않으므로 destroy 에서 정리한다 */
	private final List<Object> createdInstances = new ArrayList<>();

	public ConfiguredCommandResolver(ApplicationContext applicationContext, DispatchProperties properties) {
		this.applicationContext = applicationContext;
		this.properties = properties;
	}

	@Override
	public void afterSingletonsInstantiated() {
		// 1) 설정 자체를 먼저 검증한다 — 커맨드 인스턴스를 하나도 만들기 전에 설정 오류를 알린다
		Map<String, String> summary = new LinkedHashMap<>();
		for (DispatchProperties.CommandMapping mapping : properties.getCommands()) {
			String opcode = mapping.getOpcode();
			String commandName = mapping.getCommand();
			if (!StringUtils.hasText(opcode) || !StringUtils.hasText(commandName)) {
				throw new BeanInitializationException(String.format(
					"[%s] app.dispatch.commands 에 opcode 나 command 가 비어 있는 항목이 있습니다: opcode=%s, command=%s",
					DispatchException.EXECUTION_FAILED, opcode, commandName));
			}
			if (summary.putIfAbsent(opcode, commandName.trim()) != null) {
				throw new BeanInitializationException(String.format(
					"[%s] app.dispatch.commands 에 같은 opcode 가 두 번 있습니다: %s",
					DispatchException.DUPLICATE_OPCODE, opcode));
			}
		}

		// 2) 커맨드를 찾거나 만든다
		Map<String, MessageCommand<?, ?>> found = new HashMap<>();
		summary.forEach((opcode, commandName) -> found.put(opcode, toCommand(opcode, commandName)));

		this.commands = Map.copyOf(found);
		if (!found.isEmpty()) {
			log.info("설정 커맨드 {}개를 등록했습니다: {}", found.size(), summary);
		}
	}

	private MessageCommand<?, ?> toCommand(String opcode, String commandName) {
		Object instance;
		if (applicationContext.containsBean(commandName)) {
			instance = applicationContext.getBean(commandName);
		} else {
			Class<?> commandClass = loadClass(opcode, commandName);
			String[] beanNames = applicationContext.getBeanNamesForType(commandClass);
			if (beanNames.length > 1) {
				throw new BeanInitializationException(String.format(
					"[%s] 커맨드 타입의 빈이 둘 이상이라 하나를 고를 수 없습니다. 빈 이름으로 지정해 주세요: opcode=%s, %s → %s",
					DispatchException.EXECUTION_FAILED, opcode, commandName, String.join(", ", beanNames)));
			}
			if (beanNames.length == 1) {
				instance = applicationContext.getBean(beanNames[0]);
			} else {
				instance = applicationContext.getAutowireCapableBeanFactory().createBean(commandClass);
				synchronized (createdInstances) {
					createdInstances.add(instance);
				}
			}
		}

		if (!(instance instanceof MessageCommand<?, ?> command)) {
			throw new BeanInitializationException(String.format(
				"[%s] 설정한 커맨드가 MessageCommand 를 구현하지 않습니다: opcode=%s, %s (%s)",
				DispatchException.EXECUTION_FAILED, opcode, commandName, instance.getClass().getName()));
		}
		return command;
	}

	private Class<?> loadClass(String opcode, String className) {
		try {
			return ClassUtils.forName(className, applicationContext.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			throw new BeanInitializationException(String.format(
				"[%s] 설정한 커맨드가 빈 이름도 아니고 클래스도 찾을 수 없습니다: opcode=%s, %s",
				DispatchException.EXECUTION_FAILED, opcode, className), e);
		}
	}

	@Override
	public Optional<MessageCommand<?, ?>> resolve(RequestMessage request) {
		return Optional.ofNullable(commands.get(request.getOpcode()));
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	@Override
	public void destroy() {
		AutowireCapableBeanFactory beanFactory = applicationContext.getAutowireCapableBeanFactory();
		synchronized (createdInstances) {
			for (Object instance : createdInstances) {
				try {
					beanFactory.destroyBean(instance);
				} catch (RuntimeException e) {
					log.warn("설정 커맨드 정리 중 오류가 났습니다: {}", instance.getClass().getName(), e);
				}
			}
			createdInstances.clear();
		}
	}
}
```

- **`command` 해석 순서**
  1. 그 이름의 빈
  2. 클래스 FQN이고 그 타입의 빈이 하나면 그 빈. 둘 이상이면 기동 실패
  3. 그 타입의 빈이 없으면 `createBean`으로 DI와 초기화(`@PostConstruct`)를 마친 인스턴스를 만든다. 이 인스턴스는 컨텍스트가 관리하지 않으므로 리졸버가 `destroy()`에서 정리한다(`@PreDestroy` 호출).
- **검증과 생성을 두 단계로 나눈다.** 빈 항목과 중복 opcode 같은 설정 오류를 커맨드 인스턴스를 하나도 만들기 전에 먼저 알린다.
- 기동을 멈추는 경우: 빈 항목, 같은 opcode가 두 번(`ERR_500_DISPATCH_DUPLICATE`), 클래스를 찾을 수 없음, `MessageCommand`가 아님, 같은 타입 빈이 둘 이상

### 3.6 `MessageDispatcher`

생성자는 리졸버 목록(`List<CommandResolver>`, 순서로 정렬), 인터셉터(`ObjectProvider<DispatchInterceptor>`, 없으면 빈 목록), `dispatchTaskExecutor`를 주입받는다. 공개 메서드는 동기 `dispatch(request)`, 비동기 `dispatchAsync(request)`, 콜백 `dispatch(request, callback)` 세 개다.

**처리 순서 (`dispatch` 기준)**
1. 요청 검증: 요청이 없거나 `opcode`가 비어 있으면 `ERR_400`
2. 헤더 보충: `transactionId`, `messageId`가 비어 있으면 생성하고, `timestamp`가 0 이하면 현재 시각을 넣는다.
3. 인터셉터 `preDispatch(request)`
4. 리졸버를 순서대로 돌려 처음 찾은 커맨드를 쓴다. 아무도 못 찾으면 `ERR_404_DISPATCH_COMMAND`, 메시지 "요청을 처리할 커맨드가 없습니다: {opcode}"
5. `command.execute(request)`. 응답이 null이면 `ERR_500_DISPATCH`
6. 인터셉터 `postDispatch(request, response)`
7. 3~5에서 예외가 나면 인터셉터 `onError(request, ex)`를 호출한 뒤 예외를 다시 던진다. `MesGeneralException`이 아니면 `ERR_500_DISPATCH`로 감싼다. 원인 예외는 cause로 남긴다.

- 1~2단계의 실패는 인터셉터를 거치지 않는다. 요청 자체가 성립하지 않기 때문이다.
- 비동기(`dispatchAsync`)는 1~2단계를 **호출 스레드에서** 먼저 한다. 그래서 호출자가 생성된 `transactionId`를 바로 쓸 수 있다(예: `202 Accepted` 응답에 담기).
- `dispatchAsync`는 예외를 던지지 않는다. 모든 실패는 future로 전하고, future의 예외는 `CompletionException`으로 감싸지 않은 `MesGeneralException`이다. 큐 포화로 제출이 거절되면 인터셉터 `onError`도 호출된다.
- 실행 중에는 MDC에 `transactionId`, `opcode`를 넣는다. 로그 패턴에 `%X{transactionId}`를 쓰면 요청 단위로 묶어 볼 수 있다.
- 디스패처는 opcode만 보고 커맨드를 고른다. 커맨드가 기대하는 하위 요청 타입(예: `TcMessageRequest`)과 실제 요청 타입이 다르면 실행 중 `ClassCastException`이 나고 `ERR_500_DISPATCH`로 보고된다. 요청 타입을 맞추는 것은 앱의 책임이다.

**비동기 스레드의 컨텍스트 전파**
- `dispatchTaskExecutor`에 `TaskDecorator`를 붙여 호출 스레드의 `SecurityContext`와 MDC를 작업 스레드로 복사한다.
- 이렇게 해야 `PersistentEntity`의 작성자 필드와 로그 추적이 비동기에서도 유지된다.

#### 전체 코드

```java
package com.wow.be.framework.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wow.be.framework.MesGeneralException;

import lombok.extern.slf4j.Slf4j;

/**
 * opcode 로 커맨드를 찾아 실행한다.
 *
 * <p>처리 순서
 * <ol>
 *   <li>요청 검증 — 요청이 없거나 opcode 가 비어 있으면 {@code ERR_400}</li>
 *   <li>헤더 보충 — 비어 있는 transactionId·messageId 생성, timestamp 가 0 이면 현재 시각</li>
 *   <li>{@link DispatchInterceptor#preDispatch}</li>
 *   <li>{@link CommandResolver} 를 순서대로 돌려 처음 찾은 커맨드 선택 — 아무도 못 찾으면 {@code ERR_404_DISPATCH_COMMAND}</li>
 *   <li>{@link MessageCommand#execute}</li>
 *   <li>{@link DispatchInterceptor#postDispatch} — 3~5 에서 실패하면 대신 {@link DispatchInterceptor#onError}</li>
 * </ol>
 * 1·2 단계의 실패는 인터셉터를 거치지 않는다(요청 자체가 성립하지 않으므로).
 *
 * <p>실패는 항상 {@link MesGeneralException} 으로 전한다. 프레임워크 밖 예외(NPE 등)는 {@code ERR_500_DISPATCH} 로
 * 감싸고 원래 예외는 {@code getCause()} 로 남긴다.
 *
 * <p>커맨드는 요청 타입을 제네릭으로 선언하지만, 디스패처는 opcode 만 보고 커맨드를 고른다. 커맨드가 기대하는
 * 하위 요청 타입(예: TcMessageRequest)과 실제 요청 타입이 다르면 실행 중 {@code ClassCastException} 이 나고
 * {@code ERR_500_DISPATCH} 로 보고된다 — 요청 타입을 맞추는 것은 앱의 책임이다.
 */
@Slf4j
@Component
public class MessageDispatcher {

	private static final String MDC_TRANSACTION_ID = "transactionId";

	private static final String MDC_OPCODE = "opcode";

	private final List<CommandResolver> resolvers;

	private final List<DispatchInterceptor> interceptors;

	private final Executor executor;

	public MessageDispatcher(
			List<CommandResolver> resolvers,
			ObjectProvider<DispatchInterceptor> interceptors,
			@Qualifier(DispatchConfig.DISPATCH_TASK_EXECUTOR) Executor executor) {
		List<CommandResolver> sorted = new ArrayList<>(resolvers);
		AnnotationAwareOrderComparator.sort(sorted);
		this.resolvers = List.copyOf(sorted);
		this.interceptors = interceptors.orderedStream().toList();
		this.executor = executor;

		log.info("MessageDispatcher 준비 완료 — 리졸버: {}, 인터셉터: {}",
			this.resolvers.stream().map(r -> r.getClass().getSimpleName()).toList(),
			this.interceptors.stream().map(i -> i.getClass().getSimpleName()).toList());
	}

	// ── 동기 ─────────────────────────────────────────────────────────

	/**
	 * 호출 스레드에서 실행하고 응답을 돌려준다.
	 *
	 * @param request 요청. 비어 있는 헤더는 이 메서드가 채운다.
	 * @return 커맨드가 돌려준 응답 (업무 실패면 {@code success=false})
	 * @throws MesGeneralException 응답을 만들지 못했을 때
	 */
	public <R extends ResponseMessage> R dispatch(RequestMessage request) throws MesGeneralException {
		prepare(request);
		return execute(request);
	}

	// ── 비동기 ───────────────────────────────────────────────────────

	/**
	 * {@code dispatchTaskExecutor} 에서 실행한다. 이 메서드는 예외를 던지지 않는다 — 모든 실패는 future 로 전한다.
	 * future 의 예외는 {@link CompletionException} 으로 감싸지지 않은 {@link MesGeneralException} 이다
	 * ({@code future.get()} 에서는 {@link ExecutionException} 의 cause 로 나온다).
	 *
	 * @param request 요청. 비어 있는 헤더는 호출 스레드에서 미리 채운다 — 호출자가 transactionId 를 바로 쓸 수 있다.
	 */
	public <R extends ResponseMessage> CompletableFuture<R> dispatchAsync(RequestMessage request) {
		try {
			prepare(request);
		} catch (MesGeneralException e) {
			return CompletableFuture.failedFuture(e);
		}

		CompletableFuture<R> future = new CompletableFuture<>();
		try {
			executor.execute(() -> {
				try {
					future.complete(execute(request));
				} catch (MesGeneralException e) {
					future.completeExceptionally(e);
				} catch (RuntimeException e) {          // execute 가 이미 감싸지만, 혹시 모를 누락을 막는다
					future.completeExceptionally(toMesException(e));
				}
			});
		} catch (RejectedExecutionException e) {      // Spring TaskRejectedException 도 여기로 온다
			MesGeneralException rejected = toMesException(e);
			notifyError(request, rejected);
			future.completeExceptionally(rejected);
		}
		return future;
	}

	/**
	 * 비동기로 실행하고 결과를 콜백으로 알린다. {@link MessageCallback#onCompleted} 와
	 * {@link MessageCallback#onError} 중 정확히 하나가 한 번 호출된다. 콜백 안의 예외는 경고 로그만 남긴다.
	 */
	public <R extends ResponseMessage> void dispatch(RequestMessage request, MessageCallback<R> callback) {
		Objects.requireNonNull(callback, "callback");
		this.<R>dispatchAsync(request).whenComplete((response, error) -> {
			try {
				if (error == null) callback.onCompleted(response);
				else callback.onError(request, toMesException(error));
			} catch (RuntimeException callbackError) {
				log.warn("디스패치 콜백 처리 중 오류가 났습니다: opcode={}, transactionId={}",
					opcodeOf(request), transactionIdOf(request), callbackError);
			}
		});
	}

	// ── 내부 ─────────────────────────────────────────────────────────

	/** 1·2 단계: 검증과 헤더 보충. 인터셉터를 거치지 않는다. */
	private void prepare(RequestMessage request) throws MesGeneralException {
		if (request == null) {
			throw new DispatchException(DispatchException.INVALID_REQUEST, "요청이 비어 있습니다.");
		}
		if (!StringUtils.hasText(request.getOpcode())) {
			throw new DispatchException(DispatchException.INVALID_REQUEST, "요청에 opcode 가 없습니다.");
		}
		if (!StringUtils.hasText(request.getTransactionId())) {
			request.setTransactionId(RequestMessage.generateTransactionId());
		}
		if (!StringUtils.hasText(request.getMessageId())) {
			request.setMessageId(RequestMessage.generateMessageId());
		}
		if (request.getTimestamp() <= 0) {
			request.setTimestamp(System.currentTimeMillis());
		}
	}

	/** 3~6 단계: 인터셉터·커맨드 실행. prepare 를 거친 요청만 들어온다. */
	private <R extends ResponseMessage> R execute(RequestMessage request) throws MesGeneralException {
		MDC.put(MDC_TRANSACTION_ID, request.getTransactionId());
		MDC.put(MDC_OPCODE, request.getOpcode());
		try {
			for (DispatchInterceptor interceptor : interceptors) {
				interceptor.preDispatch(request);
			}

			MessageCommand<RequestMessage, R> command = resolve(request);
			log.debug("커맨드 실행: opcode={}, command={}", request.getOpcode(), command.getClass().getName());

			R response = command.execute(request);
			if (response == null) {
				throw new DispatchException(DispatchException.EXECUTION_FAILED,
					"커맨드가 응답을 돌려주지 않았습니다: " + request.getOpcode());
			}

			notifyPost(request, response);
			return response;
		} catch (MesGeneralException | RuntimeException e) {
			MesGeneralException cause = toMesException(e);
			notifyError(request, cause);
			throw cause;
		} finally {
			MDC.remove(MDC_TRANSACTION_ID);
			MDC.remove(MDC_OPCODE);
		}
	}

	@SuppressWarnings("unchecked")
	private <R extends ResponseMessage> MessageCommand<RequestMessage, R> resolve(RequestMessage request)
			throws DispatchException {
		for (CommandResolver resolver : resolvers) {
			Optional<MessageCommand<?, ?>> command = resolver.resolve(request);
			if (command.isPresent()) {
				return (MessageCommand<RequestMessage, R>) command.get();
			}
		}
		throw new DispatchException(DispatchException.COMMAND_NOT_FOUND,
			"요청을 처리할 커맨드가 없습니다: " + request.getOpcode());
	}

	private void notifyPost(RequestMessage request, ResponseMessage response) {
		for (DispatchInterceptor interceptor : interceptors) {
			try {
				interceptor.postDispatch(request, response);
			} catch (RuntimeException e) {
				log.warn("디스패치 인터셉터(postDispatch) 처리 중 오류가 났습니다: interceptor={}, opcode={}",
					interceptor.getClass().getName(), request.getOpcode(), e);
			}
		}
	}

	private void notifyError(RequestMessage request, MesGeneralException cause) {
		for (DispatchInterceptor interceptor : interceptors) {
			try {
				interceptor.onError(request, cause);
			} catch (RuntimeException e) {
				log.warn("디스패치 인터셉터(onError) 처리 중 오류가 났습니다: interceptor={}, opcode={}",
					interceptor.getClass().getName(), request.getOpcode(), e);
			}
		}
	}

	/** 어떤 예외든 MesGeneralException 으로 정리한다. CompletionException·ExecutionException 은 벗긴다. */
	static MesGeneralException toMesException(Throwable error) {
		Throwable t = error;
		while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
			t = t.getCause();
		}
		if (t instanceof MesGeneralException mes) {
			return mes;
		}
		if (t instanceof RejectedExecutionException) {
			return new DispatchException(DispatchException.REJECTED,
				"요청이 많아 지금은 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", t);
		}
		return new DispatchException(DispatchException.EXECUTION_FAILED, "요청을 처리하지 못했습니다.", t);
	}

	private static String opcodeOf(RequestMessage request) {
		return request != null ? request.getOpcode() : null;
	}

	private static String transactionIdOf(RequestMessage request) {
		return request != null ? request.getTransactionId() : null;
	}
}
```

### 3.7 `MessageCallback`

**지금 코드**

```java
public interface MessageCallback<T extends ResponseMessage> {
	public void onCompleted(T response);
	public void onError(T response, Throwable t);
}
```

지금은 디스패처가 실패하면 `new ResponseMessage()`를 직접 만들어 `onError`에 넘긴다. 바꾼 뒤에는 응답 타입 `R`을 커맨드가 정하므로(`TcMessageResponse` 등) 디스패처가 `R`을 만들 수 없다. 또 다음 경우에는 응답 객체가 아예 생기지 않는다.
- 커맨드를 못 찾음
- 스레드 풀이 가득 참
- `validate`에서 실패
- `createResponse` 전에 예외가 남

그래서 `onError`는 **요청과 원인**을 받는다.

**바꾼 뒤**

```java
/**
 * 비동기 디스패치 결과를 받는다. 한 번의 디스패치에 onCompleted 와 onError 중 **정확히 하나만** 호출된다.
 * 호출 스레드는 dispatchTaskExecutor 의 작업 스레드다 — 오래 걸리는 일은 여기서 하지 않는다.
 */
public interface MessageCallback<R extends ResponseMessage> {

	/** 커맨드가 정상 종료했다. response.isSuccess() 가 false 일 수도 있다(커맨드가 fail() 로 업무 실패를 표시한 경우). */
	void onCompleted(R response);

	/**
	 * 커맨드가 응답을 돌려주지 못했다.
	 * @param request 원래 요청 — transactionId·opcode·sender 로 누구에게 무엇을 알릴지 판단한다
	 * @param cause   항상 MesGeneralException(대개 DispatchException). 프레임워크 밖 예외는 ERR_500_DISPATCH 로 감싸고
	 *                원래 예외는 getCause() 로 남는다. CompletionException 은 벗겨서 넘긴다.
	 */
	void onError(RequestMessage request, MesGeneralException cause);
}
```

- `cause` 타입을 `Throwable`이 아니라 `MesGeneralException`으로 좁힌다. 받는 쪽이 `cause.getErrorCode()`를 바로 읽을 수 있다.
- 응답 모양이 필요한 쪽은 직접 만든다: `ResponseMessage.replyTo(request).fail(cause.getErrorCode(), cause.getMessage())`

**디스패처 쪽 구현**

```java
public <R extends ResponseMessage> void dispatch(RequestMessage request, MessageCallback<R> callback) {
	Objects.requireNonNull(callback, "callback");
	CompletableFuture<R> future;
	try {
		future = dispatchAsync(request);
	} catch (RuntimeException e) {                       // 제출 단계 실패(예: 큐 포화)도 콜백으로 보낸다
		future = CompletableFuture.failedFuture(e);
	}
	future.whenComplete((response, error) -> {
		try {
			if (error == null) callback.onCompleted(response);
			else               callback.onError(request, toMesException(error));
		} catch (RuntimeException callbackError) {       // 콜백 안의 예외가 디스패치 결과를 바꾸지 않게 한다
			log.warn("디스패치 콜백 처리 중 오류가 났습니다: opcode={}, transactionId={}",
				request.getOpcode(), request.getTransactionId(), callbackError);
		}
	});
}

private static MesGeneralException toMesException(Throwable error) {
	Throwable t = (error instanceof CompletionException || error instanceof ExecutionException)
		&& error.getCause() != null ? error.getCause() : error;
	if (t instanceof MesGeneralException mes) return mes;
	if (t instanceof RejectedExecutionException)
		return new DispatchException(DispatchException.REJECTED, "요청이 많아 지금은 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", t);
	return new DispatchException(DispatchException.EXECUTION_FAILED, "요청을 처리하지 못했습니다.", t);
}
```

- `dispatchAsync`도 같은 `toMesException`으로 예외를 정리해서 future를 끝낸다. 그래야 future를 직접 쓰는 쪽과 콜백을 쓰는 쪽이 같은 예외를 본다.
- `onCompleted` 안에서 예외가 나도 `onError`를 다시 부르지 않는다. "정확히 하나만" 규칙을 지키기 위해서다.

**tc에서 쓰는 예 (`/request` 비동기 요청의 결과 전달)**

```java
messageDispatcher.dispatch(request, new MessageCallback<TcMessageResponse>() {
	@Override public void onCompleted(TcMessageResponse response) {
		deliveryService.deliver(request, response);                 // 인터페이스에 정의된 채널로 전달
	}
	@Override public void onError(RequestMessage req, MesGeneralException cause) {
		TcMessageResponse failure = new TcMessageResponse().fillReplyHeader(req);
		failure.fail(cause.getErrorCode(), cause.getMessage());
		deliveryService.deliver(request, failure);                  // 실패도 같은 채널로 알린다
	}
});
```

**테스트에 추가할 것** (4절 표의 "비동기 실패" 항목을 나눈다)
- 커맨드 예외 → `onError` 1번, `onCompleted` 0번. `cause.getErrorCode()`가 커맨드가 던진 코드다.
- 커맨드 없음 → `onError`의 cause가 `ERR_404_DISPATCH_COMMAND`
- 큐 포화 → `onError`의 cause가 `ERR_503_DISPATCH_REJECTED`. 제출 단계에서 실패해도 예외가 호출자에게 던져지지 않고 콜백으로 온다
- `RuntimeException`(예: NPE) → cause가 `ERR_500_DISPATCH`이고 `getCause()`가 원래 NPE
- `onCompleted`에서 예외 → `onError`가 호출되지 않고 경고 로그만 남는다

### 3.8 `DispatchInterceptor`

- 메서드는 `preDispatch(request)`, `postDispatch(request, response)`, `onError(request, MesGeneralException cause)` 세 개이고 모두 기본 구현이 있다. 필요한 것만 override한다.
- `onError`의 두 번째 인자는 `MessageCallback.onError`와 같은 `MesGeneralException`이다.
- `preDispatch`에서 예외를 던지면 디스패치가 중단되고 `onError`가 호출된다.
- `postDispatch`와 `onError`에서 예외가 나도 디스패치 결과는 바꾸지 않는다. 로그만 남긴다.
- 한 번의 디스패치에서 `postDispatch`와 `onError` 중 하나만 호출된다.
- tc는 이 지점에 감사 기록(요청, 응답, 실패)과 SSE를 연결한다.

#### 전체 코드

```java
package com.wow.be.framework.dispatch;

import org.springframework.core.Ordered;

import com.wow.be.framework.MesGeneralException;

/**
 * 디스패치 앞·뒤·오류 시점에 끼어드는 지점. 감사 기록, SSE 알림, 로그 추적 같은 공통 처리를 커맨드 밖에 둔다.
 *
 * <p>빈으로 등록하면 {@link MessageDispatcher} 가 {@link #getOrder()} 순서(작을수록 먼저)로 모두 호출한다.
 * <ul>
 *   <li>{@link #preDispatch} 에서 예외를 던지면 디스패치가 중단되고 {@link #onError} 가 호출된다.</li>
 *   <li>{@link #postDispatch}, {@link #onError} 안의 예외는 디스패치 결과를 바꾸지 않는다 — 경고 로그만 남는다.</li>
 *   <li>한 번의 디스패치에서 {@link #postDispatch} 와 {@link #onError} 중 하나만 호출된다.</li>
 * </ul>
 *
 * <p>비동기 디스패치에서는 {@code dispatchTaskExecutor} 의 작업 스레드에서 호출된다.
 */
public interface DispatchInterceptor extends Ordered {

	/**
	 * 커맨드를 찾기 전에 호출된다. 요청 헤더(transactionId·messageId·timestamp)는 이미 채워져 있다.
	 *
	 * @param request 요청
	 * @throws MesGeneralException 디스패치를 막아야 할 때
	 */
	default void preDispatch(RequestMessage request) throws MesGeneralException {
	}

	/**
	 * 커맨드가 응답을 돌려준 뒤 호출된다. 응답이 업무 실패({@code success=false})여도 여기로 온다.
	 *
	 * @param request  요청
	 * @param response 커맨드가 돌려준 응답
	 */
	default void postDispatch(RequestMessage request, ResponseMessage response) {
	}

	/**
	 * 디스패치가 응답 없이 끝났을 때 호출된다(커맨드 없음, 실행 예외, 스레드 풀 포화, preDispatch 거부).
	 *
	 * @param request 요청
	 * @param cause   원인. 프레임워크 밖 예외는 {@link DispatchException}({@code ERR_500_DISPATCH}) 으로 감싸져 있다.
	 */
	default void onError(RequestMessage request, MesGeneralException cause) {
	}

	@Override
	default int getOrder() {
		return 0;
	}
}
```

### 3.9 `DispatchException`

```java
public class DispatchException extends MesGeneralException {
	public static final String INVALID_REQUEST   = "ERR_400";
	public static final String COMMAND_NOT_FOUND = "ERR_404_DISPATCH_COMMAND";
	public static final String DUPLICATE_OPCODE  = "ERR_500_DISPATCH_DUPLICATE";
	public static final String EXECUTION_FAILED  = "ERR_500_DISPATCH";
	public static final String REJECTED          = "ERR_503_DISPATCH_REJECTED";

	public DispatchException(String errorCode, String message) { ... }
	public DispatchException(String errorCode, String message, Throwable cause) { ... }
}
```

- 메시지는 한국어로 쓴다. 어투는 mes-dev `docs/ERROR_MESSAGES.md`를 따른다.
- **dispatch 밖이지만 같이 고치기를 권한다**: 기존 `MesGeneralException(String message, Throwable t)`는 `this("", message, null)`을 호출해 **원인 예외(cause)를 버린다**. `MesGeneralException(Throwable t)`도 이 생성자를 거치므로 원인이 사라진다. 이 생성자를 쓰는 곳은 스택 추적에 원래 오류가 남지 않는다. `this("", message, t)`로 고치면 된다.

### 3.10 `DispatchProperties`, `DispatchConfig`

```yaml
app:
  dispatch:
    commands:                      # 선택. @Command 를 못 붙이는 커맨드용
      - opcode: some.opcode
        command: someCommandBean   # 빈 이름 또는 클래스 FQN
    executor:
      core-size: 4
      max-size: 16
      queue-capacity: 200
      thread-name-prefix: dispatch-
      await-termination: 30s
```

- **`DispatchProperties`**: 스레드 풀 설정 클래스 이름은 `ExecutorProperties`다. `java.util.concurrent.Executor`와 헷갈리지 않게 하기 위해서다.
- **`DispatchConfig`(`@Configuration(proxyBeanMethods=false)`)가 하는 일**
  - `@EnableConfigurationProperties(DispatchProperties.class)`
  - `dispatchTaskExecutor`(`ThreadPoolTaskExecutor`) 빈을 만든다.
    - 호출 스레드의 `SecurityContext`와 MDC를 복사하는 `TaskDecorator`를 붙이고, 작업이 끝나면 비운다.
    - 거부 정책은 `AbortPolicy`다(→ `ERR_503_DISPATCH_REJECTED`). `CallerRunsPolicy`를 쓰면 HTTP 요청 스레드가 PLC 응답을 기다리며 묶이므로 쓰지 않는다.
- **`@ConditionalOnMissingBean`은 쓰지 않는다.** 이 설정은 자동 구성이 아니라 컴포넌트 스캔으로 올라오므로, 조건 평가 순서가 보장되지 않는다. 스레드 풀은 `app.dispatch.executor.*`로 조정한다.
- **리졸버는 이 설정에서 만들지 않는다.** `AnnotatedCommandResolver`와 `ConfiguredCommandResolver`는 각자 `@Component`로 올라오고, 생성자로 필요한 것(`ApplicationContext`, `DispatchProperties`)을 주입받는다.
- 다른 framework 패키지와 같이 컴포넌트 스캔으로 올라오게 둔다. 앱이 `com.wow.be.framework.dispatch`를 스캔해야 한다.

#### `DispatchProperties` 전체 코드

```java
package com.wow.be.framework.dispatch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 디스패치 설정 ({@code app.dispatch.*}).
 *
 * <pre>
 * app:
 *   dispatch:
 *     commands:                      # 선택. @Command 를 붙일 수 없는 커맨드를 opcode 에 연결한다
 *       - opcode: some.opcode
 *         command: someCommandBean   # 빈 이름 또는 클래스 FQN
 *     executor:
 *       core-size: 4
 *       max-size: 16
 *       queue-capacity: 200
 *       thread-name-prefix: dispatch-
 *       await-termination: 30s
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

	/** opcode → 커맨드 매핑. {@link ConfiguredCommandResolver} 가 읽는다. */
	private List<CommandMapping> commands = new ArrayList<>();

	/** 비동기 디스패치 스레드 풀 ({@code dispatchTaskExecutor}). */
	private ExecutorProperties executor = new ExecutorProperties();

	@Data
	public static class CommandMapping {

		/** 처리할 opcode */
		private String opcode;

		/** 커맨드 빈 이름 또는 클래스 FQN. 클래스가 빈이 아니면 DI 된 인스턴스를 만들어 쓴다. */
		private String command;
	}

	@Data
	public static class ExecutorProperties {

		private int coreSize = 4;

		private int maxSize = 16;

		/** 대기 큐 크기. 가득 차면 새 요청은 ERR_503_DISPATCH_REJECTED 로 거절된다. */
		private int queueCapacity = 200;

		private String threadNamePrefix = "dispatch-";

		/** 종료할 때 진행 중인 작업을 기다리는 최대 시간. 0 이면 기다리지 않는다. */
		private Duration awaitTermination = Duration.ofSeconds(30);
	}
}
```

#### `DispatchConfig` 전체 코드

```java
package com.wow.be.framework.dispatch;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 디스패치 기반 설정 — 설정값({@link DispatchProperties})과 비동기 디스패치 스레드 풀을 등록한다.
 *
 * <p>커맨드 리졸버({@link AnnotatedCommandResolver}, {@link ConfiguredCommandResolver})와
 * {@link MessageDispatcher} 는 각자 {@code @Component} 로 올라온다. 앱이 {@code com.wow.be.framework.dispatch}
 * 패키지를 컴포넌트 스캔해야 한다.
 *
 * <p>스레드 풀 크기는 {@code app.dispatch.executor.*} 로 바꾼다. 같은 이름의 빈을 앱에서 다시 정의하는 방식은
 * Spring Boot 의 빈 재정의 금지 때문에 쓰지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DispatchProperties.class)
public class DispatchConfig {

	/** {@link MessageDispatcher} 가 주입받는 스레드 풀 빈 이름 */
	public static final String DISPATCH_TASK_EXECUTOR = "dispatchTaskExecutor";

	@Bean(name = DISPATCH_TASK_EXECUTOR)
	public ThreadPoolTaskExecutor dispatchTaskExecutor(DispatchProperties properties) {
		DispatchProperties.ExecutorProperties props = properties.getExecutor();

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(props.getCoreSize());
		executor.setMaxPoolSize(props.getMaxSize());
		executor.setQueueCapacity(props.getQueueCapacity());
		executor.setThreadNamePrefix(props.getThreadNamePrefix());
		// 큐가 가득 차면 거절한다(TaskRejectedException). 호출 스레드에서 대신 실행하면 HTTP 요청 스레드가 PLC 응답을 기다리며 묶인다.
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.setWaitForTasksToCompleteOnShutdown(!props.getAwaitTermination().isZero());
		executor.setAwaitTerminationMillis(props.getAwaitTermination().toMillis());
		return executor;
	}

	/**
	 * 호출 스레드의 인증 정보(SecurityContext)와 MDC 를 작업 스레드로 복사한다.
	 * 비동기 커맨드 안에서도 PersistentEntity 의 작성자 필드가 채워지고, 로그를 같은 요청으로 묶어 볼 수 있다.
	 * 작업이 끝나면 작업 스레드의 컨텍스트를 비운다 — 풀 스레드가 다음 작업에 이전 사용자를 넘기지 않게 한다.
	 */
	static final class ContextPropagatingTaskDecorator implements TaskDecorator {

		@Override
		public Runnable decorate(Runnable runnable) {
			SecurityContext callerContext = SecurityContextHolder.getContext();
			SecurityContext copied = SecurityContextHolder.createEmptyContext();
			copied.setAuthentication(callerContext.getAuthentication());
			Map<String, String> callerMdc = MDC.getCopyOfContextMap();

			return () -> {
				SecurityContextHolder.setContext(copied);
				if (callerMdc != null) MDC.setContextMap(callerMdc);
				else MDC.clear();
				try {
					runnable.run();
				} finally {
					SecurityContextHolder.clearContext();
					MDC.clear();
				}
			};
		}
	}
}
```

## 4. 테스트 (framework-core `src/test`)

mes 정책대로 Mock 없이, 작은 실제 커맨드와 Spring 컨텍스트로 시험한다.

**사전 확인 (2026-10-30)**: 3장의 전체 코드(`Command`, 두 리졸버, `MessageDispatcher`, `DispatchInterceptor`, `DispatchProperties`, `DispatchConfig`)를 JDK 25와 Spring Boot 4.0.7 클래스패스로 컴파일했다. 아래 표에서 ✅ 표시한 항목은 임시 시험 코드로 실행해 통과를 확인했다. 시험은 `AnnotationConfigApplicationContext`에 실제 빈과 yml 속성을 넣어 돌렸다. framework-core `src/test`에는 정식 테스트로 다시 작성해야 한다.

| 시험 | 확인할 것 | 사전 확인 |
|---|---|---|
| `@Command` 찾기 | 빈으로 등록된 커맨드가 opcode로 실행되고, DI 받은 필드가 채워져 있다 | ✅ |
| 중복 opcode (`@Command`) | 두 빈이 같은 opcode를 쓰면 컨텍스트 기동이 `ERR_500_DISPATCH_DUPLICATE`로 실패한다 | ✅ |
| 중복 opcode (yml) | yml에 같은 opcode가 두 번이면, 커맨드를 만들기 전에 `ERR_500_DISPATCH_DUPLICATE`로 기동이 실패한다 | ✅ |
| 설정 매핑 | 빈 이름 매핑과 클래스 FQN 매핑 모두 동작하고, 빈이 아닌 클래스도 DI된다 | ✅ |
| 설정 오류 | 없는 클래스, `MessageCommand`가 아닌 클래스면 기동이 실패한다 | ✅ |
| 설정 커맨드 정리 | `createBean`으로 만든 커맨드의 `@PreDestroy`가 컨텍스트 종료 때 호출된다 | ✅ |
| 리졸버 우선순위 | 앱 리졸버(순서 50) → yml(100) → `@Command`(200) 순서로 선택되고, `Optional.empty()`면 다음으로 넘어간다 | ✅ |
| 설정 바인딩 | `app.dispatch.executor.core-size`가 스레드 풀에 반영된다 | ✅ |
| 커맨드 없음 | `ERR_404_DISPATCH_COMMAND`, 인터셉터 `onError` 호출 | ✅ |
| opcode 없음 | `ERR_400`, 인터셉터를 거치지 않음 | ✅ |
| 헤더 보충 | 비어 있는 transactionId, messageId, timestamp가 채워진다 | ✅ |
| 응답 골격 | `fillReplyHeader`로 sender와 receiver가 뒤바뀌고 messageId가 새로 생긴다 | 미확인 (`ResponseMessage` 미작성) |
| 비동기 실패 | 커맨드의 NPE가 `ERR_500_DISPATCH`로 감싸져 `onError`로 한 번만 오고, `getCause()`가 원래 NPE다 | ✅ |
| 콜백 예외 | `onCompleted` 안의 예외로 `onError`가 다시 불리지 않는다 | ✅ |
| 큐 포화 | 제출이 거절되면 future가 `ERR_503_DISPATCH_REJECTED`로 끝난다 | ✅ |
| 인터셉터 | pre, post 순서대로 호출되고, post에서 난 예외는 결과를 바꾸지 않는다 | ✅ |
| 컨텍스트 전파 | 비동기 커맨드 안에서 호출 스레드의 인증 사용자와 MDC가 보이고, 작업 뒤에는 비워진다 | 미확인 |

## 5. 배포

- framework-core 버전을 올린다(예: `0.8.46`). mes-dev 규칙상 버전은 회차 브랜치와 맞추므로 `version`과 `frameworkCoreVersion`을 함께 올린다.
- 배포가 끝나면 알려 주세요. tc의 `frameworkCoreVersion`을 올리고 [dispatch-redesign.md](dispatch-redesign.md) 4절의 1단계부터 진행한다.

## 6. tc가 이 위에서 할 일 (참고)

- `com.wow.be.tc.dispatch`의 복사본을 지운다: `Command`, `CommandInfo`, `DispatcherConfig`, `DispatcherCommandLoader`, `MessageCommandFactory`, `MessageDispatcher`
- `TcMessageRequest extends RequestMessage`, `TcMessageResponse extends ResponseMessage`
- `InterfaceCommandResolver`(순서 50): opcode로 인터페이스를 찾고, `CMD_CLASS_NAME` 빈이나 기본 `PlcInterfaceCommand`를 돌려준다.
- `PlcInterfaceCommand extends AbstractMessageCommand<TcMessageRequest, TcMessageResponse>`
- `AuditDispatchInterceptor`: 감사 기록과 SSE
- 결과 전달 채널(`ResultDeliveryChannel`)은 dispatch 밖, tc 쪽에 둔다.
