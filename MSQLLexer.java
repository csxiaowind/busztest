package com.ct.expr;

import java.util.*;

public class MSQLLexer {
	private int curidx = 0;
	
	private String select = null;
	private String from = null;
	private String groupby = null;	
	
	public String where = null;	
	public LinkedHashMap<String, String> selectMap = new LinkedHashMap();
	public LinkedHashMap<String, String> fromMap = new LinkedHashMap();
	public List<String> groupbyList = null; //null:不分组,  无记录:尾表分组, 有记录:分组 
	
	private String find(String sql, String keyword) throws Exception {
		int idxstart = sql.indexOf(keyword, curidx);
		if (idxstart <0)
			return null;
		
		idxstart = idxstart + keyword.length();
		idxstart = sql.indexOf('{', idxstart);
		if (idxstart <0)
			return null;
			
		    //dev的改动
			//master comment
		
		idxstart = idxstart + 1;
		int idxend = sql.indexOf('}', idxstart);
		if (idxend <0)
			return null;
		
		String result = sql.substring(idxstart, idxend);
		curidx = idxend + 1;	
		
		return result;
	}
	
	public MSQLLexer(String sql) throws Exception {
		select = find(sql, "select");
		if (null == select) 
			throw new Exception("no select block");

		from = find(sql, "from");
		if (null == from) 
			throw new Exception("no from block");
		
		where = find(sql, "where");
		if (null == where) 
			throw new Exception("no where block");
		where = where.trim();
		
		groupby = find(sql, "groupby");
		
		analyzeSelect();
		analyzeFrom();
		analyzeGroupby();
	}
	
	private void analyzeSelect() throws Exception {	
		String[] lines = select.split("\n");
		
		for (int i=0; i<lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() == 0)
				continue;
			
			int idx = line.indexOf('=');
			if (idx<0) {
				selectMap.put(line, "FV()");
				continue;
			}
			
			String name = line.substring(0, idx).trim();
			String value = line.substring(idx+1).trim();
			selectMap.put(name, value);
		}	
	}
	
	private void analyzeFrom() throws Exception {	
		String[] lines = from.split("\n");
		
		for (int i=0; i<lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() == 0)
				continue;
			
			int idx = line.indexOf('=');
			if (idx<0) {
				throw new Exception("from block invalid!");
			}
			
			String name = line.substring(0, idx).trim();
			String value = line.substring(idx+1).trim();
			fromMap.put(name, value);
		}	
	}
	
	private void analyzeGroupby() throws Exception {
		if (null == groupby)
			return;
		
		if (groupby.trim().equals("_TAIL_")) {
			groupbyList = new ArrayList();
		}
			
		String[] fieldnames = groupby.split("\\,");
		
		for (int i=0; i<fieldnames.length; i++) {
			String fieldname = fieldnames[i].trim();
			groupbyList.add(fieldname);
		}	
	}
	
	public List getExprList() {
		List result = new ArrayList();
		
		for (String key: selectMap.keySet()) {
			result.add(selectMap.get(key));
		}
		for (String key: fromMap.keySet()) {
			result.add(fromMap.get(key));
		}
		
		result.add(where);
		return result;
	}
	
}	

