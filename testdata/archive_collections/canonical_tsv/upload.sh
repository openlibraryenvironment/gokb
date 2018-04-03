curl -v --user admin:admin -X POST \
  --form content=@./test_archival_format.tsv\
  --form source="DAC_TEST" \
  --form fmt="DAC" \
  --form pkg="DAC Test Ingest 2" \
  --form platformUrl="http://dactest.com" \
  --form format="tsv" \
  --form providerName="DACTEST" \
  --form providerIdentifierNamespace="DACTEST" \
  --form reprocess="Y" \
  --form synchronous="Y" \
  --form curatoryGroup="Jisc" \
  --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
  localhost:8080/packages/deposit


# N.B. upload URL for v7 and before is /gokb/packages/deposit. When running with grails run-app for G3 and above, its just /packages/deposit
