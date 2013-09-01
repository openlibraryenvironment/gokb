<r:require modules="gokbstyle"/>
<r:require modules="editable"/>


<form class="form-horizontal">

  <input type="hidden" name="qbe" value="${params.qbe}"/>
  <g:each in="${formdefn}" var="fld">
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
                                     value="${params[fld.qparam]}"/>
        </g:if>
        <g:else>
          <input type="text" name="${fld.qparam}" id="${fld.qparam}" placeholder="${fld.placeholder}" value="${params[fld.qparam]}">
        </g:else>
      </div>
    </div>
  </g:each>

  <div class="control-group">
    <div class="controls">
      <button type="submit" class="btn">Search</button>
    </div>
  </div>

</form>

