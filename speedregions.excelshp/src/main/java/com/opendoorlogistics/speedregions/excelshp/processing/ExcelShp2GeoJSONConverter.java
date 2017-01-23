package com.opendoorlogistics.speedregions.excelshp.processing;

import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.BRFLD_BRICK_ID;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.BRFLD_SPEED_PROFILE_ID;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.BRICKS_TABLENAME;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.ROAD_TYPES;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPEED_PROFILES_TABLENAME;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPFLD_ID;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPFLD_MULTIPLIER;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPFLD_PARENT_ID;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPFLD_SPEED_PREFIX;
import static com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants.SPFLD_SPEED_UNIT;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;

import com.graphbuilder.geom.Geom;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.SpeedRulesProcesser;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelReader;
import com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants;
import com.opendoorlogistics.speedregions.excelshp.io.RawStringTable;
import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.algorithm.BoundaryNodeRule;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;
public class ExcelShp2GeoJSONConverter {
	private static final Logger LOGGER = Logger.getLogger(ExcelShp2GeoJSONConverter.class.getName());

	private final AppInjectedDependencies dependencies;
	private final String encoderType;

	public ExcelShp2GeoJSONConverter(String encoderType,AppInjectedDependencies speedProvider) {
		this.dependencies = speedProvider;
		this.encoderType = encoderType;
	}

	private int findField(String fieldname, RawStringTable table){
		String std = TextUtils.stdString(fieldname);
		int ret = table.getColumnIndex(std);
		if(ret==-1){
			throw new RuntimeException("Cannot find field " + fieldname + " in Excel sheet " + table.getName());
		}
		return ret;
	}
	
	private TreeMap<String,SpeedRule>  readSpeedRulesTable(TreeMap<String, RawStringTable> tables){
		LOGGER.info("Reading " + IOStringConstants.SPEED_PROFILES_TABLENAME +" table");
		
		TreeMap<String,SpeedRule> ret = new TreeMap<>();
		RawStringTable table = findTable(tables, IOStringConstants.SPEED_PROFILES_TABLENAME);
		
		// get field indices (checking fields exist)
		int iId = findField(SPFLD_ID, table);
		int iPId = findField(SPFLD_PARENT_ID, table);
		int iMult = findField(SPFLD_MULTIPLIER, table);
		int iUnit = findField(SPFLD_SPEED_UNIT, table);
		TreeMap<String, Integer> byType= new TreeMap<>();
		for(String rt : ROAD_TYPES){
			byType.put(rt, findField(SPFLD_SPEED_PREFIX + rt, table));
		}
		
		int line=2;
		for(List<String> row : table.getDataRows()){
			SpeedRule rule = new SpeedRule();
			
			// rows are always filled to the header length so don't need to check length
			rule.setId(TextUtils.stdString(row.get(iId)));
			
			String prefix="Row " + line + " in table " + SPEED_PROFILES_TABLENAME + " with id \"" + rule.getId()+"\" ";
		
			// parse id
			if(rule.getId().length()==0){
				throw new RuntimeException(prefix + "has empty " + SPFLD_ID);
			}
			
			// parse parent id
			rule.setParentId(row.get(iPId));
			
			// parse multiplier
			String mult = TextUtils.stdString(row.get(iMult));
			if(mult.length()>0){
				try {
					rule.setMultiplier(Double.parseDouble(mult));
					if(rule.getMultiplier()<0){
						throw new RuntimeException();
					}
				} catch (Exception e) {
					throw new RuntimeException(prefix + "has invalid multiplier value \"" + mult+ "\".");	
				}
			}
			
			int nbSpeeds=0;
			for(Map.Entry<String, Integer> rt : byType.entrySet()){
				String val =  TextUtils.stdString(row.get(rt.getValue()));
				if(val.length()>0){
					try {
						float f= Float.parseFloat(val);
						rule.getSpeedsByRoadType().put(rt.getKey(),f);
						if(f<0){
							throw new RuntimeException();
						}
						nbSpeeds++;
					} catch (Exception e) {
						throw new RuntimeException(prefix + "has invalid speed value \"" + val + "\" for field " + SPFLD_SPEED_PREFIX + rt.getKey());	
					}
				}	
			}
			
			// parse speed unit... throw error if no unit defined and we have speeds
			String unit = TextUtils.stdString(row.get(iUnit));
			if(unit.length()==0){
				if(nbSpeeds>0){
					throw new RuntimeException(prefix + "has defined speeds but the speed unit is not defined.");
				}
			}
			else{
				rule.setSpeedUnit(null);
				for(SpeedUnit su : SpeedUnit.values()){
					if(unit.equals(TextUtils.stdString(su.name()))){
						rule.setSpeedUnit(su);
						break;
					}
				}
				
				if(rule.getSpeedUnit()==null){
					StringBuilder validValues = new StringBuilder();
					int count=0;
					for(SpeedUnit su : SpeedUnit.values()){
						if(count>0){
							validValues.append(", ");							
						}
						validValues.append(su.name());
						count++;
					}
					throw new RuntimeException(prefix + "has unidentified speed unit type " + unit + ". Valid values are " + validValues.toString());					
				}
			}
			
			if(ret.containsKey(rule.getId())){
				throw new RuntimeException(prefix + "has non-unique " + SPFLD_ID + " (appears in multiple rows).");
			}
			
			// Each rule is a region type and for the set encoder type
			rule.getMatchRule().getRegionTypes().add(rule.getId());
			rule.getMatchRule().getFlagEncoders().add(encoderType);
			
			ret.put(rule.getId(), rule);
			
			line++;
		}

		LOGGER.info("Finished reading " + IOStringConstants.SPEED_PROFILES_TABLENAME +" table");		
		return ret;
	}

