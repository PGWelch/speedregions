package com.opendoorlogistics.speedregions.utils;

import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opendoorlogistics.speedregions.beans.Bounds;

public class AbstractNodeWithChildArray extends AbstractNode{
	private AbstractNodeWithChildArray[] children;

	public AbstractNodeWithChildArray() {
		super();
	}

	public AbstractNodeWithChildArray(Bounds bounds) {
		super(bounds);
	}

	public AbstractNodeWithChildArray[] getChildren() {
		return children;
	}

	public void setChildren(AbstractNodeWithChildArray[] children) {
		this.children = children;
	}

	@Override
	public int getNbChildren() {
		return children!=null? children.length:0;
	}

	@Override
	public AbstractNode getChild(int i) {
		return children[i];
	}
	
	@JsonIgnore
	public void doQuadSplit(Function< Bounds,AbstractNodeWithChildArray> factory){
		if(children!=null){
			throw new RuntimeException("Node has already been split");
		}
		setChildren(new AbstractNodeWithChildArray[4]);
		int i = 0;
		for (Bounds bounds : getBounds().getQuadSplit()) {
			getChildren()[i++] = factory.apply(bounds);
		}
	}

}
