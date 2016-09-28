# SpeedRegions
SpeedRegions is a work-in-progress utility library designed for use with [Graphhopper](https://github.com/graphhopper/graphhopper)
which allows geographic regions with different speed profiles (e.g. city, country) to be defined. 
SpeedRegions uses several types of text files containing data in JSON format as its input.
The most important text file type is an *UncompiledSpeedRules* file. Here's an example file:

	{
	  "rules" : [ {
		"multiplier" : 0.7,
		"matchRule" : {
		  "flagEncoders" : [ "car" ],
		  "regionTypes" : [ "valleta" ]
		}
	  } ],
	  "geoJson" : {
		"type" : "FeatureCollection",
		"features" : [ {
		  "type" : "Feature",
		  "properties" : {
			"regiontype" : "valleta"
		  },
		  "geometry" : {
			"type" : "Polygon",
			"coordinates" : [ [ [ 14.457384575, 35.89565677 ], [ 14.45653875, 35.899281932 ], [ 14.458598686, 35.902341063 ], [ 14.460328621, 35.903588517 ], [ 14.462642955, 35.903662185 ], [ 14.463669377, 35.903031668 ], [ 14.466885473, 35.903436003 ], [ 14.474620438, 35.900815895 ], [ 14.47560616, 35.902646356 ], [ 14.478262151, 35.903359408 ], [ 14.48170059, 35.903080892 ], [ 14.482825259, 35.905273952 ], [ 14.484661024, 35.906453246 ], [ 14.482064857, 35.909298792 ], [ 14.48261423, 35.911169136 ], [ 14.484530453, 35.912303757 ], [ 14.492025801, 35.914038195 ], [ 14.492099908, 35.916840502 ], [ 14.494218522, 35.918159473 ], [ 14.496863088, 35.917848707 ], [ 14.504244527, 35.914234066 ], [ 14.505821489, 35.912450829 ], [ 14.50531176, 35.910296543 ], [ 14.503015911, 35.90910952 ], [ 14.504048385, 35.908181328 ], [ 14.503548915, 35.905793336 ], [ 14.500927327, 35.904628272 ], [ 14.495495, 35.904402622 ], [ 14.494694111, 35.903789943 ], [ 14.498669763, 35.902729607 ], [ 14.499548669, 35.900796901 ], [ 14.498545044, 35.898904423 ], [ 14.497541163, 35.898554493 ], [ 14.502196889, 35.896830726 ], [ 14.504298724, 35.898354127 ], [ 14.506708899, 35.898981673 ], [ 14.508739395, 35.900704787 ], [ 14.512138017, 35.901597455 ], [ 14.513991283, 35.903362667 ], [ 14.516214595, 35.903592849 ], [ 14.520334468, 35.902897613 ], [ 14.522506008, 35.90169914 ], [ 14.522995309, 35.899607856 ], [ 14.521515746, 35.89784879 ], [ 14.5195844, 35.897552275 ], [ 14.519862388, 35.894473296 ], [ 14.518251389, 35.893098567 ], [ 14.515437882, 35.89195893 ], [ 14.514770674, 35.890967969 ], [ 14.515983301, 35.889085031 ], [ 14.516154084, 35.88682177 ], [ 14.515175118, 35.884706714 ], [ 14.516435314, 35.88291993 ], [ 14.516802508, 35.88092412 ], [ 14.515407889, 35.879252265 ], [ 14.51298533, 35.878784166 ], [ 14.50765095, 35.879475078 ], [ 14.505195661, 35.878075059 ], [ 14.502811432, 35.878618985 ], [ 14.500843023, 35.879815169 ], [ 14.498347497, 35.878257795 ], [ 14.49584433, 35.878623366 ], [ 14.494338579, 35.880284308 ], [ 14.494603199, 35.881985166 ], [ 14.484047356, 35.882519382 ], [ 14.481711218, 35.883442279 ], [ 14.481073397, 35.884899684 ], [ 14.478373636, 35.884188849 ], [ 14.470820536, 35.88460609 ], [ 14.468694102, 35.885361006 ], [ 14.46782897, 35.887694007 ], [ 14.463597861, 35.886755088 ], [ 14.461721805, 35.887686648 ], [ 14.458288577, 35.891024366 ], [ 14.457494404, 35.892720693 ], [ 14.457384575, 35.89565677 ] ] ]
		  }
		} ]
	  }
	}

This file defines a region around the city of Valleta, in Malta 
and a rule which applies to the Graphhopper car flag encoder (i.e. the speed profile for cars)
and slows down speeds by 30% (multiplier = 0.7) within Valleta.

## Compiled vs uncompiled
An uncompiled file is called 'uncompiled' because the SpeedRegions library still needs to build a spatial lookup
(a kind of map) which stores the geographic regions in a data structure optimised for querying the region a road
sits within. 
Building this spatial lookup can be slow - e.g. a couple of minutes just for the United Kingdom dependent on accuracy.
As speed rules will change often (e.g. make a road type a little faster or slower) but the geographic regions won't
change as often, we support pre-compiling this spatial tree before using it in Graphhopper. 
You can therefore compile the spatial tree once and then quickly modify the speed rules in the compiled file without
having to recompile the tree. 

## Types of JSON text file
SpeedRegions can use several different types of JSON text files:

1. **UncompiledSpeedRules**. A JSON text file containing the speed rules and GeoJSON feature collection.
1. **CompiledSpeedRules**. A JSON text file containing the speed rules and the built spatial tree.
1. **FeatureCollection**. A GeoJSON text file containing a single feature collection.
1. **CompiledTree**. A JSON text file the containing built spatial tree.

You can use the command line tools to build the *CompiledTree* file from a *FeatureCollection* file. 
You build this tree once and then place it into a *CompiledSpeedRules* file. 
You then tweak the speed rules in the *CompiledSpeedRules* as you like, without rebuilding the tree again.

This is an example *CompiledSpeedRules* file:

	{
	  "rules" : [ {
		"speedsByRoadType" : { },
		"multiplier" : 0.7,
		"speedUnit" : "KM_PER_HOUR",
		"matchRule" : {
		  "flagEncoders" : [ "car" ],
		  "regionTypes" : [ "valleta" ]
		}
	  } ],
	  "tree" : {
		"bounds" : {
		  "minLng" : 14.4140625,
		  "maxLng" : 14.58984375,
		  "minLat" : 35.859375,
		  "maxLat" : 35.947265625
		},
		"regionType" : "valleta",
		"assignedPriority" : 1,
		"children" : [ ]
	  }
	}

In this case the tree is very small - it contains only one node. 
Generally speaking the tree will be a very large multi-level data structure.
This is an example *FeatureCollection* file 
- it is pure geoJson and corresponds to the *geoJson* field in the UncompiledSpeedRules file:

	{
		"type" : "FeatureCollection",
		"features" : [ {
		  "type" : "Feature",
		  "properties" : {
			"regiontype" : "valleta"
		  },
		  "geometry" : {
			"type" : "Polygon",
			"coordinates" : [ [ [ 14.457384575, 35.89565677 ], [ 14.45653875, 35.899281932 ], [ 14.458598686, 35.902341063 ], [ 14.460328621, 35.903588517 ], [ 14.462642955, 35.903662185 ], [ 14.463669377, 35.903031668 ], [ 14.466885473, 35.903436003 ], [ 14.474620438, 35.900815895 ], [ 14.47560616, 35.902646356 ], [ 14.478262151, 35.903359408 ], [ 14.48170059, 35.903080892 ], [ 14.482825259, 35.905273952 ], [ 14.484661024, 35.906453246 ], [ 14.482064857, 35.909298792 ], [ 14.48261423, 35.911169136 ], [ 14.484530453, 35.912303757 ], [ 14.492025801, 35.914038195 ], [ 14.492099908, 35.916840502 ], [ 14.494218522, 35.918159473 ], [ 14.496863088, 35.917848707 ], [ 14.504244527, 35.914234066 ], [ 14.505821489, 35.912450829 ], [ 14.50531176, 35.910296543 ], [ 14.503015911, 35.90910952 ], [ 14.504048385, 35.908181328 ], [ 14.503548915, 35.905793336 ], [ 14.500927327, 35.904628272 ], [ 14.495495, 35.904402622 ], [ 14.494694111, 35.903789943 ], [ 14.498669763, 35.902729607 ], [ 14.499548669, 35.900796901 ], [ 14.498545044, 35.898904423 ], [ 14.497541163, 35.898554493 ], [ 14.502196889, 35.896830726 ], [ 14.504298724, 35.898354127 ], [ 14.506708899, 35.898981673 ], [ 14.508739395, 35.900704787 ], [ 14.512138017, 35.901597455 ], [ 14.513991283, 35.903362667 ], [ 14.516214595, 35.903592849 ], [ 14.520334468, 35.902897613 ], [ 14.522506008, 35.90169914 ], [ 14.522995309, 35.899607856 ], [ 14.521515746, 35.89784879 ], [ 14.5195844, 35.897552275 ], [ 14.519862388, 35.894473296 ], [ 14.518251389, 35.893098567 ], [ 14.515437882, 35.89195893 ], [ 14.514770674, 35.890967969 ], [ 14.515983301, 35.889085031 ], [ 14.516154084, 35.88682177 ], [ 14.515175118, 35.884706714 ], [ 14.516435314, 35.88291993 ], [ 14.516802508, 35.88092412 ], [ 14.515407889, 35.879252265 ], [ 14.51298533, 35.878784166 ], [ 14.50765095, 35.879475078 ], [ 14.505195661, 35.878075059 ], [ 14.502811432, 35.878618985 ], [ 14.500843023, 35.879815169 ], [ 14.498347497, 35.878257795 ], [ 14.49584433, 35.878623366 ], [ 14.494338579, 35.880284308 ], [ 14.494603199, 35.881985166 ], [ 14.484047356, 35.882519382 ], [ 14.481711218, 35.883442279 ], [ 14.481073397, 35.884899684 ], [ 14.478373636, 35.884188849 ], [ 14.470820536, 35.88460609 ], [ 14.468694102, 35.885361006 ], [ 14.46782897, 35.887694007 ], [ 14.463597861, 35.886755088 ], [ 14.461721805, 35.887686648 ], [ 14.458288577, 35.891024366 ], [ 14.457494404, 35.892720693 ], [ 14.457384575, 35.89565677 ] ] ]
		  }
		} ]
	}

This is our example *CompiledTree* file; it corresponds to the *tree* field in the *CompiledSpeedRules* file:

	{
		"bounds" : {
		  "minLng" : 14.4140625,
		  "maxLng" : 14.58984375,
		  "minLat" : 35.859375,
		  "maxLat" : 35.947265625
		},
		"regionType" : "valleta",
		"assignedPriority" : 1,
		"children" : [ ]
	}

## FeatureCollection format
A featureCollection should only contain Polygon or MultiPolygon geometry types.
Each feature should have a **regiontype** property which links to the speed rules.
RegionType could refer to a geographic area - e.g. London - or a type of geographic area (e.g. 'big city').
Multiple features can therefore have the same regionType, there is no requirement for it to be a unique value.

The order of a feature in the featureCollection is used as its priority when assigning an area (technically a leaf node
in the spatial tree) to a region.
If, for example, you have a featureCollection containing polygons for United Kingdom, Central London and Outer London,
your first feature should be the smallest - Central London - followed by Outer London (which Central London sits within)
and then the United Kingdom. 
The order within the collection is therefore used to model overlapping polygons where one polygon sits within another.
	
## Using with Graphhopper
The integration with Graphhopper is currently experimental and available for car speed profile only.
Full integration within the Graphhopper project is planned.
See the projects com.opendoorlogistics.speedregions.experimental.gh0.5 and com.opendoorlogistics.speedregions.experimental.ghlatest
to build a car profile graph using speed regions with Graphhopper 0.5 and Graphhopper latest release (version 0.7 as of 28/9/2016).

To build a Graphhopper graph using speed regions, build the project using Maven and run from the command line as shown [here](http://www.opendoorlogistics.com/tutorials/tutorial-vi-advance-configuration/building-road-network-graphs/)
but with one of two sets of additional command line arguments:

* Using an UncompiledSpeedRules file. Add the argument:

		speedregions.compiled=your_compiled_speed_rules_filename

* Using a CompiledSpeedRules file. Add the two arguments:

		speedregions.uncompiled=your_compiled_speed_rules_filename
		speedregions.tolerance=tolerance_in_metres
	
## Choosing the tolerance value
The tolerance measures how accurate the spatial tree will be.
Technically speaking, the spatial tree divides the world up into rectangles, assigning each rectangle to a single region.
The tolerance is the minimum side length of these rectangles; so no rectangle will be created with a side length less
than tolerance_in_metres.

Road edges whose centre is within *tolerance_in_metres* of a region boundary may therefore be placed in the wrong region.
Generally speaking, the effects of placing an edge already near a region boundary within the wrong region 
will be small, so tolerance can be a relatively high value.
The tolerance value should however be signficantly smaller than the approximate width or height of your regions,
or your regions will not be represented properly or not even used.
The building of the spatial tree (i.e. the compilation step) takes a lot longer the smaller the tolerance is
and the compiled files will be a lot larger.

We recommend using a starting value of 1000m and only reducing this if needed.

## Speed rules configuration

TODO DOCUMENT SETTING BY ROAD TYPE, AND PARENT-CHILD RULES

## Visualising regions in ODL Studio
TODO DOCUMENT THIS

## Future developments

* Full integration into Graphhopper project

* Binary format for compiled tree - the large size of the compiled tree can limit the practicallity of speed regions
	to national level. The file size could be reduced dramatically without much work (for example, the compressed file size of the JSON is just ~3% of the uncompressed).