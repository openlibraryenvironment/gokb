<g:form method="get" class="form-horizontal" controller="search" role="form">

  <input type="hidden" name="qbe" value="${params.qbe}"/>

  <g:each in="${hide}" var="hidden_var">
    <input type="hidden" name="hide" value="${hidden_var}"/>
  </g:each>

  <g:each in="${formdefn}" var="fld">
    <g:if test="${((hide?.contains(fld.qparam)) || ( fld.hide==true))}">
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
    <label class="col-sm-2 control-label"></label>
    <div class="col-sm-10">
      <g:each in="${cfg.qbeGlobals}" var="glob">
        <g:if test="${glob.qparam != null}">
          ${glob.prompt} : <select name="${glob.qparam}" value="${params[glob.qparam]}">
            <option value="on" ${(params[glob.qparam] ?: glob.default ) == 'on' ? 'selected' : ''}>On</option>
            <option value="off" ${(params[glob.qparam] ?: glob.default ) == 'off' ? 'selected' : ''}>Off</option>
          </select>
        </g:if>
      </g:each>
      <button type="submit" class="btn btn-success btn-sm pull-right">Search</button>
    </div>
  </div>


</g:form>
