package de.samply.transfair.models.beacon;

/**
 * Models a sample as understood by Beacon 2.x.
 */
public class BeaconBiosample {
  public String id;
  public String individualId;
  public String collectionDate;
  public BeaconSampleInfo info;
  public BeaconSampleOriginType sampleOriginType;
}
