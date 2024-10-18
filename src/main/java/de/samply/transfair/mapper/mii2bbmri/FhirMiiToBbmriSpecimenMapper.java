package de.samply.transfair.mapper.mii2bbmri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

@Slf4j
public class FhirMiiToBbmriSpecimenMapper extends FhirMiiToBbmriMapper {

  private static final String BBMRI_CUSTODIAN = "https://fhir.bbmri.de/StructureDefinition/Custodian";
  private static final String BBMRI_SAMPLE_TYPE = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType";
  private static final String BBMRI_STORAGE_TEMPERATURE = "https://fhir.bbmri.de/StructureDefinition/StorageTemperature";
  private static final String BBMRI_SPECIMEN_PROFILE = "https://fhir.bbmri.de/StructureDefinition/Specimen";
  private static final String MII_TEMPERATURBEDINGUNGEN = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen";
  private static final String MII_CUSTODIAN = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/VerwaltendeOrganisation";
  private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
  private static final String ICD_O_3_SYSTEM = "http://terminology.hl7.org/CodeSystem/icd-o-3";
  private static final String DERIVATIVE_OTHER = "derivative-other"; 

  private static String storageTemperatureMiiToBbmri(Pair<Integer, Integer> limit)
  {
    if (limit.getLeft() >= 2 && limit.getRight() <= 10) {
      return "temperature2to10";
    }

    if (limit.getLeft() >= 11 && limit.getRight() <= 30) {
      return "temperatureRoom";
    }

    if (limit.getLeft() >= -35 && limit.getRight() <= -18) {
      return "temperature-18to-35";
    }

    if (limit.getLeft() >= -60 && limit.getRight() <= -85) {
      return "temperature-60to-85";
    }

    if (limit.getLeft() >= -195 && limit.getRight() <= -86) {
      return "temperatureGN";
    }

    if (limit.getLeft() >= -210 && limit.getRight() <= -196) {
      return "temperatureLN";
    }

    return "temperatureOther";
  }

  public FhirMiiToBbmriSpecimenMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> snomedSampleType) {
    super(icd10Snomed, snomedSampleType);
  }

  private Specimen convertSampleTypeBySystem(Specimen in, Specimen out, String inSystem, String outSystem){

    Coding inCoding = in.getType().getCoding()
        .stream()
        .filter(coding -> coding.getSystem()
            .equals(inSystem))
        .findFirst().get();


    if (inCoding != null){
      String newCode = this.snomedToSampleType.get(inCoding.getCode());

      if (newCode == null) {
        newCode = DERIVATIVE_OTHER;
      }
      Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
      out.getType().setCoding(List.of(outCoding));
      return out;
    }

    return null; //if the type can't be mapped, return null
  }

  public List<Resource> map(Resource resource){

    Specimen in = (Specimen) resource;

    if (in.getCollection().getCollectedDateTimeType() == null) { //if collectedDateTime doesn't exist don't transfer
      return Collections.emptyList();
    }

    Specimen out = new Specimen();
    out.setId(in.getId());
    out.setSubject(in.getSubject());

    out.setCollection(in.getCollection());

    List<Resource> resources = new ArrayList<>();

    this.convertSampleTypeBySystem(in, out, SNOMED_SYSTEM, BBMRI_SAMPLE_TYPE);

    if (out != null) { //continue mapping only if the type is mappable

      out.setMeta(new Meta().addProfile(BBMRI_SPECIMEN_PROFILE));

      if ((!in.getCollection().getBodySite().getCoding().isEmpty())) {
        Coding bodySite = new Coding();
        bodySite.setCode(in.getCollection().getBodySite().getCodingFirstRep().getCode());
        bodySite.setSystem(ICD_O_3_SYSTEM);
        out.getCollection().getBodySite().setCoding(List.of(bodySite));
      }


      for (Extension processingExtension : in.getProcessingFirstRep().getExtension()) {

        switch (processingExtension.getUrl()){
          case MII_TEMPERATURBEDINGUNGEN:
            Range temperatureRange = (Range) processingExtension.getValue();
            Pair<Integer, Integer> limits = Pair.of(temperatureRange.getLow().getValue().intValue(), temperatureRange.getHigh().getValue().intValue());
            String storageTemperature =  storageTemperatureMiiToBbmri(limits);

            if (!storageTemperature.isEmpty()) {
              Extension extensionTemperature = new Extension();
              extensionTemperature.setUrl(BBMRI_STORAGE_TEMPERATURE);
              CodeableConcept codeableConceptTemperature = new CodeableConcept();
              codeableConceptTemperature.getCodingFirstRep().setSystem(BBMRI_STORAGE_TEMPERATURE);
              codeableConceptTemperature.getCodingFirstRep().setCode(storageTemperature);
              extensionTemperature.setValue(codeableConceptTemperature);
              out.addExtension(extensionTemperature);
            }
            break;
          default:
            break;
        }
      }


      for (Extension extension : in.getExtension()) {

        switch (extension.getUrl()){
          case MII_CUSTODIAN: //Biobank collection
            Extension extensionCustodian = new Extension();
            extensionCustodian.setUrl(BBMRI_CUSTODIAN);
            extensionCustodian.setValue(extension.getValue());
            out.addExtension(extensionCustodian);
            break;
          default:
            break;

        }
      }


      resources.add(out);


    }

    return resources;


  }
}