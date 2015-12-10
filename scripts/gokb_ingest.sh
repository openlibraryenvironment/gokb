#!/bin/bash

curl -v --user admin:admin -X POST \
  --form content=@./doaj \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="DOAJ_Complete" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit


curl -v --user admin:admin -X POST \
  --form content=@./acs_legacy_archives \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="American Chemical Society: American Chemical Society Legacy Archives" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./apa_psycarticles \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="American Psychological Association: Psychnet Articles" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./brill_online_journals \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Brill: Brill Journal Collection" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./bmj_online \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="BMJ Publishing Group: BMJ Online" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./jstor_complete \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="JSTOR: JSTOR Complete" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./npg_journals \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Nature Publishing Group: Nature Publishing Group Journals" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./oxford_open \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Oxford University Press: Open Access Journals" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./project_euclid_direct \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Project Euclid: Project Euclid Direct" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./project_euclid_free \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Project Euclid: Project Euclid Free" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit

curl -v --user admin:admin -X POST \
  --form content=@./"project_muse_alltitles" \
  --form source="CUFTS" \
  --form fmt="cufts" \
  --form pkg="Project Muse: Project MUSE All Titles" \
  --form platformUrl="http://cufts.org/doaj" \
  --form format="JSON" \
  https://gokb.k-int.com/gokb/packages/deposit
