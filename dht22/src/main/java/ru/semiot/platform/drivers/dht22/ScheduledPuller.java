package ru.semiot.platform.drivers.dht22;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class ScheduledPuller implements Runnable {

  private final static Logger logger = LoggerFactory.getLogger(
      ScheduledPuller.class);

  private final DeviceDriverImpl driver;
  private final CoapClient client;
  private final DHT22Device device;

  private static final String TEMPLATE_RSP_TEMP = "\"temperature-value\":\"";
  private static final String TEMPLATE_RSP_HUM = "\"humidity-value\":\"";

  public ScheduledPuller(DeviceDriverImpl driver, DHT22Device device) {
    this.driver = driver;
    this.device = device;
    client = new CoapClient(device.getURI());
  }

  @Override
  public void run() {
    try {
      logger.debug("Try to pull from {} ...", client.getURI());
      CoapResponse response = client.get();
      if (response != null) {
        String resp = response.getResponseText();
        logger.debug("Response msg is {}", resp);

        driver.publishNewObservation(parseObservation(true, resp));
        driver.publishNewObservation(parseObservation(false, resp));
      }
      else{
        logger.debug("Can't get response from {}. Try next time", client.getURI());
      }
    } catch (Throwable ex) {
      logger.error("Some reason: ", ex);
    }
  }

  private DHT22Observation parseObservation(boolean isTemperature, String text) {
    String template, template_sensor, type;
    if (isTemperature) {
      template = TEMPLATE_RSP_TEMP;
      template_sensor = DHT22Observation.TEMPERATURE_TEMPLATE_SENSOR;
      type = DHT22Observation.TEMPERATURE_TYPE;
    } else {
      template = TEMPLATE_RSP_HUM;
      template_sensor = DHT22Observation.HUMIDITY_TEMPLATE_SENSOR;
      type = DHT22Observation.HUMIDITY_TYPE;
    }
    int start = text.lastIndexOf(template) + template.length();
    double value = Double.parseDouble(text.substring(
        start,
        text.indexOf('\"', start)));
    return new DHT22Observation(device.getId(),
        template_sensor.replace("${SYSTEM_ID}", device.getId()),
        Long.toString(System.currentTimeMillis()),
        String.valueOf(value),
        type);
  }

}
