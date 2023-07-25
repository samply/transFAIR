package de.samply.transfair.models.beacon;

import java.util.List;

/**
 * Models an individual as understood by Beacon 2.x.
 */
public class BeaconIndividual {
  public String id;
  public BeaconSex sex;
  public List<BeaconMeasure> measures;
  public BeaconGeographicOrigin geographicOrigin;
}
