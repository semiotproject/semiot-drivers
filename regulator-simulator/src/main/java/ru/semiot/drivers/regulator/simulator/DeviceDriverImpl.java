package ru.semiot.drivers.regulator.simulator;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.semiot.platform.deviceproxyservice.api.drivers.Command;
import ru.semiot.platform.deviceproxyservice.api.drivers.CommandExecutionException;
import ru.semiot.platform.deviceproxyservice.api.drivers.CommandResult;
import ru.semiot.platform.deviceproxyservice.api.drivers.Configuration;
import ru.semiot.platform.deviceproxyservice.api.drivers.ControllableDeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;
import ru.semiot.platform.deviceproxyservice.api.drivers.DriverInformation;
import ru.semiot.platform.deviceproxyservice.api.drivers.RDFTemplate;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class DeviceDriverImpl implements ControllableDeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String PROTOTYPE_URI_PREFIX
      = "https://raw.githubusercontent.com/semiotproject/semiot-drivers/"
      + "master/regulator-simulator/"
      + "src/main/resources/ru/semiot/drivers/regulator/simulator/prototype.ttl#";
  private static final String DRIVER_NAME = "Regulator Simulator Driver";
  private Configuration commonConfiguration;
  private final Configuration configuration = new Configuration();
  private final DriverInformation info
      = new DriverInformation(Keys.DRIVER_PID, URI.create(PROTOTYPE_URI_PREFIX + "Regulator"));
  private static final String PROCESS_CHANGE = "pressure";
  private static final String COMMAND_CHANGE_VALUE = "change-regulator_value";
  private static RDFTemplate TEMPLATE_COMMAND_CHANGE_VALUE;
  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  static {
    try {
      TEMPLATE_COMMAND_CHANGE_VALUE = new RDFTemplate(COMMAND_CHANGE_VALUE,
          DeviceDriverImpl.class.getResourceAsStream(
              "/ru/semiot/drivers/regulator/simulator/change-regulator_value.ttl"));
    } catch (IOException ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  private Map<String, Regulator> regulators = new HashMap<>();
  private volatile DeviceDriverManager manager;

  public void start() {
    logger.debug("{} is starting!", DRIVER_NAME);
    manager.registerDriver(info);

    Set<WebLink> discover = new CoapClient(commonConfiguration.getAsString(Keys.COAP_ENDPOINT))
        .discover();
    String building;
    String uriPrefix = commonConfiguration.getAsString(Keys.COAP_ENDPOINT) + "/{NUM}" + Keys.COAP_RESOURCE;
    String id;
    for (WebLink link : discover) {
      logger.debug("Link is {}", link.getURI());
      if (link.getURI().matches("/\\d+/reg")) {
        building = link.getURI().substring(1, link.getURI().lastIndexOf('/'));
        id = hash(Keys.DRIVER_PID, building);
        Regulator reg = new Regulator(id, uriPrefix.replace("{NUM}", building), building);
        regulators.put(id, reg);
        manager.registerDevice(info, reg);

      }
    }

    for (String _id : regulators.keySet()) {
      Command cmd = new Command(PROCESS_CHANGE, COMMAND_CHANGE_VALUE);
      cmd.add(Keys.PROCESS_ID, PROCESS_CHANGE);
      cmd.add(Keys.DEVICE_ID, regulators.get(_id).getId());
      cmd.add(Keys.PROCESS_CHANGE_REGULATOR_PRESSURE, regulators.get(_id).getPressure());
      manager.registerCommand(regulators.get(_id), new CommandResult(
          cmd,
          getRDFTemplate(COMMAND_CHANGE_VALUE),
          ZonedDateTime.now()));
    }
    logger.info("{} started!", DRIVER_NAME);
  }

  public void stop() {
    logger.debug("{} are stopping!", DRIVER_NAME);
    for (Regulator reg : regulators.values()) {
      reg.shutdown();
    }
    regulators.clear();
    logger.info("{} stopped!", DRIVER_NAME);
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
      int clientPort = cfg.getAsInteger(Keys.COAP_CLIENT_PORT);
      if (uri == null) {
        logger.error("Bad common configuration! Field '{}' is null!", Keys.COAP_ENDPOINT);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Field is null");
      }
      if (uri.endsWith("/")) {
        uri = uri.substring(0, uri.length() - 1);
      }
      EndpointManager.getEndpointManager().setDefaultEndpoint(new CoapEndpoint(clientPort));
      if (!new CoapClient(uri).ping()) {
        logger.error("Bad repeatable configuration! Cannot connect with uri '{}'", uri);
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

  @Override
  public CommandResult executeCommand(Command command) throws CommandExecutionException {
    logger.debug("Command got! Try to execute it...");
    try {
      if (regulators.containsKey(command.get(Keys.DEVICE_ID))) {
        Regulator device = regulators.get(command.get(Keys.DEVICE_ID));
        String commandId = command.get(Keys.COMMAND_ID);
        String processId = command.get(Keys.PROCESS_ID);
        synchronized (device) {
          if (processId.equals(PROCESS_CHANGE)) {
            if (commandId.equals(COMMAND_CHANGE_VALUE)) {
              double value = Double.parseDouble(command.get(
                  Keys.PROCESS_CHANGE_REGULATOR_PRESSURE));
              double old_value = device.getPressure();
              device.setPressure(value);
              logger.debug("[ID={}] Changed value! Old value: {}, new value {}, real value {}",
                  device.getId(), old_value, value, device.getPressure());
            } else {
              throw CommandExecutionException.badCommand(
                  "Command [%s] is not supported!", commandId);
            }
          } else {
            throw CommandExecutionException.badCommand("Process [%s] is not supported!", processId);
          }
        }

        CommandResult result = new CommandResult(
            command, getRDFTemplate(commandId), ZonedDateTime.now());
        manager.registerCommand(device, result);

        return result;
      } else {
        throw CommandExecutionException.systemNotFound();
      }
    } catch (Throwable e) {
      if (e instanceof CommandExecutionException) {
        throw e;
      } else {
        throw CommandExecutionException.badCommand(e);
      }
    }
  }

  @Override
  public RDFTemplate getRDFTemplate(String id) {
    switch (id) {
      case COMMAND_CHANGE_VALUE:
        return TEMPLATE_COMMAND_CHANGE_VALUE;
      default:
        throw new IllegalArgumentException();
    }
  }

  private static String hash(String prefix, String id) {
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
