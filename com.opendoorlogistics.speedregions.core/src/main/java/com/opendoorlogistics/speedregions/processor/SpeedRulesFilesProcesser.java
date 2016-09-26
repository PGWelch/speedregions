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
package com.opendoorlogistics.speedregions.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.MultiPolygon;
import org.geojson.Polygon;

import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedRulesFile;
import com.rits.cloning.Cloner;
import com.vividsolutions.jts.geom.GeometryFactory;

public class SpeedRulesFilesProcesser {
	private static class TempPolygonRecord implements Comparable<TempPolygonRecord> {
		int fileIndex;
		int positionInFile;
		Polygon polygon;
		String stdRegionType;
		long globalFeatureIndex;
		long polyNumber;

		TempPolygonRecord(int fileIndex, int positionInFile, Polygon polygon, String stdRegionType, long globalFeatureIndex,
				long uniquePolyNumber) {
			this.fileIndex = fileIndex;
			this.positionInFile = positionInFile;
			this.polygon = polygon;
			this.stdRegionType = stdRegionType;
			this.globalFeatureIndex = globalFeatureIndex;
			this.polyNumber = uniquePolyNumber;
		}

		public int compareTo(TempPolygonRecord o) {
			// first positions in files first
			int diff = Integer.compare(positionInFile, o.positionInFile);

			// then files
			if (diff == 0) {
				diff = Integer.compare(fileIndex, o.fileIndex);
			}

			// then global polygon just to ensure we always add to the treeset
			if (diff == 0) {
				diff = Long.compare(polyNumber, o.polyNumber);
			}

			return diff;
		}

	}

	/**
	 * Build the quadtree from the speed rules files. All regionId strings in the quadtree are standardised.
	 * 
	 * @param files
	 * @param geomFactory
	 * @param minDiagonalLengthMetres
	 * @return
	 */
	public SpatialTreeNode buildQuadtree(List<SpeedRulesFile> files, GeometryFactory geomFactory, double minDiagonalLengthMetres) {

		TreeSet<TempPolygonRecord> prioritised = prioritisePolygons(files);

		RegionSpatialTreeBuilder builder = new RegionSpatialTreeBuilder(geomFactory, minDiagonalLengthMetres);
		for (TempPolygonRecord poly : prioritised) {
			com.vividsolutions.jts.geom.Polygon jtsPolygon = GeomConversion.toJTS(geomFactory, poly.polygon);
			builder.add(jtsPolygon, poly.stdRegionType);
		}

		return builder.build();
	}

	public List<SpeedRule> validateSpeedRules(List<SpeedRulesFile> files) {
		final HashMap<String, SpeedRule> ruleIds = new HashMap<>();
		ArrayList<SpeedRule> allRules = new ArrayList<>();
		for (SpeedRulesFile rules : files) {
			if (rules.getRules() == null) {
				continue;
			}

			for (SpeedRule rule : rules.getRules()) {

				// check id is unique if used
				if (rule.getId() != null) {
					String stdId = RegionProcessorUtils.stdString(rule.getId());
					if (ruleIds.containsKey(stdId)) {
						throw new RuntimeException("Duplicate rule id: " + stdId);
					}
					ruleIds.put(stdId, rule);
				}
				allRules.add(rule);
			}
		}

		class ParentValidator {
			HashSet<String> found = new HashSet<>();

			void validate(String parentId) {
				parentId = RegionProcessorUtils.stdString(parentId);
				if (found.contains(parentId)) {
					throw new RuntimeException("Found circular dependencies in rule parent ids around rule id " + parentId);
				}
				found.add(parentId);

				SpeedRule parent = ruleIds.get(parentId);
				if (parent == null) {
					throw new RuntimeException("Cannot find parent rule with id " + parentId);
				}

				if (parent.getParentId() != null) {
					validate(parent.getParentId());
				}
			}
		}

		// validate parent ids
		for (SpeedRule rule : allRules) {
			if (rule.getParentId() != null) {
				new ParentValidator().validate(rule.getParentId());
			}
		}

		// this also does some validation
		createSelfContainedRulesMap(allRules);

		return allRules;
	}

