package com.ct.expr;


import java.util.*;

import org.apache.log4j.Logger;
import java.math.BigDecimal;

public class MSQLExecuter {	
	ExprRegister context = null;
	
	LinkedHashMap<String, String> outdefs;                //����ֶ���������ֶα��ʽ
	LinkedHashMap<String, List> tables;                  //����,���Ӧ��List����
	List<String> groupfields;                            //ÿһ��Ϊ �����  ����.�ֶ���
	String whereexpression;                              //�������ʽ

	List<String> tabname_list = new ArrayList(); //�����б�,����ʱ���г�ʼ��
	List<String> outname_list = new ArrayList(); //����ֶ����б�����ʱ��ʼ��
	int GroupTag = 0;         //�����־, 0: ������  1: ����
	String lasttabname = null;
	
	List output = new ArrayList();
	
	//��һ��ɨ����ʹ�õı���
	HashMap cur_line = new HashMap();                          //��ʱ��¼��ǰ��������
	int cur_rownum = 0;
	Map lgp_cache = null;									   //��ǰβ����Ϣ����
	int lgp_rownum = 0;                                        //��ǰβ���к�
	Map<String, Map> grp_line = new LinkedHashMap();           //����������,ֻ�����������:(id,������)����GroupTag=2ʱʹ��
	Map<String, List> grp_group = new HashMap();               //�����嵥:(id,���б�), Ȩ��GroupTag=2ʱʹ��
	
	//��������£��ڶ���ɨ����ʹ�õı���
	String cur_grp_id = null;          //��ǰ����id, Ȩ��GroupTag=1ʱʹ��

	//createOutʱ��ʹ�õı���
	String cur_fullname = null;        //��ǰ�ֶ���
	String cur_expr_line = null;       //��ǰ���ʽ,���ڵ��Ի򱨴�
	
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
		
		//ȷ����������
		if (null==groupfields) {
			this.GroupTag = 0;
		} else {
			this.GroupTag = 1;
		}
		
		this.groupfields = groupfields;
		this.context = context;
	}
	
	
	private void dcar_recurse(int tabindex) throws Exception {
		String tabname = tabname_list.get(tabindex);		//���õ�ǰ����
		List tab = tables.get(tabname);
			
		//β����
		if (tabindex == tabname_list.size()-1) {
			lgp_cache = new HashMap();
			lgp_rownum = 0;
		}
		
		for (int i=0; i<tab.size(); i++) {	
			Object rowdata = tab.get(i);
			context.register(tabname, rowdata);	    //����ĵ�ǰ�еǼ�Ϊ����
			cur_line.put(tabname, rowdata);
						
			if (tabindex == tabname_list.size()-1) {	    //���ڲ㣬ִ��
				//System.out.println(cur_line);
				if (context.evalBool(whereexpression))
					dcar_execute((Map) cur_line.clone());
				continue;
			} else {
				dcar_recurse(tabindex+1);
			}
		}		
	}
	
	//���ɵѿ�����
	private void dcar_execute(Map line) throws Exception {
		if (0 == GroupTag) {	//������
			cur_rownum ++;
			lgp_rownum ++;
			createOut();
		} else if (1 == GroupTag) {
			grp_Execute(line);
		} else {
			throw new Exception("GroupTag��Ч:"+GroupTag);
		}
	}
	
	public List execute() throws Exception {
		dcar_recurse(0);		//���ɵѿ������������� decar_list ��
		
		if (0 == GroupTag) {
			return output;       //ֱ�ӷ��ؽ��
		} 

		if (1 == GroupTag) {
			//ע������
			for (int k=0; k<this.tabname_list.size(); k++) {
				String tabname = tabname_list.get(k);
				context.delete(tabname);
			}
			
			for (String id: grp_group.keySet()) {
				cur_rownum ++;
				cur_grp_id = id;
				
				//ע�����
				Map m = this.grp_line.get(id);   //������
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
				throw new Exception("����"+outname+"�ظ�");
			outmap.put(outname, outvalue);
		}
		output.add(outmap);
	}
	
	//ȡdcar����ĳ�е�id, ������id��������б�
	private void grp_Execute(Map<String, Map> line) throws Exception {
		StringBuffer idbuf = new StringBuffer();
		Map<String, Map> m = new HashMap();  //m:�Ľṹ(����, �ñ��Ӧ��һ��(������)����)
		for (int k=0; k<groupfields.size(); k++) {
			String fullname = groupfields.get(k);
			int idx = fullname.indexOf('.');
			if (idx<=0)
				throw new Exception("������ֶ������������["+fullname+']');
			
			String tname = fullname.substring(0, idx);	//����
			String fname = fullname.substring(idx+1);  //�ֶ���
			Map rowdata = line.get(tname);      //�ñ��������
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
		
		//������Ϣ������Ϣ����
		if (grp_group.containsKey(id)) {
			List<Map> grp = grp_group.get(id);	
			grp.add(line);					//�����Ѵ��ڣ����ڸ÷���List�м����������
		} else {
			List<Map> grp = new ArrayList();
			grp.add(line);
			grp_group.put(id, grp);        //���������Ϣ
			grp_line.put(id, m);           //�¼ӷ����ͷ��Ϣ
		}
	}
	
	//ȡ��ǰ�ֶ�ֵ
	public Object FV() throws Exception {
		if (cur_fullname.indexOf('.') < 1) 
			throw new Exception("���ܻ�ȡ��ǰ�ֶ�ֵ���ֶ���:["+cur_fullname+"]");
		
		return context.get(cur_fullname);	
	}
	
	public Object FN() throws Exception {
		return cur_fullname;
	}
	
	//β�����,�ֶ��ۼ�
	public BigDecimal LG_ADD(String tfname) throws Exception {
		if (2 == GroupTag)
			throw new Exception("LG_ADD�������ڸ÷��鷽ʽ");
		
		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//����
		String fname = tfname.substring(idx+1);		//�ֶ���
		
		if (! tname.equals(lasttabname)) {
			throw new Exception("��״̬����LG_ADD("+tname+")����������һ������!");
		}
		
		BigDecimal result = null;
		
		//����������ֵ
		String lgid = "LG_ADD|"+fname+"|"+this.lgp_rownum;
		if (1 == lgp_rownum) {//��һ�� 
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
			throw new Exception("LG_MONEYDIV�������ڸ÷��鷽ʽ");
		if (null == sour || null == total)
			throw new Exception("���������ʱ��ǰ�����ĸ����Ϊ��" + "["+cur_fullname+"  :  "+cur_expr_line+"]");
		
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
			throw new Exception("GP_SUM�������ڸ÷��鷽ʽ");
		
 		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//����
		String fname = tfname.substring(idx+1);		//�ֶ���
		if ( 1 > idx) {
			throw new Exception("ͳ�ƺ���GP_SUM("+tname+")����ָ������!");
		}
		
		//ȡ��ǰ����
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
			throw new Exception("GP_CNT�������ڸ÷��鷽ʽ");
		
 		int idx = tfname.indexOf('.');
		String tname = tfname.substring(0, idx);	//����
		String fname = tfname.substring(idx+1);		//�ֶ���
		if ( 1 > idx) {
			throw new Exception("ͳ�ƺ���GP_SUM("+tname+")����ָ������!");
		}
		
		//ȡ��ǰ����
		List grp = this.grp_group.get(this.cur_grp_id);
		
		return grp.size();
	}
	
}

