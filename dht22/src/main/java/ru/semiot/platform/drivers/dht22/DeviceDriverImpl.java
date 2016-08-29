package ru.semiot.platform.drivers.dht22;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.californium.core.CoapClient;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;

public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "DHT 22 Device Driver";

  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
          URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
              + "master/dht22/"
              + "src/main/resources/ru/semiot/platform/drivers/dht22/prototype.ttl#DHT22Device"));
  private final Map<String, DHT22Device> devicesMap = Collections.synchronizedMap(new HashMap<>());

  private volatile DeviceDriverManager deviceManager;
  private Configuration commonConfiguration;
  private List<Configuration> configurations;
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture> handles = null;
  private List<Integer> countsRepeatableProperties;

  private static final String POSTFIX = "/dht1";
  private static final String LOCATION = "/location";
  private static final String LOCATION_RSP_TEMPLATE = "\"room-number\":\"";

  public void start() {
    logger.info("{} started!", DRIVER_NAME);
    deviceManager.registerDriver(info);
    String uri, id, room, resp;
    CoapClient client = new CoapClient();
    for (Configuration cfg : configurations) {
      uri = cfg.getAsString(Keys.COAP_ENDPOINT);
      id = getHash(uri);
      client.setURI(uri + LOCATION);
      try {
        resp = client.get().getResponseText();
        room = resp.substring(resp.lastIndexOf(LOCATION_RSP_TEMPLATE) + LOCATION_RSP_TEMPLATE.length(),
            resp.indexOf("\"", resp.lastIndexOf(LOCATION_RSP_TEMPLATE) + LOCATION_RSP_TEMPLATE.length()));
        DHT22Device device = new DHT22Device(id, uri + POSTFIX, room);
        devicesMap.put(id, device);
        deviceManager.registerDevice(info, device);
      } catch (NullPointerException ex) {
        logger.warn("Can't get response from {}", uri);
      }
    }
    client.shutdown();
    handles = new ArrayList<>();
    this.scheduler = Executors.newScheduledThreadPool(devicesMap.size());
    logger.debug("Try to start {} pullers", devicesMap.size());
    for (DHT22Device dev : devicesMap.values()) {
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

  public ScheduledFuture startPuller(DHT22Device dev) {
    logger.debug("Try to start puller!");
    ScheduledPuller puller = new ScheduledPuller(this, dev);

    logger.debug("Try to schedule polling with interval {} min",
        commonConfiguration.get(Keys.POLLING_INTERVAL));

    ScheduledFuture handle = this.scheduler.scheduleAtFixedRate(
        puller, 0,
        commonConfiguration.getAsLong(Keys.POLLING_INTERVAL),
        TimeUnit.MINUTES);

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

  public void registerDevice(DHT22Device device) {
    if (!devicesMap.containsKey(device.getId())) {
      devicesMap.put(device.getId(), device);
      deviceManager.registerDevice(info, device);
    }
  }

  public void publishNewObservation(DHT22Observation observation) {
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
            countsRepeatableProperties = getCountsRepeatableProperties(Keys.COAP_ENDPOINT);
            configurations = getConfigurations(countsRepeatableProperties);
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

  private List<Integer> getCountsRepeatableProperties(String propPrefix) throws ConfigurationException {
    logger.debug("Try to get count of repeatable property \"{}\"", propPrefix);
    List<Integer> counts = new ArrayList<>();
    int index;

    for (String key : configuration.keySet()) {
      if (key.contains(propPrefix) && !counts.contains(
          index = Integer.parseInt(key.substring(0, key.indexOf("." + propPrefix))))) {
        counts.add(index);
      }
    }
    if (counts.isEmpty()) {
      logger.error("Bad repeatable configuration! Did not find a repeatable property");
      throw new ConfigurationException(propPrefix, "Did not find a repeatable property");
    }
    return counts;
  }

  private List<Configuration> getConfigurations(List<Integer> counts) throws ConfigurationException {
    logger.debug("Try to get repeatable configuration for each puller");
    List<Configuration> conf = new ArrayList<>();
    for (int i : counts) {
      Configuration cfg = new Configuration();
      String uri = configuration.getAsString(String.valueOf(i) + "." + Keys.COAP_ENDPOINT);
      if (uri == null) {
        logger.error("Bad repeatable configuration! Field '{}' is null!", Keys.COAP_ENDPOINT);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad repeatable configuration. Field is null");
      }
      if (uri.endsWith("/")) {
        uri = uri.substring(0, uri.length() - 1);
      }
      cfg.put(Keys.COAP_ENDPOINT, uri);
      conf.add(cfg);
    }
    return conf;
  }

  private Configuration getCommonConfiguration(Configuration cfg) throws ConfigurationException {
    logger.debug("Try to get common configuration");
    Configuration config = new Configuration();
    try {
      String pollingInterval = cfg.getAsString(Keys.POLLING_INTERVAL);
      if (pollingInterval == null) {
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
