package com.wow.be.tc.vplc.machine;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public abstract class VirtualPlcCommBootstrapper {
	
	private String ipAddress;

	protected FrameFormat messageFrameFormat;
	
	protected int portCount;
	
	protected int startPort;
	
	protected int connLimit;
		
	protected VirtualPlcMessageDelegate messageDelegate;
	
	public VirtualPlcCommBootstrapper(VirtualPlcMessageDelegate messageProcessor) {
		this(null, FrameFormat.BINARY, 12345, 1, 1, messageProcessor);
	}
	
	public VirtualPlcCommBootstrapper(String ipAddress, FrameFormat frameFormat, int startPort, int portCount, int connLimit, VirtualPlcMessageDelegate messageDelegate) {
		log.debug("");
		this.ipAddress=ipAddress;
		this.messageFrameFormat=frameFormat;
		this.startPort=startPort;
		this.portCount=portCount;
		this.connLimit=connLimit;
		this.messageDelegate=messageDelegate;
	}
	
	public abstract void ready() throws VirtualPlcException;
	
	public abstract void stop()  throws VirtualPlcException;
	
	public static VirtualPlcCommBootstrapper createVplcCommBoostrapper (
			String className, 
			String ipAddress,
			int startPort, 
			int portCount, 
			int connLimit, 
			VirtualProgrammableController vplc,
			FrameFormat messageFrameFormat) throws VirtualPlcException 
	{
		log.debug("VirtualPlcCommBootstraper.createVplcCommBoostraper(String className, int startPort, int portCount, int connLimit, VirtualProgrammableController vplc) is called.");

		try {
			Class<?> clazz=Class.forName(className);
			Constructor<?> constructor=clazz.getDeclaredConstructor(String.class, FrameFormat.class, int.class, int.class, int.class, VirtualPlcMessageDelegate.class);
//			if(!constructor.canAccess(clazz)) {
//				constructor.setAccessible(true);
//			}
			return (VirtualPlcCommBootstrapper) constructor.newInstance(ipAddress,messageFrameFormat, startPort, portCount, connLimit, new VirtualPlcMessageDelegate(vplc));

		} catch (NoSuchMethodException | SecurityException | ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			throw new VirtualPlcException(VirtualPlcException.ERR_500, "가상 PLC 통신 모듈을 만들 수 없습니다: " + className, e);
		}
	}
}
