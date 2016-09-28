package com.opendoorlogistics.speedregions.graphhopper;

import java.io.File;
import java.util.ArrayList;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;
import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookup.SpeedRuleLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookupBuilder;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.spatialtree.GeomUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

/**
 * A temporary hack to use speed regions with graphhopper 0.5.
 *
 */
public class ExperimentalUseSpeedRegionsWithCarGH05 {
	private static DebugExportSpeeds debugExportSpeeds;
	
	/**
	 * A temporary hack to use speed regions pending proper integration into Graphhopper
	 * 
	 * Example args:
	 * config=config.properties osmreader.osm=malta-latest.osm.pbf speedregions.compiled=compiled.json
	 *
	 */
	public static void main(String[] strArgs) throws Exception {	
		CmdArgs args = CmdArgs.read(strArgs);
		
		String debugExportFile= args.get("debugexport", null);
		if(debugExportFile!=null){
			debugExportSpeeds = new DebugExportSpeeds(new File(debugExportFile));
		}
		
		SpeedRegionLookup lookup = SpeedRegionLookupBuilder.loadFromCommandLineParameters(args.toMap());
		args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
		GraphHopper hopper = new GraphHopper(){
			@Override
			protected void postProcessing(){
				
				if(debugExportSpeeds!=null){
					debugExportSpeeds.close();
				}
				super.postProcessing();
			};
		};
		hopper.forDesktop().init(args);

	    //protected void postProcessing()
		String flagEncoders = args.get("graph.flagEncoders", "");
		int bytesForFlags = args.getInt("graph.bytesForFlags", 4);
		String[] splitEncoders = flagEncoders.split(",");
		
		ArrayList<AbstractFlagEncoder> encoders = new ArrayList<>();
		for (String encoder : splitEncoders) {
			String propertiesString = "";
			if (encoder.contains("|")) {
				propertiesString = encoder;
				encoder = encoder.split("\\|")[0];
			}
			PMap configuration = new PMap(propertiesString);

			if (encoder.equals(EncodingManager.CAR)) {
				encoders.add(newCarFlagEncoder(configuration, lookup));
			} else {
				throw new RuntimeException("Unsupported encoder");
			}

		}

		EncodingManager myEncodingManager = new EncodingManager(encoders, bytesForFlags);
		hopper.setEncodingManager(myEncodingManager);
		hopper.importOrLoad();
		hopper.close();

	}

	private static CarFlagEncoder newCarFlagEncoder(PMap config, final SpeedRegionLookup lookup) {
		final SpeedRuleLookup rules = lookup.createLookupForEncoder(EncodingManager.CAR);

		return new CarFlagEncoder(config) {

			@Override
			public long handleWayTags(OSMWay way, long allowed, long relationFlags) {

				// Set the speed region tag. This should probably be done in OSMReader instead when we integrate into
				// latest Graphhopper core.
				GHPoint estmCentre = way.getTag("estimated_center", null);
				if (estmCentre != null) {
					Point point = GeomUtils.newGeomFactory().createPoint(new Coordinate(estmCentre.lon, estmCentre.lat));
					String regionId = lookup.findRegionType(point);
					way.setTag("speed_region_id", regionId);
				}

				long val= super.handleWayTags(way, allowed, relationFlags);
				
				if(debugExportSpeeds!=null){
					debugExportSpeeds.handledWayTag(this,way, val);
				}
				return val;
			}

			private SpeedRule getSpeedRule(OSMWay way) {
				SpeedRule rule = null;
				String regionId = way.getTag("speed_region_id");
				if (regionId != null) {
					rule = rules.getSpeedRule(regionId);
					if (rule == null) {
						// TODO Should this be fatal? If someone misspelled a regionid you wouldn't want a silent fail.
						// However it may be valid to have regions without a defined rule for certain encoders?
						throw new RuntimeException(
								"Cannot find speed rule for region with id " + regionId + " and encoder " + EncodingManager.CAR);
					}
				}
				return rule;
			}

			@Override
			protected double getSpeed(OSMWay way) {
				
				SpeedRule rule = getSpeedRule(way);
				String highwayValue = way.getTag("highway");
				double speed = super.getSpeed(way);
				if(rule!=null){
					return rule.applyRule(highwayValue, speed);
				}
				return speed;

//				if (rule != null) {
//					Float speed = rule.getSpeedsByRoadType().get(highwayValue);
//					if (speed != null) {
//						return SpeedUnit.convert(speed, rule.getSpeedUnit(), SpeedUnit.KM_PER_HOUR);
//					}
//				}
//
//				// If no rule set, use the superclass logic and apply the multiplier
//				double speed = super.getSpeed(way);
//				if (rule != null) {
//					speed *= rule.getMultiplier();
//				}
//
//				return speed;
			}

			@Override
			protected double applyMaxSpeed(OSMWay way, double speed, boolean force) {
				// don't force using the max speed tag if we have a speed rule?
				SpeedRule rule = getSpeedRule(way);
				if (rule != null) {
					force = false;
				}

				return super.applyMaxSpeed(way, speed, force);
			}

		};
	}
}
