package org.gokb.client

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import java.io.File;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

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

    DatabaseEntry theKey = new DatabaseEntry(dto.packageId.getBytes("UTF-8"));
    DatabaseEntry theData = new DatabaseEntry(); // new DatabaseEntry(dto.packageId.getBytes("UTF-8"));

    // See if the key is already present
    if (db.get(null, theKey, theData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
      byte[] data_bytes = theData.getData();

      println("Got existing entry for ${dto.packageId}.. this is an update (bytes.length=${data_bytes.length})");

      def existing_package = bytesToPackage(data_bytes);

      if ( existing_package != null ) {
        println("Compare existing package and new package...");
        existing_package.compareWithPackage(dto);
      }
      else {
        println("Unable to retrieve package info from local store");
      }

      theData = new DatabaseEntry(getBytesForPackage(dto));
      db.put(null, theKey, theData);
    }
    else {
      println("New record for ${dto.packageId}");
      theData = new DatabaseEntry(getBytesForPackage(dto));
      db.put(null, theKey, theData);
    }
  }

  private byte[] getBytesForPackage(GokbPackageDTO dto) {
    byte[] result = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream()
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(dto);
      out.close();
      baos.close();
      result = baos.toByteArray();
    }catch(IOException i) {
      i.printStackTrace();
    }
    return result;
  }

  private GokbPackageDTO bytesToPackage(byte[] bytes) {
    GokbPackageDTO result = null;
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      ObjectInputStream is = new ObjectInputStream(bais);
      result = (GokbPackageDTO) is.readObject();
      is.close();
      bais.close();
    }catch(IOException i) {
      i.printStackTrace();
    }catch(ClassNotFoundException c) {
      c.printStackTrace();
    }
    return result;
  }
}

