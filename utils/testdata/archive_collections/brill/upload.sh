curl -v --user admin:admin -X POST \
  --form content=@./Brill-DAC.tsv\
  --form source="BRILL_CSV" \
  --form fmt="DAC" \
  --form pkg="Brill primary source collection" \
  --form platformUrl="http://brill.com" \
  --form format="tsv" \
  --form providerName="Brill" \
  --form providerIdentifierNamespace="BRILL" \
  --form reprocess="Y" \
  --form synchronous="Y" \
  --form curatoryGroup="Jisc" \
  --form pkg.price="1.23 GBP" \
  --form pkg.price.topup="100.00 GBP" \
  --form pkg.price.perpetual="1000.00 GBP" \
  --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
  localhost:8080/packages/deposit


# N.B. upload URL for v7 and before is /gokb/packages/deposit. When running with grails run-app for G3 and above, its just /packages/deposit
