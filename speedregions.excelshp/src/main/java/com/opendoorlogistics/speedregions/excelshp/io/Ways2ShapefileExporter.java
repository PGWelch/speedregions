package com.opendoorlogistics.speedregions.excelshp.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.Geometries;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO.IncrementalShapefileWriter;
import com.opendoorlogistics.speedregions.utils.AbstractNode;
import com.opendoorlogistics.speedregions.utils.AbstractNode.NodeVisitor;
import com.opendoorlogistics.speedregions.utils.AbstractNodeWithChildArray;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

public class Ways2ShapefileExporter {
	private static final Logger LOGGER = Logger.getLogger(Ways2ShapefileExporter.class.getName());

	private final long maxPointsInNode;
	private final int maxDepth;
	private final double simplificationTolerance;

	public Ways2ShapefileExporter(long maxPointsInNode, int maxDepth, double simplificationTolerance) {
		this.maxPointsInNode = maxPointsInNode;
		this.maxDepth = maxDepth;
		this.simplificationTolerance = simplificationTolerance;

	}

	
	/**
	 * Node to store a chunk of geometry which gets exported together
	 * @author Phil
	 *
	 */
	private static class NodeWithEnvelope extends AbstractNodeWithChildArray{
		protected Envelope envelope;
		NodeWithEnvelope(Bounds bounds) {
			super(bounds);
			envelope = bounds.asEnvelope();
		}
	}
	

	/**
	 * Node optimised for testing spatial overlaps
	 * @author Phil
	 *
	 */
	private static class SpatialOverlapCheckerNode extends NodeWithEnvelope{
		private static final int MAX_LEAVES=  10;
		private ArrayList<Envelope> leaves = new ArrayList<>();
		private boolean wholyContainedNode=false;
		private final double minSideLength;
		
		SpatialOverlapCheckerNode(Bounds bounds, double minSideLength) {
			super(bounds);
			this.minSideLength = minSideLength;
		}
		
		boolean intersects(Envelope e){
			if(wholyContainedNode && e.intersects(this.envelope)){
				return true;
			}
			
			if(getNbChildren()==0){
				for(Envelope l:leaves){
					if(l.intersects(e)){
						return true;
					}
				}
			}else{
				for(int i =0 ; i< getNbChildren() ; i++){
					if(((SpatialOverlapCheckerNode)getChild(i)).intersects(e)){
						return true;
					}
				}
			}
			return false;
		}
		
		void add(Envelope otherEnvelope, int depth, int maxDepth){
			if(wholyContainedNode){
				return;
			}
			
			// once a node is totally contained there's no point splitting it any longer
			if(otherEnvelope.contains(envelope)){
				wholyContainedNode = true;
				leaves.clear();
				return;
			}
			
			// check for intersection
			boolean intersect = otherEnvelope.intersects(envelope);
			if(!intersect){
				return;
			}
			
			// check for split
			if(getNbChildren()==0 && leaves.size()>=MAX_LEAVES 
					&& getBounds().getWidthLng() > 2*minSideLength && getBounds().getHeightLat() > 2 * minSideLength
					&& depth < maxDepth){
				
				// split node
				doQuadSplit(new Function<Bounds, AbstractNodeWithChildArray>() {
					
					@Override
					public AbstractNodeWithChildArray apply(Bounds bounds) {
						return new SpatialOverlapCheckerNode(bounds,minSideLength);
					}
				});
				
				for(AbstractNodeWithChildArray child:getChildren()){
					for(Envelope leaf : leaves){
						((SpatialOverlapCheckerNode)child).add(leaf, depth+1, maxDepth);
					}				
				}
				
				leaves.clear();
	
			}
			
			int nc = getNbChildren();
			if(nc==0){
				leaves.add(envelope);
			}else{
				for(int i =0 ; i<nc ; i++){
					((SpatialOverlapCheckerNode)getChild(i)).add(otherEnvelope, depth+1, maxDepth);
				}
			}
		}
	}

	/**
	 * Node to store a chunk of geometry which gets exported together
	 * @author Phil
	 *
	 */
	private static class GeometryChunkNode extends NodeWithEnvelope{
		private LinkedList<LineString> geoms = new LinkedList<>();
		private SpatialOverlapCheckerNode overlapChecker;
		long points;

		GeometryChunkNode(Bounds bounds, double tolerance) {
			super(bounds);
			overlapChecker = new SpatialOverlapCheckerNode(bounds, tolerance);
		}
	}


	private void addWay(LineString lineString, Coordinate centroid,  GeometryChunkNode chunkNode, int depth) {

		// check for split...
		if (chunkNode.getNbChildren() == 0&& (chunkNode.points + lineString.getNumPoints()) > maxPointsInNode && chunkNode.geoms.size() > 0
				&& depth < maxDepth) {
			splitNode(chunkNode, depth);
		}

		if (chunkNode.getNbChildren() == 0) {
			Envelope envelope = isAddable(lineString, chunkNode);
			if(envelope!=null){
				// add to this node
				chunkNode.geoms.add(lineString);
				chunkNode.overlapChecker.add(envelope, 1, 3);
				chunkNode.points += lineString.getNumPoints();
			}
		} else {
			addToNodesChildren(lineString, centroid, chunkNode, depth);
		}

	}

