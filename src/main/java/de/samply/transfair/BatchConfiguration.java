package de.samply.transfair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;

import de.samply.transfair.mapper.bbmri2beacon.FhirBbmriToBeaconResourceMapper;
import de.samply.transfair.processor.BbmriBundleToBeaconIndividualProcessor;
import de.samply.transfair.processor.BbmriBundleToBeaconBiosampleProcessor;
import de.samply.transfair.reader.FhirConditionReader;
import de.samply.transfair.reader.FhirImagingStudyReader;
import de.samply.transfair.reader.FhirObservationReader;
import de.samply.transfair.reader.FhirOrganizationReader;
import de.samply.transfair.reader.FhirPatientReader;
import de.samply.transfair.reader.FhirSpecimenReader;
import de.samply.transfair.reader.FhirAmrReader;
import de.samply.transfair.writer.BeaconIndividualWriter;
import de.samply.transfair.writer.BeaconBiosampleWriter;
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
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import ca.uhn.fhir.context.FhirContext;
import de.samply.transfair.mapper.FhirResourceMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiConditionMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiObservationMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiOrganizationMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiPatientMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiResourceMapper;
import de.samply.transfair.mapper.bbmri2mii.FhirBbmriToMiiSpecimenMapper;
import de.samply.transfair.mapper.copy.FhirCopyResourceMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriConditionMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriObservationMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriOrganizationMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriPatientMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriResourceMapper;
import de.samply.transfair.mapper.mii2bbmri.FhirMiiToBbmriSpecimenMapper;
import de.samply.transfair.processor.FhirBundleProcessor;
import de.samply.transfair.writer.FhirBundleWriter;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import de.samply.transfair.models.beacon.BeaconBiosamples;
import lombok.extern.slf4j.Slf4j;

