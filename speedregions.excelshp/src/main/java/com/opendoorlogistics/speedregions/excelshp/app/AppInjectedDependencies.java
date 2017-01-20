package com.opendoorlogistics.speedregions.excelshp.app;

import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;

/**
 * Dependencies which the app needs to operated but are injected by the calling code.
 * This enables loose coupling between Graphhopper and the app, so we can get it to
 * work with multiple versions of Graphhopper (e.g. v0.5 and v0.7) 
 * @author Phil
 *
 */
public interface AppInjectedDependencies {
	double speedKmPerHour(String flagEncoderType, String highwayType);
	void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile);
	
	/**
	 * Latest Geotools version is messing up the global logging settings.
	 * This lets us reinit them again...
	 */
	void HACK_ReinitLogging();
}
