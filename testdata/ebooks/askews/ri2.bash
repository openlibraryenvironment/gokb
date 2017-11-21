#!/bin/bash
# 
export GOKB_HOST="http://ebookstrial.k-int.com/"

#~/DATA/askews/*.xml`

for f in ./*.csv
do
    echo $f
    cat $f | sed 's/¬/\\"/g' > /tmp/askfile.tsv
    grep ¬ /tmp/askfile.tsv

    curl -v --user admin:admin -X POST \
      --form content=@/tmp/askfile.tsv \
      --form source="ASKEWS" \
      --form fmt="askews" \
      --form pkg="Askews And Holts MasterList" \
      --form platformUrl="http://www.askewsandholts.com" \
      --form format="json" \
      --form providerName="Askews And Holts" \
      --form providerIdentifierNamespace="askews" \
      --form reprocess="Y" \
      --form incremental="Y" \
      --form synchronous="Y" \
      --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
      $GOKB_HOST/gokb/packages/deposit
done


