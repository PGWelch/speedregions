package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants;
import com.opendoorlogistics.speedregions.excelshp.io.RawStringTable;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.vividsolutions.jts.geom.LineString;

public class DetailedReportBuilder  {
	private static String UNIDENTIFIED_BRICK_ID = "#Unidentified";
	private static String NO_RULE = "#NoRule";
	private static String UNIDENTIFIED_HIGHWAY_TYPE = "#Undefined";
	private static final Logger LOGGER = Logger.getLogger(DetailedReportBuilder.class.getName());
	private final SpeedUnit unit;
	private final TreeMap<String, Double> defaultSpeedsByHighwayType ;
	private final boolean useBrickId;
	private TreeMap<String, TreeMap<String, Stats>> statsByBrick = new TreeMap<>();
	private TreeMap<String, TreeMap<String, Stats>> statsByRule = new TreeMap<>();

	
	public DetailedReportBuilder(SpeedUnit unit, TreeMap<String, Double> defaultSpeedsByHighwayType, boolean useBrickId) {
		this.unit = unit;
		this.defaultSpeedsByHighwayType = defaultSpeedsByHighwayType;
		this.useBrickId = useBrickId;
	}

	private static class WeightedDoubleMeanCalculator{
		private double sumValue;
		private double sumValueSqd;
		private double sumValueTimesWeight;
		private double sumWeight;
		private long n;
	
		String format(DecimalFormat df, SpeedUnit convertFromKmToUnit){
			return format(df, 1,convertFromKmToUnit);
		}
		
		String format(DecimalFormat df, double multiplier, SpeedUnit convertFromKmToUnit){
			if(sumWeight==0){
				return AppConstants.NA;
			}
			double value = multiplier * sumValueTimesWeight/sumWeight;
			if(convertFromKmToUnit!=null){
				value = convertFromKmToUnit.convertKMToMe(value);
			}
			return df.format(value);
		}
		
		void add(double weight, double value){
			if(weight>0){
				sumValue+=value;
				sumValueSqd += value*value;
				sumWeight+=weight;
				sumValueTimesWeight+=weight*value;	
				n++;
			}
			else if (sumWeight<0){
				throw new IllegalArgumentException("Weight cannot be negative");
			}
		}

		void add(WeightedDoubleMeanCalculator wdmc){
			this.sumValueTimesWeight += wdmc.sumValueTimesWeight;
			this.sumWeight += wdmc.sumWeight;
			this.sumValue += wdmc.sumValue;
			this.sumValueSqd += wdmc.sumValueSqd;
			this.n += wdmc.n;
		}
		
		double unweightedStdDev(){
			if(n<2){
				return 0;
			}
			double val= sumValueSqd - sumValue*sumValue/n;
			val /= (n-1);
			val = Math.sqrt(val);
			return val;
		}
		
		@Override
		public String toString(){
			return format(DP_FORMAT2,null);
		}
	}
	private static class Stats {
		long edges;
		double sumLengthMetres;
		WeightedDoubleMeanCalculator originalSpeedWeightedByLength = new WeightedDoubleMeanCalculator();
		WeightedDoubleMeanCalculator newSpeedWeightedByLength = new WeightedDoubleMeanCalculator();
		WeightedDoubleMeanCalculator percentageChangeByLength =new WeightedDoubleMeanCalculator(); 
		// double minOriginalSpeedKPH = Double.MAX_VALUE;
		// double maxOriginalSpeedKPH =0;
		double minNewSpeedKPH = Double.MAX_VALUE;
		double maxNewSpeedKPH = 0;

		Stats() {

		}

		void addEdge(double lengthMetres, double originalSpeedKPH, double newSpeedKPH) {
			edges++;
			this.sumLengthMetres += lengthMetres;
			this.originalSpeedWeightedByLength.add(lengthMetres, originalSpeedKPH);
			this.newSpeedWeightedByLength.add(lengthMetres, newSpeedKPH);
			this.minNewSpeedKPH = Math.min(newSpeedKPH, minNewSpeedKPH);
			this.maxNewSpeedKPH = Math.max(newSpeedKPH, maxNewSpeedKPH);
			if(originalSpeedKPH>0){
				percentageChangeByLength.add(lengthMetres,ExcelShp2GeoJSONConverter.percentageChange(originalSpeedKPH, newSpeedKPH));
			}
		}

