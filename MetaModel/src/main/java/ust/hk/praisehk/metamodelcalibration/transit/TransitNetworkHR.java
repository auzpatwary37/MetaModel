package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicles;

import dynamicTransitRouter.TransitStop;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;

public class TransitNetworkHR implements Network{

	private final static Logger log = Logger.getLogger(TransitNetworkHR.class);

	private final Map<Id<Link>, TransitRouterNetworkLink> links = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();
	private final Map<Id<Node>, TransitRouterNetworkNode> nodes = new LinkedHashMap<Id<Node>, TransitRouterNetworkNode>();
	
	private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, Map<TransitStop, TransitWaitLink>> waitLinks = new LinkedHashMap<>();
	private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, List<TransitTravelLink>> innerLinks = new LinkedHashMap<>();
	
	protected QuadTree<TransitRouterNetworkNode> qtNodes = null;

	private long nextNodeId = 0;
	protected long nextLinkId = 0;

	public static final class TransitRouterNetworkNode implements Node {

		public final TransitRouteStop stop;
		public final TransitStop tStop;
		public final TransitRoute route;
		public final TransitLine line;
		public final Coord coord;
		final Id<Node> id;
		final Map<Id<Link>, TransitRouterNetworkLink> ingoingLinks = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();
		final Map<Id<Link>, TransitRouterNetworkLink> outgoingLinks = new LinkedHashMap<Id<Link>, TransitRouterNetworkLink>();

		public TransitRouterNetworkNode(final Id<Node> id, final TransitRouteStop stop, final TransitStop tStop,
				final TransitRoute route, final TransitLine line) {
			this.id = id;
			this.stop = stop;
			this.route = route;
			this.line = line;
			this.tStop = tStop;
			this.coord = stop.getStopFacility().getCoord();
		}

		@Override
		public Map<Id<Link>, ? extends Link> getInLinks() {
			return this.ingoingLinks;
		}

		@Override
		public Map<Id<Link>, ? extends Link> getOutLinks() {
			return this.outgoingLinks;
		}

		@Override
		public boolean addInLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addOutLink(final Link link) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Coord getCoord() {
			return this.stop.getStopFacility().getCoord();
		}

		@Override
		public Id<Node> getId() {
			return this.id;
		}

		public TransitRouteStop getStop() {
			return stop;
		}

		public TransitRoute getRoute() {
			return route;
		}

		public TransitLine getLine() {
			return line;
		}

		@Override
		public Link removeInLink(Id<Link> linkId) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public Link removeOutLink(Id<Link> outLinkId) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public void setCoord(Coord coord) {
			// TODO Auto-generated method stub
			throw new RuntimeException("not implemented");
		}

		@Override
		public Attributes getAttributes() {
			throw new UnsupportedOperationException();
		}
	}



	public TransitRouterNetworkNode createNode(final TransitRouteStop stop, final TransitRoute route,
			final TransitLine line, final TransitStop tStop) {
		Id<Node> id = null;
		if (line == null && route == null)
			id = Id.createNodeId(stop.getStopFacility().getId().toString());
		else
			id = Id.createNodeId("number:" + nextNodeId++);
		final TransitRouterNetworkNode node = new TransitRouterNetworkNode(id, stop, tStop, route, line);
		if (this.nodes.get(node.getId()) != null)
			throw new RuntimeException();
		this.nodes.put(node.getId(), node);
		return node;
	}

	/**
	 * This function creates a transfer link
	 * @param network
	 * @param fromNode
	 * @param toNode
	 * @return
	 */
	public TransitRouterNetworkLink createTransferLink(final Network network, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode) {
		final TransitRouterNetworkLink link = new TransitRouterNetworkLink(Id.createLinkId(this.nextLinkId++), fromNode,
				toNode, null, null, network);
		this.links.put(link.getId(), link);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}
	
	/**
	 * This function creates a transfer link
	 * @param network
	 * @param fromNode
	 * @param toNode
	 * @return
	 */
	public TransitRouterNetworkLink createWaitingLink(final Network network, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode, Tuple<Double, Double> timeBin, Vehicles transitVehicles) {
		final TransitWaitLink link = new TransitWaitLink(Id.createLinkId(this.nextLinkId++), fromNode,
				toNode, network, timeBin, transitVehicles);
		this.links.put(link.getId(), link);
		Tuple<Id<TransitLine>, Id<TransitRoute>> lineRouteTuple = new Tuple<>(toNode.getLine().getId(), toNode.getRoute().getId());
		Map<TransitStop, TransitWaitLink> lineWaitLinks = this.waitLinks.get(lineRouteTuple);
		if(lineWaitLinks == null) { lineWaitLinks = new HashMap<>(); }
		lineWaitLinks.put(toNode.tStop, link);
		this.waitLinks.put(lineRouteTuple, lineWaitLinks);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}
	
