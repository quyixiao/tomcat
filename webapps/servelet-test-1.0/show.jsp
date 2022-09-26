
<%@ page contentType="text/html; charset=GBK" language="java" errorPage="" %>
<%@ page import="java.util.*" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>迭代器tag file</title>
    <meta name="website" content="http://www.linjw.org" />
</head>
<body>
<h2>迭代器tag file</h2>
<%
    // 创建集合对象，用于测试Tag File所定义的标签
    List<String> a = new ArrayList<String>();
    a.add("疯狂Java讲义");
    a.add("轻量级Java EE企业应用实战");
    a.add("疯狂Ajax讲义");
    // 将集合对象放入页面范围
    request.setAttribute("a" , a);
%>
<h3>使用自定义标签</h3>
<tags:iterator bgColor="#99dd99" cellColor="#9999cc"
               title="迭代器标签" bean="a" />
</body>
</html>
