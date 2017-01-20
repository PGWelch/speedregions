package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Component;
import java.util.concurrent.Callable;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.opendoorlogistics.speedregions.utils.ExceptionUtils;

public class SwingUtils {
	public static <T> T runOnEDT(final Callable<T> callable) {
		class MyRunnable implements Runnable{
			T result;
			
			@Override
			public void run() {
				try {
					result = callable.call();					
				} catch (Exception e) {
					throw ExceptionUtils.asUncheckedException(e);
				}
			}
			
		}
		
		MyRunnable runnable = new MyRunnable();
		runOnEDT(runnable);
		return runnable.result;
	}
	
	public static void runOnEDT(Runnable runnable) {
		try {
			if (!SwingUtilities.isEventDispatchThread()) {
				SwingUtilities.invokeAndWait(runnable);		
			}else{
				runnable.run();			
			}
		} catch (Exception e) {
			throw ExceptionUtils.asUncheckedException(e);
		}

	}
	
	public static void showMessageOnEDT(final Component parentComponent,
        final Object message){
		runOnEDT( new Runnable() {
			
			@Override
			public void run() {
				JOptionPane.showMessageDialog(parentComponent, message);
			}
		});
	}
	
	
	public static void showMessageOnEDT(final Component parentComponent,
			final Object message, final String title, final int messageType){
		runOnEDT( new Runnable() {
			
			@Override
			public void run() {
				JOptionPane.showMessageDialog(parentComponent,message,title,messageType);
			}
		});
	}
	
	public static int showConfirmOnEDT(final Component parentComponent,
	        final Object message, final String title, final int optionType){
		return runOnEDT( new Callable<Integer>() {
			
			@Override
			public Integer call() throws Exception {
				return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType);
			}
		});
	}
	

	
}
