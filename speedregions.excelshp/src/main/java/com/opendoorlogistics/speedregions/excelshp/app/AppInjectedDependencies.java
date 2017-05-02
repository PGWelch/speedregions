package com.opendoorlogistics.speedregions.excelshp.app;

import java.util.TreeMap;

import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.vividsolutions.jts.geom.LineString;

/**
 * Dependencies which the app needs to operated but are injected by the calling code.
 * This enables loose coupling between Graphhopper and the app, so we can get it to
 * work with multiple versions of Graphhopper (e.g. v0.5 and v0.7) 
 * @author Phil
 *
 */
public interface AppInjectedDependencies {
	boolean isDefaultSpeedsKnown(VehicleTypeTimeProfile flagEncoderType);
	double speedKmPerHour(VehicleTypeTimeProfile flagEncoderType, String highwayType);
	TreeMap<String, Double> speedKmPerHour(VehicleTypeTimeProfile flagEncoderType);
	void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile,
			BuiltTreeListener builtTreeCB,ProcessedWayListener handledWayCB);
	
	/**
	 * Latest Geotools version is messing up the global logging settings.
	 * This lets us reinit them again...
	 */
	void HACK_ReinitLogging();
	
	public static interface BuiltTreeListener{
		void onBuiltTree(SpeedRegionLookup lookup);
	}
	
	public static interface ProcessedWayListener{
		void onProcessedWay(VehicleTypeTimeProfile vehicleType,LineString lineString, String regionId,String highwayType,double lengthMetres,SpeedRule rule, double originalSpeedKPH, double speedRegionsSpeedKPH);
	}
}
