<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Additional Property Definition</h1>

<dl class="dl-horizontal">
	<dt><g:annotatedLabel owner="${d}" property="id">Internal Id</g:annotatedLabel></dt>
	<dd>${d.id?:'New record'}</dd>
	<dt><g:annotatedLabel owner="${d}" property="propertyName">Property Name</g:annotatedLabel></dt>
	<dd><g:xEditable owner="${d}" field="propertyName"/></dd>
</dl>
