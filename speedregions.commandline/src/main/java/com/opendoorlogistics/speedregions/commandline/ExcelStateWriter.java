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
package com.opendoorlogistics.speedregions.commandline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geojson.Feature;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTable;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTableColumn;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class ExcelStateWriter {
	
	
	
	public void exportState(State state , File file){
		// write polygons
		ExportTable polygons = new ExportTable();
		polygons.setName("Polygons");
		polygons.getHeader().add(new ExportTableColumn("Number", JsonFormatTypes.NUMBER));
		polygons.getHeader().add(new ExportTableColumn(SpeedRegionConsts.REGION_TYPE_KEY, JsonFormatTypes.STRING));
		polygons.getHeader().add(new ExportTableColumn(SpeedRegionConsts.SOURCE_KEY, JsonFormatTypes.STRING));
		polygons.getHeader().add(new ExportTableColumn("Geom", JsonFormatTypes.STRING));
		int i =0 ;
		for(Feature feature : state.featureCollection.getFeatures()){
			List<String> row = new ArrayList<>();
			row.add(Integer.toString(i++));
			row.add(TextUtils.findRegionType(feature));
			row.add(TextUtils.findProperty(feature, SpeedRegionConsts.SOURCE_KEY));
			if(feature.getGeometry()!=null){
				row.add(GeomUtils.toWKT(GeomUtils.toJTS(GeomUtils.newGeomFactory(), feature.getGeometry())));				
			}
			polygons.getRows().add(row);
		}	

		ExportTable tree = new ExportTable(); 
		if(state.compiled!=null){
			tree= ExcelWriter.exportTree(state.compiled);			
		}
			
		ExcelWriter.writeSheets(file, polygons,tree);
	}

}
