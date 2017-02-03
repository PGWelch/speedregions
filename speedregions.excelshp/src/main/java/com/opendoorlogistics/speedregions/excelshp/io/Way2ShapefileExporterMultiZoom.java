package com.opendoorlogistics.speedregions.excelshp.io;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO.IncrementalShapefileWriter;
import com.opendoorlogistics.speedregions.excelshp.io.Ways2ShapefileExporter.CreateFeaturesReport;
import com.vividsolutions.jts.geom.LineString;

public class Way2ShapefileExporterMultiZoom {
	private static final Logger LOGGER = Logger.getLogger(Way2ShapefileExporterMultiZoom.class.getName());

	private static class ZoomRange {
		final double toleranceDegrees;
		final int minZoom;
		final int maxZoom;

		ZoomRange(double toleranceDegrees, int minZoom, int maxZoom) {
			this.toleranceDegrees = toleranceDegrees;
			this.minZoom = minZoom;
			this.maxZoom = maxZoom;
		}

		@Override
		public String toString() {
			return "ZoomRange [toleranceDegrees=" + toleranceDegrees + ", minZoom=" + minZoom + ", maxZoom=" + maxZoom + "]";
		}
		
		

	}

	static{
		class ZoomDegPerPixel{
			int zoom;
			double degPerPixel;
			ZoomDegPerPixel(int zoom, double degPerPixel) {
				this.zoom = zoom;
				this.degPerPixel = degPerPixel;
			}
			
	
		}
		
		//  See http://wiki.openstreetmap.org/wiki/Zoom_levels Output at different min / max zoom levels.
		ZoomDegPerPixel [] zdpp = new ZoomDegPerPixel[]{
				new ZoomDegPerPixel(0,1.40625),
				new ZoomDegPerPixel(1,0.703125000000),
				new ZoomDegPerPixel(2,0.351562500000),
				new ZoomDegPerPixel(3,0.175781250000),
				new ZoomDegPerPixel(4,0.087890625000),
				new ZoomDegPerPixel(5,0.043945312500),
				new ZoomDegPerPixel(6,0.021972656250),
				new ZoomDegPerPixel(7,0.010988281250),
				new ZoomDegPerPixel(8,0.005492187500),
				new ZoomDegPerPixel(9,0.002746093750),
				new ZoomDegPerPixel(10,0.001375000000),
				new ZoomDegPerPixel(11,0.000687500000),
				new ZoomDegPerPixel(12,0.000343750000),
				new ZoomDegPerPixel(13,0.000171875000),
				new ZoomDegPerPixel(14,0.000085937500),
				new ZoomDegPerPixel(15,0.000042968750),
				new ZoomDegPerPixel(16,0.000019531250),
				new ZoomDegPerPixel(17,0.000011718750),
				new ZoomDegPerPixel(18,0.000003906250),
				new ZoomDegPerPixel(19,0.000001953125),				
		};
		
		double []tols = new double[]{0.1,0.01,0.001,0.0001,0.00001};//,0.000001,0.0000001};
		
		// for each zoom choose the first tolerance less than twice it
		ArrayList<ZoomRange> ranges = new ArrayList<>();

		for(ZoomDegPerPixel z: zdpp){
			double chosen = 0;
			for(double tol:tols){
				chosen = tol;
				if(chosen < 2*z.degPerPixel){
					break;
				}
			}
			
			ZoomRange range = new ZoomRange(chosen, z.zoom, z.zoom);
			int n = ranges.size();
			ZoomRange lastRange = ranges.size()>0? ranges.get(n-1):null;
			if(lastRange==null || lastRange.toleranceDegrees!=chosen){
				ranges.add(range);
			}else{
				ranges.set(n-1, new ZoomRange(chosen, lastRange.minZoom, z.zoom));
			}
	
		}
		
		ZOOM_RANGES = ranges.toArray(new ZoomRange[ranges.size()]);
		
		for(ZoomRange zr:ranges){
			LOGGER.info(zr.toString());
		}
	}
	
	private static final ZoomRange[] ZOOM_RANGES;

	private final double kmPerHourResolution;
	private final long maxPointsInNode;
	private final int maxDepth;

	public Way2ShapefileExporterMultiZoom(double kmPerHourResolution, long maxPointsInNode, int maxDepth) {
		this.kmPerHourResolution = kmPerHourResolution;
		this.maxPointsInNode = maxPointsInNode;
		this.maxDepth = maxDepth;
	}

	public void buildShapefile(File file) {
		LOGGER.info("Exporting roads multizoom shapefile to " + file.getAbsolutePath());

		SimpleFeatureType type = Ways2ShapefileExporter.buildType();

		IncrementalShapefileWriter writer = new IncrementalShapefileWriter();
		writer.start(file, type);
		for (int i = 0; i < ZOOM_RANGES.length; i++) {
			ZoomRange zr = ZOOM_RANGES[i];
			Ways2ShapefileExporter exporter = new Ways2ShapefileExporter(maxPointsInNode, maxDepth, ZOOM_RANGES[i].toleranceDegrees);
			for (Map.Entry<Double, LinkedList<LineString>> entry : bySpeedBin.entrySet()) {
				double low = entry.getKey();
				double high = low + kmPerHourResolution;

				CreateFeaturesReport report = exporter.createFeatures(type, entry.getValue(), low, high, zr.minZoom, zr.maxZoom, writer);

				LOGGER.info("...Built zoom range " + (i + 1) + "/" + ZOOM_RANGES.length + " (zoom " + zr.minZoom + "-" + zr.maxZoom + ")"
						+ ", speed range " + low + "-" + high + ", result=[" + report.toString() + "]");
			}
		}
		writer.close();

		LOGGER.info("Finished exporting roads multizoom shapefile to " + file.getAbsolutePath());

	}

	private final TreeMap<Double, LinkedList<LineString>> bySpeedBin = new TreeMap<>();

	public void addWay(LineString lineString, double speedKMPerHour) {
		double floor = kmPerHourResolution * Math.floor(speedKMPerHour / kmPerHourResolution);
		LinkedList<LineString> list = bySpeedBin.get(floor);
		if (list == null) {
			list = new LinkedList<>();
			bySpeedBin.put(floor, list);
		}
		list.add(lineString);

	}
}
