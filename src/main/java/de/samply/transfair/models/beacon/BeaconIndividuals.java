package de.samply.transfair.models.beacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Models a set of individuals as understood by Beacon 2.x.
 */
public class BeaconIndividuals {
  public List<BeaconIndividual> individuals = new ArrayList<>();

  /**
   * Add the supplied individual to the list of individuals.
   *
   * @param beaconIndividual individual to be added.
   */
  public void add(BeaconIndividual beaconIndividual) {
    individuals.add(beaconIndividual);
  }
}
