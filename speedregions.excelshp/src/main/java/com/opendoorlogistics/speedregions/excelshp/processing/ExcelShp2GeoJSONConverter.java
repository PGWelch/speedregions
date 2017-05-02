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
import com.opendoorlogistics.speedregions.excelshp.app.AppSettings;
import com.opendoorlogistics.speedregions.excelshp.app.VehicleType;
import com.opendoorlogistics.speedregions.excelshp.app.VehicleTypeTimeProfile;
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
	private final AppSettings settings;

	public ExcelShp2GeoJSONConverter(AppSettings settings,AppInjectedDependencies speedProvider) {
		this.dependencies = speedProvider;
		this.settings = settings;
	}

	private int findField(String fieldname, RawStringTable table){
		String std = TextUtils.stdString(fieldname);
		int ret = table.getColumnIndex(std);
		if(ret==-1){
			throw new RuntimeException("Cannot find field " + fieldname + " in Excel sheet " + table.getName());
		}
		return ret;
	}
	
	private TreeMap<String,SpeedRule>  readSpeedRulesTable(RawStringTable table){
		LOGGER.info("Reading " + table.getName() +" table");
		
		TreeMap<String,SpeedRule> ret = new TreeMap<>();
	
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
			
			String prefix="Row " + line + " in table " + table.getName() + " with id \"" + rule.getId()+"\" ";
		
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
			

			ret.put(rule.getId(), rule);
			
			line++;
		}

		LOGGER.info("Finished reading " + table.getName() +" table");		
		return ret;
	}

	RawStringTable findTable(Iterable<RawStringTable> tables, String tablename) {
		for(RawStringTable table: tables){
			if(TextUtils.equalsStd(tablename, table.getName())){
				return table;
			}
		}

		throw new RuntimeException("Cannot find Excel table named " + tablename);

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
	//	private MultiPolygon mergedPolygons;		
	//	private LinkedList<Polygon> polygons = new LinkedList<>();
	//	private TreeSet<String> brickIds = new TreeSet<>();
		private TreeMap<String, Double> percentageSpeedChangeByRoadType = new TreeMap<>();

		public SpeedRule getOriginalRule() {
			return originalRule;
		}
		public void setOriginalRule(SpeedRule rule) {
			this.originalRule = rule;
		}

//		public TreeSet<String> getBrickIds() {
//			return brickIds;
//		}
//		public void setBrickIds(TreeSet<String> brickIds) {
//			this.brickIds = brickIds;
//		}
		
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

	}
	
	public static class BrickItem{
		public String brickId;
		public String speedProfileId;
	}
	
	private List<BrickItem> readBricksTableToRules(List<RawStringTable> tables,
			TreeMap<String, Geometry> shp){
		LOGGER.info("Reading bricks table");
		RawStringTable table = findTable(tables, BRICKS_TABLENAME);

		int iBid = findField(BRFLD_BRICK_ID, table);
		int iSPid = findField(BRFLD_SPEED_PROFILE_ID, table);

		int line=1;
		ArrayList<BrickItem> bricks = new ArrayList<>();
		HashSet<String> usedBrickids = new HashSet<>();
		for(List<String> row : table.getDataRows()){
			// Validate brick id usage first
			String prefix="Row " + (line++) + " in table " + BRICKS_TABLENAME;
			BrickItem brick = new BrickItem();
			brick.brickId = TextUtils.stdString(row.get(iBid));
			if(brick.brickId.length()==0){
				throw new RuntimeException(prefix+ " has empty " + BRFLD_BRICK_ID);
			}
			prefix += " with brick id \"" + brick.brickId + "\"";
			
			// get brick and check not already used
			Geometry brickGeometry = shp.get(brick.brickId);
			if(brickGeometry==null){
				throw new RuntimeException(prefix +  " has no corresponding brick with the same id in the shapefile (i.e. we don't have a polygon boundary for it).");
			}
			if(usedBrickids.contains(brick.brickId)){
				throw new RuntimeException(prefix +  " has already had its id used on a previous row");				
			}
			usedBrickids.add(brick.brickId);
			
			brick.speedProfileId = TextUtils.stdString(row.get(iSPid));
			bricks.add(brick);
		}
		
		LOGGER.info("Finished reading bricks table");
		return bricks;
	}
	
	/**
	 * Add the brick ids to the rules
	 * @param tables
	 * @param shp
	 * @param rulesBySpeedProfileId
	 */
	private void applyBricksTableToRules(List<BrickItem> bricks,
			String speedProfilesTableName,
			Map<String, RuleConversionInfo> rulesBySpeedProfileId){
		LOGGER.info("Reading bricks table");

		for(BrickItem brickItem : bricks){

			// get speed rule
			String spid = TextUtils.stdString(brickItem.speedProfileId);
			if(spid.length()==0){
				continue;
			}
			RuleConversionInfo rule = rulesBySpeedProfileId.get(spid);
			if(rule==null){
				throw new RuntimeException("Brick " + brickItem.brickId +  " has " + BRFLD_SPEED_PROFILE_ID + " \"" + spid + "\" but no speed profile was found with this id in table "+ speedProfilesTableName + ".");
			}

			// always add brick id even if merging regions before the build as we use it in the summary report
			rule.getOriginalRule().getMatchRule().getRegionTypes().add(brickItem.brickId);
			rule.getParentCollapsedRule().getMatchRule().getRegionTypes().add(brickItem.brickId);				

		}
		
		LOGGER.info("Finished reading bricks table");
	}

