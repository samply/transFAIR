package de.samply.transfair.writer;

import de.samply.transfair.mapper.bbmri2beacon.BeaconFileSaver;
import de.samply.transfair.models.beacon.BeaconBiosamples;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;

/**
 * This mapping transfers patient-related data from one blaze with bbmri to an
 * biosamples.json file.
 */
@Slf4j
public class BeaconBiosampleWriter implements ItemWriter<BeaconBiosamples> {
  private static final String COLLECTION_NAME = "biosamples";
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
  public void write(Chunk<? extends BeaconBiosamples> chunk) throws Exception {
    BeaconBiosamples beaconBiosamples = chunk.getItems().get(0);

    if (writeToFile)
      BeaconFileSaver.export(beaconBiosamples.biosamples, outFileDir, COLLECTION_NAME);
    else
      BeaconMongoSaver.export(beaconBiosamples.biosamples, COLLECTION_NAME, mongoHost, mongoPort, mongoUser, mongoPass);
  }
}
