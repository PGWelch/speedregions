package com.opendoorlogistics.speedregions.excelshp.app;

public class AppSettings {
	private String pbfFile="";
	private String outdirectory="";
	private boolean useExcelShape;
	private String excelfile="";
	private String shapefile="";
	private String idFieldNameInShapefile="";
	private double gridCellMetres=200;
	
	public String getOutdirectory() {
		return outdirectory;
	}
	public void setOutdirectory(String outdirectory) {
		this.outdirectory = outdirectory;
	}
	public String getPbfFile() {
		return pbfFile;
	}
	public void setPbfFile(String pbfFile) {
		this.pbfFile = pbfFile;
	}
	public boolean isUseExcelShape() {
		return useExcelShape;
	}
	public void setUseExcelShape(boolean useExcelShape) {
		this.useExcelShape = useExcelShape;
	}
	public String getExcelfile() {
		return excelfile;
	}
	public void setExcelfile(String excelfile) {
		this.excelfile = excelfile;
	}
	public String getShapefile() {
		return shapefile;
	}
	public void setShapefile(String shapefile) {
		this.shapefile = shapefile;
	}
	public String getIdFieldNameInShapefile() {
		return idFieldNameInShapefile;
	}
	public void setIdFieldNameInShapefile(String idFieldNameInShapefile) {
		this.idFieldNameInShapefile = idFieldNameInShapefile;
	}
	public double getGridCellMetres() {
		return gridCellMetres;
	}
	public void setGridCellMetres(double gridCellMetres) {
		this.gridCellMetres = gridCellMetres;
	}
	
	
}
