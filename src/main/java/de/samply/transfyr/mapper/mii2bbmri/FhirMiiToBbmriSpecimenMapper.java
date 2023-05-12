package de.samply.transfyr.mapper.mii2bbmri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import lombok.extern.slf4j.Slf4j;

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

  private static final Map<Pair<Integer, Integer>, String> storageTemperatureMiiToBbmri = Map.ofEntries(
      Map.entry(Pair.of(2 , 10), "temperature2to10"), 
      Map.entry(Pair.of(-35 , -18), "temperature-18to-35"), 
      Map.entry(Pair.of(-85 , -60), "temperature-60to-85"), 
      Map.entry(Pair.of(-195 , -160), "temperatureGN"), 
      Map.entry(Pair.of(-209 , -196), "temperatureLN"), 
      Map.entry(Pair.of(11 , 30), "temperatureRoom")
      );

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

    Specimen out = in.copy(); 
    out.setExtension(null);
    out.getCollection().setExtension(null);

    List<Resource> resources = new ArrayList<>();

    this.convertSampleTypeBySystem(in, out, SNOMED_SYSTEM, BBMRI_SAMPLE_TYPE);

    if (out != null) { //continue mapping only if the type is mappable

      out.setMeta(new Meta().addProfile(BBMRI_SPECIMEN_PROFILE));

      Coding bodySite = new Coding();

      if ((in.getCollection() != null) && (in.getCollection().getBodySite() != null) ) {
        bodySite.setCode(in.getCollection().getBodySite().getCodingFirstRep().getCode());
        bodySite.setSystem(ICD_O_3_SYSTEM);
        out.getCollection().getBodySite().setCoding(List.of(bodySite));
      }


      for (Extension processingExtension : in.getProcessingFirstRep().getExtension()) {

        switch (processingExtension.getUrl()){
          case MII_TEMPERATURBEDINGUNGEN:
            Range temperatureRange = (Range) processingExtension.getValue();
            Pair<Integer, Integer> limits = Pair.of(temperatureRange.getLow().getValue().intValue(), temperatureRange.getHigh().getValue().intValue());
            String storageTemperature =  storageTemperatureMiiToBbmri.get(limits);

            if (storageTemperature != null) {
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