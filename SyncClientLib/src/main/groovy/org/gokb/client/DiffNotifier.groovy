package org.gokb.client

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import java.io.File;
import com.sleepycat.je.DatabaseEntry;


public class DiffNotifier implements GokbUpdateTarget {

  Environment myDbEnv;
  Database db

  public DiffNotifier() {
    println("DiffNotifier::DiffNotifier()");
  }

  public void init() {
    println("\n\n***init()");
    EnvironmentConfig envConfig = new EnvironmentConfig();
    DatabaseConfig dbconfig = new DatabaseConfig();
    envConfig.setAllowCreate(true);
    dbconfig.setAllowCreate(true);
    // storeConfig.setAllowCreate(true);
    def env_file = new File("dbEnv")
    if ( !env_file.exists() ) {
      println("Create dbEnv");
      env_file.mkdir();
    }
    myDbEnv = new Environment(env_file, envConfig);
    db = myDbEnv.openDatabase(null, "PackageDB", dbconfig);

    // store = new EntityStore(myDbEnv, "EntityStore", storeConfig);
  }

  public void shutdown() {
    println("\n\n***shutdown");
    db.close();
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
    byte[] data = [ 01, 02, 03 ]

    DatabaseEntry theKey = new DatabaseEntry(dto.packageId.getBytes("UTF-8"));
    DatabaseEntry theData = new DatabaseEntry(data);

    db.put(null, theKey, theData);
  }

}

