package ru.semiot.drivers.mercury270;

import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class Mercury extends Device {

  private static final String TEMPLATE_PATH = "/ru/semiot/drivers/mercury270/description.ttl";
  private static String DESCRIPTION_TEMPLATE;

  static {
    try {
      DESCRIPTION_TEMPLATE = IOUtils.toString(Mercury.class.getResourceAsStream(TEMPLATE_PATH));
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
  public String getRDFTemplate() {
    return DESCRIPTION_TEMPLATE;
  }

}
