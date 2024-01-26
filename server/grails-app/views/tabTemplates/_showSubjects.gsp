<g:set var="editable" value="${d.isEditable()}"/>
<div class="tab-pane" id="subjects">
    <g:if test="${d.id != null}">
        <dl>
            <dt>
                <g:annotatedLabel owner="${d}" property="subjects">Subjects</g:annotatedLabel>
            </dt>
            <dd>
                <table class="table table-striped table-bordered">
                    <thead>
                    <tr>
                        <th>Scheme</th>
                        <th>Value</th>
                        <g:if test="${showActions}">
                            <th>Actions</th>
                        </g:if>
                    </tr>
                    </thead>
                    <tbody>
                    <g:each in="${d.subjects}" var="someSubject">
                        <tr>
                            <td>${someSubject.subject.scheme.value}</td>
                            <td>${someSubject.subject.heading}</td>
                            <td>
                                <g:if test="${editable && showActions}">
                                    <g:link controller="ajaxSupport" class="confirm-click"
                                            data-confirm-message="Are you sure you wish to delete this Subject?"
                                            action="deleteSubject" id="${someSubject.id}">Delete</g:link>
                                </g:if>
                            </td>
                        </tr>
                    </g:each>
                    </tbody>
                </table>

                <g:if test="${editable}">
                    <h4>
                        <g:annotatedLabel owner="${d}" property="addSubject">Add Subject</g:annotatedLabel>
                    </h4>
                    <dl class="dl-horizontal">
                        <g:form controller="ajaxSupport" action="addSubject"
                                class="form-inline">
                            <input type="hidden" name="__context"
                                   value="${d.class.name}:${d.id}"/>
                            <input type="hidden" name="__recip" value="owner"/>
                            <dt class="dt-label">Subject Scheme</dt>
                            <dd>
                                <g:simpleReferenceTypedown class="form-control"
                                                           name="scheme"
                                                           baseClass="org.gokb.cred.RefdataValue"
                                                           filter1="Subject.Scheme"/>
                            </dd>
                            <dt class="dt-label">Value</dt>
                            <dd>
                                <input type="text" class="form-control select-m" name="val"/>
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
        Subjects can be added after the creation process is finished.
    </g:else>
</div>
