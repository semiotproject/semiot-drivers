package ru.semiot.drivers.regulator.simulator;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public abstract class Keys extends DeviceProperties {

  public static final String DRIVER_PID = "ru.semiot.drivers.regulator-simulator";
  public static final String COAP_ENDPOINT = "ru.semiot.drivers.COAPEndpoint";
  public static final String COAP_RESOURCE = "/regulator";
  public static final String PROCESS_CHANGE_REGULATOR_PRESSURE = "ru.semiot.drivers.regulator.simulator.change.pressure";
  public static final String BUILDING_ID = "ru.semiot.drivers.regulator.simulator.building.id";
}
