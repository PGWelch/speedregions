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


public abstract class AbstractCommand {
	private final String description;
	private final String []keywords;
	

	
	public AbstractCommand(String description, String ...keywords) {
		this.description = description;
		this.keywords = keywords;
	}
	
	public String [] getKeywords(){
		return keywords;
	}
	
	public String getDescription() {
		return description;
	}

	public abstract void execute(String []args,State state);
}
