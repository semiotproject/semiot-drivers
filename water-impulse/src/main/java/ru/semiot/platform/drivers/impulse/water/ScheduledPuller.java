package ru.semiot.platform.drivers.impulse.water;

import org.eclipse.californium.core.CoapClient;
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
  private final ImpulseDevice device;
  private final double MAGIC_NUMBER = 0.1;
  private long lastValue = -1;

  private static final String TEMPLATE_MSG = "\"tick-value\":\"";

  public ScheduledPuller(DeviceDriverImpl driver, ImpulseDevice device) {
    this.driver = driver;
    this.device = device;
    client = new CoapClient(device.getURI());
  }

  @Override
  public void run() {
    try {
       logger.debug("Try to pull from {} ...", client.getURI());
      String resp = client.get().getResponseText();
      logger.debug("Response msg is {}", resp);
      long val = Long.parseLong(resp.substring(
          resp.lastIndexOf(TEMPLATE_MSG) + TEMPLATE_MSG.length(),
          resp.lastIndexOf('\"')));
      if (val != lastValue) {
        ImpulseObservation obs = new ImpulseObservation(device.getId(),
            ImpulseObservation.TEMPLATE_SENSOR.replace("${SYSTEM_ID}", device.getId()),
            Long.toString(System.currentTimeMillis()),
            String.valueOf(MAGIC_NUMBER * val),
            ImpulseObservation.TYPE);
        lastValue = val;
        driver.publishNewObservation(obs);
      }
    } catch (Throwable ex) {
      logger.error("Some reason: ", ex);
    }
  }

}
