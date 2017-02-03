package com.opendoorlogistics.speedregions.excelshp.io;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.opendoorlogistics.speedregions.utils.TextUtils;

public class RawStringTable {
	private ArrayList<List<String>> list = new ArrayList<>();
	private String name;
	private String description;

	public RawStringTable(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public List<String> getHeaderRow(){
		return list.get(0);
	}
	
	public List<String> getDataRow(int dataRowIndex){
		return list.get(dataRowIndex+1);
	}
	
	public int getNbDataRows(){
		return list.size()-1;
	}
	
	public int getColumnIndex(String name){
		List<String> header = getHeaderRow();
		for(int i =0 ; i<header.size();i++){
			if(TextUtils.stdString(name).equals(TextUtils.stdString(header.get(i)))){
				return i;
			}
	
		}
		return -1;
	}
	

	public int getNbColumns(){
		return getHeaderRow().size();
	}
	

	public void add(List<String> row){
		list.add(row);
	}


	public List<List<String>> getDataRows(){
		return list.subList(1, list.size());
	}

	public String toCSV(){
		StringBuilder builder = new StringBuilder();
		for(List<String> row:list){
			int n = row.size();
			for(int i =0 ; i<n;i++){
				if(i>0){
					builder.append(",");
				}
				builder.append(row.get(i));
			}
			builder.append(System.lineSeparator());
		}
		return builder.toString();
	}
	
	@Override
	public String toString(){
		return toCSV();
	}
	
	public static JScrollPane toJTable(final RawStringTable rst, String borderText){
		
		JTable table= new JTable(new AbstractTableModel(){

			@Override
			public int getRowCount() {
				return rst.getNbDataRows();
			}

			@Override
			public int getColumnCount() {
				return rst.getNbColumns();
			}

			@Override
			public Object getValueAt(int rowIndex, int col) {
				List<String> row =rst.getDataRow(rowIndex);
				if(col<row.size()){
					return row.get(col);
				}
				return null;
			}
			
			@Override
			public String getColumnName(int column) {
				return rst.getHeaderRow().get(column);
			}
		});
		
		// Standard setup...
		table.getTableHeader().setReorderingAllowed(false);
		
		// Let headers wrap
		table.getTableHeader().setDefaultRenderer(new TableCellRenderer() {
			
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				JTextArea cell = new JTextArea(value.toString());
				cell.setLineWrap(true);
				cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
				cell.setFont(cell.getFont().deriveFont( Font.BOLD));
				return cell;
			}
		});
		
		// Alternating table colours
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {

	        @Override
	        public Component getTableCellRendererComponent(JTable table, 
	                Object value, boolean isSelected, boolean hasFocus,
	                int row, int column) {
	            Component c = super.getTableCellRendererComponent(table, 
	                value, isSelected, hasFocus, row, column);
	            c.setBackground(row%2!=0 ? Color.white : new Color(255, 255, 225));                        
	            return c;
	        };
	    });
		
		// Needed to work in a scrollpane properly
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		JScrollPane tableScrollPane = new JScrollPane(table);
		tableScrollPane.setPreferredSize(new Dimension(800, 250));
		
		if(borderText!=null){
			tableScrollPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5),
					BorderFactory.createTitledBorder(borderText)));
			
		}
		return tableScrollPane;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	
}