package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.models.beacon.BeaconGeographicOrigin;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/** Static methods for converting the bbmri.de address to Beacon geographic location.
 * See full list here:
 * https://github.com/EnvironmentOntology/gaz/blob/master/src/ontology/gaz_countries.csv
 * */
@Slf4j
public class BbmriBeaconAddressConverter {
  private static final String GEOGRAPHIC_LOCATION_ID = "GAZ:00000448";

  private BbmriBeaconAddressConverter() {
    
  }

  /** From bbmri.de to Beacon address. */
  public static BeaconGeographicOrigin fromBbmriToBeacon(String bbmriCountry) {
    if (Objects.isNull(bbmriCountry)) {
      return null;
    }
    if (bbmriCountry.equalsIgnoreCase("Italy")) {
      return new BeaconGeographicOrigin("GAZ:00002650", "Italy");
    }
    if (bbmriCountry.equalsIgnoreCase("Malta")) {
      return new BeaconGeographicOrigin("GAZ:00004017", "Malta");
    }
    if (bbmriCountry.equalsIgnoreCase("Spain")) {
      return new BeaconGeographicOrigin("GAZ:00000591", "Spain");
    }
    if (bbmriCountry.equalsIgnoreCase("UK")) {
      return new BeaconGeographicOrigin("GAZ:00002637", "United Kingdom");
    }
    if (bbmriCountry.equalsIgnoreCase("USA")) {
      return new BeaconGeographicOrigin("GAZ:00002459", "United States of America");
    }
    log.warn("No Beacon geographic origin found for: " + bbmriCountry);
    return new BeaconGeographicOrigin(GEOGRAPHIC_LOCATION_ID, "geographic location");
  }

  /**
   * Tests the supplied string to see if it corresponds to a known country.
   *
   * @param potentialCountry String that needs to be tested.
   * @return True, if the string is a country.
   */
  public static boolean isCountry(String potentialCountry) {
    BeaconGeographicOrigin beaconGeographicOrigin =
            BbmriBeaconAddressConverter.fromBbmriToBeacon(potentialCountry);

    return !Objects.isNull(beaconGeographicOrigin)
            &&
            !beaconGeographicOrigin.id.equals(BbmriBeaconAddressConverter.GEOGRAPHIC_LOCATION_ID);
  }
}
