package com.opendoorlogistics.speedregions.commandline;

import java.util.ArrayList;

public class ExampleCommands {

	public static void main(String[] args) {
		buildCompiled();
	}

	public static void buildCompiled(){
		ArrayList<String> commands = new ArrayList<>();
		
		commands.add("-l");
		commands.add("C:\\Users\\Phil\\Dropbox\\Business\\Dev\\GithubHPLaptop\\speedregions\\com.opendoorlogistics.speedregions.core\\examples\\GB-with-buffers.geojson");
	
		// 100 m accuracy limit
		commands.add("-limit");
		commands.add("100");
		
		commands.add("-odl");
		commands.add("c:\\temp\\odlregionstest.xlsx");

		commands.add("-et");
		commands.add("c:\\temp\\compiledtree.json");

		commands.add("-er");
		commands.add("c:\\temp\\compiledrules.json");
		
		new CommandLine().processCommandLine(commands.toArray(new String[commands.size()]),new State());

		
	}
	
	public static void createUKBuffers(){
		
		ArrayList<String> commands = new ArrayList<>();
		
		commands.add("-l");
		commands.add("C:\\Users\\Phil\\Dropbox\\Business\\Dev\\GithubHPLaptop\\speedregions\\com.opendoorlogistics.speedregions.core\\examples\\GB-geoJSON.json");
		
		// 100 m accuracy limit
		commands.add("-limit");
		commands.add("100");
		
		// 1 km buffer round congestion
		commands.add("-buffer");
		commands.add("LondonCongestionZoneApprox");
		commands.add("27700"); 
		commands.add("1000");
		commands.add("Congestion+1KM");

		// 2 km buffer round Inner London
		commands.add("-buffer");
		commands.add("InnerLondon");
		commands.add("27700"); 
		commands.add("2000");
		commands.add("InnerLondon+2KM");

		// 5 km buffer round Outer London
		commands.add("-buffer");
		commands.add("OuterLondon");
		commands.add("27700"); 
		commands.add("5000");
		commands.add("OuterLondon+5KM");
		
		// 5 km buffer round Birmingham
		commands.add("-buffer");
		commands.add("Birmingham");
		commands.add("27700"); 
		commands.add("5000");
		commands.add("Birmingham+5KM");
		
		//commands.add("-odl");
	//	commands.add("c:\\temp\\odlregionstest.xlsx");

		commands.add("-s");
		commands.add("C:\\Users\\Phil\\Dropbox\\Business\\Dev\\GithubHPLaptop\\speedregions\\com.opendoorlogistics.speedregions.core\\examples\\GB-geoJSONv2.json");
		
		new CommandLine().processCommandLine(commands.toArray(new String[commands.size()]),new State());
	}

//	private static void maltaTest(ArrayList<String> commands) {
//		commands.add("-limit");
//		commands.add("250");
//		
//		commands.add("-s");
//		commands.add("c:\\temp\\uncompiled.json");
//		
//		commands.add("-buffer");
//		commands.add("valleta");
//		commands.add("3004"); // Italian projection
//		commands.add("2000");
//		commands.add("Valleta2KBuffer");
//		
//		commands.add("-odl");
//		commands.add("c:\\temp\\odlregionstest.xlsx");
//	}

}
