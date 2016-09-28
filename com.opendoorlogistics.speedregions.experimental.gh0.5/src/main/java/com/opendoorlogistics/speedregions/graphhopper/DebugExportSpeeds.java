package com.opendoorlogistics.speedregions.graphhopper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Random;

import org.apache.commons.io.FilenameUtils;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.util.shapes.GHPoint;
import com.opendoorlogistics.speedregions.TextUtils;

public class DebugExportSpeeds {
	private Random random = new Random(123);
	private HashSet<GHPoint> exported = new HashSet<>();
	private BufferedWriter out;
	private BufferedWriter outSubset;
	private DecimalFormat speedformat = new DecimalFormat("#.#");
	private DecimalFormat llformat = new DecimalFormat("#.#######");
	
	public DebugExportSpeeds(File file) throws Exception{
		out = new BufferedWriter(new FileWriter(file));
		String subset=FilenameUtils.removeExtension(file.getAbsolutePath()) + ".subset." + FilenameUtils.getExtension(file.getAbsolutePath());
		outSubset = new BufferedWriter(new FileWriter(subset));
		
		String header = "Latitude\tLongitude\tKMH" + System.lineSeparator();
		out.write(header);
		outSubset.write(header);
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
			String s = llformat.format(estmCentre.lat) + "\t" + llformat.format(estmCentre.lon) + "\t" + speedformat.format(speed) + System.lineSeparator();
			out.write(s);
			if(random.nextInt(10)==0){
				outSubset.write(s);
			}
		} catch (IOException e) {
			throw TextUtils.asUncheckedException(e);
		}
	}
	
	public void close() {
		try {
			out.flush();
			out.close();
			outSubset.flush();
			outSubset.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
