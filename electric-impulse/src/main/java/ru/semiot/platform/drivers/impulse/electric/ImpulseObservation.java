package ru.semiot.platform.drivers.impulse.electric;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class ImpulseObservation extends Observation {

  private static final String TEMPLATE_PATH =
      "/ru/semiot/platform/drivers/impulse/electric/observation.jsonld";
  private static RDFTemplate TEMPLATE;

  public static final String TYPE = "impulse";
  public static final String TEMPLATE_SENSOR = "${SYSTEM_ID}-impulse";

  static {
    try {
      TEMPLATE = new RDFTemplate("temperature",
          ImpulseObservation.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(ImpulseObservation.class).error(ex.getMessage(), ex);
    }
  }

  public ImpulseObservation(String deviceId, String sensorId, String timestamp, String value,
      String type) {
    super(deviceId, sensorId, timestamp);

    getProperties().put(Keys.OBSERVATION_VALUE, value);
    getProperties().put(Keys.OBSERVATION_TYPE, type);
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    if (getProperty(Keys.OBSERVATION_TYPE).equalsIgnoreCase(TYPE)) {
      return TEMPLATE;
    }
    throw new IllegalStateException();
  }

}
