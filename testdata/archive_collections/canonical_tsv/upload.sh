curl -v --user admin:admin -X POST \
  --form content=@./test_archival_format.tsv\
  --form source="DAC_TEST" \
  --form fmt="DAC" \
  --form pkg="DAC Test Ingest" \
  --form platformUrl="http://dactest.com" \
  --form format="tsv" \
  --form providerName="DACTEST" \
  --form providerIdentifierNamespace="DACTEST" \
  --form reprocess="Y" \
  --form synchronous="Y" \
  --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
  localhost:8080/gokb/packages/deposit

