package com.opendoorlogistics.speedregions.excelshp.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class VehicleTypeTimeProfile implements Comparable<VehicleTypeTimeProfile>{
	private VehicleType vehicleType;
	private String timeProfileId;
	
	public VehicleTypeTimeProfile(VehicleType vehicleType, String timeProfileId) {
		this.vehicleType = vehicleType;
		this.timeProfileId = timeProfileId;
	}
	
	/**
	 * Return the type and profile id if this is a speed profiles table.
	 * Null otherwise.
	 * @param tablename
	 * @return
	 */
	public static VehicleTypeTimeProfile parseTableName(String tablename ){
		tablename  = TextUtils.stdString(tablename);
		String stdPrefix = TextUtils.stdString(IOStringConstants.SPEED_PROFILES_TABLENAME);
		if(!tablename.startsWith(stdPrefix)){
			// not a speed profiles table
			return null;
		}

		// remove prefix from tablename
		tablename = TextUtils.stdString(tablename.replaceFirst(stdPrefix, ""));
		
		VehicleTypeTimeProfile ret = parseCombinedId(tablename);

		return ret;
	}

	public static VehicleTypeTimeProfile parseCombinedId(String combinedId) {
		// sort vehicle types by longest standardised graphhopper name first
		ArrayList<VehicleType>types = new ArrayList<>(Arrays.asList(VehicleType.values()));
		types.sort(new Comparator<VehicleType>() {

			@Override
			public int compare(VehicleType o1, VehicleType o2) {
				return Integer.compare(TextUtils.stdString(o2.getGraphhopperName()).length(), TextUtils.stdString(o1.getGraphhopperName()).length());
			}
		});
		
	
		// identify type and remove from tablename
		VehicleType type=null;
		for(VehicleType t:types){
			String std=TextUtils.stdString(t.getGraphhopperName());
			if(combinedId.contains(std)){
				type = t;
				combinedId = TextUtils.stdString(combinedId.replaceFirst(std, ""));
				break;
			}
	
		}
		
		
		if(type==null){
			throw new RuntimeException("Failed to identify the vehicle type in " + combinedId);
		}
		
		
		if(combinedId.startsWith("_")){
			combinedId=combinedId.replaceFirst("_", "");
		}
		
		VehicleTypeTimeProfile ret= new VehicleTypeTimeProfile(type, combinedId);
		return ret;
	}
	
	public VehicleTypeTimeProfile(){}
	
	public VehicleType getVehicleType() {
		return vehicleType;
	}
	public void setVehicleType(VehicleType vehicleType) {
		this.vehicleType = vehicleType;
	}
	public String getTimeProfileId() {
		return timeProfileId;
	}
	public void setTimeProfileId(String timeProfileId) {
		this.timeProfileId = timeProfileId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((timeProfileId == null) ? 0 : timeProfileId.hashCode());
		result = prime * result + ((vehicleType == null) ? 0 : vehicleType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VehicleTypeTimeProfile other = (VehicleTypeTimeProfile) obj;
		if (timeProfileId == null) {
			if (other.timeProfileId != null)
				return false;
		} else if (!timeProfileId.equals(other.timeProfileId))
			return false;
		if (vehicleType != other.vehicleType)
			return false;
		return true;
	}
	
	public String getCombinedId(){
		return vehicleType.getGraphhopperName() +(timeProfileId!=null && timeProfileId.length()>0?"_"+ timeProfileId:"");
	}

	@Override
	public int compareTo(VehicleTypeTimeProfile o) {
		int diff = vehicleType.compareTo(o.vehicleType);
		if(diff==0){
			diff = timeProfileId.compareTo(o.timeProfileId);
		}
		return diff;
	}
	
	@Override
	public String toString(){
		return getCombinedId();
	}
}
