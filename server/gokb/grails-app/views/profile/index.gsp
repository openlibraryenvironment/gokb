<!DOCTYPE html>
<r:require modules="gokbstyle" />
<r:require modules="editable" />
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : About</title>
  </head>
  <body>
	<div class="container-fluid">
		<div class="row-fluid">
			<h1>Application Info</h1>
			<dl class="dl-horizontal">
				<dt>Show Info Icon :</dt>
				<dd>
					<g:xEditableRefData owner="${user}" field="showInfoIcon"
						config="YN" />
				</dd>
				<dt>Show Quick View :</dt>
				<dd>
					<g:xEditableRefData owner="${user}" field="showQuickView"
						config="YN" />
				</dd>
				<dt>Default Page Size :</dt>
				<dd>
					<g:xEditable owner="${user}" field="defaultPageSize" />
				</dd>
		  </dl>
		</div>
	</div>
</body>
</html>