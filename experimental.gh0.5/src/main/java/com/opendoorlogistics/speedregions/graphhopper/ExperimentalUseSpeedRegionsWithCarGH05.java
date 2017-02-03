package com.opendoorlogistics.speedregions.graphhopper;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

import com.graphhopper.GraphHopper;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookup.SpeedRuleLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookupBuilder;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.ProcessedWayListener;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
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
			new RunWizardApp().runWizardAppForCar(args);
		} else {
			runCommandLine(args);
		}
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
		SpeedRegionsFlagEncodersFactory factory = new SpeedRegionsFlagEncodersFactory(bytesForFlags);		
		ArrayList<AbstractFlagEncoder> encoders = new ArrayList<>();
		for (String encoder : splitEncoders) {
			String propertiesString = "";
			if (encoder.contains("|")) {
				propertiesString = encoder;
				encoder = encoder.split("\\|")[0];
			}
			PMap configuration = new PMap(propertiesString);
			encoders.add(factory.createEncoder(encoder, configuration, speedRegionLookup, null));
		
		}
		
		EncodingManager myEncodingManager = new EncodingManager(encoders, bytesForFlags);
		hopper.setEncodingManager(myEncodingManager);
		hopper.importOrLoad();
		hopper.close();
	}


}
