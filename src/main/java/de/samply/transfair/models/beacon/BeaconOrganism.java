package de.samply.transfair.models.beacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Models an organism as understood by Beacon 2.x.
 */
public class BeaconOrganism {
  public List<String> ontologyTerms;
  public String text;

  /**
   * Create an instance of this class that is specific to H. Sapiens.
   *
   * @return Instance of this class.
   */
  public static BeaconOrganism createHumanBeaconOrganism() {
    BeaconOrganism beaconOrganism = new BeaconOrganism();
    beaconOrganism.text = "Homo sapiens";
    beaconOrganism.ontologyTerms = new ArrayList<>();
    beaconOrganism.ontologyTerms.add("http://purl.obolibrary.org/obo/NCBITaxon_9606");

    return beaconOrganism;
  }
}
