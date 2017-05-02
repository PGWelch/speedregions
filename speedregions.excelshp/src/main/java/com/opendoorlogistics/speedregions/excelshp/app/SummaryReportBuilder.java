package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.excelshp.io.IOStringConstants;
import com.opendoorlogistics.speedregions.excelshp.io.RawStringTable;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.BrickItem;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.ConversionResult;
import com.opendoorlogistics.speedregions.excelshp.processing.ExcelShp2GeoJSONConverter.RuleConversionInfo;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class SummaryReportBuilder {
	private final AppInjectedDependencies dependencies;
	private final Random random = new Random(123);
	private final double percentResolution = 2.5;
	private final SpeedUnit unit;

	public SummaryReportBuilder(AppInjectedDependencies speedProvider, SpeedUnit unit) {
		this.dependencies = speedProvider;
		this.unit = unit;
	}

	// Collate by 10 percent bins
	private static class CollationRec implements Comparable<CollationRec> {
		double low;
		double high;
		TreeSet<String> brickIds = new TreeSet<>();
		TreeSet<String> highwayTypes = new TreeSet<>();

		@Override
		public int compareTo(CollationRec o) {
			return Double.compare(low, o.low);
		}

	}

	public boolean showSummaryReport(final ConversionResult cresult) {

		return SwingUtils.runOnEDT(new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				return showSummaryReportOnEDT(cresult);
			}
		});
	}

	/**
	 * Show a summary report and return false if uses cancels
	 * 
	 * @param details
	 * @return
	 */
	private boolean showSummaryReportOnEDT(ConversionResult conversionResult) {
		JScrollPane textScrollPane = buildTextAreaReport(conversionResult);
		JScrollPane tableScrollPane = buildTable(conversionResult);

		JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		splitPane.setTopComponent(tableScrollPane);
		splitPane.setBottomComponent(textScrollPane);
		splitPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		return SwingUtils.showModalDialogOnEDT(splitPane, "Summary report - review before building graph",
				"Build graph (takes several minutes)", "Cancel", null, false);
	}

	private JScrollPane buildTextAreaReport(ConversionResult conversionResult) {
		StringBuilder builder = new StringBuilder();
		final TreeMap<VehicleTypeTimeProfile, TreeMap<String, RuleConversionInfo>> detailsByVehicleType = conversionResult.rules;

		for (Map.Entry<VehicleTypeTimeProfile, TreeMap<String, RuleConversionInfo>> entry : detailsByVehicleType
				.entrySet()) {
			if (!dependencies.isDefaultSpeedsKnown(entry.getKey())) {
				// Skip if default speeds are unknown
				continue;
			}

			builder.append("For vehicle type " + entry.getKey().getCombinedId()
					+ ", speeds for one or more types of road are:" + System.lineSeparator() + System.lineSeparator());

			TreeMap<CollationRec, CollationRec> recs = new TreeMap<>();
			for (RuleConversionInfo info : entry.getValue().values()) {
				collateForRule(info, recs);
			}
			addCollatedToStringBuilder("...", recs, builder);

			builder.append(System.lineSeparator());
		}

		// collate bricks by rule key
		TreeMap<String, TreeSet<String>> collated = new TreeMap<>();
		for (BrickItem brickItem : conversionResult.bricks) {
			TreeSet<String> set = collated.get(TextUtils.stdString(brickItem.speedProfileId));
			if (set == null) {
				set = new TreeSet<>();
				collated.put(TextUtils.stdString(brickItem.speedProfileId), set);
			}
			set.add(brickItem.brickId);
		}

		// sort by smallest first
		ArrayList<Map.Entry<String, TreeSet<String>>> sorted = new ArrayList<>(collated.entrySet());
		Collections.sort(sorted, new Comparator<Map.Entry<String, TreeSet<String>>>() {

			@Override
			public int compare(Entry<String, TreeSet<String>> o1, Entry<String, TreeSet<String>> o2) {
				return Integer.compare(o1.getValue().size(), o2.getValue().size());
			}
		});

		for (Map.Entry<String, TreeSet<String>> entry : sorted) {
			builder.append(System.lineSeparator());
			builder.append("Rule " + entry.getKey() + " has bricks ");
			for (String s : entry.getValue()) {
				builder.append(s);
				builder.append(", ");
			}
			builder.append(System.lineSeparator());
		}

		JTextArea textArea = new JTextArea(builder.toString());
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setPreferredSize(new Dimension(700, 200));
		scrollPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5),
				BorderFactory.createTitledBorder("Detailed report")));
		return scrollPane;
	}

	private JScrollPane buildTable(ConversionResult conversionResult) {
		final TreeMap<VehicleTypeTimeProfile, TreeMap<String, RuleConversionInfo>> detailsByVehicleType = conversionResult.rules;

		class TableRow {
			VehicleTypeTimeProfile vehicle;
			RuleConversionInfo rule;
			String meanPCChange;

			int nbBricks() {
				return rule.getParentCollapsedRule().getMatchRule().getRegionTypes().size();
			}
		}

		// init table
		RawStringTable rsTable = new RawStringTable("Summary");
		ArrayList<String> header = new ArrayList<>();
		header.add("Vehicle");
		header.add("RuleId");
		header.add("BrickCount");
		header.add("% change");
		rsTable.add(header);
		for (String rt : IOStringConstants.ROAD_TYPES) {
			header.add(rt);
		}

		// loop over each vehicle type
		final DecimalFormat df = new DecimalFormat("0.0");
		for (Map.Entry<VehicleTypeTimeProfile, TreeMap<String, RuleConversionInfo>> vehicleTypeRules : detailsByVehicleType
				.entrySet()) {
			final ArrayList<TableRow> rows = new ArrayList<>();
			for (RuleConversionInfo rule : vehicleTypeRules.getValue().values()) {
				TableRow row = new TableRow();
				row.vehicle = vehicleTypeRules.getKey();
				row.rule = rule;

				row.meanPCChange = "n/a";
				if (dependencies.isDefaultSpeedsKnown(row.vehicle)) {
					DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
					for (String ht : IOStringConstants.ROAD_TYPES) {
						double originalSpeed = dependencies.speedKmPerHour(row.vehicle, ht);
						double newSpeed = rule.getParentCollapsedRule().applyRule(ht, originalSpeed, true);
						statistics.accept(ExcelShp2GeoJSONConverter.percentageChange(originalSpeed, newSpeed));
					}
					row.meanPCChange = df.format(statistics.getAverage());

				}
				rows.add(row);
			}

			Collections.sort(rows, new Comparator<TableRow>() {

				@Override
				public int compare(TableRow o1, TableRow o2) {
					return Integer.compare(o1.nbBricks(), o2.nbBricks());
				}
			});

			// add rows to the raw string table
			for (TableRow row : rows) {
				ArrayList<String> rsRow = new ArrayList<>();
				rsTable.add(rsRow);
				SpeedRule rule = row.rule.getParentCollapsedRule();
				rsRow.add(row.vehicle.getCombinedId());
				rsRow.add(rule.getId());
				rsRow.add(Integer.toString(row.nbBricks()));
				rsRow.add(row.meanPCChange);
				for (String highwayType : IOStringConstants.ROAD_TYPES) {
					if (dependencies.isDefaultSpeedsKnown(row.vehicle)) {
						double originalSpeed = dependencies.speedKmPerHour(row.vehicle, highwayType);
						double newSpeed = rule.applyRule(highwayType, originalSpeed, true);
						rsRow.add(df.format(unit.convertKMToMe(newSpeed)));
					} else {
						rsRow.add("n/a");
					}
				}

			}

			// add row(s) showing the default values
			// for (VehicleTypeTimeProfile vehicleType : detailsByVehicleType.keySet()) {
			VehicleTypeTimeProfile vehicleType = vehicleTypeRules.getKey();
			if (dependencies.isDefaultSpeedsKnown(vehicleType)) {
				ArrayList<String> rsRow = new ArrayList<>();
				rsTable.add(rsRow);
				rsRow.add(vehicleType.getCombinedId());
				rsRow.add(AppConstants.DEFAULT_SPEEDS_DISPLAY_NAME);
				rsRow.add(AppConstants.NA);
				rsRow.add(AppConstants.NA);
				for (String highwayType : IOStringConstants.ROAD_TYPES) {
					double originalSpeed = dependencies.speedKmPerHour(vehicleType, highwayType);
					rsRow.add(df.format(unit.convertKMToMe(originalSpeed)));
				}
			}
			// }

		}

		JScrollPane table = RawStringTable.toJTable(rsTable,
				"By vehicle and rule, bricks count, mean % change and default speed in " + unit.getShorthand() + "/hr");
		return table;
	}

	private void collateForRule(RuleConversionInfo info, TreeMap<CollationRec, CollationRec> recs) {
		for (double percentage : info.getPercentageSpeedChangeByRoadType().values()) {
			CollationRec rec = new CollationRec();
			rec.low = percentResolution * (long) Math.floor(percentage / percentResolution);
			rec.high = rec.low + percentResolution;

			CollationRec prexisting = recs.get(rec);
			if (prexisting != null) {
				rec = prexisting;
			} else {
				recs.put(rec, rec);
			}

			rec.brickIds.addAll(info.getParentCollapsedRule().getMatchRule().getRegionTypes());
			// rec.highwayTypes.a
		}
	}

	private void addCollatedToStringBuilder(String prefix, TreeMap<CollationRec, CollationRec> recs,
			StringBuilder builder) {
		for (CollationRec rec : recs.keySet()) {
			builder.append(prefix + "changed by " + rec.low + "% to " + rec.high + "% for " + rec.brickIds.size()
					+ " brick(s), e.g. ");
			int i = 0;

			// randomly select example bricks (better than showing the first alphabetical few)
			List<String> tmp = new ArrayList<>(rec.brickIds);
			Collections.shuffle(tmp, random);
			if (tmp.size() > AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE) {
				tmp = tmp.subList(0, AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE);
			}
			for (String brickId : new TreeSet<>(tmp)) {

				if (i > 0) {
					builder.append(",");
				}

				if (i >= AppConstants.SUMMARY_REPORT_MAX_BRICKIDS_PER_LINE) {
					builder.append("...");
					break;
				}

				builder.append(brickId);

				i++;
			}
			builder.append(System.lineSeparator());

		}
	}

}
