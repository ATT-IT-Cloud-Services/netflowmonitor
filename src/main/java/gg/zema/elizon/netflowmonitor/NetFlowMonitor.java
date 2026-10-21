package gg.zema.elizon.netflowmonitor;

import gg.zema.elizon.netflowmonitor.config.MonitorConfig;
import gg.zema.elizon.netflowmonitor.utilities.Collector;

import java.nio.file.Path;

public class NetFlowMonitor {

    static void main() {
        Path configPath = Path.of("config", "netflowmonitor.config.json");
        MonitorConfig config = MonitorConfig.loadOrCreate(configPath);

        Collector collector = new Collector(config);

        collector.startCollecting();
    }

}
