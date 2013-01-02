gokb-phase1
===========

# Phase 1
## Rules Collection / Refine Extension

* To build the refine extension, 
** cd into gokb-phase1/refine/extensions/gokb
* run "ant" with any local overrides for refine installation dir and tomcat for server jars, eg
** ant -Drefine.dir=/home/ibbo/google-refine-2.5 -Dserver.dir=/home/ibbo/apache-tomcat

Copy (Or arrange to symlink) the gokb directory to your local equivalent of ~/google-refine-2.5/webapp/extensions/ 


## Server side component (dev)

from gokb-phase1/server/gokb
grails run-app



# First run
