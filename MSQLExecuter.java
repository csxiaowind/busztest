package com.ct.expr;


import java.util.*;

import org.apache.log4j.Logger;
import java.math.BigDecimal;

public class MSQLExecuter {	
	ExprRegister context = null;
	
	LinkedHashMap<String, String> outdefs;                //输出字段名，输出字段表达式
	LinkedHashMap<String, List> tables;                  //表名,表对应的List对象
	List<String> groupfields;                            //每一行为 分组的  表名.字段名
	String whereexpression;                              //条件表达式

	List<String> tabname_list = new ArrayList(); //表名列表,构造时进行初始化
	List<String> outname_list = new ArrayList(); //输出字段名列表，构造时初始化
	int GroupTag = 0;         //分组标志, 0: 不分组  1: 分组
	String lasttabname = null;
	
	List output = new ArrayList();
	
	//第一轮扫描所使用的变量
	HashMap cur_line = new HashMap();                          //临时记录当前各表的情况
	int cur_rownum = 0;
	Map lgp_cache = null;									   //当前尾表信息缓存
	int lgp_rownum = 0;                                        //当前尾表行号
	Map<String, Map> grp_line = new LinkedHashMap();           //分组行数据,只包括分组变量:(id,行数据)，仅GroupTag=2时使用
	Map<String, List> grp_group = new HashMap();               //分组清单:(id,组列表), 权在GroupTag=2时使用
	
	//分组情况下，第二轮扫描所使用的变量
	String cur_grp_id = null;          //当前分组id, 权在GroupTag=1时使用

	//createOut时所使用的变量
	String cur_fullname = null;        //当前字段名
	String cur_expr_line = null;       //当前表达式,用于调试或报错
	
	public MSQLExecuter(LinkedHashMap outdefs, LinkedHashMap<String, List> tables, String whereexpression, List<String> groupfields, ExprRegister context) {
		this.context = context;
		this.outdefs = outdefs;
		this.tables = tables;
		this.whereexpression = whereexpression;

		for (String key: this.tables.keySet() )
			tabname_list.add(key);
		for (String key: this.outdefs.keySet() )
			outname_list.add(key);
		
		lasttabname = tabname_list.get(tabname_list.size()-1);
		
		//确定分组类型
		if (null==groupfields) {
			this.GroupTag = 0;
		} else {
			this.GroupTag = 1;
		}
		
		this.groupfields = groupfields;
		this.context = context;
	}
	
	
	private void dcar_recurse(int tabindex) throws Exception {
		String tabname = tabname_list.get(tabindex);		//设置当前表名
		List tab = tables.get(tabname);
			
		//尾表缓冲
		if (tabindex == tabname_list.size()-1) {
			lgp_cache = new HashMap();
			lgp_rownum = 0;
		}
		
		for (int i=0; i<tab.size(); i++) {	
			Object rowdata = tab.get(i);
			context.register(tabname, rowdata);	    //将表的当前行登记为别名
			cur_line.put(tabname, rowdata);
						
			if (tabindex == tabname_list.size()-1) {	    //最内层，执行
				//System.out.println(cur_line);
				if (context.evalBool(whereexpression))
					dcar_execute((Map) cur_line.clone());
				continue;
			} else {
				dcar_recurse(tabindex+1);
			}
		}		
	}
	
	//生成笛卡尔积
	private void dcar_execute(Map line) throws Exception {
		if (0 == GroupTag) {	//不分组
			cur_rownum ++;
			lgp_rownum ++;
			createOut();
		} else if (1 == GroupTag) {
			grp_Execute(line);
		} else {
			throw new Exception("GroupTag无效:"+GroupTag);
		}
	}
	
	public List execute() throws Exception {
		dcar_recurse(0);		//生成笛卡尔积，保存在 decar_list 中
		
		if (0 == GroupTag) {
			return output;       //直接返回结果
		} 

		if (1 == GroupTag) {
			//注销别名
			for (int k=0; k<this.tabname_list.size(); k++) {
				String tabname = tabname_list.get(k);
				context.delete(tabname);
			}
			
			for (String id: grp_group.keySet()) {
				cur_rownum ++;
				cur_grp_id = id;
				
				//注册变量
				Map m = this.grp_line.get(id);   //行数据
				context.registerAll(m);
				
				createOut();
			}	
			return output;
		}
			
		throw new Exception("not supported GroupTag:"+GroupTag);
	}
	
	private void createOut() throws Exception {
		Map outmap = new LinkedHashMap();
		for (int i=0; i<outname_list.size(); i++) {
			String name = outname_list.get(i);
			cur_fullname = name;
			//cur_fieldname = null;
			String outname = name;
			Object outvalue = null;
			
			int nidx = name.indexOf('.');
			if (nidx>=0) {
				outname = name.substring(nidx+1);
			}
			
			String line = (String) outdefs.get(name);
			cur_expr_line = line;
			outvalue = context.eval(line);
			
			if (outmap.containsKey(outname))
				throw new Exception("名字"+outname+"重复");
			outmap.put(outname, outvalue);
		}
		output.add(outmap);
	}
	
