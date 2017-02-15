package com.opendoorlogistics.speedregions.excelshp.app;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.PlainDocument;

import com.opendoorlogistics.speedregions.excelshp.io.ShapefileIO;
import com.opendoorlogistics.speedregions.utils.TextUtils;

public class SettingsPanel extends JPanel{
	private static final String PREFKEY = "excelshp_settings";
	private static final Preferences USER_PREFERENCES = Preferences.userNodeForPackage(SettingsPanel.class);
	
	private final FileBrowserPanel pbf;
	private final FileBrowserPanel outdir;
	private final JCheckBox excelShp;
	private final JCheckBox[] vehicleTypes = new JCheckBox[VehicleType.values().length];
	private final FileBrowserPanel excel;
	private final FileBrowserPanel shp;
	private final JLabel idFieldLabel;
	private final JComboBox<String> idField;
	private final JLabel gridSizeLabel;
	private final JTextField gridSize;
	private final JCheckBox miles;
	private final JCheckBox mergeRegionsBeforeBuild;
	private AppSettings settings;

	private static AppSettings load(){
		String s = USER_PREFERENCES.get(PREFKEY,TextUtils.toJSON(new AppSettings()));
		try {
			return TextUtils.fromJSON(s, AppSettings.class);
		} catch (Exception e) {
			// can fail if data structure has changed with different versions....
		}
		return new AppSettings();
	}

	private static void save(AppSettings s){
		USER_PREFERENCES.put(PREFKEY, TextUtils.toJSON(s));
	}
	
