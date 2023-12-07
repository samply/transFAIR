package de.samply.transfair.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Serialize objects to JSON. */
@Slf4j
public class JsonSerializer {
  /** Serialize the given data into JSON and return as a String.
   *
   * @param data The object to be serialized.
   *
   * @return Serialized object.
   */
  
  private JsonSerializer() {
  }
  
  /** Returns the Beacon Object as a JSON string. */
  public static String toJsonString(Object data) {
    if (Objects.isNull(data)) {
      log.warn("Data is null");
      return null;
    }
    String jsonData = null;
    try {
      jsonData = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data);
    } catch (JsonProcessingException e) {
      log.error("An error occurred while converting a Beacon object into JSON");
      e.printStackTrace();
    }
    return jsonData;
  }

  /** Serialize the given data into JSON and return as a list of
   * JSON strings. Only works if the supplied object is of type
   * List.
   *
   * @param data The object to be serialized.
   *
   * @return Serialized object.
   */
  public static List<String> toJsonList(Object data) {
    if (Objects.isNull(data)) {
      log.warn("Data is null");
      return Collections.emptyList();
    }
    if (!List.class.isAssignableFrom(data.getClass())) {
      log.warn("This method can only be applied to List objects, not to " + data.getClass());
      return Collections.emptyList();
    }
    List<String> jsonList = new ArrayList<>();
    for (Object object : (List<Object>) data) {
      String jsonString = toJsonString(object);
      jsonList.add(jsonString);
    }
    return jsonList;
  }
}