	//取dcar积中某行的id, 并根据id处理分组列表
	private void grp_Execute(Map<String, Map> line) throws Exception {
		StringBuffer idbuf = new StringBuffer();
		Map<String, Map> m = new HashMap();  //m:的结构(表名, 该表对应的一行(分组内)数据)
		for (int k=0; k<groupfields.size(); k++) {
			String fullname = groupfields.get(k);
			int idx = fullname.indexOf('.');
			if (idx<=0)
				throw new Exception("分组的字段名必须带别名["+fullname+']');
			
			String tname = fullname.substring(0, idx);	//表名
			String fname = fullname.substring(idx+1);  //字段名
			Map rowdata = line.get(tname);      //该表的行数据
			Object value = rowdata.get(fname); 
			
			if (m.containsKey(tname)) {
				Map x = m.get(tname);
				x.put(fname, value);
			} else {
				Map x = new HashMap();
				x.put(fname, value);
				m.put(tname, x);
			}
			idbuf.append(k).append(':').append(value).append('|');
		}

		String id = idbuf.toString();
		
		//分组信息及行信息设置
		if (grp_group.containsKey(id)) {
			List<Map> grp = grp_group.get(id);	
			grp.add(line);					//分组已存在，则在该分组List中加入该行数据
		} else {
			List<Map> grp = new ArrayList();
			grp.add(line);
			grp_group.put(id, grp);        //加入分组信息
			grp_line.put(id, m);           //新加分组表头信息
		}
	}
	
	//取当前字段值
	public Object FV() throws Exception {
		if (cur_fullname.indexOf('.') < 1) 
			throw new Exception("不能获取当前字段值。字段名:["+cur_fullname+"]");
		
		return context.get(cur_fullname);	
	}
	
	public Object FN() throws Exception {
		return cur_fullname;
	}
	
	//尾表分组,字段累加
	public BigDecimal LG_ADD(String tfname) throws Exception {
		if (2 == GroupTag)
			throw new Exception("LG_ADD不能用于该分组方式");
		
		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//表名
		String fname = tfname.substring(idx+1);		//字段名
		
		if (! tname.equals(lasttabname)) {
			throw new Exception("有状态函数LG_ADD("+tname+")必须针对最后一个表名!");
		}
		
		BigDecimal result = null;
		
		//本函数索引值
		String lgid = "LG_ADD|"+fname+"|"+this.lgp_rownum;
		if (1 == lgp_rownum) {//第一条 
			result = (BigDecimal) context.eval(tfname);
			lgp_cache.put(lgid, result);
		} else if (lgp_cache.containsKey(lgid)) {
			result = (BigDecimal) lgp_cache.get(lgid);
		} else {
			String lastid = "LG_ADD|"+fname+"|"+(this.lgp_rownum-1);
			result = ((BigDecimal) context.eval(tfname)).add((BigDecimal) lgp_cache.get(lastid));
		}
		
		return result;
	}

	public BigDecimal LG_MDIV(BigDecimal sour, String tfname, BigDecimal total) throws Exception {
		if (1 == GroupTag)
			throw new Exception("LG_MONEYDIV不能用于该分组方式");
		if (null == sour || null == total)
			throw new Exception("按比例拆分时当前金额或分母金额不能为空" + "["+cur_fullname+"  :  "+cur_expr_line+"]");
		
		BigDecimal added = LG_ADD(tfname);
		BigDecimal fvalue = (BigDecimal) context.eval(tfname);
		
		BigDecimal m1 = added.multiply(sour).divide(total, 2, BigDecimal.ROUND_HALF_UP);
		BigDecimal m2 = added.subtract(fvalue).multiply(sour).divide(total, 2, BigDecimal.ROUND_HALF_UP);
//		Map temp = new LinkedHashMap();
//		temp.put("total", total);
//		temp.put("sour", sour);
//		temp.put("added", added);
//		temp.put("fvalue", fvalue);
//		temp.put("m1", m1);
//		temp.put("m2", m2);
//		temp.put("result", m1.subtract(m2));
//		System.out.println(temp);
		return m1.subtract(m2);
	}
	
	public BigDecimal GP_SUM(String tfname) throws Exception {
		if (1 != GroupTag)
			throw new Exception("GP_SUM不能用于该分组方式");
		
 		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//表名
		String fname = tfname.substring(idx+1);		//字段名
		if ( 1 > idx) {
			throw new Exception("统计函数GP_SUM("+tname+")必须指定别名!");
		}
		
		//取当前分组
		List grp = this.grp_group.get(this.cur_grp_id);
		
		BigDecimal result = new BigDecimal("0.00");
		for (int i=0; i<grp.size(); i++) {
			Map line = (Map) grp.get(i);
			Map rowdata = (Map) line.get(tname);
			BigDecimal val = (BigDecimal) rowdata.get(fname);
			result = result.add(val);
		}
		
		return result;
	}

	public int GP_CNT(String tfname) throws Exception {
		if (2 != GroupTag)
			throw new Exception("GP_CNT不能用于该分组方式");
		
 		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//表名
		String fname = tfname.substring(idx+1);		//字段名
		if ( 1 > idx) {
			throw new Exception("统计函数GP_SUM("+tname+")必须指定别名!");
		}
		
		//取当前分组
		List grp = this.grp_group.get(this.cur_grp_id);
		
		return grp.size();
	}
	
}

