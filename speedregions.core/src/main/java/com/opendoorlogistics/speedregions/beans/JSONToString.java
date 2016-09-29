package com.opendoorlogistics.speedregions.beans;

import com.opendoorlogistics.speedregions.TextUtils;

public abstract class JSONToString {

	@Override
	public String toString(){
		return TextUtils.toJSON(this);
	}
}
