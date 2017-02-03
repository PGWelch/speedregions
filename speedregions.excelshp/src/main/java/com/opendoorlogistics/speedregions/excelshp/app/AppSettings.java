package com.opendoorlogistics.speedregions.excelshp.app;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AppSettings {
	private String pbfFile="";
	private String outdirectory="";
	private boolean useExcelShape;
	private String excelfile="";
	private String shapefile="";
	private String idFieldNameInShapefile="";
	private boolean car=true;
	private boolean motorcycle=false;
	private boolean bike=false;
	private boolean foot=false;
	private double gridCellMetres=100;
	private boolean reportInMiles = false;
	
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
	public boolean isCar() {
		return car;
	}
	public void setCar(boolean car) {
		this.car = car;
	}
	public boolean isMotorcycle() {
		return motorcycle;
	}
	public void setMotorcycle(boolean motorcycle) {
		this.motorcycle = motorcycle;
	}
	public boolean isBike() {
		return bike;
	}
	public void setBike(boolean bike) {
		this.bike = bike;
	}
	public boolean isFoot() {
		return foot;
	}
	public void setFoot(boolean foot) {
		this.foot = foot;
	}
	
	@JsonIgnore
	public boolean isEnabled(VehicleType type){
		switch(type){
		case CAR:
			return isCar();
		
		case MOTORCYCLE:
			return isMotorcycle();
			
		case BICYCLE:
			return isBike();
			
		case FOOT:
			return isFoot();
		};
		
		throw new IllegalArgumentException();
	}
	
	
	@JsonIgnore
	public void setEnabled(VehicleType type, boolean enabled){
		switch(type){
		case CAR:
			setCar(enabled);
			return;
		
		case MOTORCYCLE:
			setMotorcycle(enabled);
			return;
			
		case BICYCLE:
			setBike(enabled);
			return;
			
		case FOOT:
			setFoot(enabled);
			return;
		};
		
		throw new IllegalArgumentException();
	}
	public boolean isReportInMiles() {
		return reportInMiles;
	}
	public void setReportInMiles(boolean reportInMiles) {
		this.reportInMiles = reportInMiles;
	}
	
	
}
