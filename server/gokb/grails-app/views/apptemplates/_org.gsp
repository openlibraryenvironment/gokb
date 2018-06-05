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
              <g:annotatedLabel owner="${d}" property="reference">Reference</g:annotatedLabel>
      </dt>
      <dd>
              <g:xEditable class="ipe" owner="${d}" field="reference" />
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
</dl>

<div id="content">
  <g:if test="${d.id != null}">
    <ul id="tabs" class="nav nav-tabs">
      <li class="active"><a href="#orgdetails" data-toggle="tab">Organization</a></li>
      <li><a href="#altnames" data-toggle="tab">Alternate Names <span
                      class="badge badge-warning"> ${d.variantNames?.size() ?: '0'}
              </span></a></li>
      <li><a href="#ids" data-toggle="tab">IDs <span
                      class="badge badge-warning"> ${d.ids?.size() ?: '0'}
              </span></a></li>
      <li><a href="#relations" data-toggle="tab">Relations </span></a></li>
      <li><a href="#licenses" data-toggle="tab">Licenses <span class="badge badge-warning"> ${d.heldLicenses?.size() ?: '0'}</span></a></li>
      <li><a href="#packages" data-toggle="tab">Packages <span class="badge badge-warning"> ${d.providedPackages?.size() ?: '0'}</span></a></li>
      <li><a href="#titles" data-toggle="tab">Published Titles <span class="badge badge-warning"> ${d.publishedTitles?.size() ?: '0'}</span></a></li>
      <li><a href="#platforms" data-toggle="tab">Platforms <span class="badge badge-warning"> ${d.providedPlatforms?.size() ?: '0'}</span></a></li>
      <li><a href="#addprops" data-toggle="tab">Custom Fields <span
                      class="badge badge-warning"> ${d.additionalProperties?.size() ?: '0'}
              </span></a></li>
      <li><a href="#review" data-toggle="tab">Review Tasks <span
                      class="badge badge-warning"> ${d.reviewRequests?.size() ?: '0'}
              </span></a></li>
      <li><a href="#offices" data-toggle="tab">Offices <span
                      class="badge badge-warning"> ${d.offices?.size() ?: '0'}
              </span></a></li>
    </ul>

    <div id="my-tab-content" class="tab-content">
      <div class="tab-pane active" id="orgdetails">
        <g:if test="${d.id != null}">
          <dl class="dl-horizontal">
            <dt>
              <g:annotatedLabel owner="${d}" property="mission">Mission</g:annotatedLabel>
            </dt>
            <dd>
              <g:xEditableRefData owner="${d}" field="mission"
                                  config='Org.Mission' />
            </dd>
            <dt>
              <g:annotatedLabel owner="${d}" property="homepage">Homepage</g:annotatedLabel>
            </dt>
            <dd>
              <g:xEditable class="ipe" owner="${d}" field="homepage" />
            </dd>
            <dt>
              <g:annotatedLabel owner="${d}" property="roles">Roles</g:annotatedLabel>
            </dt>
            <dd>
              <g:if test="${d.id != null}">
                <ul>
                  <g:each in="${d.roles?.sort({"${it.value}"})}" var="t">
                    <li>
                      ${t.value}
                    </li>
                  </g:each>
                </ul>
                <br />

                <g:if test="${d.isEditable()}">
                  <g:form controller="ajaxSupport" action="addToStdCollection"
                    class="form-inline">
                    <input type="hidden" name="__context"
                            value="${d.class.name}:${d.id}" />
                    <input type="hidden" name="__property" value="roles" />
                    <g:simpleReferenceTypedown class="form-control" name="__relatedObject"
                            baseClass="org.gokb.cred.RefdataValue" filter1="Org.Role" />
                    <input type="submit" value="Add..."
                            class="btn btn-default btn-primary btn-sm " />
                  </g:form>
                </g:if>
              </g:if>
              <g:else>
                Record must be saved before roles can be edited.
              </g:else>
            </dd>
          </dl>
        </g:if>
      </div>

      <g:render template="showVariantnames"
                contextPath="../tabTemplates"
                model="${[d:displayobj, showActions:true]}" />

      <div class="tab-pane" id="ids">
        <g:if test="${d.id != null}">
          <dl>
            <dt>
              <g:annotatedLabel owner="${d}" property="ids">IDs</g:annotatedLabel>
            </dt>
            <dd>
              <g:render template="comboList" contextPath="../apptemplates"
                        model="${[d:d, property:'ids', cols:[[expr:'namespace.value',colhead:'Namespace'],[expr:'value',colhead:'Identifier']]]}" />

              <g:render template="addIdentifier" contextPath="../apptemplates" model="${[d:d]}"/>

            </dd>
          </dl>
        </g:if>
      </div>

      <div class="tab-pane" id="relations">
        <dl class="dl-horizontal">
          <dt>
            <g:annotatedLabel owner="${d}" property="parent">Parent</g:annotatedLabel>
          </dt>
          <dd>
            ${d.parent?.name}
          </dd>
          <dt>
            <g:annotatedLabel owner="${d}" property="children">Subsidiaries</g:annotatedLabel>
          </dt>
          <dd>
            <g:render template="simpleCombos" contextPath="../apptemplates"
                    model="${[d:d, property:'children', delete:'true', direction:'in', cols:[[expr:'name',colhead:'Name', action:'link']],targetClass:'org.gokb.cred.Org']}" />
          </dd>
        </dl>
      </div>
      <div class="tab-pane" id="addprops">
        <dl>
          <dt>
            <g:annotatedLabel owner="${d}" property="addprops">Additional Properties</g:annotatedLabel>
          </dt>
          <dd>
            <g:render template="addprops" contextPath="../apptemplates" model="${[d:d]}" />
          </dd>
        </dl>
      </div>

      <div class="tab-pane" id="review">
        <dl>
          <dt>
            <g:annotatedLabel owner="${d}" property="reviewrequests">Review Requests</g:annotatedLabel>
          </dt>
          <dd>
            <g:render template="revreqtab" contextPath="../apptemplates" model="${[d:d]}" />
          </dd>
        </dl>
      </div>

      <div class="tab-pane" id="offices">
        <dl>
          <dt>
                  <g:annotatedLabel owner="${d}" property="offices">Offices</g:annotatedLabel>
          </dt>
          <dd>
            <g:render template="comboList" contextPath="../apptemplates"
                    model="${[d:d, property:'offices', cols:[[expr:'name',colhead:'Office Name', action:'link']],targetClass:'org.gokb.cred.Office',direction:'in']}" />

            <g:if test="${d.isEditable()}">
              <button
                      class="hidden-license-details btn btn-default btn-sm btn-primary "
                      data-toggle="collapse" data-target="#collapseableAddOffice">
                      Add new <i class="glyphicon glyphicon-plus"></i>
              </button>
              <dl id="collapseableAddOffice" class="dl-horizontal collapse">
                <g:form controller="ajaxSupport" action="addToCollection"
                        class="form-inline">
                  <input type="hidden" name="__context"
                          value="${d.class.name}:${d.id}" />
                  <input type="hidden" name="__newObjectClass"
                          value="org.gokb.cred.Office" />
                  <input type="hidden" name="__addToColl" value="offices" />
                  <dt>Office Name</dt>
                  <dd>
                          <input type="text" name="name" />
                  </dd>
                  <dt>Website</dt>
                  <dd>
                          <input type="text" name="website" />
                  </dd>
                  <dt>Email</dt>
                  <dd>
                          <input type="text" name="email" />
                  </dd>
                  <dt>Number</dt>
                  <dd>
                          <input type="text" name="phoneNumber" />
                  </dd>
                  <dt>Address 1</dt>
                  <dd>
                          <input type="text" name="addressLine1" />
                  </dd>
                  <dt>Address 2</dt>
                  <dd>
                          <input type="text" name="addressLine2" />
                  </dd>
                  <dt>City</dt>
                  <dd>
                          <input type="text" name="city" />
                  </dd>
                  <dt>Region</dt>
                  <dd>
                          <input type="text" name="region" />
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
      </div>

			<div class="tab-pane" id="licenses">
				<dl>
					<dt>
						<g:annotatedLabel owner="${d}" property="licenses">Licenses</g:annotatedLabel>
					</dt>
					<dd>
						<g:render template="comboList" contextPath="../apptemplates"
							model="${[d:d, property:'heldLicenses', cols:[[expr:'name',colhead:'License Name']],targetClass:'org.gokb.cred.License']}" />
					</dd>
				</dl>
			</div>

			<div class="tab-pane" id="platforms">
				<dl>
					<dt>
						<g:annotatedLabel owner="${d}" property="platforms">Platforms</g:annotatedLabel>
					</dt>
					<dd>
						<g:render template="comboList" contextPath="../apptemplates"
							model="${[d:d, property:'providedPlatforms', cols:[[expr:'name', action:'link', colhead:'Platform Name',targetClass:'org.gokb.cred.Platform']]]}" />
					</dd>
				</dl>
			</div>

			<div class="tab-pane" id="titles">
				<g:link class="display-inline" controller="search" action="index"
					params="[qbe:'g:1titles', qp_pub:'org.gokb.cred.Org:'+d.id, hide:['qp_pub']]"
					id="">Titles published</g:link>
			</div>

			<div class="tab-pane" id="packages">
				<dl>
					<dt>
						<g:annotatedLabel owner="${d}" property="packages">Packages</g:annotatedLabel>
					</dt>
					<dd>
						<g:render template="comboList" contextPath="../apptemplates"
							model="${[d:d, property:'providedPackages', cols:[[expr:'name',colhead:'Package Name', action:'link']],targetClass:'org.gokb.cred.Package']}" />
					</dd>
				</dl>
			</div>
		</div>
		<g:render template="componentStatus" contextPath="../apptemplates"
			model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
	</g:if>
</div>
