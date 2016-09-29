package com.opendoorlogistics.speedregions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.geojson.FeatureCollection;
import org.junit.Test;
import static org.junit.Assert.*;

import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.spatialtree.GeomUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class TestSpatialTree_IT {
	static class PointRecord{
		boolean inside;
		Coordinate coordinate;
		Point point;
	}

	@Test
	public void testTreeNodesGenerated(){
		FeatureCollection fc = Examples.createMaltaSingleFeatureCollection();
		UncompiledSpeedRulesFile uncompiled = new UncompiledSpeedRulesFile();
		uncompiled.setGeoJson(fc);
	
		// get bounding box
		Geometry jtsGeom=GeomUtils.toJTS(GeomUtils.newGeomFactory(), fc.getFeatures().get(0).getGeometry());
		Envelope boundingBox = jtsGeom.getEnvelopeInternal();
		Bounds bounds = new Bounds(boundingBox.getMinX(), boundingBox.getMaxX(), boundingBox.getMinY(), boundingBox.getMaxY());
		double boundsHeightMetres = GeomUtils.getHeightMetres(bounds);
		double boundsWidthMetres = GeomUtils.getWidthMetres(bounds);
		
		// keep on reducing the size checking the number of nodes increases monotonically
		long lastNbNodes=-1;
		double minCellLength = 1000000;
		int step = 0;
		while(lastNbNodes < 10000 ){

			CompiledSpeedRulesFile compiled= SpeedRegionLookupBuilder.compileFile(uncompiled, minCellLength);

			// count the number of nodes
			long nbNodes = compiled.getTree().countNodes();
			if(step==0){
				assertEquals("Should only have one node on first step", 1, nbNodes);
			}
			
			// get an approximate upper bound on the number of nodes require
			double nx = Math.max( boundsWidthMetres / minCellLength,1);
			double ny = Math.max(boundsHeightMetres / minCellLength,1);
			double upperBound = nx * ny;
			assertTrue("Should have less nodes than the upper bound",nbNodes <= upperBound);
			
			if(lastNbNodes!=-1){
				assertTrue("Nb nodes should increase monotonically" , nbNodes >= lastNbNodes);
			}
			
			System.out.println("Cell length=" + minCellLength + ", nbNodes=" +nbNodes + ", upperbound(nbNodes)=" + upperBound);
			
			minCellLength /=3;
			lastNbNodes = nbNodes;
			step++;

		}
	}
	
	@Test
	public void testQuery(){
		FeatureCollection fc = Examples.createMaltaSingleFeatureCollection();
		assertEquals(1, fc.getFeatures().size());
		
		// get JTS geometry and bounding box
		Geometry jtsGeom=GeomUtils.toJTS(GeomUtils.newGeomFactory(), fc.getFeatures().get(0).getGeometry());
		Envelope boundingBox = jtsGeom.getEnvelopeInternal();
		
		UncompiledSpeedRulesFile uncompiled = new UncompiledSpeedRulesFile();
		uncompiled.setGeoJson(fc);

		// build some random points within 
		Random random = new Random(123);
		int npoints = 100;
		List<PointRecord> points = new ArrayList<>();
		for (int i = 0; i < npoints; i++) {
			PointRecord pr = new PointRecord();
			pr.coordinate = new Coordinate(random.nextDouble() * (boundingBox.getMaxX() - boundingBox.getMinX()) + boundingBox.getMinX(),
					random.nextDouble() * (boundingBox.getMaxY() - boundingBox.getMinY()) + boundingBox.getMinY());
			pr.point = GeomUtils.newGeomFactory().createPoint(pr.coordinate);
			pr.inside = jtsGeom.contains(pr.point);
			points.add(pr);
		}

		// compile
		double minCellLength = 1000000;
		int step = 0;
		int wrong = 0;
		int lastWrong=-1;
		while(minCellLength > 0.000001 && (lastWrong!=0)){
			CompiledSpeedRulesFile compiled= SpeedRegionLookupBuilder.compileFile(uncompiled, minCellLength);
			SpeedRegionLookup lookup = SpeedRegionLookupBuilder.fromCompiled(compiled);
	
			// count the wrong ones
			wrong = 0;
			for(PointRecord pr:points ){
				String region = lookup.findRegionType(pr.point);
				boolean correct = region!=null == pr.inside;
				if(!correct){
					wrong++;
				}
			}
			
			System.out.println("It " + step + ", MinCell " + minCellLength + ", nbWrong " + wrong);;
			if(step==0){
				assertTrue("Many points should be wrong on first step", wrong > npoints/4);
			}
			
			if(lastWrong!=-1){
				assertTrue("Nb wrong should be the same or reduce on each step (unless we get very unlucky!)" , wrong<=lastWrong);
			}
			
			minCellLength /=3;
			step++;
			lastWrong = wrong;
		}

		assertEquals("No points should be wrong on first step",0, wrong);
 
	}
}
