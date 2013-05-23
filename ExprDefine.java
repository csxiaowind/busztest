package com.ct.expr;

import java.util.*;

import groovy.lang.*;

public class ExprDefine {
	public Map table = new HashMap();
	public Class scriptClazz = null;
	String text = null;
		
	public String getText() {
		return text;
	}
	
	public static boolean isExpression(String line) {
		if (null == line || "null".equals(line.trim()))
			return false;
		if ("true".equals(line)) {  //true和false会误判成变量名
			return false;
		}
		if ("false".equals(line)) {  //true和false会误判成变量名
			return false;
		}
		if ( line.matches("[a-zA-Z_]+(\\.[a-zA-Z_]+)?")) {
			return false;
		}	
		
		return true;
	}
		
	public ExprDefine(List exprlist, String runtext) {
		text = createExprText(exprlist, this.table);
		text = runtext + text;
		GroovyShell shell = new GroovyShell(new Binding());
		//System.out.println(text);
		this.scriptClazz = shell.parse(text).getClass();
	}
	
	public ExprDefine(List exprlist) {
		String text = createExprText(exprlist, this.table);
		GroovyShell shell = new GroovyShell(new Binding());
		this.scriptClazz = shell.parse(text).getClass();
	}
	
	public static String createExprText(List exprlist, Map dest) {
		dest.clear();
		
		String margin = "    ";
		StringBuffer buf = new StringBuffer();
		buf.append("def ExprDefine_Expression(int ExprDefine_Expression_Index) {\n");
		buf.append("switch (ExprDefine_Expression_Index) {\n");
				
		//int index = 0;
		for (int i=0; i<exprlist.size(); i++) {
			String expr = (String) exprlist.get(i);
			dest.put(expr, i);	//在对照表中生成序号
			buf.append(margin).append("case ").append(i).append(": ");
			buf.append(" ").append(expr).append("\n").append(margin).append(margin).append("break;\n");
		 }
		
		//不在序号内则报错
		buf.append(margin).append("default:").append("throw new Exception('未定义该表达式序号'+ExprDefine_Expression_Index);\n");
		
		buf.append("}\n");
		buf.append("}\n");
		
		//System.out.println(buf);
		return buf.toString();
	}	
	
	public ExprRegister newRegister() throws Exception {
		return new ExprRegister(scriptClazz, table);
	}
}
