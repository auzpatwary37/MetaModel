package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.LinkedHashMap;
import java.util.Map;

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

import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.transit.TransitNetworkHR.TransitRouterNetworkNode;

/**
 * This is the class designated for the waiting link
 * @author eleead
 *
 */
public class TransitWaitLink extends TransitRouterNetworkLink {
	
	private int maximumCapacity = 0;
	private double frequency = 0;
	private double timeBinLength;
	
	private TransitTravelLink linkOfLastStop; //To get the remaining seat.

	public TransitWaitLink(Id<Link> id, TransitRouterNetworkNode fromNode, TransitRouterNetworkNode toNode, Network network, 
			Tuple<Double, Double> timeBin, Vehicles transitVehicles) {
		super(id, fromNode, toNode, null, null, network);
		//TODO: Find out the maximum capacity
		for(Departure departure: toNode.route.getDepartures().values()) {
			double departureTimeAtStop = departure.getDepartureTime() + toNode.getStop().getDepartureOffset();
			if(departureTimeAtStop >= timeBin.getFirst() && departureTimeAtStop <= timeBin.getSecond()) {
				VehicleCapacity vc = transitVehicles.getVehicles().get(departure.getVehicleId()).getType().getCapacity();
				maximumCapacity += (vc.getSeats() + vc.getStandingRoom());
				frequency += 1;
			}
		}
		timeBinLength = timeBin.getSecond() - timeBin.getFirst();
		initialLinkOfLastStop(toNode);
	}
	
	/**
	 * Given a stop, find the capacity left.
	 * @param toNode
	 * @return
	 */
	private void initialLinkOfLastStop(TransitRouterNetworkNode toNode) {
		for(Link link: toNode.getInLinks().values()) {
			if(link instanceof TransitTravelLink) {
				linkOfLastStop = (TransitTravelLink) link;
				return;
			}
		}
		linkOfLastStop = null;
	}
	
	public Id<TransitLine> getLineIdWaiting(){
		return this.toNode.getLine().getId();
	}
	
	public Id<TransitRoute> getRouteIdWaiting(){
		return this.toNode.getRoute().getId();
	}
	
	public Id<TransitStopFacility> getStopId(){
		return this.toNode.tStop.getFacilityId();
	}
	
	public String getWaitingMode() {
		return this.toNode.getRoute().getTransportMode();
	}
	
	/**
	 * The main function to get the waiting time, based on the volumes stored.
	 * @return
	 */
	public double getWaitingTime(Map<String, Double> anaParams) {
		if(this.maximumCapacity == 0) {
			return timeBinLength * 1.5;
		}
		double remainSeat = linkOfLastStop != null? linkOfLastStop.getExpectedRemainingSeat() : maximumCapacity;
		double passCount = this.passangerCount;
		double headway = frequency==0? timeBinLength: timeBinLength / frequency;
		double passengerOnboard = remainSeat > maximumCapacity? 0: maximumCapacity - remainSeat;
		double waitingTime = headway*anaParams.get(CNLSUEModel.TransferalphaName)+
				headway * Math.pow( (passCount + passengerOnboard )/maximumCapacity, 
						anaParams.get(CNLSUEModel.TransferbetaName));
		if(waitingTime==Double.NaN||waitingTime==Double.POSITIVE_INFINITY) {
			return 86400;
		}
		if(waitingTime < 0) {
			throw new IllegalArgumentException("The waiting time is wrong!");
		}
		return waitingTime;
	}

}