	public SettingsPanel() {
		this.settings = load();
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// Can't use lambda as on java 1.7...
		Consumer<String> changeCB = new  Consumer<String>() {
			
			@Override
			public void accept(String t) {
				writePanelDataToSettings();
			}
		};
		add( new JLabel("<html>Download OpenStreetMap map data in osm.pbf format from <a href=\"http://download.geofabrik.de/\">http://download.geofabrik.de/</a></html>"));
		addSep();
		
		pbf = new FileBrowserPanel(0, "osm.pbf file ", settings.getPbfFile(), changeCB, false, "OK", new FileNameExtensionFilter("OSM data (.osm.pbf)", "pbf")) ;
		add(pbf);
		
		addSep();
		outdir = new FileBrowserPanel(0, "Output directory ", settings.getOutdirectory(), changeCB, true, "OK");
		add(outdir);
		
		addVehicleTypesPanel();

		addSep();
		miles = new JCheckBox("Create reports in miles instead of km",settings.isReportInMiles());
		ActionListener actionListener = new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				writePanelDataToSettings();
			}
		};
		miles.addActionListener(actionListener);
		add(miles);
		
		addSep();
		excelShp = new JCheckBox("Use Excel + shapefile containing SpeedRegions", settings.isUseExcelShape());
		excelShp.addActionListener(actionListener);
		add(excelShp);
		
		addSep();
		excel = new FileBrowserPanel(0, "Excel file ", settings.getExcelfile(), changeCB, false, "OK", new FileNameExtensionFilter("Excel file (xls,xlsx)", "xls", "xlsx")) ;
		add(excel);
		
		addSep();
		shp = new FileBrowserPanel(0, "Shapefile ", settings.getShapefile(), changeCB, false, "OK", new FileNameExtensionFilter("Shapefile (shp)", "shp")) ;
		add(shp);
		
		addSep();
		JPanel fieldnamePanel = new JPanel();
		fieldnamePanel.setLayout(new BoxLayout(fieldnamePanel, BoxLayout.X_AXIS));
		idFieldLabel=new JLabel("Name of ID field in shapefile ");
		fieldnamePanel.add(idFieldLabel);
		idField = new JComboBox<>(new DefaultComboBoxModel<String>());
		fieldnamePanel.add(idField);
		configureIdCombo();
		add(fieldnamePanel);
		
		addSep();
		gridSizeLabel = new JLabel("Lookup grid cell length in metres (recommended 100m) ");
		gridSize = FileBrowserPanel.createTextField(Double.toString(settings.getGridCellMetres()), changeCB);
		((PlainDocument)gridSize.getDocument()).setDocumentFilter(new DoubleDocumentFilter(this));
		JPanel gridSizePanel = new JPanel();
		gridSizePanel.setLayout(new BoxLayout(gridSizePanel, BoxLayout.X_AXIS));
		gridSizePanel.add(gridSizeLabel);
		gridSizePanel.add(gridSize);
		add(gridSizePanel);
		
		addSep();
		mergeRegionsBeforeBuild = new JCheckBox("Merge regions before building graph (quicker, but no reports by brick)", settings.isMergeRegionsBeforeBuild());
		mergeRegionsBeforeBuild.addActionListener(actionListener);
		add(mergeRegionsBeforeBuild);
		
		for(Component component : getComponents()){
			if(component instanceof JComponent){
				((JComponent)component).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
		}
		writeSettingsToPanel();
	}

	private void addVehicleTypesPanel() {
		addSep();
		JPanel vehicleTypesPanel = new JPanel();
		vehicleTypesPanel.setLayout(new BoxLayout(vehicleTypesPanel, BoxLayout.X_AXIS));
		vehicleTypesPanel.add(new JLabel("Build for "));
		for(VehicleType type: VehicleType.values()){
			vehicleTypes[type.ordinal()] = new JCheckBox(type.getGraphhopperName() + (!type.isSpeedRegionsSupported()?" (speed regions unsupported)":"") +" ", settings.isEnabled(type));
			vehicleTypes[type.ordinal()].addActionListener(new ActionListener() {
				
				@Override
				public void actionPerformed(ActionEvent e) {
					writePanelDataToSettings();
				}
			});	
			vehicleTypesPanel.add(vehicleTypes[type.ordinal()]);
		}
		add(vehicleTypesPanel);
	}

	private void configureIdCombo() {
		idField.setEditable(true);
		idField.setSelectedItem(settings.getIdFieldNameInShapefile());
		idField.addPopupMenuListener(new PopupMenuListener() {
			
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
				// refresh values
				DefaultComboBoxModel<String> model =(DefaultComboBoxModel<String>) idField.getModel();
				model.removeAllElements();
				try {
					for(String s: ShapefileIO.readHeaderNames(new File(settings.getShapefile()))){
						model.addElement(s);
					}
				} catch (Exception e2) {
					// fails if shapefile not set etc
				}
			}
			
			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
			}
			
			@Override
			public void popupMenuCanceled(PopupMenuEvent e) {	
			}
		});
		idField.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				writePanelDataToSettings();
			}
		});

		idField.getEditor().addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				writePanelDataToSettings();
			}
		});
		idField.getEditor().getEditorComponent().addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
				writePanelDataToSettings();
			}

			@Override
			public void keyReleased(KeyEvent e) {
				writePanelDataToSettings();
			}

			@Override
			public void keyPressed(KeyEvent e) {
				writePanelDataToSettings();
			}
		});
	}
	
	private void update(){
		for(Component component : new Component[]{excel,shp,idFieldLabel,idField,gridSizeLabel,gridSize,mergeRegionsBeforeBuild}){
			component.setEnabled(settings.isUseExcelShape());
		}

	}
	
	private void addSep(){
		add(Box.createRigidArea(new Dimension(1, 16)));
	}

	private void writePanelDataToSettings(){
		settings.setPbfFile(pbf.getFilename());
		settings.setOutdirectory(outdir.getFilename());
		settings.setUseExcelShape(excelShp.isSelected());
		settings.setExcelfile(excel.getFilename());
		settings.setShapefile(shp.getFilename());
		settings.setIdFieldNameInShapefile(idField.getEditor().getItem().toString());
		settings.setReportInMiles(miles.isSelected());
		settings.setMergeRegionsBeforeBuild(mergeRegionsBeforeBuild.isSelected());
		
		for(VehicleType type:VehicleType.values()){
			settings.setEnabled(type,vehicleTypes[type.ordinal()].isSelected());
		}
		
		try {
			settings.setGridCellMetres(Double.parseDouble(gridSize.getText().trim()));
		} catch (Exception e) {
		}
		update();
		
		// persist?
		save(settings);
	}
	
	
	private void writeSettingsToPanel(){
		pbf.setFilename(settings.getPbfFile());
		outdir.setFilename(settings.getOutdirectory());
		excelShp.setSelected(settings.isUseExcelShape());
		excel.setFilename(settings.getExcelfile());
		shp.setFilename(settings.getShapefile());
		idField.setSelectedItem(settings.getIdFieldNameInShapefile());
		gridSize.setText(Double.toString(settings.getGridCellMetres()));
		miles.setSelected(settings.isReportInMiles());
		mergeRegionsBeforeBuild.setSelected(settings.isMergeRegionsBeforeBuild());
		
		for(VehicleType type:VehicleType.values()){
			vehicleTypes[type.ordinal()].setSelected(settings.isEnabled(type));
		}
		
		update();

		
	}
	
	public static AppSettings modal(){
		SettingsPanel panel = new SettingsPanel();
		return JOptionPane.showConfirmDialog(null, panel, "Create road network graph", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION?panel.settings:null;
	}
	

}
