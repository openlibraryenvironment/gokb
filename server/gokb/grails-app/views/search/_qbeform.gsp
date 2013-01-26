<form class="form-horizontal">

  <input type="hidden" name="qbe" value="${params.qbe}"/>
  <g:each in="${formdefn}" var="fld">
    <div class="control-group">
      <label class="control-label" for="${fld.qparam}">${fld.prompt}</label>
      <div class="controls">
        <input type="text" name="${fld.qparam}" id="${fld.qparam}" placeholder="${fld.placeholder}" value="${params[fld.qparam]}">
      </div>
    </div>
  </g:each>

  <div class="control-group">
    <div class="controls">
      <button type="submit" class="btn">Search</button>
    </div>
  </div>

</form>

