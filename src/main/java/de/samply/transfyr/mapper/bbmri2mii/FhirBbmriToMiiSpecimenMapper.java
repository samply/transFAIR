package de.samply.transfyr.mapper.bbmri2mii;

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
public class FhirBbmriToMiiSpecimenMapper extends FhirBbmriToMiiMapper {

  private static final String BBMRI_DIAGNOSIS = "https://fhir.bbmri.de/StructureDefinition/SampleDiagnosis";
  private static final String BBMRI_CUSTODIAN = "https://fhir.bbmri.de/StructureDefinition/Custodian";
  private static final String BBMRI_SAMPLE_TYPE = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType";
  private static final String BBMRI_STORAGE_TEMPERATURE = "https://fhir.bbmri.de/StructureDefinition/StorageTemperature";
  private static final String MII_SPECIMEN_PROFILE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Specimen";
  private static final String MII_TEMPERATURBEDINGUNGEN = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen";
  private static final String MII_DIAGNOSE = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Diagnose";
  private static final String MII_CUSTODIAN = "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/VerwaltendeOrganisation";
  private static final String MII_PROFILE_DIAGNOSE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
  private static final String ICD_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
  private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
  private static final String ICD_10_GM_CODE_SYSTEM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm";
  private static final String ICD_O_3_SYSTEM = "http://terminology.hl7.org/CodeSystem/icd-o-3";


  private static final Map<String, Pair<Integer, Integer>> storageTemperatureBbmriToMii = Map.ofEntries(
      Map.entry("temperature2to10", Pair.of(2 , 10)), 
      Map.entry("temperature-18to-35", Pair.of(-35 , -18)), 
      Map.entry("temperature-60to-85", Pair.of(-85 , -60)), 
      Map.entry("temperatureGN", Pair.of(-195 , -160)), 
      Map.entry("temperatureLN", Pair.of(-209 , -196)), 
      Map.entry("temperatureRoom", Pair.of(11 , 30))
      );

  public FhirBbmriToMiiSpecimenMapper(
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
      String newCode = this.sampleType2Snomed.get(inCoding.getCode());

      if (newCode != null) {
        Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
        out.getType().setCoding(List.of(outCoding));
        return out;

      }
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
    //TODO if there's no sampling date, don't transfer

    this.convertSampleTypeBySystem(in, out, BBMRI_SAMPLE_TYPE, SNOMED_SYSTEM);

    if (out != null) { //continue mapping only if the type is mappable

      out.setMeta(new Meta().addProfile(MII_SPECIMEN_PROFILE));

      Coding bodySite = new Coding();

      if ((in.getCollection() != null) && (in.getCollection().getBodySite() != null) ) {
        bodySite.setCode(in.getCollection().getBodySite().getCodingFirstRep().getCode());
        bodySite.setSystem(ICD_O_3_SYSTEM);
        out.getCollection().getBodySite().setCoding(List.of(bodySite));
      }



      for (Extension extension : in.getExtension()) {

        switch (extension.getUrl()){
          case BBMRI_STORAGE_TEMPERATURE:
            Pair<Integer, Integer> limits;
            String storageTemperature = ((CodeableConcept) extension.getValue()).getCodingFirstRep().getCode();

            if ((storageTemperature != null) && ((limits = storageTemperatureBbmriToMii.get(storageTemperature))!= null)) {
              Extension extensionTemperature = new Extension();
              extensionTemperature.setUrl(MII_TEMPERATURBEDINGUNGEN);
              extensionTemperature.setValue(new Range().setHigh(new Quantity(limits.getRight())).setLow(new Quantity(limits.getLeft())));
              out.getProcessingFirstRep().addExtension(extensionTemperature);
            }
            break;
          case BBMRI_CUSTODIAN: //Biobank collection
            Extension extensionCustodian = new Extension();
            extensionCustodian.setUrl(MII_CUSTODIAN);
            extensionCustodian.setValue(extension.getValue());
            out.addExtension(extensionCustodian); 
            break;
          case BBMRI_DIAGNOSIS:
            //create a Condition
            String diagnosisSystem = ((CodeableConcept) extension.getValue()).getCodingFirstRep().getSystem();
            String diagnosis = ((CodeableConcept) extension.getValue()).getCodingFirstRep().getCode();
            Condition condition = new Condition();
            condition.setMeta(new Meta().addProfile(MII_PROFILE_DIAGNOSE));
            String conditionId = String.valueOf(UUID.randomUUID());
            condition.setId(conditionId);
            condition.setRecordedDate(in.getCollection().getCollectedDateTimeType().getValue());
            condition.setSubject(new Reference(in.getSubject().getReference()));
            //subject of Condition is subject of Specimen
            Extension extensionDiagnose = new Extension();
            extensionDiagnose.setUrl(MII_DIAGNOSE);
            extensionDiagnose.setValue(new Reference().setReference("Condition/" + conditionId));
            CodeableConcept codeableConceptDiagnosis = new CodeableConcept();
            codeableConceptDiagnosis.getCodingFirstRep().setSystem(ICD_10_GM_CODE_SYSTEM);
            codeableConceptDiagnosis.getCodingFirstRep().setCode(diagnosis);//FIXME mapping to GM function
            condition.setCode(codeableConceptDiagnosis);
            out.addExtension(extensionDiagnose);
            resources.add(condition);
            break;
          default:

        }
      }

      resources.add(out);

    }

    return resources;
  }
}
