package org.gokb.client

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.StoreConfig;
import java.io.File;

public class DiffNotifier implements GokbUpdateTarget {

  Environment myDbEnv;
  EntityStore store;

  public DiffNotifier() {
    println("DiffNotifier::DiffNotifier()");
  }

  public void init() {
    println("\n\n***init()");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    StoreConfig storeConfig = new StoreConfig();
    envConfig.setAllowCreate(true);
    storeConfig.setAllowCreate(true);
    myDbEnv = new Environment(new File("dbEnv"), envConfig);
    store = new EntityStore(myDbEnv, "EntityStore", storeConfig);
  }

  public void shutdown() {
    println("\n\n***shutdown");
    store.close();
    myDbEnv.close();
  }

  public static void main(String[] args) {

    println("DiffNotifier::main(${args})");

    Sync sc = new Sync()
    DiffNotifier dn = new DiffNotifier();

    dn.init();

    if ( args.length > 0 )
      sc.doSync(args[0], dn);
    else
      sc.doSync('http://localhost:8080', dn);

    dn.shutdown()
  }

  public void notifyChange(GokbPackageDTO dto) {
    println("DiffNotifier::notifyChange on ${dto.packageName} (${dto.packageId})")
  }

}

