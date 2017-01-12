/*
 * Copyright 2016 Open Door Logistics Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.opendoorlogistics.speedregions.commandline;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.geojson.FeatureCollection;

import com.opendoorlogistics.speedregions.Examples;
import com.opendoorlogistics.speedregions.SpeedRulesProcesser;
import com.opendoorlogistics.speedregions.beans.files.CompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.beans.files.UncompiledSpeedRulesFile;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class CommandLine {
	private static final Logger LOGGER = Logger.getLogger(CommandLine.class.getName());
	private final Map<String,AbstractCommand> commands;
	
	public static void main(String[] args) {
		new CommandLine().processCommandLine(args,new State());
	}


	CommandLine(){
		commands= createCommands();
	}

	public void processCommandLine(String[] args ,State state) {
		int i = 0;
		while (i < args.length) {
			String arg = args[i];

			String command = getCommandToken(arg);
			if (command != null) {

				// get all other arguments until next command
				ArrayList<String> otherArgs = getCommandArguments(args, i);

				// mark arguments as used
				i += 1 + otherArgs.size();

				// find command
				AbstractCommand commandExecutor = commands.get(command);
				if (commandExecutor == null) {
					throw new RuntimeException("Unknown command " + command);
				}

				// Run it
				commandExecutor.execute(otherArgs.toArray(new String[otherArgs.size()]), state);
	
			} else {
				throw new RuntimeException("Error parsing command line input. Expected - before argument " + (i + 1));
			}

		}

	}

	/**
	 * Get the arguments following a command
	 * 
	 * @param args
	 * @param commandIndex
	 * @return
	 */
	private static ArrayList<String> getCommandArguments(String[] args, int commandIndex) {
		ArrayList<String> otherArgs = new ArrayList<String>();
		for (int j = commandIndex + 1; j < args.length; j++) {
			String other = args[j];
			if (getCommandToken(other) == null) {

				// remove start and end speech marks
				if (other.length() > 0 && other.startsWith("\"")) {
					other = other.substring(1, other.length());
				}
				if (other.length() > 0 && other.endsWith("\"")) {
					other = other.substring(0, other.length() - 1);
				}
				otherArgs.add(other);
			} else {
				break;
			}
		}
		return otherArgs;
	}

	private static String getCommandToken(String arg) {
		// see if starts with argument
		int nbConsecutiveMinus = 0;
		while (arg.length() > 0) {
			boolean found = false;
			for (String minus : new String[]{"-", "\u2013", "\u2014"}) {
				if (arg.startsWith(minus)) {
					nbConsecutiveMinus++;
					arg = arg.substring(1, arg.length());
					found = true;
					break;
				}
			}

			if (!found) {
				break;
			}
		}
		String command = null;
		if (nbConsecutiveMinus > 0) {
			command = TextUtils.stdString(arg);
		}
		return command;
	}

	private Map<String,AbstractCommand> createCommands() {

		ArrayList<AbstractCommand> tmp = new ArrayList<AbstractCommand>();

		tmp.add(new AbstractCommand("Load a geoJSON text file containing a feature collection. Replaces existing feature collection. Usage -l filename.", "l") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length!=1){
					throw new RuntimeException("Expected one argument for load command");
				}
				state.featureCollection = TextUtils.fromJSON(new File(args[0]),FeatureCollection.class);
			}
		});

//		tmp.add(new AbstractCommand("Import a raw rules file. Contents are added to existing file. Usage -i filename.", "i") {
//			
//			@Override
//			public void execute(String[] args, State state) {
//				if(args.length!=1){
//					throw new RuntimeException("Expected one argument for load command");
//				}
//				UncompiledSpeedRulesFile newFile= TextUtils.fromJSON(new File(args[0]),UncompiledSpeedRulesFile.class);
//				state.compiled = null;
//				Utils.addToFile(state.featureCollections, newFile);
//			}
//		});
		
		tmp.add(new AbstractCommand("Replace existing features with example Malta features. Usage -malta.", "malta") {
			
			@Override
			public void execute(String[] args, State state) {
				state.compiled = null;
				state.featureCollection = Examples.createMaltaSingleFeatureCollection();
			}
		});
		
		tmp.add(new AbstractCommand("Save the geoJSON features collection. Usage -s filename.", "s") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length!=1){
					throw new RuntimeException("Expected one argument for save command");
				}
				TextUtils.toJSONFile(state.featureCollection, new File(args[0]));
			}
		});
		
		tmp.add(new AbstractCommand("Compile features collection into the spatial tree. Usage -c" , "c") {
			
			@Override
			public void execute(String[] args, State state) {
				state.compile();
			}
		});

		tmp.add(new AbstractCommand("Export the compiled tree as a spatial tree text file. Trigger compile if one not done before. Usage -et filename" , "et") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length==0){
					throw new RuntimeException("No filename provided");
				}
				state.compileIfNull();
				TextUtils.toJSONFile(state.compiled, new File(args[0]));
				
			}
		});

		tmp.add(new AbstractCommand("Export the compiled tree as an compiled rules test file. Trigger compile if one not done before. Usage -er filename" , "er") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length==0){
					throw new RuntimeException("No filename provided");
				}
				state.compileIfNull();
				CompiledSpeedRulesFile compiledSpeedRulesFile = new CompiledSpeedRulesFile();
				compiledSpeedRulesFile.setTree(state.compiled);
				TextUtils.toJSONFile(compiledSpeedRulesFile, new File(args[0]));
				
			}
		});
		tmp.add(new AbstractCommand("Export an Excel file for visualisation in ODL Studio. Trigger compile if one not done before. Usage -odl filename" , "odl") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length==0){
					throw new RuntimeException("No filename provided");
				}
				state.compileIfNull();
				TextUtils.toJSONFile(state.compiled, new File(args[0]));
				new ExcelWriter().exportState(state, new File(args[0]));
			}
		});
		
		tmp.add(new AbstractCommand("Set min cell length limit (metres). Usage -limit metres", "limit") {
			
			@Override
			public void execute(String[] args, State state) {
				if(args.length==0){
					throw new RuntimeException("No limit provided");
				}
				state.minCellLength = Double.parseDouble(args[0]);
			}
		});

		
		// add buffer
		tmp.add(new BufferCommand());

		// add help
		tmp.add(new AbstractCommand("List all commands", "help", "h") {

			@Override
			public void execute(String[] args, State state) {
				StringBuilder out = new StringBuilder();
				out.append("ODL Studio command line interface. The following commands are available:" + System.lineSeparator());
				for (Map.Entry<String, AbstractCommand> entry : commands.entrySet()) {
					out.append("-" + entry.getKey());
					out.append(System.lineSeparator());
					String lines[] = entry.getValue().getDescription().split("\\r?\\n");
					for(String line : lines){
						out.append("    " + line);
						out.append(System.lineSeparator());
					}
					out.append(System.lineSeparator());
				}
				System.out.println(out.toString());
			}
		});


		
		// build return map from the list
		Map<String,AbstractCommand> ret =new TreeMap<>();		
		for (AbstractCommand c : tmp) {
			for (String kw : c.getKeywords()) {
				ret.put(TextUtils.stdString(kw), c);

			}
		}

		return ret;
	}



}
