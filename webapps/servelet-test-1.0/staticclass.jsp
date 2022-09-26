<%-- Created by xxx --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.sql.*,javax.sql.*,javax.naming.*" %>

<body>
<%!
    class Hello{
        private String hello="";
        public Hello() {
            hello="Hello World!";
        }
        public String getHello() {
            return hello;
        }
    }
%>

<%
    Hello hello = new Hello();
%>
<h1>
    <%= hello.getHello() %>
</h1>
</body>
