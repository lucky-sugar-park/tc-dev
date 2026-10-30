import com.sun.tools.attach.VirtualMachine;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/** Ask a local Spring Boot app (spring.application.admin.enabled=true) to shut down gracefully, like SIGTERM. */
public class ShutdownViaJmx {
	public static void main(String[] args) throws Exception {
		VirtualMachine vm = VirtualMachine.attach(args[0]);
		String address = vm.startLocalManagementAgent();
		vm.detach();
		try (JMXConnector c = JMXConnectorFactory.connect(new JMXServiceURL(address))) {
			MBeanServerConnection m = c.getMBeanServerConnection();
			m.invoke(new ObjectName("org.springframework.boot:type=Admin,name=SpringApplication"), "shutdown", null, null);
		}
		System.out.println("shutdown requested: " + args[0]);
	}
}
