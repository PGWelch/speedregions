package com.opendoorlogistics.speedregions.beans.files;

import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;

/**
 * Speed rules and a compiled spatial tree of regions.
 * @author Phil
 *
 */
public class CompiledSpeedRulesFile extends AbstractSpeedRulesFile{
	private RegionsSpatialTreeNode tree;
	
	public RegionsSpatialTreeNode getTree() {
		return tree;
	}
	public void setTree(RegionsSpatialTreeNode quadtree) {
		this.tree = quadtree;
	}
	
	
}
