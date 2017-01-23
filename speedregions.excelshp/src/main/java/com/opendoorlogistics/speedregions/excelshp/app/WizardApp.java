package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTable;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.RuleConversionInfo;
import com.opendoorlogistics.speedregions.utils.ExceptionUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;

/**
 * App. We output to console and hence assume this is run from the console
 * @author Phil
 *
 */
public class WizardApp {
	private static final Logger LOGGER = Logger.getLogger(WizardApp.class.getName());

	private final AppInjectedDependencies dependencies;
	private final String encoderType;

	public WizardApp(String encoderType,AppInjectedDependencies speedProvider) {
		this.dependencies = speedProvider;
		this.encoderType = encoderType;
	}


//	public static void main(String[] args) {
//		
//		// use default speeds from Graphhopper for testing the main method....
//        final TreeMap<String, Integer> speedMap = new TreeMap<>();
//        speedMap.put("motorway", 100);
//        speedMap.put("motorway_link", 70);
//        speedMap.put("motorroad", 90);
//        speedMap.put("trunk", 70);
//        speedMap.put("trunk_link", 65);
//        speedMap.put("primary", 65);
//        speedMap.put("primary_link", 60);
//        speedMap.put("secondary", 60);
//        speedMap.put("secondary_link", 50);
//        speedMap.put("tertiary", 50);
//        speedMap.put("tertiary_link", 40);
//        speedMap.put("unclassified", 30);
//        speedMap.put("residential", 30);
//        speedMap.put("living_street", 5);
//        speedMap.put("service", 20);
//        speedMap.put("road", 20);
//        speedMap.put("track", 15);
//        
//        AppInjectedDependencies dummySpeeds = new AppInjectedDependencies() {
//			
//			@Override
//			public double speedKmPerHour(String flagEncoderType, String highwayType) {
//				return speedMap.get(highwayType).doubleValue();
//			}
//
//			@Override
//			public void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile) {
//				// TODO Auto-generated method stub
//				
//			}
//		};
//		
//		new WizardApp("car",dummySpeeds).runWizard();
//
//	}

	private String toIntRoundMBStr(double bytes){
		return Long.toString((long)Math.round(bytes/ (1024.0*1024.0)));
	}
	
