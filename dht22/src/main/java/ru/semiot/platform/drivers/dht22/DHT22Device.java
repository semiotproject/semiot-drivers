package ru.semiot.platform.drivers.dht22;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DHT22Device extends Device {

  private static final String TEMPLATE_PATH
      = "/ru/semiot/platform/drivers/dht22/description.ttl";
  private static RDFTemplate DESCRIPTION_TEMPLATE;
  private final String URI;
  private final String room;

  static {
    try {
      DESCRIPTION_TEMPLATE = new RDFTemplate("description",
          DHT22Device.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      LoggerFactory.getLogger(DHT22Device.class)
          .error(ex.getMessage(), ex);
    }
  }

  public DHT22Device(String id, String URI, String room) {
    super(id);
    setProperty(Keys.DEVICE_ID, id);
    setProperty(Keys.ROOM, room);
    this.URI = URI;
    this.room = room;
  }

  public String getURI(){
    return URI;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof DHT22Device) {
      DHT22Device that = (DHT22Device) obj;

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