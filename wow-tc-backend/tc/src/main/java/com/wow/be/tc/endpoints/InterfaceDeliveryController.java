package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.DeliveryDefinitionInfo;
import com.wow.be.tc.delivery.DeliveryDefinitionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 인터페이스별 결과 전달 정의(INTERFACE_DELIVERY) 관리 API.
 *
 * <p>한 인터페이스에 정의가 하나라도 있으면 그 정의대로만 결과를 보낸다. 하나도 없으면 레거시 방식
 * (요청의 callback 주소 + 응답 인터페이스의 WebHook 목록)으로 보낸다.
 */
@Tag(name = "Interface Delivery", description = "인터페이스별 결과 전달 정의 관리")
@RestController
@RequestMapping("/api/v1/ifsvc/delivery")
@RequiredArgsConstructor
// 권한(설계 §4) — REST 메서드로 동작을 추론한다(POST C, PUT U, DELETE D)
@Requires("tc.delivery")
public class InterfaceDeliveryController {

	private final DeliveryDefinitionService deliveryDefinitionService;

	@Operation(summary = "전달 정의 목록", description = "interfaceName 을 주면 그 인터페이스의 정의만 돌려준다.")
	@GetMapping
	public List<DeliveryDefinitionInfo> search(@RequestParam(name = "interfaceName", required = false) String interfaceName) {
		return interfaceName == null || interfaceName.isBlank()
			? deliveryDefinitionService.searchAll()
			: deliveryDefinitionService.searchByInterfaceName(interfaceName);
	}

	@Operation(summary = "전달 정의 한 건")
	@GetMapping("/{id}")
	public DeliveryDefinitionInfo find(@PathVariable("id") String id) throws ToolControlException {
		return deliveryDefinitionService.findById(id);
	}

	@Operation(summary = "전달 정의 등록", description = "id 를 비우면 {인터페이스}.{유형}.{대상} 으로 만든다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeliveryDefinitionInfo register(@RequestBody DeliveryDefinitionInfo info) throws ToolControlException {
		return deliveryDefinitionService.register(info);
	}

	@Operation(summary = "전달 정의 수정", description = "인터페이스와 id 는 바꿀 수 없다.")
	@PutMapping("/{id}")
	public DeliveryDefinitionInfo update(@PathVariable("id") String id, @RequestBody DeliveryDefinitionInfo info)
			throws ToolControlException {
		info.setId(id);
		return deliveryDefinitionService.update(info);
	}

	@Operation(summary = "전달 정의 삭제")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("id") String id) throws ToolControlException {
		deliveryDefinitionService.delete(id);
	}
}
