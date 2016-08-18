package ru.semiot.platform.drivers.impulse.electric;

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
  private final double MAGIC_NUMBER = 0.3125;
  private long lastValue = 0;

  private static final String TEMPLATE_MSG = "\"tick-value\":\"";

  public ScheduledPuller(DeviceDriverImpl driver, ImpulseDevice device) {
    this.driver = driver;
    this.device = device;
    client = new CoapClient(device.getURI());
  }

  @Override
  public void run() {
    try {
      logger.debug("Try to pull...");
      String resp = client.get().getResponseText();
      long val = Long.parseLong(resp.substring(
          resp.lastIndexOf(TEMPLATE_MSG) + TEMPLATE_MSG.length(),
          resp.lastIndexOf('\"')));
      if (val != lastValue) {
        ImpulseObservation obs = new ImpulseObservation(device.getId(),
            ImpulseObservation.TEMPLATE_SENSOR,
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
