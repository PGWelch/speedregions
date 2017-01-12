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

import java.util.HashMap;
import java.util.List;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier;

public class BufferCommand extends AbstractCommand{

	public BufferCommand() {
		super("Create a new region by buffering around an existing one."
				+ System.lineSeparator() + "Usage -buffer inputRegionId EPSGCoordSystem bufferDistance newRegionId."
				+ System.lineSeparator() + "If the region is enclosed by another, the buffer will be limited to within the enclosing region.", new String[]{"buffer"});
	}

	private Geometry transform(Geometry geometry,MathTransform transform, String EPSG){
		try {
			return JTS.transform(geometry, transform);			
		} catch (Exception e) {
			throw new RuntimeException("Error transforming the feature to / from EPSG grid " +EPSG);
		}
	}
	
	@Override
	public void execute(String[] args, State state) {
		// region id, projection, bufferkm
		if(args.length!=4){
			throw new RuntimeException("Buffer needs 4 arguments");
		}
		String regionid = args[0];
		String EPSG = args[1];
		String bufferDistance = args[2];
		String newRegionId = args[3];
		
		FeatureCollection fc = state.featureCollection;
		
		CreateResult result = createBuffer(regionid, EPSG, bufferDistance,newRegionId, fc);
		
		// overwrite existing
		String stdNewRegionid = TextUtils.stdString(newRegionId);
		for(int i =0 ; i< fc.getFeatures().size();i++){
			String regionId = TextUtils.findRegionType(fc.getFeatures().get(i));
			if(regionid!=null && TextUtils.stdString(regionId).equals(stdNewRegionid)){
				fc.getFeatures().set(i, result.newFeature);
				return;
			}
		}
		
		// add instead... if we have an enclosing feature add before that so it has correct priority
		if(result.enclosedBy!=null){
			int indx = fc.getFeatures().indexOf(result.enclosedBy);
			fc.getFeatures().add(indx, result.newFeature);
		}else{
			// otherwise add at the end
			fc.getFeatures().add(result.newFeature);			
		}
		
		// clear compiled as no longer valid
		state.compiled = null;
	}

	private static class CreateResult{
		Feature newFeature;
		Feature enclosedBy;
		
		CreateResult(Feature newFeature, Feature enclosedBy) {
			this.newFeature = newFeature;
			this.enclosedBy = enclosedBy;
		}
		
		
	}
	
	private CreateResult createBuffer(
			String regionid,
			String EPSG,
			String bufferDistance ,
			String newRegionId ,		
			FeatureCollection fc) {
		// get jts geoms and envelopes
		GeometryFactory factory = GeomUtils.newGeomFactory();
		List<Feature> features = fc.getFeatures();
		HashMap<Feature, Geometry> jts = new HashMap<>();
		final HashMap<Feature, Envelope> envelopes = new HashMap<>();
		for (Feature a : features) {
			if(!GeomUtils.isGeoJSONPolyOrMultiPoly(a.getGeometry())){
				throw new RuntimeException("Found feature which isn't polygon or multipolygon");
			}
			
			Geometry g = GeomUtils.toJTS(factory, a.getGeometry());
			jts.put(a, g);
			envelopes.put(a, g.getEnvelopeInternal());
		}
			
		// find the feature we're doing the buffer around
		Feature toBufferFeature=null;
		String regionId = TextUtils.stdString(regionid);
		for (Feature a : features) {
			String testId = TextUtils.findRegionType(a);
			if(testId!=null){
				testId = TextUtils.stdString(testId);
				if(testId.equals(regionId)){
					toBufferFeature = a;
					break;
				}
			}
		}
		if(toBufferFeature==null){
			throw new RuntimeException("Buffer: Cannot find feature with regionid " + regionId);
		}
		
		// Find the smallest area polygon enclosing the feature. 
		// TODO change from angular area to projected area.
		double smallestEnclosingAngularArea = Double.MAX_VALUE;
		Feature smallestEnclosing=null;
		Geometry g = jts.get(toBufferFeature);
		for (Feature a : features) {
			if(a==toBufferFeature){
				continue;
			}
			Geometry ga = jts.get(a);
			
			// do a buffer around the geometry to clear up issues like slivers
			ga = ga.buffer(0);
			
			if(ga.contains(g)){
				double area = ga.getArea();
				if(smallestEnclosing==null || area < smallestEnclosingAngularArea){
					smallestEnclosing = a;
					smallestEnclosingAngularArea = area;
				}
			}
		}
		
		// init the coord system to buffer in
		System.setProperty("org.geotools.referencing.forceXY", "true");
		CRSAuthorityFactory crsFac = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", null);
		CoordinateReferenceSystem system;
		CoordinateReferenceSystem wgs84;
		MathTransform toGrid;
		MathTransform fromGrid;
		try {
			system =crsFac.createCoordinateReferenceSystem(EPSG);
			wgs84 = crsFac.createCoordinateReferenceSystem("4326");
			toGrid = CRS.findMathTransform(wgs84, system, true);
			fromGrid = CRS.findMathTransform(system, wgs84, true);
			
		} catch (Exception e) {
			throw new RuntimeException("Error creating EPSG coordinate system " +EPSG);
		}
		

		// project the feature to the grid
		Geometry projectedFeature =transform(jts.get(toBufferFeature), toGrid, EPSG);

		// do buffer in the projected coords
		double bufferSize=0;
		try {
			bufferSize = Double.parseDouble(bufferDistance);
		} catch (Exception e) {
			throw new RuntimeException("Buffer: error with buffer distance: " + bufferDistance);
		}
		Geometry buffer=null;
		buffer = projectedFeature.buffer(bufferSize);
		
		// ensure we don't go outside the enclosing feature
		if(smallestEnclosing!=null){
			Geometry projectedEnclosing =transform(jts.get(smallestEnclosing), toGrid, EPSG);
			buffer = buffer.intersection(projectedEnclosing);
		}
		
		// simply the buffer. Tolerance of 1% buffer distance should be OK
		double simplifyTol = bufferSize/100;
		buffer = TopologyPreservingSimplifier.simplify(buffer, simplifyTol);
		
		// project back
		Geometry wgs84Buffer = transform(buffer, fromGrid, EPSG);
		String originalSource = TextUtils.findProperty(toBufferFeature, SpeedRegionConsts.SOURCE_KEY);
		String newSource = "Buffer distance " + bufferSize + " around region " + regionId +".";
		if(originalSource!=null){
			newSource = newSource + " " + originalSource;
		}
		
		// create feature
		Feature newFeature = new Feature();
		newFeature.setGeometry(GeomUtils.toGeoJSON(wgs84Buffer));
		newFeature.getProperties().put(SpeedRegionConsts.REGION_TYPE_KEY, newRegionId);
		newFeature.getProperties().put(SpeedRegionConsts.SOURCE_KEY, newSource);
		return new CreateResult(newFeature, smallestEnclosing);
	}

}
