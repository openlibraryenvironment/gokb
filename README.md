gokb-phase1
===========

# Phase 1
## Rules Collection / Refine Extension

* To build the refine extension, 
** cd into gokb-phase1/refine/extensions/gokb
* run "ant" with any local overrides for refine installation dir and tomcat for server jars, eg
** ant -Drefine.dir=/home/ibbo/google-refine-2.5 -Dserver.dir=/home/ibbo/apache-tomcat

Copy (Or arrange to symlink) the gokb directory to your local equivalent of ~/google-refine-2.5/webapp/extensions/ 


I packaged up the gokb extension with #

 zip -r gokb.zip ./gokb


## Server side component (dev)

from gokb-phase1/server/gokb
grails run-app



# First run




# Orgs Import

A script is provdied to import the ncsu orgs list, run from gokb-phase1/scripts/import/orgs as follows:

./ncsu_orgs_imp.groovy ./ncsu-auth-orgs-roles-2013-01-11.csv 





