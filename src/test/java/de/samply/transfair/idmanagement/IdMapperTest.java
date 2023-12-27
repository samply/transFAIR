package de.samply.transfair.idmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import de.samply.transfair.PseudonymisationProperties;
import de.samply.transfair.idmanagement.connectors.enums.ResourceType;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 Test classes responsible for the {@link IdMapper}
 */
@SpringBootTest
public class IdMapperTest {

  public IdMapperTest(){}

  // Testing instance, mocked `resource` should be injected here

  IdMapper idMapper;

  /**
   * Tests that instance of the converter {@link IdMapper} is set up correctly and that it maps ids between the correct domains
   */
  @Test
  void idMapper(){

    PseudonymisationProperties pseudonymisationProperties = new PseudonymisationProperties();

    pseudonymisationProperties.setting = "csvmapping";
    pseudonymisationProperties.csvpath = "./test_mapping.csv";
    pseudonymisationProperties.bbmridomain = "BBMRI.Patient";
    pseudonymisationProperties.miidomain = "MII.Patient";

    idMapper = new IdMapper(pseudonymisationProperties);

    String bbmri_patient_id = "A";
    String mii_patient_id = "1";

    String bbmri_specimen_id = "B";
    String mii_specimen_id = "2";
    String mapping_string = "BBMRI.Patient,MII.Patient,BBMRI.Specimen,MII.Specimen\n"+bbmri_patient_id+","+mii_patient_id+"\n,,"+bbmri_specimen_id+","+mii_specimen_id;

    try {
      File file = new File("./test_mapping.csv");
      FileWriter writer = new FileWriter(file);
      file.deleteOnExit();
      writer.write(mapping_string);
      writer.close();
    } catch (IOException ex) {
      System.out.println(ex.getMessage());
    }

    try {
        // Test mapping MII->BBMRI
        assertEquals(bbmri_patient_id, idMapper.toBbmri(mii_patient_id, ResourceType.PATIENT));
        //assertEquals(bbmri_specimen_id, idMapper.toBbmri(mii_specimen_id, ResourceType.SPECIMEN));

        //Test mapping BBMRI->MII
        assertEquals(mii_patient_id, idMapper.toMii(bbmri_patient_id, ResourceType.PATIENT));
        //assertEquals(mii_specimen_id, idMapper.toMii(bbmri_specimen_id, ResourceType.SPECIMEN));
    }catch(Exception e){
        System.out.println("Unexpected exception thrown!");
        e.printStackTrace();
        fail();
    }

  }

}
