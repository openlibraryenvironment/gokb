
<pre>
  FTH ${d?.fullTitleHistory}
  PT ${d?.precedingTitleId?:'null'}
</pre>
<ul>
  <g:each in="${d?.fullTitleHistory?.th}" var="theevent">
    <li>${theevent.eventDate}
      <g:each in="${theevent.participants}" var="p">
        ${p.participantRole} ${p.participant}
      </g:each>
    </li>
  </g:each>
</ul>
