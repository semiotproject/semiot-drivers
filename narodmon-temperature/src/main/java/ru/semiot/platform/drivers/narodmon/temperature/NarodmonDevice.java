package ru.semiot.platform.drivers.narodmon.temperature;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

import java.io.IOException;

public class NarodmonDevice extends Device {

  private static final String TEMPLATE_PATH
      = "/ru/semiot/platform/drivers/narodmon/temperature/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description",
          NarodmonDevice.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(NarodmonDevice.class)
          .error(ex.getMessage(), ex);
    }
  }

  public NarodmonDevice(String id, String latitude, String longitude) {
    super(id);
    setProperty(Keys.DEVICE_ID, id);
    setProperty(Keys.LATITUDE, latitude);
    setProperty(Keys.LONGITUDE, longitude);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof NarodmonDevice) {
      NarodmonDevice that = (NarodmonDevice) obj;

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
