package de.samply.transfair.reader.dicom;

import org.dcm4che3.data.Attributes;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * This class collects DICOM attributes from a web client.
 * It sends an HTTP GET request to a specified URL, retrieves DICOM data in JSON format, and
 * converts it to Attributes objects.
 */
@Slf4j
public class CollectDicomAttributesFromWebClient {
    private final String url;

    /**
     * Constructs a CollectDicomAttributesFromWebClient object with the specified URL.
     *
     * @param url the URL of a DicomWeb endpoint.
     */
    public CollectDicomAttributesFromWebClient(String url) {
        this.url = url.replaceAll("/+$", "");
    }

    /**
     * Collects DICOM attributes from a DicomWeb endpoint.
     *
     * @return a list of Attributes objects representing the collected DICOM attributes
     */
    public List<Attributes> collectAttributes() {
        // create an HttpClient object
        HttpClient client = HttpClient.newHttpClient();

        // create a URI object with the endpoint URL and query parameters
        URI uri = URI.create(url + "/instances");

        // create an HttpRequest object with the GET method and the Accept header set to application/dicom+json
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/dicom+json")
                .build();

        // send the request and get the response as a HttpResponse object
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn(e.toString());
        } catch (InterruptedException e) {
            log.warn(e.toString());
        }
        if (response == null)
            return null;

        // get the body of the response as a String
        String body = response.body();

        // parse the body as a JSON array
        JSONArray jsonArray = new JSONArray(body);

        List<Attributes> attributesList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            // get the JSON object at the current index
            JSONObject jsonObject = jsonArray.getJSONObject(i);

            Attributes attributes = DicomUtils.convertJsonToAttributes(jsonObject.toString());

            attributesList.add(attributes);
        }

        return attributesList;
    }
}
