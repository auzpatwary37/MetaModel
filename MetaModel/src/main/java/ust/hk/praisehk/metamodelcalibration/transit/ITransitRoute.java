package ust.hk.praisehk.metamodelcalibration.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;

public class ITransitRoute implements AnalyticalModelTransitRoute {
	private final static String directWalkName = "direct walking";
	
	private final Logger logger=Logger.getLogger(ITransitRoute.class);
	private final Id<AnalyticalModelTransitRoute> trRouteId;
	//private final TransitSchedule transitSchedule;
	private final Path transitPath;
	private final Coord originCoord;
	private final Coord destinationCoord;
	private List<Trip> trips = new ArrayList<>();
	private List<Id<Link>> linkTravelled = new ArrayList<>();
	private List<TransitWaitLink> waitingLinks = new ArrayList<>();
	private final double routeDistance;
	private double walkingDistance = 0.0;
	private final double transferCount;
	private double routeFare;
	private double routeUtility;
	
	private Map<String, Double> routeCapacity;
	
	private final TransitRouterFareDynamicImpl planRouter;
	
	/**
	 * Constructor for the ITransitRoute.
	 * @param trRouteId
	 * @param ts
	 * @param transitPath The path extracted from the TransitNetworkHR.
	 */
	public ITransitRoute(TransitRouterFareDynamicImpl planRouter, Coord fromCoord, Coord toCoord, Path transitPath) {
		this.trRouteId = getIdFromPath(transitPath);
		this.planRouter = planRouter;
		//this.transitSchedule = ts;
		double routeDistance = 0.0;
		double transferCount = 0.0;
		double walkingDistance = transitPath.nodes.isEmpty()? NetworkUtils.getEuclideanDistance(fromCoord, toCoord) : 
						NetworkUtils.getEuclideanDistance(fromCoord, transitPath.nodes.get(0).getCoord()) + 
						NetworkUtils.getEuclideanDistance(toCoord, transitPath.nodes.get(transitPath.nodes.size() - 1).getCoord());
		for(Link link: transitPath.links) {
			if(link instanceof TransitTravelLink) {
				routeDistance += link.getLength();
				linkTravelled.addAll(((TransitTravelLink) link).linksPassed);
			}else if(link instanceof TransitWaitLink) {
				transferCount +=1;
				waitingLinks.add((TransitWaitLink) link);
			}else {
				walkingDistance += link.getLength(); //Store the walking distance for the inside link.
			}
		}
		this.walkingDistance = walkingDistance;
		this.transitPath = transitPath;
		this.routeDistance = routeDistance;
		this.transferCount = transferCount;
		this.originCoord = fromCoord;
		this.destinationCoord = toCoord;
	}
	
	public Coord getOriginCoord() {
		return originCoord;
	}

	public Coord getDestinationCoord() {
		return destinationCoord;
	}

	/**
	 * It is a helper function to obtain the ID given the path in the transit network.
	 * Also, it would set up the trips variable
	 * @param transitPath
	 * @return
	 */
	private Id<AnalyticalModelTransitRoute> getIdFromPath(Path transitPath) {
		if(transitPath.links.isEmpty()) {
			return Id.create(directWalkName, AnalyticalModelTransitRoute.class);
		}
		
		StringBuilder buildId = new StringBuilder();
		String lastStop = null;
		for(Link link: transitPath.links) {
			if(link instanceof TransitTravelLink) {
				lastStop = ((TransitTravelLink) link).getToStopFacilityId().toString();
				trips.get(trips.size()-1).toFacilityId = ((TransitTravelLink) link).getToStopFacilityId();
			}else if(link instanceof TransitWaitLink) {
				if(lastStop!=null) {
					buildId.append("-" + lastStop+"==");
				}
				Id<TransitLine> toTransitLineId = ((TransitWaitLink) link).getLineIdWaiting();
				Id<TransitRoute> toTransitRouteId = ((TransitWaitLink) link).getRouteIdWaiting();
				Id<TransitStopFacility> boardingStopFacilityId = ((TransitWaitLink) link).getStopId();
				buildId.append(toTransitLineId.toString());
				buildId.append("_" + toTransitRouteId.toString() + "_");
				buildId.append(boardingStopFacilityId.toString());
				
				String mode = ((TransitWaitLink) link).getWaitingMode();
				if(trips.size()>0) {
					trips.get(trips.size()-1).nextMode = mode;
				}				
				trips.add( new Trip( toTransitLineId, toTransitRouteId, boardingStopFacilityId, null, mode) );
			}
		}
		buildId.append("-" + lastStop);
		return Id.create(buildId.toString(), AnalyticalModelTransitRoute.class);
	}
	
