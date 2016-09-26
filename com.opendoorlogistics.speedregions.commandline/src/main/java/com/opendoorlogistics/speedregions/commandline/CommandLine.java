package com.opendoorlogistics.speedregions.commandline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.opendoorlogistics.speedregions.processor.RegionProcessorUtils;

public class CommandLine {
	private static final Logger LOGGER = Logger.getLogger(CommandLine.class.getName());
	private final Map<String,AbstractCommand> commands;
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine();
		State state = new State();
		cl.processCommandLine(args,state);
	}


	private CommandLine(){
		commands= createCommands();
	}

	private void processCommandLine(String[] args ,State state) {
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
			command = RegionProcessorUtils.stdString(arg);
		}
		return command;
	}

	private Map<String,AbstractCommand> createCommands() {

		ArrayList<AbstractCommand> tmp = new ArrayList<AbstractCommand>();

		// add buffer
		tmp.add(new AbstractCommand("Create buffer around a region" , "buffer") {
			
			@Override
			public void execute(String[] args, State state) {
				// region id, projection, bufferkm
				if(args.length!=3){
					throw new RuntimeException("Buffer needs 3 commands");
				}
			}
		});

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
				ret.put(RegionProcessorUtils.stdString(kw), c);

			}
		}

		return ret;
	}


	private final static Pattern SPLIT_COMMAND_PATTERN = Pattern.compile("\"(\\\"|[^\"])*?\"|[^ ]+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	public static String[] splitLineIntoTokens(String line) {

		List<String> found = new ArrayList<String>();
		if (line != null && line.length() > 0) {
			line = line.trim();
			Matcher result = SPLIT_COMMAND_PATTERN.matcher(line);
			while (result.find()) {
				found.add(result.group());
			}

		}
		return found.toArray(new String[found.size()]);
	}
}