	RawStringTable findTable(TreeMap<String, RawStringTable> tables, String tablename) {
		RawStringTable table = tables.get(tablename);
		if(table==null){
			throw new RuntimeException("Cannot find Excel table named " + tablename);
		}
		return table;
	}
	
	/**
	 * Dump of all conversion information so we can create a report on it...
	 * @author Phil
	 *
	 */
	public static class RuleConversionInfo{
		private SpeedRule originalRule;
		private SpeedRule parentCollapsedRule;
	//	private Geometry geometry;
		private MultiPolygon mergedPolygons;		
		private LinkedList<Polygon> polygons = new LinkedList<>();
		private TreeSet<String> brickIds = new TreeSet<>();
		private TreeMap<String, Double> percentageSpeedChangeByRoadType = new TreeMap<>();

		public SpeedRule getOriginalRule() {
			return originalRule;
		}
		public void setOriginalRule(SpeedRule rule) {
			this.originalRule = rule;
		}

		public TreeSet<String> getBrickIds() {
			return brickIds;
		}
		public void setBrickIds(TreeSet<String> brickIds) {
			this.brickIds = brickIds;
		}
		public TreeMap<String, Double> getPercentageSpeedChangeByRoadType() {
			return percentageSpeedChangeByRoadType;
		}
		public void setPercentageSpeedChangeByRoadType(TreeMap<String, Double> percentageSpeedChangeByRoadType) {
			this.percentageSpeedChangeByRoadType = percentageSpeedChangeByRoadType;
		}
		public SpeedRule getParentCollapsedRule() {
			return parentCollapsedRule;
		}
		public void setParentCollapsedRule(SpeedRule parentCollapsedRule) {
			this.parentCollapsedRule = parentCollapsedRule;
		}
		public LinkedList<Polygon> getPolygons() {
			return polygons;
		}
		public void setPolygons(LinkedList<Polygon> polygons) {
			this.polygons = polygons;
		}
		public MultiPolygon getMergedPolygons() {
			return mergedPolygons;
		}
		public void setMergedPolygons(MultiPolygon mergedPolygons) {
			this.mergedPolygons = mergedPolygons;
		}
		
		
	}
	
	private void readBricksTable(TreeMap<String, RawStringTable> tables,
			Map<String, RuleConversionInfo> rules ,
			TreeMap<String, Geometry> shp){
		LOGGER.info("Reading bricks table");
		RawStringTable table = findTable(tables, BRICKS_TABLENAME);

		int iBid = findField(BRFLD_BRICK_ID, table);
		int iSPid = findField(BRFLD_SPEED_PROFILE_ID, table);
		
		PrecisionModel pm = new PrecisionModel(PrecisionModel.FLOATING_SINGLE);
		GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);

		// read and merge the rules
		int line=1;
		HashSet<String> usedBrickids = new HashSet<>();
		for(List<String> row : table.getDataRows()){
			// Validate brick id usage first
			String prefix="Row " + (line++) + " in table " + BRICKS_TABLENAME;
			String bid = TextUtils.stdString(row.get(iBid));
			if(bid.length()==0){
				throw new RuntimeException(prefix+ " has empty " + BRFLD_BRICK_ID);
			}
			prefix += " with brick id \"" + bid + "\"";
			
			// get brick and check not already used
			Geometry brickGeometry = shp.get(bid);
			if(brickGeometry==null){
				throw new RuntimeException(prefix +  " has no corresponding brick with the same id in the shapefile (i.e. we don't have a polygon boundary for it).");
			}
			if(usedBrickids.contains(bid)){
				throw new RuntimeException(prefix +  " has already had its id used on a previous row");				
			}
			usedBrickids.add(bid);
			
			// get speed rule
			String spid = TextUtils.stdString(row.get(iSPid));
			if(spid.length()==0){
				continue;
			}
			RuleConversionInfo rule = rules.get(spid);
			if(rule==null){
				throw new RuntimeException(prefix +  " has " + BRFLD_SPEED_PROFILE_ID + " \"" + spid + "\" but no speed profile was found with this id.");
			}
				
			// reducing precision to help prevent slivers etc
			brickGeometry = reducer.reduce(brickGeometry);
			ShapefileIO.throwIfNonPolygonOrMultiPolygon(bid, brickGeometry);
			if(brickGeometry instanceof MultiPolygon){
				for(int i =0 ; i < brickGeometry.getNumGeometries() ; i++){
					rule.getPolygons().add((Polygon)brickGeometry.getGeometryN(i));
				}
			}
			else{
				rule.getPolygons().add((Polygon)brickGeometry);
			}

			rule.getBrickIds().add(bid);
		}
		
