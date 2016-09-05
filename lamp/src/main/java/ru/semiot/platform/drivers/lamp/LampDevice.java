package ru.semiot.platform.drivers.lamp;

import java.io.IOException;
import org.eclipse.californium.core.CoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class LampDevice extends Device {

  private static final Logger logger = LoggerFactory.getLogger(LampDevice.class);
  private static final String TEMPLATE_PATH = "/ru/semiot/platform/drivers/lamp/description.ttl";
  private static RDFTemplate TEMPLATE_DESCRIPTION;
  private static final String TEMPLATE_MSG = "{\"@context\":\"/config/context\",\"pwm-value\":\"${VAL}\"}";
  private CoapClient client;

  private boolean isOn = false;
  private int brightness = 50;

  static {
    try {
      TEMPLATE_DESCRIPTION = new RDFTemplate("description",
          LampDevice.class.getResourceAsStream(TEMPLATE_PATH));
    } catch (IOException ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  public LampDevice(String id, String uri) {
    super(id);
    client = new CoapClient(uri);
    try {
      String resp = client.get().getResponseText();

      this.brightness = Integer.parseInt(resp.substring(
          resp.lastIndexOf("\"pwm-value\":\"") + "\"pwm-value\":\"".length(),
          resp.lastIndexOf('\"')));
      this.isOn = brightness > 0;
    } catch (NumberFormatException | NullPointerException ex) {
      logger.error("Bad response! Can't parse response value. Exception message is {}", ex.getMessage());
    } catch (RuntimeException ex) {
      logger.error("Bad resource! Can't execute request! URI is {}.Exception message is {}", client.getURI(), ex.getMessage());
    }
  }

  public void shutdown() {
    client.shutdown();
  }

  public boolean getIsOn() {
    return isOn;
  }

  public void setIsOn(boolean isOn) {
    this.isOn = isOn;
    client.put(TEMPLATE_MSG.replace("${VAL}", String.valueOf(isOn ? brightness : 0)), 0);
  }

  public int getBrightness() {
    return brightness;
  }

  public void setBrightness(int brightness) {
    this.brightness = brightness;
    client.put(TEMPLATE_MSG.replace("${VAL}", String.valueOf(this.brightness)), 0);
  }

  @Override
  public RDFTemplate getRDFTemplate() {
    return TEMPLATE_DESCRIPTION;
  }

}
