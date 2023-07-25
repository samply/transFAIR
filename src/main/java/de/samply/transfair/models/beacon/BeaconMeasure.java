package de.samply.transfair.models.beacon;

/**
 * Models a measure as understood by Beacon 2.x.
 */
public class BeaconMeasure {
  public BeaconAssayCode assayCode;
  public String date;
  public BeaconMeasurementValue measurementValue;

  /**
   * Create a BeaconMeasure object for the given BMI.
   *
   * @param bmi BMI in kg/m^2.
   * @return constructed BeaconMeasure object
   */
  public static BeaconMeasure makeBmiMeasure(double bmi, String date) {
    BeaconAssayCode assayCode = new BeaconAssayCode("LOINC:35925-4", "BMI");
    BeaconMeasurementValue measurementValue = new BeaconMeasurementValue();
    measurementValue.units = new BeaconUnits("NCIT:C49671", "Kilogram per Square Meter");
    measurementValue.value = bmi;
    BeaconMeasure measure = new BeaconMeasure();
    measure.assayCode = assayCode;
    measure.measurementValue = measurementValue;
    if (date != null) {
      measure.date = date;
    }

    return measure;
  }

  /**
   * Create a BeaconMeasure object for the given BMI.
   *
   * @param weight Weight in kg.
   * @return constructed BeaconMeasure object
   */
  public static BeaconMeasure makeWeightMeasure(double weight, String date) {
    BeaconAssayCode assayCode = new BeaconAssayCode("LOINC:3141-9", "Weight");
    BeaconMeasurementValue measurementValue = new BeaconMeasurementValue();
    measurementValue.units = new BeaconUnits("NCIT:C28252", "Kilogram");
    measurementValue.value = weight;
    BeaconMeasure measure = new BeaconMeasure();
    measure.assayCode = assayCode;
    measure.measurementValue = measurementValue;
    if (date != null) {
      measure.date = date;
    }

    return measure;
  }

  /**
   * Create a BeaconMeasure object from the given BMI and weight.
   *
   * @param height Height in cm.
   * @return constructed BeaconMeasure object
   */
  public static BeaconMeasure makeHeightMeasure(double height, String date) {
    BeaconAssayCode assayCode = new BeaconAssayCode("LOINC:8308-9", "Height-standing");
    BeaconMeasurementValue measurementValue = new BeaconMeasurementValue();
    measurementValue.units = new BeaconUnits("NCIT:C49668", "Centimeter");
    measurementValue.value = height;
    BeaconMeasure measure = new BeaconMeasure();
    measure.assayCode = assayCode;
    measure.measurementValue = measurementValue;
    if (date != null) {
      measure.date = date;
    }

    return measure;
  }
}
