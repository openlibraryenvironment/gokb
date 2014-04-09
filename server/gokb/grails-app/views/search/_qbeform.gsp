<r:require modules="gokbstyle"/>
<r:require modules="editable"/>


<g:form class="form-horizontal open-inline" method="get" controller="search" >

  <input type="hidden" name="qbe" value="${params.qbe}"/>
  <g:each in="${hide}" var="hidden_var">
    <input type="hidden" name="hide" value="${hidden_var}"/>
  </g:each>
  <g:each in="${formdefn}" var="fld">
    <g:if test="${hide?.contains(fld.qparam)}" >
      <input type="hidden" name="${fld.qparam}" id="${fld.qparam}" value="${params[fld.qparam]}" />
    </g:if>
    <g:else>
      <div class="control-group">
	      <label class="control-label" for="${fld.qparam}">${fld.prompt}</label>
	      <div class="controls">
	        <g:if test="${fld.type=='lookup'}">
	          <g:simpleReferenceTypedown id="refdata_combo_${fld.qparam}"
	                                     class="input-xxlarge" 
	                                     style="width:350px;" 
	                                     name="${fld.qparam}" 
	                                     baseClass="${fld.baseClass}"
	                                     filter1="${fld.filter1?:''}"
	                                     value="${params[fld.qparam]}"
	                                     addBlankValue="yes" />
	        </g:if>
	        <g:else>
                  <div class="input-prepend input-append"><g:if test="${fld.contextTree.wildcard=='B' || fld.contextTree.wildcard=='R'}"><span class="add-on">*</span></g:if><input type="text" name="${fld.qparam}" id="${fld.qparam}" placeholder="${fld.placeholder}" value="${params[fld.qparam]}" /><g:if test="${fld.contextTree.wildcard=='B' || fld.contextTree.wildcard=='L'}"><span class="add-on">*</span></g:if></div>
	        </g:else>
	      </div>
	    </div>
    </g:else>
  </g:each>

  <div class="control-group">
    <div class="controls">
      <button type="submit" class="btn btn-primary">Search</button>
    </div>
  </div>

</g:form>