	public void runWizard() {
		// Show settings UI
		LOGGER.info("Prompting user for settings");
		AppSettings settings = SwingUtils.runOnEDT(new Callable<AppSettings>() {

			@Override
			public AppSettings call() throws Exception {
				return SettingsPanel.modal();
			}
		});
		
		if (settings == null) {
			// user cancelled
			return;
		}

		// Ensure output dir exists etc and quit if there's a problem
		if (!processDir(settings.getOutdirectory())) {
			return;
		}

		// Check osm.pbf exists
		File inputFile=new File(settings.getPbfFile());
		if(!inputFile.exists() || inputFile.isDirectory()){
			showError("Input map data file does not exist: " + settings.getPbfFile());
			return;
		}
		
		// Warn user if not enough memory...
		long maxMemoryBytes= Runtime.getRuntime().maxMemory();
		double estmRequiredBytes = inputFile.length() * AppConstants.AVAILABLE_MEMORY_WARNING_TOLERANCE;
		if(maxMemoryBytes < estmRequiredBytes){
			if(SwingUtils.showConfirmOnEDT(null, "We estimate the input file will need " +toIntRoundMBStr(estmRequiredBytes) +
					" MB to process but this program only has " + toIntRoundMBStr(maxMemoryBytes) + " MB available."
					+ System.lineSeparator()
					+ "You are advised to increase the Xmx setting in the .bat file (on Windows computers) to give this program more memory, if your computer has it."
					+ System.lineSeparator() +"Stop this program, change the .bat file and then re-run the program."
					+ System.lineSeparator() + System.lineSeparator() + "Do you want to stop this program?"
					, "Low memory warning", JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){
				return;
			}
		}
		
		// do first processing to create maps
		UncompiledSpeedRulesFile uncompiled=null;
		final File outdir=new File(settings.getOutdirectory());
		if(settings.isUseExcelShape()){
			if(settings.getGridCellMetres() < AppConstants.MIN_GRID_METRES || settings.getGridCellMetres() > AppConstants.MAX_GRID_METRES){
				showError("The lookup grid cell must be between " + AppConstants.MIN_GRID_METRES + " and " + AppConstants.MAX_GRID_METRES + " metres.");
				return;
			}
			
			try {
				TreeMap<String,RuleConversionInfo> result = new ExcelShp2GeoJSONConverter(encoderType,dependencies).convert(new File(settings.getExcelfile()), new File(settings.getShapefile()), settings.getIdFieldNameInShapefile());
				
				// show summary report if using excel / shape...
				if(!showSummaryReport(result)){
					return;
				}
				
				// Merge polygons
				ExcelShp2GeoJSONConverter.createMergedPolygons(result.values());
				
				// Export shapefile
				ShapefileIO.exportShapefile(result.values(), new File(outdir, "merged_speed_regions.shp"));
				
				// Create the uncompiled file. which is fed into the graph builder.
				// This involves slow polygon unioning, which and so should be done after the summary report.
				uncompiled = new UncompiledSpeedRulesFile();
				for(RuleConversionInfo rci : result.values()){
					uncompiled.getRules().add(rci.getParentCollapsedRule());
				}
				uncompiled.setGeoJson(ExcelShp2GeoJSONConverter.mergedRulesToFeatureCollection(result.values()));
				
				// Export the uncompiled file and geojson on its own
				TextUtils.toJSONFile(uncompiled, new File(outdir, "merged_uncompiled.json"));
				TextUtils.toJSONFile(uncompiled.getGeoJson(), new File(outdir, "merged_polygons.geojson"));
			} catch (Exception e) {
				showError(e);
				return;
			}

		}
					
		// now compile
		try {
			dependencies.buildGraph(settings, uncompiled, new Consumer<SpatialTreeNode>(){

				@Override
				public void accept(SpatialTreeNode t) {
					// TODO CHECK FOR NODES WITH NO ASSIGNED REGION...
					LOGGER.info("Writing spatial tree nodes to Excel");
					ExportTable table = ExcelWriter.exportTree(t);
					LOGGER.info("...writing " + table.getRows().size() + " row(s)");
					ExcelWriter.writeSheets(new File(outdir, "TreeNodes.xlsx"),table);
					LOGGER.info("Finished writing spatial tree nodes to Excel");
				}
				
			});
			SwingUtils.showMessageOnEDT(null, "Finished building graph in directory " + settings.getOutdirectory(), "Finished", JOptionPane.OK_OPTION);
		} catch (Exception e) {
			showError(e);
		}
		
	}
	
	/**
	 * Show a summary report and return false if uses cancels
	 * @param details
	 * @return
	 */
	private boolean showSummaryReport(TreeMap<String,RuleConversionInfo> details){
		StringBuilder builder = new StringBuilder();
		builder.append("Speeds for one or more types of road are:" + System.lineSeparator()+ System.lineSeparator());
		
		// Collate by 10 percent bins
		class TmpRec implements Comparable<TmpRec>{
			double low;
			double high;
			TreeSet<String> brickIds = new TreeSet<>();
			
			@Override
			public int compareTo(TmpRec o) {
				return Double.compare(low, o.low);
			}
			
		}
		
		double percentResolution = 2.5;
		
		TreeMap<TmpRec,TmpRec> recs = new TreeMap<>();
		for(RuleConversionInfo info : details.values()){
			for(double percentage : info.getPercentageSpeedChangeByRoadType().values()){
				TmpRec rec = new TmpRec();
				rec.low = percentResolution*(long)Math.floor(percentage /percentResolution);
				rec.high = rec.low + percentResolution;
				
				TmpRec prexisting = recs.get(rec);
				if(prexisting!=null){
					rec = prexisting;
				}else{
					recs.put(rec, rec);
				}
				
				rec.brickIds.addAll(info.getBrickIds());
			}
		}
		
		Random random = new Random(123);
		for(TmpRec rec : recs.keySet()){
			builder.append("...changed by " + rec.low + "% to " + rec.high + "% for " + rec.brickIds.size() + " brick(s), e.g. ");
			int i =0 ;
			
			// randomly select example bricks (better than showing the first alphabetical few)
			List<String> tmp = new ArrayList<>(rec.brickIds);
			Collections.shuffle(tmp, random);
			if(tmp.size()>AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE){
				tmp = tmp.subList(0, AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE);
			}
			for(String brickId: new TreeSet<>(tmp)){
				
				if(i>0){
					builder.append(",");
				}
				
				if(i>=AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE){
					builder.append("...");
					break;
				}
			
				builder.append(brickId);
				
				i++;
			}
			builder.append(System.lineSeparator());
		}

		JTextArea textArea = new JTextArea(builder.toString());
		textArea.setEditable(false);
		textArea.setLineWrap(false);
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(700, 400));
		return SwingUtils.showConfirmOnEDT(null,scrollPane, "Summary report", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;
	}

	private void showError(Exception e) {
		String msg = ExceptionUtils.getMessage(e);
		showError("An error occurred when processing." + System.lineSeparator() + (msg!=null?msg:""));
	}

	private boolean processDir(String dirname) {
		LOGGER.info("Checking output directory");

		// Prompt to delete directory if not empty
		File dir = new File(dirname);
		if (dir.exists() && !dir.isDirectory()) {
			showError("The output directory is a file, not a directory.");
			return false;
		}

		// Clear a preexisting directory
		if (dir.exists()) {
			int count = 0;
			for (File file : dir.listFiles()) {
				if (file.isDirectory()) {
					showError("The output directory cannot already contain subdirectories.");
					return false;
				}
				count++;
			}

			if (count > 0) {
				if (SwingUtils.showConfirmOnEDT(null,
						"The output directory " + dirname + " is not empty." + System.lineSeparator()
								+ "You must delete all files from the directory before continuing." + System.lineSeparator()
								+ "Do you want to delete all files from it?",
						"Delete files in dir", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {

					for (File file : dir.listFiles()) {
						if(!file.delete()){
							showError("Could not delete file " + file.getName());
							return false;
						}
					}
				} else {
					return false;
				}
			}
		}
		else{
			if(!dir.mkdirs()){
				showError("Could not make output directory: " + dirname);
				return false;
			}
		}

		return true;
	}

	private void showError(String s) {
		SwingUtils.showMessageOnEDT(null, s, "Error", JOptionPane.ERROR_MESSAGE);
	}
}
