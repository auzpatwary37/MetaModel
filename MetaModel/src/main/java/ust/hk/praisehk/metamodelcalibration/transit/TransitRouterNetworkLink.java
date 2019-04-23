package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.utils.objectattributes.attributable.Attributes;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.transit.TransitNetworkHR.TransitRouterNetworkNode;

/**
 * Looks to me like an implementation of the Link interface, with
 * get(Transit)Route and get(Transit)Line on top. To recall: TransitLine is
 * something like M44. But it can have more than one route, e.g. going north,
 * going south, long route, short route. That is, presumably we have one such
 * TransitRouterNetworkLink per TransitRoute. kai/manuel, feb'12
 */
public class TransitRouterNetworkLink extends TransitLink implements Link {

	final TransitRouterNetworkNode fromNode;
	final TransitRouterNetworkNode toNode;
	final TransitRoute route;
	final TransitLine line;
	final Id<Link> id;
	private double length;

	public TransitRouterNetworkLink(final Id<Link> id, final TransitRouterNetworkNode fromNode,
			final TransitRouterNetworkNode toNode, final TransitRoute route, final TransitLine line,
			Network network) {
		super(null, null, null, null);
		this.id = id;
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.route = route;
		this.line = line;
		if (route == null)
			this.length = CoordUtils.calcEuclideanDistance(this.toNode.stop.getStopFacility().getCoord(),
					this.fromNode.stop.getStopFacility().getCoord());
		else {
			this.length = 0;
			for (Id<Link> linkId : route.getRoute().getSubRoute(fromNode.stop.getStopFacility().getLinkId(),
					toNode.stop.getStopFacility().getLinkId()).getLinkIds())
				this.length += network.getLinks().get(linkId).getLength();
			this.length += network.getLinks().get(toNode.stop.getStopFacility().getLinkId()).getLength();
		}
	}

	@Override
	public TransitRouterNetworkNode getFromNode() {
		return this.fromNode;
	}

	@Override
	public TransitRouterNetworkNode getToNode() {
		return this.toNode;
	}

	@Override
	public double getCapacity() {
		return getCapacity(Time.UNDEFINED_TIME);
	}

	@Override
	public double getCapacity(final double time) {
		return 9999; //Infite capacity
	}

	@Override
	public double getFreespeed() {
		return getFreespeed(Time.UNDEFINED_TIME);
	}

	@Override
	public double getFreespeed(final double time) {
		return 10; //The speed also doesn't matter.
	}

	@Override
	public Id<Link> getId() {
		return this.id;
	}

	@Override
	public double getNumberOfLanes() {
		return getNumberOfLanes(Time.UNDEFINED_TIME);
	}

	@Override
	public double getNumberOfLanes(final double time) {
		return 1;
	}

	@Override
	public double getLength() {
		return this.length;
	}

	@Override
	public void setCapacity(final double capacity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setFreespeed(final double freespeed) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean setFromNode(final Node node) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setNumberOfLanes(final double lanes) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLength(final double length) {
		this.length = length;
	}

	@Override
	public boolean setToNode(final Node node) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Coord getCoord() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> getAllowedModes() {
		return null;
	}

	@Override
	public void setAllowedModes(final Set<String> modes) {
		throw new UnsupportedOperationException();
	}

	public TransitRoute getRoute() {
		return route;
	}

	public TransitLine getLine() {
		return line;
	}

	@Override
	public double getFlowCapacityPerSec() {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public double getFlowCapacityPerSec(double time) {
		// TODO Auto-generated method stub
		throw new RuntimeException("not implemented");
	}

	@Override
	public Attributes getAttributes() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addPassanger(double d, AnalyticalModelNetwork Network) {
		this.passangerCount += d;
	}

	@Override
	public Id<TransitLink> getTrLinkId() {
		return Id.create(id, TransitLink.class);
	}
}
