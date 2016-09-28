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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.geojson.Feature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Miscellaneous utils used to create the region lookup
 * @author Phil
 *
 */
public class TextUtils {
	private static final ObjectMapper JACKSON_MAPPER;
	static {
		JACKSON_MAPPER = new ObjectMapper();
		JACKSON_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		JACKSON_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
	}

//	public static void toJSONFile(Object o, File file){
//		String json = toJSON(o);
//		try {
//			internalStringToFile(file, json);			
//		} catch (Exception e) {
//			throw asUncheckedException(e);
//		}
//	}
//	
	public static void toJSONFile(Object o, File textfile){
		try {
			JACKSON_MAPPER.writeValue(textfile, o);
		} catch (Exception e) {
			throw asUncheckedException(e);
		}
	}

	public static void stringToFile(File file, String s) throws FileNotFoundException {
		try(  PrintWriter out = new PrintWriter(file )  ){
		    out.println( s );
		}
	}
	
	public static String toJSON(Object o) {
		StringWriter writer = new StringWriter();
		try {
			JACKSON_MAPPER.writeValue(writer, o);
		} catch (Exception e) {
			throw asUncheckedException(e);
		}
		return writer.toString();
	}
	
	public static RuntimeException asUncheckedException(Throwable e){
		if(RuntimeException.class.isInstance(e)){
			return (RuntimeException)e;
		}
		return new RuntimeException(e);
	}
	

	public static <T> T fromJSON(File textFile, Class<T> cls) {

		try {
			return JACKSON_MAPPER.readValue(textFile, cls);
		} catch (Exception e) {
			
			throw asUncheckedException(e);
		}		
	}
	

	
	public static <T> T fromJSON(String json, Class<T> cls) {

		try {
			return JACKSON_MAPPER.readValue(json, cls);
		} catch (Exception e) {
			
			throw asUncheckedException(e);
		}

	}
	
	public static String stdString(String s){
		if(s==null){
			return "";
		}
		return s.toLowerCase().trim();
	}

	public static String readTextFile(File file){
		try {
			return internalReadTextFile(file);
		} catch (Exception e) {
			throw asUncheckedException(e);
		}
	}
	
	private static String internalReadTextFile(File file)throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(file));
		try {
		    StringBuilder builder = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
		        builder.append(line);
		        builder.append(System.lineSeparator());
		        line = br.readLine();
		    }
		    return builder.toString();
		} finally {
		    br.close();
		}
		
	}
	
	
	/**
	 * Finds regionid. Doesn't standardise it.
	 * @param feature
	 * @return
	 */
	public static String findRegionType(Feature feature ){
		return findProperty(feature, SpeedRegionConsts.REGION_TYPE_KEY);
	}
	
	/**
	 * Finds regionid. Doesn't standardise it.
	 * @param feature
	 * @return
	 */
	public static String findProperty(Feature feature , String key){
		key = stdString(key);
		for (Map.Entry<String, Object> entry : feature.getProperties().entrySet()) {
			if (entry.getValue() != null && key.equals(stdString(entry.getKey())) ) {
				return entry.getValue().toString();
			}
		}
		return null;
	}
	
}
