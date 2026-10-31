package com.wow.be.tc.plc.connector.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.opcua.OpcUaDriver;
import com.wow.be.tc.plc.protocol.opcua.OpcUaSessions;

/**
 * OPC UA 연결 시험·노드 찾아보기 (docs/opcua-design.md O10) — 게시·소유권과 상관없이 짧은 세션을 따로 열어 본다.
 */
public final class OpcUaTools {

	/** Objects 폴더 */
	static final NodeId OBJECTS_FOLDER = new NodeId(0, 85);

	/** HierarchicalReferences(하위 형 포함) */
	static final NodeId HIERARCHICAL = new NodeId(0, 33);

	/** 한 번에 보여 줄 자식 수 */
	static final int MAX_CHILDREN = 1000;

	/** OPC UA 기본 데이터 형(ns=0 의 숫자 id) → 점 형 */
	private static final Map<Integer, PointType> TYPES = Map.ofEntries(
		Map.entry(1, PointType.BOOL), Map.entry(2, PointType.INT16), Map.entry(3, PointType.UINT16),
		Map.entry(4, PointType.INT16), Map.entry(5, PointType.UINT16), Map.entry(6, PointType.INT32), Map.entry(7, PointType.UINT32),
		Map.entry(8, PointType.INT64), Map.entry(9, PointType.INT64), Map.entry(10, PointType.FLOAT32), Map.entry(11, PointType.FLOAT64),
		Map.entry(12, PointType.STRING), Map.entry(15, PointType.BYTES));

	private OpcUaTools() {
	}

	/** 찾아본 노드 하나 — {@code type} 은 변수의 데이터 형으로 추정한 점 형(모르면 null), {@code array} 는 값이 배열 */
	public record BrowseNode(String nodeId, String browseName, String displayName, String nodeClass, String dataType, PointType type,
			boolean array, boolean hasChildren) {
	}

