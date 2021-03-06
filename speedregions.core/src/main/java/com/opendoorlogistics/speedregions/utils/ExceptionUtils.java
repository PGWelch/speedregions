package com.opendoorlogistics.speedregions.utils;

public class ExceptionUtils {
	public static RuntimeException asUncheckedException(Throwable e){
		if(RuntimeException.class.isInstance(e)){
			return (RuntimeException)e;
		}
		return new RuntimeException(e);
	}
	
	public static String getMessage(Throwable e){
		while(e!=null && e.getMessage()==null){
			e = e.getCause();
		}
		
		return e!=null ? e.getMessage() : null;
	}
}
