package com.opendoorlogistics.speedregions.graphhopper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

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
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies;
import com.opendoorlogistics.speedregions.excelshp.app.AppSettings;
import com.opendoorlogistics.speedregions.excelshp.app.SwingUtils;
import com.opendoorlogistics.speedregions.excelshp.app.WizardApp;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

/**
 * A temporary hack to use speed regions with graphhopper 0.5.
 *
 */
public class ExperimentalUseSpeedRegionsWithCarGH05 {
	private static final Logger LOGGER = Logger.getLogger(ExperimentalUseSpeedRegionsWithCarGH05.class.getName());
	private static DebugExportSpeeds debugExportSpeeds;

	/**
	 * A temporary hack to use speed regions pending proper integration into Graphhopper
	 * 
	 * Example args: config=config.properties osmreader.osm=malta-latest.osm.pbf speedregions.compiled=compiled.json
	 *
	 */
	public static void main(String[] strArgs) throws Exception {
		CmdArgs args = CmdArgs.read(strArgs);
		if (args.getBool("odlwizardapp", false)) {
			runWizardAppForCar(args);
		} else {
			runCommandLine(args);
		}
	}

	/**
	 * Only car profile supported at the moment....
	 * 
	 * @param args
	 * @throws Exception
	 */
	private static void runWizardAppForCar(CmdArgs args) throws Exception {

		
		// use config file from the run directory... (car only)
		File file = new File("config.properties");
		if (!file.exists()) {
			SwingUtils.showMessageOnEDT(null, "Cannot find config.properties file");
			return;
		}

		// merge with the existing args ensuring the args point towards the config
		args.put("config", file.getAbsolutePath());
		final CmdArgs mergedArgs = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");

		// copy default speeds over so the wizard app has them
		final TreeMap<String, TreeMap<String, Double>> defaultSpeeds = new TreeMap<>();
		TreeMap<String, Double> carMap = new TreeMap<>();
		defaultSpeeds.put("car", carMap);
		for(Map.Entry<String, Integer> entry : new MyCarFlagEncoder(new PMap(), null).getDefaultSpeeds().entrySet()){
			carMap.put(entry.getKey(), entry.getValue().doubleValue());
		}
		
		AppInjectedDependencies dependencies = new AppInjectedDependencies() {

			@Override
			public double speedKmPerHour(String flagEncoderType, String highwayType) {
				return defaultSpeeds.get(flagEncoderType).get(highwayType);
			}

			@Override
			public void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile,Consumer<SpatialTreeNode> builtTreeCB) {
				LOGGER.info("Compiling speed regions lookup");
				SpeedRegionLookup speedRegionLookup = SpeedRegionLookupBuilder.loadFromUncompiledSpeedRulesFile(uncompiledSpeedRulesFile,
						settings.getGridCellMetres());

				if(builtTreeCB!=null){
					builtTreeCB.accept(speedRegionLookup.getTree());
				}
				
				ArrayList<AbstractFlagEncoder> encoders = new ArrayList<>();
				MyCarFlagEncoder encoder = new MyCarFlagEncoder(new PMap(), speedRegionLookup);
				encoders.add(encoder);
				int bytesForFlags = mergedArgs.getInt("graph.bytesForFlags", 4);
				EncodingManager myEncodingManager = new EncodingManager(encoders, bytesForFlags);

				LOGGER.info("Building graph");
				
				// need to set OSM file before calling init on Graphhopper as an exception will fire otherwise...
				mergedArgs.put("osmreader.osm", new File(settings.getPbfFile()).getAbsolutePath());
				new GraphHopper().forDesktop().init(mergedArgs).setGraphHopperLocation(settings.getOutdirectory()).setEncodingManager(myEncodingManager).importOrLoad().close();

			}

			@Override
			public void HACK_ReinitLogging() {
				// just do nicely formatted console logging
				Logger rootLogger = Logger.getLogger("");
				for(Handler handler : rootLogger.getHandlers()){
					rootLogger.removeHandler(handler);
				}
				ConsoleHandler consoleHandler = new ConsoleHandler();
				rootLogger.addHandler(consoleHandler);
				consoleHandler.setFormatter(new SimpleFormatter(){
					@Override
				    public synchronized String format(LogRecord record) {
						if(record.getLevel() == Level.INFO){
							return LocalDateTime.now().toString() + ": " + record.getMessage() + System.lineSeparator();
						}else{
							return super.format(record);
						}
				    }
				});
				consoleHandler.setLevel(Level.INFO);
			}
		};
		
