package com.opendoorlogistics.speedregions.spatialtree;

import java.util.HashSet;
import java.util.TreeMap;

import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.JSONToString;
import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;
import com.rits.cloning.Cloner;

public class SpatialTreeStats extends JSONToString{
	private long nodes;
	private long leafNodes;
	private long regionIds;
	private TreeMap<String, Bounds> regionBounds = new TreeMap<>();
	
	public static long countNodes(RegionsSpatialTreeNode node){
		long ret=1;
		for(RegionsSpatialTreeNode child:node.getChildren()){
			ret += countNodes(child);
		}
		return ret;
	}

	public static SpatialTreeStats build(RegionsSpatialTreeNode node){
		SpatialTreeStats ret = new SpatialTreeStats();
		recurse(node, ret);
		return ret;
		
	}
	
	private static void recurse(RegionsSpatialTreeNode node,SpatialTreeStats stats){
		if(node.getRegionType()!=null){
			if(!stats.regionBounds.containsKey(node.getRegionType())){
				stats.regionBounds.put(node.getRegionType(), Cloner.standard().deepClone(node.getBounds()));
				stats.regionIds++;
			}
			
			stats.regionBounds.get(node.getRegionType()).expand(node.getBounds());
		}
		stats.nodes++;
		if(node.getChildren().size()==0){
			stats.leafNodes++;
		}
		for(RegionsSpatialTreeNode child:node.getChildren()){
			recurse(child,stats);
		}
	}

	public long getNodes() {
		return nodes;
	}

	public void setNodes(long nodes) {
		this.nodes = nodes;
	}

	public long getLeafNodes() {
		return leafNodes;
	}

	public void setLeafNodes(long leafNodes) {
		this.leafNodes = leafNodes;
	}

	public long getRegionIds() {
		return regionIds;
	}

	public void setRegionIds(long regionIds) {
		this.regionIds = regionIds;
	}

	public TreeMap<String, Bounds> getRegionBounds() {
		return regionBounds;
	}

	public void setRegionBounds(TreeMap<String, Bounds> regionBounds) {
		this.regionBounds = regionBounds;
	}
	
	
}