	@Override
	public double calcRouteUtility(Map<String, Double> params, Map<String, Double> anaParams,
			AnalyticalModelNetwork network, Map<String, FareCalculator> farecalc, 
			TransferDiscountCalculator tdc, Tuple<Double, Double> timeBean) {
		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUDistance=params.get(CNLSUEModel.MarginalUtilityOfDistancePtName);
		double MUWalkTime=params.get(CNLSUEModel.MarginalUtilityOfWalkingName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUWaitingTime=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant=params.get(CNLSUEModel.ModeConstantPtname);
		double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
		double DistanceBasedMoneyCostWalk=params.get(CNLSUEModel.DistanceBasedMoneyCostWalkName);
		double fare = this.getFare(null, farecalc, tdc);
		double travelTime=this.calcRouteTravelTime(network,timeBean,params,anaParams);
		double walkTime=this.getRouteWalkingDistance()/1.4;
		double walkDist=this.getRouteWalkingDistance();
		double waitingTime=this.getRouteWaitingTime(anaParams,network);
		double MUTransfer=params.get(CNLSUEModel.UtilityOfLineSwitchName);
		
		this.routeUtility = ModeConstant+
				travelTime*MUTravelTime+
				MUMoney*fare+
				MUWalkTime*walkTime+
				MUMoney*DistanceBasedMoneyCostWalk*walkDist+
				MUWaitingTime*waitingTime
				+ MUTransfer* this.transferCount
				+MUDistance * routeDistance * MUMoney;
		return this.routeUtility*anaParams.get(CNLSUEModel.LinkMiuName); //It is possible that the utility is 0, as the origin and destination is the same.
	}

	@Override
	public double getFare(TransitSchedule ts, Map<String, FareCalculator> farecalc, TransferDiscountCalculator tdc) {
		if(this.routeFare!=0) {
			return this.routeFare;
		}
		Id<TransitStopFacility> startStopIdTrain=null; //Store the start stop for the case of MTR.
		for(Trip trip: trips) {
			Id<TransitLine> tlineId = trip.transitLineId;
			Id<TransitRoute> trouteId = trip.trId;
			
			//Handling the train fare
			if(trip.mode.equals("train")) {
				if(startStopIdTrain == null) {
					startStopIdTrain = trip.fromFacilityId; //Train trip not yet started
				}
				if( !trip.nextMode.equals("train") ) { //If next mode is not train, charge the fare.
					MTRFareCalculator mtrFare=(MTRFareCalculator) farecalc.get("train");
					this.routeFare += mtrFare.getMinFare(null, null, startStopIdTrain, trip.toFacilityId);
				}
			}else{//not a train trip leg, so just add the fare.
				this.routeFare += farecalc.get(trip.mode).getMinFare(trouteId, tlineId, trip.fromFacilityId, trip.toFacilityId);
			}
		}
		return this.routeFare;
	}

	@Override
	public double calcRouteTravelTime(AnalyticalModelNetwork network, Tuple<Double, Double> timeBean,
			Map<String, Double> params, Map<String, Double> anaParams) {
		double travelTime = 0.;
		for(Id<Link> link: linkTravelled) {
			travelTime += ((AnalyticalModelLink) network.getLinks().get(link)).getLinkTravelTime(timeBean, params, anaParams);
		}
		if(travelTime > 2e6) {
			//logger.warn("Adjusted the time to 200000s");
			return 2e6;
		}
		return travelTime;
	}

	@Override
	public double getRouteWalkingDistance() {
		return this.walkingDistance;
	}

	@Override
	public double getRouteWaitingTime(Map<String, Double> anaParams, AnalyticalModelNetwork network) {
		double waitingTime = 0.0;
		for(TransitWaitLink waitLink: waitingLinks) {
			waitingTime += waitLink.getWaitingTime(anaParams);
		}
		return waitingTime;
	}

	@Override
	public Id<AnalyticalModelTransitRoute> getTrRouteId() {
		return this.trRouteId;
	}

	@Override
	public ArrayList<Id<TransitLink>> getTrLinkIds() {
		ArrayList<Id<TransitLink>> tLinks = new ArrayList<>();
		for(Object link: transitPath.links) {
			tLinks.add( ((TransitLink) link).getTrLinkId() );
		}
		return tLinks;
	}

	@Override
	public Map<String, Double> getRouteCapacity() {
		return this.routeCapacity;
	}

	@Override
	/**
	 * It is a dummy function for the bigger picture of MetaModal
	 */
	public void calcCapacityHeadway(Map<String, Tuple<Double, Double>> timeBean, String timeBeanId) {
		if(routeCapacity!=null && routeCapacity.containsKey(timeBeanId)) {
			return;
		}else if(routeCapacity!=null) {
			routeCapacity.put(timeBeanId, 1.0); //It should be avilable 
		}else {
			routeCapacity = new HashMap<>();
			routeCapacity.put(timeBeanId, 1.0); //It should be avilable 
		}
	}

	@Override
	public AnalyticalModelTransitRoute cloneRoute() {
		return new ITransitRoute(this.planRouter, this.originCoord, this.destinationCoord, this.transitPath);
	}
	
	@Override
	public List<Leg> getLegListRoute(double departureTime) {
		if(this.trRouteId.toString().equals(directWalkName)) {
			List<Leg> legs = new ArrayList<Leg>();
			Leg leg = PopulationUtils.createLeg(TransportMode.transit_walk);
			Route walkRoute = RouteUtils.createGenericRouteImpl(null, null);
			walkRoute.setDistance(this.walkingDistance);
			leg.setRoute(walkRoute);
			leg.setTravelTime(this.walkingDistance / 1.4);
			legs.add(leg);
			return legs;
		}else {
			//Convert the path back to the one cope with the original network
			List<Node> nodeList = new ArrayList<>();
			for(Node node: this.transitPath.nodes) {
				nodeList.add(this.planRouter.getTransitRouterNetwork().getNodes().get(node.getId()));
			}
			List<Link> listList = new ArrayList<>();
			for(Link link: this.transitPath.links) {
				listList.add(this.planRouter.getTransitRouterNetwork().getLinks().get(link.getId()));
			}
			
			Path path = new Path(nodeList, listList, this.transitPath.travelCost, this.transitPath.travelTime);
			return this.planRouter.convertPathToLegList(departureTime, path, this.originCoord, this.destinationCoord, 
					null);
		}
	}
	
	public String toString() {
		return this.getTrRouteId().toString();
	}
	
	private class Trip {
		private final Id<TransitLine> transitLineId;
		private final Id<TransitRoute> trId;
		private final Id<TransitStopFacility> fromFacilityId;
		private Id<TransitStopFacility> toFacilityId;
		private final String mode;
		private String nextMode;
		
		private Trip(Id<TransitLine> transitLineId, Id<TransitRoute> trId, Id<TransitStopFacility> fromFacilityId, 
				Id<TransitStopFacility> toFacilityId, String mode) {
			this.transitLineId = transitLineId;
			this.trId = trId;
			this.fromFacilityId = fromFacilityId;
			this.toFacilityId = toFacilityId;
			this.mode = mode;
			this.nextMode = "end"; //It is default as ending.
		}
	}
}
