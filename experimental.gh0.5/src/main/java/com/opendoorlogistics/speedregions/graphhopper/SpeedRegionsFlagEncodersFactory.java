package com.opendoorlogistics.speedregions.graphhopper;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.routing.util.MotorcycleFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.SpeedRegionLookup;
import com.opendoorlogistics.speedregions.SpeedRegionLookup.SpeedRuleLookup;
import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.excelshp.app.AppInjectedDependencies.ProcessedWayListener;
import com.opendoorlogistics.speedregions.excelshp.app.VehicleType;
import com.opendoorlogistics.speedregions.utils.GeomUtils;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

class SpeedRegionsFlagEncodersFactory {
	private final int bytesForFlags;
	private final ArrayList<AbstractFlagEncoder> originalEncoders = new ArrayList<>();

	SpeedRegionsFlagEncodersFactory(int bytesForFlags) {
		this.bytesForFlags = bytesForFlags;
	}

	AbstractFlagEncoder createCar(PMap config, final SpeedRegionLookup lookup, ProcessedWayListener cb) {
		MyCarFlagEncoder ret = new MyCarFlagEncoder(config, lookup, cb);
		originalEncoders.add(ret.helper.noSpeedRegionsFlagEncoder);
		return ret;
	}

	AbstractFlagEncoder createMotorcycle(PMap config, final SpeedRegionLookup lookup, ProcessedWayListener cb) {
		MyMotorcycleFlagEncoder ret = new MyMotorcycleFlagEncoder(config, lookup, cb);
		originalEncoders.add(ret.helper.noSpeedRegionsFlagEncoder);
		return ret;
	}

	AbstractFlagEncoder createEncoder(String type, PMap config, final SpeedRegionLookup lookup, ProcessedWayListener cb) {
		if (TextUtils.equalsStd(type, EncodingManager.CAR)) {
			return createCar(new PMap(), lookup, cb);
		} else if (TextUtils.equalsStd(type, EncodingManager.MOTORCYCLE)) {
			return createMotorcycle(new PMap(), lookup, cb);
		} else if (TextUtils.equalsStd(type, EncodingManager.BIKE)) {
			return new BikeFlagEncoder();
		} else if (TextUtils.equalsStd(type, EncodingManager.FOOT)) {
			return new FootFlagEncoder();
		}
		throw new UnsupportedOperationException("Unsupported encoder type: " + type);
	}

	void finish() {
		// Also create a dummy encoding manager for the original encoder so they're properly initialised
		new EncodingManager(originalEncoders, bytesForFlags);

	}

	/**
	 * Get default speeds by encoder and highway type. Only supported for car and motorcycle
	 * 
	 * @return
	 */
	TreeMap<String, TreeMap<String, Double>> getDefaultSpeeds() {
		final TreeMap<String, TreeMap<String, Double>> defaultSpeeds = new TreeMap<>();
		for (String type : new String[] { EncodingManager.CAR, EncodingManager.MOTORCYCLE }) {
			TreeMap<String, Double> mapForType = new TreeMap<>();
			for (Map.Entry<String, Integer> entry : ((HasDefaultSpeeds) createEncoder(type, new PMap(), null, null)).getDefaultSpeeds()
					.entrySet()) {
				mapForType.put(entry.getKey(), entry.getValue().doubleValue());
			}
			defaultSpeeds.put(type, mapForType);
		}

		return defaultSpeeds;
	}

	private static final String ORIGINAL_SPEED_TAG = "odl-original-speed-";
	private static final String SPEED_RULE_TAG = "odl-speed-rule-";

	private static class FlagEncoderHelper {
		final private SpeedRegionLookup lookup;
		final private SpeedRuleLookup rules;
		final private String key2StoreOriginalSpeedInWay;
		final private String key2StoreSpeedRule;
		final private AbstractFlagEncoder speedRegionsFlagEncoder;
		final AbstractFlagEncoder noSpeedRegionsFlagEncoder;
		final private ProcessedWayListener processedWayListener;
		final private String encoderType;

