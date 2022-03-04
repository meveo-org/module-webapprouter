# templating

When the approuter generates the index.html (and other files) corresponding to a webapp it uses a set of template files.
Those files have to be adapted to the context of the webapp, for instance we have to correctly set the page title, the list of imports,...

there are several approches that range from simply copying the template files in the meveo branch 
then let the user manually customize them in the master branch to creating the html files from scratch in the script without using any template.

between those extremes several templating technologies could be used

the criteria that need to be evaluated from 1 (bad) to 5 (good) are :
* LRN : easy to learn by users that need to modify the template
* PRV : easy to preview the result of modification
* FLX : Flexibility (the modifications can be done in the template without having to override the html)
* PRF : performance
* CST : cost of setup
* MTN : maintenance (dependencies, usage of special tools,...)

## String replacement
This method use a notation like `#{MY_VARIABLE}` to replace some part of the template by variables sets in the script,
and some comment like `<%-- LIB_INSERT --%>` to insert predefined parts

LRN:5
PRV:4
FLX:1
PRF:5
CST:5
MTN:5


## Expression language engine
This method,used throughout all parts of meveo already, use the EL engine to replace expression like `#{MY_VARIABLE}` or more complex expression `#{cube=(x->x*x*x);cube(page.pageCounter)}`
Some comment like `<%-- LIB_INSERT --%>` can still be used to insert predefined parts

LRN:4
PRV:4
FLX:3
PRF:4
CST:5
MTN:5

## jstl engine

The JSTL library tag can be used to in conjunction to the JSP engine to use template like 
```
<%@ page language="java" contentType="text/html; charset=US-ASCII"
    pageEncoding="US-ASCII"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "https://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=US-ASCII">
<title>Home Page</title>
<%@ taglib uri="https://java.sun.com/jsp/jstl/core" prefix="c" %>
<style>
table,th,td
{
border:1px solid black;
}
</style>
</head>
<body>
<%-- Using JSTL forEach and out to loop a list and display items in table --%>
<table>
<tbody>
<tr><th>ID</th><th>Name</th><th>Role</th></tr>
<c:forEach items="${requestScope.empList}" var="emp">
<tr><td><c:out value="${emp.id}"></c:out></td>
<td><c:out value="${emp.name}"></c:out></td>
<td><c:out value="${emp.role}"></c:out></td></tr>
</c:forEach>
</tbody>
</table>
<br><br>
<%-- simple c:if and c:out example with HTML escaping --%>
<c:if test="${requestScope.htmlTagData ne null }">
<c:out value="${requestScope.htmlTagData}" escapeXml="true"></c:out>
</c:if>
<br><br>
<%-- c:set example to set variable value --%>
<c:set var="id" value="5" scope="request"></c:set>
<c:out value="${requestScope.id }" ></c:out>
<br><br>
<%-- c:url example --%>
<a href="<c:url value="${requestScope.url }"></c:url>">JournalDev</a>
</body>
</html>
```
LRN:3
PRV:1
FLX:5
PRF:3
CST:5
MTN:5

## jstl webcomponent
The main drawback of the jstl engine approch is that there is no easy way to preview the modifications of a template

To cope with this we can introduce jstl-webcomponent and rewrite the previous template like:
```
import "jstl-module.js";
<!--<%@ page language="java" contentType="text/html; charset=US-ASCII"
    pageEncoding="US-ASCII"%>-->
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "https://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=US-ASCII">
<title>Home Page</title>
<!--<%@ taglib uri="https://java.sun.com/jsp/jstl/core" prefix="c" %>-->
<style>
table,th,td
{
border:1px solid black;
}
</style>
</head>
<body>
<!-- Using JSTL forEach and out to loop a list and display items in table -->
<table>
<tbody>
<tr><th>ID</th><th>Name</th><th>Role</th></tr>
<c-forEach items="${requestScope.empList}" var="emp">
<tr><td><c-out value="${emp.id}"></c-out></td>
<td><c-out value="${emp.name}"></c-out></td>
<td><c-out value="${emp.role}"></c-out></td></tr>
</c-forEach>
</tbody>
</table>
<br><br>
<!-- simple c:if and c:out example with HTML escaping -->
<c-if test="${requestScope.htmlTagData ne null }">
<c-out value="${requestScope.htmlTagData}" escapeXml="true"></c-out>
</c-if>
<br><br>
<!-- c:set example to set variable value -->
<c-set var="id" value="5" scope="request"></c-set>
<c-out value="${requestScope.id }" ></c-out>
<br><br>
</body>
</html>
```
the jstl js module would then define all the jstl web-components  c-if, c-forEach,.... that would allow to correctly display the result of the template using an http server.

A simple conversion turn this template to a jstl page that is then evaluated using the jsp engine.

LRN:3
PRV:5
FLX:5
PRF:3
CST:3
MTN:3

## meveo template webcomponent

to avoid the performance and maintenance issues of using the jsp engine,
we could decide not to evaluate the template on the server and just serialise the info of the webapp as a json string used to render the components

so a template like :
```
import "mv-templating.js";
<html>
<head>
<script>
  var webapp = {
     "name" :"MyWebapp";
     "stylesheets" : ["./css/style.css","./css/index.css"]
   }
   var requestScope = { ... }
</script>
</head>
<body>
<table>
<tbody>
<tr><th>ID</th><th>Name</th><th>Role</th></tr>
<mv-forEach items="${requestScope.empList}" var="emp">
  <tr><td><mv-out value="${emp.id}"></mvtp-out></td>
  <td><mv-out value="${emp.name}"></mvtp-out></td>
  <td><mv-out value="${emp.role}"></mvtp-out></td></tr>
</mv-forEach>
</tbody>
</table>
<br><br>
<!-- simple c:if and c:out example with HTML escaping -->
<mv-if test="${requestScope.htmlTagData ne null }">
  <mv-out value="${requestScope.htmlTagData}" escapeXml="true"></mv-out>
</mv-if>
<br><br>
<!-- c:set example to set variable value -->
<mv-set var="id" value="5" scope="request"></mv-set>
<mv-out value="${requestScope.id }" ></mv-out>
<br><br>
</body>
</html>
```
would produce an index.html scritcly identical except that the webapp var would be replaced by the serialized version of the real one.

LRN:3
PRV:5
FLX:5
PRF:5
CST:4
MTN:5

