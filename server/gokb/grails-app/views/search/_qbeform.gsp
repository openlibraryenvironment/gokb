<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<g:form method="get" class="form-horizontal" controller="search" role="form">

  <input type="hidden" name="qbe" value="${params.qbe}"/>

  <g:each in="${hide}" var="hidden_var">
    <input type="hidden" name="hide" value="${hidden_var}"/>
  </g:each>

  <g:each in="${formdefn}" var="fld">
    <g:if test="${hide?.contains(fld.qparam)}" >
      <input type="hidden" name="${fld.qparam}" id="${fld.qparam}" value="${params[fld.qparam]}" />
    </g:if>
    <g:else>
      <div class="form-group">
	      <label class="col-sm-2 control-label" for="${fld.qparam}">${fld.prompt}</label>
	      <div class="col-sm-10">
	        <g:if test="${fld.type=='lookup'}">
	          <g:simpleReferenceTypedown id="refdata_combo_${fld.qparam}"
	                                     class="form-control"
	                                     name="${fld.qparam}" 
	                                     baseClass="${fld.baseClass}"
	                                     filter1="${fld.filter1?:''}"
	                                     value="${params[fld.qparam]}"
	                                     addBlankValue="yes" />
	        </g:if>
	        <g:else>
                  <div class="${fld.contextTree.wildcard!=null?'input-group':''}">
                    <g:if test="${fld.contextTree.wildcard=='B' || fld.contextTree.wildcard=='L'}"><span class="input-group-addon">*</span></g:if>
                    <input class="form-control" type="text" name="${fld.qparam}" id="${fld.qparam}" placeholder="${fld.placeholder}" value="${params[fld.qparam]}" />
                    <g:if test="${fld.contextTree.wildcard=='B' || fld.contextTree.wildcard=='R'}"><span class="input-group-addon">*</span></g:if>
                  </div>
	        </g:else>
	      </div>
      </div>
    </g:else>
  </g:each>

  <div class="form-group">
    <div class="col-sm-offset-2 col-sm-10">
      <button type="submit" class="btn btn-default btn-sm">Search</button>
    </div>
  </div>

</g:form>
