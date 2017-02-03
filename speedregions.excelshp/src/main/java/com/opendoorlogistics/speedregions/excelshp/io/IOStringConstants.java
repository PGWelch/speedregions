package com.opendoorlogistics.speedregions.excelshp.io;

import com.opendoorlogistics.speedregions.utils.TextUtils;

public class IOStringConstants {
	public static final String [] ROAD_TYPES = new String[]{
			"motorway",
			"motorway_link",
			"motorroad",
			"trunk",
			"trunk_link",
			"primary",
			"primary_link",
			"secondary",
			"secondary_link",
			"tertiary",
			"tertiary_link",
			"unclassified",
			"residential",
			"service",			
			"living_street",
			"road",
			"track",};
	
	public static final String SPEED_PROFILES_TABLENAME =TextUtils.stdString("SpeedProfiles");
	public static final String BRICKS_TABLENAME =TextUtils.stdString("Bricks");

	public static final String BRFLD_BRICK_ID = "id";
	public static final String BRFLD_SPEED_PROFILE_ID = "speed_profile_id";

	public static final String SPFLD_ID = "id";
	public static final String SPFLD_PARENT_ID = "parent_id";
	public static final String SPFLD_MULTIPLIER = "multiplier";
	public static final String SPFLD_SPEED_UNIT = "speed_unit";
	public static final String SPFLD_SPEED_PREFIX = "speed_";

//	private String id;
//	private String parentId;
//	private Map<String, Float> speedsByRoadType = new TreeMap<String, Float>();
//	private double multiplier = 1;
//	private SpeedUnit speedUnit = SpeedUnit.DEFAULT;
//	private MatchRule matchRule = new MatchRule();
//		private List<String> regionTypes = new ArrayList<String>();
}