		void addStats(Stats other) {
			this.edges += other.edges;
			this.sumLengthMetres += other.sumLengthMetres;
			this.originalSpeedWeightedByLength.add(other.originalSpeedWeightedByLength);
			this.newSpeedWeightedByLength.add(other.newSpeedWeightedByLength);
			this.percentageChangeByLength.add(other.percentageChangeByLength);
			this.minNewSpeedKPH = Math.min(other.minNewSpeedKPH, minNewSpeedKPH);
			this.maxNewSpeedKPH = Math.max(other.maxNewSpeedKPH, maxNewSpeedKPH);

		}

	}


	public void onProcessedWay(LineString lineString, String brickId, String highwayType, double lengthMetres, SpeedRule rule,
			double originalSpeedKPH, double speedRegionsSpeedKPH) {
		if (TextUtils.stdString(highwayType).length() == 0) {
			highwayType = UNIDENTIFIED_HIGHWAY_TYPE;
		}

		if (brickId == null) {
			brickId = UNIDENTIFIED_BRICK_ID;
		}

		String ruleId = rule != null ? rule.getId() : null;
		if (TextUtils.stdString(ruleId).length() == 0) {
			ruleId = NO_RULE;
		}

		// Filter dodgy linestrings
		int nc = lineString.getNumPoints();
		if (nc < 2) {
			return;
		}

		// Create stats object for this way
		Stats stats = new Stats();
		stats.addEdge(lengthMetres, originalSpeedKPH, speedRegionsSpeedKPH);

		// Add to brick stats
		if(useBrickId){
			addStatsToMap(brickId, highwayType, stats, statsByBrick);			
		}

		// And region stats
		addStatsToMap(ruleId, highwayType, stats, statsByRule);
	}

	private void addStatsToMap(String collationKey, String highwayType, Stats stats, TreeMap<String, TreeMap<String, Stats>> map) {
		collationKey = TextUtils.stdString(collationKey);
		TreeMap<String, Stats> byCollationKey = map.get(collationKey);
		if (byCollationKey == null) {
			byCollationKey = new TreeMap<>();
			map.put(collationKey, byCollationKey);
		}

		highwayType = TextUtils.stdString(highwayType);
		Stats prexisting = byCollationKey.get(highwayType);
		if (prexisting == null) {
			prexisting = new Stats();
			byCollationKey.put(highwayType, prexisting);
		}
		prexisting.addStats(stats);
	}


	public List<RawStringTable> buildReports(RawStringTable [] getDefault) {
		ArrayList<RawStringTable> ret = new ArrayList<>();
		if(useBrickId){
			ret.addAll(buildStatsTable("StatsByBrick", "BrickId", this.statsByBrick,null));			
		}
		ret.addAll(buildStatsTable("StatsByRule", "RuleId", this.statsByRule,getDefault));
		// RegionId, BrickId,
		return ret;
	}

	private static final String REPLACE_UNIT = "#UNIT#";
	
	private enum StatType {
		MEAN_SPEED("MeanSpeed", "mean speed ("+REPLACE_UNIT + "/hr)"),
		MIN_SPEED("MinSpeed", "min speed (" +REPLACE_UNIT+ "/hr)"), 
		MAX_SPEED("MaxSpeed", "max speed (" + REPLACE_UNIT + "/hr)"),
		STD_DEV_SPEED("StdDevSpeed", "std. dev speed (" + REPLACE_UNIT + "/hr)"),
		PERCENT_DIFF("%Diff", "mean percentage speed diff from default"),
		TOTAL_LENGTH("Total" + REPLACE_UNIT, "total " + REPLACE_UNIT),PC_TOTAL_LENGTH("%TotalLength", "% of total length within brick/rule"),
		;

		private StatType(String fieldname, String description) {
			this.fieldname = fieldname;
			this.description = description;
		}

		private final String fieldname;
		private final String description;
	}

	private static final DecimalFormat DP_FORMAT1 = new DecimalFormat("0.0");
	private static final DecimalFormat DP_FORMAT2 = new DecimalFormat("0.00");

