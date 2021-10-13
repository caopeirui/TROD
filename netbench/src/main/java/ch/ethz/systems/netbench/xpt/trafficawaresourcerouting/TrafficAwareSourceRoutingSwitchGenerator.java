package ch.ethz.systems.netbench.xpt.trafficawaresourcerouting;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.config.GraphDetails;
import ch.ethz.systems.netbench.core.log.SimulationLogger;
import ch.ethz.systems.netbench.core.network.NetworkDevice;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.run.infrastructure.IntermediaryGenerator;
import ch.ethz.systems.netbench.core.run.infrastructure.NetworkDeviceGenerator;
import static ch.ethz.systems.netbench.core.Simulator.getConfiguration;


import java.util.Map;
import java.util.HashMap;
import java.io.*;

public class TrafficAwareSourceRoutingSwitchGenerator extends NetworkDeviceGenerator {

    private final int numNodes;
    
    private final IntermediaryGenerator intermediaryGenerator;

    protected HashMap<Integer, Integer> switchIDToBlockID; 

    public TrafficAwareSourceRoutingSwitchGenerator(IntermediaryGenerator intermediaryGenerator, int numNodes) {
        SimulationLogger.logInfo("Network device", "TRAFFIC_AWARE_SOURCE_ROUTING_SWITCH(numNodes=" + numNodes + ")");

        // Standard fields
        this.numNodes = numNodes;
        this.intermediaryGenerator = intermediaryGenerator;
        this.switchIDToBlockID = new HashMap<Integer, Integer>();
        String filename = Simulator.getConfiguration().getPropertyOrFail("switch_to_block_filename");
        this.readSwitchIDToCorrespondingBlockID(filename);
    }

    // Note : needs to be called in populateRoutingTables before routing tables are being populated
    private void readSwitchIDToCorrespondingBlockID(String filename) {
        try {
            File f = new File(filename);
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(filename));
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    // Split up by comma
                    String[] split = line.split(",");
                    // ignore any line with split length smaller than 2
                    if (split.length < 2 || line.charAt(0) == '#') {
                        continue;
                    }
                    // Retrieve source and destination graph device identifier
                    
                    int switchID = Integer.parseInt(split[0]);
                    int blockID = Integer.parseInt(split[1]);
                    this.switchIDToBlockID.put(switchID, blockID);
                }
                // Close stream
                br.close();
                System.out.println(" Done.");
            } 
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public NetworkDevice generate(int identifier) {
        return this.generate(identifier, null);
    }

    @Override
    public NetworkDevice generate(int identifier, TransportLayer transportLayer) {
        int blockID = -1;
        if (transportLayer == null) {
            blockID = switchIDToBlockID.get(identifier);
        } else {
            GraphDetails details = getConfiguration().getGraphDetails();
            int switchID = details.getTorIdOfServer(identifier);
            blockID = this.switchIDToBlockID.get(switchID);
        }
        return new TrafficAwareSourceRoutingSwitch(identifier, transportLayer, intermediaryGenerator.generate(identifier), blockID);
    }

}
