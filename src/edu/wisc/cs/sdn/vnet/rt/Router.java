package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.*;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import java.util.Arrays;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	private static int RIP_PORT = 520;

	private static String RIP_MULTICAST_IP = "224.0.0.9";
	private static MACAddress RIP_BROADCAST_MAC;

	private static byte[] broadcast_addr = new byte[6];
	private Timer timer = new Timer("Unsolicited RIP responses");

	static {
		Arrays.fill(broadcast_addr, (byte) 0xFF);
		RIP_BROADCAST_MAC = new MACAddress(broadcast_addr);
	}

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile) {
		super(host, logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable() {
		return this.routeTable;
	}

	/**
	 * Load a new routing table from a file.
	 * 
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile) {
		if (!routeTable.load(routeTableFile, this)) {
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * 
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile) {
		if (!arpCache.load(arpCacheFile)) {
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * 
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface     the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface) {
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets */

		switch (etherPacket.getEtherType()) {
			case Ethernet.TYPE_IPv4:
				this.handleIpPacket(etherPacket, inIface);
				break;
			// Ignore all other packet types, for now
		}
		/********************************************************************/
	}

	private void sendRipPacket(int destIP, MACAddress destMACAddress, byte mode, Iface outIface) {
		// Create packet structure
		Ethernet etherPacket = new Ethernet();
		IPv4 ipPacket = new IPv4();
		UDP udpPacket = new UDP();
		RIPv2 ripPacket = new RIPv2();

		etherPacket.setDestinationMACAddress(destMACAddress.toBytes());
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);

		ipPacket.setSourceAddress(outIface.getIpAddress());
		ipPacket.setDestinationAddress(destIP);
		ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
		ipPacket.setTtl((byte) 2);

		udpPacket.setSourcePort((short) RIP_PORT);
		udpPacket.setDestinationPort((short) RIP_PORT);

		etherPacket.setPayload(ipPacket);
		ipPacket.setPayload(udpPacket);
		udpPacket.setPayload(ripPacket);
		ripPacket.setCommand(mode);

		if (mode == RIPv2.COMMAND_RESPONSE) { // Make rip response
			for (RouteEntry entry : routeTable.getEntries()) {
				RIPv2Entry ripEntry = new RIPv2Entry(entry.getDestinationAddress(), entry.getMaskAddress(),
						entry.getMetric());
				ripEntry.setNextHopAddress(entry.getGatewayAddress());
				ripPacket.addEntry(ripEntry);
			}
		}

		sendPacket(etherPacket, outIface);
	}

	private void sendRipPacket(String destIP, MACAddress destMACAddress, byte mode, Iface outIface) {
		// Create packet structure
		Ethernet etherPacket = new Ethernet();
		IPv4 ipPacket = new IPv4();
		UDP udpPacket = new UDP();
		RIPv2 ripPacket = new RIPv2();

		etherPacket.setDestinationMACAddress(destMACAddress.toBytes());
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		etherPacket.setEtherType(Ethernet.TYPE_IPv4);

		ipPacket.setSourceAddress(outIface.getIpAddress());
		ipPacket.setDestinationAddress(destIP);
		ipPacket.setProtocol(IPv4.PROTOCOL_UDP);

		udpPacket.setSourcePort((short) RIP_PORT);
		udpPacket.setDestinationPort((short) RIP_PORT);

		etherPacket.setPayload(ipPacket);
		ipPacket.setPayload(udpPacket);
		udpPacket.setPayload(ripPacket);
		ripPacket.setCommand(mode);

		if (mode == RIPv2.COMMAND_RESPONSE) { // Make rip response
			for (RouteEntry entry : routeTable.getEntries()) {
				RIPv2Entry ripEntry = new RIPv2Entry(entry.getDestinationAddress(), entry.getMaskAddress(),
						entry.getMetric());
				ripEntry.setNextHopAddress(entry.getGatewayAddress());
				ripPacket.addEntry(ripEntry);
			}
		}

		sendPacket(etherPacket, outIface);
	}

	private void broadcast(byte mode) {
		for (Iface iface : this.getInterfaces().values()) {
			sendRipPacket(RIP_MULTICAST_IP, RIP_BROADCAST_MAC, mode, iface);
		}
	}

	public void startRip() {
		// TODO: Review (basically setting up all the immediate neighbors)
		System.out.println("Router inferfaces are : " + this.getInterfaces().values());
		for (Iface iface : this.getInterfaces().values()) {
			int mask = iface.getSubnetMask();
			routeTable.insert(iface.getIpAddress() & mask, 0, mask, iface, 0);
			System.out.println("Adding to current route table");
		}
		System.out.println("-------------- FIRST ROUTE TABLE -------------- ");
		System.out.println(this.routeTable);
		broadcast(RIPv2.COMMAND_REQUEST);

		System.out.println("-------------- ROUTE TABLE AFTER INITIAL BROADCAST REQUEST -------------- ");
		System.out.println(this.routeTable);
		timer.scheduleAtFixedRate(new TimerTask() { // This will periodically send unsolicited response out
			@Override
			public void run() {
				System.out.println("------- 10 SECOND UNSOLICITED BROADCAST! SENT BELOW: ----- ");
				System.out.println(routeTable);
				broadcast(RIPv2.COMMAND_RESPONSE);
			}
		}, 10000, 10000);
	}

	private void updateTable(RIPv2 ripPacket, Iface inIface, int ripSenderIp) { // Handles RIP responses. No need to
		for (RIPv2Entry entry : ripPacket.getEntries()) {
			if (entry.getMetric() >= 16) {
				continue;
			}
			int new_metric = entry.getMetric() + 1;

			RouteEntry match = routeTable.find(entry.getAddress(), entry.getSubnetMask());
			int next_hop = (entry.getNextHopAddress() == 0) ? ripSenderIp : entry.getNextHopAddress();

			if (match == null) { // Then we need to add this to our route table
				routeTable.insert(entry.getAddress(), next_hop, entry.getSubnetMask(), inIface,
						new_metric);
				continue;
			}
			if (match.getMetric() > new_metric && match.getGatewayAddress() != 0) { // If my current metric is less.
				routeTable.update(entry.getAddress(), entry.getSubnetMask(), next_hop, inIface,
						new_metric);
				continue;
			}

			if (ripSenderIp == match.getGatewayAddress() && match.getInterface() == inIface) {
				routeTable.update(entry.getAddress(), entry.getSubnetMask(), next_hop, inIface,
						new_metric);
			}

		}
	}

	private void handleRipPacket(Ethernet etherPacket, Iface inIface) {
		// Make sure the input packet is an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		}

		// Get RIP packet
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		UDP udpPacket = (UDP) ipPacket.getPayload();
		RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();

		if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST) { // 1) Send our current route table in response to
																// specific request
			sendRipPacket(ipPacket.getSourceAddress(), etherPacket.getSourceMAC(), RIPv2.COMMAND_RESPONSE, inIface);

		} else if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE) { // Recieved a RIP response & update table
			updateTable(ripPacket, inIface, ipPacket.getSourceAddress());
			System.out.println("-------------- NEW ROUTE TABLE AFTER RESPONSE RECIEVED -------------- ");
			System.out.println(this.routeTable);
		} else {
			System.err.println("Invalid RIP command detected.");
			System.exit(1);
		}

	}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface) {
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		}

		// Get IP header
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum) {
			return;
		}

		// Check TTL
		ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
		if (0 == ipPacket.getTtl()) {
			return;
		}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
			UDP udp = (UDP) ipPacket.getPayload();
			if (udp.getDestinationPort() == RIP_PORT || udp.getSourcePort() == RIP_PORT) {
				this.handleRipPacket(etherPacket, inIface); // This is a RIP packet and should be handled here
				return;
			}
		}

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values()) {
			if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
				return;
			}
		}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface) {
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		}
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch) {
			return;
		}

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface) {
			return;
		}

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop) {
			nextHop = dstAddr;
		}

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry) {
			return;
		}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}

}
