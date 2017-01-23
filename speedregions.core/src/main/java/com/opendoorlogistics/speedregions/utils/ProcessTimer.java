package com.opendoorlogistics.speedregions.utils;

public class ProcessTimer {
	private long startedMillis=-1;
	private long stoppedMillis=-1;
	
	public ProcessTimer start(){
		startedMillis = System.currentTimeMillis();
		return this;
	}
	
	public ProcessTimer stop(){
		stoppedMillis = System.currentTimeMillis();
		return this;
	}
	
	public long millisDuration(){
		return stoppedMillis - startedMillis;
	}
	
	public double secondsDuration(){
		return millisDuration() / 1000.0;
	}
}
