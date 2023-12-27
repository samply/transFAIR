package de.samply.transfair.idmanagement.connectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
  Test classes responsible for ID mapping. In particular {@link IdMapping}, {@link CsvMapping} and {@link IdentityMapping}.
 */
class IdMappingTest {

  private final String csvPathConfig;
  String domA;
  String domB;

  String[] idsA;
  String[] idsB;

  public IdMappingTest(){
    this.csvPathConfig = "./test_mapping.csv";
    this.domA = "Domain_A";
    this.domB = "Domain_B";
    this.idsA = new String[]{"A", "B", "C", "D", "E"};
    this.idsB = new String[]{"1", "2", "3", "4", "5"};
  }

  /**
   * Test construction of {@link CsvMapping} object which inherits from ID_Mapping
   */
  @Test
  void generateObject() {
    CsvMapping csvmapping;
    csvmapping = new CsvMapping(); // TODO: Assertion!
    assertInstanceOf(CsvMapping.class, csvmapping);
    csvmapping = new CsvMapping(csvPathConfig);
    assertInstanceOf(CsvMapping.class, csvmapping);
  }

  /**
   * Test set_mappings and set_mapping function of class {@link IdMapping} by using instance of inherited class {@link CsvMapping}
   */
  @Test
  void addMapping() {
    IdMapping mapping = new CsvMapping();

    // Test exceptions
    String[] tooShort = {"A"};
    Exception exception = assertThrows(IllegalArgumentException.class, () -> {
      mapping.setMappings(domA, domB, tooShort, idsB); //Should throw IllegalArgumentException, because ID lists do not have the same length
    });
    assertTrue(exception.getMessage().contains("ID lists must have same length! First list has length "+tooShort.length+" and second list has length "+ idsB.length));

    String theSame = "TheSame";
    exception = assertThrows(IllegalArgumentException.class, () -> {
      mapping.setMapping(theSame, theSame, idsA[0], idsB[0]); // Should throw IllegalArgumentException, because domain names are equal
    });
    assertTrue(exception.getMessage().contains("Equal domain names not allowed! Both domain names are '"+theSame+"'"));

    exception = assertThrows(IllegalArgumentException.class, () -> {
      mapping.setMapping("", domB, idsA[0], idsB[0]); // Should throw IllegalArgumentException, because one domain name is empty
    });
    assertTrue(exception.getMessage().contains("Empty domain name is not allowed!"));

    exception = assertThrows(IllegalArgumentException.class, () -> {
      mapping.setMapping(domA, domB, "", idsB[0]); //Should throw IllegalArgumentException, because empty ID is not allowed
    });
    assertTrue(exception.getMessage().contains("Empty ID is not allowed!"));

    //Add single mapping and test whether it is stored correctly
    mapping.setMapping(domA, domB, idsA[0], idsB[0]);
    assertTrue(mapping.getMappingsCache().containsKey(domA));
    assertTrue(mapping.getMappingsCache().get(domA).containsKey(domB));
    HashMap<String, String> A_to_B = mapping.getMappingsCache().get(domA).get(domB); //
    assertEquals(idsB[0], A_to_B.get(idsA[0]));

    assertTrue(mapping.getMappingsCache().containsKey(domB));
    assertTrue(mapping.getMappingsCache().get(domB).containsKey(domA));
    HashMap<String, String> B_to_A = mapping.getMappingsCache().get(domB).get(domA);
    assertEquals(idsA[0], B_to_A.get(idsB[0]));

    // Add multiple mappings batch-wise and test whether they are stored correctly
    mapping.setMappings(domA, domB, idsA, idsB);
    for(int i=0; i< idsA.length; i++){
      assertEquals(idsB[i], A_to_B.get(idsA[i]));
    }
    for(int i=0; i< idsB.length; i++){
      assertEquals(idsA[i], B_to_A.get(idsB[i]));
    }

  }

  /**
   * Test map_id function of class {@link IdMapping} when reading from cache. Instance of inherited class {@link CsvMapping} is used.
   */
  @Test
  void mapID() {
    IdMapping mapping = new CsvMapping();
    mapping.setMappings(domA, domB, idsA, idsB); // Add mappings to be tested

    int idx = 1; // Use mapping at index 1 for whole test function

    // Test exceptions
    String non_existing_domain = "non_existing_domain";
    Exception exception = assertThrows(Exception.class, () -> {
      mapping.mapId(idsA[idx], non_existing_domain, domB); // Should throw Exception, because mapping between these domains does not exist
    });

    String non_existing_id = "Non_existing_ID";
    exception = assertThrows(Exception.class, () -> {
      mapping.mapId(non_existing_id, domA, domB); // Should throw Exception, because ID does not exist in source domain
    });

    // Test whether correct mapping is returned in both directions
    try {
      assertEquals(idsB[idx], mapping.mapId(idsA[idx], domA, domB));
      assertEquals(idsA[idx], mapping.mapId(idsB[idx], domB, domA));
    }catch(Exception e){ //Should usually not happen
      System.out.println("Unexpected exception thrown!");
      fail();
    }
  }

