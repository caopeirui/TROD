package ch.ethz.systems.netbench.xpt.tcpbase;

import ch.ethz.systems.netbench.core.Simulator;
import ch.ethz.systems.netbench.core.log.LogFailureException;
import ch.ethz.systems.netbench.core.log.LoggerCallback;
import ch.ethz.systems.netbench.core.log.SimulationLogger;

import java.io.BufferedWriter;
import java.io.IOException;

public class TcpLogger implements LoggerCallback {

    private final long flowId;
    private long maxFlowlet;
    private long numberOfAckedPackets;
    private double smoothRTT;
    private final BufferedWriter congestionWindowWriter;
    private final BufferedWriter packetBurstGapWriter;
    private final BufferedWriter maxFlowletWriter;
    private final BufferedWriter smoothRTTWriter;
    private final BufferedWriter recordResend;
    private final boolean logPacketBurstGapEnabled;
    private final boolean logCongestionWindowEnabled;
    private final boolean logSmoothRTTEnabled;
    private final boolean isReceiver;
    private final boolean logRecordResendEnable;

    public TcpLogger(long flowId, boolean isReceiver) {
        this.flowId = flowId;
        this.maxFlowlet = 0;
        this.congestionWindowWriter = SimulationLogger.getExternalWriter("congestion_window.csv.log");
        this.packetBurstGapWriter = SimulationLogger.getExternalWriter("packet_burst_gap.csv.log");
        this.maxFlowletWriter = SimulationLogger.getExternalWriter("max_flowlet.csv.log");
        this.smoothRTTWriter = SimulationLogger.getExternalWriter("smoothed_rtt.csv.log");
        this.recordResend = SimulationLogger.getExternalWriter("record_resend.log");
        this.logPacketBurstGapEnabled = Simulator.getConfiguration().getBooleanPropertyWithDefault("enable_log_packet_burst_gap", false);
        this.logCongestionWindowEnabled = Simulator.getConfiguration().getBooleanPropertyWithDefault("enable_log_congestion_window", false);
        this.logSmoothRTTEnabled = Simulator.getConfiguration().getBooleanPropertyWithDefault("enable_smooth_rtt", false);
        this.logRecordResendEnable = Simulator.getConfiguration().getBooleanPropertyWithDefault("enable_record_resend", false);
        this.isReceiver = isReceiver;
        SimulationLogger.registerCallbackBeforeClose(this);
    }

    public void logRecordResend(int srcId, int dstId) {
        if (this.logRecordResendEnable) {
            try {
                this.recordResend.write(srcId + "->" + dstId + "\n");
            } catch (IOException e) {
                throw new LogFailureException(e);
            }
        }
    }


    /**
     * Log the congestion window of a specific flow at a certain point in time.
     *
     * @param congestionWindow      Current size of congestion window
     */
    public void logCongestionWindow(double congestionWindow) {
        if (logCongestionWindowEnabled) {
            try {
                congestionWindowWriter.write(flowId + "," + congestionWindow + "," + Simulator.getCurrentTime() + "\n");
            } catch (IOException e) {
                throw new LogFailureException(e);
            }
        }
    }

    /**
     * Log the maximum flowlet identifier observed acknowledged.
     *
     * @param flowlet   Flowlet identifier
     */
    public void logMaxFlowlet(long flowlet) {
        assert(flowlet >= maxFlowlet);
        maxFlowlet = flowlet;
    }

    /**
     * Log the packet burst gap (ns).
     *
     * @param gapNs Packet burst gap in nanoseconds
     */
    public void logPacketBurstGap(long gapNs) {
        try {
            if (logPacketBurstGapEnabled) {
                packetBurstGapWriter.write(gapNs + "\n");
            }
        } catch (IOException e) {
            throw new LogFailureException(e);
        }
    }

    public void logSmoothRTT(double smoothRTT) {
        this.smoothRTT = smoothRTT;
        ++this.numberOfAckedPackets;
    }

    @Override
    public void callBeforeClose() {
        try {
            if (!isReceiver) {
                maxFlowletWriter.write(flowId + "," + maxFlowlet + "\n");
            }
            if (logSmoothRTTEnabled & !isReceiver) {
                smoothRTTWriter.write(flowId + "," + this.smoothRTT + "," + this.numberOfAckedPackets + "\n");
            }
        } catch (IOException e) {
            throw new LogFailureException(e);
        }
    }

}
