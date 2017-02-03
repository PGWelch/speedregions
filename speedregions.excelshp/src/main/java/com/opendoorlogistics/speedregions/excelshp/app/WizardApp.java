package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Dimension;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.io.FilenameUtils;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.Geometries;
import org.opengis.feature.simple.SimpleFeatureType;

import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.BuiltTreeListener;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.ProcessedWayListener;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter;
import com.opendoorlogistics.speedregions.excelshp.io.ExcelWriter.ExportTable;
import com.opendoorlogistics.speedregions.excelshp.io.RawStringTable;
import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO;
import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO.IncrementalShapefileWriter;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.BrickItem;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.ConversionResult;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.RuleConversionInfo;
import com.opendoorlogistics.speedregions.utils.AbstractNode;
import com.opendoorlogistics.speedregions.utils.AbstractNode.NodeVisitor;
import com.opendoorlogistics.speedregions.utils.ExceptionUtils;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;

/**
 * App. We output to console and hence assume this is run from the console
 * 
 * @author Phil
 *
 */
public class WizardApp {
	private static final Logger LOGGER = Logger.getLogger(WizardApp.class.getName());

	private final AppInjectedDependencies dependencies;

	public WizardApp(AppInjectedDependencies speedProvider) {
		this.dependencies = speedProvider;

	}

	// public static void main(String[] args) {
	//
	// // use default speeds from Graphhopper for testing the main method....
	// final TreeMap<String, Integer> speedMap = new TreeMap<>();
	// speedMap.put("motorway", 100);
	// speedMap.put("motorway_link", 70);
	// speedMap.put("motorroad", 90);
	// speedMap.put("trunk", 70);
	// speedMap.put("trunk_link", 65);
	// speedMap.put("primary", 65);
	// speedMap.put("primary_link", 60);
	// speedMap.put("secondary", 60);
	// speedMap.put("secondary_link", 50);
	// speedMap.put("tertiary", 50);
	// speedMap.put("tertiary_link", 40);
	// speedMap.put("unclassified", 30);
	// speedMap.put("residential", 30);
	// speedMap.put("living_street", 5);
	// speedMap.put("service", 20);
	// speedMap.put("road", 20);
	// speedMap.put("track", 15);
	//
	// AppInjectedDependencies dummySpeeds = new AppInjectedDependencies() {
	//
	// @Override
	// public double speedKmPerHour(String flagEncoderType, String highwayType) {
	// return speedMap.get(highwayType).doubleValue();
	// }
	//
	// @Override
	// public void buildGraph(AppSettings settings, UncompiledSpeedRulesFile uncompiledSpeedRulesFile) {
	// // TODO Auto-generated method stub
	//
	// }
	// };
	//
	// new WizardApp("car",dummySpeeds).runWizard();
	//
	// }

	private String toIntRoundMBStr(double bytes) {
		return Long.toString((long) Math.round(bytes / (1024.0 * 1024.0)));
	}

