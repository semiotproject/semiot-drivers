package ru.semiot.platform.drivers.impulse.electric;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class ImpulseDevice extends Device {

  private static final String TEMPLATE_PATH
      = "/ru/semiot/platform/drivers/impulse/electric/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;
  private final String URI;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description",
          ImpulseDevice.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(ImpulseDevice.class)
          .error(ex.getMessage(), ex);
    }
  }

  public ImpulseDevice(String id, String URI) {
    super(id);
    setProperty(Keys.DEVICE_ID, id);
    this.URI = URI;
  }

  public String getURI(){
    return URI;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof ImpulseDevice) {
      ImpulseDevice that = (ImpulseDevice) obj;

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
