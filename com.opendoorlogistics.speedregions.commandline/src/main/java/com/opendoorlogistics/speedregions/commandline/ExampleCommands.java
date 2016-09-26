package com.opendoorlogistics.speedregions.commandline;

import java.util.ArrayList;

public class ExampleCommands {

	public static void main(String[] args) {
		ArrayList<String> commands = new ArrayList<>();
		commands.add("-malta");
		
		commands.add("-limit");
		commands.add("250");
		
		commands.add("-s");
		commands.add("c:\\temp\\uncompiled.json");
		
		commands.add("-buffer");
		commands.add("valleta");
		commands.add("3004"); // Italian projection
		commands.add("2000");
		commands.add("Valleta2KBuffer");
		
		commands.add("-odl");
		commands.add("c:\\temp\\odlregionstest.xlsx");

		
		new CommandLine().processCommandLine(commands.toArray(new String[commands.size()]),new State());
	}

}