	/**
	 * Create a map which lets you lookup the speed rule by encoder first and then by regiontype. All string keys are
	 * standardised. Parent id relations are removed so the rules are self-contained
	 * 
	 * @param files
	 * @return
	 */
	public TreeMap<String, TreeMap<String, SpeedRule>> createSelfContainedRulesMap(List<SpeedRule> rules) {
		// build id map and clone rules
		rules = new ArrayList<>(rules);
		final HashMap<String, SpeedRule> originalRules = new HashMap<>();
		int n = rules.size();
		for(int i =0 ; i <n;i++){
			
			// save to original map first
			SpeedRule rule =rules.get(i);
			if(rule.getId()!=null){
				originalRules.put(RegionProcessorUtils.stdString(rule.getId()), rule);
			}
			
			// then clone and standardise road type strings
			SpeedRule cloned= Cloner.standard().deepClone(rule);
			if(rule.getSpeedsByRoadType()!=null){
				cloned.getSpeedsByRoadType().clear();
				for(Map.Entry<String, Float> entry : rule.getSpeedsByRoadType().entrySet()){
					cloned.getSpeedsByRoadType().put(RegionProcessorUtils.stdString(entry.getKey()), entry.getValue());
				}					
			}
			rules.set(i, cloned);
		}

		// collapse parent relations
		for(SpeedRule rule : rules){
			if(rule.getSpeedsByRoadType()==null){
				rule.setSpeedsByRoadType(new TreeMap<String, Float>());
			}
			
			String parentId = rule.getParentId();
			while(parentId!=null){
				
				// standardise parent string and find parent (should alrewady be validated so should exist)
				parentId = RegionProcessorUtils.stdString(parentId);
				SpeedRule originalParent = originalRules.get(parentId);
				
				// combine multiplier
				rule.setMultiplier(rule.getMultiplier() * originalParent.getMultiplier());
				
				// combine speeds by type
				if(originalParent.getSpeedsByRoadType()!=null){
					for(Map.Entry<String, Float> entry : originalParent.getSpeedsByRoadType().entrySet()){
						String type = entry.getKey();
						type = RegionProcessorUtils.stdString(type);
						if(!rule.getSpeedsByRoadType().containsKey(type)){
							rule.getSpeedsByRoadType().put(type, entry.getValue());
						}
					}					
				}
				
				// go to next parent
				parentId = originalParent.getParentId();
			}
			
			// blank parent id now we've taken all its information
			rule.setParentId(null);
		}
		
		TreeMap<String, TreeMap<String, SpeedRule>> ret = new TreeMap<String, TreeMap<String, SpeedRule>>();
		for (SpeedRule rule : rules) {
			for (String encoder : rule.getMatchRule().getFlagEncoders()) {
				encoder = RegionProcessorUtils.stdString(encoder);
				TreeMap<String, SpeedRule> map4Encoder = ret.get(encoder);
				if (map4Encoder == null) {
					map4Encoder = new TreeMap<String, SpeedRule>();
					ret.put(encoder, map4Encoder);
				}

				for (String regionType : rule.getMatchRule().getRegionTypes()) {
					regionType = RegionProcessorUtils.stdString(regionType);
					SpeedRule prexisting = map4Encoder.get(regionType);
					if (prexisting != null) {
						throw new RuntimeException("More than one speed rule exists for encoder type " + encoder + " and "
								+ SpeedRegionConsts.REGION_TYPE_KEY + " + " + regionType + ".");
					}
					map4Encoder.put(regionType, rule);
				}
			}
		}

		return ret;
	}

	private TreeSet<TempPolygonRecord> prioritisePolygons(List<SpeedRulesFile> files) {
		// validateCountryCodes(files);

		// prioritise all polygons and link them up to the local rules in their files
		int nfiles = files.size();
		TreeSet<TempPolygonRecord> prioritisedPolygons = new TreeSet<SpeedRulesFilesProcesser.TempPolygonRecord>();
		long globalFeatureIndex = 0;
		for (int ifile = 0; ifile < nfiles; ifile++) {
			SpeedRulesFile file = files.get(ifile);

			FeatureCollection fc = file.getGeoJson();
			if (fc != null) {
				int nfeatures = fc.getFeatures().size();
				for (int iFeat = 0; iFeat < nfeatures; iFeat++) {
					Feature feature = fc.getFeatures().get(iFeat);
					String regionType = RegionProcessorUtils.findRegionType(feature);

					if (regionType == null) {
						throw new RuntimeException("Found record without a " + SpeedRegionConsts.REGION_TYPE_KEY + " property");
					}

					if (regionType.trim().length() == 0) {
						throw new RuntimeException("Found record with an empty " + SpeedRegionConsts.REGION_TYPE_KEY + " property");
					}

					regionType = RegionProcessorUtils.stdString(regionType);

					// if (stdRegionIds.contains(regionId)) {
					// throw new RuntimeException(
					// "RegionId " + regionId + " was found in more than one feature. RegionIds should be unique."
					// + " Use a multipolygon if you want a region spanning several polygons.");
					// }
					// stdRegionIds.add(regionId);

					if (feature.getGeometry() == null) {
						throw new RuntimeException("Found feature without geometry");
					}

					if (GeomConversion.isGeoJSONPoly(feature.getGeometry())) {
						prioritisedPolygons.add(new TempPolygonRecord(ifile, iFeat, (Polygon) feature.getGeometry(), regionType,
								globalFeatureIndex, prioritisedPolygons.size()));
					} else if (GeomConversion.isGeoJSONMultiPoly(feature.getGeometry())) {
						for (Polygon polygon : GeomConversion.toGeoJSONPolygonList((MultiPolygon) feature.getGeometry())) {
							prioritisedPolygons.add(new TempPolygonRecord(ifile, iFeat, polygon, regionType, globalFeatureIndex,
									prioritisedPolygons.size()));
						}

					} else {
						throw new RuntimeException("Found feature with non-polygon geometry type");
					}

					globalFeatureIndex++;
				}
			}
		}

		return prioritisedPolygons;
	}

	// private static CountryCode validateCountryCode(String id) {
	// if (id == null) {
	// throw new RuntimeException("Found rules file with no country code set.");
	// }
	//
	// id = id.trim();
	//
	// if (id.length() != 2) {
	// throw new RuntimeException("Found rules file with a country code not equal to 2 characters.");
	// }
	//
	// CountryCode code = CountryCode.getByCodeIgnoreCase(id);
	// if (code == null) {
	// throw new RuntimeException(
	// "Failed to identify ISO 3166-1 country code in rules file (see
	// https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)."
	// + " Code was " + id);
	// }
	// return code;
	// }
	
	public static void addToFile(SpeedRulesFile addToThis, SpeedRulesFile addMe){
		addToThis.getRules().addAll(addMe.getRules());
		addToThis.getGeoJson().getFeatures().addAll(addMe.getGeoJson().getFeatures());		
	}
}