  /**
   * Test import of mappings in {@link CsvMapping} from csv file
   */
  @Test
  void csvImport() {

    File file;
    FileWriter writer;
    String filepath_broken = "./broken_file.csv";

    String dom_C = "Domain_C";
    String[] IDs_A = {"A", "B", "", ""}; // The fact that row[3] is smaller than the others should not lead to an exception during the test!
    String[] IDs_B = {"1", "2", "3", "", ""};
    String[] IDs_C = {"alpha", "", "gamma", "", "epsilon"};

    //Create test mapping as csv file
    StringBuilder mapping_string = new StringBuilder(domA + "," + domB + "," + dom_C + "\n");
    for (int i = 0; i < IDs_A.length; i++) {
      mapping_string.append(IDs_A[i]).append(",").append(IDs_B[i]).append(",").append(IDs_C[i]).append("\n");
    }

    try {
      file = new File(csvPathConfig);
      writer = new FileWriter(file);
      file.deleteOnExit();
      writer.write(mapping_string.toString());
      writer.close();

      file = new File(filepath_broken);
      writer = new FileWriter(file);
      file.deleteOnExit();
      writer.write("\",\n");
      writer.close();

    } catch (IOException ex) {
      System.out.println(ex.getMessage());
    }

    CsvMapping csv_mapping = new CsvMapping();

    // Test exceptions
    assertThrows(Exception.class, () -> { //TODO Assert based on second exception in stack and based on message of second exception?
      csv_mapping.setFilepath("./Non_existing_file.csv");
      csv_mapping.mapId(IDs_A[0], domA, domB); // Should throw IOException, because file can not be read
    });

    assertThrows(Exception.class, () -> { //TODO Assert based on second exception in stack and based on message of second exception?
      csv_mapping.setFilepath(filepath_broken);
      csv_mapping.mapId(IDs_A[0], domA, domB); // Should throw CsvMalformedLineException, because file is not a proper csv
    });

    // Test if mappings are read correctly
    // Use modified this.IDs_A and this.IDs_B (see top of method)
    csv_mapping.setFilepath(csvPathConfig); //TODO Is this really the correct filename?

    //Index 0: Mapping between all three
    try {
      assertEquals(IDs_B[0], csv_mapping.mapId(IDs_A[0], domA, domB)); //A->B
      assertEquals(IDs_A[0], csv_mapping.mapId(IDs_B[0], domB, domA)); //B->A
      assertEquals(IDs_C[0], csv_mapping.mapId(IDs_A[0], domA, dom_C)); //A->C
      assertEquals(IDs_A[0], csv_mapping.mapId(IDs_C[0], dom_C, domA)); //C->A
      assertEquals(IDs_C[0], csv_mapping.mapId(IDs_B[0], domB, dom_C)); //B->C
      assertEquals(IDs_B[0], csv_mapping.mapId(IDs_C[0], dom_C, domB)); //C->B
    }catch(Exception e){ //Should usually not happen
      System.out.println("Unexpected exception thrown!");
      e.printStackTrace();
      fail();
    }

    //Index 1: Mapping between domain A and domain B
    try{
      assertEquals(IDs_B[1], csv_mapping.mapId(IDs_A[1], domA, domB)); //A->B
      assertEquals(IDs_A[1], csv_mapping.mapId(IDs_B[1], domB, domA)); //B->A
    }catch(Exception e){ //Should usually not happen
      System.out.println("Unexpected exception thrown!");
      e.printStackTrace();
      fail();
    }
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_A[1], domA, dom_C)); //A->C //TODO Specify exception type?
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[1], dom_C, domA)); //C->A
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[1], domB, dom_C)); //B->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[1], dom_C, domB)); //C->B

    //Index 2: Mapping between domain B and domain C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_A[2], domA, domB)); //A->B
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[2], domB, domA)); //B->A
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_A[2], domA, dom_C)); //A->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[2], dom_C, domA)); //C->A
    try{
      assertEquals(IDs_C[2], csv_mapping.mapId(IDs_B[2], domB, dom_C)); //B->C
      assertEquals(IDs_B[2], csv_mapping.mapId(IDs_C[2], dom_C, domB)); //C->B
    }catch(Exception e){ //Should usually not happen
      System.out.println("Unexpected exception thrown!");
      e.printStackTrace();
      fail();
    }

    //Index 3: No mapping as all three empty
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_A[3], domA, domB)); //A->B
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[3], domB, domA)); //B->A
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_A[3], domA, dom_C)); //A->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[3], dom_C, domA)); //C->A
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[3], domB, dom_C)); //B->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[3], dom_C, domB)); //C->B

    //Index 4: No mapping, as there is just ID in domain C
    // Domain A does not have this index //A->B
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[4], domB, domA)); //B->A
    // Domain A does not have this index //A->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[4], dom_C, domA)); //C->A
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_B[4], domB, dom_C)); //B->C
    assertThrows(Exception.class, () -> csv_mapping.mapId(IDs_C[4], dom_C, domB)); //C->B
  }

  /**
   * Tests that the identity mapper {@link IdentityMapping} returns the input id
   */
  @Test
  void identityMapper(){
    String id = "A";
    IdentityMapping identity_mapper = new IdentityMapping();
    assertEquals(id, identity_mapper.mapId(id, domA, domB)); // id itself should be returned in both directions
    assertEquals(id, identity_mapper.mapId(id, domB, domA)); // id itself should be returned in both directions
  }

}
