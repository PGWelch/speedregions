package com.opendoorlogistics.speedregions.commandline;

import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;

public class Utils {
	public static void addToFile(UncompiledSpeedRulesFile addToThis, UncompiledSpeedRulesFile addMe){
		addToThis.getRules().addAll(addMe.getRules());
		addToThis.getGeoJson().getFeatures().addAll(addMe.getGeoJson().getFeatures());		
	}
}
