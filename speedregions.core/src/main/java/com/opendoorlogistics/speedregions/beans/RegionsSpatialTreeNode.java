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
package com.opendoorlogistics.speedregions.beans;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opendoorlogistics.speedregions.utils.AbstractNode;
import com.opendoorlogistics.speedregions.utils.TextUtils;

/**
 * Spatial tree node with support for JSON serialisation / deserialisation
 * @author Phil
 *
 */
public class RegionsSpatialTreeNode extends AbstractNode{
	private String regionType;
	private long assignedPriority;
	private List<RegionsSpatialTreeNode> children = new ArrayList<RegionsSpatialTreeNode>(0);

	public RegionsSpatialTreeNode() {

	}

	public RegionsSpatialTreeNode(RegionsSpatialTreeNode deepCopyThis) {
		copyNonChildFields(deepCopyThis, this);

		for (RegionsSpatialTreeNode childToCopy : deepCopyThis.getChildren()) {
			this.children.add(new RegionsSpatialTreeNode(childToCopy));
		}

	}

	public static void copyNonChildFields(RegionsSpatialTreeNode deepCopyThis, RegionsSpatialTreeNode copyToThis) {
		if (deepCopyThis.getBounds() != null) {
			copyToThis.setBounds(new Bounds(deepCopyThis.getBounds()));
		} else {
			copyToThis.setBounds(null);

		}
		copyToThis.setRegionType(deepCopyThis.getRegionType());
		copyToThis.setAssignedPriority(deepCopyThis.getAssignedPriority());
	}


	/**
	 * In the built quadtree children are sorted by highest priority (numerically lowest) first
	 * @return
	 */
	public List<RegionsSpatialTreeNode> getChildren() {
		return children;
	}

	public void setChildren(List<RegionsSpatialTreeNode> children) {
		this.children = children;
	}

	/**
	 * Only leaf nodes have assigned region ids
	 * @return
	 */
	public String getRegionType() {
		return regionType;
	}

	public void setRegionType(String assignedRegionId) {
		this.regionType = assignedRegionId;
	}

	/**
	 * For a leaf node the priority is the priority of its assigned region.
	 * For non-leaf nodes the priority is the highest (numerically lowest)
	 * priority of its children
	 * @return
	 */
	public long getAssignedPriority() {
		return assignedPriority;
	}

	public void setAssignedPriority(long assignedPriority) {
		this.assignedPriority = assignedPriority;
	}


	@JsonIgnore
	public String toJSON() {
		return TextUtils.toJSON(this);
	}

	@Override
	public String toString() {
		// ensure we're not printing subclass fields like geometry which aren't json-friendly
		return new RegionsSpatialTreeNode(this).toJSON();
	}

	

	public static RegionsSpatialTreeNode fromJSON(String json) {
		return TextUtils.fromJSON(json, RegionsSpatialTreeNode.class);
	}
	

	@JsonIgnore
	@Override
	public int getNbChildren() {
		return children.size();
	}

	@JsonIgnore
	@Override
	public AbstractNode getChild(int i) {
		return children.get(i);
	}
	

}
