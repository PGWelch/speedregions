package com.opendoorlogistics.speedregions.beans;

import java.util.List;

/**
 * This class allows for serialisation of the built quadtree together with the rules
 * @author Phil
 *
 */
public class CompiledSpeedRegions extends JSONToString{
	private SpatialTreeNode quadtree;
	private List<SpeedRule> validatedRules;
	
	public SpatialTreeNode getQuadtree() {
		return quadtree;
	}
	public void setQuadtree(SpatialTreeNode quadtree) {
		this.quadtree = quadtree;
	}
	public List<SpeedRule> getValidatedRules() {
		return validatedRules;
	}
	public void setValidatedRules(List<SpeedRule> rules) {
		this.validatedRules = rules;
	}
	
	
}
