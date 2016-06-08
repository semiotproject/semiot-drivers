package ru.semiot.drivers.temperature.simulator;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class TemperatureDevice extends Device {

  private static final String TEMPLATE_PATH = "/ru/semiot/drivers/temperature/simulator/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description", TemperatureDevice.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(TemperatureDevice.class).error(ex.getMessage(), ex);
    }
  }

  public TemperatureDevice(String id, String building_num, String flat_num, String building_id, String flat_id) {
    super(id);
    getProperties().put(Keys.BUILDING, building_num);
    getProperties().put(Keys.FLAT, flat_num);
    getProperties().put(Keys.BUILDING_ID, building_id);
    getProperties().put(Keys.FLAT_ID, flat_id);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof TemperatureDevice) {
      TemperatureDevice that = (TemperatureDevice) obj;

      return super.equals(obj);
    }

    return false;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash += super.hashCode();
    return hash;
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return DESCRIPTION_TEMPLATE;
  }

}