	private List<RawStringTable> buildStatsTable(String name, String collationKeyName, TreeMap<String, TreeMap<String, Stats>> stats,RawStringTable [] getDefaultReport) {
		// get all highway types
		TreeSet<String> highwayTypes = new TreeSet<>();
		for (TreeMap<String, Stats> map : stats.values()) {
			highwayTypes.addAll(map.keySet());
		}
		ArrayList<RawStringTable> ret = new ArrayList<>();

		// Get order of all highway types in the array
		final TreeMap<String, Integer> highwayTypeToOrderIndex = new TreeMap<>();
		for(int i =0 ; i< IOStringConstants.ROAD_TYPES.length ; i++){
			highwayTypeToOrderIndex.put(TextUtils.stdString(IOStringConstants.ROAD_TYPES[i]), i);
		}
		
		// Sort all detected highway types by order in the predefined array
		ArrayList<String> sortedHighwayTypes = new ArrayList<>(highwayTypes);
		Collections.sort(sortedHighwayTypes, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return Integer.compare(findIndx(o1), findIndx(o2));
			}
			
			private int findIndx(String s){
				Integer indx = highwayTypeToOrderIndex.get(TextUtils.stdString(s));
				if(indx!=null){
					return indx;
				}
				return Integer.MAX_VALUE;
			}
		});
		
		for (StatType statType : StatType.values()) {
			RawStringTable table = new RawStringTable(name + "-" + statType.fieldname.replaceAll(REPLACE_UNIT, unit.getShorthand()));
			
			// create table
			ret.add(table);
			ArrayList<String> header = new ArrayList<>();
			table.add(header);
			header.add(collationKeyName);
			String distUnit = TextUtils.capitaliseFirstLetter(unit.getShorthand());
			header.add(distUnit);
			header.add("%TotalLength");
			header.add("Mean" + distUnit.substring(0, 1) + "PH");
			for(String ht : sortedHighwayTypes){
				header.add(ht);
			}

			// set description
			table.setDescription("For each " + collationKeyName +", total " +unit.getShorthand()+", % of total road length, mean " +
			unit.getShorthand()+ "/hr and " +statType.description.replaceAll(REPLACE_UNIT, unit.getShorthand()) +  " for each highway type.");
			// Get total km across everywhere...
			double totalKmEverywhere = 0;
			for (TreeMap<String, Stats> map : stats.values()) {
				for (Stats s : map.values()) {
					totalKmEverywhere += 0.001 * s.sumLengthMetres;
				}
			}

			if (totalKmEverywhere == 0) {
				// something bad happened...
				return ret;
			}

			// loop over different entries
			ArrayList<ArrayList<String> > rowsToSort = new ArrayList<>();
			for (Map.Entry<String, TreeMap<String, Stats>> entry : stats.entrySet()) {
				ArrayList<String> row = new ArrayList<>();
				rowsToSort.add(row);

				// key
				String key = entry.getKey();
				row.add(key);

				// total km
				double totalKM = 0;
				WeightedDoubleMeanCalculator meanSpeedOverAll=new WeightedDoubleMeanCalculator();
				for (Stats s : entry.getValue().values()) {
					totalKM += 0.001 * s.sumLengthMetres;
					meanSpeedOverAll.add(s.newSpeedWeightedByLength);
				}
				row.add(Long.toString((long)Math.round(unit.convertKMToMe(totalKM))));

				// percentage of all km
				row.add(DP_FORMAT2.format(100*totalKM / totalKmEverywhere));

				// mean speed (averaged by metres)
				row.add(meanSpeedOverAll.format(DP_FORMAT1,unit));
				
				// loop over all highway types...
				for(String ht : sortedHighwayTypes){
					String val=AppConstants.NA;
					Stats s = entry.getValue().get(ht);
					double km = s!=null?0.001*s.sumLengthMetres:0;
					
					switch(statType){
					case TOTAL_LENGTH:
						val = DP_FORMAT2.format(unit.convertKMToMe(km));
						break;
						
					case PC_TOTAL_LENGTH:
						if(totalKM>0){
							val = DP_FORMAT2.format(100*km/totalKM);
						}
						break;
						
					case MEAN_SPEED:
						if(s!=null &&km>0){
							val = s.newSpeedWeightedByLength.format(DP_FORMAT2,unit);
						}
						break;
						
					case MIN_SPEED:
						if(s!=null && s.minNewSpeedKPH !=Double.MAX_VALUE){
							val = DP_FORMAT2.format(unit.convertKMToMe(s.minNewSpeedKPH));
						}
						break;
						
					case MAX_SPEED:
						if(s!=null &&s.minNewSpeedKPH !=0){
							val = DP_FORMAT2.format(unit.convertKMToMe(s.maxNewSpeedKPH));
						}
						break;
						
					case PERCENT_DIFF:
						if(s!=null){
							val = s.percentageChangeByLength.format(DP_FORMAT2,null);							
						}
						break;

					case STD_DEV_SPEED:
						if(s!=null){
							val = DP_FORMAT2.format(s.newSpeedWeightedByLength.unweightedStdDev());
						}
					}		
					
					row.add(val);
				}

			}

			// sort rows by least km first then add to table
			Collections.sort(rowsToSort, new Comparator<ArrayList<String>>() {

				@Override
				public int compare(ArrayList<String> o1, ArrayList<String> o2) {
					// This is inefficient but probably doesn't matter
					return Double.compare(Double.parseDouble(o1.get(1)), Double.parseDouble(o2.get(1)));
				}
			});
			for(ArrayList<String> row:rowsToSort){
				table.add(row);
			}
			
			// write default speeds row....
			if(statType==StatType.MEAN_SPEED && defaultSpeedsByHighwayType!=null){
				ArrayList<String> row = new ArrayList<>();
				table.add(row);
				row.add(AppConstants.DEFAULT_SPEEDS_DISPLAY_NAME);
				row.add(AppConstants.NA);
				row.add(AppConstants.NA);				
				row.add(AppConstants.NA);
				
				for(String ht : sortedHighwayTypes){
					Double val = defaultSpeedsByHighwayType.get(ht);
					row.add(val==null?AppConstants.NA : DP_FORMAT1.format(val));
				}
			}
			
			// flag this as the default report if needed
			if(statType == StatType.MEAN_SPEED && getDefaultReport!=null){
				getDefaultReport[0] = table;
			}
		}
		return ret;
	}
	
	public static void main(String [] args){
		SwingUtils.runOnEDT(new Runnable() {
			
			@Override
			public void run() {
				RawStringTable rst = new RawStringTable("blah");
				ArrayList<String> row = new ArrayList<>();
				row.add("hello");
				row.add("world");
				rst.add(row);
				rst.add(row);
				rst.add(row);
				
				ArrayList<RawStringTable> list = new ArrayList<>();
				list.add(rst);
				showReportsOnEDT(list, null);
			}
		});
	}
	
	public static void showReportsOnEDT(List<RawStringTable> reports, RawStringTable defaultReport){
		class Helper{
			RawStringTable rst;
			
			@Override
			public String toString(){
				return rst.getDescription()!=null?rst.getDescription():rst.getName();
			}
		}
		
		Helper [] items = new Helper[reports.size()];
		for(int i =0 ;i<reports.size();i++){
			Helper helper = new Helper();
			helper.rst = reports.get(i);
			items[i]=helper;
		}
		
		class MyPanel extends JPanel{
			RawStringTable selected ;
			JScrollPane table;
			
			void set(RawStringTable rst){
				if(table!=null){
					remove(table);
					selected = null;
				}
						
				if(rst!=null){
					table = RawStringTable.toJTable(rst,null);
					selected = rst;
					add(table,BorderLayout.CENTER);					
				}
			
				//invalidate();
				revalidate();
				repaint();

			}
		}
		final MyPanel panel = new MyPanel();
		panel.setLayout(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));		
		topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel("<html><b>Road network graph has been built.</b><br><br>"
				+ "Various tables of statistics are available below detailing the exact speeds used per rule or brick, and other stats."
				+ " These are broken down by highway type. Additional factors in the map data - e.g. maximum speed attributes - cause"
				+ " differences between the speeds defined in the Excel (and shown before the graph build), and the exact speeds shown below.</html>");
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		// put label in its own panel so it aligns to the left
		JPanel labelPanel =new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		labelPanel.add(label);
		topPanel.add(labelPanel);
		
		topPanel.add(Box.createRigidArea(new Dimension(1, 16)));
		final JComboBox<Helper> box = new JComboBox<>(items);
		box.setEditable(false);
		box.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				panel.set(box.getSelectedItem()!=null?((Helper)box.getSelectedItem()).rst :null);
			}
		});
		topPanel.add(box);
		topPanel.add(Box.createRigidArea(new Dimension(1, 16)));
		panel.add(topPanel,BorderLayout.NORTH);
		
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		bottomPanel.add(new JButton(new AbstractAction("Open in separate window") {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				RawStringTable rst = panel.selected;
				if(rst!=null){
					JFrame frame = new JFrame("Build report");
					frame.add(RawStringTable.toJTable(rst,rst.getDescription()!=null?rst.getDescription():rst.getName()));
					frame.pack();
					frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
					frame.setResizable(true);
					frame.setVisible(true);
				}
			}
		}));
		panel.add(bottomPanel,BorderLayout.SOUTH);
		
		// set first report
		if(defaultReport==null){
			defaultReport = reports.get(0);
		}
		for(int i =0 ; i < box.getItemCount();i++){
			Helper helper = box.getItemAt(i);
			if(helper.rst == defaultReport){
				panel.set(helper.rst);
				box.setSelectedItem(helper);
			}
		}
		panel.setPreferredSize(new Dimension(900, 450));
		SwingUtils.showModalDialogOnEDT(panel, "Road network graph build report", "Close", null,Dialog.ModalityType.MODELESS,true);
	}
}
