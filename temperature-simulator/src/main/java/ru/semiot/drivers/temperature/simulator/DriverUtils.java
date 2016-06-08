package ru.semiot.drivers.temperature.simulator;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final String FLAT_NUMBER = "flat_number";

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

  public static List<TemperatureDevice> getDevices(JSONArray description) {
    List<TemperatureDevice> devices = new ArrayList<>();
    for (int i = 0; i < description.length(); i++) {
      try {
        JSONObject building = description.getJSONObject(i);
        String building_num = Integer.toString(building.getInt(BUILDING_NUMBER));
        String building_id = hash(Keys.BUILDING_ID, building_num);
        JSONArray flats = building.getJSONArray(FLATS);
        for (int q = 0; q < flats.length(); q++) {
          JSONObject flat = flats.getJSONObject(q);
          String flat_number = Integer.toString(flat.getInt(FLAT_NUMBER));
          String flat_id = hash(Keys.FLAT_ID, flat_number);
          JSONArray sensors = flat.getJSONArray(SENSORS);
          for (int j = 0; j < sensors.length(); j++) {
            String sensor_id = hash(Keys.DRIVER_PID, Integer.toString(sensors.getJSONObject(j).getInt(SENSOR_ID)));
            devices.add(new TemperatureDevice(sensor_id, building_num, flat_number, building_id, flat_id));
          }
        }
      } catch (JSONException ex) {
        logger.warn("Can't read data from description, bad json! Exception message is {}", ex.getMessage());
      }
    }
    return devices;
  }

  public static List<TemperatureObservation> getObservations(JSONArray observations) {
    List<TemperatureObservation> obs = new ArrayList<>();
    for (int i = 0; i < observations.length(); i++) {
      try {
        JSONObject object = observations.getJSONObject(i);
        String id = hash(Keys.DRIVER_PID, Integer.toString(object.getInt(SENSOR_ID)));
        String value = Double.toString(object.getDouble(VALUE));
        String timestamp = Long.toString(object.getLong(TIMESTAMP));
        timestamp = timestamp.substring(0, timestamp.length() - 3);
        obs.add(new TemperatureObservation(id, timestamp, value));
      } catch (JSONException ex) {
        logger.warn("Can't read data from observations, bad json! Exception message is {}", ex.getMessage());
      }
    }

    return obs;
  }
}
