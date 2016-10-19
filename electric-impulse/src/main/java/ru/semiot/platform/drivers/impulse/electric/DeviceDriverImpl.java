package ru.semiot.platform.drivers.impulse.electric;

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
  private static final String DRIVER_NAME = "Electric Impulse Device Driver";

  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  private final Configuration fullConfiguration = new Configuration();
  private List<Integer> countsRepeatableProperties;
  private List<Configuration> repeatableConfigurations;
  
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
          URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
              + "master/electric-impulse/"
              + "src/main/resources/ru/semiot/platform/drivers/impulse/electric/prototype.ttl#ImpulseDevice"));
  private final Map<String, ImpulseDevice> devicesMap = Collections.synchronizedMap(new HashMap<>());

  private volatile DeviceDriverManager deviceManager;
  //private Configuration commonConfiguration;
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture> handles = null;

  public void start() {
    logger.info("{} started!", DRIVER_NAME);
    deviceManager.registerDriver(info);
    handles = new ArrayList<>();
    this.scheduler = Executors.newScheduledThreadPool(repeatableConfigurations.size());

    logger.debug("Try to start {} pullers", repeatableConfigurations.size());

    for (Configuration cfg : repeatableConfigurations) {
      String uri = cfg.getAsString(Keys.COAP_ENDPOINT) + "/dischargeValue";
      String id = getHash(uri);
      ImpulseDevice device = new ImpulseDevice(id, uri);
      devicesMap.put(id, device);
      deviceManager.registerDevice(info, device);
      handles.add(startPuller(device, cfg.getAsInteger(Keys.POLLING_INTERVAL)));
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

  public ScheduledFuture startPuller(ImpulseDevice dev, int interval) {
    logger.debug("Try to start puller!");
    ScheduledPuller puller = new ScheduledPuller(this, dev);

    logger.debug("Try to schedule polling with interval {} sec",
            interval);

    ScheduledFuture handle = this.scheduler.scheduleAtFixedRate(
            puller, 0,
            interval,
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
        if (!fullConfiguration.isConfigured()) {
          logger.debug("Configuration got");
          try {
            fullConfiguration.putAll(properties);
            //commonConfiguration = getCommonConfiguration(fullConfiguration);
            countsRepeatableProperties = getCountsRepeatableProperties(Keys.COAP_ENDPOINT);
            repeatableConfigurations = getAllRepeatableConfigurations(countsRepeatableProperties, null);
            fullConfiguration.setConfigured();
            logger.info("Received configuration is correct!");
          } catch (ConfigurationException ex) {
            fullConfiguration.clear();
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
  
  private List<Integer> getCountsRepeatableProperties(String propPrefix) throws ConfigurationException {
    logger.debug("Try to get count of repeatable property \"{}\"", propPrefix);
    List<Integer> counts = new ArrayList<>();
    int index;

    for (String key : fullConfiguration.keySet()) {
      if (key.contains(propPrefix) && !counts.contains(
              index = Integer.parseInt(key.substring(0, key.indexOf("." + propPrefix))))) {
        counts.add(index);
      }
    }
    if (counts.isEmpty()) {
      logger.error("Bad repeatable configuration! Did not find a repeatable property");
      throw new ConfigurationException(propPrefix, "Did not find a repeatable property");
    }
    logger.debug("Count of repeatable properties is {}", counts.size());
    return counts;
  }

  private List<Configuration> getAllRepeatableConfigurations(List<Integer> counts, Configuration commonConfiguration) throws ConfigurationException {
    logger.debug("Try to get repeatable configuration for each puller");
    List<Configuration> cfgs = new ArrayList<>();
    for (int i : counts) {
      Configuration cfg = new Configuration();
      logger.debug("Try to get repeatable field {}", i + Keys.COAP_ENDPOINT);
      cfg.put(Keys.COAP_ENDPOINT, fullConfiguration.getAsString(i + "." + Keys.COAP_ENDPOINT));
      logger.debug("Try to get repeatable field {}", i + Keys.COAP_ENDPOINT);
      cfg.put(Keys.POLLING_INTERVAL, fullConfiguration.getAsString(i + "." + Keys.POLLING_INTERVAL));
      //logger.debug("Count is {}, configuration is [{}]", i, cfg);
      if (commonConfiguration != null) {
        cfg.putAll(commonConfiguration);
      }
      cfgs.add(cfg);
    }
    return cfgs;
  }

}
