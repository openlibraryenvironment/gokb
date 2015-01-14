<div id="content">

	<dl class="dl-horizontal">
		<dt>
			<g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditable class="ipe" owner="${d}" field="name" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="status">Status</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="status"
				config="KBComponent.Status" />
		</dd>
		<dt>
			<g:annotatedLabel owner="${d}" property="source">Source</g:annotatedLabel>
		</dt>
		<dd>
			<g:manyToOneReferenceTypedown owner="${d}" field="source"
				baseClass="org.gokb.cred.Source">
				${d.source?.name}
			</g:manyToOneReferenceTypedown>
		</dd>

		<dt>
			<g:annotatedLabel owner="${d}" property="editStatus">Edit Status</g:annotatedLabel>
		</dt>
		<dd>
			<g:xEditableRefData owner="${d}" field="editStatus"
				config='KBComponent.EditStatus' />
		</dd>

    <dt><g:annotatedLabel owner="${d}" property="territories">Territories</g:annotatedLabel></dt>
    <dd>
       <g:render template="territories" contextPath="../apptemplates" model="${[d:d]}" />
    </dd>


	</dl>

	<ul id="tabs" class="nav nav-tabs">
		<li class="active"><a href="#platformdetails" data-toggle="tab">Platform
				Details</a></li>
		<li><a href="#altnames" data-toggle="tab">Alternate Names <span
					class="badge badge-warning"> ${d.variantNames?.size()}
	</ul>


	<div id="my-tab-content" class="tab-content">
		<div class="tab-pane active" id="platformdetails">

			<dl class="dl-horizontal">
				<dt>
					<g:annotatedLabel owner="${d}" property="primaryURL">Primary URL</g:annotatedLabel>
				</dt>
				<dd>
					<g:xEditable class="ipe" owner="${d}" field="primaryUrl">
						${d.primaryUrl}
					</g:xEditable>
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="software">Software</g:annotatedLabel>
				</dt>
				<dd>
					<g:xEditableRefData owner="${d}" field="software"
						config='Platform.Software' />
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="service">Service</g:annotatedLabel>
				</dt>
				<dd>
					<g:xEditableRefData owner="${d}" field="service"
						config='Platform.Service' />
				</dd>

				<dt>
					<g:annotatedLabel owner="${d}" property="authentication">Authentication</g:annotatedLabel>
				</dt>
				<dd>
					<g:xEditableRefData owner="${d}" field="authentication"
						config='Platform.AuthMethod' />
				</dd>
		
			</dl>
		</div>
		<div class="tab-pane" id="altnames">
			<g:if test="${d.id != null}">
				<dl>
					<dt>
						<g:annotatedLabel owner="${d}" property="alternateNames">Alternate Names</g:annotatedLabel>
					</dt>
					<dd>
						<table class="table table-striped table-bordered">
							<thead>
								<tr>
									<th>Alternate Name</th>
									<th>Status</th>
									<th>Variant Type</th>
									<th>Locale</th>
								</tr>
							</thead>
							<tbody>
								<g:each in="${d.variantNames}" var="v">
									<tr>
										<td>
											${v.variantName}
										</td>
										<td><g:xEditableRefData owner="${v}" field="status"
												config='KBComponentVariantName.Status' /></td>
										<td><g:xEditableRefData owner="${v}" field="variantType"
												config='KBComponentVariantName.VariantType' /></td>
										<td><g:xEditableRefData owner="${v}" field="locale"
												config='KBComponentVariantName.Locale' /></td>
									</tr>
								</g:each>
							</tbody>
						</table>

						<g:if test="${d.isEditable()}">
							<h4>
								<g:annotatedLabel owner="${d}" property="addVariantName">Add Variant Name</g:annotatedLabel>
							</h4>
							<dl class="dl-horizontal">
								<g:form controller="ajaxSupport" action="addToCollection"
									class="form-inline">
									<input type="hidden" name="__context"
										value="${d.class.name}:${d.id}" />
									<input type="hidden" name="__newObjectClass"
										value="org.gokb.cred.KBComponentVariantName" />
									<input type="hidden" name="__recip" value="owner" />
									<dt>Variant Name</dt>
									<dd>
										<input type="text" name="variantName" />
									</dd>
									<dt>Locale</dt>
									<dd>
										<g:simpleReferenceTypedown class="form-control" name="locale"
											baseClass="org.gokb.cred.RefdataValue"
											filter1="KBComponentVariantName.Locale" />
									</dd>
									<dt>Variant Type</dt>
									<dd>
										<g:simpleReferenceTypedown class="form-control" name="variantType"
											baseClass="org.gokb.cred.RefdataValue"
											filter1="KBComponentVariantName.VariantType" />
									</dd>
									<dt></dt>
									<dd>
										<button type="submit"
											class="btn btn-default btn-primary btn-sm ">Add</button>
									</dd>
								</g:form>
							</dl>
						</g:if>
					</dd>
				</dl>
			</g:if>
		</div>
	</div>
	<g:render template="componentStatus" contextPath="../apptemplates"
		model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

</div>
