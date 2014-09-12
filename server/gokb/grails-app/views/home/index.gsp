<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKb</title>
  </head>
  <body>

   <div class="container">
     <div class="row">
       <div id="openActivities" class="col-md-6 well">
         <g:if test="${(openActivities != null ) && ( openActivities.size() > 0 )}">
           <h3>Currently open activities</h3>
           <table class="table table-striped table-bordered">
             <thead>
               <tr>
                 <td>Activity</td>
                 <td>Type</td>
                 <td>Created</td>
                 <td>Last Updated</td>
               </tr>
             </thead>
             <tbody>
               <g:each in="${openActivities}" var="activity">
                 <tr>
                   <td><g:link controller="workflow" action="${activity.activityAction}" id="${activity.id}">${activity.activityName?:'No name'}</g:link></td>
                   <td>${activity.type}</td>
                   <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${activity.dateCreated}"/></td>
                   <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${activity.lastUpdated}"/></td>
                 </tr>
               </g:each>
             </tbody>
           </table>
         </g:if>
         <g:else>
           <h3>No open activities</h3>
         </g:else>

         <g:if test="${(recentReviewRequests != null ) && ( recentReviewRequests.size() > 0 )}">
           <h3>Your most recent review requests</h3>
           <table class="table table-striped table-bordered">
             <thead>
               <tr>
                 <td>Object</td>
                 <td>Cause</td>
                 <td>Request</td>
                 <td>Date</td>
               </tr>
             </thead>
             <tbody>
               <g:each in="${recentReviewRequests}" var="rr">
                 <tr>
                   <td><g:link controller="resource" action="show" id="${rr.componentToReview.class.name}:${rr.componentToReview.id}">${rr.componentToReview.toString()}</g:link></td>
                   <td>${rr.descriptionOfCause}</td>
                   <td>${rr.reviewRequest}</td>
                   <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${rr.dateCreated}"/></td>
                 </tr>
               </g:each>
             </tbody>
           </table>
         </g:if>
         <g:else>
           <h3>There are no outstanding review requests allocated to you</h3>
         </g:else>


         <g:if test="${(recentlyClosedActivities != null ) && ( recentlyClosedActivities.size() > 0 )}">
           <h3>Recently Closed activities</h3>
           <table class="table table-striped table-bordered">
             <thead>
               <tr>
                 <td>Activity</td>
                 <td>Type</td>
                 <td>Status</td>
                 <td>Created</td>
                 <td>Last Updated</td>
               </tr>
             </thead>
             <tbody>
               <g:each in="${recentlyClosedActivities}" var="activity">
                 <tr>
                   <td><g:link controller="workflow" action="${activity.activityAction}" id="${activity.id}">${activity.activityName?:'No name'}</g:link></td>
                   <td>${activity.type.value}</td>
                   <td>${activity.status.value}</td>
                   <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${activity.dateCreated}"/></td>
                   <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${activity.lastUpdated}"/></td>
                 </tr>
               </g:each>
             </tbody>
           </table>
         </g:if>
         <g:else>
           <h3>No recently closed activities</h3>
         </g:else>

       </div>
       <div id="recentActivity" class="col-md-6 well">
         <h3>History</h3>
         <table class="table table-striped table-bordered">
           <thead>
             <tr>
               <td>Activity</td>
               <td>Date</td>
             </tr>
           </thead>
           <tbody>
             <g:each in="${recentlyViewed}" var="activity">
               <tr>
                 <td><g:link controller="${activity.controller}" action="${activity.action}" id="${activity.actionid}">${activity.title}</g:link></td>
                 <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${activity.activityDate}"/></td>
               </tr>
             </g:each>
           </tbody>
         </table>
       </div>
     </div>
   </div>
  
  </body>
</html>
