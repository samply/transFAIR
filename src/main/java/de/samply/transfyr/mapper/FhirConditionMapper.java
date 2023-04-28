package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirConditionMapper extends FhirMapper {


  public FhirConditionMapper(HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
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

  public Resource map(Resource resource){

    Condition in = (Condition) resource;
    Condition out = in.copy();
    
    out.setMeta(
        new Meta()
            .addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"));


    //this.convertCodingBySystem(in, out, "http://hl7.org/fhir/sid/icd-10", "http://snomed.info/sct");


    //TODO - Return null if resource could not be mapped

    return out;
  }
}
