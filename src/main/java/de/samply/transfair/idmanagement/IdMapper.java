package de.samply.transfair.idmanagement;


import de.samply.transfair.configuration.Configuration;
import de.samply.transfair.idmanagement.connectors.CsvMapping;
import de.samply.transfair.idmanagement.connectors.IdMapping;
import de.samply.transfair.idmanagement.connectors.IdentityMapping;
import de.samply.transfair.idmanagement.connectors.MainzellisteMapping;
//import de.samply.transfair.idmanagement.connectors.MosaicMapping;
import de.samply.transfair.idmanagement.connectors.enums.ResourceType;
import de.samply.transfair.idmanagement.connectors.exceptions.IdMappingException;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * This class holds the different ID's and can map to the opposite project.
 * It uses the Singleton pattern in order to be accessible from the whole project.
 */
@Slf4j
public class IdMapper {

  private static final String SPECIMEN = "Specimen";

  private static final String PATIENT = "Patient";

  private IdMapping idMapping;

  Configuration configuration;

  /** New Idmapper. */
  public IdMapper(Configuration configuration) {
    this.configuration = configuration;
    this.mapperSetting = this.configuration.getIdMapperSetting();
    this.patientPseudonymDomainBbmri = this.configuration.getBbmriIdDomain();
    this.patientPseudonymDomainMii = this.configuration.getMiiIdDomain();

    if (this.configuration.getIdMapperSetting().equals("csvmapping")) {
      log.info("Using csvmapping " + configuration.getIdMappingCsvPath());
      this.idMapping = new CsvMapping(configuration.getIdMappingCsvPath());
    } else if (this.configuration.getIdMapperSetting().equals("none")) {
      log.info("No ID-Mapping used");
    } else {
      // If none of the above settings is matched
      log.info("No ID-Mappings defined");
      this.idMapping = new IdentityMapping();
    }
  }

  private static final String PREFIX_BBMRI = "BBMRI."; // Prefix for IDs within BBMRI FHIR-Store
  private static final String PREFIX_MII = "MII."; // Prefix for IDs within MII FHIR-Store
  private final String mapperSetting; //TODO: Pull from configuration
  private final String patientPseudonymDomainBbmri; //TODO: pull from configuration
  private final String patientPseudonymDomainMii; //TODO: pull from configuration

  private String csvMappingsPath; //TODO: Pull from configuration

  /** New Idmapper. */
  public IdMapper() {
    // FIXME injections do not work in test...
    this.mapperSetting = "csvmapping";
    this.csvMappingsPath = "./test_mapping.csv";

    this.patientPseudonymDomainBbmri = "pid";
    this.patientPseudonymDomainMii = "intid";
  }

  @PostConstruct
   void setup() {
    // injection. @PostConstruct causes NullpointerException in .to... methods
    switch (this.mapperSetting) {
      case "csvmapping" -> {
        // log.info("Using csvmapping " + this.csv_mappings_path); //TODO: Readd
        this.idMapping = new CsvMapping(this.csvMappingsPath);
      }
      case "mainzellistemapping" -> {
        //TODO: read base_url and api_key from configuration
        String baseUrl = "http://localhost:8080";
        String apiKey = "ApiKeyTest1234";
        this.idMapping = new MainzellisteMapping(baseUrl, apiKey);
      }
      /*case "mosaicmapping" -> {
        // TODO: Pull base_url from configuration
        String baseUrl = "";
        this.idMapping = new MosaicMapping("localhost:8080", "ukaachen");
      }*/
      default -> { // If none of the above settings is matched
        // log.info("No ID-Mappings defined"); //TODO: Readd
        this.idMapping = new IdentityMapping();
      }
    }
  }

  /**
   * Standard getter for this.mapper_setting.
   *
   * @return setting, which type of mapper should be used (e.g. csvmapper)
   */
  public String getMapperSetting() {
    return this.mapperSetting;
  }

  /**
   * Standard getter for this.csv_mappings_path.
   *
   * @return setting, where csv file with mappings is located
   */
  public String getCsvMappingsPath() {
    return this.csvMappingsPath;
  }

  /**
   * Maps ids from MII domains to BBMRI domains depending on the FHIR resource type.
   *
   * @param id the id to be mapped to BBMRI domain
   * @param resourceType define which FHIR resource type the id belongs to - e.g. Patient, Specimen
   * @return id from the respective BBMRI domain
   * @throws IllegalArgumentException escalates exceptions from {@link IdMapping}.map_id method
   */
  public String toBbmri(String id, ResourceType resourceType) throws IdMappingException {
    return switch (resourceType) {
      case PATIENT -> this.idMapping
          .mapId(id, patientPseudonymDomainMii, patientPseudonymDomainBbmri);
      case SPECIMEN -> null;
    };
  }

  /**
   * Maps ids from BBMRI domains to MII domains depending on the FHIR resource type.
   *
   * @param id the id to be mapped to MII domain
   * @param resourceType define which FHIR resource type the id belongs to - e.g. Patient, Specimen
   * @return id from the respective MII domain
   * @throws IllegalArgumentException escalates exceptions from {@link IdMapping}.map_id method
   */
  public String toMii(String id, ResourceType resourceType) throws IdMappingException {
    return switch (resourceType) {
      case PATIENT -> idMapping
          .mapId(id, patientPseudonymDomainBbmri, patientPseudonymDomainMii);
      case SPECIMEN -> null;
    };
  }
}
