package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.*;
import java.util.concurrent.*;

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

	/** RIP UDP port */
	private static final short RIP_PORT = 520;

	/** RIP multicast IP 224.0.0.9 */
	private static final int RIP_MCAST_IP = IPv4.toIPv4Address("224.0.0.9");

	/** RIP periodic unsolicited response interval (ms) */
	private static final int RIP_PERIODIC_MS = 10_000;

	/** RIP route timeout to expire learned entries (ms) */
	private static final int RIP_TIMEOUT_MS = 30_000;

	/** Scheduler for periodic tasks */
	private ScheduledExecutorService ripScheduler = null;

	/** Track directly-connected subnets (never expire) */
	private final Map<String, RipRecord> connected = new ConcurrentHashMap<>();

	/** Track RIP-learned routes (subject to timeout) */
	private final Map<String, RipRecord> learned = new ConcurrentHashMap<>();

	/** Track last-touched time of learned routes */
	private final Map<String, Long> touched = new ConcurrentHashMap<>();

	private static class RipRecord {
		final int prefix;
		final int mask;
		final Iface iface;
		final int nextHop;
		int metric;// 0..16 (16 = unreachable)

		RipRecord(int prefix, int mask, Iface iface, int nextHop, int metric) {
			this.prefix = prefix;
			this.mask = mask;
			this.iface = iface;
			this.nextHop = nextHop;
			this.metric = metric;
		}
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

				IPv4 ipPacket = (IPv4) etherPacket.getPayload();

				if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
					UDP udp = (UDP) ipPacket.getPayload();
					if (udp.getDestinationPort() == RIP_PORT || udp.getSourcePort() == RIP_PORT) {
						this.handleRipPacket(etherPacket, inIface);
						return;
					}
				}

				this.handleIpPacket(etherPacket, inIface);
				break;
			// Ignore all other packet types, for now
		}
		/********************************************************************/
	}

	private void sendRipResponse(String destIP, MACAddress destMACAddress) {

	}

	private void handleRipPacket(Ethernet etherPacket, Iface inIface) {
		// Make sure the input packet is an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		}

		// Get RIP packet
		RIPv2 ripPacket = (RIPv2) etherPacket.getPayload();
		byte[] destMacAddress = new byte[6];
		Arrays.fill(destMacAddress, (byte) 0xFF);

		if (ripPacket.getCommand() == ripPacket.COMMAND_REQUEST) { // Send our current route table
			sendRipResponse("224.0.0.9", new MACAddress(destMacAddress));
		} else if (ripPacket.getCommand() == ripPacket.COMMAND_RESPONSE) { // Recieved a RIP response

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

	public void startRip() {

		// Add all directly-connected subnets into the route table and announce set
		for (Iface iface : this.interfaces.values()) {
			int ip = iface.getIpAddress();
			int mask = iface.getSubnetMask();
			int subnet = ip & mask;

			this.routeTable.insert(subnet, 0, mask, iface);

			RipRecord rec = new RipRecord(subnet, mask, iface, 0, 0);
			connected.put((subnet & mask) + "/" + mask, rec);
		}

		// send a RIP request out of all interfaces
		for (Iface iface : this.interfaces.values()) {
			Ethernet eth = buildRipBase(iface, RIP_MCAST_IP, null);
			RIPv2 rip = new RIPv2();
			rip.setCommand(RIPv2.COMMAND_REQUEST);
			UDP udp = (UDP) ((IPv4) eth.getPayload()).getPayload();
			udp.setPayload(rip);
			udp.resetChecksum();
			IPv4 ip = (IPv4) eth.getPayload();
			ip.resetChecksum();
			sendPacket(eth, iface);
		}

		// start periodic unsolicited responses

		// start expirer

	}
}
