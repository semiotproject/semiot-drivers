package ru.semiot.platform.drivers.narodmon.temperature;

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

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;

import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;

public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private final String driverName = "Narodmon Driver";
  private final Map<String, Device> devicesMap
      = Collections.synchronizedMap(new HashMap<>());
  private final Configuration fullConfiguration = new Configuration();
  private final DriverInformation info = new DriverInformation(
      Keys.DRIVER_PID,
      URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
          + "master/narodmon-temperature/"
          + "src/main/resources/ru/semiot/platform/drivers/narodmon/temperature/prototype.ttl#"
          + "NarodmonDevice"));

  private volatile DeviceDriverManager deviceManager;

  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture> handles = null;
  private List<Configuration> configurations;
  private List<Integer> countsRepeatableProperties;

  public void start() {
    logger.info("{} started!", driverName);
    deviceManager.registerDriver(info);

    handles = new ArrayList<>();
    this.scheduler = Executors.newScheduledThreadPool(countsRepeatableProperties.size());
    logger.debug("Try to start {} pullers", countsRepeatableProperties.size());
    for (Configuration cfg : configurations) {
      handles.add(startPuller(cfg));
    }
    logger.debug("All pullers started");
  }

  public void stop() {
    logger.debug("Try to stop {} pullers", handles.size());
    for (ScheduledFuture handle : handles) {
      stopPuller(handle);
    }
    logger.debug("All pullers stoped");
    handles = null;
    scheduler.shutdown();
    try {
      scheduler.awaitTermination(1, TimeUnit.MINUTES);
    } catch (InterruptedException ex) {
      logger.warn(ex.getMessage(), ex);
    }
    scheduler.shutdownNow();
    logger.debug("Sheduler stoped");
    logger.info("{} stopped!", driverName);
  }

  @Override
  public void updated(Dictionary dictionary) throws ConfigurationException {
    synchronized (this) {
      if (dictionary != null) {
        if (!fullConfiguration.isConfigured()) {
          logger.debug("Configuration got");
          try {
            fullConfiguration.putAll(dictionary);
            logger.debug("Full configuration is {}", fullConfiguration);
            Configuration commonConfiguration = getCommonConfiguration();
            countsRepeatableProperties = getCountsRepeatableProperties(Keys.AREA);
            configurations = getAllRepeatableConfigurations(countsRepeatableProperties, commonConfiguration);
            fullConfiguration.setConfigured();
            logger.info("Received configuration is correct!");
          } catch (ConfigurationException ex) {
            fullConfiguration.clear();
            throw ex;
          }
        } else {
          logger.warn("Driver is already configured! Ignoring it");
        }
      } else {
        logger.debug("Configuration is empty. Skipping it");
      }
    }
  }

  private List<Configuration> getAllRepeatableConfigurations(List<Integer> counts, Configuration commonConfiguration) throws ConfigurationException {
    logger.debug("Try to get repeatable configuration for each puller");
    List<Configuration> cfgs = new ArrayList<>();
    for (int i : counts) {
      Configuration cfg = getAreaConfiguration(i);
      //logger.debug("Count is {}, configuration is [{}]", i, cfg);
      cfg.putAll(commonConfiguration);
      cfgs.add(cfg);
    }
    return cfgs;
  }

  @Override
  public String getDriverName() {
    return driverName;
  }

  public void registerDevice(Device device) {
    if (!devicesMap.containsKey(device.getId())) {
      devicesMap.put(device.getId(), device);
      deviceManager.registerDevice(info, device);
    }
  }

  public void publishNewObservation(NarodmonObservation observation) {
    String deviceId = observation.getProperty(Keys.DEVICE_ID);
    deviceManager.registerObservation(devicesMap.get(deviceId), observation);
  }

  public ScheduledFuture startPuller(Configuration config) {
    logger.debug("Try to start puller!");
    logger.debug("Config is " + config.toString());
    ScheduledPuller puller = new ScheduledPuller(this, config);

    logger.debug("Try to schedule polling with interval {}min with configuration [{}]",
        fullConfiguration.get(Keys.POLLING_INTERVAL),
        config.toString());

    ScheduledFuture handle = this.scheduler.scheduleAtFixedRate(
        puller, 0,
        fullConfiguration.getAsLong(Keys.POLLING_INTERVAL),
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
    return counts;
  }

  private Configuration getCommonConfiguration() throws ConfigurationException {
    logger.debug("Try to get common configuration");
    Configuration config = new Configuration();
    try {
      //Put only needed properties
      config.put(Keys.API_KEY, fullConfiguration.get(Keys.API_KEY));
      config.put(Keys.UUID, fullConfiguration.get(Keys.UUID));
      config.put(Keys.POLLING_INTERVAL, fullConfiguration.get(Keys.POLLING_INTERVAL));
    } catch (java.lang.NullPointerException ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

  private Configuration getAreaConfiguration(int area) throws ConfigurationException {
    logger.debug("Try to get configuration for {} area", area);
    Configuration config = new Configuration();
    double lon, lat, radius;
    try {
      //1.ru.semiot.area.longitude
      lon = Double.parseDouble(fullConfiguration.getAsString(area + "." + Keys.AREA + ".longitude"));
      lat = Double.parseDouble(fullConfiguration.getAsString(area + "." + Keys.AREA + ".latitude"));
      radius = Double.parseDouble(fullConfiguration.getAsString(area + "." + Keys.AREA + ".radius"));
      if (lon > 180 || lon < -180 || radius > 50 || radius < 1 || lat > 90 || lat < -90) {
        throw new java.lang.NullPointerException();
      }
      config.put(Keys.LAT, String.valueOf(lat));
      config.put(Keys.LONG, String.valueOf(lon));
      config.put(Keys.RADIUS, String.valueOf(radius));
    } catch (java.lang.NullPointerException ex) {
      logger.error("Bad repeatable configuration! Can not extract field of property {}.{}", Keys.AREA, area);
      throw new ConfigurationException(Keys.AREA + "." + area, "Can not extract field of repeatable property", ex);
    }
    return config;
  }

}
