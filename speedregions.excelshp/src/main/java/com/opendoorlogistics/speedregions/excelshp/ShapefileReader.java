package com.opendoorlogistics.speedregions.excelshp;

import static com.opendoorlogistics.speedregions.utils.ExceptionUtils.asUncheckedException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.opendoorlogistics.speedregions.utils.ExceptionUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class ShapefileReader {
	private static final CoordinateReferenceSystem wgs84crs;
	private static final CRSAuthorityFactory crsFac;
	private static final String SHAPEFILE_GEOM_FIELDNAME;
	
//	private static final SimpleSoftReferenceMap<File, ODLDatastore<? extends ODLTableReadOnly>> shapefileLookupCache = new SimpleSoftReferenceMap<>();

	
	static{
		// This fieldname is standard for all shapefiles
		SHAPEFILE_GEOM_FIELDNAME = TextUtils.stdString("the_geom");
		
		// ensure geotools uses longitude, latitude order, not latitude, longitude, in the entire application
		System.setProperty("org.geotools.referencing.forceXY", "true");
		crsFac = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", null);		
		try {
			wgs84crs = crsFac.createCoordinateReferenceSystem("4326");	
			

		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	private static MathTransform getTransformToWGS84(DataStore shapefile, String type) throws IOException {
		CoordinateReferenceSystem crs = shapefile.getSchema(type).getCoordinateReferenceSystem();
		try {
			return CRS.findMathTransform(crs, wgs84crs,true);
		} catch (Throwable e) {
			throw ExceptionUtils.asUncheckedException(e);
		}

	}

	/**
	 * Read the shapefile and return it in a map keyed by the standardised string id field value
	 * @param file
	 * @param idFieldname
	 * @return
	 */
	public static TreeMap<String, Geometry> readShapefile(File file, String idFieldname){
		SimpleFeatureIterator it = null;
		DataStore shapefile = null;
		try {
			// open the shapefile
			Map<String, URL> map = new HashMap<String, URL>();
			map.put("url", file.toURI().toURL());
			shapefile =DataStoreFinder.getDataStore(map);
			
			// check not corrupt
			if(shapefile.getTypeNames().length!=1){
				throw new RuntimeException("Shapefile should only contain one type");
			}
	
			// Get the index of the id field and the_geom field
			String typename=shapefile.getTypeNames()[0];
			SimpleFeatureType schema = shapefile.getSchema(typename);
			int nAttrib = schema.getAttributeCount();
			int idField=-1;
			int geomField=-1;
			for(int i =0 ; i< nAttrib ; i++){
				String std =TextUtils.stdString(schema.getDescriptor(i).getLocalName());
				if(TextUtils.stdString(idFieldname).equals(std)){
					idField = i;
				}
				if(SHAPEFILE_GEOM_FIELDNAME.equals(std)){
					geomField = i;
				}
		
			}
			if(idField==-1){
				throw new RuntimeException("Could not find id field in shapefile");
			}
			if(geomField==-1){
				throw new RuntimeException("Could not find the_geom field in shapefile");
			}
			
			// get coord transform to turn into wgs84 long-lat
			MathTransform toWGS84 = getTransformToWGS84(shapefile, typename);

			// parse the objects
			SimpleFeatureSource source = shapefile.getFeatureSource(typename);
			SimpleFeatureCollection collection = source.getFeatures();
			it = collection.features();
			int objectIndex=0;
			TreeMap<String, Geometry> ret = new TreeMap<>();
			while (it.hasNext()) {
				SimpleFeature feature = it.next();

				//System.out.println(feature.getID());
				
				if (SimpleFeature.class.isInstance(feature)) {
					SimpleFeature sf = (SimpleFeature) feature;
					
					// get id
					Object oid = sf.getAttribute(idField);
					if(oid==null || oid.toString().length()==0){
						throw new RuntimeException("Found null or empty id for object in shapefile");
					}
					String id = oid.toString();
					
					// get geometry
					Object ogeom = sf.getAttribute(geomField);
					if(ogeom==null || !Geometry.class.isInstance(ogeom)){
						continue;
					}
					
					Geometry geometry = JTS.transform((Geometry)ogeom, toWGS84);				
					if(!Polygon.class.isInstance(geometry) && !MultiPolygon.class.isInstance(geometry)){
						throw new RuntimeException("Found non-polygonal geometry for object with id " + id  +" in shapefile");
					}
			
					ret.put(TextUtils.stdString(id), geometry);
				}
			}
			
			return ret;
			
		} catch (Throwable e) {
			throw asUncheckedException(e);
		}
		finally {
			if (it != null) {
				it.close();
			}
			if (shapefile != null) {
				shapefile.dispose();
			}
		}
		
	}
}
