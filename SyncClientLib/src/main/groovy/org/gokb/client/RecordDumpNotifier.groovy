package org.gokb.client

public class RecordDumpNotifier implements GokbUpdateTarget {

  public static void main(String[] args) {

    println("RecordDumpNotifier::main(${args})");

    Sync sc = new Sync()
    if ( args.length > 0 )
      sc.doSync(args[0], new RecordDumpNotifier())
    else
      sc.doSync('http://localhost:8080', new RecordDumpNotifier())
  }

  public void notifyChange(GokbPackageDTO dto) {
    println(dto.dump());
  }

}

