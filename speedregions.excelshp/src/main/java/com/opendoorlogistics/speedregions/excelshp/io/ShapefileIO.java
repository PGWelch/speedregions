package com.opendoorlogistics.speedregions.excelshp.io;

import static com.opendoorlogistics.speedregions.utils.ExceptionUtils.asUncheckedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.RuleConversionInfo;
import com.opendoorlogistics.speedregions.utils.ExceptionUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class ShapefileIO {
	private static final Logger LOGGER = Logger.getLogger(ShapefileIO.class.getName());

	private static final CoordinateReferenceSystem wgs84crs;
	private static final CRSAuthorityFactory crsFac;
	private static final String SHAPEFILE_GEOM_FIELDNAME;

	// private static final SimpleSoftReferenceMap<File, ODLDatastore<? extends ODLTableReadOnly>> shapefileLookupCache
	// = new SimpleSoftReferenceMap<>();

	static {
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
			return CRS.findMathTransform(crs, wgs84crs, true);
		} catch (Throwable e) {
			throw ExceptionUtils.asUncheckedException(e);
		}

	}

	public static List<String> readHeaderNames(File file) {
		DataStore shapefile = null;
		try {
			shapefile = DataStoreFinder.getDataStore(createURLMap(file));

			// check not corrupt
			if (shapefile.getTypeNames().length != 1) {
				throw new RuntimeException("Shapefile should only contain one type");
			}

			String typename = shapefile.getTypeNames()[0];
			SimpleFeatureType schema = shapefile.getSchema(typename);
			int nAttrib = schema.getAttributeCount();
			ArrayList<String> ret = new ArrayList<>();
			for (int i = 0; i < nAttrib; i++) {
				ret.add(schema.getDescriptor(i).getLocalName());
			}
			return ret;

		} catch (Throwable e) {
			throw asUncheckedException(e);
		} finally {

			if (shapefile != null) {
				shapefile.dispose();
			}
		}
	}

	/**
	 * Read the shapefile and return it in a map keyed by the standardised string id field value
	 * 
	 * @param file
	 * @param idFieldname
	 * @return
	 */
	public static TreeMap<String, Geometry> readShapefile(File file, String idFieldname) {
		LOGGER.info("Reading shapefile " + file.getAbsolutePath());
		SimpleFeatureIterator it = null;
		DataStore shapefile = null;
		try {
			// open the shapefile
			shapefile = DataStoreFinder.getDataStore(createURLMap(file));

			if (shapefile == null) {
				throw new RuntimeException("Could not load shapefile: " + file.getAbsolutePath());
			}

			// check not corrupt
			if (shapefile.getTypeNames().length != 1) {
				throw new RuntimeException("Shapefile should only contain one type");
			}

			// Get the index of the id field and the_geom field
			String typename = shapefile.getTypeNames()[0];
			SimpleFeatureType schema = shapefile.getSchema(typename);
			int nAttrib = schema.getAttributeCount();
			int idField = -1;
			int geomField = -1;
			for (int i = 0; i < nAttrib; i++) {
				String std = TextUtils.stdString(schema.getDescriptor(i).getLocalName());
				if (TextUtils.stdString(idFieldname).equals(std)) {
					idField = i;
				}
				if (SHAPEFILE_GEOM_FIELDNAME.equals(std)) {
					geomField = i;
				}

			}
			if (idField == -1) {
				throw new RuntimeException("Could not find id field \"" + idFieldname + "\" in shapefile");
			}
			if (geomField == -1) {
				throw new RuntimeException("Could not find the_geom field in shapefile");
			}

			// get coord transform to turn into wgs84 long-lat
			MathTransform toWGS84 = getTransformToWGS84(shapefile, typename);

			// parse the objects
			SimpleFeatureSource source = shapefile.getFeatureSource(typename);
			SimpleFeatureCollection collection = source.getFeatures();
			it = collection.features();
			TreeMap<String, Geometry> ret = new TreeMap<>();
			while (it.hasNext()) {
				SimpleFeature feature = it.next();

				// System.out.println(feature.getID());

				if (SimpleFeature.class.isInstance(feature)) {
					SimpleFeature sf = (SimpleFeature) feature;

					// get id
					Object oid = sf.getAttribute(idField);
					if (oid == null || oid.toString().length() == 0) {
						throw new RuntimeException("Found null or empty id for object in shapefile");
					}
					String id = oid.toString();

					// get geometry
					Object ogeom = sf.getAttribute(geomField);
					if (ogeom == null || !Geometry.class.isInstance(ogeom)) {
						continue;
					}

					Geometry geometry = JTS.transform((Geometry) ogeom, toWGS84);
					throwIfNonPolygonOrMultiPolygon(id, geometry);

					ret.put(TextUtils.stdString(id), geometry);
				}
			}

			LOGGER.info("Finished reading shapefile " + file.getAbsolutePath());
			return ret;

		} catch (Throwable e) {
			throw asUncheckedException(e);
		} finally {
			if (it != null) {
				it.close();
			}
			if (shapefile != null) {
				shapefile.dispose();
			}
		}

	}

	public static boolean isPolygonOrMultiPolygon(Geometry geometry) {
		return Polygon.class.isInstance(geometry) || MultiPolygon.class.isInstance(geometry);
	}

	public static void throwIfNonPolygonOrMultiPolygon(String id, Geometry geometry) {
		if (!isPolygonOrMultiPolygon(geometry)) {
			throw new RuntimeException("Found non-polygonal geometry for object with id " + id + " in shapefile");
		}
	}

	private static Map<String, URL> createURLMap(File file) throws MalformedURLException {
		Map<String, URL> map = new HashMap<String, URL>();
		map.put("url", file.toURI().toURL());
		return map;
	}

