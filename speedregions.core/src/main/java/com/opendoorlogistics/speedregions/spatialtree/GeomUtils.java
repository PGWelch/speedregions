package com.opendoorlogistics.speedregions.spatialtree;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

import java.util.ArrayList;
import java.util.List;

import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.geojson.MultiPolygon;

import com.opendoorlogistics.speedregions.TextUtils;
import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;

public class GeomUtils {
	private static final double EARTH_RADIUS_METRES = 6371000;

	public static org.geojson.Polygon toGeoJSONPolygon(String wkt){
		return toGeoJSON((Polygon)toJTS(wkt));
//		try {
//			WKTReader reader = new WKTReader(RegionProcessorUtils.newGeomFactory());
//			Polygon polygon = (Polygon)reader.read(wkt);
//			return toGeoJSON(polygon);			
//		} catch (ParseException e) {
//			throw RegionProcessorUtils.asUncheckedException(e);
//		}

	}
	
	public static Geometry toJTS(String wkt){
		try {
			WKTReader reader = new WKTReader(GeomUtils.newGeomFactory());
			return reader.read(wkt);		
		} catch (ParseException e) {
			throw TextUtils.asUncheckedException(e);
		}	
	}

	public static List<org.geojson.Polygon> toGeoJSONPolygonList(org.geojson.MultiPolygon mp){
		ArrayList<org.geojson.Polygon>ret = new ArrayList<>(mp.getCoordinates().size());
		for (List<List<LngLatAlt>> coordLists : mp.getCoordinates()) {
			org.geojson.Polygon polygon = new org.geojson.Polygon();
			int nLists = coordLists.size();
			for (int i = 0; i < nLists; i++) {
				if (i == 0) {
					polygon.setExteriorRing(coordLists.get(0));
				} else {
					polygon.addInteriorRing(coordLists.get(i));
				}
			}
			ret.add(polygon);
		}	
		return ret;
	}
	
	public static boolean isGeoJSONPoly(GeoJsonObject obj){
		return obj!=null && (obj instanceof org.geojson.Polygon);
	}
	
	public static boolean isGeoJSONMultiPoly(GeoJsonObject obj){
		return obj!=null && (obj instanceof org.geojson.MultiPolygon);
	}
	
	public static boolean isGeoJSONPolyOrMultiPoly(GeoJsonObject obj){
		return isGeoJSONPoly(obj) || isGeoJSONMultiPoly(obj);
	}

	public static com.vividsolutions.jts.geom.MultiPolygon toJTS(GeometryFactory factory,org.geojson.MultiPolygon geoJsonObject){
		List<org.geojson.Polygon> gjPols = toGeoJSONPolygonList(geoJsonObject);
		Polygon[] polys = new Polygon[ gjPols.size()];
		for(int i =0 ; i < polys.length;i++){
			polys[i] = toJTS(factory, gjPols.get(i));
		}
		return factory.createMultiPolygon(polys);
	}

	public static Geometry toJTS(GeometryFactory factory,org.geojson.GeoJsonObject geoJsonObject){
		if(!isGeoJSONPolyOrMultiPoly(geoJsonObject)){
			throw new RuntimeException("Only geoJSON polygons or multipolygons are supported");
		}
		
		if(isGeoJSONPoly(geoJsonObject)){
			return toJTS(factory, (org.geojson.Polygon)geoJsonObject);
		}
		else{
			return toJTS(factory, (org.geojson.MultiPolygon)geoJsonObject);
		}
	}
	
	public static Polygon toJTS(GeometryFactory geomFactory,org.geojson.Polygon polygon){
		// need to turn a geojson polygon into a JTS polygon
		int nLists = polygon.getCoordinates().size();
		if (nLists == 0) {
			throw new RuntimeException("Invalid polygon");
		}
		int nHoles = Math.max(nLists - 1, 0);
		LinearRing exterior = null;
		LinearRing[] holes = new LinearRing[nHoles];
		for (int i = 0; i < nLists; i++) {
			List<LngLatAlt> list = polygon.getCoordinates().get(i);
			int nc = list.size();
			Coordinate[] coords = new Coordinate[nc];
			for (int j = 0; j < nc; j++) {
				LngLatAlt ll = list.get(j);
				coords[j] = new Coordinate(ll.getLongitude(), ll.getLatitude());
			}
			LinearRing ring = geomFactory.createLinearRing(coords);
			if (i == 0) {
				exterior = ring;
			} else {
				holes[i - 1] = ring;
			}
		}
		com.vividsolutions.jts.geom.Polygon jtsPolygon = geomFactory.createPolygon(exterior, holes);
		return jtsPolygon;
	}
	
	public static Polygon toJTS(Bounds b){
		
		// do clockwise
		Coordinate [] coords = new Coordinate[5];
		coords[0] = new Coordinate(b.getMinLng(), b.getMinLat());
		coords[1] = new Coordinate(b.getMinLng(), b.getMaxLat());
		coords[2] = new Coordinate(b.getMaxLng(), b.getMaxLat());
		coords[3] = new Coordinate(b.getMaxLng(), b.getMinLat());
		coords[4] = new Coordinate(b.getMinLng(), b.getMinLat());
		return GeomUtils.newGeomFactory().createPolygon(coords);
	}
	
