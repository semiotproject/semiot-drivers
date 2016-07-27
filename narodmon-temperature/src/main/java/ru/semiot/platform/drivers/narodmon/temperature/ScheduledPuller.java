package ru.semiot.platform.drivers.narodmon.temperature;

import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class ScheduledPuller implements Runnable {

  private final static Logger logger = LoggerFactory.getLogger(
      ScheduledPuller.class);

  private final DeviceDriverImpl driver;
  private final Configuration config;

  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  private final String URL = "http://narodmon.ru/api";
  private final String API_CMD = "cmd";
  private final String API_LATITUDE = "lat";
  private final String API_LONGITUDE = "lng";
  private final String API_RADIUS = "radius";
  private final String API_UUID = "uuid";
  private final String API_KEY = "api_key";
  private final String API_TYPE = "types";
  public static String API_CMD_SENSOR_NEARBY = "sensorsNearby";

  public ScheduledPuller(DeviceDriverImpl driver, Configuration config) {
    this.driver = driver;
    this.config = config;
  }


  @Override
  public void run() {
    try {
      logger.debug("Try to pull...");
      CloseableHttpClient client = HttpClients.createDefault();
      HttpPost post = new HttpPost(URL);
      JSONObject json = new JSONObject();
      json.put(API_CMD, API_CMD_SENSOR_NEARBY)
          .put(API_LATITUDE, config.getAsString(Keys.LAT))
          .put(API_LONGITUDE, config.getAsString(Keys.LONG))
          .put(API_RADIUS, config.getAsString(Keys.RADIUS))
          .put(API_TYPE, "1")
          .put(API_UUID, config.getAsString(Keys.UUID))
          .put(API_KEY, config.getAsString(Keys.API_KEY));
      post.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));
      HttpResponse response = client.execute(post);
      String futureJSON = EntityUtils.toString(response.getEntity());
      client.close();
      //logger.debug("JSON is {}",futureJSON);
      json = new JSONObject(futureJSON);
      Map <NarodmonDevice, NarodmonObservation> devices = getData(json);
      logger.debug("Found {} devices", devices.size());
      for(NarodmonDevice device : devices.keySet()){
        driver.registerDevice(device);
        driver.publishNewObservation(devices.get(device));
      }
    } catch (Throwable ex) {
      logger.error("Some reason: ", ex);
    }
  }

  private Map<NarodmonDevice, NarodmonObservation> getData(JSONObject data) throws JSONException {
    Map<NarodmonDevice, NarodmonObservation> map = new HashMap<>();
    if (data.has("devices")) {
      JSONArray devices = new JSONArray((data.get("devices")).toString());
      for (int i = 0; i < devices.length(); i++) {
        JSONObject device = (JSONObject) devices.get(i);
        JSONArray sensors = new JSONArray(device.get("sensors").toString());
        for (int j = 0; j < sensors.length(); j++) {
          JSONObject sensor = sensors.getJSONObject(j);
          String sensor_id = getHash(String.valueOf(device.get("id")));
          String value = String.valueOf(sensor.get("value"));
          String lat = String.valueOf(device.get("lat"));
          String lon = String.valueOf(device.get("lng"));
          NarodmonDevice dev = new NarodmonDevice(sensor_id, lat, lon);
          NarodmonObservation narodmonObservation = new NarodmonObservation(dev.getId(),
              NarodmonObservation.TEMPERATURE_TEMPLATE_SENSOR.replace("${SYSTEM_ID}", dev.getId()),
              Long.toString(System.currentTimeMillis()), value, NarodmonObservation.TEMPERATURE_TYPE);
          map.put(dev, narodmonObservation);
        }
      }
    }
    return map;
  }

  private String getHash(String id) {
    String name = Keys.DRIVER_PID + id;
    int h = FNV_32_INIT;
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      h ^= name.charAt(i);
      h *= FNV_32_PRIME;
    }
    long longHash = h & 0xffffffffl;
    return String.valueOf(longHash);
  }
}
