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
package com.opendoorlogistics.speedregions.spatialtree;

import com.opendoorlogistics.speedregions.beans.Bounds;
import com.opendoorlogistics.speedregions.beans.SpatialTreeNode;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

class SpatialTreeNodeWithGeometry extends SpatialTreeNode{
	private Geometry geometry;
	private Envelope envelope;

	public SpatialTreeNodeWithGeometry(GeometryFactory factory, Bounds bounds){
		setBounds(bounds);
		initGeometry(factory);
	}

	private void initGeometry(GeometryFactory factory) {
		this.envelope = getBounds().asEnvelope();
		this.geometry = factory.toGeometry(envelope);
		

	}
	
	public Geometry getExpandedGeometry(GeometryFactory factory,double expandFraction){
		// work out centre and width
		Bounds bounds = getBounds();
		double centreLng = 0.5*(bounds.getMinLng() + bounds.getMaxLng());
		double centreLat = 0.5*(bounds.getMinLat() + bounds.getMaxLat());
		double widthLng = bounds.getMaxLng() - bounds.getMinLng();
		double heightLat = bounds.getMaxLat() - bounds.getMinLat();
		
		// get expanded bounds
		double fraction = 0.5 + expandFraction*0.5;
		double xMinLng = centreLng - widthLng * fraction;
		double xMaxLng = centreLng + widthLng * fraction;
		double xMinLat= centreLat - heightLat * fraction;
		double xMaxLat = centreLat + heightLat * fraction;
		Bounds xBounds = new Bounds(xMinLng, xMaxLng, xMinLat, xMaxLat);
		Geometry xGeometry = factory.toGeometry(xBounds.asEnvelope());
		
		return xGeometry;
	}
	
	/**
	 * Copy and create geometry object
	 * @param factory
	 * @param node
	 */
	public SpatialTreeNodeWithGeometry(GeometryFactory factory , SpatialTreeNode node){
		SpatialTreeNode.copyNonChildFields(node, this);
		initGeometry(factory);
		for(SpatialTreeNode childToCopy: node.getChildren()){
			getChildren().add(new SpatialTreeNodeWithGeometry(factory,childToCopy));
		}
	}
	
	public static SpatialTreeNodeWithGeometry createGlobal(GeometryFactory factory){
		SpatialTreeNodeWithGeometry ret = new SpatialTreeNodeWithGeometry(factory,Bounds.createGlobal());
		ret.setBounds(Bounds.createGlobal());
		return ret;
	}
	
	public Geometry getGeometry() {
		return geometry;
	}

	public Envelope getEnvelope() {
		return envelope;
	}


	
}