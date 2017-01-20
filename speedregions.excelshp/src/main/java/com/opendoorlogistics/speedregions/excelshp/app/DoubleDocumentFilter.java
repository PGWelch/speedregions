package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

/**
 * Class taken from answer here http://stackoverflow.com/questions/11093326/restricting-jtextfield-input-to-integers
 * Modified to test for doubles
 * @author Phil
 *
 */
public class DoubleDocumentFilter extends DocumentFilter {
	private final Component parent;
	
	public DoubleDocumentFilter(Component parent) {
		this.parent = parent;
	}

	@Override
	public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {

		Document doc = fb.getDocument();
		StringBuilder sb = new StringBuilder();
		sb.append(doc.getText(0, doc.getLength()));
		sb.insert(offset, string);

		if (isOkDouble(sb.toString())) {
			super.insertString(fb, offset, string, attr);
		} else {
			warnUser();
		}
	}

	private void warnUser(){
		JOptionPane.showMessageDialog(parent, "Please enter only a valid number into this box");//, JOptionPane.OK_OPTION);
	}
	
	private boolean isOkDouble(String text) {
		try {
			Double.parseDouble(text);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {

		Document doc = fb.getDocument();
		StringBuilder sb = new StringBuilder();
		sb.append(doc.getText(0, doc.getLength()));
		sb.replace(offset, offset + length, text);

		if (isOkDouble(sb.toString())) {
			super.replace(fb, offset, length, text, attrs);
		} else {
			warnUser();
		}

	}

	@Override
	public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
		Document doc = fb.getDocument();
		StringBuilder sb = new StringBuilder();
		sb.append(doc.getText(0, doc.getLength()));
		sb.delete(offset, offset + length);

		if (isOkDouble(sb.toString())) {
			super.remove(fb, offset, length);
		} else {
			warnUser();
		}

	}
}
