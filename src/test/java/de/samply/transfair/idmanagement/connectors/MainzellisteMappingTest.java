package de.samply.transfair.idmanagement.connectors;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class MainzellisteMappingTest {

  /**
   * Preliminary test. Requires a Mainzelliste instance at localhost:8080 with ApiKey ApiKeyTest1234 and a Patient with
   * pid 0003Y0WZ and intid 1. To receive such an instance, perform the following:
   * 1.) Download Mainzelliste Docker deployment
   * 2.) Configure Mainzelliste ApiKey in docker-compose file via environment variable
   * 3.) Add a patient to Mainzelliste with pid and intid from standardconfiguration
   * 4.) Replace id_a and/or id_B in the test if necessary
   */
  @Test
  void mainzellisteMapping() {
    MainzellisteMapping mainzelliste_mapping = new MainzellisteMapping("http://localhost:8080", "ApiKeyTest1234");
    String res = "";

    String domain_A = "pid";
    String id_A = "0003Y0WZ";
    String domain_B = "intid";
    String id_B = "1";

    try {
      res = mainzelliste_mapping.fetchMapping("0003Y0WZ", "pid", "intid");
    }catch(Exception e){
      e.printStackTrace();
    }
    assertEquals(id_B, res);

    try {
      res = mainzelliste_mapping.fetchMapping("1", "intid", "pid");
    }catch(Exception e){
      e.printStackTrace();
    }
    assertEquals(id_A, res);
  }
}
