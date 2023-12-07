package de.samply.transfair.models.beacon;

/**
 * Models units as understood by Beacon 2.x.
 */
public class BeaconUnits {
  public BeaconUnits(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id;
  public String label;
}
