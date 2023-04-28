package de.samply.transfyr;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ConceptMap;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import ca.uhn.fhir.context.FhirContext;
import de.samply.transfyr.mapper.FhirConditionMapper;
import de.samply.transfyr.mapper.FhirObservationMapper;
import de.samply.transfyr.mapper.FhirOrganizationMapper;
import de.samply.transfyr.mapper.FhirPatientMapper;
import de.samply.transfyr.mapper.FhirResourceMapper;
import de.samply.transfyr.mapper.FhirSpecimenMapper;
import de.samply.transfyr.processor.FhirBundleProcessor;
import de.samply.transfyr.reader.FhirConditionReader;
import de.samply.transfyr.reader.FhirObservationReader;
import de.samply.transfyr.reader.FhirOrganizationReader;
import de.samply.transfyr.reader.FhirSpecimenReader;
import de.samply.transfyr.writer.FhirBundleWriter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class BatchConfiguration {

  @Autowired
  private FhirProperties fhirProperties;

  private FhirContext ctx = FhirContext.forR4();

  @Bean
  public HashMap<String,String> icd10Snomed() throws FileNotFoundException {
    HashMap<String,String> icd10Snomed = new HashMap<>();
    ConceptMap cp = (ConceptMap) ctx.newJsonParser().parseResource(new FileInputStream("example-concept.json"));
    cp.getGroup().get(0).getElement().forEach(
        elem -> icd10Snomed.put(elem.getCode(), elem.getTarget().get(0).getCode())
        );

    return icd10Snomed;
  }
  
  @Bean
  public HashMap<String,String> sampleType2Snomed() throws FileNotFoundException {
    HashMap<String,String> sampleTypeSnomed = new HashMap<>();
    ConceptMap cp = (ConceptMap) ctx.newJsonParser().parseResource(new FileInputStream("BBMRI_sample_type_to_snomed_concept_map.json"));
    cp.getGroup().get(0).getElement().forEach(
        elem -> sampleTypeSnomed.put(elem.getCode(), elem.getTarget().get(0).getCode())
        );

    return sampleTypeSnomed;
  }

  @Bean
  public FhirResourceMapper fhirMapper(HashMap<String,String> icd10Snomed, HashMap<String, String> sampleType2Snomed) {
    return new FhirResourceMapper(new FhirPatientMapper(icd10Snomed, sampleType2Snomed), new FhirConditionMapper(icd10Snomed, sampleType2Snomed),
        new FhirSpecimenMapper(icd10Snomed, sampleType2Snomed), new FhirObservationMapper(icd10Snomed, sampleType2Snomed), 
        new FhirOrganizationMapper(icd10Snomed, sampleType2Snomed));
  }
  
  @Bean
  public ItemReader<Bundle> organizationReader() {
    return new FhirOrganizationReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }

  @Bean
  public ItemReader<Bundle> conditionReader() {
    return new FhirConditionReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }
  
  @Bean
  public ItemReader<Bundle> observationReader() {
    return new FhirObservationReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }
  
  @Bean
  public ItemReader<Bundle> specimenReader() {
    return new FhirSpecimenReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }
  


  @Bean
  public FhirBundleProcessor processor(FhirResourceMapper fhirMapper) {
    return new FhirBundleProcessor(fhirMapper);
  }

  @Bean
  @StepScope
  public ItemWriter<Bundle> writer() {
    return new FhirBundleWriter(ctx.newRestfulGenericClient(fhirProperties.getOutput().getUrl()));
  }
  
  @Bean
  public Step stepOrganization(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepOrganization", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(organizationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }
  
  @Bean
  public Step stepCondition(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepCondition", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(conditionReader())
        .processor(processor)
        .writer(writer())
        .build();
  }
  
  
  @Bean
  public Step stepObservation(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepObservation", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(observationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }
  
  
  @Bean
  public Step stepSpecimen(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepSpecimen", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(specimenReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  public Job bbmriToMiiJob(JobRepository jobRepository, Step stepCondition, Step stepOrganization, Step stepObservation, Step stepSpecimen) {
    return new JobBuilder("bbmriToMiiJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(stepOrganization)
        //.next(stepCondition)
        //TODO double check if Condition is needed
        .next(stepObservation)
        .next(stepSpecimen)
        .build();
  }

}