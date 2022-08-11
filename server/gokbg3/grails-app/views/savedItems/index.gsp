<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: File Upload</title>
</head>
<body>



<h3>Saved Items</h3>
<hr/>
<div class="container">
  <div class="row threeColGrid">
    <g:each in="${saved_items}" var="itm">
      <g:if test="${itm instanceof org.gokb.cred.SavedSearch}">
        <div class="col-md-3 center-block center-text">
          <i class="fa fa-search fa-fw"></i>
          <g:link controller="search" action="index" params="${itm.toParam()}">${itm.name}</g:link>
        </div>
      </g:if>
      <g:if test="${1==2}">
        <div class="col-md-3 center-block center-text">
          <i class="fa fa-folder fa-fw"></i>
        </div>
      </g:if>
    </g:each>
  </div>
</div>

</body>
</html>
