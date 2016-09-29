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
package com.opendoorlogistics.speedregions.beans.files;

import org.geojson.FeatureCollection;

/**
 * Speed rules and a geoJSON feature collection of polygons and multipolygons.
 * @author Phil
 *
 */
public class UncompiledSpeedRulesFile extends AbstractSpeedRulesFile{
	private FeatureCollection geoJson = new FeatureCollection();

	public FeatureCollection getGeoJson() {
		return geoJson;
	}
	public void setGeoJson(FeatureCollection geojsonFeatureCollection) {
		this.geoJson = geojsonFeatureCollection;
	}

	
}
