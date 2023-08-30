package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.models.beacon.BeaconSampleOriginType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/** Static methods for converting the bbmri.de sample type to Beacon sample type.
 */
@Slf4j
public class BbmriBeaconTypeConverter {

  private static final String TISSUE = "tissue";

  private BbmriBeaconTypeConverter() {
  }

  /** From bbmri.de to Beacon sample type (Uberon ontology). */
  public static BeaconSampleOriginType fromBbmriToBeacon(String bbmriSampleType) {
    if (Objects.isNull(bbmriSampleType)) {
      return null;
    }
    if (bbmriSampleType.toLowerCase().indexOf("ascites") >= 0) {
      return new BeaconSampleOriginType("UBERON:0007795", "ascitic fluid");
    }
    if (bbmriSampleType.toLowerCase().indexOf("bone marrow") >= 0) {
      return new BeaconSampleOriginType("UBERON:0002371", "bone marrow");
    }
    if (bbmriSampleType.toLowerCase().indexOf("csf") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001359", "cerebrospinal fluid");
    }
    if (bbmriSampleType.toLowerCase().indexOf("saliva") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001836", "saliva");
    }
    if (bbmriSampleType.toLowerCase().indexOf("stool") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001988", "feces");
    }
    if (bbmriSampleType.toLowerCase().indexOf("faeces") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001988", "feces");
    }
    if (bbmriSampleType.toLowerCase().indexOf("serum") >= 0) {
      return new BeaconSampleOriginType("OBI:0100017", "blood serum");
    }
    if (bbmriSampleType.toLowerCase().indexOf("plasma") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001969", "blood plasma");
    }
    if (bbmriSampleType.toLowerCase().indexOf("blood") >= 0) {
      return new BeaconSampleOriginType("UBERON:0000178", "blood");
    }
    if (bbmriSampleType.toLowerCase().indexOf("urine") >= 0) {
      return new BeaconSampleOriginType("UBERON:0001088", "Urine");
    }
    if (bbmriSampleType.toLowerCase().indexOf("dna") >= 0) {
      return new BeaconSampleOriginType("OBI:0001051", "DNA");
    }
    if (bbmriSampleType.toLowerCase().indexOf("rna") >= 0) {
      return new BeaconSampleOriginType("OBI:0000880", "Ribonucleic Acid");
    }
    if (bbmriSampleType.toLowerCase().indexOf("swab") >= 0) {
      return new BeaconSampleOriginType("OBI:0002819", "Swab");
    }
    if (bbmriSampleType.toLowerCase().indexOf("tissue-formalin") >= 0) {
      return new BeaconSampleOriginType("OBI:1200000",
              "Formalin-Fixed Paraffin-Embedded Tissue Sample");
    }
    if (bbmriSampleType.toLowerCase().indexOf("tissue-frozen") >= 0) {
      return new BeaconSampleOriginType("OBI:0000922", "Frozen Tissue");
    }
    if (bbmriSampleType.toLowerCase().indexOf(TISSUE) >= 0) {
      return new BeaconSampleOriginType("UBERON:0000479", TISSUE);
    }
    log.warn("No Uberon term found for sample type: " + bbmriSampleType);
    return new BeaconSampleOriginType("UBERON:0000479", TISSUE);
  }
}
