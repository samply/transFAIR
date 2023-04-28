package de.samply.transfyr.mapper;

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


  public FhirObservationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }


  public Resource map(Resource resource){

    Observation in = (Observation) resource;


    if (in.getMeta().getProfile().stream().anyMatch(canonicalType -> canonicalType.equals(BBMRI_PROFILE_CAUSE_OF_DEATH))) {
      Condition out = new Condition();
      out.setMeta(new Meta().addProfile(MII_PROFILE_CAUSE_OF_DEATH));
      
      out.setId(in.getId());
      
      out.setSubject(new Reference(in.getSubject().getReference()));

      CodeableConcept codingLoinc = new CodeableConcept();
      codingLoinc.getCodingFirstRep().setSystem("http://loinc.org");
      codingLoinc.getCodingFirstRep().setCode("79378-6");
      
      CodeableConcept codingSnomedCt = new CodeableConcept();
      codingSnomedCt.getCodingFirstRep().setSystem("http://snomed.info/sct");
      codingSnomedCt.getCodingFirstRep().setCode("16100001");

      out.setCategory(List.of(codingLoinc, codingSnomedCt));
      
      CodeableConcept codeableConceptCause = new CodeableConcept();
      codeableConceptCause.getCodingFirstRep().setSystem(in.getValueCodeableConcept().getCodingFirstRep().getSystem());
      codeableConceptCause.getCodingFirstRep().setCode(in.getValueCodeableConcept().getCodingFirstRep().getCode());
      out.setCode(codeableConceptCause);

    return out;
  } else {
    Observation out = in.copy();
    return out;
  }
}
}
