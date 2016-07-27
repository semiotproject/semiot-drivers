package ru.semiot.platform.drivers.narodmon.temperature;


import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

import java.io.IOException;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class NarodmonObservation extends Observation {

  private static final String TEMPERATURE_TEMPLATE_PATH =
      "/ru/semiot/platform/drivers/narodmon/temperature/observation.jsonld";
  private static RDFTemplate TEMPERATURE_TEMPLATE;

  public static final String TEMPERATURE_TYPE = "temperature";
  public static final String TEMPERATURE_TEMPLATE_SENSOR = "${SYSTEM_ID}-temperature";

  static {
    try {
      TEMPERATURE_TEMPLATE = new RDFTemplate("temperature",
          NarodmonObservation.class.getResourceAsStream(TEMPERATURE_TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(NarodmonObservation.class).error(ex.getMessage(), ex);
    }
  }

  public NarodmonObservation(String deviceId, String sensorId, String timestamp, String value,
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
    throw new IllegalStateException();
  }

}
