package com.opendoorlogistics.speedregions.graphhopper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.util.shapes.GHPoint;
import com.opendoorlogistics.speedregions.TextUtils;

public class DebugExportSpeeds {
	private HashSet<GHPoint> exported = new HashSet<>();
	private BufferedWriter out;
	private DecimalFormat speedformat = new DecimalFormat("#.#");
	private DecimalFormat llformat = new DecimalFormat("#.#######");
	
	public DebugExportSpeeds(File file) throws Exception{
		out = new BufferedWriter(new FileWriter(file));
	}

	public void handledWayTag(AbstractFlagEncoder encoder,OSMWay way, long val) {
		GHPoint estmCentre = way.getTag("estimated_center", null);
		if(estmCentre==null){
			return;
		}
		if(exported.contains(estmCentre)){
			return;
		}
		
		exported.add(estmCentre);
		double speed = encoder.getSpeed(val);
		try {
			out.write(llformat.format(estmCentre.lat) + "\t" + llformat.format(estmCentre.lon) + "\t" + speedformat.format(speed));
		} catch (IOException e) {
			throw TextUtils.asUncheckedException(e);
		}
	}
	
	public void close() throws IOException{
		out.close();
	}
}
