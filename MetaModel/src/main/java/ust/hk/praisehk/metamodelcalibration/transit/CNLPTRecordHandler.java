package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.TransitStop;
import dynamicTransitRouter.TripsData;
import dynamicTransitRouter.costs.StopStopTime;
import dynamicTransitRouter.costs.VehicleOccupancy;
import dynamicTransitRouter.costs.WaitingTime;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;

/**
 * This function is implemented for the metamodel, based on the transit assignment of last iteration,
 * try to go for a transit assignment in the next iteration.
 * @author cetest
 *
 */
public class CNLPTRecordHandler implements WaitingTime, StopStopTime, VehicleOccupancy {
	
	//private Vehicles transitVehicles;
	// private final EventsManager events; //Seems not need if we don't charge them.

	//private Map<TransitLineRoute, Map<TransitStop, ArrivalInfo>> arriveMap;
	//private Map<TransitLineRoute, Map<TransitStop, short[]>> queueLength;
	//private Map<Id<Vehicle>, VehicleInfo> vehicleInfoCache;
	//private final Map<Id<Person>, TripsData> peopleTripData = new HashMap<>();
	//private final List<Id<Person>> peopleStillQueueing = new ArrayList<>();

	private int stepSizeInSecond;
	private int numOfBins;
	private final double simulationStartTime;
	private final double simulationEndTime;
	
	private Map<String,Double> anaParams;
	
	private final Map<String, TransitNetworkHR> transitNetworks;
	private final Map<String, Tuple<Double,Double>> timeBeans;
	private final AnalyticalModel analyticalModal;

	//private final Map<Id<TransitLine>, TransitLine> transitLines;
	//private final Map<Id<TransitStopFacility>, TransitStopFacility> stops;
	
	//private final Map<Id<TransitStopFacility>, Set<TransitLineRoute>> lineAndRoutesAtStop;
	//boolean processedQueue; //If it is false, then the residual queue would need to be checked.
	private final static Logger log = Logger.getLogger(CNLPTRecordHandler.class);

	public CNLPTRecordHandler(Scenario scenario, Map<String, TransitNetworkHR> transitNetworks, AnalyticalModel model) {
		//this.transitVehicles = scenario.getTransitVehicles();
		//this.transitLines = scenario.getTransitSchedule().getTransitLines();
		//this.stops = scenario.getTransitSchedule().getFacilities();
		this.transitNetworks = transitNetworks;
		this.timeBeans = model.getTimeBeans();
		this.anaParams = model.getAnalyticalModelInternalParams();
		this.analyticalModal = model;
		
		this.simulationStartTime = scenario.getConfig().qsim().getStartTime();
		this.simulationEndTime = scenario.getConfig().qsim().getEndTime();
		this.stepSizeInSecond = scenario.getConfig().travelTimeCalculator().getTraveltimeBinSize();
		if(scenario.getConfig().qsim().getStartTime()==Double.NEGATIVE_INFINITY || 
				simulationEndTime==Double.NEGATIVE_INFINITY) {
			throw new IllegalArgumentException("The start time or end time in the scneario cannot be undefined!");
		}
		this.numOfBins = (int) ((this.simulationEndTime - this.simulationStartTime) / this.stepSizeInSecond + 1);
		
		TransitRouterFareDynamicImpl.initializeDirectWalkCount(); //Initialize the direct walk count.
	}
	
	private String getTimeBean(double tripStartTime) {
		double lastTime=0;
		String lastTimeBean=null;
		for(String t : this.timeBeans.keySet()) {
			//This part is for finding the last timeBin
			if(timeBeans.get(t).getSecond()>lastTime) {
				lastTime=timeBeans.get(t).getSecond();
				lastTimeBean=t;
			}
			if(tripStartTime>=this.timeBeans.get(t).getFirst() && tripStartTime<this.timeBeans.get(t).getSecond()) {
				return t; //This is for finding the middle timeBins
			}	
		}
		return lastTimeBean;
	}
	
	@Override
	public double getVehicleSeatRemain(TransitStop stop, Id<TransitLine> lineId, Id<TransitRoute> routeId,
			double time) {
		String timeBin = getTimeBean(time);
		TransitNetworkHR tn = transitNetworks.get(timeBin);
		TransitTravelLink travelLink = tn.getTravelLink(lineId, routeId, null, stop);
		return travelLink.getExpectedRemainingSeat();
	}

	@Override
	public double expectedStopStopTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop fromTransitStop, TransitStop toTransitStop, double time) {
		String timeBin = getTimeBean(time);
		TransitNetworkHR tn = transitNetworks.get(timeBin);
		TransitTravelLink travelLink = tn.getTravelLink(transitLineId, transitRouteId, fromTransitStop, toTransitStop);
		return travelLink.getExpectedTravelTime(analyticalModal, time);
	}

	@Override
	public double expectedWaitingTime(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId,
			TransitStop transitStop, double time) {
		//Step 1: Find the respective waiting link
		String timeBin = getTimeBean(time);
		TransitNetworkHR tn = transitNetworks.get(timeBin);
		TransitWaitLink waitLink = tn.getWaitingLink(transitLineId, transitRouteId, transitStop);
		
		//Step 2: Return the time based on some calculation function.
		return waitLink.getWaitingTime(this.anaParams);
	}

	/**
	 * A tuple like object containing lineId and routeId
	 * @author eleead
	 *
	 */
	private class TransitLineRoute {
		private final Id<TransitLine> lineId;
		private final Id<TransitRoute> routeId;

		/**
		 * Initialize by transit line and route object.
		 * @param line original TransitLine
		 * @param route original TransitRoute
		 */
		private TransitLineRoute(TransitLine line, TransitRoute route) {
			this(line.getId(), route.getId());
		}

		private TransitLineRoute(Id<TransitLine> lineId, Id<TransitRoute> routeId) {
			this.lineId = lineId;
			this.routeId = routeId;
		}

		/**
		 * Equal if the line Id and the route Id is the same
		 */
		@Override
		public int hashCode() {
			return 41 * lineId.hashCode() + routeId.hashCode();
		}

		@Override
		public boolean equals(Object other) {
			TransitLineRoute others = (TransitLineRoute) other;
			return others.lineId.equals(this.lineId) && others.routeId.equals(this.routeId);
		}

		@Override
		public String toString() {
			return "Line: " + this.lineId.toString() + " Route: " + this.routeId.toString();

		}
	}
}
