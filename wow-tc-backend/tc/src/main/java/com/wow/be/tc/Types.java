package com.wow.be.tc;

public class Types {

	public enum PlcConnectorInfoStatus {
		REGISTERED, PUBLISHED, RELEASED, UNKNOWN
	}

	public enum PlcConnectorOperStatus {
		CONNECTION_TEST_OK, CONNECTION_TEST_ERROR, CONNECTION_TEST_NONE, RUNNING, STOPPED, PAUSED, RESUMED, ERROR, UNKNOWN
	}

	public enum InterfaceType {
		READ, WRITE, REPLY, PUSH, UNKNOWN
	}

	public enum RequestType {
		READ("READ"), WRITE("WRITE"), UNKNOWN("UKNOWN");

		private String name;

		private RequestType(String name) {
			this.name=name;
		}

		public String getName() {
			return name;
		}

		public static RequestType toRequestType(String name) {
			switch(name) {
			case "READ":
				return READ;
			case "WRITE":
				return WRITE;
			default:
				return UNKNOWN;
			}
		}
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
	
	public enum PropType {
		
		BOOLEAN("BOOLEAN", 0, "BOOLEAN"),
		BYTE("BYTE", 1, "BYTE"), 
		SHORT("SHORT", 2, "SHORT"),
		INT("INT", 3, "INT"), 
		LONG("LONG", 4, "LONG"),
		DOUBLE("DOUBLE", 5, "DOUBLE"),
		STRING("STRING", 6, "STRING");
		
		private int value;
		private String name;
		private String displayName;

		private PropType(String name, int value, String displayName) {
			this.name=name;
			this.value=value;
			this.displayName=displayName;
		}
		
		public int getValue() {
			return this.value;
		}
		
		public String getName() {
			return this.name;
		}
		
		public String getDisplayName() {
			return this.displayName;
		}
	}
}