//	public static void exportShapefile(Collection<RuleConversionInfo> merged, File file) {
//		LOGGER.info("Exporting shapefile to " + file.getAbsolutePath());
//
//		// built the feature type
//
//		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
//		builder.setName("MergedSpeedRegions");
//		builder.setCRS(DefaultGeographicCRS.WGS84);
//
//		// add geom first
//		builder.add("the_geom", Geometries.POLYGON.getBinding());
//
//		// add other types
//		String[] fieldnames = new String[] { "speed_profile_id" };
//		builder.add(fieldnames[0], String.class);
//		SimpleFeatureType type = builder.buildFeatureType();
//
//		try {
//
//			// build list of features
//			List<SimpleFeature> features = new ArrayList<SimpleFeature>();
//			for (RuleConversionInfo rule : merged) {
//				for (int i = 0; i < rule.getMergedPolygons().getNumGeometries(); i++) {
//					Polygon p = (Polygon) rule.getMergedPolygons().getGeometryN(i);
//
//					// write geom first
//					SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
//					featureBuilder.add(p);
//					featureBuilder.add(rule.getParentCollapsedRule().getId());
//
//					SimpleFeature feature = featureBuilder.buildFeature(null);
//					features.add(feature);
//				}
//			}
//
//			writeShapefile(file, type, features);
//
//		} catch (Exception e) {
//			throw ExceptionUtils.asUncheckedException(e);
//		}
//
//		LOGGER.info("Finished exporting shapefile to " + file.getAbsolutePath());
//
//	}
	
	public static class IncrementalShapefileWriter{
		private ShapefileDataStore newDataStore;
		private FeatureWriter<SimpleFeatureType, SimpleFeature> writer ;
		
		public void start(File file, SimpleFeatureType type){
			newDataStore = startShapefileWrite(file, type);
			try {
				writer = newDataStore.getFeatureWriterAppend(newDataStore.getTypeNames()[0],
						Transaction.AUTO_COMMIT);
			} catch (IOException e) {
				throw ExceptionUtils.asUncheckedException(e);
			}
		}
		
		public void writeFeature(SimpleFeature feature){
			try {
				SimpleFeature toWrite = writer.next();
				for (int i = 0; i < toWrite.getType().getAttributeCount(); i++) {
					String name = toWrite.getType().getDescriptor(i).getLocalName();
					toWrite.setAttribute(name, feature.getAttribute(name));
				}

				// copy over the user data
				if (feature.getUserData().size() > 0) {
					toWrite.getUserData().putAll(feature.getUserData());
				}

				// perform the write
				writer.write();		
			} catch (Exception e) {
				throw ExceptionUtils.asUncheckedException(e);
			}
		
		}
		
		public void close(){
			try {
				if(writer!=null){
					writer.close();
				}		
			} catch (Exception e) {
				throw ExceptionUtils.asUncheckedException(e);
			}

		}
	}


	public static void writeShapefile(File file, SimpleFeatureType type, List<SimpleFeature> features) {
		try {
			ShapefileDataStore newDataStore = startShapefileWrite(file, type);

			Transaction transaction = new DefaultTransaction("create");
			// String typeName = newDataStore.getTypeNames()[0];
			String typeName = newDataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

			SimpleFeatureCollection collection = new ListFeatureCollection(type, features);
			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(collection);
				transaction.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
			}

		} catch (Exception e) {
			throw ExceptionUtils.asUncheckedException(e);
		}
	}

	private static ShapefileDataStore startShapefileWrite(File file, SimpleFeatureType type)  {
		try {
			// create shapefile
			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
			Map<String, Serializable> params = new HashMap<String, Serializable>();
			params.put("url", file.toURI().toURL());
			params.put("create spatial index", Boolean.FALSE);
			ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

			// create type in shapefile
			newDataStore.createSchema(type);
			return newDataStore;
		} catch (Exception e) {
			throw ExceptionUtils.asUncheckedException(e);
		}

	}

	public static FluentSimpleFeatureTypeBuilder createWGS84FeatureTypeBuilder(String name, Geometries geomType){
		FluentSimpleFeatureTypeBuilder builder = new FluentSimpleFeatureTypeBuilder();
		builder.setName(name);
		builder.setCRS(DefaultGeographicCRS.WGS84);

		// add geom first
		builder.add("the_geom", geomType.getBinding());
		return builder;
	}
	
	public static class FluentSimpleFeatureTypeBuilder extends SimpleFeatureTypeBuilder{
	    public FluentSimpleFeatureTypeBuilder addStr(String name) {
	    	super.add(name,String.class);
	    	return this;
	    }
	    
	    public FluentSimpleFeatureTypeBuilder addLong(String name) {
	    	super.add(name,Long.class);
	    	return this;
	    }
	}
}
