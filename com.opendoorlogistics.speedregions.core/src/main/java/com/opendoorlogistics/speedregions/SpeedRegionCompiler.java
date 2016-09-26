package com.opendoorlogistics.speedregions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.opendoorlogistics.speedregions.beans.CompiledSpeedRegions;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.opendoorlogistics.speedregions.beans.SpeedRulesFile;
import com.opendoorlogistics.speedregions.processor.GeomConversion;
import com.opendoorlogistics.speedregions.processor.RegionProcessorUtils;
import com.opendoorlogistics.speedregions.processor.SpatialTreeStats;
import com.opendoorlogistics.speedregions.processor.SpeedRulesFilesProcesser;

public class SpeedRegionCompiler {
	private static final Logger LOGGER = Logger.getLogger(SpeedRegionCompiler.class.getName());

	/**
	 * Build a lookup bean, which can be serialised to JSON
	 * @param files
	 * @param minDiagonalLengthMetres
	 * @return
	 */
	public static CompiledSpeedRegions buildBeanFromSpeedRulesObjs(List<SpeedRulesFile> files, double minDiagonalLengthMetres) {
		SpeedRulesFilesProcesser processer = new SpeedRulesFilesProcesser();
		final SpatialTreeNode root=processer.buildQuadtree(files, GeomConversion.newGeomFactory(), minDiagonalLengthMetres);
		LOGGER.info("Built quadtree: " + SpatialTreeStats.build(root).toString());
		
		CompiledSpeedRegions built = new CompiledSpeedRegions();
		built.setQuadtree(root);
		
		built.setValidatedRules(processer.validateSpeedRules(files));
		return built;
	}
	
	public static CompiledSpeedRegions buildBeanFromSpeedRulesFiles(List<File> files ,double minDiagonalLengthMetres){
		return buildBeanFromSpeedRulesObjs(loadSpeedRulesFiles(files), minDiagonalLengthMetres);
	}
	
	public static CompiledSpeedRegions loadBean(File file){
		return RegionProcessorUtils.fromJSON(file, CompiledSpeedRegions.class);
	}
	
	public static void saveBean(CompiledSpeedRegions bean,File file){
		RegionProcessorUtils.toJSONFile(bean, file);
	}

	private static List<SpeedRulesFile> loadSpeedRulesFiles(List<File> files) {
		ArrayList<SpeedRulesFile> objects = new ArrayList<SpeedRulesFile>(files.size());
		for(File file : files){
			objects.add(RegionProcessorUtils.fromJSON(file, SpeedRulesFile.class));
		}
		return objects;
	}
	
	

}
