package ru.semiot.platform.drivers.narodmon.temperature;

import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceProperties;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class Keys extends DeviceProperties {

  public static final String DRIVER_PID
      = "ru.semiot.platform.drivers.narodmon-temperature";
  public static final String PREFIX = "ru.semiot";

  public static final String LATITUDE = "ru.semiot.drivers.narodmon.latitude";
  public static final String LONGITUDE = "ru.semiot.drivers.narodmon.langitude";
  public static final String OBSERVATION_VALUE = "ru.semiot.drivers.narodmon.observation.value";
  public static final String OBSERVATION_TYPE = "ru.semiot.drivers.narodmon.observation.type";
  public static final String POLLING_INTERVAL = PREFIX + ".pollingInterval";
  public static final String API_KEY = PREFIX + ".clientAppID";
  public static final String UUID = PREFIX + ".uuid";
  public static final String RADIUS = PREFIX + ".radius";
  public static final String LAT = PREFIX + ".latitude";
  public static final String LONG = PREFIX + ".longitude";
  public static final String AREA = PREFIX + ".area";
}
