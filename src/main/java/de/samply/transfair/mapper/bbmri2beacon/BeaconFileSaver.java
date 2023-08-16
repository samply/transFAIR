package de.samply.transfair.mapper.bbmri2beacon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Export objects as JSON files.
 */
@Slf4j
public class BeaconFileSaver {
  /**
   * Serialize the given data into JSON and dump into the given filename.
   *
   * If a file of the given name already exists, combine the existing data in
   * the file with the new data and write back to the file.
   *
   * @param data The object to be serialized.
   * @param pathname Path to the directory where the file should be stored.
   * @param filename Name of the file where the data will be dumped.
   */
    public static void export(Object data, String pathname, String filename) {
      Path path = Path.of(pathname);
      Path filepath = path.resolve(filename);
      File file = filepath.toFile();
      log.info("export: filepath=" + filepath);

      try {
        ObjectMapper objectMapper = new ObjectMapper();

        // Check if the file exists
        if (file.exists()) {
          // Read the existing contents of the file
          StringBuilder fileContents = new StringBuilder();
          try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
              fileContents.append(line);
            }
          }

          // Parse the existing JSON from the read contents
          Object existingData = objectMapper.readValue(fileContents.toString(), Object.class);

          // Combine the existing JSON with the new data
          Object combinedData = mergeJSON(existingData, data);

          // Write the combined JSON to the file
          FileWriter myWriter = new FileWriter(file);
          String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(combinedData);
          myWriter.write(output);
          myWriter.close();
        } else {
          // Write the new data as JSON to the file
          FileWriter myWriter = new FileWriter(file);
          String output = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
          myWriter.write(output);
          myWriter.close();
        }
      } catch (IOException e) {
        log.error("An error occurred while writing output to file " + filepath);
        e.printStackTrace();
      }
    }

    /**
     * Merge two JSON arrays into a single array.
     *
     * @param existingData The existing JSON array to merge.
     * @param newData      The new JSON array to merge.
     * @return The merged JSON array. If the input data types are not arrays or if one of them is not an array,
     *         returns {@code null}.
     */
    private static Object mergeJSON(Object existingData, Object newData) {
      if (existingData instanceof List && newData instanceof List) {
        List<Object> mergedArray = new ArrayList<>();

        // Add all elements from existingData to the mergedArray
        mergedArray.addAll((List<Object>) existingData);

        // Add all elements from newData to the mergedArray
        mergedArray.addAll((List<Object>) newData);

        return mergedArray;
      }

      // Default case: Return null if the data types are not arrays or if one of them is not an array
      return null;
    }
}
