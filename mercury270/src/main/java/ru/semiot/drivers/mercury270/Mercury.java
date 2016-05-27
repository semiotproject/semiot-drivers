package ru.semiot.drivers.mercury270;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class Mercury extends Device {

  private static final String TEMPLATE_PATH = "/ru/semiot/drivers/mercury270/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description", Mercury.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(Mercury.class).error(ex.getMessage(), ex);
    }
  }

  public Mercury(String id) {
    super(id);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof Mercury) {
      Mercury that = (Mercury) obj;

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
