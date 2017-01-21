package com.opendoorlogistics.speedregions.excelshp.io;

/*
 * Copyright 2016 Open Door Logistics Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTable;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTableColumn;
import com.opendoorlogistics.speedregions.utils.GeomUtils;

public class ExcelWriter {
	
	private static void writeToCell(String value, JsonFormatTypes type, Cell cell) {
		
		if(type == JsonFormatTypes.STRING){
			// HACK... POI is giving an exception when we try to write cells greater than
			// the Excel 2007 max length. However we don't care about this if we're just viewing
			// the results in ODL Studio, so we bypass the check for max cell length.
			XSSFCell hack = (XSSFCell)cell;
			hack.getCTCell().setV(value);
		}else{
			cell.setCellValue(value);			
		}
		
		if (value != null) {
			boolean setToString = true;
			if (value.length() > 0 && (type == JsonFormatTypes.NUMBER || type == JsonFormatTypes.INTEGER)) {
				setToString = false;
				try {
					cell.setCellValue(Double.parseDouble(value));
					cell.setCellType(Cell.CELL_TYPE_NUMERIC);
				}
				catch (Exception e) {
					setToString = true;
				}
			}

			if (setToString) {
				cell.setCellType(Cell.CELL_TYPE_STRING);
			}
		}

	}
	

	public static class ExportTableColumn{
		private String name;
		private JsonFormatTypes formatType;
		
		
		public ExportTableColumn(String name, JsonFormatTypes formatType) {
			this.name = name;
			this.formatType = formatType;
		}
		
		public ExportTableColumn(){}
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public JsonFormatTypes getFormatType() {
			return formatType;
		}
		public void setFormatType(JsonFormatTypes formatType) {
			this.formatType = formatType;
		}
		
		
	}
	
	public static class ExportTable{
		private String name;
		private List<ExportTableColumn> header = new ArrayList<>();
		private List<List<String>> rows = new ArrayList<>();
		
		
		public List<ExportTableColumn> getHeader() {
			return header;
		}
		public void setHeader(List<ExportTableColumn> header) {
			this.header = header;
		}
		public List<List<String>> getRows() {
			return rows;
		}
		public void setRows(List<List<String>> rows) {
			this.rows = rows;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}

	}
	
	public static void writeSheets(File file, ExportTable...tables) {
		// create empty workbook with a bold font style
		XSSFWorkbook wb = new XSSFWorkbook();
		XSSFCellStyle headerStyle = wb.createCellStyle();
		XSSFFont boldfont = wb.createFont();
		boldfont.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);
		boldfont.setFontHeight(12);
		headerStyle.setFont(boldfont);

		// fill workbook
		for(ExportTable table : tables){
			Sheet sheet = wb.createSheet(table.getName());
			
			Row headerRow = sheet.createRow(0);
			for (int c = 0; c < table.getHeader().size(); c++) {
				Cell cell = headerRow.createCell(c);	
				cell.setCellStyle(headerStyle);
				cell.setCellValue(table.getHeader().get(c).getName());				
			}

			List<List<String>> rows = table.getRows();
			
			int nr = rows.size();
			for (int r = 0; r < nr; r++) {
				Row row = sheet.createRow(r+1);
				List<String> srcRow = rows.get(r);
				int nc = srcRow.size();
				for (int c = 0; c < nc; c++) {
					//JsonFormatTypes type = table.getColumnType(c);
					Cell cell = row.createCell(c);
					String value = srcRow.get(c);

					writeToCell(value, c < table.getHeader().size()? table.getHeader().get(c).getFormatType():JsonFormatTypes.STRING, cell);
					
				}
			}
		}

		// try saving
		FileOutputStream fileOut = null;
		try {
			fileOut = new FileOutputStream(file);
			wb.write(fileOut);
		}
		catch (Exception e) {
			// TODO: handle exception
		}
		finally {

			try {
				if (fileOut != null) {
					fileOut.close();
				}
			}
			catch (Exception e2) {
				throw new RuntimeException(e2);
			}

			try {
				if (wb != null) {
					wb.close();
				}
			}
			catch (Exception e2) {
				throw new RuntimeException(e2);
			}
		}
	}
	
	public static ExportTable exportTree(SpatialTreeNode spatialTree) {
		// write quadtree (leaves only?)
		final ExportTable tree = new ExportTable();
		tree.setName("SpatialTreeLeaves");
		tree.getHeader().add(new ExportTableColumn("Number", JsonFormatTypes.NUMBER));
		tree.getHeader().add(new ExportTableColumn(SpeedRegionConsts.REGION_TYPE_KEY, JsonFormatTypes.STRING));
		tree.getHeader().add(new ExportTableColumn("Priority", JsonFormatTypes.NUMBER));
		tree.getHeader().add(new ExportTableColumn("Geom", JsonFormatTypes.STRING));
		
		class Recurser{
			int i;
			void recurse(SpatialTreeNode n){
				if( n.getChildren().size()==0){
					List<String> row = new ArrayList<>();
					tree.getRows().add(row);
					row.add(Integer.toString(i++));
					row.add(n.getRegionType());
					row.add(Long.toString(n.getAssignedPriority()));
					row.add(GeomUtils.toWKT(GeomUtils.toJTS(n.getBounds())));
				}
				for(SpatialTreeNode child:n.getChildren()){
					recurse(child);
				}
				
			}
		}
		Recurser recurser = new Recurser();
	
			recurser.recurse(spatialTree);
		return tree;
	}

	
}