//	public static void createMergedPolygons(Iterable<RuleConversionInfo> merged){
//		for( RuleConversionInfo mrule  :merged){
//			if(mrule.getPolygons().size()==0){
//				LOGGER.info("Skipping polygon merge for rule with id \"" + mrule.getParentCollapsedRule().getId() + "\" as no polygons found for it.");
//				continue;
//			}
//			
//			LOGGER.info("...Merging " + mrule.getPolygons().size() +"  polygons in rule " + mrule.getParentCollapsedRule().getId());
//			Geometry unioned = CascadedPolygonUnion.union(mrule.getPolygons());
//			if(!ShapefileIO.isPolygonOrMultiPolygon(unioned)){
//				throw new RuntimeException("Merging all brick polygons for rule with id \"" + mrule.getParentCollapsedRule().getId() +" \" resulted in bad geometry which isn't a polygon.");
//			}
//			
//			if(unioned instanceof MultiPolygon){
//				mrule.setMergedPolygons((MultiPolygon)unioned);
//			}else{
//				mrule.setMergedPolygons( new GeometryFactory().createMultiPolygon(new Polygon[]{(Polygon)unioned}));
//			}
//			
//		}
//	}
	
	public static FeatureCollection shapefileToGeoJSON(TreeMap<String, Geometry> shp) {
		FeatureCollection ret = new FeatureCollection();
		for( Map.Entry<String, Geometry> brick  :shp.entrySet()){
//			if(mrule.getPolygons().size()==0){
//				LOGGER.info("Skipping rule with id \"" + mrule.getParentCollapsedRule().getId() + "\" as no polygons found for it.");
//				continue;
//			}
			
			Feature feature = new Feature();
			feature.setProperty(SpeedRegionConsts.REGION_TYPE_KEY,brick.getKey());
			LOGGER.info("Creating geoJSON for brick " + brick.getKey());
			GeoJsonObject geoJson = GeomUtils.toGeoJSON(brick.getValue());
			feature.setGeometry(geoJson);
			ret.add(feature);			
		}
		return ret;
	}
	
	public static class ConversionResult{
		public TreeMap<VehicleTypeTimeProfile,TreeMap<String,RuleConversionInfo>> rules = new TreeMap<>();
		public TreeMap<String, Geometry> shp;
		public List<BrickItem> bricks;
	}
	
	public ConversionResult convert(File excel, File shapefile, String shapefileIdFieldName){
		// Read Excel into tables of strings
		List<RawStringTable> tables = ExcelReader.readExcel(excel);
		
		// Read shapefile
		ConversionResult ret = new ConversionResult();
		ret.shp = ShapefileIO.readShapefile(shapefile, shapefileIdFieldName);
		
		// Read bricks
		ret.bricks= readBricksTableToRules(tables, ret.shp);
		
		// HACK. Geotools initialisation replaces the logging handler with a new one with logging level warning.
		// Set logging back to all...
		dependencies.HACK_ReinitLogging();
		LOGGER.info("Finished reading shapefile");

		for(RawStringTable table: tables){
			VehicleTypeTimeProfile vttp = VehicleTypeTimeProfile.parseTableName(table.getName());
			if(vttp!=null && settings.isEnabled(vttp.getVehicleType())){
				if(!vttp.getVehicleType().isSpeedRegionsSupported()){
					LOGGER.info("Skipping build of rules for table " + table.getName()+ " as not supported for type " + vttp.getVehicleType().getGraphhopperName());				
					continue;
				}else{
					LOGGER.info("Building rules for table " + table.getName());				
				}
				

				// Read speed profiles table
				TreeMap<String, SpeedRule> originalRules = readSpeedRulesTable(table);

				// And a match rule for the vehicle type
				for(SpeedRule sr:originalRules.values()){
					sr.getMatchRule().getFlagEncoders().add(vttp.getCombinedId());
				}

				// Collapse the parent relations in the speed rules and save in a map by id
				TreeMap<String,RuleConversionInfo> details = new TreeMap<>();
				for(Map.Entry<SpeedRule,SpeedRule> entry : new SpeedRulesProcesser().collapseParentRelations(originalRules.values()).entrySet()){
					RuleConversionInfo info = new RuleConversionInfo();
					info.setOriginalRule(entry.getKey());
					info.setParentCollapsedRule(entry.getValue());
					details.put(entry.getKey().getId(), info);
				}

				// Read bricks and merge geometries etc
				applyBricksTableToRules(ret.bricks,table.getName(), details);

				if(dependencies.isDefaultSpeedsKnown(vttp)){
					calculatePercentageSpeedChanges(vttp,details);				
				}
				
				ret.rules.put(vttp, details);	
			}
		}
		
//		for(VehicleType type:VehicleType.values()){
//			if(!settings.isEnabled(type)){
//				continue;
//			}
//			
//			if(!type.isSpeedRegionsSupported()){
//				LOGGER.info("Skipping build of rules for vehicle type " + type.getGraphhopperName() + " as not supported");				
//				continue;
//			}else{
//				LOGGER.info("Building rules for vehicle type " + type.getGraphhopperName());				
//			}
//			
//			String tablename = IOStringConstants.SPEED_PROFILES_TABLENAME +TextUtils.capitaliseFirstLetter(type.getGraphhopperName());
//			
//			// Read speed profiles table
//			TreeMap<String, SpeedRule> originalRules = readSpeedRulesTable(tablename,tables);
//
//			// And a match rule for the vehicle type
//			for(SpeedRule sr:originalRules.values()){
//				sr.getMatchRule().getFlagEncoders().add(type.getGraphhopperName());
//			}
//
//			// Collapse the parent relations in the speed rules and save in a map by id
//			TreeMap<String,RuleConversionInfo> details = new TreeMap<>();
//			for(Map.Entry<SpeedRule,SpeedRule> entry : new SpeedRulesProcesser().collapseParentRelations(originalRules.values()).entrySet()){
//				RuleConversionInfo info = new RuleConversionInfo();
//				info.setOriginalRule(entry.getKey());
//				info.setParentCollapsedRule(entry.getValue());
//				details.put(entry.getKey().getId(), info);
//			}
//
//			// Read bricks and merge geometries etc
//			applyBricksTableToRules(ret.bricks,tablename, details);
//
//			if(dependencies.isDefaultSpeedsKnown(type.getGraphhopperName())){
//				calculatePercentageSpeedChanges(type,details);				
//			}
//			
//			ret.rules.put(type, details);
//						
//		}

		LOGGER.info("Finished processing shapefile+Excel");

		return ret;
	}

	private void calculatePercentageSpeedChanges(VehicleTypeTimeProfile type,TreeMap<String, RuleConversionInfo> details) {
		
		// For the rules, get their percentage speed change by road type
		for(RuleConversionInfo rule : details.values()){
			for(String highwayType : IOStringConstants.ROAD_TYPES){
				double defaultSpeed = dependencies.speedKmPerHour(type, highwayType);
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
				
				double percent = percentageChange(defaultSpeed, newSpeed);
				rule.getPercentageSpeedChangeByRoadType().put(highwayType, percent);
			}
		}
	}

	public static double percentageChange(double originalSpeed, double newSpeed){
		double diff = newSpeed - originalSpeed;
		double percent = 100*(diff / originalSpeed);
		return percent;
	}
}
