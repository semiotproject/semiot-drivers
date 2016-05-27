package ru.semiot.drivers.mercury270;

import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

import java.io.IOException;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class MercuryObservation extends Observation {

  private static final String TICK_TEMPLATE_PATH =
      "/ru/semiot/drivers/mercury270/tick-observation.ttl";
  private static RDFTemplate TICK_TEMPLATE;
  private static final String TICK_TEMPLATE_SENSOR = "${SYSTEM_ID}-tick";

  static {
    try {
      TICK_TEMPLATE =
          new RDFTemplate("tick", MercuryObservation.class.getResourceAsStream(TICK_TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(MercuryObservation.class).error(ex.getMessage(), ex);
    }
  }

  public MercuryObservation(String deviceId, String timestamp, String value) {
    super(deviceId, TICK_TEMPLATE_SENSOR.replace("${SYSTEM_ID}", deviceId), timestamp);
    getProperties().put(Keys.OBSERVATION_VALUE, value);
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return TICK_TEMPLATE;
  }

}
