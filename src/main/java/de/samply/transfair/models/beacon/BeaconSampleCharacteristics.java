package de.samply.transfair.models.beacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Models sample characteristics as understood by Beacon 2.x.
 */
public class BeaconSampleCharacteristics {
  public List<BeaconOrganism> organism;

  /**
   * Create an instance of this class that is specific to H. Sapiens.
   *
   * @return Instance of this class.
   */
  public static BeaconSampleCharacteristics createHumanSampleCharacteristics() {
    BeaconSampleCharacteristics characteristics = new BeaconSampleCharacteristics();
    List<BeaconOrganism> organisms = new ArrayList<>();
    organisms.add(BeaconOrganism.createHumanBeaconOrganism());
    characteristics.organism = organisms;

    return characteristics;
  }
}
