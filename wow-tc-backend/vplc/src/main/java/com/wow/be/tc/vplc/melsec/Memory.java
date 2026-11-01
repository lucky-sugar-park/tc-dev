package com.wow.be.tc.vplc.melsec;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;

import com.wow.be.tc.vplc.melsec.MelsecTypes.MemoryType;

import lombok.Data;

@Data
public class Memory {
	
	/**
	 * default로 2만개 short 값을 저장함 (2만 Word)
	 */
	private static final int DEFAULT_SIZE=20000;
	
	private MemoryType memoryType;
	
	private int capacity;
	
	/**
	 * word 단위로 읽기 때문에 2 byte 길이 데이터 타입이 적당함
	 */
	private short words[];
	
	private String description;
	
	private List<MemoryWatcher> watcherList;
		
	public Memory(MemoryType memType) {
		this(memType, DEFAULT_SIZE);
	}

	public Memory(MemoryType memType, int size) {
		this.memoryType=memType;
		this.words=new short[size];
		this.capacity=size;
		this.description=memType.getDescription();

		watcherList=new ArrayList<>();
	}
	
	public void addWatcher(MemoryWatcher watcher) {
		this.watcherList.add(watcher);
	}

	public WordInfo[] read20Words(int start) {
		return this.readWords(start, 20);
	}

	public WordInfo[] readWords(int start, int wordCount) {
		int k=start;
		WordInfo wInfos[]=new WordInfo[wordCount];
		for(int i=0;i<wordCount;i++) {
			wInfos[i]=new WordInfo(this.words[k++]);
		}

		return wInfos;
	}

	public void writeWord(int pos, short word) {
		this.words[pos]=word;
	}
	
	public void writeWords(int start, WordInfo[] wInfos) {
		if(start+wInfos.length>=this.capacity) {
			return;
		}

		int k=start;
		for(int i=0; i<wInfos.length; i++) {
			this.words[k++]=wInfos[i].getWord();	
		}
	}
	
	public void writeWords(int start, short[] warr) {
		if(start+warr.length>=this.capacity) {
			return;
		}
		int k=start;
		for(int i=0; i<warr.length; i++) {
			this.words[k++]=warr[i];	
		}
	}

	public void writeWord(int pos, byte low, byte high) {
		this.writeWord(pos, (short)(high<<8|(low&0xFF)));
	}

	public void writeWord(int pos, WordInfo wInfo) {
		this.writeWord(pos, wInfo.getWord());
	}

//=================================================================================

	public byte[] read(int start, int wordCount) {
		int k=start;
		byte reads[]=new byte[wordCount<<1];
		for(int i=0; i<(wordCount<<1); i+=2) {
			short v=words[k++];
			reads[i]=(byte)(v&0xFF); reads[i+1]=(byte)(v>>8); 
		}

		return reads;
	}
	
	public void write(byte[] data, int start, int wordCount) {
		int k=start, idx=0;
		WordInfo wInfos[]=new WordInfo[wordCount];
		byte nbyte[]=new byte[data.length+1];
		int i=0;
		while(i<data.length) nbyte[i]=data[i++];
		if((data.length&1)==1) nbyte[i]='\0';

		for(i=0; i<wordCount; i++) {
			words[k++]=convert(nbyte[i<<1], nbyte[(i<<1)+1]);
			wInfos[idx++]=new WordInfo(words[k-1]);
		}
		
		MemoryChangedEvent event=new MemoryChangedEvent(this.memoryType, start, wInfos, new Date());
		this.watcherList.forEach(watcher->{
			Executors.newCachedThreadPool().execute(()->{
				watcher.onMemoryChanged(event);	
			});
		});
	}
	
	public void clear() {
		this.words=new short[this.words.length];
	}
	
	private short convert(byte low, byte high) {
		return (short) ( (high<<8) | (low&0xFF) );
	}

	public interface MemoryWatcher {
		
		public void onMemoryChanged (MemoryChangedEvent event);
	}
	
	@Data
	public class MemoryChangedEvent {
		
		private MemoryType memType;
		private int startAddress;
		private WordInfo[] changedWords;
		private Date timestamp;
		
		public MemoryChangedEvent(MemoryType memType, int startAddress, WordInfo[] changedWords, Date timestamp) {
			this.memType=memType;
			this.startAddress=startAddress;
			this.changedWords=changedWords;
			this.timestamp=timestamp;
		}
	}
}

