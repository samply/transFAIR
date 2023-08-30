package de.samply.transfair.writer;

import de.samply.transfair.mapper.bbmri2beacon.BeaconFileSaver;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Value;

/**
 * This mapping transfers patient-related data from one blaze with bbmri to an
 * individuals.json file.
 */
@Slf4j
public class BeaconIndividualWriter implements ItemWriter<BeaconIndividuals> {
  private static final String COLLECTION_NAME = "individuals";
  @Value("${data.outputFileDirectory}")
  private String outFileDir;
  @Value("${data.writeBundlesToFile}")
  private boolean writeToFile;
  @Value("${beacon.mongoHost}")
  private String mongoHost;
  @Value("${beacon.mongoPort}")
  private String mongoPort;
  @Value("${beacon.mongoUser}")
  private String mongoUser;
  @Value("${beacon.mongoPass}")
  private String mongoPass;

  @Override
  public void write(Chunk<? extends BeaconIndividuals> chunk) throws Exception {
    BeaconIndividuals beaconIndividuals = chunk.getItems().get(0);

    if (writeToFile)
      BeaconFileSaver.export(beaconIndividuals.individuals, outFileDir, COLLECTION_NAME);
    else
      BeaconMongoSaver.export(beaconIndividuals.individuals, COLLECTION_NAME, mongoHost, mongoPort, mongoUser, mongoPass);
  }
}
