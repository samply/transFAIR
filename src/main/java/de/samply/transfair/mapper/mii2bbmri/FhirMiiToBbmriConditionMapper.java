package de.samply.transfair.mapper.mii2bbmri;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirMiiToBbmriConditionMapper extends FhirMiiToBbmriMapper {

  private static final String BBMRI_PROFILE_CAUSE_OF_DEATH = "https://fhir.bbmri.de/StructureDefinition/CauseOfDeath";
  private static final String MII_PROFILE_CAUSE_OF_DEATH =
      "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Todesursache";
  private static final String ICD_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String MII_PROFILE_DIAGNOSE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
  private static final String BBMRI_PROFILE_DIAGNOSE = "https://fhir.bbmri.de/StructureDefinition/Condition";


  public FhirMiiToBbmriConditionMapper(HashMap<String, String> icd10Snomed, HashMap<String, String> snomedSampleType) {
    super(icd10Snomed, snomedSampleType);
  }

  private Condition convertCodingBySystem(Condition in, Condition out, String inSystem, String outSystem){


    Coding inCoding = in.getCode().getCoding()
        .stream()
        .filter(coding -> coding.getSystem()
            .equals(inSystem))
        .findFirst().get();


    if (inCoding != null){
      String newCode = this.icd10Snomed.get(inCoding.getCode());

      if (newCode != null) {
        Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
        out.getCode().setCoding(List.of(outCoding));

      }
    }

    return out;
  }

  public List<Resource> map(Resource resource){

    Condition in = (Condition) resource;

    if (in.getMeta().getProfile().stream().anyMatch(canonicalType -> canonicalType.equals(MII_PROFILE_CAUSE_OF_DEATH))) {
      Observation out = new Observation();
      out.setMeta(new Meta().addProfile(BBMRI_PROFILE_CAUSE_OF_DEATH));
      out.setStatus(Observation.ObservationStatus.FINAL);

      out.setId(in.getId());

      out.setEffective(in.getRecordedDateElement());

      out.setSubject(new Reference(in.getSubject().getReference()));

      CodeableConcept codingLoinc = new CodeableConcept();
      codingLoinc.getCodingFirstRep().setSystem(LOINC_SYSTEM);
      codingLoinc.getCodingFirstRep().setCode("68343-3");

      out.setCode(codingLoinc);

      CodeableConcept codeableConceptCause = new CodeableConcept();
      codeableConceptCause.getCodingFirstRep().setSystem(ICD_SYSTEM); 
      codeableConceptCause.getCodingFirstRep().setCode(in.getCode().getCodingFirstRep().getCode()); //FIXME map ICD10GM to ICD10WHO
      out.setValue(codeableConceptCause);

      return List.of(out);
    } else if (in.getMeta().getProfile().stream().anyMatch(canonicalType -> canonicalType.equals(MII_PROFILE_DIAGNOSE))) {


      Condition out = in.copy();

      out.setMeta(new Meta().addProfile(BBMRI_PROFILE_DIAGNOSE));

      out.getCode().getCodingFirstRep().setSystem(ICD_SYSTEM);
      out.getCode().getCodingFirstRep().setCode(in.getCode().getCodingFirstRep().getCode()); //FIXME map ICD10GM to ICD10WHO

      return List.of(out);
    } else {
      return Collections.emptyList();
    } 
  }


}