	/** 세션을 열고 서버 상태 노드를 한 번 읽는다 — 연결 시험 화면과 같은 모양({@code result}, {@code endpoint}, {@code cause}) */
	public static Map<String, String> testConnection(PlcConnectorInfo info, Map<String, Object> settings) {
		Map<String, String> result = new HashMap<>();
		String url = endpoint(info, settings);
		result.put("endpoint", url);
		OpcUaClient client = null;
		try {
			client = OpcUaSessions.create(url, settings);
			client.connect();
			DataValue state = client.readValue(0.0, TimestampsToReturn.Neither, new NodeId(0, 2259));
			result.put("result", PlcConnectorOperStatus.CONNECTION_TEST_OK.name());
			result.put("serverState", String.valueOf(state.getValue() != null ? state.getValue().getValue() : null));
		} catch (Exception e) {
			result.put("result", PlcConnectorOperStatus.CONNECTION_TEST_ERROR.name());
			result.put("cause", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			result.put("description", "주소·포트·endpointPath, 서버가 SecurityPolicy None 엔드포인트를 주는지 확인하세요");
		} finally {
			close(client);
		}
		return result;
	}

	/**
	 * 노드의 자식(Object·Variable)을 본다. nodeId 를 비우면 Objects 폴더.
	 * @throws IllegalArgumentException NodeId 문법(400)
	 * @throws ToolControlException 연결·서비스 실패(503·504)
	 */
	public static List<BrowseNode> browse(PlcConnectorInfo info, Map<String, Object> settings, String nodeId) throws ToolControlException {
		NodeId start = nodeId == null || nodeId.isBlank() ? OBJECTS_FOLDER
			: NodeId.parseSafe(nodeId.trim()).orElseThrow(() -> new IllegalArgumentException("NodeId 가 아닙니다: " + nodeId));
		OpcUaClient client = null;
		try {
			client = OpcUaSessions.create(endpoint(info, settings), settings);
			client.connect();
			List<ReferenceDescription> refs = references(client, start);
			List<NodeId> ids = new ArrayList<>();
			for (ReferenceDescription r : refs) {
				ids.add(r.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null));
			}
			Map<NodeId, DataValue[]> varInfo = variableInfo(client, refs, ids);
			List<BrowseNode> out = new ArrayList<>();
			for (int i = 0; i < refs.size(); i++) {
				ReferenceDescription r = refs.get(i);
				NodeId id = ids.get(i);
				if (id == null) {
					continue;
				}
				boolean variable = r.getNodeClass() == NodeClass.Variable;
				String dataType = null;
				PointType type = null;
				boolean array = false;
				DataValue[] vi = varInfo.get(id);
				if (vi != null) {
					Object dt = vi[0].getValue() != null ? vi[0].getValue().getValue() : null;
					if (dt instanceof NodeId d) {
						dataType = d.toParseableString();
						if (d.getNamespaceIndex().intValue() == 0 && d.getIdentifier() instanceof Number n) {
							type = TYPES.get(n.intValue());
						}
					}
					Object rank = vi[1].getValue() != null ? vi[1].getValue().getValue() : null;
					array = rank instanceof Number n && n.intValue() >= 1;
				}
				out.add(new BrowseNode(id.toParseableString(), r.getBrowseName() != null ? r.getBrowseName().getName() : null,
					r.getDisplayName() != null ? r.getDisplayName().getText() : null, String.valueOf(r.getNodeClass()), dataType, type, array,
					!variable));
			}
			return out;
		} catch (UaException e) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "OPC UA 찾아보기 실패: " + e.getMessage());
		} finally {
			close(client);
		}
	}

	private static List<ReferenceDescription> references(OpcUaClient client, NodeId start) throws UaException {
		BrowseDescription bd = new BrowseDescription(start, BrowseDirection.Forward, HIERARCHICAL, true,
			uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()), uint(BrowseResultMask.All.getValue()));
		BrowseResult result = client.browse(bd);
		List<ReferenceDescription> refs = new ArrayList<>();
		addAll(refs, result);
		ByteString cp = result.getContinuationPoint();
		while (cp != null && cp.isNotNull() && refs.size() < MAX_CHILDREN) {
			BrowseResult[] next = client.browseNext(false, List.of(cp)).getResults();
			if (next == null || next.length == 0) {
				break;
			}
			addAll(refs, next[0]);
			cp = next[0].getContinuationPoint();
		}
		if (cp != null && cp.isNotNull()) {
			client.browseNext(true, List.of(cp));
		}
		return refs.size() > MAX_CHILDREN ? refs.subList(0, MAX_CHILDREN) : refs;
	}

	private static void addAll(List<ReferenceDescription> refs, BrowseResult result) {
		if (result.getReferences() != null) {
			refs.addAll(List.of(result.getReferences()));
		}
	}

	/** 변수 자식의 DataType·ValueRank — 한 번의 Read 로 */
	private static Map<NodeId, DataValue[]> variableInfo(OpcUaClient client, List<ReferenceDescription> refs, List<NodeId> ids) throws UaException {
		List<ReadValueId> reads = new ArrayList<>();
		List<NodeId> vars = new ArrayList<>();
		for (int i = 0; i < refs.size(); i++) {
			if (refs.get(i).getNodeClass() == NodeClass.Variable && ids.get(i) != null) {
				vars.add(ids.get(i));
				reads.add(new ReadValueId(ids.get(i), AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE));
				reads.add(new ReadValueId(ids.get(i), AttributeId.ValueRank.uid(), null, QualifiedName.NULL_VALUE));
			}
		}
		Map<NodeId, DataValue[]> out = new LinkedHashMap<>();
		if (reads.isEmpty()) {
			return out;
		}
		DataValue[] r = client.read(0.0, TimestampsToReturn.Neither, reads).getResults();
		for (int i = 0; i < vars.size() && r != null && i * 2 + 1 < r.length; i++) {
			out.put(vars.get(i), new DataValue[] { r[i * 2], r[i * 2 + 1] });
		}
		return out;
	}

	private static String endpoint(PlcConnectorInfo info, Map<String, Object> settings) {
		Integer port = info.getPorts() != null && !info.getPorts().isEmpty() ? info.getPorts().get(0) : OpcUaDriver.DEFAULT_PORT;
		return OpcUaSessions.endpointUrl(info.getHostIp(), port, settings);
	}

	private static void close(OpcUaClient client) {
		if (client != null) {
			try {
				client.disconnectAsync().get(3, TimeUnit.SECONDS);
			} catch (Exception e) {
				// 닫기 실패는 무시
			}
		}
	}
}
