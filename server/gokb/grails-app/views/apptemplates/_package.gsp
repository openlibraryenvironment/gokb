  <dl class="dl-horizontal">
    <dt>
      <g:annotatedLabel owner="${d}" property="name">Package Name</g:annotatedLabel>
    </dt>
    <dd>
      ${d.name}
      <g:if test="${ d.isEditable() }">(Modify name through variants below)</g:if>
    </dd>

    <dt>
      <g:annotatedLabel owner="${d}" property="provider">Provider</g:annotatedLabel>
    </dt>
    <dd>
      ${d.provider?.name?:'Provider Not Set'}
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

    <g:if test="${d.lastProject}">
      <dt>
        <g:annotatedLabel owner="${d}" property="lastProject">Last Project</g:annotatedLabel>
      </dt>
      <dd>
        <g:link controller="resource" action="show"
          id="${d.lastProject?.getClassName()+':'+d.lastProject?.id}">
          ${d.lastProject?.name}
        </g:link>
      </dd>
    </g:if>

    <dt>
      <g:annotatedLabel owner="${d}" property="listVerifier">List Verifier</g:annotatedLabel>
    </dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" field="listVerifier" />
    </dd>
    <dt>
      <g:annotatedLabel owner="${d}" property="listVerifierDate">List Verifier Date</g:annotatedLabel>
    </dt>
    <dd>
      <g:xEditable class="ipe" owner="${d}" type="date"
        field="listVerifiedDate" />
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
      <li class="active"><a href="#packagedetails" data-toggle="tab">Package Details</a></li>
      <li><a href="#titledetails" data-toggle="tab">Titles <span class="badge badge-warning"> ${d.tipps?.size()} </span></a></li>
                        <li><a href="#identifiers" data-toggle="tab">Identifiers <span class="badge badge-warning"> ${d.ids?.size()} </span></a></li>

      <g:if test="${ d.isEditable() }">
        <li><a href="#altnames" data-toggle="tab">Alt Names</a></li>
      </g:if>
    </ul>

    <div id="my-tab-content" class="tab-content">

      <div class="tab-pane active" id="packagedetails">
        <dl class="dl-horizontal">
          <g:render template="refdataprops" contextPath="../apptemplates"
            model="${[d:(d), rd:(rd), dtype:(dtype)]}" />
          <dt>
            <g:annotatedLabel owner="${d}" property="nominalPlatform">Nominal Platform</g:annotatedLabel>
          </dt>
          <dd>
            <g:manyToOneReferenceTypedown owner="${d}" field="nominalPlatform"
              name="${comboprop}" baseClass="org.gokb.cred.Platform">
              ${d.nominalPlatform?.name ?: ''}
            </g:manyToOneReferenceTypedown>
          </dd>
        </dl>
      </div>

      <div class="tab-pane" id="titledetails">
        <g:link class="display-inline" controller="search" action="index"
          params="[qbe:'g:tipps', qp_pkg_id:d.id, hide:['qp_pkg_id', 'qp_cp', 'qp_pkg', 'qp_pub_id', 'qp_plat']]"
          id="">Titles in this package</g:link>

        <g:if test="${ d.isEditable() }">
          <g:form controller="ajaxSupport" action="addToCollection"
            class="form-inline">
            <input type="hidden" name="__context"
              value="${d.class.name}:${d.id}" />
            <input type="hidden" name="__newObjectClass"
              value="org.gokb.cred.TitleInstancePackagePlatform" />
            <input type="hidden" name="__addToColl" value="tipps" />
            <dl class="dl-horizontal">
              <dt>Title</dt>
              <dd>
                <g:simpleReferenceTypedown class="form-control" name="title"
                  baseClass="org.gokb.cred.TitleInstance" />
              </dd>
              <dt>Platform</dt>
              <dd>
                <g:simpleReferenceTypedown class="form-control" name="hostPlatform"
                  baseClass="org.gokb.cred.Platform" />
              </dd>
              <dt></dt>
              <dd>
                <button type="submit"
                  class="btn btn-default btn-primary btn-sm ">Add</button>
              </dd>
            </dl>
          </g:form>
        </g:if>
      </div>


      <g:if test="${ d.isEditable() }">
        <div class="tab-pane" id="altnames">
          <div class="control-group">
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
                      <th>Actions</th>
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
                        <td><g:xEditableRefData owner="${v}"
                            field="variantType"
                            config='KBComponentVariantName.VariantType' /></td>
                        <td><g:xEditableRefData owner="${v}" field="locale"
                            config='KBComponentVariantName.Locale' /></td>
                        <td><g:link controller="workflow"
                            action="AuthorizeVariant" id="${v.id}">Make Authorized</g:link>,
                          <g:link controller="workflow" action="DeleteVariant"
                          	class="confirm-click" data-confirm-message="Are you sure you wish to delete this Variant?"
                            id="${v.id}">Delete</g:link></td>
                      </tr>
                    </g:each>
                  </tbody>
                </table>

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
              </dd>
            </dl>
          </div>
        </div>
      </g:if>

      <div class="tab-pane" id="identifiers">
        <g:render template="combosByType" contextPath="../apptemplates"
                                model="${[d:d, property:'ids', cols:[
                  [expr:'toComponent.namespace.value', colhead:'Namespace'],
                  [expr:'toComponent.value', colhead:'ID', action:'link']]]}" />
      </div>

    </div>
    <g:render template="componentStatus" contextPath="../apptemplates"
      model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />
  </div>
