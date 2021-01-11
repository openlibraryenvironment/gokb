# #!/bin/bash
# 
export GOKB_HOST="http://localhost:8080"

#~/DATA/askews/*.xml`

for xml in `ls ~/DATA/askews/KB_20151014120236.xml` 
do
    echo process $xml
    xsltproc ./askews.xsl $xml | sed 's/¬/\\"/g' > /tmp/askfile.tsv
    
    # Convert all ¬ to """ in /tmp/askfile.tsv

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


