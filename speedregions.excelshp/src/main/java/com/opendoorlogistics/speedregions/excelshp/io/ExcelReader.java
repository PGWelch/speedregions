package com.opendoorlogistics.speedregions.excelshp.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter;
import com.opendoorlogistics.speedregions.utils.ExceptionUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class ExcelReader {
	private static final Logger LOGGER = Logger.getLogger(ExcelReader.class.getName());		

	private static final DecimalFormat DF;

	static {
		// see http://stackoverflow.com/questions/16098046/how-to-print-double-value-without-scientific-notation-using-java
		DF = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		DF.setMaximumFractionDigits(340); // 340 = DecimalFormat.DOUBLE_FRACTION_DIGITS
	}
	
	private static String getTextValue(Cell cell, CellType treatAsCellType) {
		if (cell == null) {
			return null;
		}
		switch (treatAsCellType) {
		case STRING:
			return cell.getRichStringCellValue().getString();

		case NUMERIC:
			if (DateUtil.isCellDateFormatted(cell)) {
				return "";
			} else {

				// String ret = Double.toString(cell.getNumericCellValue());

				// see http://stackoverflow.com/questions/16098046/how-to-print-double-value-without-scientific-notation-using-java
				String ret = DF.format(cell.getNumericCellValue());

				// take off trailing empty 0 so we get nice integers
				if (ret.endsWith(".0")) {
					ret = ret.substring(0, ret.length() - 2);
				}

				return ret;
			}

		case BOOLEAN:
			return cell.getBooleanCellValue() ? "T" : "F";

		case FORMULA:
			return cell.getCellFormula();

		case BLANK:
			return null;
			
		default:
			return "";			
		}
	}

	private static String getFormulaSafeTextValue(Cell cell) {
		if (cell == null) {
			return null;
		}
		if (cell.getCellTypeEnum() == CellType.FORMULA) {
			return getTextValue(cell, cell.getCachedFormulaResultTypeEnum());
		} else {
			return getTextValue(cell, cell.getCellTypeEnum());
		}
	}
	
	/**
	 * Read the excel and return as a map of string tables.
	 * The map key is the sheet name in lower case with spaces trimmed (i.e. std string)
	 * @param file
	 * @return
	 */
	public static TreeMap<String,RawStringTable> readExcel(File file) {
		LOGGER.info("Reading Excel " + file.getAbsolutePath());
		InputStream inp = null;
		Workbook wb = null;
		TreeMap<String,RawStringTable> tables = new TreeMap<String,RawStringTable>();
		try {
			inp = new FileInputStream(file);

			wb = WorkbookFactory.create(inp);
			int ns = wb.getNumberOfSheets();
			for (int i = 0; i < ns; i++) {
				Sheet sheet = wb.getSheetAt(i);

				RawStringTable rst = new RawStringTable(sheet.getSheetName());
				//TreeMap<Integer, ColumnStats> columnStats = new TreeMap<>();
				tables.put(TextUtils.stdString(sheet.getSheetName()), rst);

				int firstRow = sheet.getFirstRowNum();
				int lastRow = sheet.getLastRowNum();
				int numberHeaderColumns = -1;

				// loop over each row including the (assumed) header row
				for (int r = firstRow; r <= lastRow; r++) {
					Row row = sheet.getRow(r);
					if (row != null) {

						ArrayList<String> stringRow = new ArrayList<>();
						rst.add(stringRow);

						// loop from the first cell position to the last non empty cell
						int lastCell = row.getLastCellNum() - 1;
						for (int c = 0; c <= lastCell; c++) {
							String value = null;
							Cell cell = row.getCell(c);
							if (cell != null) {
								value = getFormulaSafeTextValue(cell);
							}
							stringRow.add(value);

						}

						// initialise on header row
						if (numberHeaderColumns == -1) {
							numberHeaderColumns = stringRow.size();
						}
						else{
							// ensure we fill to the header size
							while(stringRow.size() < numberHeaderColumns){
								stringRow.add(null);
							}
						}
					}
				}

			}
		}
		catch(Exception e){
			throw ExceptionUtils.asUncheckedException(e);
		}
		finally{
			try {
				if(wb!=null){
					wb.close();					
				}
			}
			catch (IOException e1) {
				throw ExceptionUtils.asUncheckedException(e1);
			}		
		}

		LOGGER.info("Finished reading Excel " + file.getAbsolutePath());
		return tables;
	}

}
