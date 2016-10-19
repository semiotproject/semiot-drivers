package ru.semiot.drivers.regulator.relay;

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

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Dictionary;

public class DeviceDriverImpl implements ControllableDeviceDriver, ManagedService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceDriverImpl.class);
  private static final String PROTOTYPE_URI_PREFIX =
      "https://raw.githubusercontent.com/semiotproject/semiot-drivers/master/regulator-relay/"
          + "src/main/resources/ru/semiot/drivers/regulator/relay/prototype.ttl#";
  private static final String DRIVER_NAME = "Regulator Relay Driver";
  private Configuration commonConfiguration;
  private final Configuration configuration = new Configuration();
  private final DriverInformation info =
      new DriverInformation(Keys.DRIVER_PID, URI.create(PROTOTYPE_URI_PREFIX + "Regulator"));
  private static final String PROCESS_CHANGE = "relay";
  private static final String COMMAND_RELAY_START = "relay-startcommand"; // ???
  private static final String COMMAND_RELAY_STOP = "relay-stopcommand"; // ???
  private static RDFTemplate TEMPLATE_RELAY_START_COMMAND;
  private static RDFTemplate TEMPLATE_RELAY_STOP_COMMAND;
  private static final int FNV_32_INIT = 0x811c9dc5;
  private static final int FNV_32_PRIME = 0x01000193;

  static {
    try {
      TEMPLATE_RELAY_START_COMMAND = new RDFTemplate(COMMAND_RELAY_START, DeviceDriverImpl.class
          .getResourceAsStream("/ru/semiot/drivers/regulator/relay/relay-startcommand.ttl"));
      TEMPLATE_RELAY_STOP_COMMAND = new RDFTemplate(COMMAND_RELAY_STOP, DeviceDriverImpl.class
          .getResourceAsStream("/ru/semiot/drivers/regulator/relay/relay-stopcommand.ttl"));
    } catch (IOException ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  private Regulator regulator = null; // ?
  private volatile DeviceDriverManager manager;

  public void start() {
    logger.debug("{} is starting!", DRIVER_NAME);
    manager.registerDriver(info);

    String id = hash(Keys.DRIVER_PID, "1");
    regulator = new Regulator(id,
        commonConfiguration.getAsString(Keys.COAP_ENDPOINT) + Keys.COAP_RESOURCE_RELAY);
    manager.registerDevice(info, regulator);

    Command cmd = new Command(PROCESS_CHANGE, COMMAND_RELAY_START);
    cmd.add(Keys.PROCESS_ID, PROCESS_CHANGE);
    cmd.add(Keys.DEVICE_ID, regulator.getId());
    // cmd.add(Keys.PROCESS_CHANGE_REGULATOR_PRESSURE, regulators.get(_id).getPressure());
    
    
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      logger.info(e.getMessage(), e);
    }
    /* manager.registerCommand(regulator,
        new CommandResult(cmd, getRDFTemplate(COMMAND_RELAY_START), ZonedDateTime.now())); // поправить */

    logger.info("{} started!", DRIVER_NAME);
  }

  public void stop() {
    logger.debug("{} are stopping!", DRIVER_NAME);
    regulator.shutdown();
    logger.info("{} stopped!", DRIVER_NAME);
  }

  @Override
  public String getDriverName() {
    return DRIVER_NAME;
  }

  @Override
  public CommandResult executeCommand(Command command) throws CommandExecutionException {
    logger.debug("Command got! Try to execute it...");
    try {
      if (regulator.getId().equals(command.get(Keys.DEVICE_ID))) {
        // Regulator device = regulators.get(command.get(Keys.DEVICE_ID));
        String commandId = command.get(Keys.COMMAND_ID);
        String processId = command.get(Keys.PROCESS_ID);
        synchronized (regulator) {
          if (processId.equals(PROCESS_CHANGE)) {
            if (commandId.equals(COMMAND_RELAY_START)) {
              boolean oldState = regulator.getState();
              regulator.setState(true);
              logger.debug("[ID={}] Changed value! Old value: {}, new value {}, real value {}",
                  regulator.getId(), oldState, true, regulator.getState());
            } else if (commandId.equals(COMMAND_RELAY_STOP)) {
              boolean oldState = regulator.getState();
              regulator.setState(false);
              logger.debug("[ID={}] Changed value! Old value: {}, new value {}, real value {}",
                  regulator.getId(), oldState, false, regulator.getState());
            } else {
              throw CommandExecutionException.badCommand("Command [%s] is not supported!",
                  commandId);
            }
          } else {
            throw CommandExecutionException.badCommand("Process [%s] is not supported!", processId);
          }
        }

        CommandResult result =
            new CommandResult(command, getRDFTemplate(commandId), ZonedDateTime.now());
        manager.registerCommand(regulator, result);

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
      /* if (!new CoapClient(uri).ping()) {
        logger.error("Bad common configuration! Cannot connect with uri '{}'", uri);
        throw new ConfigurationException(Keys.COAP_ENDPOINT,
            "Bad common configuration. Cannot connect with uri " + uri);
      } */
      config.put(Keys.COAP_ENDPOINT, uri);
    } catch (Throwable ex) {
      logger.error("Bad common configuration! Can not extract fields");
      throw new ConfigurationException("Common property", "Can not extract fields", ex);
    }
    return config;
  }

  @Override
  public RDFTemplate getRDFTemplate(String id) {
    switch (id) {
      case COMMAND_RELAY_START:
        return TEMPLATE_RELAY_START_COMMAND;
      case COMMAND_RELAY_STOP:
        return TEMPLATE_RELAY_STOP_COMMAND;
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
