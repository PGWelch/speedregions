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
package com.opendoorlogistics.speedregions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.opendoorlogistics.speedregions.beans.SpeedRule;
import com.opendoorlogistics.speedregions.beans.SpeedUnit;
import com.opendoorlogistics.speedregions.beans.files.AbstractSpeedRulesFile;
import com.opendoorlogistics.speedregions.utils.TextUtils;
import com.rits.cloning.Cloner;

public class SpeedRulesProcesser {
	
	public List<SpeedRule> validateSpeedRules(List<? extends AbstractSpeedRulesFile> files) {
		final HashMap<String, SpeedRule> ruleIds = new HashMap<>();
		ArrayList<SpeedRule> allRules = new ArrayList<>();
		for (AbstractSpeedRulesFile rules : files) {
			if (rules.getRules() == null) {
				continue;
			}

			for (SpeedRule rule : rules.getRules()) {

				// check id is unique if used
				if (rule.getId() != null) {
					String stdId = TextUtils.stdString(rule.getId());
					if (ruleIds.containsKey(stdId)) {
						throw new RuntimeException("Duplicate rule id: " + stdId);
					}
					ruleIds.put(stdId, rule);
				}
				allRules.add(rule);
			}
		}

		class ParentValidator {
			HashSet<String> found = new HashSet<>();

			void validate(String parentId) {
				parentId = TextUtils.stdString(parentId);
				if (found.contains(parentId)) {
					throw new RuntimeException("Found circular dependencies in rule parent ids around rule id " + parentId);
				}
				found.add(parentId);

				SpeedRule parent = ruleIds.get(parentId);
				if (parent == null) {
					throw new RuntimeException("Cannot find parent rule with id " + parentId);
				}

				if (parent.getParentId() != null) {
					validate(parent.getParentId());
				}
			}
		}

		// validate parent ids
		for (SpeedRule rule : allRules) {
			if (rule.getParentId() != null) {
				new ParentValidator().validate(rule.getParentId());
			}
		}

		// this also does some validation
		createSelfContainedRulesMap(allRules);

		return allRules;
	}

	/**
	 * Create a map which lets you lookup the speed rule by encoder first and then by regiontype. All string keys are
	 * standardised. Parent id relations are removed so the rules are self-contained
	 * 
	 * @param files
	 * @return
	 */
	public TreeMap<String, TreeMap<String, SpeedRule>> createSelfContainedRulesMap(List<SpeedRule> rules) {
		// build id map and clone rules
		rules = new ArrayList<>(rules);
		final HashMap<String, SpeedRule> originalRules = new HashMap<>();
		int n = rules.size();
		for(int i =0 ; i <n;i++){
			
			// save to original map first
			SpeedRule rule =rules.get(i);
			if(rule.getId()!=null){
				originalRules.put(TextUtils.stdString(rule.getId()), rule);
			}
			
			// then clone and standardise road type strings
			SpeedRule cloned= Cloner.standard().deepClone(rule);
			if(rule.getSpeedsByRoadType()!=null){
				cloned.getSpeedsByRoadType().clear();
				for(Map.Entry<String, Float> entry : rule.getSpeedsByRoadType().entrySet()){
					cloned.getSpeedsByRoadType().put(TextUtils.stdString(entry.getKey()), entry.getValue());
				}					
			}
			rules.set(i, cloned);
		}

		// collapse parent relations
		for(SpeedRule rule : rules){
			if(rule.getSpeedsByRoadType()==null){
				rule.setSpeedsByRoadType(new TreeMap<String, Float>());
			}
			
			String parentId = rule.getParentId();
			while(parentId!=null){
				
				// standardise parent string and find parent (should already be validated so should exist)
				parentId = TextUtils.stdString(parentId);
				SpeedRule originalParent = originalRules.get(parentId);
	
				// combine speeds by type, including our current multiplier
				if(originalParent.getSpeedsByRoadType()!=null){
					for(Map.Entry<String, Float> entry : originalParent.getSpeedsByRoadType().entrySet()){
						String type = entry.getKey();
						type = TextUtils.stdString(type);
						if(!rule.getSpeedsByRoadType().containsKey(type)){
							double parentSpeedInParentUnits = entry.getValue();
							double parentSpeedInChildUnits = SpeedUnit.convert(parentSpeedInParentUnits, originalParent.getSpeedUnit(), rule.getSpeedUnit());
							double childSpeed= parentSpeedInChildUnits * rule.getMultiplier();
							rule.getSpeedsByRoadType().put(type,(float)childSpeed);
						}
					}					
				}
	
				// update multiplier with the parent multiplier
				rule.setMultiplier(rule.getMultiplier() * originalParent.getMultiplier());
				
				// go to next parent
				parentId = originalParent.getParentId();
			}
			
			// blank parent id now we've taken all its information
			rule.setParentId(null);
		}
		
		TreeMap<String, TreeMap<String, SpeedRule>> ret = new TreeMap<String, TreeMap<String, SpeedRule>>();
		for (SpeedRule rule : rules) {
			for (String encoder : rule.getMatchRule().getFlagEncoders()) {
				encoder = TextUtils.stdString(encoder);
				TreeMap<String, SpeedRule> map4Encoder = ret.get(encoder);
				if (map4Encoder == null) {
					map4Encoder = new TreeMap<String, SpeedRule>();
					ret.put(encoder, map4Encoder);
				}

				for (String regionType : rule.getMatchRule().getRegionTypes()) {
					regionType = TextUtils.stdString(regionType);
					SpeedRule prexisting = map4Encoder.get(regionType);
					if (prexisting != null) {
						throw new RuntimeException("More than one speed rule exists for encoder type " + encoder + " and "
								+ SpeedRegionConsts.REGION_TYPE_KEY + " + " + regionType + ".");
					}
					map4Encoder.put(regionType, rule);
				}
			}
		}

		return ret;
	}



}
