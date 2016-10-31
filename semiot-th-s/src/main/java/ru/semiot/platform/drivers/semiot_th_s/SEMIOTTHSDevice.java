package ru.semiot.platform.drivers.semiot_th_s;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

public class SEMIOTTHSDevice extends Device {

  private static final String TEMPLATE_PATH
      = "/ru/semiot/platform/drivers/semiot_th_s/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description",
          SEMIOTTHSDevice.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(SEMIOTTHSDevice.class)
          .error(ex.getMessage(), ex);
    }
  }

  public SEMIOTTHSDevice(String label, String id, String room) {
    super(id);
    setProperty(Keys.DEVICE_ID, id);
    setProperty(Keys.DEVICE_LABEL, label);
    setProperty(Keys.DEVICE_ROOM, room);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof SEMIOTTHSDevice) {
      SEMIOTTHSDevice that = (SEMIOTTHSDevice) obj;

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