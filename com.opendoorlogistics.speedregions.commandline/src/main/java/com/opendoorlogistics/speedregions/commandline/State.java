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
package com.opendoorlogistics.speedregions.commandline;

import java.util.Arrays;

import com.opendoorlogistics.speedregions.SpeedRegionCompiler;
import com.opendoorlogistics.speedregions.SpeedRegionConsts;
import com.opendoorlogistics.speedregions.beans.CompiledSpeedRegions;
import com.opendoorlogistics.speedregions.beans.SpeedRulesFile;

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
public class State {
	SpeedRulesFile rules = new SpeedRulesFile();
	CompiledSpeedRegions compiled;
	double minCellLength = SpeedRegionConsts.DEFAULT_MIN_CELL_LENGTH_METRES;
	
	void compile(){
		compiled = SpeedRegionCompiler.buildBeanFromSpeedRulesObjs(Arrays.asList(rules), minCellLength);		
	}
	
	void compileIfNull(){
		if(compiled==null){
			compile();
		}
	}
}
