export GOKB_HOST="http://localhost:8080"



echo 1. Load issn-l data dump
echo 2. Sync user accounts
curl -vvv --user admin:admin -X POST --form users=@./users.tsv $GOKB_HOST/gokb/api/bulkLoadUsers
echo 3. Sync orgs

echo 4. Sync titles
echo 5. Sync packages