		LOGGER.info("Finished reading bricks table");
	}

	public static void createMergedPolygons(Iterable<RuleConversionInfo> merged){
		for( RuleConversionInfo mrule  :merged){
			if(mrule.getPolygons().size()==0){
				LOGGER.info("Skipping polygon merge for rule with id \"" + mrule.getParentCollapsedRule().getId() + "\" as no polygons found for it.");
				continue;
			}
			
			LOGGER.info("...Merging " + mrule.getPolygons().size() +"  polygons in rule " + mrule.getParentCollapsedRule().getId());
			Geometry unioned = CascadedPolygonUnion.union(mrule.getPolygons());
			if(!ShapefileIO.isPolygonOrMultiPolygon(unioned)){
				throw new RuntimeException("Merging all brick polygons for rule with id \"" + mrule.getParentCollapsedRule().getId() +" \" resulted in bad geometry which isn't a polygon.");
			}
			
			if(unioned instanceof MultiPolygon){
				mrule.setMergedPolygons((MultiPolygon)unioned);
			}else{
				mrule.setMergedPolygons( new GeometryFactory().createMultiPolygon(new Polygon[]{(Polygon)unioned}));
			}
			
		}
	}
	public static FeatureCollection mergedRulesToFeatureCollection(Iterable<RuleConversionInfo> merged) {
		FeatureCollection ret = new FeatureCollection();
		for( RuleConversionInfo mrule  :merged){
			if(mrule.getPolygons().size()==0){
				LOGGER.info("Skipping rule with id \"" + mrule.getParentCollapsedRule().getId() + "\" as no polygons found for it.");
				continue;
			}
			
			Feature feature = new Feature();
			feature.setProperty(SpeedRegionConsts.REGION_TYPE_KEY,mrule.getOriginalRule().getId());
			LOGGER.info("Creating geoJSON for rule " + mrule.getOriginalRule().getId());
			GeoJsonObject geoJson = GeomUtils.toGeoJSON(mrule.getMergedPolygons());
			feature.setGeometry(geoJson);
			ret.add(feature);			
		}
		return ret;
	}
	

	
	public TreeMap<String,RuleConversionInfo> convert(File excel, File shapefile, String shapefileIdFieldName){
		// Read Excel into tables of strings
		TreeMap<String, RawStringTable> tables = ExcelReader.readExcel(excel);
		
		// Read speed profiles table and turn it into a list of speed rules
		TreeMap<String, SpeedRule> originalRules = readSpeedRulesTable(tables);

		// Collapse the parent relations in the speed rules and save in a map by id
		TreeMap<String,RuleConversionInfo> details = new TreeMap<>();
		for(Map.Entry<SpeedRule,SpeedRule> entry : new SpeedRulesProcesser().collapseParentRelations(originalRules.values()).entrySet()){
			RuleConversionInfo info = new RuleConversionInfo();
			info.setOriginalRule(entry.getKey());
			info.setParentCollapsedRule(entry.getValue());
			details.put(entry.getKey().getId(), info);
		}



		// Read shapefile
		TreeMap<String, Geometry> shp = ShapefileIO.readShapefile(shapefile, shapefileIdFieldName);
		
		// HACK. Geotools initialisation replaces the logging handler with a new one with logging level warning.
		// Set logging back to all...
		dependencies.HACK_ReinitLogging();
		LOGGER.info("Finished reading shapefile");

		// Read bricks and merge geometries etc
		readBricksTable(tables, details, shp);

		calculatePercentageSpeedChanges(details);
		
		LOGGER.info("Finished processing shapefile+Excel");
		return details;
	}

	private void calculatePercentageSpeedChanges(TreeMap<String, RuleConversionInfo> details) {
		// For the rules, get their percentage speed change by road type
		for(RuleConversionInfo rule : details.values()){
			for(String highwayType : IOStringConstants.ROAD_TYPES){
				double defaultSpeed = dependencies.speedKmPerHour(encoderType, highwayType);
				if(defaultSpeed==0){
					// edge case where road type is disabled anyway by default and we can't 
					// report a percentage. Just ignore as probably irrelevant
					continue;
				}
				
				double newSpeed = rule.getParentCollapsedRule().applyRule(highwayType, defaultSpeed, false);
	
				if(newSpeed<0){
					throw new RuntimeException("Setting road type " + highwayType + " to have negative speed is not allowed.");					
				}
				else if(newSpeed==0){
					if(defaultSpeed==0){
						// was zero, still zero... this is OK
						continue;
					}
					throw new RuntimeException("Setting road type " + highwayType + " to have zero speed is not allowed.");
				}
				
				double diff = newSpeed - defaultSpeed;
				double percent = 100*(diff / defaultSpeed);
				rule.getPercentageSpeedChangeByRoadType().put(highwayType, percent);
			}
		}
	}

}
