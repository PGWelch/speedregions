package com.opendoorlogistics.speedregions.excelshp.io;

import java.util.ArrayList;
import java.util.List;

import com.opendoorlogistics.speedregions.utils.TextUtils;

public class RawStringTable {
	private ArrayList<List<String>> list = new ArrayList<>();
	private String name;

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

	

}