package de.samply.transfair.models.beacon;

/**
 * Models sex as understood by Beacon 2.x.
 */
public class BeaconSex {
  public BeaconSex(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id;
  public String label;
}
