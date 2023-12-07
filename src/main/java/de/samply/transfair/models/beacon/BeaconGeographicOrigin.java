package de.samply.transfair.models.beacon;

/**
 * Models geographic origin as understood by Beacon 2.x.
 */
public class BeaconGeographicOrigin {
  public BeaconGeographicOrigin(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id;
  public String label;
}
