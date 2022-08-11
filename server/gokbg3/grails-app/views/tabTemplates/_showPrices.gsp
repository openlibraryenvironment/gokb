<g:set var="editable" value="${d.isEditable()}"/>
<div class="tab-pane" id="prices">
    <g:if test="${d.id != null}">
        <dl>
            <dt>
                <g:annotatedLabel owner="${d}" property="prices">Prices</g:annotatedLabel>
            </dt>
            <dd>
                <table class="table table-striped table-bordered">
                    <thead>
                    <tr>
                        <th>Price Type</th>
                        <th>Value</th>
                        <th>Currency</th>
                        <th>Start Date</th>
                        <th>End Date</th>
                        <g:if test="${showActions}">
                            <th>Actions</th>
                        </g:if>
                    </tr>
                    </thead>
                    <tbody>
                    <g:each in="${d.prices}" var="somePrice">
                        <tr>
                            <td><g:xEditableRefData owner="${somePrice}" field="priceType" config='Price.type'/></td>
                            <td><g:xEditable owner="${somePrice}" field="price"/></td>
                            <td><g:xEditableRefData owner="${somePrice}" field="currency" config='Currency'/></td>
                            <td><g:xEditable owner="${somePrice}" field="startDate" type="date"/></td>
                            <td><g:xEditable owner="${somePrice}" field="endDate" type="date"/></td>
                            <td>
                                <g:if test="${editable && showActions}">
                                    <g:link controller="ajaxSupport" class="confirm-click"
                                            data-confirm-message="Are you sure you wish to delete this Price?"
                                            action="deletePrice" id="${somePrice.id}">Delete</g:link>
                                </g:if>
                            </td>
                        </tr>
                    </g:each>
                    </tbody>
                </table>

                <g:if test="${editable}">
                    <h4>
                        <g:annotatedLabel owner="${d}" property="addPrice">Add Price</g:annotatedLabel>
                    </h4>
                    <dl class="dl-horizontal">
                        <g:form controller="ajaxSupport" action="addToCollection"
                                class="form-inline">
                            <input type="hidden" name="__context"
                                   value="${d.class.name}:${d.id}"/>
                            <input type="hidden" name="__newObjectClass"
                                   value="org.gokb.cred.ComponentPrice"/>
                            <input type="hidden" name="__recip" value="owner"/>
                            <dt class="dt-label">Price Type</dt>
                            <dd>
                                <g:simpleReferenceTypedown class="form-control"
                                                           name="priceType"
                                                           baseClass="org.gokb.cred.RefdataValue"
                                                           filter1="Price.type"/>
                            </dd>
                            <dt class="dt-label">Price</dt>
                            <dd>
                                <input type="number" class="form-control select-m" name="price" step="0.01"/>
                            </dd>
                            <dt class="dt-label">Currency</dt>
                            <dd>
                                <g:simpleReferenceTypedown class="form-control" name="currency"
                                                           baseClass="org.gokb.cred.RefdataValue"
                                                           filter1="Currency"/>
                            </dd>
                            <dt class="dt-label">Start Date</dt>
                            <dd>
                                <input type="date" class="form-control select-m" format="yyyy-MM-dd" name="startDate" value="${java.util.Calendar.instance.time}"/>
                            </dd>
                            <dt></dt>
                            <dd>
                                <button type="submit"
                                        class="btn btn-default btn-primary">Add</button>
                            </dd>
                        </g:form>
                    </dl>
                </g:if>
            </dd>
        </dl>
    </g:if>
    <g:else>
        Prices can be added after the creation process is finished.
    </g:else>
</div>
