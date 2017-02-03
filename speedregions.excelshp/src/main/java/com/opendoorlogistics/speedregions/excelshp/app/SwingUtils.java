package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

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
	
	public static boolean showModalDialogOnEDT(JComponent contents,String title, String okButtonMessage, String cancelButtonMessage, ModalityType modalityType, final boolean systemExitOnDisposed){
		

		class Result{
			boolean OK=false;
		}
		final Result result = new Result();
		
		final JDialog dialog = new JDialog((Frame)null, title, true){
			@Override
			public void dispose(){
				super.dispose();
				if(systemExitOnDisposed){
					System.exit(0);
				}
			}
		};
		
		// ensure it doesn't block other dialogs
		if(modalityType!=null){
			dialog.setModalityType(modalityType);			
		}
		
		// See http://stackoverflow.com/questions/1343542/how-do-i-close-a-jdialog-and-have-the-window-event-listeners-be-notified
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		if(okButtonMessage!=null){
			buttonPane.add(new JButton(new AbstractAction(okButtonMessage) {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					result.OK = true;
					dialog.dispose();
				}
			}));	
		}

		if(cancelButtonMessage!=null){
			buttonPane.add(new JButton(new AbstractAction(cancelButtonMessage) {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					dialog.dispose();
				}
			}));	
		}
		
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.setLayout(new BorderLayout());
		panel.add(contents, BorderLayout.CENTER);
		panel.add(buttonPane,BorderLayout.SOUTH);
		
		// startup frame
		dialog.getContentPane().add(panel);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.pack();
		
		// This hopefully centres the dialog even though the parameter is null 
		// see http://stackoverflow.com/questions/213266/how-do-i-center-a-jdialog-on-screen
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);	
		
		return result.OK;
	}
	

}