@Configuration("ehds2AppConfig")
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
  @Profile("bbmri2mii")
  public HashMap<String,String> sampleType2Snomed() throws FileNotFoundException {
    HashMap<String,String> sampleTypeSnomed = new HashMap<>();
    ConceptMap cp = (ConceptMap) ctx.newJsonParser().parseResource(new FileInputStream("BBMRI_sample_type_to_SNOMED_concept_map.json"));
    cp.getGroup().get(0).getElement().forEach(
        elem -> sampleTypeSnomed.put(elem.getCode(), elem.getTarget().get(0).getCode())
        );

    return sampleTypeSnomed;
  }
  
  @Bean
  @Profile("mii2bbmri")
  public HashMap<String,String> snomed2SampleType() throws FileNotFoundException {
    HashMap<String,String> sampleTypeSnomed = new HashMap<>();
    ConceptMap cp = (ConceptMap) ctx.newJsonParser().parseResource(new FileInputStream("SNOMED_to_BBMRI_sample_type_concept_map.json"));
    cp.getGroup().get(0).getElement().forEach(
        elem -> sampleTypeSnomed.put(elem.getCode(), elem.getTarget().get(0).getCode())
        );

    return sampleTypeSnomed;
  }

  @Bean
  @Profile("bbmri2mii")
  public FhirResourceMapper fhirBbmriToMiiMapper(HashMap<String,String> icd10Snomed, HashMap<String, String> sampleType2Snomed) {
    return new FhirBbmriToMiiResourceMapper(new FhirBbmriToMiiPatientMapper(icd10Snomed, sampleType2Snomed), new FhirBbmriToMiiConditionMapper(icd10Snomed, sampleType2Snomed),
        new FhirBbmriToMiiSpecimenMapper(icd10Snomed, sampleType2Snomed), new FhirBbmriToMiiObservationMapper(icd10Snomed, sampleType2Snomed), 
        new FhirBbmriToMiiOrganizationMapper(icd10Snomed, sampleType2Snomed));
  }
  
  @Bean
  @Profile("mii2bbmri")
  public FhirResourceMapper fhirMiiToBbmriMapper(HashMap<String,String> icd10Snomed, HashMap<String, String> snomed2SampleType) {
    return new FhirMiiToBbmriResourceMapper(new FhirMiiToBbmriPatientMapper(icd10Snomed, snomed2SampleType), new FhirMiiToBbmriConditionMapper(icd10Snomed, snomed2SampleType),
        new FhirMiiToBbmriSpecimenMapper(icd10Snomed, snomed2SampleType), new FhirMiiToBbmriObservationMapper(icd10Snomed, snomed2SampleType), 
        new FhirMiiToBbmriOrganizationMapper(icd10Snomed, snomed2SampleType));
  }

  @Bean
  @Profile("fhircopy")
  public FhirResourceMapper copyMapper() {
    return new FhirCopyResourceMapper();
  }

  @Bean
  @Profile("bbmri2beacon")
  public FhirResourceMapper bbmriToBeaconMapper() {
    // FHIR to Beacon conversion does not need a mapper, but without it, invisible Spring magic stops working.
    return new FhirBbmriToBeaconResourceMapper();
  }

  @Bean
  @Profile("dicom2fhir")
  public FhirResourceMapper dicomToFhirMapper() {
    return new FhirCopyResourceMapper();
  }

  @Bean
  @Profile("amr2fhir")
  public FhirResourceMapper amrToFhirMapper() {
    return new FhirCopyResourceMapper();
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
  public ItemReader<Bundle> patientReader() {
    return new FhirPatientReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }

  @Bean
  public ItemReader<Bundle> imagingStudyReader() {
    return new FhirImagingStudyReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }

  @Bean
  public ItemReader<Bundle> amrReader() {
    return new FhirAmrReader(ctx.newRestfulGenericClient(fhirProperties.getInput().getUrl()));
  }


  @Bean
  public FhirBundleProcessor processor(FhirResourceMapper mapper) {
    return new FhirBundleProcessor(mapper);
  }

  @Bean
  public BbmriBundleToBeaconIndividualProcessor bbmriBundleToBeaconIndividualProcessor(FhirResourceMapper mapper) {
    return new BbmriBundleToBeaconIndividualProcessor(mapper);
  }

  @Bean
  public BbmriBundleToBeaconBiosampleProcessor bbmriBundleToBeaconBiosampleProcessor(FhirResourceMapper mapper) {
    return new BbmriBundleToBeaconBiosampleProcessor(mapper);
  }


  @Bean
  @StepScope
  public ItemWriter<Bundle> writer() {
    return new FhirBundleWriter(ctx.newRestfulGenericClient(fhirProperties.getOutput().getUrl()));
  }

  @Bean
  @StepScope
  public ItemWriter<BeaconIndividuals> beaconIndividualWriter() {
    return new BeaconIndividualWriter();
  }

  @Bean
  @StepScope
  public ItemWriter<BeaconBiosamples> beaconBiosampleWriter() {
    return new BeaconBiosampleWriter();
  }

  @Bean
  @Profile("bbmri2mii")
  public Step stepBbmriToMiiOrganization(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepBbmriToMiiOrganization", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(organizationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("bbmri2mii")
  public Step stepBbmriToMiiCondition(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepBbmriToMiiCondition", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(conditionReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("bbmri2mii")
  public Step stepBbmriToMiiObservation(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepBbmriToMiiObservation", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(observationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("bbmri2mii")
  public Step stepBbmriToMiiSpecimen(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepBbmriToMiiSpecimen", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(specimenReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("mii2bbmri")
  public Step stepMiiToBbmriOrganization(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriOrganization", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(organizationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("mii2bbmri")
  public Step stepMiiToBbmriCondition(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriCondition", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(conditionReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("mii2bbmri")
  public Step stepMiiToBbmriObservation(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriObservation", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(observationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("mii2bbmri")
  public Step stepMiiToBbmriSpecimen(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriSpecimen", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(specimenReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("fhircopy")
  public Step copyOrganization(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriOrganization", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(organizationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("fhircopy")
  public Step copyCondition(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriCondition", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(conditionReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("fhircopy")
  public Step copyObservation(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriObservation", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(observationReader())
        .processor(processor)
        .writer(writer())
        .build();
  }


  @Bean
  @Profile("fhircopy")
  public Step copySpecimen(JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FhirBundleProcessor processor) {
    return new StepBuilder("stepMiiToBbmriSpecimen", jobRepository)
        .<Bundle, Bundle> chunk(10, transactionManager)
        .reader(specimenReader())
        .processor(processor)
        .writer(writer())
        .build();
  }

  @Bean
  @Profile("bbmri2beacon")
  public Step stepBbmri2beaconPatient(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      BbmriBundleToBeaconIndividualProcessor bbmriBundleToBeaconIndividualProcessor) {
    return new StepBuilder("stepBbmri2beaconPatient", jobRepository)
            .<Bundle, BeaconIndividuals> chunk(10, transactionManager)
            .reader(patientReader())
            .processor(bbmriBundleToBeaconIndividualProcessor)
            .writer(beaconIndividualWriter())
            .build();
  }

  @Bean
  @Profile("bbmri2beacon")
  public Step stepBbmri2beaconSample(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                     BbmriBundleToBeaconBiosampleProcessor bbmriBundleToBeaconBiosampleProcessor) {
    return new StepBuilder("stepBbmri2beaconSample", jobRepository)
            .<Bundle, BeaconBiosamples> chunk(10, transactionManager)
            .reader(specimenReader())
            .processor(bbmriBundleToBeaconBiosampleProcessor)
            .writer(beaconBiosampleWriter())
            .build();
  }

  @Bean
  @Profile("dicom2fhir")
  public Step stepDicomToFhirImagingStudy(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          FhirBundleProcessor processor) {
    return new StepBuilder("stepDicomToFhirImagingStudy", jobRepository)
            .<Bundle, Bundle> chunk(10, transactionManager)
            .reader(imagingStudyReader())
            .processor(processor)
            .writer(writer())
            .build();
  }

  @Bean
  @Profile("amr2fhir")
  public Step stepAmrToFhir(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          FhirBundleProcessor processor) {
    return new StepBuilder("stepAmrToFhir", jobRepository)
            .<Bundle, Bundle> chunk(10, transactionManager)
            .reader(amrReader())
            .processor(processor)
            .writer(writer())
            .build();
  }


  @Bean
  @Profile("bbmri2mii")
  public Job bbmriToMiiJob(JobRepository jobRepository, Step stepBbmriToMiiCondition, Step stepBbmriToMiiOrganization, Step stepBbmriToMiiObservation, Step stepBbmriToMiiSpecimen) {
    return new JobBuilder("bbmriToMiiJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(stepBbmriToMiiOrganization)
        //.next(stepCondition) //Conditions are mapped from Specimens (diagnoses) and Observations (causes of death)
        .next(stepBbmriToMiiObservation)
        .next(stepBbmriToMiiSpecimen)
        .build();
  }

  @Bean
  @Profile("mii2bbmri")
  public Job miiToBbmriJob(JobRepository jobRepository, Step stepMiiToBbmriCondition, Step stepMiiToBbmriOrganization, Step stepMiiToBbmriObservation, Step stepMiiToBbmriSpecimen) {
    return new JobBuilder("miiToBbmriJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(stepMiiToBbmriOrganization)
        .next(stepMiiToBbmriCondition)
        .next(stepMiiToBbmriObservation)
        .next(stepMiiToBbmriSpecimen)
        .build();
  }

  @Bean
  @Profile("fhircopy")
  public Job copy(JobRepository jobRepository, Step copyCondition, Step copyOrganization, Step copyObservation, Step copySpecimen) {
    return new JobBuilder("miiToBbmriJob", jobRepository)
        .incrementer(new RunIdIncrementer())
        .start(copyOrganization)
        .next(copyCondition)
        .next(copyObservation)
        .next(copySpecimen)
        .build();
  }

  @Bean
  @Profile("bbmri2beacon")
  public Job bbmriToBeaconJob(JobRepository jobRepository, Step stepBbmri2beaconPatient, Step stepBbmri2beaconSample) {
    return new JobBuilder("bbmriToBeaconJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(stepBbmri2beaconPatient)
            .next(stepBbmri2beaconSample)
            .build();
  }

  @Bean
  @Profile("dicom2fhir")
  public Job dicomToFhirJob(JobRepository jobRepository, Step stepDicomToFhirImagingStudy) {
    return new JobBuilder("dicomToFhirJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(stepDicomToFhirImagingStudy)
            .build();
  }

  @Bean
  @Profile("amr2fhir")
  public Job amrToFhirJob(JobRepository jobRepository, Step stepAmrToFhir) {
    return new JobBuilder("amrToFhirJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(stepAmrToFhir)
            .build();
  }

}
