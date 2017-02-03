package com.opendoorlogistics.speedregions.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.RegionsSpatialTreeNode;

public abstract class AbstractNode{
	private Bounds bounds;

	public Bounds getBounds() {
		return bounds;
	}

	public void setBounds(Bounds bounds) {
		this.bounds = bounds;
	}

	public AbstractNode(){
		
	}

	public AbstractNode(Bounds bounds) {
		this.bounds = bounds;
	}
	
	public abstract int getNbChildren();
	
	public abstract AbstractNode getChild(int i);
	
	@JsonIgnore
	public long countNodes(){
		long ret=1;
		int n= getNbChildren();
		for(int i =0 ; i<n ; i++){
			ret += getChild(i).countNodes();			
		}

		return ret;
	}
	
	public static interface NodeVisitor{
		/**
		 * Visit node and return true if visits should continue
		 * @param node
		 * @return
		 */
		boolean visit(AbstractNode node);
	}
	/**
	 * Visit nodes returning false if parsing to stop
	 * @param visitor
	 * @return
	 */
	@JsonIgnore
	public boolean visitNodes(NodeVisitor visitor){
		if(!visitor.visit(this)){
			return false;
		}
		int nc = getNbChildren();
		for(int i =0 ; i<nc ; i++){
			if(!getChild(i).visitNodes(visitor)){
				return false;
			}
		}
		return true;
	}

}
