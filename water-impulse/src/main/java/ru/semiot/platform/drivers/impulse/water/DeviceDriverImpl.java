package ru.semiot.platform.drivers.impulse.water;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.WebLink;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "Water Impulse Device Driver";

  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
          URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
              + "master/water-impulse/"
              + "src/main/resources/ru/semiot/platform/drivers/impulse/water/prototype.ttl#ImpulseDevice"));
  private final Map<String, ImpulseDevice> devicesMap = Collections.synchronizedMap(new HashMap<>());

  private volatile DeviceDriverManager deviceManager;
  private Configuration commonConfiguration;
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture> handles = null;

  public void start() {
    logger.info("{} started!", DRIVER_NAME);
    deviceManager.registerDriver(info);

    Set<WebLink> discover = new CoapClient(commonConfiguration.getAsString(Keys.COAP_ENDPOINT))
        .discover();
    String index;
    String URI = commonConfiguration.getAsString(Keys.COAP_ENDPOINT);
    String id;
    int count = 0;
    for (WebLink link : discover) {
      logger.debug("Link is {}", link.getURI());
      if (link.getURI().matches("/tick\\d+")) {
        index = link.getURI();
        id = getHash(index);
        ImpulseDevice device = new ImpulseDevice(id, URI + "/tick");
        //ImpulseDevice device = new ImpulseDevice(id, URI + index);
        devicesMap.put(id, device);
        deviceManager.registerDevice(info, device);
        count++;
      }
    }

    handles = new ArrayList<>();
    this.scheduler = Executors.newScheduledThreadPool(count);
    logger.debug("Try to start {} pullers", count);
    for (ImpulseDevice dev : devicesMap.values()) {
      handles.add(startPuller(dev));
    }
    logger.debug("All pullers started");
  }

  public void stop() {
    logger.debug("{} is stopping!", DRIVER_NAME);

    try {

      devicesMap.clear();
      scheduler.shutdown();
      try {
        scheduler.awaitTermination(10, TimeUnit.SECONDS);
      } finally {
        scheduler.shutdownNow();
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
    logger.info("{} stopped!", DRIVER_NAME);
  }

  public ScheduledFuture startPuller(ImpulseDevice dev) {
    logger.debug("Try to start puller!");
    ScheduledPuller puller = new ScheduledPuller(this, dev);

    logger.debug("Try to schedule polling with interval {} min",
        commonConfiguration.get(Keys.POLLING_INTERVAL));

    ScheduledFuture handle = this.scheduler.scheduleAtFixedRate(
        puller, 0,
        commonConfiguration.getAsLong(Keys.POLLING_INTERVAL),
        TimeUnit.SECONDS);

    logger.debug("Puller started!");
    return handle;
  }

  public void stopPuller(ScheduledFuture handle) {
    logger.debug("Try to stop puller!");
    if (handle == null) {
      return;
    }
    handle.cancel(true);
    logger.debug("Puller stoped!");
  }

  public void registerDevice(ImpulseDevice device) {
    if (!devicesMap.containsKey(device.getId())) {
      devicesMap.put(device.getId(), device);
      deviceManager.registerDevice(info, device);
    }
  }

  public void publishNewObservation(ImpulseObservation observation) {
    String deviceId = observation.getProperty(Keys.DEVICE_ID);
    deviceManager.registerObservation(devicesMap.get(deviceId), observation);
  }

  @Override
  public String getDriverName() {
    return DRIVER_NAME;
  }

  @Override
  public void updated(Dictionary properties) throws ConfigurationException {
    synchronized (this) {
      if (properties != null) {
        if (!configuration.isConfigured()) {
          logger.debug("Configuration got");
          try {
            configuration.putAll(properties);
            commonConfiguration = getCommonConfiguration(configuration);
            configuration.setConfigured();
            logger.info("Received configuration is correct!");
          } catch (ConfigurationException ex) {
            configuration.clear();
            throw ex;
          }
        } else {
          logger.warn("Is already configured! Skipping.");
        }
      } else {
        logger.debug("Configuration is empty. Skipping.");
      }
    }
  }

  private Configuration getCommonConfiguration(Configuration cfg) throws ConfigurationException {
    logger.debug("Try to get common configuration");
    Configuration config = new Configuration();
    try {
      String uri = cfg.getAsString(Keys.COAP_ENDPOINT);
      if (uri == null) {
        logger.error("Bad common configuration! Field '{}' is null!", Keys.COAP_ENDPOINT);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Field is null");
      }
      if (uri.endsWith("/")) {
        uri = uri.substring(0, uri.length() - 1);
      }
      config.put(Keys.COAP_ENDPOINT, uri);
      String pollingInterval = cfg.getAsString(Keys.POLLING_INTERVAL);
      if (uri == null) {
        logger.error("Bad common configuration! Field '{}' is null!", Keys.POLLING_INTERVAL);
        throw new ConfigurationException(Keys.POLLING_INTERVAL,
            "Bad common configuration. Field is null");
      }
      config.put(Keys.POLLING_INTERVAL, pollingInterval);
    } catch (Throwable ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

  private String getHash(String id) {
    String name = Keys.DRIVER_PID + id;
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