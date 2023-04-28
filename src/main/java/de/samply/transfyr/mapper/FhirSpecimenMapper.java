package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirSpecimenMapper extends FhirMapper {


  private static final Map<String, Pair<Integer, Integer>> storageTemperatureBbmriToMii = Map.ofEntries(
      Map.entry("temperature2to10", Pair.of(2 , 10)), 
      Map.entry("temperature-18to-35", Pair.of(-35 , -18)), 
      Map.entry("temperature-60to-85", Pair.of(-85 , -60)), 
      Map.entry("temperatureGN", Pair.of(-195 , -160)), 
      Map.entry("temperatureLN", Pair.of(-209 , -196)), 
      Map.entry("temperatureRoom", Pair.of(11 , 30))
      );

  public FhirSpecimenMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }

  private Specimen convertSampleTypeBySystem(Specimen in, Specimen out, String inSystem, String outSystem){


    Coding inCoding = in.getType().getCoding()
        .stream()
        .filter(coding -> coding.getSystem()
            .equals(inSystem))
        .findFirst().get();


    if (inCoding != null){
      String newCode = this.sampleTypeSnomed.get(inCoding.getCode());

      if (newCode != null) {
        Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
        out.getType().setCoding(List.of(outCoding));

      }
    }

    return out;
  }

  public Resource map(Resource resource){

    Specimen in = (Specimen) resource;
    Specimen out = in.copy(); //TODO is it less messy to build it instead copy and cleanup?

    out.setMeta(
        new Meta()
        .addProfile(
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Specimen"));


    for (Extension e : in.getExtension()) {

      switch (e.getUrl()){
        case "https://fhir.bbmri.de/StructureDefinition/StorageTemperature":
          Pair<Integer, Integer> limits;
          String storageTemperature = ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode();

          if ((storageTemperature != null) && ((limits = storageTemperatureBbmriToMii.get(storageTemperature))!= null)) {
            Extension extension = new Extension();
            extension.setUrl("https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen");
            extension.setValue(new Range().setHigh(new Quantity(limits.getRight())).setLow(new Quantity(limits.getLeft())));
            out.getCollection().setExtension(List.of(extension)); // more extensions here?
          }
          break;
        case "https://fhir.bbmri.de/StructureDefinition/Custodian":
          String collectionRef = ((Reference) e.getValue()).getReference();
          break;
        case "https://fhir.bbmri.de/StructureDefinition/SampleDiagnosis":
          String diagnosisSystem = ((CodeableConcept) e.getValue()).getCodingFirstRep().getSystem();
          String diagnosis = ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode();
          //TODO create an entire new condition for the diagnosis
          break;
        default:

      }
    }





    this.convertSampleTypeBySystem(in, out, "https://fhir.bbmri.de/CodeSystem/SampleMaterialType", "http://snomed.info/sct");

    //TODO storage temperature

    return out;
  }
}
