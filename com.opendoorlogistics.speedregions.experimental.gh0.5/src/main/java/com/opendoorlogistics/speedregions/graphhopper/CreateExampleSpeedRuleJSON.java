package com.opendoorlogistics.speedregions.graphhopper;

import java.util.Map;

import com.graphhopper.routing.util.CarFlagEncoder;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;

public class CreateExampleSpeedRuleJSON {
	public static void main(String[] strArgs) throws Exception {
		final SpeedRule rule = new SpeedRule();
		rule.setSpeedUnit(SpeedUnit.MILES_PER_HOUR);
		
		class TmpEncoder extends CarFlagEncoder{
			TmpEncoder(){	
				// copy default values over
				for(Map.Entry<String, Integer> entry : defaultSpeedMap.entrySet()){
					double val =  SpeedUnit.convert(entry.getValue(), SpeedUnit.KM_PER_HOUR, rule.getSpeedUnit());
					// round to 1 dp
					val = Math.round(val*10.0)/10.0;
					rule.getSpeedsByRoadType().put(entry.getKey(),(float) val);
				}
			};	
		};
		rule.getMatchRule().getFlagEncoders().add("car");
		rule.getMatchRule().getRegionTypes().add("UK");
		rule.setId("TypicalUKSpeeds");
		new TmpEncoder();
		
		System.out.println(rule);
	}
}
