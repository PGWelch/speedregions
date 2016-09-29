package com.opendoorlogistics.speedregions.beans.files;

import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;

/**
 * Speed rules and a compiled spatial tree of regions.
 * @author Phil
 *
 */
public class CompiledSpeedRulesFile extends AbstractSpeedRulesFile{
	private SpatialTreeNode tree;
	
	public SpatialTreeNode getTree() {
		return tree;
	}
	public void setTree(SpatialTreeNode quadtree) {
		this.tree = quadtree;
	}
	
	
}
