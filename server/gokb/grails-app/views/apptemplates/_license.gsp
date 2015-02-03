<g:render template="kbcomponent" contextPath="../apptemplates"
	model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
<g:if test="${d.id != null}">
	<dl class="dl-horizontal">
		<dt>
			<g:annotatedLabel owner="${d}" property="licenseURL">License URL</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="url" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="licenseType">License Type</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="type" config='License.Type' />
		</dd>

    <dt><g:annotatedLabel owner="${d}" property="curatoryGroups">Curatory Groups</g:annotatedLabel></dt>
    <dd>
       <g:render template="curatory_groups" contextPath="../apptemplates" model="${[d:d]}" />
    </dd>

	</dl>

	<ul id="tabs" class="nav nav-tabs">
		<li class="active"><a href="#licsummary" data-toggle="tab">License
				Summary</a></li>
		<li><a href="#lists" data-toggle="tab">Files</a></li>
	</ul>

	<g:render template="showDataFiles" contextPath="../tabTemplates" model="${[d:displayobj,allowEdit:false]}" />

</g:if>
