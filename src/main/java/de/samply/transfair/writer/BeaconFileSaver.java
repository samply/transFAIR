package de.samply.transfair.writer;

import de.samply.transfair.util.JsonSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/** Export objects as JSON files. */
@Slf4j
public class BeaconFileSaver {

  private BeaconFileSaver() {
  }

  /**
   * Serialize the given data into JSON and dump into the given collectionName.
   *
   * @param data The object to be serialized.
   *
   * @param pathname Path to the directory where the file should be stored.
   *
   * @param collectionName Name of the file where the data will be dumped.
   */
  public static void export(Object data, String collectionName, String pathname) {
    Path path = Path.of(pathname);
    Path filepath = path.resolve(collectionName + ".json");
    log.info("export: filepath=" + filepath);
    try (FileWriter myWriter = new FileWriter(filepath.toFile())) {
      String output = JsonSerializer.toJsonString(data);
      myWriter.write(output);
    } catch (IOException e) {
      log.error("An error occurred while writing output to file " + filepath);
      e.printStackTrace();
    } 
  }
}