		dependencies.HACK_ReinitLogging();
		new WizardApp("car",dependencies ).runWizard();

	}

	private static void runCommandLine(CmdArgs args) throws Exception {

		// Check the arguments to see if we need to export debugging / profiling info
		String debugExportFile = args.get("debugexport", null);
		if (debugExportFile != null) {
			debugExportSpeeds = new DebugExportSpeeds(new File(debugExportFile));
		}

		SpeedRegionLookup speedRegionLookup = SpeedRegionLookupBuilder.loadFromCommandLineParameters(args.toMap());
		args = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");
		GraphHopper hopper = new GraphHopper() {
			@Override
			protected void postProcessing() {

				if (debugExportSpeeds != null) {
					debugExportSpeeds.close();
				}
				super.postProcessing();
			};
		};

		hopper.forDesktop().init(args);
		
		String flagEncoders = args.get("graph.flagEncoders", "");
		int bytesForFlags = args.getInt("graph.bytesForFlags", 4);
		String[] splitEncoders = flagEncoders.split(",");
		
		// Build all the flag encoders
		ArrayList<AbstractFlagEncoder> encoders = new ArrayList<>();
		for (String encoder : splitEncoders) {
			String propertiesString = "";
			if (encoder.contains("|")) {
				propertiesString = encoder;
				encoder = encoder.split("\\|")[0];
			}
			PMap configuration = new PMap(propertiesString);
		
			if (encoder.equals(EncodingManager.CAR)) {
				encoders.add(new MyCarFlagEncoder(configuration, speedRegionLookup));
			} else {
				throw new RuntimeException("Unsupported encoder");
			}
		
		}
		
		EncodingManager myEncodingManager = new EncodingManager(encoders, bytesForFlags);
		hopper.setEncodingManager(myEncodingManager);
		hopper.importOrLoad();
		hopper.close();
	}

	private static class MyCarFlagEncoder extends CarFlagEncoder {
		final private SpeedRegionLookup lookup;
		final private SpeedRuleLookup rules;

		MyCarFlagEncoder(PMap config, final SpeedRegionLookup lookup) {
			super(config);
			this.lookup = lookup;
			this.rules = lookup != null ? lookup.createLookupForEncoder(EncodingManager.CAR) : null;
		}

		Map<String, Integer> getDefaultSpeeds(){
			return this.defaultSpeedMap;
		}
		
		@Override
		public long handleWayTags(OSMWay way, long allowed, long relationFlags) {

			// Set the speed region tag. This should probably be done in OSMReader instead when we integrate into
			// latest Graphhopper core.
			GHPoint estmCentre = way.getTag("estimated_center", null);
			if (estmCentre != null && lookup != null) {
				Point point = GeomUtils.newGeomFactory().createPoint(new Coordinate(estmCentre.lon, estmCentre.lat));
				String regionId = lookup.findRegionType(point);
				way.setTag("speed_region_id", regionId);
			}

			long val = super.handleWayTags(way, allowed, relationFlags);

			if (debugExportSpeeds != null) {
				debugExportSpeeds.handledWayTag(this, way, val);
			}
			return val;
		}

		@Override
		protected double getSpeed(OSMWay way) {

			// get the default Graphhopper speed and whether we used the maxspeed tag
			String highwayValue = way.getTag("highway");
			double speed = super.getSpeed(way);
			double maxSpeed = getMaxSpeed(way);
			boolean useMaxSpeed = maxSpeed >= 0;
			if (useMaxSpeed) {
				speed = maxSpeed * 0.9;
			}

			// apply the rule
			String regionId = way.getTag("speed_region_id");
			if (regionId != null && rules != null) {
				SpeedRule rule = rules.getSpeedRule(regionId);
				if (rule == null) {
					// TODO Should this be fatal? If someone misspelled a regionid you wouldn't want a silent fail.
					// However it may be valid to have regions without a defined rule for certain encoders?
					throw new RuntimeException(
							"Cannot find speed rule for region with id " + regionId + " and encoder " + EncodingManager.CAR);
				}
				double regionSpeed= rule.applyRule(highwayValue, speed, useMaxSpeed);
				
				// AbstractFlagEncoder.speedFactor should be the minimum speed which can be stored by the encoder.
				// If we have a non-zero speed which is smaller than the minimum, set to the minimum instead
				// to ensure we don't accidently disabled roads (i.e. set to zero speed) that we don't want to...
				if(regionSpeed>0 && regionSpeed < speedFactor){
					regionSpeed = speedFactor;
				}
				
				return regionSpeed;
			}
			else{
				return speed;				
			}
		}

		@Override
		protected double applyMaxSpeed(OSMWay way, double speed, boolean force) {
			// max speed already handled in getSpeed...
			return speed;
		}

	}

}
