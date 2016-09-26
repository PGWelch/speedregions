package com.opendoorlogistics.speedregions;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

import org.geojson.Feature;

import com.opendoorlogistics.speedregions.beans.RegionLookupBean;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedRules;
import com.opendoorlogistics.speedregions.processor.GeomConversion;
import com.opendoorlogistics.speedregions.processor.RegionProcessorUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;

public class Examples {
	public static String CENTRAL_MALTA_POLYGON = "POLYGON ((14.457384575303589 35.89565677043419, 14.456538749635243 35.899281931840044, 14.45859868615868 35.90234106346939, 14.460328621300578 35.90358851655882, 14.46264295483867 35.903662185061194, 14.463669377398375 35.90303166793873, 14.466885473317713 35.90343600291866, 14.474620438287124 35.90081589501755, 14.475606160330354 35.902646355913724, 14.478262151134963 35.903359408417245, 14.481700590101356 35.903080891875106, 14.482825259247209 35.905273952038094, 14.484661024372603 35.90645324612588, 14.482064856652158 35.90929879181718, 14.482614230258406 35.911169136445515, 14.484530452670631 35.91230375710397, 14.492025801013021 35.91403819532742, 14.49209990785928 35.91684050224841, 14.494218521886934 35.91815947314537, 14.496863088245474 35.917848707287725, 14.504244527454459 35.914234065703646, 14.50582148853122 35.91245082851032, 14.505311760149157 35.910296542761735, 14.5030159105819 35.909109520457314, 14.504048385472043 35.90818132829852, 14.503548914955177 35.90579333582091, 14.50092732725821 35.904628271697696, 14.495495000399066 35.90440262247155, 14.494694111497987 35.90378994267667, 14.498669762918261 35.90272960741797, 14.499548668948856 35.900796901378556, 14.498545043874289 35.89890442345793, 14.497541162722243 35.89855449253056, 14.502196889073126 35.89683072556156, 14.504298723703307 35.89835412719782, 14.50670889892207 35.898981673434285, 14.508739395180763 35.90070478653498, 14.512138016515323 35.90159745544735, 14.513991282823419 35.903362666724455, 14.51621459514123 35.903592849092746, 14.520334468188105 35.902897613283116, 14.522506008211584 35.90169913988075, 14.52299530936342 35.8996078562811, 14.521515745664901 35.89784879017723, 14.519584399741111 35.89755227488691, 14.519862388036774 35.89447329640674, 14.518251389449883 35.89309856713643, 14.515437881535677 35.8919589303002, 14.514770673501175 35.890967968579645, 14.515983300634563 35.88908503097143, 14.516154084243057 35.88682176965722, 14.515175118313456 35.88470671427295, 14.516435313522003 35.88291993047562, 14.516802508091873 35.88092412007049, 14.515407889330998 35.87925226543253, 14.512985329981154 35.87878416554829, 14.507650950126955 35.87947507812174, 14.50519566081577 35.878075059180354, 14.502811431884766 35.878618985083854, 14.50084302309484 35.879815168822255, 14.498347497452109 35.87825779485734, 14.495844329748312 35.87862336637347, 14.494338578898267 35.88028430826766, 14.49460319924726 35.881985165681826, 14.484047355963513 35.882519382255566, 14.481711218323378 35.883442278600285, 14.481073397425481 35.88489968444014, 14.478373636248305 35.88418884940565, 14.470820535662368 35.88460609021213, 14.468694102091542 35.88536100567918, 14.467828969865243 35.887694007343896, 14.463597860707246 35.88675508772746, 14.461721804994067 35.887686648168255, 14.458288577455004 35.891024365638074, 14.457494403605521 35.8927206934688, 14.457384575303589 35.89565677043419))";

	public static void main(String[] args) throws Exception {

		SpeedRules gb = RegionProcessorUtils.fromJSON(new File("examples\\GB.json"), SpeedRules.class);
		RegionLookupBean rlb = SpeedRegionBeanBuilder.buildBeanFromSpeedRulesObjs(Arrays.asList(gb), 250);
		System.out.println(GeomConversion.toODLTable(rlb.getQuadtree(), true));

	}

	public static void runMaltaExample() {
		// build single quadtree
		SpeedRules rules = createMaltaExample(0.75);

		RegionLookupBean rlb = SpeedRegionBeanBuilder.buildBeanFromSpeedRulesObjs(Arrays.asList(rules), 200);

		System.out.println();
		System.out.println(GeomConversion.toODLTable(rlb.getQuadtree(), true));

		// build some random points
		SpeedRegionLookup lookup = SpeedRegionLookupBuilder.convertFromBean(rlb);
		Envelope envelope = GeomConversion.toJTS(CENTRAL_MALTA_POLYGON).getEnvelopeInternal();
		Random random = new Random(123);
		int npoints = 100;
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < npoints; i++) {
			Coordinate coordinate = new Coordinate(random.nextDouble() * (envelope.getMaxX() - envelope.getMinX()) + envelope.getMinX(),
					random.nextDouble() * (envelope.getMaxY() - envelope.getMinY()) + envelope.getMinY());
			Point point = RegionProcessorUtils.newGeomFactory().createPoint(coordinate);
			String regionId = lookup.findRegionId(point);
			builder.append(Double.toString(coordinate.y));
			builder.append("\t");
			builder.append(Double.toString(coordinate.x));
			builder.append("\t");
			builder.append(regionId!=null?regionId:"");
			builder.append(System.lineSeparator());
		}
		System.out.println();
		System.out.println(builder.toString());
	}

	public static SpeedRules createMaltaExample(double speedMultiplier) {
		Feature feature = new Feature();
		feature.setProperty(SpeedRegionConsts.REGION_ID_KEY, "valleta");
		org.geojson.Polygon geoJSONPolygon = GeomConversion.toGeoJSONPolygon(CENTRAL_MALTA_POLYGON);
		feature.setGeometry(geoJSONPolygon);

		SpeedRules rules = new SpeedRules();
		rules.setCountryCode("mt");
		rules.getGeojsonFeatureCollection().add(feature);

		SpeedRule rule = new SpeedRule();
		rule.getFlagEncoders().add("car");
		rule.getRegionIds().add("valleta");
		rule.setMultiplier(speedMultiplier);
		rules.getRules().add(rule);
		return rules;
	}

}
