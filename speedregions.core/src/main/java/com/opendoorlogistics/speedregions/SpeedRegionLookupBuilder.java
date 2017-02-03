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
package com.opendoorlogistics.speedregions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.geojson.FeatureCollection;

import com.opendoorlogistics.speedregions.SpeedRegionLookup.SpeedRuleLookup;
import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.spatialtree.QueryProcessor;
import com.opendoorlogistics.speedregions.spatialtree.TreeBuilder;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Geometry;

public class SpeedRegionLookupBuilder {
	public static final double DEFAULT_MIN_CELL_LENGTH_METRES = 10;
//	private static final Logger LOGGER = Logger.getLogger(SpeedRegionLookupBuilder.class.getName());

	/**
	 * Load the lookup from a text file containing {@link com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile} in JSON form.
	 * The spatial tree in the file has already been compiled.
	 * @param compiled
	 * @return
	 */
	public static SpeedRegionLookup loadFromCompiledFile(File built) {
		return fromCompiled(TextUtils.fromJSON(built, CompiledSpeedRulesFile.class));
	}

	/**
	 * Load the lookup from a text file containing {@link com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile} in JSON form.
	 * The spatial tree will be compiled.
	 * @param uncompiledFile
	 * @param minCellLengthMetres
	 * @return
	 */
	public static SpeedRegionLookup loadFromUncompiledSpeedRulesFile(File uncompiledFile, double minCellLengthMetres) {
		UncompiledSpeedRulesFile uncompiledSpeedRulesFile = TextUtils.fromJSON(uncompiledFile, UncompiledSpeedRulesFile.class);
		return loadFromUncompiledSpeedRulesFile(uncompiledSpeedRulesFile, minCellLengthMetres);
	}
	public static SpeedRegionLookup loadFromUncompiledSpeedRulesFile(UncompiledSpeedRulesFile uncompiledSpeedRulesFile,
			double minCellLengthMetres) {
		CompiledSpeedRulesFile compiled = compileFiles(Arrays.asList(uncompiledSpeedRulesFile), minCellLengthMetres);
		return fromCompiled(compiled);
	}
	

	/**
	 * Convert an {@link com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile} object
	 * into the lookup object. The speed rules are validated as part of the conversion.
	 * @param compiled
	 * @return
	 */
	public static SpeedRegionLookup fromCompiled(final CompiledSpeedRulesFile compiled) {
		
		SpeedRulesProcesser processer = new SpeedRulesProcesser();
		processer.validateSpeedRules(Arrays.asList(compiled));
		final TreeMap<String, TreeMap<String, SpeedRule>> rulesMap =processer.createSelfContainedRulesLookupMap(compiled.getRules());
				
		final QueryProcessor queryProcessor = new QueryProcessor(GeomUtils.newGeomFactory(), compiled.getTree());
		return new SpeedRegionLookup() {
			
			public String findRegionType(Geometry edge) {
				String regionId =queryProcessor.query(edge);
				return regionId;
			}
			
			public SpeedRuleLookup createLookupForEncoder(String encoder) {
				return createRulesLookupForEncoder(rulesMap, encoder);
			}

			@Override
			public RegionsSpatialTreeNode getTree() {
				return compiled.getTree();
			}
		};
	}
	
	public static SpeedRegionLookup loadFromCommandLineParameters(Map<String, String> parameters){
		// try loading from compiled first
		String compiled = parameters.get(SpeedRegionConsts.COMMAND_LINE_COMPILED_FILE);
		if(compiled!=null){
			return loadFromCompiledFile(new File(compiled));
		}
		
		String uncompiled = parameters.get(SpeedRegionConsts.COMMAND_LINE_UNCOMPILED_FILE);
		if(uncompiled!=null){
			String tol = parameters.get(SpeedRegionConsts.COMMAND_LINE_TOLERANCE);
			if(tol==null){
				throw new RuntimeException("Uncompiled speed regions input file was specified but metres tolerance parameter " + SpeedRegionConsts.COMMAND_LINE_TOLERANCE + " was not specified");
			}
			
			double dTol;
			try {
				dTol = Double.parseDouble(tol);
			} catch (Exception e) {
				throw new RuntimeException("Could not parse " + SpeedRegionConsts.COMMAND_LINE_TOLERANCE + " as a number. Input string was: " + tol);
			}
			
			return loadFromUncompiledSpeedRulesFile(new File(uncompiled), dTol);
		}
		return null;
	}

	private static SpeedRuleLookup createRulesLookupForEncoder(final TreeMap<String, TreeMap<String, SpeedRule>> rulesMap, String encoder) {
		TreeMap<String, SpeedRule> map = rulesMap.get(TextUtils.stdString(encoder));
		if(map==null){
			map = new TreeMap<String, SpeedRule>();
		}
		final TreeMap<String, SpeedRule> finalMap = map;
		return new SpeedRuleLookup(){

			public SpeedRule getSpeedRule(String standardisedRegionId) {
				return finalMap.get(standardisedRegionId);
			};
		
		};
	}

	/**
	 * Create a 'compiled' {@link com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile} from an 
	 * {@link com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile} object.
	 * The spatial tree will be compiled (i.e. built).
	 * @param file
	 * @param minCellLengthMetres
	 * @return
	 */
	public static CompiledSpeedRulesFile compileFile(UncompiledSpeedRulesFile file, double minCellLengthMetres) {
		return compileFiles(Arrays.asList(file), minCellLengthMetres);
	}
	

	private static CompiledSpeedRulesFile compileFiles(List<UncompiledSpeedRulesFile> files, double minCellLengthMetres) {
		SpeedRulesProcesser processer = new SpeedRulesProcesser();
		List<FeatureCollection> collections = new ArrayList<>(files.size());
		for(UncompiledSpeedRulesFile file:files){
			if(file.getGeoJson()!=null){
				collections.add(file.getGeoJson());				
			}
		}
		final RegionsSpatialTreeNode root=TreeBuilder.build(collections, minCellLengthMetres);
	//	LOGGER.info("Built quadtree: " + SpatialTreeStats.build(root).toString());
		
		CompiledSpeedRulesFile built = new CompiledSpeedRulesFile();
		built.setTree(root);
		
		built.setRules(processer.validateSpeedRules(files));
		return built;
	}
	

	
}
