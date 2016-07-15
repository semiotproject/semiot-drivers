package ru.semiot.drivers.temperature.simulator;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
import ru.semiot.platform.deviceproxyservice.api.drivers.Observation;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DeviceDriverImpl implements DeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String DRIVER_NAME = "Temperature Simulator Driver";
  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID,
      URI.create("https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
          + "master/temperature-simulator/"
          + "src/main/resources/ru/semiot/drivers/temperature/simulator/prototype.ttl#Mercury270"));
  private final Map<String, Device> devicesMap = Collections.synchronizedMap(new HashMap<>());
  private final ExecutorService executorService = Executors.newFixedThreadPool(3, r -> {
    Thread t = new Thread(r, "TemperatureSimulator");
    t.setDaemon(true);
    return t;
  });
  private volatile DeviceDriverManager manager;
  private Configuration commonConfiguration;
  private CoapClient client;
  private List<CoapObserveRelation> relations = new ArrayList<>();

  public void start() {
    try {
      logger.debug("{} is starting!", DRIVER_NAME);
      manager.registerDriver(info);

      executorService.submit(mainThread());

      logger.info("{} started!", DRIVER_NAME);
    } catch (Throwable ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  public void stop() {
    logger.debug("{} is stopping!", DRIVER_NAME);
    logger.debug("Try to shutdown CoapClient");
    try {
      for (CoapObserveRelation relation : relations) {
        relation.reactiveCancel();
      }
      //client.shutdown();
      devicesMap.clear();
      executorService.shutdown();
      try {
        executorService.awaitTermination(10, TimeUnit.SECONDS);
      } finally {
        executorService.shutdownNow();
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
    logger.info("{} stopped!", DRIVER_NAME);
  }

  private Runnable mainThread() {
    return () -> {
      try {
        logger.debug("Try to get descrioption of devices");
        client.setURI(commonConfiguration.getAsString(Keys.COAP_ENDPOINT));
        List<String> buildings = new ArrayList<>();
        Set<WebLink> discover = client.discover();
        for (WebLink link : discover) {
          if (link.getURI().matches("/\\d+" + Keys.SIMULATOR_DESCRIPTION_POSTFIX)) {
            buildings.add(link.getURI().substring(1, link.getURI().lastIndexOf('/')));
          }
        }

        logger.info("Found {} buildings!", buildings.size());

        int count = 0;
        for (String building : buildings) {
          try {
            client.setURI(commonConfiguration.getAsString(Keys.COAP_ENDPOINT) + "/" + building
                + Keys.SIMULATOR_DESCRIPTION_POSTFIX);
            CoapResponse response = client.get();
            if (response == null) {
              logger.error("[Building={}] Failed to get the description!", building);
            } else {
              String desq = response.getResponseText();
              if (desq == null) {
                logger.error(
                    "[Building={}] Couldn't get the description! Payload is null.", building);
              } else {
                List<TemperatureDevice> devices = DriverUtils.getDevices(new JSONObject(desq));
                for (Device dev : devices) {
                  registerDevice(dev);
                }
                logger.debug("[Building={}] {} devices were registered", building, devices.size());
                count += devices.size();
              }
            }
          } catch (JSONException ex) {
            logger.error(ex.getMessage(), ex);
          }
        }
        logger.debug("{} devices were registered", count);

        Thread.sleep(300000); // 5 seconds

        logger.debug("Subscribe for observations");
        for (String building : buildings) {
          client.setURI(
              commonConfiguration.getAsString(Keys.COAP_ENDPOINT)
                  + "/"
                  + building
                  + Keys.SIMULATOR_OBSERVATION_POSTFIX);
          relations.add(client.observeAndWait(new CoapHandler() {
            @Override
            public void onLoad(CoapResponse response) {
              try {
                if (response != null) {
                  DriverUtils.getAndPublishObservations(
                      new JSONArray(response.getResponseText()), manager, devicesMap);
                } else {
                  logger.error("[Building={}] Received null instead of observations", building);
                }
              } catch (Throwable ex) {
                logger.error(ex.getMessage(), ex);
              }
            }

            @Override
            public void onError() {
              logger.error("[Building={}] Can't get observation!", building);
            }
          }));
        }
      } catch (Throwable e) {
        logger.error(e.getMessage(), e);
      }
    };
  }

  public void registerDevice(Device device) {
    devicesMap.put(device.getId(), device);
    manager.registerDevice(info, device);
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
      client = new CoapClient(uri)
          .setExecutor(executorService);
      if (!client.ping()) {
        logger.error("Bad common configuration! Cannot connect with uri '{}'" + uri);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Cannot connect with uri " + uri);
      }
      config.put(Keys.COAP_ENDPOINT, uri);
    } catch (Throwable ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

}
