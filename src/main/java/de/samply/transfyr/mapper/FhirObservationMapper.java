package de.samply.transfyr.mapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirObservationMapper extends FhirMapper {

  public static final String BBMRI_PROFILE_CAUSE_OF_DEATH = "https://fhir.bbmri.de/StructureDefinition/CauseOfDeath";
  public static final String MII_PROFILE_CAUSE_OF_DEATH =
      "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Todesursache";
  public static final String ICD_SYSTEM = "http://hl7.org/fhir/sid/icd-10";
  public static final String LOINC_SYSTEM = "http://loinc.org";
  public static final String SNOMED_SYSTEM = "http://snomed.info/sct";

  public FhirObservationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }


  public List<Resource> map(Resource resource){

    Observation in = (Observation) resource;


    if (in.getMeta().getProfile().stream().anyMatch(canonicalType -> canonicalType.equals(BBMRI_PROFILE_CAUSE_OF_DEATH))) {
      Condition out = new Condition();
      out.setMeta(new Meta().addProfile(MII_PROFILE_CAUSE_OF_DEATH));
      
      out.setId(in.getId());
      
      out.setSubject(new Reference(in.getSubject().getReference()));

      CodeableConcept codingLoinc = new CodeableConcept();
      codingLoinc.getCodingFirstRep().setSystem(LOINC_SYSTEM);
      codingLoinc.getCodingFirstRep().setCode("79378-6");
      
      CodeableConcept codingSnomedCt = new CodeableConcept();
      codingSnomedCt.getCodingFirstRep().setSystem(SNOMED_SYSTEM);
      codingSnomedCt.getCodingFirstRep().setCode("16100001");

      out.setCategory(List.of(codingLoinc, codingSnomedCt));
      
      CodeableConcept codeableConceptCause = new CodeableConcept();
      codeableConceptCause.getCodingFirstRep().setSystem(in.getValueCodeableConcept().getCodingFirstRep().getSystem()); //FIXME convert to GM, just 1 on 1 for the beginning
      codeableConceptCause.getCodingFirstRep().setCode(in.getValueCodeableConcept().getCodingFirstRep().getCode());//FIXME mapping to GM function
      out.setCode(codeableConceptCause);

    return List.of(out);
  } else {
    //MII doesn't need other observations
    return Collections.emptyList();
  }
}
}
