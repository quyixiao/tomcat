<%-- Created by xxx --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.sql.*,javax.sql.*,javax.naming.*" %>
<%@ page import="com.example.servelettest.Person" %>
<%@ page import="java.util.ArrayList" %>
<%@ taglib prefix="ex" uri="/WEB-INF/hello.tld" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%-- 我在测试 --%>
<%
      Person person =new Person();
      person.setName("帅哥");
      person.setHeight(167);
      person.setAge(20);
      request.setAttribute("person", person);




%>
名字 ： ${person.name} <br>
人身高 ： ${person.height}




<ex:hello/>
