
<%@ page contentType="text/html; charset=GBK" language="java" errorPage="" %>
<%@ page import="java.util.*" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <title>������tag file</title>
    <meta name="website" content="http://www.linjw.org" />
</head>
<body>
<h2>������tag file</h2>
<%
    // �������϶������ڲ���Tag File������ı�ǩ
    List<String> a = new ArrayList<String>();
    a.add("���Java����");
    a.add("������Java EE��ҵӦ��ʵս");
    a.add("���Ajax����");
    // �����϶������ҳ�淶Χ
    request.setAttribute("a" , a);
%>
<h3>ʹ���Զ����ǩ</h3>
<tags:iterator bgColor="#99dd99" cellColor="#9999cc"
               title="��������ǩ" bean="a" />
</body>
</html>
