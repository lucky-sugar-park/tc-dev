package com.wow.be.tc.vplc;

public class Types {

	public enum PlcStatus {
		REGISTERED, PUBLISHED, RUNNING, PAUSED, STOPPED, RELEASED, UNKNOWN
	}
	
	/**
	 * 가상 PLC 종류 — tc 커넥터 프로토콜과 같은 이름. 한 vplc 앱이 세 종류를 여러 대씩 띄운다(docs/virtual-devices.md).
	 * 예전 줄(PROTOCOL 이 비어 있음)은 MELSEC_MC.
	 */
	public enum Protocol {
		MELSEC_MC, MODBUS_TCP, OPC_UA;

		public static Protocol of(String s) {
			return s == null || s.isBlank() ? MELSEC_MC : Protocol.valueOf(s.trim().toUpperCase());
		}
	}

	public enum FrameFormat {
		ASCII, BINARY, UNKNOWN
	}

	public enum MemoryType {
		SM("SM", (byte)0x91, 0x00000, 0x002047, "Special relay"),
		SD("SD", (byte)0xA9, 0x00000, 0x002047, "Special register"),
		X ("X*", (byte)0x9C, 0x00000, 0x001FFF, "Inpout relay"),
		Y ("Y*", (byte)0x9D, 0x00000, 0x001FFF, "Ouput relay"),
		M ("M*", (byte)0x90, 0x00000, 0x008191, "Internal relay"),
		L ("L*", (byte)0x92, 0x00000, 0x008191, "Latch relay"),
		F ("F*", (byte)0x93, 0x00000, 0x002047, "Annunciator"),
		V ("V*", (byte)0x94, 0x00000, 0x002047, "Edge relay"),
		B ("B*", (byte)0xA0, 0x00000, 0x001FFF, "Link relay"),
		D ("D*", (byte)0xA8, 0x00000, 0x012287, "Data register"),
		W ("W*", (byte)0xB4, 0x00000, 0x001FFF, "Link register"),
		TS("TS", (byte)0xC1, 0x00000, 0x002047, "Timer contact"),
		TC("TC", (byte)0xC0, 0x00000, 0x002047, "Timer coil"),
		TN("TN", (byte)0xC2, 0x00000, 0x002047, "Timer current value"),
		SS("SS", (byte)0xC7, 0x00000, 0x002047, "Accumulation timer contact"),
		SC("SC", (byte)0xC6, 0x00000, 0x002047, "Accumulation timer coil"),
		SN("SN", (byte)0xC8, 0x00000, 0x002047, "Accumulation timer current value"),
		CS("CS", (byte)0xC4, 0x00000, 0x001023, "Counter contact"),
		CC("CC", (byte)0xC3, 0x00000, 0x001023, "Counter coil"),
		CN("CN", (byte)0xC5, 0x00000, 0x001023, "Counter current value"),
		SB("SB", (byte)0xA1, 0x00000, 0x0007FF, "Link special relay"),
		SW("SW", (byte)0xB5, 0x00000, 0x0007FF, "Link special register"),
		S ("S*", (byte)0x98, 0x00000, 0x008191, "Step relay"),
		DX("DX", (byte)0xA2, 0x00000, 0x001FFF, "Direct input"),
		DY("DY", (byte)0xA3, 0x00000, 0x001FFF, "Direct output"),
		Z ("Z*", (byte)0xCC, 0x00000, 0x000015, "Index register"),
		R ("R*", (byte)0xAF, 0x00000, 0x032767, "File register"),
		ZR("ZR", (byte)0xB0, 0x00000, 0x0FE7FF, "File register");

		private byte binaryCode;
		private String asciiCode;
		private int boundaryFrom;
		private int boundaryTo;
		private String description;

		private MemoryType(String asciiCode, byte binaryCode, int boundaryFrom, int boundaryTo, String description) {
			this.asciiCode=asciiCode;
			this.binaryCode=binaryCode;
			this.boundaryFrom=boundaryFrom;
			this.boundaryTo=boundaryTo;
			this.description=description;
		}

		public byte getBinaryCode() {
			return this.binaryCode;
		}

		public String getAsciiCode() {
			return this.asciiCode;
		}

		public int getBoundaryFrom() {
			return this.boundaryFrom;
		}

		public int getBoundaryTo() {
			return this.boundaryTo;
		}
		
		public String getDescription() {
			return this.description;
		}

		/** 비트 디바이스(접점·코일·릴레이) — 비트 단위(서브 명령 0001)로 읽고 쓸 수 있다 */
		public boolean isBitDevice() {
			return switch (this) {
				case SM, X, Y, M, L, F, V, B, TS, TC, SS, SC, CS, CC, SB, S, DX, DY -> true;
				default -> false;
			};
		}

		public static MemoryType toMemoryType(String memType) {
			switch (memType) {
			case "SM":
				return SM;
			case "SD":
				return SD;
			case "X":
			case "X*":
				return X;
			case "Y":
			case "Y*":
				return Y;
			case "M":
			case "M*":
				return M;
			case "L":
			case "L*":
				return L;
			case "F":
			case "F*":
				return F;
			case "V":
			case "V*":
				return V;
			case "B":
			case "B*":
				return B;
			case "D":
			case "D*":
				return D;
			case "W":
			case "W*":
				return W;
			case "TS":
				return TS;
			case "TC":
				return TC;
			case "TN":
				return TN;
			case "SS":
				return SS;
			case "SC":
				return SC;
			case "SN":
				return SN;
			case "CS":
				return CS;
			case "CC":
				return CC;
			case "CN":
				return CN;
			case "SB":
				return SB;
			case "SW":
				return SW;
			case "S":
			case "S*":
				return S;
			case "DX":
				return DX;
			case "DY":
				return DY;
			case "Z":
			case "Z*":
				return Z;
			case "R":
			case "R*":
				return R;
			case "ZR":
				return ZR;
			default:
				return D;
			}
		}

		public static MemoryType toMemoryType(byte binaryCode) {
			switch (binaryCode) {
			case (byte) 0x91:
				return SM;
			case (byte)0xA9:
				return SD;
			case (byte)0x9C:
				return X;
			case (byte)0x9D:
				return Y;
			case (byte)0x90:
				return M;
			case (byte)0x92:
				return L;
			case (byte)0x93:
				return F;
			case (byte)0x94:
				return V;
			case (byte)0xA0:
				return B;
			case (byte)0xA8:
				return D;
			case (byte)0xB4:
				return W;
			case (byte)0xC1:
				return TS;
			case (byte)0xC0:
				return TC;
			case (byte)0xC2:
				return TN;
			case (byte)0xC7:
				return SS;
			case (byte)0xC6:
				return SC;
			case (byte)0xC8:
				return SN;
			case (byte)0xC4:
				return CS;
			case (byte)0xC3:
				return CC;
			case (byte)0xC5:
				return CN;
			case (byte)0xA1:
				return SB;
			case (byte)0xB5:
				return SW;
			case (byte)0x98:
				return S;
			case (byte)0xA2:
				return DX;
			case (byte)0xA3:
				return DY;
			case (byte)0xCC:
				return Z;
			case (byte)0xAF:
				return R;
			case (byte)0xB0:
				return ZR;
			default:
				return D;
			}
		}
	}
}
