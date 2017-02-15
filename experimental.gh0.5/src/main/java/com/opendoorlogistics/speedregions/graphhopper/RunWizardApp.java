package com.opendoorlogistics.speedregions.graphhopper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.PMap;
import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookupBuilder;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.BuiltTreeListener;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.ProcessedWayListener;
import com.opendoorlogistics.speedregions.excelshp.app.AppSettings;
import com.opendoorlogistics.speedregions.excelshp.app.SwingUtils;
import com.opendoorlogistics.speedregions.excelshp.app.VehicleType;
import com.opendoorlogistics.speedregions.excelshp.app.WizardApp;
import com.opendoorlogistics.speedregions.utils.ProcessTimer;

public class RunWizardApp {
	private static final Logger LOGGER = Logger.getLogger(RunWizardApp.class.getName());

	/**
	 * Only car profile supported at the moment....
	 * 
	 * @param args
	 * @throws Exception
	 */
	void runWizardApp(CmdArgs args) throws Exception {
		

		
		// use config file from the run directory... (car only)
		File file = new File("speedregions.graphhopper.properties");
		if (!file.exists()) {
			SwingUtils.showMessageOnEDT(null, "Cannot find speedregions.graphhopper.properties file");
			return;
		}

		// merge with the existing args ensuring the args point towards the config
		args.put("config", file.getAbsolutePath());
		final CmdArgs mergedArgs = CmdArgs.readFromConfigAndMerge(args, "config", "graphhopper.config");

		AppInjectedDependencies dependencies = createAppDependencies(mergedArgs);

		
		dependencies.HACK_ReinitLogging();
		
		// Run me
		new WizardApp(dependencies).runWizard();

	}

	private AppInjectedDependencies createAppDependencies(final CmdArgs mergedArgs) {
		// copy default speeds over so the wizard app has them
		final TreeMap<String, TreeMap<String, Double>> defaultSpeeds =new SpeedRegionsFlagEncodersFactory(0).getDefaultSpeeds();
		
		AppInjectedDependencies dependencies = new AppInjectedDependencies() {

			@Override
			public double speedKmPerHour(String flagEncoderType, String highwayType) {
				return defaultSpeeds.get(flagEncoderType).get(highwayType);
			}

			@Override
			public void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile,
					BuiltTreeListener builtTreeCB,final ProcessedWayListener handledWayCB) {
				RunWizardApp.this.buildGraph(mergedArgs, settings, uncompiledSpeedRulesFile, builtTreeCB, handledWayCB);
			}

			@Override
			public void HACK_ReinitLogging() {
				hackReinitLogging();
			}

			@Override
			public boolean isDefaultSpeedsKnown(String flagEncoderType) {
				return defaultSpeeds.get(flagEncoderType)!=null;
			}

			@Override
			public TreeMap<String, Double> speedKmPerHour(String flagEncoderType) {
				return defaultSpeeds.get(flagEncoderType);
			}
		};
		return dependencies;
	}

	private void buildGraph(final CmdArgs mergedArgs, AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile,
			BuiltTreeListener builtTreeCB, final ProcessedWayListener handledWayCB) {
		LOGGER.info("Compiling speed regions lookup");
		ProcessTimer lookupTime = new ProcessTimer().start();
		SpeedRegionLookup speedRegionLookup = SpeedRegionLookupBuilder.loadFromUncompiledSpeedRulesFile(uncompiledSpeedRulesFile,
				settings.getGridCellMetres());
		LOGGER.info("Speed region lookup took " + lookupTime.stop().secondsDuration() + " seconds to build");
		
		if(builtTreeCB!=null){
			builtTreeCB.onBuiltTree(speedRegionLookup);
		}

		// Create graphhopper object.				
		// We also need to set OSM file before calling init on Graphhopper as an exception will fire otherwise..
		mergedArgs.put("osmreader.osm", new File(settings.getPbfFile()).getAbsolutePath());
		final GraphHopper graphHopper =new GraphHopper();
		graphHopper.forDesktop().init(mergedArgs).setGraphHopperLocation(settings.getOutdirectory());

		// Get enabled vehicles
		ArrayList<VehicleType> vehicleTypes = new ArrayList<>();
		for(VehicleType type : VehicleType.values()){
			if(settings.isEnabled(type)){
				vehicleTypes.add(type);
			}
		}

		// We need more bytes for flags if we have more vehicle types...
		int bytesForFlags = mergedArgs.getInt("graph.bytesForFlags", vehicleTypes.size()<=2 ? 4 : 8);

		ArrayList<AbstractFlagEncoder> newSpeedEncoders = new ArrayList<>();
		SpeedRegionsFlagEncodersFactory factory = new SpeedRegionsFlagEncodersFactory(bytesForFlags);
		for(VehicleType type : vehicleTypes){
			newSpeedEncoders.add(factory.createEncoder(type.getGraphhopperName(),new PMap(), speedRegionLookup, handledWayCB));
		}
	
		// Don't forget to call this otherwise the dummy encoders used to get original
		// speeds won't be initialised properly
		factory.finish();
		
		// Create the proper encoding manager
		EncodingManager myEncodingManager = new EncodingManager(newSpeedEncoders, bytesForFlags);
		graphHopper.setEncodingManager(myEncodingManager);

		LOGGER.info("Building graph");
		ProcessTimer graphTimer = new ProcessTimer().start();
		graphHopper.importOrLoad().close();
		System.out.println("Graph took " + graphTimer.stop().secondsDuration() +" seconds to build");
	}

	private static void hackReinitLogging() {
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


}
