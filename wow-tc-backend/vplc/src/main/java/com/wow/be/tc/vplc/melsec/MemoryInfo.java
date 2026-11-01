package com.wow.be.tc.vplc.melsec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.melsec.MelsecTypes.MemoryType;

import lombok.Data;

@Data
public class MemoryInfo {
	
	private String vplcId;
	
	private MemoryType memoryType;
	
	private FrameFormat frameFormat;
	
	private int startAddress;
	
	private short[] words;
	
	private int capacity;
	
	private WordInfo[] wordInfos;
	
	public MemoryInfo() {
		
	}
	
	// setWords 가 둘(short[]·byte[])이라 Jackson 3 가 "Conflicting setter" 로 역직렬화를 거부한다.
	// JSON 의 words 는 short[] 로 받고, byte[] 판은 코드에서만 쓴다.
	@JsonSetter
	public void setWords(short[] words) {
		this.words=words;
		this.wordInfos=new WordInfo[words.length];
		for(int i=0;i<words.length;i++) {
			WordInfo wInfo=new WordInfo(words[i]);
			this.wordInfos[i]=wInfo;
		}
	}
	
	@JsonIgnore
	public void setWords(byte[] bytes) {
		short wordValues[]=new short[bytes.length>>1];
		int idx=0;
		for(int i=0;i<bytes.length;i+=2) {
			// 낮은 바이트는 부호 없이 — 예전에는 0x80 이상이면 부호가 번져 1234 가 -46 으로 보였다
			wordValues[idx++]=(short)(bytes[i+1]<<8|(bytes[i]&0xFF));
		}
		this.setWords(wordValues);
	}
	
	public void setWordInfos(WordInfo[] words) {
		this.wordInfos=words;
	}
}
