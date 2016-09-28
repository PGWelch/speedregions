package com.opendoorlogistics.speedregions.beans.files;

import java.util.ArrayList;
import java.util.List;

import com.opendoorlogistics.speedregions.beans.JSONToString;
import com.opendoorlogistics.speedregions.beans.SpeedRule;

public abstract class AbstractSpeedRulesFile extends JSONToString {
	private List<SpeedRule> rules = new ArrayList<SpeedRule>();

	public AbstractSpeedRulesFile() {
		super();
	}

	public List<SpeedRule> getRules() {
		return rules;
	}

	public void setRules(List<SpeedRule> rules) {
		this.rules = rules;
	}

}