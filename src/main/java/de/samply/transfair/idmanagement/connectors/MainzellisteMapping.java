package de.samply.transfair.idmanagement.connectors;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


/**
 * Mapping for Mainzelliste. For documentation of REST API see
 * https://www.unimedizin-mainz.de/imbei/medical-informatics/ag-verbundforschung/mainzelliste.html
 * For successfull fetching of ID-Mappings it is required that the Mainzelliste instance allows
 * connections from the host machine of TransFAIR.
 */
@Slf4j
public class MainzellisteMapping extends IdMapping {

  private final String baseUrl;
  private final String apiKey;
  private final String apiVersion;

  /**
   * Constructor of the MainzellisteMapping. Reads ID-Mappings from Mainzelliste
   *
   * @param baseUrl Base URL of the Mainzelliste instance. E.g. "http://mainzelliste.ukaachen.de/"
   *     incluing the final /
   * @param apiKey Mainzelliste API Key for authorisation
   */
  public MainzellisteMapping(@NotNull String baseUrl, @NotNull String apiKey) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.apiVersion = "3.2";
  }

  /**
   * Fetches ID-Mappings from a mainzelliste instance by using the connection and authorisation
   * details provided in the constructor.
   *
   * @param id Identifier to be mapped to a different domain
   * @param srcDomain Domain that id belongs to
   * @param tarDomain The domain that the ID shall be mapped to
   * @return ID from tarDomain that correspond to the id provided from the domain srcDomain
   * @throws Exception Different kinds of exceptions if an error occurred during the fetching. E.g.
   *     authorisation failed, Mainzelliste host not found, mapping does not exist
   */
  @Override
  public String fetchMapping(
      @NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) throws Exception {

    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest request;
    HttpResponse<String> response;
    int statusCode;

    // 1. Create session
    request =
        HttpRequest.newBuilder()
            .uri(new URI(baseUrl + "/sessions"))
            .header("mainzellisteApiKey", apiKey)
            .header("mainzellisteApiVersion", apiVersion)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    statusCode = response.statusCode();
    if (statusCode != 201) {
      throw new Exception(
          "Failed to create Mainzelliste session with status code "
              + statusCode
              + ":\n"
              + response
              + "\n"
              + response.body());
    }

    String sessionUrl = response.headers().map().get("Location").get(0);

    String token =
        "{\n"
            + "\"type\": \"readPatients\",\n"
            + "\"data\": {\n"
            + "\"searchIds\": [\n"
            + "{\n"
            + "\"idType\":\""
            + srcDomain
            + "\",\n"
            + "\"idString\":\""
            + id
            + "\"\n"
            + "}\n"
            + "],\n"
            + "\"resultIds\":[\""
            + tarDomain
            + "\"]\n"
            + "}\n"
            + "}";

    // 2. Add token
    request =
        HttpRequest.newBuilder()
            .uri(new URI(sessionUrl + "tokens"))
            .header("mainzellisteApiKey", apiKey)
            .header("mainzellisteApiVersion", apiVersion)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(token))
            .build();
    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    statusCode = response.statusCode();
    if (statusCode != 201) {
      throw new Exception(
          "Failed to provide ReadPatients token to Mainzelliste with status code "
              + statusCode
              + ":\n"
              + response
              + "\n"
              + response.body());
    }
    JSONParser jsonParser = new JSONParser();
    String tokenId = (String) ((JSONObject) jsonParser.parse(response.body())).get("id");

    // 3. Request ID
    request =
        HttpRequest.newBuilder()
            .uri(new URI(baseUrl + "/patients/tokenId/" + tokenId))
            .header("mainzellisteApiKey", apiKey)
            .header("mainzellisteApiVersion", apiVersion)
            .header("Content-Type", "application/json")
            .GET()
            .build();
    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    statusCode = response.statusCode();
    if (statusCode != 200) {
      throw new Exception(
          "Failed to request ID from Mainzelliste after token "
          + "was successfully provided with status code "
              + statusCode
              + ":\n"
              + response
              + "\n"
              + response.body());
    }

    JSONArray completeResponse = (JSONArray) jsonParser.parse(response.body());
    JSONObject lonelyPatient = (JSONObject) completeResponse.get(0);
    JSONArray ids = (JSONArray) lonelyPatient.get("ids");
    JSONObject lonelyId = (JSONObject) ids.get(0);
    String tarId = (String) lonelyId.get("idString");
    
    // 4. Delete the session
    request =
        HttpRequest.newBuilder()
            .uri(new URI(sessionUrl))
            .header("mainzellisteApiVersion", apiVersion)
            .DELETE()
            .build();
    response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    statusCode = response.statusCode();
    if (statusCode != 204) {
      log.info(
          "Deletion of Mainzelliste session "
              + sessionUrl
              + " failed with status code "
              + statusCode
              + ":\n"
              + response
              + "\n"
              + response.body());
    }

    return tarId;
  }
}
