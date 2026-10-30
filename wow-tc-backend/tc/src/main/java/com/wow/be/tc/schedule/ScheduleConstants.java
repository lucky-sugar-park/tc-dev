package com.wow.be.tc.schedule;

public class ScheduleConstants {

	public static final String TRIGGER_STATE_NORMAL = "NORMAL";
	public static final String TRIGGER_STATE_PAUSED = "PAUSED";
	public static final String TRIGGER_STATE_COMPLETE = "COMPLETE";
    public static final String TRIGGER_STATE_ERROR = "ERROR";
    public static final String TRIGGER_STATE_BLOCKED = "BLOCKED";
    public static final String TRIGGER_STATE_NONE = "NONE";
    
	public static final String TRIGGER_TYPE_CRON = "cron";
	public static final String TRIGGER_TYPE_SIMPLE = "simple";
	
	public static final String ENTRY_VAULE_TYPE_REF = "ref";
	public static final String ENTRY_VAULE_TYPE_TEXT = "text"; // text means int|long|string
	public static final String ENTRY_VAULE_TYPE_INT = "int";
	public static final String ENTRY_VAULE_TYPE_LONG = "long";
	public static final String ENTRY_VAULE_TYPE_STRING = "string";
	public static final String ENTRY_VAULE_TYPE_LIST = "list";
	public static final String ENTRY_VAULE_TYPE_MAP = "map";
}
