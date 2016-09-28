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
package com.opendoorlogistics.speedregions.beans;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * For a given flag encoder and regionid, the speed rule is applied if the speed rule contains both the regionid in its
 * list of regions, and the flag encoder name (e.g. "car", "foot") in its list of flag encoders.
 * 
 * @author Phil
 *
 */
public class SpeedRule extends JSONToString {
	private String id;
	private String parentId;
	private Map<String, Float> speedsByRoadType = new TreeMap<String, Float>();
	private double multiplier = 1;
	private SpeedUnit speedUnit = SpeedUnit.DEFAULT;
	private MatchRule matchRule = new MatchRule();

	public Map<String, Float> getSpeedsByRoadType() {
		return speedsByRoadType;
	}

	public void setSpeedsByRoadType(Map<String, Float> speedsByRoadType) {
		this.speedsByRoadType = speedsByRoadType;
	}

	public double getMultiplier() {
		return multiplier;
	}

	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
	}

	public SpeedUnit getSpeedUnit() {
		return speedUnit;
	}

	public void setSpeedUnit(SpeedUnit speedUnit) {
		this.speedUnit = speedUnit;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public MatchRule getMatchRule() {
		return matchRule;
	}

	public void setMatchRule(MatchRule matchRule) {
		this.matchRule = matchRule;
	}

	/**
	 * Apply rule and return speed in km/hr
	 * @param highwayType
	 * @param originalSpeedKmH
	 * @return
	 */
	@JsonIgnore
	public double applyRule(String highwayType, double originalSpeedKmH) {

		if(highwayType!=null){
			Float speed = getSpeedsByRoadType().get(highwayType);
			if (speed != null) {
				return SpeedUnit.convert(speed, getSpeedUnit(), SpeedUnit.KM_PER_HOUR);
			}			
		}

		// If no rule set, use the speed and apply the multiplier
		double ret = originalSpeedKmH * getMultiplier();
		return ret;
	}
}
