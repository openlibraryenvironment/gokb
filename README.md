gokb
====

GOKB is an environment for managing the collaborative import, cleaning and maintaining of publisher title lists. The focus is the supply of information around the administration and availability of print and electronic resources rather than specific bibliographic information. The goal of GOKb is to reduce the burden of data maintenance and editing on any one party by sharing the load over a collaborative network of subject matter experts. Because the information is global, and the same for everyone, the idea is to focus all our effort on editing one copy of the information that can then be freely reused and shared.

Some screenshots follow

![Dashboard](https://raw.github.com/k-int/gokb-phase1/dev/images/dash.png)
![File Upload](https://raw.github.com/k-int/gokb-phase1/dev/images/licenses.png)
![Login](https://raw.github.com/k-int/gokb-phase1/dev/images/login.png)
![Master List](https://raw.github.com/k-int/gokb-phase1/dev/images/masterlist.png)
![Perms](https://raw.github.com/k-int/gokb-phase1/dev/images/perms.png)
![Title Full](https://raw.github.com/k-int/gokb-phase1/dev/images/titlefull.png)
![Titles Side-By-Side](https://raw.github.com/k-int/gokb-phase1/dev/images/titles-sidebyside.png)
![ToDos](https://raw.github.com/k-int/gokb-phase1/dev/images/todos.png)
![Welcome](https://raw.github.com/k-int/gokb-phase1/dev/images/welcome.png)

# Development Information





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





