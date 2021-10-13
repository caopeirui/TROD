package ch.ethz.systems.netbench.xpt.infiniband;

import ch.ethz.systems.netbench.xpt.infiniband.simpleIB.SimpleInfinibandSwitch;
import ch.ethz.systems.netbench.core.network.Socket;
import ch.ethz.systems.netbench.core.network.TransportLayer;
import ch.ethz.systems.netbench.core.network.Packet;

import java.util.*;

public class InfinibandTransportLayer extends TransportLayer {

	// Generator for unique flow identifiers amongst all transport layers
    private static long flowIdCounter = 0;
    private static Map<Long, Long> flowIdToFlowSizeBytes = new HashMap<>();
    private static Map<Long, Socket> flowIdToReceiverSockets = new HashMap<>();

    // Map the flow identifier to the responsible socket
    private Map<Long, Socket> flowIdToSocket;
    private Set<Long> finishedFlowIds;
    private List<Long> runningSenderFlowIds;  // Keeps a sorted list of sender sockets' flow Ids.
    private int currentSendingFlowIndex;		// Keeps track of the current index in the list of running flow indices

    private InfinibandSwitchInterface underlyingServer;

    // Flag for determining whether in-order packet delivery is required.
    private final boolean checkPacketOrder;
    /**
     * Constructor for Simple IB transport layer.
     *
     * @param identifier                The ID to which this transport layer belongs to.
     * @param checkDeliveryOrder        Flag for determining whether if the transport layer should check for packet delivery order
     */
    public InfinibandTransportLayer(int identifier, boolean checkDeliveryOrder) {
        super(identifier);
        this.runningSenderFlowIds = new LinkedList<Long>();
        this.flowIdToSocket = new HashMap<Long, Socket>();
        this.finishedFlowIds = new HashSet<Long>();
        this.currentSendingFlowIndex = -1;
        this.underlyingServer = null;
        this.checkPacketOrder = checkDeliveryOrder;
    }

    /**
     * Since infiniband routers require tight vertical integration between the transport layer and the network and link layers,
     * a routing decision must be made with the knowledge of network switch's queue information to apply backpressure. Therefore,
     * each transport layer must also have a direct reference to the underlying router.
     */
    public void setUnderlyingDevice(InfinibandSwitchInterface underlyingServer) {
    	if (this.underlyingServer != null) {
    		throw new IllegalStateException("The underlying device for transport layer: " + identifier + " has been set before!");
    	}
    	this.underlyingServer = underlyingServer;
    }

    /**
     * Key function: based on the backpressure from the underlying physical switch
     * push only enough packets that can be successfully queued onto the output port's
     * buffer, no more and no less.
     *
     * Called by the underlying network device (Infiniband Network Switch)
     * when the queue has freed up and can enter the queue. The transport layer will
     * pick one socket to send a packet.
     *
     * @return A packet arbitrated based on the available flows
     */
    public void arbitrateFlows(long remainingBufferSizeBits) {
    	int initialSenderFlowIdsNum = runningSenderFlowIds.size();
    	// Go to the next available flow to send a packet based on the remainingBufferSizeBits
    	if (initialSenderFlowIdsNum > 0) {
    		// Find the next flow to send. If the flow's socket generated packet is of enough size, then
    		long currentFlow = runningSenderFlowIds.get(currentSendingFlowIndex);
	    	InfinibandSocket ibSocket = (InfinibandSocket) flowIdToSocket.get(currentFlow);
	    	// Next, make sure that the socket can push only a packet that does not exceed remainingBufferSizeBits
	    	long consumedBufferSizeBits = ibSocket.tryToSend(remainingBufferSizeBits);
	    	// The current sending flow has been removed because it has been sent completely.
	    	if (consumedBufferSizeBits > 0 && initialSenderFlowIdsNum > runningSenderFlowIds.size()) {
	    		if (runningSenderFlowIds.size() > 0) {
	    			if (currentSendingFlowIndex == runningSenderFlowIds.size()) {
	    				currentSendingFlowIndex = 0;
	    			}
	    		} else {
	    			currentSendingFlowIndex = -1;
	    		}
	    	} else {
	    		currentSendingFlowIndex = (currentSendingFlowIndex + 1) % initialSenderFlowIdsNum; 
	    	}
	    	
	    	assert(remainingBufferSizeBits >= consumedBufferSizeBits);
    	}
    }

    /**
     * Start the sending of a flow to the destination.
     *
     * @param destination       Destination network device identifier
     * @param flowSizeByte      Byte size of the flow
     */
    @Override
    public void startFlow(int destination, long flowSizeByte) {

        // Create new sender socket
        Socket senderSocket = createSocket(flowIdCounter, destination, flowSizeByte, false);
        flowIdToSocket.put(flowIdCounter, senderSocket);
        runningSenderFlowIds.add(flowIdCounter);
        flowIdToFlowSizeBytes.put(flowIdCounter, flowSizeByte);
        

        // Create new receiver socket
        Socket receiverSocket = createSocket(flowIdCounter, destination, flowSizeByte, true);
        flowIdToReceiverSockets.put(flowIdCounter, receiverSocket);

        // Increment flow id counter for the next counter
        flowIdCounter++;
        if (runningSenderFlowIds.size() == 1) {
        	currentSendingFlowIndex = 0;
        }

        // Mark the snder socket off as initiator
        senderSocket.markAsSender();
        senderSocket.start();
    }

    /**
     * Reception of a packet from its network device (through the intermediary).
     *
     * @param genericPacket    Packet instance
     */
    @Override
    public void receive(Packet genericPacket) {
        InfinibandPacket packet = (InfinibandPacket) genericPacket;
        Socket socket = flowIdToSocket.get(packet.getFlowId());

        // If the socket does not yet exist, it is an incoming socket, so create it on the receiver side.
        if (socket == null && !finishedFlowIds.contains(packet.getFlowId())) {
            socket = flowIdToReceiverSockets.get(genericPacket.getFlowId());
            flowIdToSocket.put(packet.getFlowId(), socket);
            //flowIdToReceiverSockets.remove(genericPacket.getFlowId());
        }

        // Give packet to socket (we do not care about stray packets)
        if (socket != null) {
            socket.handle(packet);
        }
    }

    /**
     * Clean up the socket references of a specific flow identifier (also overreaches
     * to the receiver).
     *
     * @param flowId    Flow identifier
     */
    public void cleanupSockets(long flowId, boolean isReceiver) {
        this.finishedFlowIds.add(flowId);
        this.flowIdToSocket.remove(flowId);
        // check current flow id that the running flow index is pointing towards
        if (isReceiver) {
        	flowIdToFlowSizeBytes.remove(flowId);
        } else {
        	this.runningSenderFlowIds.remove(flowId);
    	}
    }

    /**
     * Creates a socket used to control sending of packets, takes in an additional boolean flag to devide whether flowLogger 
     * should be initialized or not.
     */
    protected Socket createSocket(long flowId, int destinationId, long flowSizeByte, boolean flowLogger) {
        return new InfinibandSocket(this, underlyingServer, flowId, identifier, destinationId, flowSizeByte, flowLogger, checkPacketOrder);
    }

    /**
     * Creates a socket used to control sending of packets
     */
    @Override
    protected Socket createSocket(long flowId, int destinationId, long flowSizeByte) {
        return new InfinibandSocket(this, underlyingServer, flowId, identifier, destinationId, flowSizeByte, true, checkPacketOrder);
    }
}
