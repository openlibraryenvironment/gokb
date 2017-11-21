<dl class="dl-horizontal">
	<dt>
		<g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel>
	</dt>
	<dd>
		<g:xEditable class="ipe" owner="${d}" field="name" />
	</dd>
	<g:if test="${d?.id != null}">
	  <dt>
	    <g:annotatedLabel owner="${d}" property="description">Description</g:annotatedLabel>
	  </dt>
	  <dd class="multiline" >
	    <g:xEditable class="ipe" owner="${d}" field="description" />
	  </dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="tags">Tags</g:annotatedLabel>
		</dt>
		<dd>
			<ul>
				<g:each in="${d.tags?.sort({"${it.value}"})}" var="t">
					<li>
						${t.value} (<g:link controller="ajaxSupport" action="unlinkManyToMany"
						  params="${ ["__context" : "${d.class.name}:${d.id}", "__property" : "tags", "__itemToRemove" : "${t.getClassName()}:${t.id}" ] }" 
						  class='confirm-click' data-confirm-message="Are you sure you wish to remove this tag?"
						  >delete</g:link>)
					</li>
				</g:each>
			</ul>
			<g:if test="${d.isEditable()}">
				<g:form controller="ajaxSupport" action="addToStdCollection"
					class="form-inline">
					<input type="hidden" name="__context"
						value="${d.class.name}:${d.id}" />
					<input type="hidden" name="__property" value="tags" />
					<g:simpleReferenceTypedown class="form-control allow-add"
						name="__relatedObject" baseClass="org.gokb.cred.RefdataValue"
						filter1="Macro.Tags" />
					<input type="submit" value="Add..."
						class="btn btn-default btn-primary btn-sm " />
				</g:form>
			</g:if>
		</dd>
	  <dt>
	    <g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
	  </dt>
	  <dd>
	    <g:xEditableRefData owner="${d}" field="status"
	      config="KBComponent.Status" />
	  </dd>

		<dt>
			<g:annotatedLabel owner="${d}" property="refineTransformations">Refine Transformations</g:annotatedLabel>
		</dt>
		<dd class="multiline json refine-transform preformatted" >
			<g:xEditable class="ipe" owner="${d}" field="refineTransformations" data-tpl="tpl" />
		</dd>
	</g:if>
</dl>