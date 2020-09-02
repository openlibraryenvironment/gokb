export GOKB_HOST="http://localhost:8080"

curl -v --user admin:admin -X POST \
  --form content=@./availiability_test_1.tsv \
  --form source="TESTPROVIDER" \
  --form fmt="elsevier" \
  --form pkg="Availability Test - pakage#1" \
  --form platformUrl="http://www.testprovider.com" \
  --form format="JSON" \
  --form providerName="testProvider" \
  --form providerIdentifierNamespace="TestProvider" \
  --form reprocess="Y" \
  --form synchronous="Y" \
  --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
  $GOKB_HOST/gokb/packages/deposit

