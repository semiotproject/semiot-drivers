package ru.semiot.drivers.mercury270;

import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;

import java.io.IOException;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class MercuryObservation extends Observation {

  private static final String TICK_TEMPLATE_PATH =
      "/ru/semiot/drivers/mercury270/tick-observation.ttl";
  private static String TICK_TEMPLATE;

  static {
    try {
      TICK_TEMPLATE =
          IOUtils.toString(MercuryObservation.class.getResourceAsStream(TICK_TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(MercuryObservation.class).error(ex.getMessage(), ex);
    }
  }

  public MercuryObservation(String deviceId, String sensorId, String timestamp, String value) {
    super(deviceId, sensorId, timestamp);
    getProperties().put(Keys.OBSERVATION_VALUE, value);
  }

  @Override
  public String getRDFTemplate() {
    return TICK_TEMPLATE;
  }

}
