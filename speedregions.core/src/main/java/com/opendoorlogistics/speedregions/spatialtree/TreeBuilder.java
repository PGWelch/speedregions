/*
 * Copyright 2016 Open Door Logistics Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opendoorlogistics.speedregions.spatialtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.LngLatAlt;

import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

public class TreeBuilder {
	private static final double INTERSECTION_GEOM_SAFETY_FRACTION = 0.01;
	private static final Logger LOGGER = Logger.getLogger(TreeBuilder.class.getName());

	private final static double MIN_SIDES_RATIO = 0.25;
	private final GeometryFactory geomFactory;
	private final SpatialTreeNodeWithGeometry root;
	private final double minLengthMetres;
	private long nextPolygonPriority = 1;

	private TreeBuilder(GeometryFactory geomFactory, double minSideLengthMetres) {
		this.geomFactory = geomFactory;
		this.root = SpatialTreeNodeWithGeometry.createGlobal(geomFactory);
		this.minLengthMetres = minSideLengthMetres;

		// distance calculations aren't valid if we stretch round the whole globe
		// so we split the globe into quarters first to ensure they can be done
		boolean horizSplit = true;
		List<SpatialTreeNodeWithGeometry> openSet = new ArrayList<>();
		openSet.add(root);
		for (int i = 0; i < 2; i++) {
			List<SpatialTreeNodeWithGeometry> newNodes = new ArrayList<>();
			for (SpatialTreeNode node : openSet) {
				Bounds b = node.getBounds();
				List<SpatialTreeNodeWithGeometry> split = horizSplit ? getHorizontalSplit(b) : getVerticalSplit(b);
				node.getChildren().addAll(split);
				newNodes.addAll(split);
			}
			openSet = newNodes;
			horizSplit = !horizSplit;
		}
	}

	private boolean isVerticallySplittable(Bounds b) {
		double newWidth = GeomUtils.getWidthMetres(b) / 2;
		double ratio = newWidth / GeomUtils.getHeightMetres(b);
		return isOkSidesRatio(ratio) && newWidth > minLengthMetres;
	}

	private boolean isOkSidesRatio(double ratio) {
		return ratio >= (MIN_SIDES_RATIO - 0.0001);
	}

	private boolean isHorizontallySplittable(Bounds b) {
		double newHeight = GeomUtils.getHeightMetres(b) / 2;
		double ratio = newHeight / GeomUtils.getWidthMetres(b);
		return isOkSidesRatio(ratio) && newHeight > minLengthMetres;
	}

	/**
	 * Return true if and only if both are assigned and assigned to the same region
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	private boolean isEqualNonNullAssignment(SpatialTreeNode a, SpatialTreeNode b) {
		if (a.getRegionType() == null || b.getRegionType() == null) {
			return false;
		}
		return a.getRegionType().equals(b.getRegionType());
	}

	private void recombineChildrenIfPossible(SpatialTreeNode node) {
		if (recombineChildrenIfPossibleRule1(node)) {
			return;
		}

		// if(recombineChildrenIfPossibleRule2(node)){
		//
		// }
	}

	// /**
	// * TODO If you have 3 or 4 grandchildren and only one is assigned,
	// * check if the other 3 can be combined together.
	// * @param node
	// * @return
	// */
	// private boolean recombineChildrenIfPossibleRule2(SpatialTreeNode node) {
	// int nc = node.getChildren().size();
	// if (nc == 0) {
	// // Must have children
	// return false;
	// }
	//
	// ArrayList<SpatialTreeNode> grandchildren = new ArrayList<>();
	// for(SpatialTreeNode child:node.getChildren()){
	// grandchildren.addAll(child.getChildren());
	// }
	//
	// int assignedGrandchildren=0;
	// for(SpatialTreeNode gc:grandchildren){
	// if(gc.getRegionId()!=null){
	// assignedGrandchildren++;
	// }else if(gc.getChildren().size()>0){
	// // unassigned grandchildren must be empty
	// return false;
	// }
	// }
	//
	// if(assignedGrandchildren!=1){
	// return false;
	// }
	//
	// // can we recombine them??
	//
	// // what about a child which isn't split into grandchildren yet?
	//
	// // we'll probably commonly have a node, split into 2 children, with 1 child only split again.
	//
	// // what if we recombine something that's assigned later-on....
	//
	// return false;
	// }

	/**
	 * Recombine a node's children if they are all assigned to the same region *and* they don't have children themselves
	 * (we recursively call this anyway so we should still recursively recombine).
	 * 
	 * @param node
	 */
	private boolean recombineChildrenIfPossibleRule1(SpatialTreeNode node) {

		int nc = node.getChildren().size();
		if (nc == 0) {
			// Must have children
			return false;
		}

		for (int i = 0; i < nc; i++) {
			SpatialTreeNode child = node.getChildren().get(i);
			if (child.getChildren().size() > 0) {
				// Children cannot have children
				return false;
			}

			if (child.getRegionType() == null) {
				// Must all be assigned
				return false;
			}

			if (i > 0) {
				if (!isEqualNonNullAssignment(child, node.getChildren().get(0))) {
					// Must all be have same assigned id
					return false;
				}

			}
		}

		// recombine
		SpatialTreeNode child0 = node.getChildren().get(0);
		node.getChildren().clear();
		node.setRegionType(child0.getRegionType());
		node.setAssignedPriority(child0.getAssignedPriority());
		return true;
	}

	private void addRecursively(SpatialTreeNodeWithGeometry node, int depth, Polygon originalGeometry, String geometryId) {
		// check if node is already assigned, nodes are assigned to the first geometry
		// that (a) totally encloses them, or (b) if the node is split to the finest granularity level,
		// the first geometry they intersect
		if (node.getRegionType() != null) {
			return;
		}

		// Getting the intersection goes straight into costly operations so do envelopes first
		Envelope ogEnvelope = originalGeometry.getEnvelopeInternal();
		Envelope nEnvelope = node.getEnvelope();
		if (!ogEnvelope.intersects(nEnvelope)) {
			return;
		}

		// We're getting nodes on boundaries which aren't properly assigned.
		// Get a slightly larger intersection geometry (e.g. 10% larger than node)
		// and use this as the 'cut-down' / filtered geometry to test against
		Geometry expandedNodeGeometry = node.getExpandedGeometry(geomFactory, INTERSECTION_GEOM_SAFETY_FRACTION);

		// Get intersection with the node.
		// We can quit if the intersection is empty and crucially all calculations
		// within this node and its children only need to be done on the intersecting geometry,
		// so we cut out massive amount of geometry - thereby speeding up processing lots
		Geometry intersection = originalGeometry.intersection(expandedNodeGeometry);

		// Ensure geometry is a polygon(s).
		// The intersection can sometimes create a GEOMETRYCOLLECTION containing polygons which gives an
		// exception in the JTS contains function.
		// Theoretically it might be able to create points and lines too.
		LinkedList<Polygon> polygons = GeomUtils.getPolygons(intersection);
		if (polygons.size() < originalGeometry.getNumGeometries()) {
			LOGGER.info(
					"...Had " + originalGeometry.getNumGeometries() + " input geometries but only " + polygons.size() + " polygons found.");
		}
		for (Polygon polygon : polygons) {

			// Do proper intersection test for the individual polygon
			if (polygon.intersects(node.getGeometry())) {

				if (node.getChildren().size() == 0) {
					
					// No children already. Assign and return if:
					// (a) a node is totally contained by the polygon or
					// (b) we can't split the node anymore
					if (isNodeAssign(node, polygon)) {
						node.setRegionType(geometryId);
						node.setAssignedPriority(nextPolygonPriority);

						// No need to process any more polygons as node is assigned...
						return;
					}
					else{
						splitNode(node, depth, polygon);		
					}
				}
				
				// Must have child nodes at this point
				
//				// If already we have children then pass down to them
//				if (node.getChildren().size() > 0) {
//					for (SpatialTreeNode child : node.getChildren()) {
//						addRecursively((SpatialTreeNodeWithGeometry) child, depth + 1, polygon, geometryId);
//					}
//				} else {
//					// No children already. Assign and return if:
//					// (a) a node is totally contained by the polygon or
//					// (b) we can't split the node anymore
//					if (isNodeAssign(node, polygon)) {
//						node.setRegionType(geometryId);
//						node.setAssignedPriority(nextPolygonPriority);
//
//						// No need to process any more polygons as node is assigned...
//						return;
//					}
//					else{
//	
//						// Split and add to the child geometries after split
//						splitNode(node, depth, polygon);
//						for (SpatialTreeNode child : node.getChildren()) {
//							addRecursively((SpatialTreeNodeWithGeometry) child, depth + 1, polygon, geometryId);
//						}
//					}
//
//				}
				
				
				// Add to children
				for (SpatialTreeNode child : node.getChildren()) {
					addRecursively((SpatialTreeNodeWithGeometry) child, depth + 1, polygon, geometryId);
				}
				
				// Try to recombine node and quit if this means the node is now assigned
				recombineChildrenIfPossible(node);
				if (node.getRegionType() != null) {
					return;
				}
			}
		}

	}

	private boolean isNodeAssign(SpatialTreeNodeWithGeometry node, Polygon polygon) {
		boolean assignNode = !isHorizontallySplittable(node.getBounds()) && !isVerticallySplittable(node.getBounds());
		if (!assignNode) {
			// The contains test is expensive, so we only do it after the cheap test
			if (polygon.contains(node.getGeometry())) {
				assignNode = true;
			}
		}
		return assignNode;
	}

	/**
	 * Split a node either horizontally or vertically (a test is done to try and estimate the best split) Only call this
	 * method on a node which can split at least horizontally or vertically.
	 * 
	 * @param node
	 * @param depth
	 * @param polygon
	 */
	private void splitNode(SpatialTreeNodeWithGeometry node, int depth, Polygon polygon) {
		Bounds b = node.getBounds();
		boolean horizSplitOK = isHorizontallySplittable(b);
		boolean verticalSplitOK = isVerticallySplittable(b);
		if (!horizSplitOK && !verticalSplitOK) {
			throw new RuntimeException("Only call this method on a node which can split at least horizontally or vertically");
		}

		if (!horizSplitOK) {
			node.getChildren().addAll(getVerticalSplit(b));
		} else if (!verticalSplitOK) {
			node.getChildren().addAll(getHorizontalSplit(b));
		} else {
			// General case. Test both splits. If one gives a non-intersecting side, take that
			// horizontal split along line of constant latitude
			List<SpatialTreeNodeWithGeometry> hSplitList = getHorizontalSplit(b);
			boolean hGood = isGoodSplit(polygon, hSplitList);

			// vertical split along line of constant longitude
			List<SpatialTreeNodeWithGeometry> vSplitList = getVerticalSplit(b);
			boolean vGood = isGoodSplit(polygon, vSplitList);

			List<SpatialTreeNodeWithGeometry> chosen = null;
			if (hGood && !vGood) {
				chosen = hSplitList;
			} else if (!hGood && vGood) {
				chosen = vSplitList;
			} else if (depth % 2 == 0) {
				chosen = hSplitList;
			} else {
				chosen = vSplitList;
			}
			node.getChildren().addAll(chosen);

		}
	}

	private boolean isGoodSplit(Polygon geometry, List<SpatialTreeNodeWithGeometry> hSplitList) {
		return isIntersecting(hSplitList.get(0), geometry) != isIntersecting(hSplitList.get(1), geometry);
	}

	private List<SpatialTreeNodeWithGeometry> getVerticalSplit(Bounds b) {
		List<SpatialTreeNodeWithGeometry> vSplitList = new ArrayList<>(2);
		double dLngCentre = GeomUtils.getLngCentre(b);
		for (int i = 0; i <= 1; i++) {
			double lngMin = i == 0 ? b.getMinLng() : dLngCentre;
			double lngMax = i == 0 ? dLngCentre : b.getMaxLng();
			vSplitList.add(new SpatialTreeNodeWithGeometry(geomFactory, new Bounds(lngMin, lngMax, b.getMinLat(), b.getMaxLat())));
		}
		return vSplitList;
	}

	private List<SpatialTreeNodeWithGeometry> getHorizontalSplit(Bounds b) {
		List<SpatialTreeNodeWithGeometry> hSplitList = new ArrayList<>(2);
		double dLatCentre = GeomUtils.getLatCentre(b);
		for (int i = 0; i <= 1; i++) {
			double latMin = i == 0 ? b.getMinLat() : dLatCentre;
			double latMax = i == 0 ? dLatCentre : b.getMaxLat();
			hSplitList.add(new SpatialTreeNodeWithGeometry(geomFactory, new Bounds(b.getMinLng(), b.getMaxLng(), latMin, latMax)));
		}
		return hSplitList;
	}

	private boolean isIntersecting(SpatialTreeNodeWithGeometry node, Geometry geometry) {
		return node.getGeometry().intersects(geometry);
	}

	private void recurseFinaliseNode(SpatialTreeNode node) {
		if (node.getRegionType() != null) {
			return;
		}

		// // if we have a horizontal or vertical which are assigned to the same, combine them
		// if(node.getChildren().size()==4){
		//
		// }

		// set no 'no assigned descendents' priority
		node.setAssignedPriority(Long.MAX_VALUE);

		// loop over children getting highest priority and trimming empty
		Iterator<SpatialTreeNode> itChild = node.getChildren().iterator();
		while (itChild.hasNext()) {
			SpatialTreeNode child = itChild.next();
			recurseFinaliseNode(child);
			if (child.getAssignedPriority() == Long.MAX_VALUE) {
				// remove empty child
				itChild.remove();
			} else {
				// get the highest (numerically minimum) priority
				node.setAssignedPriority(Math.min(node.getAssignedPriority(), child.getAssignedPriority()));
			}
		}

		// sort children by highest (numerically lowest) priority first
		Collections.sort(node.getChildren(), new Comparator<SpatialTreeNode>() {

			public int compare(SpatialTreeNode o1, SpatialTreeNode o2) {
				return Long.compare(o1.getAssignedPriority(), o2.getAssignedPriority());
			}
		});

		// if we only have one child remaining, delete this node by replacing our content with the child's
		if (node.getChildren().size() == 1) {
			SpatialTreeNode child = node.getChildren().get(0);
			node.setChildren(child.getChildren());
			SpatialTreeNode.copyNonChildFields(child, node);
		}

	}

	private synchronized SpatialTreeNode finishBuilding() {
		// deep copy without the extra builder fields (i.e. so use the base bean class)
		SpatialTreeNode ret = new SpatialTreeNode(root);
		recurseFinaliseNode(ret);
		LOGGER.info("Finished building spatial tree");
		return ret;
	}

	public static SpatialTreeNode build(FeatureCollection fc, double minDiagonalLengthMetres) {
		return build(Arrays.asList(fc), minDiagonalLengthMetres);
	}

	/**
	 * Build the spatial tree, giving priority to polygons based on their order within each feature collection.
	 * 
	 * @param featureCollections
	 * @param minDiagonalLengthMetres
	 * @return
	 */
	public static SpatialTreeNode build(List<FeatureCollection> featureCollections, double minDiagonalLengthMetres) {
		LOGGER.info("Starting build of spatial tree");

		GeometryFactory geomFactory = GeomUtils.newGeomFactory();
		TreeSet<TempPolygonRecord> prioritised = prioritisePolygons(featureCollections);

		TreeBuilder builder = new TreeBuilder(geomFactory, minDiagonalLengthMetres);
		long count = 0;
		for (TempPolygonRecord poly : prioritised) {
			// count points in polygon for logging only
			long pointsCount = 0;
			for (List<LngLatAlt> list : poly.polygon.getCoordinates()) {
				pointsCount += list.size();
			}
			LOGGER.info("Adding polygon " + count++ + "/" + prioritised.size() + " containing " + pointsCount
					+ " points to spatial tree with " + builder.root.countNodes() + " node(s)");
			com.vividsolutions.jts.geom.Polygon jtsPolygon = GeomUtils.toJTS(geomFactory, poly.polygon);
			builder.addRecursively(builder.root, 0, jtsPolygon, poly.stdRegionType);
			builder.nextPolygonPriority++;
			// builder.add(jtsPolygon, poly.stdRegionType);
		}

		return builder.finishBuilding();
	}

	private static class TempPolygonRecord implements Comparable<TempPolygonRecord> {
		int fileIndex;
		int positionInFile;
		org.geojson.Polygon polygon;
		String stdRegionType;
		long polyNumber;

		TempPolygonRecord(int fileIndex, int positionInFile, org.geojson.Polygon polygon, String stdRegionType, long uniquePolyNumber) {
			this.fileIndex = fileIndex;
			this.positionInFile = positionInFile;
			this.polygon = polygon;
			this.stdRegionType = stdRegionType;
			this.polyNumber = uniquePolyNumber;
		}

		public int compareTo(TempPolygonRecord o) {
			// first positions in files first
			int diff = Integer.compare(positionInFile, o.positionInFile);

			// then files
			if (diff == 0) {
				diff = Integer.compare(fileIndex, o.fileIndex);
			}

			// then global polygon just to ensure we always add to the treeset
			if (diff == 0) {
				diff = Long.compare(polyNumber, o.polyNumber);
			}

			return diff;
		}

	}

	private static TreeSet<TempPolygonRecord> prioritisePolygons(List<FeatureCollection> files) {

		// prioritise all polygons and link them up to the local rules in their files
		int nfiles = files.size();
		TreeSet<TempPolygonRecord> prioritisedPolygons = new TreeSet<TempPolygonRecord>();
		for (int ifile = 0; ifile < nfiles; ifile++) {
			FeatureCollection fc = files.get(ifile);
			if (fc != null) {
				int nfeatures = fc.getFeatures().size();
				for (int iFeat = 0; iFeat < nfeatures; iFeat++) {
					Feature feature = fc.getFeatures().get(iFeat);
					String regionType = TextUtils.findRegionType(feature);

					if (regionType == null) {
						throw new RuntimeException("Found record without a " + SpeedRegionConsts.REGION_TYPE_KEY + " property");
					}

					if (regionType.trim().length() == 0) {
						throw new RuntimeException("Found record with an empty " + SpeedRegionConsts.REGION_TYPE_KEY + " property");
					}

					regionType = TextUtils.stdString(regionType);

					// if (stdRegionIds.contains(regionId)) {
					// throw new RuntimeException(
					// "RegionId " + regionId + " was found in more than one feature. RegionIds should be unique."
					// + " Use a multipolygon if you want a region spanning several polygons.");
					// }
					// stdRegionIds.add(regionId);

					if (feature.getGeometry() == null) {
						throw new RuntimeException("Found feature without geometry");
					}

					if (GeomUtils.isGeoJSONPoly(feature.getGeometry())) {
						prioritisedPolygons.add(new TempPolygonRecord(ifile, iFeat, (org.geojson.Polygon) feature.getGeometry(), regionType,
								prioritisedPolygons.size()));
					} else if (GeomUtils.isGeoJSONMultiPoly(feature.getGeometry())) {
						for (org.geojson.Polygon polygon : GeomUtils
								.toGeoJSONPolygonList((org.geojson.MultiPolygon) feature.getGeometry())) {
							prioritisedPolygons.add(new TempPolygonRecord(ifile, iFeat, polygon, regionType, prioritisedPolygons.size()));
						}

					} else {
						throw new RuntimeException("Found feature with non-polygon geometry type");
					}

				}
			}
		}

		return prioritisedPolygons;
	}

}
