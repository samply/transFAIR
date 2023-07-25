package de.samply.transfair.models.beacon;

import java.util.ArrayList;
import java.util.List;

/**
 * Models a set of samples as understood by Beacon 2.x.
 */
public class BeaconBiosamples {
  public List<BeaconBiosample> biosamples = new ArrayList<>();

  /**
   * Add the supplied sample to the list of individuals.
   *
   * @param beaconBiosample sample to be added.
   */
  public void add(BeaconBiosample beaconBiosample) {
    biosamples.add(beaconBiosample);
  }
}