		FlagEncoderHelper(final SpeedRegionLookup lookup, String encoderType, AbstractFlagEncoder speedRegionsFlagEncoder,
				AbstractFlagEncoder originalCarFlagEncoder, ProcessedWayListener processedWayListener) {
			this.lookup = lookup;
			this.rules = lookup != null ? lookup.createLookupForEncoder(encoderType) : null;
			this.processedWayListener = processedWayListener;
			this.encoderType = encoderType;
			this.speedRegionsFlagEncoder = speedRegionsFlagEncoder;
			this.key2StoreOriginalSpeedInWay = ORIGINAL_SPEED_TAG + encoderType;
			this.key2StoreSpeedRule = SPEED_RULE_TAG + encoderType;
			this.noSpeedRegionsFlagEncoder = originalCarFlagEncoder;

		}

		void handleWayTagsCB(OSMWay way, long allowed, long relationFlags) {

			// Store original flag encoder speed as well if needed
			if (processedWayListener != null) {
				long flags = noSpeedRegionsFlagEncoder.handleWayTags(way, allowed, relationFlags);
				double speed = noSpeedRegionsFlagEncoder.getSpeed(flags);
				way.setTag(key2StoreOriginalSpeedInWay, speed);
			}

			// Set the speed region tag and rules tag.
			// This should probably be done in OSMReader instead when we integrate into latest Graphhopper core.
			GHPoint estmCentre = way.getTag("estimated_center", null);
			if (estmCentre != null && lookup != null) {
				Point point = GeomUtils.newGeomFactory().createPoint(new Coordinate(estmCentre.lon, estmCentre.lat));
				String regionId = lookup.findRegionType(point);
				way.setTag(SpeedRegionConsts.REGION_ID_TAG_IN_OSM_WAY, regionId);

				if (regionId != null && rules != null) {
					SpeedRule rule = rules.getSpeedRule(regionId);
					if (rule == null) {
						// TODO Should this be fatal? If someone misspelled a regionid you wouldn't want a silent fail.
						// However it may be valid to have regions without a defined rule for certain encoders?
						throw new RuntimeException(
								"Cannot find speed rule for region with id " + regionId + " and encoder " + EncodingManager.CAR);
					}
					way.setTag(key2StoreSpeedRule, rule);
				}
			}

		}

		private double getSpeedCB(OSMWay way, double typeBasedSpeed, double maxSpeed, double speedFactor) {
			String highwayValue = way.getTag("highway");
			boolean useMaxSpeed = maxSpeed >= 0;
			double speed = typeBasedSpeed;
			if (useMaxSpeed) {
				speed = maxSpeed * 0.9;
				//speed = maxSpeed;
			}

			// apply the rule
			SpeedRule rule = way.getTag(key2StoreSpeedRule, (SpeedRule) null);
			if (rule != null) {

				double regionSpeed = rule.applyRule(highwayValue, speed, useMaxSpeed);

				// AbstractFlagEncoder.speedFactor should be the minimum speed which can be stored by the encoder.
				// If we have a non-zero speed which is smaller than the minimum, set to the minimum instead
				// to ensure we don't accidently disabled roads (i.e. set to zero speed) that we don't want to...
				if (regionSpeed > 0 && regionSpeed < speedFactor) {
					regionSpeed = speedFactor;
				}

				return regionSpeed;
			} else {
				return speed;
			}
		}

