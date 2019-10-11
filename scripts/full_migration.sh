export GOKB_HOST="http://localhost:8080"



# echo 1. Load issn-l data dump
# Got DB Dump for that

# echo 2. Sync user accounts
# curl -vvv --user admin:admin -X POST --form users=@./users.tsv $GOKB_HOST/gokb/api/bulkLoadUsers

echo 3. Sync orgs
groovy ./sync_gokb_orgs.groovy

# echo 4. Sync sources
# groovy ./sync_gokb_sources.groovy


echo 5. Sync titles
groovy ./sync_gokb_titles.groovy

echo 6. Sync platforms
groovy ./sync_gokb_platforms.groovy

echo 7. Sync packages
groovy ./sync_gokb_packages.groovy