	/**
	 * Check if addable and return the search / blocking envelope if addable
	 * @param ls
	 * @param chunkNode
	 * @return
	 */
	private Envelope isAddable(LineString ls,GeometryChunkNode chunkNode){
		double len = ls.getLength();
		
		Coordinate centre = ls.getCentroid().getCoordinate();
		Envelope searchEnvelope = new Envelope(centre.x - simplificationTolerance, centre.x + simplificationTolerance,
				centre.y - simplificationTolerance, centre.y + simplificationTolerance);
		
		if (len == 0) {
			// filter any zero length lines from the start...
			return null;
		}

		// add if no linestrings included or line is longer than tol
		if (chunkNode.geoms.size() == 0 || len>simplificationTolerance) {
			return searchEnvelope;
		}

		// Add if no other line strings within tol
		// As we add in longest first order, we know any other line strings within
		// tol must be larger than this one (and hence more important)
		if(!chunkNode.overlapChecker.intersects(searchEnvelope)){
			return searchEnvelope;
		}
		
		return null;
	}
	
	private void addToNodesChildren(LineString lineString, Coordinate centroid, GeometryChunkNode node, int depth) {
		// find the containing node
		for (AbstractNodeWithChildArray child : node.getChildren()) {
			GeometryChunkNode sn = (GeometryChunkNode)child;
			if (sn.envelope.intersects(centroid)) {
				addWay(lineString, centroid,  sn, depth + 1);
				return;
			}
		}

		throw new RuntimeException("Centroid not contained within any node:" + centroid.toString());
	}

	private void splitNode(GeometryChunkNode node, int depth) {
		// split node
		node.doQuadSplit(new Function<Bounds, AbstractNodeWithChildArray>() {
			
			@Override
			public AbstractNodeWithChildArray apply(Bounds bounds) {
				return new GeometryChunkNode(bounds,simplificationTolerance);
			}
		});


		// add children to node's children
		for (LineString ls: node.geoms) {
			addToNodesChildren(ls, ls.getCentroid().getCoordinate(), node, depth);
		}

		// clear geoms on the node
		node.geoms.clear();
		node.overlapChecker = null;
		node.points = 0;
	}

//	public void buildShapefile(File file) {
//		LOGGER.info("Exporting roads shapefile to " + file.getAbsolutePath());
//
//		// built the feature type
//
//		final SimpleFeatureType type = buildType();
//		final List<SimpleFeature> features = new LinkedList<SimpleFeature>();
//
//		createFeatures(type, 0, 1000, features);
//		ShapefileIO.writeShapefile(file, type, features);
//
//		LOGGER.info("Finished exporting roads shapefile to " + file.getAbsolutePath());
//
//	}

	public static SimpleFeatureType buildType() {
		SimpleFeatureTypeBuilder builder = ShapefileIO.createWGS84FeatureTypeBuilder("RoadChunks", Geometries.MULTILINESTRING);

		// add other types
		builder.add("LowKPH", Double.class);
		builder.add("HighKPH", Double.class);
		builder.add("NbGeoms", Integer.class);
		builder.add("MinZoom", Integer.class);
		builder.add("MaxZoom", Integer.class);
		final SimpleFeatureType type = builder.buildFeatureType();
		return type;
	}

	/**
	 * Stores a line string with a len and is naturally sortest largest len first
	 * @author Phil
	 *
	 */

	public class CreateFeaturesReport{
		public long nbChunks;
		public long nbLineStrings;
		public long nbPoints;
		
		@Override
		public String toString() {
			return "nbChunks=" + nbChunks + ", nbLineStrings=" + nbLineStrings + ", nbPoints=" + nbPoints ;
		}
		
		
	}

	public CreateFeaturesReport createFeatures(final SimpleFeatureType type, LinkedList<LineString> ways,final double lowKPH, final double highKPH, final int minZoom,
			final int maxZoom, final IncrementalShapefileWriter writer) {
		
		class LineStringLen implements Comparable<LineStringLen> {
			final LineString ls;
			final double len;

			LineStringLen(LineString ls, double len) {
				this.ls = ls;
				this.len = len;
			}

			@Override
			public int compareTo(LineStringLen o) {
				// Sort largest first
				return Double.compare(o.len, len);
			}

		}
		
		// Simplify and sort by size. Store in an array list so we can sort easily (may create memory problems
		// with very large numbers of ways though)
		ArrayList<LineStringLen> list = new ArrayList<>();
		for(LineString ls : ways){
			if (simplificationTolerance > 0) {
				ls = (LineString) DouglasPeuckerSimplifier.simplify(ls, simplificationTolerance);
			}			
			double len = ls.getLength();
			if(len>0){
				list.add(new LineStringLen(ls, len));
			}
		}
		
		// Add in largest first order
		Collections.sort(list);
		GeometryChunkNode root = new GeometryChunkNode(Bounds.createGlobal(),simplificationTolerance);
		for(LineStringLen lsl : list){
			addWay(lsl.ls, lsl.ls.getCentroid().getCoordinate(), root, 1);			
		}

		final CreateFeaturesReport report = new CreateFeaturesReport();
		
		root.visitNodes(new NodeVisitor() {
			
			@Override
			public boolean visit(AbstractNode node) {
				GeometryChunkNode gcn =(GeometryChunkNode)node;
				if(gcn.geoms.size()>0){
					report.nbChunks++;
					report.nbLineStrings+= gcn.geoms.size();
					report.nbPoints += gcn.points;
					LineString[] lsa = gcn.geoms.toArray(new LineString[gcn.geoms.size()]);
		
					GeometryFactory factory = new GeometryFactory();
					MultiLineString mls = factory.createMultiLineString(lsa);
					SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
					featureBuilder.add(mls);
					featureBuilder.add(lowKPH);
					featureBuilder.add(new Double(highKPH));
					featureBuilder.add(new Integer(lsa.length));
					featureBuilder.add(new Integer(minZoom));
					featureBuilder.add(new Integer(maxZoom));
					writer.writeFeature(featureBuilder.buildFeature(null));
				}
	
				return true;
			}
		});
		
		return report;
	}

	
}
