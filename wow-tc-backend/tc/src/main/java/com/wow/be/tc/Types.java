package com.wow.be.tc;

public class Types {

	public enum PlcConnectorInfoStatus {
		REGISTERED, PUBLISHED, RELEASED, UNKNOWN
	}

	public enum PlcConnectorOperStatus {
		CONNECTION_TEST_OK, CONNECTION_TEST_ERROR, CONNECTION_TEST_NONE, RUNNING, STOPPED, PAUSED, RESUMED, ERROR, UNKNOWN
	}

	public enum InterfaceType {
		READ, WRITE
	}

	public enum FrameFormat {

		BINARY("BINARY", 1), ASCII("",2), UNKNOWN("",99);

		private String name;
		private int value;
		
		private FrameFormat(String name, int value) {
			this.name=name;
			this.value=value;
		}
		
		public String getName() {
			return this.name;
		}
		
		public int getValue() {
			return this.value;
		}
		
		public static FrameFormat toFrameFormat(String name) {
			switch(name) {
			case "BINARY":
				return BINARY;
			case "ASCII":
				return ASCII;
			default:
				return UNKNOWN;
			}
		}
	}
}
