package ru.semiot.drivers.temperature.simulator;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public abstract class Keys {

  public static final String DRIVER_PID = "ru.semiot.drivers.temperature-simulator";
  public static final String COAP_ENDPOINT = "ru.semiot.drivers.COAPEndpoint";
  public static final String COAP_CLIENT_PORT = "ru.semiot.drivers.COAPClientPort";

  public static final String SIMULATOR_DESCRIPTION_POSTFIX = "/desc";
  public static final String SIMULATOR_OBSERVATION_POSTFIX = "/obs";

  public static final String OBSERVATION_VALUE = "ru.semiot.drivers.temperature.simulator.observation.value";
  public static final String BUILDING_ID = "ru.semiot.drivers.temperature.simulator.building.id";
}
