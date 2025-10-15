<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.*, com.ctm.model.Tournament" %>

<%
    String role = (String) session.getAttribute("role");
    if (role == null || !"admin".equalsIgnoreCase(role)) {
        response.sendRedirect("index.jsp");
        return;
    }
    response.setHeader("Cache-Control","no-cache,no-store,must-revalidate");
    response.setHeader("Pragma","no-cache");
    response.setDateHeader("Expires",0);

    String msg = request.getParameter("msg");
    String err = request.getParameter("err");
    String mode = (String) request.getAttribute("mode");
    if (mode == null) mode = "tournaments";
%>

<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Start Match | Admin</title>
<link rel="stylesheet" href="resources/css/admin_start_match.css">
</head>
<body>

<div class="top">
  <div class="brand">🏏 Start Match Panel</div>
  <div>
    <a href="adminmain.jsp" class="viewer-btn">🏠 Home</a>
    <a href="logout" class="danger">🚪 Logout</a>
  </div>
</div>

<% if (msg != null) { %>
  <div class="msg"><%= msg %></div>
<% } if (err != null) { %>
  <div class="err"><%= err %></div>
<% } %>

<div class="wrap">

<%-- ==========================
     1️⃣ LIST ALL TOURNAMENTS
     ========================== --%>
<% if ("tournaments".equals(mode)) {
    List<Tournament> tournaments = (List<Tournament>) request.getAttribute("tournaments");
    Map<Long,Integer> todayCounts = (Map<Long,Integer>) request.getAttribute("todayCounts");
%>
<h2 style="text-align:center;">Select Tournament to Start Matches</h2>
<div class="table-wrap table-center">
<table>
  <thead>
    <tr>
      <th>ID</th>
      <th>Name</th>
      <th>Format</th>
      <th>Today's Matches</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
  <% for (Tournament t : tournaments) { %>
    <tr>
      <td><%= t.getId() %></td>
      <td><%= t.getName() %></td>
      <td><%= t.getFormat() %></td>
      <td><%= todayCounts.getOrDefault(t.getId(), 0) %></td>
      <td>
        <form method="get" action="startmatch">
          <input type="hidden" name="tid" value="<%= t.getId() %>">
          <button class="viewer-btn" type="submit">View Matches</button>
        </form>
      </td>
    </tr>
  <% } %>
  </tbody>
</table>
</div>
<% } %>

<%-- ==========================
     2️⃣  TODAY'S MATCHES + TOSS SELECTION
     ========================== --%>
<% if ("matches".equals(mode)) {
    Tournament t = (Tournament) request.getAttribute("tournament");
    List<Map<String,Object>> matches = (List<Map<String,Object>>) request.getAttribute("matches");
%>
<h2 style="text-align:center;">Today's Matches - <%= t != null ? t.getName() : "Tournament" %></h2>
<div class="table-wrap table-center">
<table>
  <thead>
    <tr>
      <th>Match ID</th>
      <th>Teams</th>
      <th>Venue</th>
      <th>Date</th>
      <th>Toss Winner</th>
      <th>Decision</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
  <% if (matches.isEmpty()) { %>
    <tr><td colspan="7" class="empty">No scheduled matches for today</td></tr>
  <% } else {
       for (Map<String,Object> m : matches) { %>
    <tr>
      <td><%= m.get("id") %></td>
      <td><strong><%= m.get("aName") %></strong> vs <strong><%= m.get("bName") %></strong></td>
      <td><%= m.get("venue") %></td>
      <td><%= m.get("datetime") %></td>
      <td>
        <select name="tossWinnerId" form="form_<%= m.get("id") %>">
          <option value="<%= m.get("aId") %>"><%= m.get("aName") %></option>
          <option value="<%= m.get("bId") %>"><%= m.get("bName") %></option>
        </select>
      </td>
      <td>
        <select name="tossDecision" form="form_<%= m.get("id") %>">
          <option value="BAT">Bat</option>
          <option value="BOWL">Bowl</option>
        </select>
      </td>
      <td>
        <form id="form_<%= m.get("id") %>" method="post" action="startmatch">
          <input type="hidden" name="tid" value="<%= t.getId() %>">
          <input type="hidden" name="matchId" value="<%= m.get("id") %>">
          <button class="primary" type="submit">Start Match</button>
        </form>
      </td>
    </tr>
  <% } } %>
  </tbody>
</table>
</div>

<div style="text-align:center; margin-top: 36px;">
  <a href="startmatch" class="secondary">⬅ Back to Tournaments</a>
</div>
<% } %>

</div> <!-- wrap -->

<footer>
  Cricket Tournament Manager • Admin Portal
</footer>

</body>
</html>
