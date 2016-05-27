package ru.semiot.drivers.mercury270;

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
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.Device;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "Mercury 270";
  private final Configuration configuration = new Configuration();
  private final DriverInformation info =
      new DriverInformation(Keys.DRIVER_PID,
          URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
              + "master/mercury270/"
              + "src/main/resources/ru/semiot/drivers/mercury270/prototype.ttl#Mercury270"));
  private volatile DeviceDriverManager manager;
  private List<Configuration> configurations;
  private List<Integer> countsRepeatableProperties;
  private final Map<String, Device> devicesMap = Collections.synchronizedMap(new HashMap<>());
  private ScheduledExecutorService scheduler;
  private List<ScheduledFuture> handles = null;

  public void start() {
    logger.debug("{} is starting!", DRIVER_NAME);
    manager.registerDriver(info);
    handles = new ArrayList<>();
    this.scheduler = Executors.newScheduledThreadPool(countsRepeatableProperties.size());
    logger.debug("Try to start {} pullers", countsRepeatableProperties.size());
    for (Configuration cfg : configurations) {
      handles.add(startPuller(cfg));
    }
    logger.debug("All pullers started");
    logger.info("{} started!", DRIVER_NAME);
  }

  public ScheduledFuture startPuller(Configuration config) {
    logger.debug("Try to start puller!");
    logger.debug("Config is " + config.toString());
    ScheduledPuller puller = new ScheduledPuller(this, config);

    logger.debug("Try to schedule pulling. Starts with interval {} min with configuration [{}]",
        config.get(Keys.PULLING_INTERVAL), config.toString());

    ScheduledFuture handle = this.scheduler.scheduleAtFixedRate(puller, 0,
        config.getAsLong(Keys.PULLING_INTERVAL), TimeUnit.MINUTES);

    logger.debug("Puller started!");
    return handle;
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
    logger.info("{} stoped!", DRIVER_NAME);
  }

  public void stopPuller(ScheduledFuture handle) {
    logger.debug("Try to stop puller!");
    if (handle == null) {
      return;
    }
    handle.cancel(true);
    logger.debug("Puller stoped!");
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
            countsRepeatableProperties =
                getCountsRepeatableProperties(Keys.COAP_ENDPOINT, configuration);
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

  private List<Configuration> getConfigurations(List<Integer> counts)
      throws ConfigurationException {
    logger.debug("Try to get repeatable configuration for each puller");
    List<Configuration> conf = new ArrayList<>();
    for (int i : counts) {
      Configuration cfg = getRepeatableConfiguration(i, configuration);
      conf.add(cfg);
    }
    return conf;
  }

  private List<Integer> getCountsRepeatableProperties(String propPrefix, Configuration config)
      throws ConfigurationException {
    logger.debug("Try to get count of repeatable property \"{}\"", propPrefix);
    List<Integer> counts = new ArrayList<>();
    int index;

    for (String key : config.keySet()) {
      if (key.contains(propPrefix) && !counts
          .contains(index = Integer.parseInt(key.substring(0, key.indexOf("." + propPrefix))))) {
        counts.add(index);
      }
    }
    if (counts.isEmpty()) {
      logger.error("Bad repeatable configuration! Did not find a repeatable property '{}'",
          propPrefix);
      throw new ConfigurationException(propPrefix, "Did not find a repeatable property");
    }
    return counts;
  }

  private Configuration getRepeatableConfiguration(int count, Configuration cfg)
      throws ConfigurationException {
    String uri = cfg.getAsString(count + "." + Keys.COAP_ENDPOINT);
    String pulling_interval = cfg.getAsString(count + "." + Keys.PULLING_INTERVAL);
    if (uri == null || pulling_interval == null) {
      logger.error("Bad repeatable configuration! Some field is null! {} is {}, {} is {}",
          count + "." + Keys.COAP_ENDPOINT, uri, count + "." + Keys.PULLING_INTERVAL,
          pulling_interval);
      throw new ConfigurationException(
          (uri == null ? count + "." + Keys.COAP_ENDPOINT : "")
              + (pulling_interval == null ? count + "." + Keys.PULLING_INTERVAL : ""),
          "Some field is null");
    }
    if (uri.endsWith("/")) {
      uri = uri.substring(0, uri.length() - 1);
    }
    if (!ping(uri, 5000)) {
      logger.error("Bad repeatable configuration! Cannot connect with uri '{}'" + uri);
      throw new ConfigurationException(count + "." + Keys.COAP_ENDPOINT,
          "Cannot connect with uri " + uri);
    }
    Configuration config = new Configuration();
    config.put(Keys.COAP_ENDPOINT, uri);
    config.put(Keys.PULLING_INTERVAL, cfg.get(count + "." + Keys.PULLING_INTERVAL));

    return config;
  }

  public static boolean ping(String uri, int timeout) {
    try {
      Request request = new Request(null, CoAP.Type.CON);
      request.setToken(new byte[0]);
      request.setURI(uri);
      request.send().waitForResponse(timeout);
      if (request.isAcknowledged() && !request.isTimedOut()) {
        return true;
      }
    } catch (InterruptedException e) {
    }
    return false;
  }

  public void registerDevice(Device device) {
    devicesMap.put(device.getId(), device);
    manager.registerDevice(info, device);
  }

  public void publishNewObservation(MercuryObservation observation) {
    String deviceId = observation.getProperty(DeviceProperties.DEVICE_ID);
    manager.registerObservation(devicesMap.get(deviceId), observation);
  }
}