		void applyWayTagsCB(OSMWay way, EdgeIteratorState edge) {
			if (processedWayListener != null) {
				// get geometry
				PointList points = edge.fetchWayGeometry(3);
				int n = points.getSize();
				Coordinate[] coords = new Coordinate[n];
				for (int i = 0; i < n; i++) {
					coords[i] = new Coordinate(points.getLon(i), points.getLat(i));
				}
				LineString ls = new GeometryFactory().createLineString(coords);

				// get other values needed for callback
				String highwayValue = way.getTag("highway");
				double originalSpeed = (Double) way.getTag(key2StoreOriginalSpeedInWay, Double.NaN);
				if (Double.isNaN(originalSpeed)) {
					throw new RuntimeException("Original speed not recorded on way");
				}
				double distanceMetres = edge.getDistance();

				double finalSpeed = speedRegionsFlagEncoder.getSpeed(edge.getFlags());

				String regionId = way.getTag(SpeedRegionConsts.REGION_ID_TAG_IN_OSM_WAY);
				SpeedRule rule = way.getTag(key2StoreSpeedRule, (SpeedRule) null);
				// do callback
				processedWayListener.onProcessedWay(encoderType, ls, regionId, highwayValue, distanceMetres, rule, originalSpeed,
						finalSpeed);
			}
		}

		protected double applyMaxSpeedCB(OSMWay way, double speed, boolean force) {
			// max speed already handled in getSpeed...
			return speed;
		}

	}

	private static interface HasDefaultSpeeds {
		Map<String, Integer> getDefaultSpeeds();
	}

	private static class MyCarFlagEncoder extends CarFlagEncoder implements HasDefaultSpeeds {
		final FlagEncoderHelper helper;

		MyCarFlagEncoder(PMap config, final SpeedRegionLookup lookup, ProcessedWayListener cb) {
			super(config);
			helper = new FlagEncoderHelper(lookup, EncodingManager.CAR, this, new CarFlagEncoder(new PMap()), cb);
		}

		@Override
		public Map<String, Integer> getDefaultSpeeds() {
			return this.defaultSpeedMap;
		}

		@Override
		public long handleWayTags(OSMWay way, long allowed, long relationFlags) {
			helper.handleWayTagsCB(way, allowed, relationFlags);
			return super.handleWayTags(way, allowed, relationFlags);
		}

		@Override
		protected double getSpeed(OSMWay way) {
			return helper.getSpeedCB(way, super.getSpeed(way), getMaxSpeed(way), speedFactor);
		}

		@Override
		public void applyWayTags(OSMWay way, EdgeIteratorState edge) {
			super.applyWayTags(way, edge);
			helper.applyWayTagsCB(way, edge);
		}

		@Override
		protected double applyMaxSpeed(OSMWay way, double speed, boolean force) {
			return helper.applyMaxSpeedCB(way, speed, force);
		}

	}

	private static class MyMotorcycleFlagEncoder extends MotorcycleFlagEncoder implements HasDefaultSpeeds {
		final FlagEncoderHelper helper;

		MyMotorcycleFlagEncoder(PMap config, final SpeedRegionLookup lookup, ProcessedWayListener cb) {
			super(config);
			helper = new FlagEncoderHelper(lookup, EncodingManager.MOTORCYCLE, this, new MotorcycleFlagEncoder(new PMap()), cb);
		}

		@Override
		public Map<String, Integer> getDefaultSpeeds() {
			return this.defaultSpeedMap;
		}

		@Override
		public long handleWayTags(OSMWay way, long allowed, long relationFlags) {
			helper.handleWayTagsCB(way, allowed, relationFlags);
			return super.handleWayTags(way, allowed, relationFlags);
		}

		@Override
		protected double getSpeed(OSMWay way) {
			return helper.getSpeedCB(way, super.getSpeed(way), getMaxSpeed(way), speedFactor);
		}

		@Override
		public void applyWayTags(OSMWay way, EdgeIteratorState edge) {
			super.applyWayTags(way, edge);
			helper.applyWayTagsCB(way, edge);
		}

		@Override
		protected double applyMaxSpeed(OSMWay way, double speed, boolean force) {
			return helper.applyMaxSpeedCB(way, speed, force);

		}

	}

}