	public TransitWaitLink getWaitingLink(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop transitStop) {
		return this.waitLinks.get(new Tuple<>(transitLineId, transitRouteId)).get(transitStop);
	}

	public TransitRouterNetworkLink createTravelLink(final Network network, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line,
			Tuple<Double, Double> timeBin, Vehicles transitVehicles) {
		final TransitTravelLink link = new TransitTravelLink(Id.createLinkId(this.nextLinkId++), fromNode,
				toNode, route, line, network, timeBin, transitVehicles);
		this.links.put(link.getId(), link);
		Tuple<Id<TransitLine>, Id<TransitRoute>> lineRouteTuple = new Tuple<>(toNode.getLine().getId(), toNode.getRoute().getId());
		List<TransitTravelLink> lineTravelLinks = this.innerLinks.get(lineRouteTuple);
		if(lineTravelLinks == null) { lineTravelLinks = new ArrayList<>(); }
		lineTravelLinks.add(link);
		this.innerLinks.put(lineRouteTuple, lineTravelLinks);
		fromNode.outgoingLinks.put(link.getId(), link);
		toNode.ingoingLinks.put(link.getId(), link);
		return link;
	}
	
	public TransitTravelLink getTravelLink(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop fromTransitStop, TransitStop toTransitStop) {
		List<TransitTravelLink> travelLinksForRoute = this.innerLinks.get(new Tuple<>(transitLineId, transitRouteId));
		for(TransitTravelLink travelLink: travelLinksForRoute) {
			if( (fromTransitStop==null || travelLink.fromNode.tStop.equals(fromTransitStop)) && 
					travelLink.toNode.tStop.equals(toTransitStop)) {
				return travelLink; //Find the link with the same transitStop, or no from transitStop.
			}
		}
		throw new IllegalArgumentException("Travel link not found!");
	}
	
	/**
	 * A convenient function to get the link by transitLink id.
	 * @param transitLinkId
	 * @return
	 */
	public TransitRouterNetworkLink getLink(Id<TransitLink> transitLinkId) {
		return links.get(Id.createLinkId(transitLinkId));
	}

	@Override
	public Map<Id<Node>, TransitRouterNetworkNode> getNodes() {
		return this.nodes;
	}

	@Override
	public Map<Id<Link>, TransitRouterNetworkLink> getLinks() {
		return this.links;
	}

