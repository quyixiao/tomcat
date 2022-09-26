package com.luban;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyTaglib implements Tag {
 
    private PageContext pageContext;
    private Tag parent;
 
    public MyTaglib() {
        super();
    }
 
    @Override
    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }
 
    @Override
    public void setParent(Tag tag) {
        this.parent = tag;
    }
 
    @Override
    public Tag getParent() {
        return this.parent;
    }
 
    @Override
    public int doStartTag() throws JspException {
        //返回 SKIP_BODY，表示不计算标签体
        return SKIP_BODY;
    }
 
    @Override
    public int doEndTag() throws JspException {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();// 获取当前时间
            pageContext.getOut().write(sdf.format(date));
        } catch (IOException e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
 
    @Override
    public void release() {
 
    }
}