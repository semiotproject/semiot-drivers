package ru.semiot.drivers.regulator.relay;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;
import ru.semiot.platform.deviceproxyservice.api.drivers.ControllableDeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriver;
import ru.semiot.platform.deviceproxyservice.api.drivers.DeviceDriverManager;

import java.util.Properties;

public class Activator extends DependencyActivatorBase {

  @Override
  public void init(BundleContext bc, DependencyManager manager) throws Exception {
    Properties properties = new Properties();
    properties.setProperty(Constants.SERVICE_PID, Keys.DRIVER_PID);

    manager.add(createComponent()
        .setInterface(new String[] {ControllableDeviceDriver.class.getName(),
            DeviceDriver.class.getName(), ManagedService.class.getName()}, properties)
        .setImplementation(DeviceDriverImpl.class)
        .add(createServiceDependency().setService(DeviceDriverManager.class).setRequired(true))
        .add(createConfigurationDependency().setPid(Keys.DRIVER_PID)));
  }

}
