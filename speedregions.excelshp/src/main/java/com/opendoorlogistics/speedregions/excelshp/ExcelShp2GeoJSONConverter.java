package com.opendoorlogistics.speedregions.excelshp;

import static com.opendoorlogistics.speedregions.excelshp.StringConstants.BRFLD_BRICK_ID;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.BRFLD_SPEED_PROFILE_ID;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.BRICKS_TABLENAME;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.ROAD_TYPES;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPEED_PROFILES_TABLENAME;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPFLD_ID;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPFLD_MULTIPLIER;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPFLD_PARENT_ID;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPFLD_SPEED_PREFIX;
import static com.opendoorlogistics.speedregions.excelshp.StringConstants.SPFLD_SPEED_UNIT;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;

import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;
public class ExcelShp2GeoJSONConverter {

	private int findField(String fieldname, RawStringTable table){
		String std = TextUtils.stdString(fieldname);
		int ret = table.getColumnIndex(std);
		if(ret==-1){
			throw new RuntimeException("Cannot find field " + fieldname + " in Excel sheet " + table.getName());
		}
		return ret;
	}
	
	private TreeMap<String,SpeedRule>  readSpeedRules(TreeMap<String, RawStringTable> tables){
		TreeMap<String,SpeedRule> ret = new TreeMap<>();
		RawStringTable table = findTable(tables, StringConstants.SPEED_PROFILES_TABLENAME);
		
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
			String prefix="Row " + line + " in table " + SPEED_PROFILES_TABLENAME + " ";

			// rows are always filled to the header length...
			SpeedRule rule = new SpeedRule();
			
			// parse id
			rule.setId(TextUtils.stdString(row.get(iId)));
			if(rule.getId().length()==0){
				throw new RuntimeException(prefix + " has empty " + SPFLD_ID);
			}
			
			// parse parent id
			rule.setParentId(row.get(iPId));
			
			// parse multiplier
			String mult = TextUtils.stdString(row.get(iMult));
			if(mult.length()>0){
				try {
					rule.setMultiplier(Double.parseDouble(mult));
				} catch (Exception e) {
					throw new RuntimeException(prefix + "has invalid multiplier value \"" + mult+ "\".");	
				}
			}
			
			int nbSpeeds=0;
			for(Map.Entry<String, Integer> rt : byType.entrySet()){
				String val =  TextUtils.stdString(row.get(rt.getValue()));
				if(val.length()>0){
					try {
						rule.getSpeedsByRoadType().put(rt.getKey(), Float.parseFloat(val));
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
					throw new RuntimeException(prefix + "has unidentified speed unit type " + unit);					
				}
			}
			
			if(ret.containsKey(rule.getId())){
				throw new RuntimeException(prefix + "has non-unique " + SPFLD_ID + " (appears in multiple rows).");
			}
			
			// Each rule is a region type.....
			rule.getMatchRule().getRegionTypes().add(rule.getId());
			
			ret.put(rule.getId(), rule);
			
			line++;
		}

		return ret;
	}

	RawStringTable findTable(TreeMap<String, RawStringTable> tables, String tablename) {
		RawStringTable table = tables.get(tablename);
		if(table==null){
			throw new RuntimeException("Cannot find Excel table named " + tablename);
		}
		return table;
	}
	
	private FeatureCollection readBricksTable(TreeMap<String, RawStringTable> tables,TreeMap<String, SpeedRule> rules ,TreeMap<String, Geometry> shp){
		RawStringTable table = findTable(tables, BRICKS_TABLENAME);

		int iBid = findField(BRFLD_BRICK_ID, table);
		int iSPid = findField(BRFLD_SPEED_PROFILE_ID, table);
		
		PrecisionModel pm = new PrecisionModel(PrecisionModel.FLOATING_SINGLE);
		GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);

		// read and merge the rules
		int line=2;
		HashMap<SpeedRule, Geometry> merged = new HashMap<>();
		HashSet<String> usedBrickids = new HashSet<>();
		for(List<String> row : table.getDataRows()){
			String prefix="Row " + line + " in table " + BRICKS_TABLENAME + " ";

			String bid = TextUtils.stdString(row.get(iBid));
			if(bid.length()==0){
				throw new RuntimeException(prefix+ "has empty " + BRFLD_BRICK_ID);
			}
			
			String spid = TextUtils.stdString(row.get(iSPid));
			if(spid.length()==0){
				throw new RuntimeException(prefix + "has empty " + BRFLD_SPEED_PROFILE_ID);
			}
			
			// get brick and check not already used
			Geometry geometry = shp.get(bid);
			if(geometry==null){
				throw new RuntimeException(prefix +  "has " + BRFLD_BRICK_ID + " " + bid + " but no brick (e.g. postcode polygon) with this id was found in the shapefile.");
			}
			if(usedBrickids.contains(bid)){
				throw new RuntimeException(prefix +  "has " + BRFLD_BRICK_ID + " " + bid + " but this brick id was already used in an earlier row.");				
			}
			usedBrickids.add(bid);
			
			// get speed rule
			SpeedRule rule = rules.get(spid);
			if(rule==null){
				throw new RuntimeException(prefix +  "has " + BRFLD_SPEED_PROFILE_ID + " " + spid + " but no speed profile was found with this id.");
			}
				
			// merge geometries, reducing precision to help prevent slivers etc
			geometry = reducer.reduce(geometry);
			if(merged.containsKey(rule)){
				merged.put(rule, merged.get(rule).union(geometry));
			}else{
				merged.put(rule, geometry);
			}
			line++;
		}
		
		FeatureCollection ret = new FeatureCollection();
		for(Map.Entry<SpeedRule, Geometry> entry : merged.entrySet()){
			
			Feature feature = new Feature();
			feature.setProperty(SpeedRegionConsts.REGION_TYPE_KEY,entry.getKey().getId());
			GeoJsonObject geoJson = GeomUtils.toGeoJSON(entry.getValue());
			feature.setGeometry(geoJson);
			ret.add(feature);			
		}

		return ret;
	}
	
	public UncompiledSpeedRulesFile convert(File excel, File shapefile, String shapefileIdFieldName){
		// Read Excel
		TreeMap<String, RawStringTable> tables = ExcelReader.readExcel(excel);
		
		// Read speed profiles table and turn it into a list of speed rules
		TreeMap<String, SpeedRule> rules = readSpeedRules(tables);
		UncompiledSpeedRulesFile ret = new UncompiledSpeedRulesFile();
		ret.setRules(new ArrayList<>(rules.values()));
		
		// Read shapefile
		TreeMap<String, Geometry> shp = ShapefileReader.readShapefile(shapefile, shapefileIdFieldName);

		// Read bricks table...
		ret.setGeoJson(readBricksTable(tables, rules, shp));

		return ret;
	}

}