	public void runWizard() {
		// Show settings UI
		LOGGER.info("Prompting user for settings");
		final AppSettings settings = SwingUtils.runOnEDT(new Callable<AppSettings>() {

			@Override
			public AppSettings call() throws Exception {
				return SettingsPanel.modal();
			}
		});

		if (settings == null) {
			// user cancelled
			return;
		}

		// Check at least one graph being built
		int count = 0;
		StringBuilder unsupportedExcelShp = new StringBuilder();
		for (VehicleType type : VehicleType.values()) {
			if (settings.isEnabled(type)) {
				count++;
				
				if(!type.isSpeedRegionsSupported() && settings.isUseExcelShape()){
					if(unsupportedExcelShp.length()>0){
						unsupportedExcelShp.append("," );
					}
					unsupportedExcelShp.append(type.getGraphhopperName());
				}
			}
		}
		if (count == 0) {
			showError("You should tick at least one type of vehicle (car, bike etc) to be built");
			return;
		}
		
		if(unsupportedExcelShp.length()>0){
			showWarning("Excel + shapefile speed regions are not supported for " + unsupportedExcelShp.toString() + " and will not be used");
		}


		// Ensure output dir exists etc and quit if there's a problem
		if (!processDir(settings.getOutdirectory())) {
			return;
		}

		// Check osm.pbf exists
		File inputFile = new File(settings.getPbfFile());
		if (!inputFile.exists() || inputFile.isDirectory()) {
			showError("Input map data file does not exist: " + settings.getPbfFile());
			return;
		}

		// Warn user if not enough memory...
		long maxMemoryBytes = Runtime.getRuntime().maxMemory();
		double estmRequiredBytes = inputFile.length() * AppConstants.AVAILABLE_MEMORY_WARNING_TOLERANCE;
		if (maxMemoryBytes < estmRequiredBytes) {
			if (SwingUtils.showConfirmOnEDT(null,
					"We estimate the input file will need " + toIntRoundMBStr(estmRequiredBytes)
							+ " MB to process but this program only has " + toIntRoundMBStr(maxMemoryBytes) + " MB available."
							+ System.lineSeparator()
							+ "You are advised to increase the Xmx setting in the .bat file (on Windows computers) to give this program more memory, if your computer has it."
							+ System.lineSeparator() + "Stop this program, change the .bat file and then re-run the program."
							+ System.lineSeparator() + System.lineSeparator() + "Do you want to stop this program?",
					"Low memory warning", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				return;
			}
		}

		// do first processing to create maps
		UncompiledSpeedRulesFile uncompiled = null;
		ConversionResult result = null;
		final File outdir = new File(settings.getOutdirectory());
		File shapefile = new File(settings.getShapefile());
		final SpeedUnit unit=settings.isReportInMiles()? SpeedUnit.MILES_PER_HOUR:SpeedUnit.KM_PER_HOUR;
		if (settings.isUseExcelShape()) {
			if (settings.getGridCellMetres() < AppConstants.MIN_GRID_METRES
					|| settings.getGridCellMetres() > AppConstants.MAX_GRID_METRES) {
				showError("The lookup grid cell must be between " + AppConstants.MIN_GRID_METRES + " and " + AppConstants.MAX_GRID_METRES
						+ " metres.");
				return;
			}

			try {
				result = new ExcelShp2GeoJSONConverter(settings, dependencies).convert(new File(settings.getExcelfile()), shapefile,
						settings.getIdFieldNameInShapefile());

				// show summary report if using excel / shape...
				if (!new SummaryReportBuilder(dependencies,unit).showSummaryReport(result)) {
					return;
				}

				// Create the uncompiled file. which is fed into the graph builder.
				uncompiled = new UncompiledSpeedRulesFile();
				for (TreeMap<String, RuleConversionInfo> map : result.rules.values()) {
					// rules will already be marked as belonging to the correct encoder
					for (RuleConversionInfo rci : map.values()) {
						uncompiled.getRules().add(rci.getParentCollapsedRule());
					}
				}
				uncompiled.setGeoJson(ExcelShp2GeoJSONConverter.shapefileToGeoJSON(result.shp));

				// TextUtils.toJSONFile(uncompiled, new File(outdir, "merged_uncompiled.json"));
				// TextUtils.toJSONFile(uncompiled.getGeoJson(), new File(outdir, "merged_polygons.geojson"));
			} catch (Exception e) {
				showError(e);
				return;
			}

		}

		// init detailed report builder
		final TreeMap<String, DetailedReportBuilder> detailedReportBuilders = new TreeMap<>(); 
	//	DetailedReportBuilder detailedReportBuilder = new DetailedReportBuilder();

		// now compile graph
		try {
			final ConversionResult finalConversionResult = result;
			dependencies.buildGraph(settings, uncompiled, new BuiltTreeListener() {

				@Override
				public void onBuiltTree(SpeedRegionLookup t) {
					// remember that regiontype is brick id in the tree
					if (finalConversionResult != null) {
						exportTreeToShapefiles(outdir, t, finalConversionResult);
					}
				}
			}, new ProcessedWayListener(){

				@Override
				public void onProcessedWay(String vehicleType, LineString lineString, String regionId, String highwayType,
						double lengthMetres, SpeedRule rule, double originalSpeedKPH, double speedRegionsSpeedKPH) {
					DetailedReportBuilder builder =detailedReportBuilders.get(vehicleType);
					if(builder==null){
						builder = new DetailedReportBuilder(unit,dependencies.speedKmPerHour(vehicleType));
						detailedReportBuilders.put(vehicleType, builder);
					}
					builder.onProcessedWay(lineString, regionId, highwayType, lengthMetres, rule, originalSpeedKPH, speedRegionsSpeedKPH);
				}
				
			});

			// Build reports and output to text and Excel file
			final ArrayList<RawStringTable> allReports = new ArrayList<>();
			final RawStringTable [] defaultReport = new RawStringTable[1];
			for(Map.Entry<String, DetailedReportBuilder> reportBuilder:detailedReportBuilders.entrySet()){
				List<RawStringTable> reports = reportBuilder.getValue().buildReports(defaultReport);
				
				// export to excel
				int nr = reports.size();
				ExportTable[] exportTables = new ExportTable[nr];
				for (int i = 0; i < nr; i++) {
					exportTables[i] = new ExportTable(reports.get(i));
				}
				ExcelWriter.writeSheets(new File(outdir, "Stats." + reportBuilder.getKey() + ".xlsx"), exportTables);

				for (RawStringTable report : reports) {
					
					// export to text
					String name = report.getName() + "." + reportBuilder.getKey() ;
					File file = new File(outdir,name + ".txt");
					TextUtils.stringToFile(file, report.toCSV());
					LOGGER.info("Wrote report " + file.getAbsolutePath());
					
					// set fully qualified name
					report.setName(name);
					allReports.add(report);
				}
	

			}

			zipOutputFiles(outdir);

			// show report here...
			SwingUtils.runOnEDT(new Runnable() {
				
				@Override
				public void run() {
					DetailedReportBuilder.showReportsOnEDT(allReports,defaultReport[0]);
				}
			});

		//	SwingUtils.showMessageOnEDT(null, "Finished building graph in directory " + settings.getOutdirectory(), "Finished",
			//		JOptionPane.OK_OPTION);

		} catch (Exception e) {
			showError(e);
		}

	}


	private void showError(Exception e) {
		String msg = ExceptionUtils.getMessage(e);
		showError("An error occurred when processing." + System.lineSeparator() + (msg != null ? msg : ""));
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
						if (!file.delete()) {
							showError("Could not delete file " + file.getName());
							return false;
						}
					}
				} else {
					return false;
				}
			}
		} else {
			if (!dir.mkdirs()) {
				showError("Could not make output directory: " + dirname);
				return false;
			}
		}

		return true;
	}

	private void showError(String s) {
		SwingUtils.showMessageOnEDT(null, s, "Error", JOptionPane.ERROR_MESSAGE);
	}

	private void showWarning(String s) {
		SwingUtils.showMessageOnEDT(null, s, "Warning", JOptionPane.WARNING_MESSAGE);
	}

	private void exportTreeToShapefiles(final File outdir, SpeedRegionLookup lookup, ConversionResult conversionResult) {

		final TreeMap<String, String> brickId2GeographicType = new TreeMap<>();
		for (BrickItem item : conversionResult.bricks) {
			brickId2GeographicType.put(item.brickId, TextUtils.stdString(item.speedProfileId));
		}

		for (boolean b : new boolean[] { true, false }) {
			final boolean collateByBrickId = b;
			File file = new File(outdir, collateByBrickId ? "NodedBricks.shp" : "NodedGeographicTypes.shp");
			LOGGER.info("Exporting to shapefile " + file.getAbsolutePath());

			// Collate nodes by brick id (a.k.a. region type) or rule id
			final TreeMap<String, LinkedList<RegionsSpatialTreeNode>> collatedByBrickId = new TreeMap<>();
			lookup.getTree().visitNodes(new NodeVisitor() {

				@Override
				public boolean visit(AbstractNode node) {
					RegionsSpatialTreeNode rn = (RegionsSpatialTreeNode) node;
					if (rn.getRegionType() != null && rn.getNbChildren() == 0) {

						String collateKey = rn.getRegionType();
						if (!collateByBrickId) {
							// translate to rule id / geographic type
							collateKey = brickId2GeographicType.get(collateKey);
						}

						if (collateKey != null) {
							LinkedList<RegionsSpatialTreeNode> list = collatedByBrickId.get(collateKey);
							if (list == null) {
								list = new LinkedList<>();
								collatedByBrickId.put(collateKey, list);
							}
							list.add(rn);
						}
					}
					return true;
				}
			});

			// build the output type
			SimpleFeatureType brickNodesType = ShapefileIO.createWGS84FeatureTypeBuilder("UnionedBrickNodes", Geometries.MULTIPOLYGON)
					.addStr(collateByBrickId?"BrickId":"RuleId").addLong("NodesCount").buildFeatureType();

			// stuff to reduce precision to help prevent slivers etc
			PrecisionModel pm = new PrecisionModel(PrecisionModel.FLOATING_SINGLE);
			GeometryPrecisionReducer reducer = new GeometryPrecisionReducer(pm);
			GeometryFactory factory = new GeometryFactory(pm);

			// write bricks as translated to nodes to shapefile
			IncrementalShapefileWriter writer = new IncrementalShapefileWriter();
			writer.start(file, brickNodesType);
			HashMap<String, MultiPolygon> unionedByRegionType = new HashMap<>();
			for (Map.Entry<String, LinkedList<RegionsSpatialTreeNode>> entry : collatedByBrickId.entrySet()) {

				// union all polygon bounding boxes for all nodes in the region
				LinkedList<Geometry> polygons = new LinkedList<>();
				for (RegionsSpatialTreeNode n : entry.getValue()) {
					polygons.add(reducer.reduce(GeomUtils.toJTS(n.getBounds())));
				}
				Geometry unioned = CascadedPolygonUnion.union(polygons);
				MultiPolygon mp = GeomUtils.toJTSMultiPolygon(unioned, factory);

				// write to the shapefile
				unionedByRegionType.put(entry.getKey(), mp);
				SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(brickNodesType);
				featureBuilder.add(mp);
				featureBuilder.add(entry.getKey());
				featureBuilder.add(new Long(entry.getValue().size()));
				writer.writeFeature(featureBuilder.buildFeature(null));
			}
			writer.close();

			dependencies.HACK_ReinitLogging();
		}

		// exportUnionedNodeShapefileForRule(outdir, lookup, factory, unionedByRegionType);
		// final TreeMap<String, TreeMap<String, SpeedRule>> rulesMap
		// =processer.createSelfContainedRulesLookupMap(compiled.getRules());

	}

	// private void exportUnionedNodeShapefileForRule(final File outdir, SpeedRegionLookup lookup, GeometryFactory
	// factory,
	// HashMap<String, MultiPolygon> unionedByBrick) {
	// // Now collate by rule...
	// HashMap<String, LinkedList<Geometry>> byRule = new HashMap<>();
	// SpeedRuleLookup lookup4Encoder = lookup.createLookupForEncoder(encoderType);
	// for (Map.Entry<String, MultiPolygon> entry : unionedByBrick.entrySet()) {
	// SpeedRule rule = lookup4Encoder.getSpeedRule(TextUtils.stdString(entry.getKey()));
	// if (rule != null) {
	// String ruleId = TextUtils.stdString(rule.getId());
	// if (ruleId.length() > 0) {
	// LinkedList<Geometry> list = byRule.get(ruleId);
	// if (list == null) {
	// list = new LinkedList<>();
	// byRule.put(ruleId, list);
	// }
	// list.add(entry.getValue());
	// }
	// }
	// }
	//
	// // Union by rule
	// SimpleFeatureType ruleNodesType = ShapefileIO.createWGS84FeatureTypeBuilder("UnionedRuleNodes",
	// Geometries.MULTIPOLYGON)
	// .addStr("RuleId").addLong("BricksCount").buildFeatureType();
	// File file2 = new File(outdir, "UnionedBrickTreeNodes.shp");
	// LOGGER.info("Exporting brick unioned tree to shapefile " + file2.getAbsolutePath());
	// IncrementalShapefileWriter writer2 = new IncrementalShapefileWriter();
	// writer2.start(file2, ruleNodesType);
	// for (Map.Entry<String, LinkedList<Geometry>> entry : byRule.entrySet()) {
	// Geometry unioned = CascadedPolygonUnion.union(entry.getValue());
	// MultiPolygon mp = GeomUtils.toJTSMultiPolygon(unioned, factory);
	// SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(ruleNodesType);
	// featureBuilder.add(mp);
	// featureBuilder.add(entry.getKey());
	// featureBuilder.add(new Long(entry.getValue().size()));
	// writer2.writeFeature(featureBuilder.buildFeature(null));
	// }
	// writer2.close();
	// }
	
	private void zipOutputFiles(File dir){
		try {
			ZipOutputStream zos=null;
			try {
				File zipfile=new File(dir, "reports.zip");
				FileOutputStream fos = new FileOutputStream(zipfile.getAbsolutePath());
				zos = new ZipOutputStream(fos);
				byte[] buffer = new byte[1024];
				String [] toZip = new String[]{"txt","xlsx", "dbf", "fix", "prj" ,"shp", "shx"};
				for(File file :dir.listFiles()){
					String ext = FilenameUtils.getExtension(file.getAbsolutePath()).toLowerCase();
					boolean found=false;
					for(String tz:toZip){
						if(ext.equals(tz)){
							found = true;
							break;
						}
					}
					
					if(found){
						LOGGER.info("Adding file " + file.getName() + " to zipfile " +zipfile.getAbsolutePath() );
						ZipEntry ze= new ZipEntry(file.getName());
			    		zos.putNextEntry(ze);
			    		
			    		// read file...
			    		FileInputStream in = new FileInputStream(file);
			    		int len;
			    		while ((len = in.read(buffer)) > 0) {
			    			zos.write(buffer, 0, len);
			    		}

			    		in.close();
			    		zos.closeEntry();
			    		
			    		// delete after
			    		file.delete();
					}
				}
			
			}finally{
				if(zos!=null){
					zos.close();
				}
			}		
		} catch (Exception e) {
			throw ExceptionUtils.asUncheckedException(e);
		}


	}
}
