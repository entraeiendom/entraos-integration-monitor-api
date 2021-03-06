package io.entraos.monitor;

import io.entraos.monitor.alerting.Alerter;
import io.entraos.monitor.logon.LogonMonitor;
import io.entraos.monitor.scheduler.ScheduledMonitorManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Properties;

import static no.cantara.config.ServiceConfig.getProperty;
import static org.slf4j.LoggerFactory.getLogger;

@Singleton
@Service
public class MonitorService {
    private static final Logger log = getLogger(MonitorService.class);
    private static final String SERVICE_TYPE = "A2A"; //https://wiki.cantara.no/display/OWSOA/A2A
    private final String environment;
    private final String serviceName;
    private final Instant startedAt = Instant.now();
    private final ScheduledMonitorManager scheduler;
    private Instant lastSuccessfulLogon = null;
    private Instant lastFailedLogon = null;
    private Status status = Status.NOT_RUN_YET;


    @Autowired
    public MonitorService(Alerter... alerters) {
        this(Configuration.getString("environment"), getProperty("service_name"), alerters);

    }
    public MonitorService(String environment, String serviceName, Alerter... alerters) {
        this.environment = environment;
        this.serviceName = serviceName;
        URI logonUri = URI.create(getProperty("logon_uri"));
        log.warn("Creating MonitorService for url: {}", logonUri);
        LogonMonitor logonMonitor = new LogonMonitor(logonUri);
        scheduler = new ScheduledMonitorManager(logonMonitor, alerters);
        scheduler.startScheduledMonitor();
    }

    MonitorService(String environment, String serviceName, ScheduledMonitorManager scheduledMonitorManager) {
        this.environment = environment;
        this.serviceName = serviceName;
        this.scheduler = scheduledMonitorManager;
    }

    public void setLastSuccessfulLogon(Instant lastSuccessfulLogon) {
        this.lastSuccessfulLogon = lastSuccessfulLogon;
    }

    public void setLastFailedLogon(Instant lastFailedLogon) {
        this.lastFailedLogon = lastFailedLogon;
    }

    public String toJson() {
        updateStatus();
        updateLastSuccessful();
        updateLastFailed();
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                .add("environment", environment)
                .add("name", serviceName)
                .add("serviceType", SERVICE_TYPE)
                .add("ip", getMyIPAddresssString())
                .add("version", getVersion())
                .add("now", Instant.now().toString())
                .add("startedAt", startedAt.toString());
        if (lastSuccessfulLogon != null) {
            jsonBuilder.add("lastSuccessfullLogon", lastSuccessfulLogon.toString());
        }
        if (lastFailedLogon != null) {
            jsonBuilder.add("lastFailedLogon", lastFailedLogon.toString());
        }
        if (status != null) {
            jsonBuilder.add("status", status.toString());
        } else {
            jsonBuilder.add("status", "unknown");
        }
        return jsonBuilder.build().toString();
    }

    public boolean isStatusOk() {
        updateStatus();
        updateLastSuccessful();
        updateLastFailed();
        boolean statusOk = false;
        if (status != null && status == Status.OK) {
            statusOk = true;
        }
        return statusOk;
    }

    void updateStatus() {
        Status status = scheduler.getStatus();
        this.status = status;
    }
    void updateLastSuccessful() {
        Instant lastSuccessfulConnect = scheduler.getLastSuccessfulConnect();
        this.lastSuccessfulLogon = lastSuccessfulConnect;
    }

    void updateLastFailed() {
        Instant lastFailedConnect = scheduler.getLastFailedConnect();
        this.lastFailedLogon = lastFailedConnect;
    }

    protected String getVersion() {
        Properties mavenProperties = new Properties();
        String resourcePath = "/META-INF/maven/io.entraos.monitor/integration-monitor/pom.properties";

        URL mavenVersionResource = MonitorService.class.getResource(resourcePath);
        if (mavenVersionResource != null) {
            try {
                mavenProperties.load(mavenVersionResource.openStream());
                return mavenProperties.getProperty("version", "missing version info in " + resourcePath);
            } catch (IOException e) {
                log.warn("Problem reading version resource from classpath: ", e);
            }
        }
        return "(DEV VERSION)" + " [" + serviceName + " - " + getMyIPAddresssesString() + "]";
    }

    public static String getMyIPAddresssesString() {
        String ipAdresses = "";

        try {
            ipAdresses = InetAddress.getLocalHost().getHostAddress();
            Enumeration n = NetworkInterface.getNetworkInterfaces();

            while (n.hasMoreElements()) {
                NetworkInterface e = (NetworkInterface) n.nextElement();

                InetAddress addr;
                for (Enumeration a = e.getInetAddresses(); a.hasMoreElements(); ipAdresses = ipAdresses + "  " + addr.getHostAddress()) {
                    addr = (InetAddress) a.nextElement();
                }
            }
        } catch (Exception e) {
            ipAdresses = "Not resolved";
        }

        return ipAdresses;
    }

    public static String getMyIPAddresssString() {
        String fullString = getMyIPAddresssesString();
        return fullString.substring(0, fullString.indexOf(" "));
    }

    @Override
    public String toString() {
        return "MonitorService{" +
                "environment='" + environment + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", serviceType='" + SERVICE_TYPE + '\'' +
                ", startedAt=" + startedAt +
                ", lastSuccessfullLogon=" + lastSuccessfulLogon +
                ", lastFailedLogon=" + lastFailedLogon +
                '}';
    }
}
