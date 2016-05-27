package ru.semiot.drivers.mercury270;

import org.eclipse.californium.core.CoapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class ScheduledPuller implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(ScheduledPuller.class);
  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  final CoapClient client;
  final DeviceDriverImpl driver;
  final Configuration configuration;
  final String deviceId;

  public ScheduledPuller(DeviceDriverImpl driver, Configuration config) {
    this.driver = driver;
    this.configuration = config;
    client = new CoapClient();
    String uri = configuration.getAsString(Keys.COAP_ENDPOINT);
    logger.debug("Try to get a model from {}", (uri + Keys.DEVICE_MODEL));
    String model = client.setURI(uri + Keys.DEVICE_MODEL).get().getResponseText();
    logger.debug("Model got! It is {}", model);
    logger.debug("Try to get a serial from {}", (uri + Keys.DEVICE_SERIAL));
    String serial = client.setURI(uri + Keys.DEVICE_SERIAL).get().getResponseText();
    logger.debug("Serial got! It is {}", serial);
    logger.debug("Try to get a mac from {}", (uri + Keys.DEVICE_MAC));
    int mac = ByteBuffer.wrap(client.setURI(uri + Keys.DEVICE_MAC).get().getPayload())
        .order(ByteOrder.BIG_ENDIAN).getInt();
    logger.debug("Mac got! It is {}", mac);
    deviceId = hash(Keys.DRIVER_PID, model + serial + mac);
    logger.debug("Device's id is {}", deviceId);
    logger.debug("Try to register this device");
    this.driver.registerDevice(new Mercury(deviceId));
    logger.debug("Start pulling from {}", (uri + Keys.DEVICE_TICK));
    client.setURI(uri + Keys.DEVICE_TICK);
  }

  @Override
  public void run() {
    logger.debug("Starting to pull...");
    if (!DeviceDriverImpl.ping(client.getURI(), 5000)) {
      logger.warn("The device with uri {} does not respond! Try to pull later", client.getURI());
      return;
    }
    int value = ByteBuffer.wrap(client.get().getPayload()).order(ByteOrder.BIG_ENDIAN).getInt();

    String timestamp = String.valueOf(System.currentTimeMillis());
    timestamp = timestamp.substring(0, timestamp.length() - 3);
    logger.debug("Value is {} with timestamp {}", value, timestamp);
    MercuryObservation obs = new MercuryObservation(deviceId, timestamp, Integer.toString(value));

    driver.publishNewObservation(obs);

  }


  public String hash(String prefix, String id) {
    String name = prefix + id;
    int h = FNV_32_INIT;
    final int len = name.length();
    for (int i = 0; i < len; i++) {
      h ^= name.charAt(i);
      h *= FNV_32_PRIME;
    }
    long longHash = h & 0xffffffffl;
    return String.valueOf(longHash);
  }
}
