package ru.semiot.drivers.temperature.simulator;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class TemperatureObservation extends Observation {

  private static final String TEMPERATURE_TEMPLATE_PATH
      = "/ru/semiot/drivers/temperature/simulator/temperature-observation.ttl";
  private static RDFTemplate TEMPERATURE_TEMPLATE;
  private static final String TEMPERATURE_TEMPLATE_SENSOR = "${SYSTEM_ID}-temperature";

  static {
    try {
      TEMPERATURE_TEMPLATE
          = new RDFTemplate("temperature", TemperatureObservation.class.getResourceAsStream(TEMPERATURE_TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(TemperatureObservation.class).error(ex.getMessage(), ex);
    }
  }

  public TemperatureObservation(String deviceId, String timestamp, String value) {
    super(deviceId, TEMPERATURE_TEMPLATE_SENSOR.replace("${SYSTEM_ID}", deviceId), timestamp);
    getProperties().put(Keys.OBSERVATION_VALUE, value);
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return TEMPERATURE_TEMPLATE;
  }
}