	public static String toWKT(Geometry geometry){
		WKTWriter writer = new WKTWriter();
		return writer.write(geometry);
	}
	
	/**
	 * Write out quadtree as a tab-separated table designed for viewing in ODL Studio
	 * @param node
	 * @return
	 */
	public static String toODLTable(SpatialTreeNode node,final boolean leafNodesOnly){
		final StringBuilder builder = new StringBuilder();
		class Recurser{
			void recurse(SpatialTreeNode n){
				if(!leafNodesOnly || n.getChildren().size()==0){
					if(builder.length()>0){
						builder.append(System.lineSeparator());					
					}
					builder.append(n.getRegionType()!=null?n.getRegionType() : "");
					builder.append("\t");
					builder.append(toWKT(toJTS(n.getBounds())));				
				}
				for(SpatialTreeNode child:n.getChildren()){
					recurse(child);
				}
				
			}
		}
		Recurser recurser = new Recurser();
		recurser.recurse(node);
		return builder.toString();
	}

	public static org.geojson.MultiPolygon toGeoJSON(com.vividsolutions.jts.geom.MultiPolygon mp){
		org.geojson.MultiPolygon ret = new MultiPolygon();
		int n = mp.getNumGeometries();
		for(int i =0 ; i<n ; i++){
			org.geojson.Polygon conv = toGeoJSON((com.vividsolutions.jts.geom.Polygon)mp.getGeometryN(i));
			ret.add(conv);
		}
		return ret;
	}

	public static org.geojson.GeoJsonObject toGeoJSON(com.vividsolutions.jts.geom.Geometry jts){
		if(jts instanceof Polygon){
			return toGeoJSON((Polygon)jts);
		}
		if(jts instanceof com.vividsolutions.jts.geom.MultiPolygon){
			return toGeoJSON(( com.vividsolutions.jts.geom.MultiPolygon)jts);
		}
		throw new RuntimeException("Invalid geometry. Conversion to geoJSON only supported for polygons and multipolygons.");
	}
	
	public static org.geojson.Polygon toGeoJSON(com.vividsolutions.jts.geom.Polygon jtsPolygon){
	
		Coordinate []ring= jtsPolygon.getExteriorRing().getCoordinates();
		List<LngLatAlt> coords = coordsToLngLats(ring);
		org.geojson.Polygon geoJSONPolygon = new org.geojson.Polygon(coords);
		
		int nbRings = jtsPolygon.getNumInteriorRing();
		for(int i =0 ; i < nbRings ; i++){
			geoJSONPolygon.addInteriorRing(coordsToLngLats(jtsPolygon.getInteriorRingN(i).getCoordinates()));
		}
		return geoJSONPolygon;
	}

	private static List<LngLatAlt> coordsToLngLats(Coordinate[] ring) {
		List<LngLatAlt> coords = new ArrayList<>(ring.length);
		for(int i =0 ; i<ring.length ; i++){
			Coordinate c = ring[i];
			coords.add(new LngLatAlt(c.x, c.y));
		}
		return coords;
	}
	
	/**
	 * We may need to change the precision model in the geometry factory later-on,
	 * so we keep the creation of a geometry factory in one place
	 * @return
	 */
	public static GeometryFactory newGeomFactory() {
		GeometryFactory factory = new GeometryFactory();
		return factory;
	}


	/**
	 * See http://en.wikipedia.org/wiki/Great-circle_distance
	 * Vincetty formula. Returns metres
	 * @param from
	 * @param to
	 * @return
	 */
	public static double greatCircleApprox(double latFrom, double lngFrom, double latTo, double lngTo) {
		double lat1 = toRadians(latFrom);
		double lat2 = toRadians(latTo);
		double lng1 = toRadians(lngFrom);
		double lng2 = toRadians(lngTo);

		double deltaLng = Math.abs(lng1 - lng2);

		double a = cos(lat2) * sin(deltaLng);
		a *= a;

		double b = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng);
		b *= b;

		double c = sin(lat1) * sin(lat2);

		double d = cos(lat1) * cos(lat2) * cos(deltaLng);

		double numerator = Math.sqrt(a + b);
		double denominator = c + d;

		double centralAngle = atan2(numerator, denominator);
		double distance = EARTH_RADIUS_METRES * centralAngle;
		return distance;
	}
	
	public static double getLatCentre(Bounds b) {
		double dLatCentre = 0.5 * (b.getMinLat() + b.getMaxLat());
		return dLatCentre;
	}

	public static double getLngCentre(Bounds b) {
		double dLngCentre = 0.5 * (b.getMinLng() +b.getMaxLng());
		return dLngCentre;
	}

	public static double getWidthMetres(Bounds b) {
		double dLatCentre = getLatCentre(b);
		double width = greatCircleApprox(dLatCentre, b.getMinLng(), dLatCentre, b.getMaxLng());
		return width;
	}

	public static double getHeightMetres(Bounds b) {
		double dLngCentre = getLngCentre(b);
		double height = greatCircleApprox(b.getMinLat(), dLngCentre, b.getMaxLat(), dLngCentre);
		return height;
	}
}
