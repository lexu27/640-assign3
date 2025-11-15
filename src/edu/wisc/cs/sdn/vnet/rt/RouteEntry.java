package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An entry in a route table.
 * 
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteEntry {
	/** Destination IP address */
	private int destinationAddress;

	/** Gateway IP address */
	private int gatewayAddress;

	/** Subnet mask */
	private int maskAddress;

	/**
	 * Router interface out which packets should be sent to reach
	 * the destination or gateway
	 */
	private Iface iface;

	/* Hold the metric for the distance vector */
	private int metric;

	private Timer timer;
	private RouteTable routeTable; // Need this in order to remove from route table holding this entry

	/**
	 * Create a new route table entry.
	 * 
	 * @param destinationAddress destination IP address
	 * @param gatewayAddress     gateway IP address
	 * @param maskAddress        subnet mask
	 * @param iface              the router interface out which packets should
	 *                           be sent to reach the destination or gateway
	 */
	public RouteEntry(int destinationAddress, int gatewayAddress,
			int maskAddress, Iface iface, int metric) {
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.iface = iface;
		this.metric = metric;
	}

	/**
	 * Create a new route table entry.
	 * 
	 * @param destinationAddress destination IP address
	 * @param gatewayAddress     gateway IP address
	 * @param maskAddress        subnet mask
	 * @param iface              the router interface out which packets should
	 *                           be sent to reach the destination or gateway
	 */
	public RouteEntry(int destinationAddress, int gatewayAddress,
			int maskAddress, Iface iface) {
		this.destinationAddress = destinationAddress;
		this.gatewayAddress = gatewayAddress;
		this.maskAddress = maskAddress;
		this.iface = iface;
	}

	/**
	 * @return destination IP address
	 */
	public int getDestinationAddress() {
		return this.destinationAddress;
	}

	/**
	 * @return gateway IP address
	 */
	public int getGatewayAddress() {
		return this.gatewayAddress;
	}

	public void setGatewayAddress(int gatewayAddress) {
		this.gatewayAddress = gatewayAddress;
	}

	/**
	 * Literally just set the metric for the route entry
	 */
	public int getMetric() {
		return this.metric;
	}

	public void setTable(RouteTable routeTable) {
		this.routeTable = routeTable;
	}

	public RouteTable getTable() {
		return this.routeTable;
	}

	/**
	 * Literally just set the metric for the route entry
	 */
	public void setMetric(int metric) {
		this.metric = metric;
	}

	/**
	 * @return subnet mask
	 */
	public int getMaskAddress() {
		return this.maskAddress;
	}

	/**
	 * @return the router interface out which packets should be sent to
	 *         reach the destination or gateway
	 */
	public Iface getInterface() {
		return this.iface;
	}

	public void setInterface(Iface iface) {
		this.iface = iface;
	}

	public void refresh() {
		if (timer != null) {
			this.timer.cancel();
			this.timer.purge();
		}
		timer = new Timer();
		this.timer.schedule(new TimerTask() {
			@Override
			public void run() {
				routeTable.remove(destinationAddress, maskAddress);
				System.out.println("30 SECONDS PASSED! Removing stale RIP entries.");
				System.out.println("---------- ROUTE TABLE AFTER 30 SECOND CLEANUP ---------");
				System.out.println(routeTable);
			}
		}, (long) 30000);
	}

	public String toString() {
		return String.format("%s \t%s \t%s \t%s \t%d",
				IPv4.fromIPv4Address(this.destinationAddress),
				IPv4.fromIPv4Address(this.gatewayAddress),
				IPv4.fromIPv4Address(this.maskAddress),
				this.iface.getName(),
				this.metric);
	}
}
