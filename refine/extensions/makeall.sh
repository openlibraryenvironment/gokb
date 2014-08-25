
echo run from gokb-phase1/refine/extensions

for config in gokb_live_kuali gokb_test_kint gokb_test_kuali
do
  cd gokb
  cp module/MOD-INF/$config.properties module/MOD-INF/gokb.properties
  grep "^api.entry" module/MOD-INF/$config.properties
  ant -Drefine.dir=/home/ibbo/google-refine-2.5 -Dserver.dir=/home/ibbo/apache-tomcat
  cd ..
  zip -r $config ./gokb
  echo completed build for $config
done

