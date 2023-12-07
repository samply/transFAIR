package de.samply.transfair.models.beacon;

/**
 * Models assay codes as understood by Beacon 2.x.
 */
public class BeaconAssayCode {
  public BeaconAssayCode(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public String id;
  public String label;
}
