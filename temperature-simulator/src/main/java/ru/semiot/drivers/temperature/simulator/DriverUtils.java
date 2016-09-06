package ru.semiot.drivers.temperature.simulator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DriverUtils {

  private static final Logger logger = LoggerFactory.getLogger(DriverUtils.class);

  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  private static final String SENSOR_ID = "sensor_id";
  private static final String VALUE = "value";
  private static final String TIMESTAMP = "timestamp";
  private static final String FLATS = "building_flats";
  private static final String SENSORS = "flat_sensors";
  private static final String BUILDING_NUMBER = "building_number";

  private static String hash(String prefix, String id) {
    String name = prefix + id;
    int h = FNV_32_INIT;
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      h ^= name.charAt(i);
      h *= FNV_32_PRIME;
    }
    long longHash = h & 0xffffffffl;
    return String.valueOf(longHash);
  }

  public static List<TemperatureDevice> getDevices(JSONObject building) {
    List<TemperatureDevice> devices = new ArrayList<>();
    try {
      String building_num = Integer.toString(building.getInt(BUILDING_NUMBER));
      JSONArray flats = building.getJSONArray(FLATS);
      for (int i = 0; i < flats.length(); i++) {
        JSONObject flat = flats.getJSONObject(i);
        JSONArray sensors = flat.getJSONArray(SENSORS);
        for (int j = 0; j < sensors.length(); j++) {
          //String sensor_id = hash(Keys.DRIVER_PID, Integer.toString(sensors.getJSONObject(j).getInt(SENSOR_ID)));
          String sensor_id = Integer.toString(sensors.getJSONObject(j).getInt(SENSOR_ID));
          devices.add(new TemperatureDevice(sensor_id, building_num));
        }
      }
    } catch (JSONException ex) {
      logger.warn("Can't read data from description, bad json! Exception message is {}", ex.getMessage());
    }
    return devices;
  }

  public static List<TemperatureObservation> getObservations(JSONArray observations) {
    List<TemperatureObservation> obs = new ArrayList<>();
    String timestamp = Long.toString(System.currentTimeMillis());
    for (int i = 0; i < observations.length(); i++) {
      try {
        JSONObject object = observations.getJSONObject(i);
        String id = hash(Keys.DRIVER_PID, Integer.toString(object.getInt(SENSOR_ID)));
        String value = Double.toString(object.getDouble(VALUE));
        //Using driver timestamp
        //String timestamp = Long.toString(object.getLong(TIMESTAMP));
        obs.add(new TemperatureObservation(id, timestamp, value));
      } catch (JSONException ex) {
        logger.warn("Can't read data from observations, bad json! Exception message is {}", ex.getMessage());
      }
    }

    return obs;
  }

  public static void getAndPublishObservations(JSONArray observations, DeviceDriverManager manager,
      Map<String, Device> map) {

    for (int i = 0; i < observations.length(); i++) {
      try {
        JSONObject object = observations.getJSONObject(i);
        // String id = hash(Keys.DRIVER_PID, Integer.toString(object.getInt(SENSOR_ID)));
        String id = Integer.toString(object.getInt(SENSOR_ID));
        String value = Double.toString(object.getDouble(VALUE));
        //Using driver timestamp
        String timestamp = Long.toString(System.currentTimeMillis());
        manager.registerObservation(map.get(id), new TemperatureObservation(id, timestamp, value));
      } catch (JSONException ex) {
        logger.warn("Can't read data from observations, bad json!", ex);
      }
    }
  }
}
