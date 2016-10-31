package ru.semiot.platform.drivers.semiot_th_s;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

public class SEMIOTTHSObservation extends Observation {

  private static final String TEMPERATURE_TEMPLATE_PATH =
      "/ru/semiot/platform/drivers/semiot_th_s/observation-temperature.jsonld";
  private static final String HUMIDITY_TEMPLATE_PATH =
      "/ru/semiot/platform/drivers/semiot_th_s/observation-humidity.jsonld";
  private static RDFTemplate TEMPERATURE_TEMPLATE;
  private static RDFTemplate HUMIDITY_TEMPLATE;

  public static final String TEMPERATURE_TYPE = "temperature";
  public static final String HUMIDITY_TYPE = "humidity";
  public static final String TEMPERATURE_TEMPLATE_SENSOR = "${SYSTEM_ID}-temperature";
  public static final String HUMIDITY_TEMPLATE_SENSOR = "${SYSTEM_ID}-humidity";

  static {
    try {
      TEMPERATURE_TEMPLATE = new RDFTemplate("temperature",
          SEMIOTTHSObservation.class.getResourceAsStream(TEMPERATURE_TEMPLATE_PATH));
      HUMIDITY_TEMPLATE = new RDFTemplate("humidity",
          SEMIOTTHSObservation.class.getResourceAsStream(HUMIDITY_TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(SEMIOTTHSObservation.class).error(ex.getMessage(), ex);
    }
  }

  public SEMIOTTHSObservation(String deviceId, String sensorId, String timestamp, String value,
      String type) {
    super(deviceId, sensorId, timestamp);

    getProperties().put(Keys.OBSERVATION_VALUE, value);
    getProperties().put(Keys.OBSERVATION_TYPE, type);
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    if (getProperty(Keys.OBSERVATION_TYPE).equalsIgnoreCase(TEMPERATURE_TYPE)) {
      return TEMPERATURE_TEMPLATE;
    }
    if (getProperty(Keys.OBSERVATION_TYPE).equalsIgnoreCase(HUMIDITY_TYPE)) {
      return HUMIDITY_TEMPLATE;
    }

    throw new IllegalStateException();
  }

}