	public void finishInit() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (TransitRouterNetworkNode node : getNodes().values())
			if (node.line == null) {
				Coord c = node.stop.getStopFacility().getCoord();
				if (c.getX() < minX)
					minX = c.getX();
				if (c.getY() < minY)
					minY = c.getY();
				if (c.getX() > maxX)
					maxX = c.getX();
				if (c.getY() > maxY)
					maxY = c.getY();
			}
		QuadTree<TransitRouterNetworkNode> quadTree = new QuadTree<TransitRouterNetworkNode>(minX, minY, maxX, maxY);
		for (TransitRouterNetworkNode node : getNodes().values()) {
			if (node.line == null) {
				Coord c = node.stop.getStopFacility().getCoord();
				quadTree.put(c.getX(), c.getY(), node);
			}
		}
		this.qtNodes = quadTree;
	}

	public static TransitNetworkHR createFromSchedule(final Network network, final TransitSchedule schedule,
			final Vehicles transitVehicles, final double maxBeelineWalkConnectionDistance, Tuple<Double, Double> timeBin) {
		log.info("start creating transit network");
		final TransitNetworkHR transitNetwork = new TransitNetworkHR();
		final Counter linkCounter = new Counter(" link #");
		final Counter nodeCounter = new Counter(" node #");
		int numTravelLinks = 0, numWaitingLinks = 0, numInsideLinks = 0, numTransferLinks = 0;
		Map<Id<TransitStopFacility>, TransitRouterNetworkNode> stops = new HashMap<Id<TransitStopFacility>, TransitRouterNetworkNode>();
		TransitRouterNetworkNode nodeSR, nodeS;
		// build stop nodes
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values())
				for (TransitRouteStop stop : route.getStops()) {
					nodeS = stops.get(stop.getStopFacility().getId());
					if (nodeS == null) {
						nodeS = transitNetwork.createNode(stop, null, null, null);// Node for stopFacility
						nodeCounter.incCounter();
						stops.put(stop.getStopFacility().getId(), nodeS);
					}
				}
		transitNetwork.finishInit();
		// build transfer links
		log.info("add transfer links");
		// connect all stops with walking links if they're located less than
		// beelineWalkConnectionDistance from each other
		for (TransitRouterNetworkNode node : transitNetwork.getNodes().values())
			for (TransitRouterNetworkNode node2 : transitNetwork.getNearestNodes(node.stop.getStopFacility().getCoord(),
					maxBeelineWalkConnectionDistance))
				if (node != node2) {
					transitNetwork.createTransferLink(network, node, node2);
					linkCounter.incCounter();
					numTransferLinks++;
				}
		// build nodes and links connecting the nodes according to the transit routes
		log.info("add travel, waiting and inside links");
		for (TransitLine line : schedule.getTransitLines().values())
			for (TransitRoute route : line.getRoutes().values()) {
				TransitRouterNetworkNode prevNode = null;
				List<TransitRouteStop> stoplist = route.getStops();
				for (int i = 0; i < stoplist.size(); i++) {
					TransitRouteStop stop = stoplist.get(i);
					nodeS = stops.get(stop.getStopFacility().getId());

					int occ = 0;
					for (int j = 0; j < i; j++) {
						if (stoplist.get(j).getStopFacility().getId().toString().replace("BT_", "").replace("bus_", "")
								.equals(stop.getStopFacility().getId().toString().replace("BT_", "").replace("bus_", ""))) {
							occ++;
						}
					}

					nodeSR = transitNetwork.createNode(stop, route, line, new TransitStop(stop, occ)); // nodes stop
																										// route
					nodeCounter.incCounter();
					if (prevNode != null) {
						transitNetwork.createTravelLink(network, prevNode, nodeSR, route, line, timeBin, 
								transitVehicles);
						linkCounter.incCounter();
						numTravelLinks++;
					}
					prevNode = nodeSR;
					transitNetwork.createTransferLink(network, nodeSR, nodeS); //Internal transfer link
					linkCounter.incCounter();
					numInsideLinks++;
					if( i < stoplist.size() - 1) { //It is not allowed to board in the final stop.
						transitNetwork.createWaitingLink(network, nodeS, nodeSR, timeBin, transitVehicles);
						linkCounter.incCounter();
						numWaitingLinks++;
					}
				}
			}
		log.info("transit router network statistics:");
		log.info(" # nodes: " + transitNetwork.getNodes().size());
		log.info(" # links total:     " + transitNetwork.getLinks().size());
		log.info(" # travel links:  " + numTravelLinks);
		log.info(" # waiting links:  " + numWaitingLinks);
		log.info(" # inside links:  " + numInsideLinks);
		log.info(" # transfer links:  " + numTransferLinks);
		return transitNetwork;
	}
	
	public Map<Id<TransitLink>,TransitLink> getTransitLinkMap(){
		Map<Id<TransitLink>,TransitLink> transitLinkMap = new HashMap<>();
		for(Map.Entry<Id<Link>, TransitRouterNetworkLink> link: links.entrySet()) {
			transitLinkMap.put(Id.create(link.getKey(), TransitLink.class), link.getValue());
		}
		return transitLinkMap;
	}

	public Collection<TransitRouterNetworkNode> getNearestNodes(final Coord coord, final double distance) {
		return this.qtNodes.getDisk(coord.getX(), coord.getY(), distance);
	}

	public TransitRouterNetworkNode getNearestNode(final Coord coord) {
		return this.qtNodes.getClosest(coord.getX(), coord.getY());
	}

	@Override
	public double getCapacityPeriod() {
		return 3600.0;
	}

	@Override
	public NetworkFactory getFactory() {
		return null;
	}

	@Override
	public double getEffectiveLaneWidth() {
		return 3;
	}

	@Override
	public void addNode(Node nn) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addLink(Link ll) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Link removeLink(Id<Link> linkId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Node removeNode(Id<Node> nodeId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCapacityPeriod(double capPeriod) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setEffectiveCellSize(double effectiveCellSize) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setEffectiveLaneWidth(double effectiveLaneWidth) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public double getEffectiveCellSize() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public Attributes getAttributes() {
		throw new UnsupportedOperationException();
	}

}
