package ru.semiot.drivers.temperature.simulator;

import java.util.Properties;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;

/**
 *
 * @author Daniil Garayzuev <garayzuev@gmail.com>
 */
public class Activator extends DependencyActivatorBase {

  @Override
  public void init(BundleContext bc, DependencyManager manager) throws Exception {
    Properties properties = new Properties();
    properties.setProperty(Constants.SERVICE_PID, Keys.DRIVER_PID);

    manager.add(createComponent()
        .setInterface(new String[] {DeviceDriver.class.getName(), ManagedService.class.getName()},
            properties)
        .setImplementation(DeviceDriverImpl.class)
        .add(createServiceDependency().setService(DeviceDriverManager.class).setRequired(true))
        .add(createConfigurationDependency().setPid(Keys.DRIVER_PID)));
  }

}