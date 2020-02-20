<g:if test="${hide.contains('SEARCH_FORM')}">
</g:if>
<g:elseif test="${params.inline}">
  <g:form method="get" class="form-horizontal" controller="search" role="form">
    <input type="hidden" name="qbe" value="${params.qbe}"/>

    <g:each in="${hide}" var="hidden_var">
      <input type="hidden" name="hide" value="${hidden_var}"/>
    </g:each>

    <g:if test="${refOid}">
      <input type="hidden" name="refOid" value="${refOid}"/>
    </g:if>

    <g:each in="${formdefn}" var="fld">
      <g:if test="${((hide?.contains(fld.qparam)) || ( fld.hide==true))}">
        <input type="hidden" name="${fld.qparam}" id="${fld.qparam}" value="${params[fld.qparam]}" />
      </g:if>
    </g:each>

    <div class="form-group">
      <div class="col-sm-12">
        <g:each in="${cfg.qbeGlobals}" var="glob">
          <g:if test="${(glob.qparam) && ( glob.prompt )}">
            <span>${glob.prompt}: <b>${params[glob.qparam] ?: glob.default}</b></span>
          </g:if>
        </g:each>

        <div class="btn-group pull-right" role="group" aria-label="Search Buttons">
          <button name="searchAction" type="submit" class="btn btn-success" value="search">Search View</button>
        </div>
      </div>
    </div>
  </g:form>
</g:elseif>
<g:else>
  <g:form method="get" class="form-horizontal" controller="search" role="form">

    <input type="hidden" name="qbe" value="${params.qbe}"/>

    <g:if test="${refOid}">
      <input type="hidden" name="refOid" value="${refOid}"/>
    </g:if>

    <g:each in="${hide}" var="hidden_var">
      <input type="hidden" name="hide" value="${hidden_var}"/>
    </g:each>

    <g:each in="${formdefn}" var="fld" status="frmidx">
      <g:if test="${((hide?.contains(fld.qparam)) || ( fld.hide==true))}">
        <input type="hidden" name="${fld.qparam}" id="${fld.qparam}" value="${params[fld.qparam]}" />
      </g:if>
      <g:else>
        <div class="col-sm-6">
          <div class="form-group">
                <label class="col-sm-3 control-label" for="${fld.qparam}">${fld.prompt}</label>
                <div class="col-sm-9">
                  <g:if test="${fld.type=='lookup'}">
                    <g:simpleReferenceTypedown id="refdata_combo_${params.inline ? 'inline_': ''}${fld.qparam}"
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
                      <input class="form-control" type="${fld.contextTree.type == 'java.lang.Long' ? 'number' : 'text'}" name="${fld.qparam}" id="${fld.qparam}" placeholder="${fld.placeholder}" value="${params[fld.qparam]}" />
                      <g:if test="${fld.contextTree.wildcard=='B' || fld.contextTree.wildcard=='R'}"><span class="input-group-addon">*</span></g:if>
                    </div>
                  </g:else>
                </div>
          </div>
        </div>
      </g:else>
    </g:each>

    <div class="col-sm-12">
      <div class="form-group">
        <div class="col-sm-10">
          <div class="col-sm-2">
          </div>
          <div class="col-sm-10">
            <g:each in="${cfg.qbeGlobals}" var="glob">
              <g:if test="${(glob.qparam) && ( glob.prompt )}">
                <span>
                  ${glob.prompt} : <select class="form-control" style="display:inline;max-width:75px" name="${glob.qparam}" value="${params[glob.qparam]}">
                    <option value="on" ${(params[glob.qparam] ?: glob.default ) == 'on' ? 'selected' : ''}>On</option>
                    <option value="off" ${(params[glob.qparam] ?: glob.default ) == 'off' ? 'selected' : ''}>Off</option>
                  </select>
                </span>
              </g:if>
            </g:each>
          </div>
        </div>
        <div class="col-sm-2">
          <g:if test="${hide.contains('SEARCH_BUTTONS')}">
          </g:if>
          <g:else>
            <div class="btn-group pull-right" role="group" aria-label="Search Buttons">
              <button name="searchAction" type="submit" class="btn btn-success btn-sm" value="search">Search</button>
              <div class="btn-group" role="group">
                <button class="btn btn-success btn-sm"
                        data-toggle="dropdown" >Save <span class="caret"></span></button>
                <ul id="savePopupForm" class="dropdown-menu pull-right well" role="menu" style="width: 400px;">
                  <li>
                    <div class="panel panel-default">
                      <div class="panel-heading">
                        <h3 class="panel-title">Save Search</h3>
                      </div>
                      <div class="panel-body">
                        Save As : <input type="text" name="searchName"/> <input class="btn btn-success btn-sm" type="submit" name="searchAction" value="save"/>
                      </div>
                    </div>

                  </li>
                </ul>
              </div>
            </div>
          </g:else>
        </div>
      </div>
    </div>
  </g:form>
</g:else>

<g:javascript>
  // When DOM is ready.
  $(document).ready(function(){

    $("#savePopupForm").click(function(e) {
      e.stopPropagation();
      // $('#savePopupForm').toggle();
    });

    var form_selects = $(".simpleReferenceTypedown");

    form_selects.each(function() {

      var conf = {
        placeholder: "Search for...",
        allowClear: true,
        width:'resolve',
        minimumInputLength: 0,
        ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
          url: gokb.config.lookupURI,
          dataType: 'json',
          data: function (term, page) {
            return {
              format:'json',
              q: term,
              baseClass:$(this).data('domain'),
              filter1:$(this).data('filter1'),
              addEmpty:'Y'
            };
          },
          results: function (data, page) {
            // console.log("resultsFn");
            return {results: data.values};
          }
        },
        initSelection : function (element, callback) {
          var idv=$(element).val();
          // console.log("initSelection..%o"+idv,element);
          var txt=$(element).context.dataset.displayvalue;
          var data = {id: idv, text: txt};
          callback(data);
        }
      };

      var me = $(this);

      if (me.hasClass("allow-add")) {
        // Add to the config...
        conf.createSearchChoice = function(term, data) {
          if ($(data).filter(function() {
            return term.localeCompare(this.text) === 0;
          }).length === 0) {
            return {
              id: me.data('domain') + ":__new__:" + me.data('filter1') + ":" + term,
              text:  term  + ' (new tag)'
            };
          }
        };
      }

      me.select2(conf);
    });

  });
</g:javascript>

