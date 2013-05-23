package com.ct.expr;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.util.HashMap;
import java.util.Map;

public class ExprRegister {
	private Map table = null;
	private Script script = null;
	private Map scriptvars = null; 
			
	public void clearVar() {
		script.getBinding().getVariables().clear();
	}
	
	public ExprRegister(Class scriptClazz, Map table) throws Exception {
		this.script = (Script) scriptClazz.newInstance();
		this.scriptvars = script.getBinding().getVariables();
		this.table = table;
	}
		
	public void register(String name, Object value) {
		int index = name.indexOf('.');
		if (index < 0) {
			scriptvars.put(name, value);
			return;
		}
		
		String lname = name.substring(0, index);
		String rname = name.substring(index+1);
		Map x = (Map) scriptvars.get(lname);
		if (null == x) {
			x = new HashMap();
			x.put(rname, value);
			scriptvars.put(lname, x);
		} else {
			x.put(rname, value);	
		}
	}
	
	public void delete(String name) {
		scriptvars.remove(name);
	}
	
	
	public void registerAll(Map m) {
		scriptvars.putAll(m);
	}	
	
	public Map getMap(String name) throws Exception {
		Map m = (Map) scriptvars.get(name);
		if (null != m)
			return m;
		throw new Exception("未找到Map对象:"+name);
	}
	
	public Object get(String name) throws Exception {
		//return varMap.get(name);
		int index = name.indexOf('.');
		if (index < 0) {
			return scriptvars.get(name);
		}
		
		String lname = name.substring(0, index);
		String rname = name.substring(index+1);
		Map lobj = getMap(lname);
		if (null == lobj) 
			throw new Exception("未找到对象:"+lname);
		Map m = getMap(lname);
		Object value = m.get(rname);
		if (null != value)
			return value;
		if (m.containsKey(rname))
			return value;
		throw new Exception("对象"+lname+"中没有"+rname+"属性");
	}
	
	public  boolean varExists(String name) throws Exception {
		//return varMap.get(name);
		int index = name.indexOf('.');
		if (index < 0) {
			return scriptvars.containsKey(name);
		}
		
		
		String lname = name.substring(0, index);
		String rname = name.substring(index+1);
		if (!scriptvars.containsKey(lname)) 
			return false;
		
		Map m = getMap(lname);
		return m.containsKey(rname);
	}
	
	
	public Object eval(String line) throws Exception {
		if (null != line)
			line = line.trim();
		if (null == line || "null".equals(line.trim()))
			return null;
		if ("true".equals(line)) {  //true和false会误判成变量名
			return new Boolean(true);
		}
		if ("false".equals(line)) {  //true和false会误判成变量名
			return new Boolean(false);
		}
		if ( line.matches("[a-zA-Z_]+(\\.[a-zA-Z_]+)?")) { //提速优化语句
			//System.out.println("--------line=["+line+"]");
			return this.get(line);
		}
		//System.out.println("fdd:"+line);
		return eval_exprid(line);
	}	
	
	public Object eval_exprid(String exprid) throws Exception {
		if (! table.containsKey(exprid)) {
			throw new Exception("未注册的表达式:"+exprid);
		}
		
		int index = ((Integer) table.get(exprid)).intValue();
		//System.out.println("calc..."+exprid+",id="+index);
		script.setProperty("ExprDefine_Expression_Index", index);
		
		Object o = script.invokeMethod("ExprDefine_Expression", new Object[]{new Integer(index)});
		//System.out.println("resut="+o);
		return o;
	}
	
	public boolean evalBool(String line) throws Exception {
		Boolean b = (Boolean) eval(line);
		return b.booleanValue();
	}
	
	public Map getVars() {
		return scriptvars;
	}
	
	public Object forceEval(String line) throws Exception {
		return script.evaluate(line);
	}
	
	public Object runScript() {
		return script.run();
	}

}
