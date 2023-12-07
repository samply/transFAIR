package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.models.beacon.BeaconSex;

/** Static methods for converting the bbmri.de sex to Beacon sex. */
public class BbmriBeaconSexConverter {

  private BbmriBeaconSexConverter() {
  }

  /** From bbmri.de to Beacon sex/gender. */
  public static BeaconSex fromBbmriToBeacon(String bbmriGender) {
    if (bbmriGender.equalsIgnoreCase("male")) {
      return new BeaconSex("NCIT:C20197", "male");
    }
    if (bbmriGender.equalsIgnoreCase("female")) {
      return new BeaconSex("NCIT:C16576", "female");
    }
    return new BeaconSex("NCIT:C1799", "unknown");
  }
}
