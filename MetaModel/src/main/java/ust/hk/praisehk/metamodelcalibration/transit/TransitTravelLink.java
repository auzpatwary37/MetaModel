package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.transit.TransitNetworkHR.TransitRouterNetworkNode;

/**
 * This is the class for travel link (i.e. between two stops of a route)
 * @author eleead
 *
 */
public class TransitTravelLink extends TransitRouterNetworkLink {
	
	List<Id<Link>> linksPassed;
	private final int spaceProvided;

	public TransitTravelLink(Id<Link> id, TransitRouterNetworkNode fromNode, TransitRouterNetworkNode toNode,
			TransitRoute route, TransitLine line, Network network, Tuple<Double, Double> timeBin, 
			Vehicles transitVehicles) {
		super(id, fromNode, toNode, route, line, network);
		List<Id<Link>> linkIdList = Lists.newArrayList(route.getRoute().getLinkIds());
		linkIdList.add(0, route.getRoute().getStartLinkId());
		linkIdList.add(route.getRoute().getEndLinkId());
		
		Id<Link> startLinkId = fromNode.getStop().getStopFacility().getLinkId();
		int startLinkIndex = linkIdList.indexOf(startLinkId);
		
		Id<Link> endLinkId = toNode.getStop().getStopFacility().getLinkId();
		int endLinkIndex = linkIdList.subList(startLinkIndex, linkIdList.size()).indexOf(endLinkId) + startLinkIndex;
		
		linksPassed = Collections.unmodifiableList(linkIdList.subList(startLinkIndex, endLinkIndex));
		
		int expectedSeats = 0;
		for(Departure departure: route.getDepartures().values()) {
			double departureTimeAtStop = departure.getDepartureTime() + fromNode.getStop().getDepartureOffset();
			if(departureTimeAtStop >= timeBin.getFirst() && departureTimeAtStop <= timeBin.getSecond()) {
				VehicleCapacity vc = transitVehicles.getVehicles().get(departure.getVehicleId()).getType().getCapacity();
				expectedSeats += (vc.getSeats() + vc.getStandingRoom());
			}
		}
		this.spaceProvided = expectedSeats;
	}
	
	public Id<TransitStopFacility> getToStopFacilityId(){
		return this.toNode.tStop.getFacilityId();
	}
	
	/**
	 * Get the expected remaining seat, based on the volume.
	 * @return
	 */
	public double getExpectedRemainingSeat() {
		return Math.max(0, this.spaceProvided - this.passangerCount);
	}
	
	public double getExpectedTravelTime(AnalyticalModel model, double time) {
		double travelTime = 0.0;
		for(Id<Link> linkId: linksPassed) {
			travelTime += model.getAverageLinkTravelTime(linkId, time);
		}
		return travelTime;
	}

}
