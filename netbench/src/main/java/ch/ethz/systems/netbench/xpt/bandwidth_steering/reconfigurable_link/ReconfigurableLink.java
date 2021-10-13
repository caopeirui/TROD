package ch.ethz.systems.netbench.xpt.bandwidth_steering.reconfigurable_link;

import ch.ethz.systems.netbench.core.network.Link;

public class ReconfigurableLink extends Link implements ReconfigurableLinkInterface {

    private final long delayNs;
    private long multiplicity;
    private final long bandwidthBitPerNs;
    private long effectiveBandwidthBitPerNs;   // = multiplicity * bandwidthBitPerNs
    /**
     * Perfect simple link but with reconfigurable bandwidth to emulate the 
     * bandwidth steering links.
     *
     * @param delayNs               Delay of each packet in nanoseconds
     * @param bandwidthBitPerNs     Bandwidth of the link (maximum line rate) in bits/ns
     */
    ReconfigurableLink(long delayNs, long bandwidthBitPerNs, long multiplicity) {
        this.delayNs = delayNs;
        this.bandwidthBitPerNs = bandwidthBitPerNs;
        this.multiplicity = multiplicity;
        this.effectiveBandwidthBitPerNs = multiplicity * bandwidthBitPerNs;
    }

    /**
     * Sets the multiplicity of the current link.
     */
    @Override
    public void setMultiplicity(long multiplicity) {
        assert(multiplicity >= 0);
        this.multiplicity = multiplicity;
        this.effectiveBandwidthBitPerNs = this.multiplicity * this.bandwidthBitPerNs;
    }

    /**
     * Gets the multiplicity of the current link.
     */
    @Override
    public long getMultiplicity() {
        return this.multiplicity;
    }

    @Override
    public long getBandwidthBitPerNs() {
        //System.out.println("bandwidth is " + effectiveBandwidthBitPerNs);
        return this.effectiveBandwidthBitPerNs;
    }

    @Override
    public long getDelayNs() {
        return delayNs;
    }

    @Override
    public boolean doesNextTransmissionFail(long packetSizeBits) {
        return false;
    }
}
