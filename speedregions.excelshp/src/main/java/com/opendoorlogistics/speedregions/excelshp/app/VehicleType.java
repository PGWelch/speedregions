package com.opendoorlogistics.speedregions.excelshp.app;

public enum VehicleType {
	CAR("car",true),
	MOTORCYCLE("motorcycle",true),
	BICYCLE("bike",false),
	FOOT("foot",false);
	
	
	private final String graphhopperName;
	private final boolean speedRegionsSupported;
	
	private VehicleType(String graphhopperName, boolean speedRegionsSupported) {
		this.graphhopperName = graphhopperName;
		this.speedRegionsSupported= speedRegionsSupported;
	}

	public String getGraphhopperName() {
		return graphhopperName;
	}

	public boolean isSpeedRegionsSupported() {
		return speedRegionsSupported;
	}

	
}
