package ust.hk.praisehk.metamodelcalibration.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.pt.PtConstants;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.transit.ITransitRoute;

/**
 * This class is designed to put the result of the modal to the MATSim population
 * @author Enoch LEE
 *
 */
public class ModalToMATSim {
	private Map<String, Tuple<Double,Double>> timeBeans;
	private Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> odPairSet;
	private TransitSchedule ts;
	private Network network;
	private TeleportationRoutingModule walkRouter;
	private final Logger logger=Logger.getLogger(ModalToMATSim.class);
	
	public ModalToMATSim(CNLSUEModel model, Scenario scenario) {
		timeBeans = model.getTimeBeans();
		ts = scenario.getTransitSchedule();
		network = scenario.getNetwork();
		PlansCalcRouteConfigGroup cg = (PlansCalcRouteConfigGroup) scenario.getConfig().getModules().get("planscalcroute");
		walkRouter = new TeleportationRoutingModule(TransportMode.transit_walk, 
				PopulationUtils.getFactory(), cg.getTeleportedModeSpeeds().get(TransportMode.walk),
				cg.getBeelineDistanceFactors().get(TransportMode.walk)	);
		odPairSet = model.getODPairset();
	}
	
	/**
	 * Given an OD pair, and respective start and end link IDs, find the appropriate route.
	 * Return null if there is no route in this OD pair
	 * @param odPair
	 * @param startLinkId
	 * @param endLinkId
	 * @param timeBin
	 * @return the route found, null if route is not found
	 */
	private Route findAppropriateRoute(AnalyticalModelODpair odPair, Id<Link> startLinkId, Id<Link> endLinkId, String timeBin) {
		List<Tuple<Route, Double>> foundRouteAndUtility = Lists.newArrayList();
		double totalUtility = 0.0;
		if(startLinkId != null && endLinkId != null) {
			for(AnalyticalModelRoute route: odPair.getRoutes()) {
				if(route.getRoute().getStartLinkId().equals(startLinkId) && 
						route.getRoute().getEndLinkId().equals(endLinkId)) {
					double utility = odPair.getRouteUtility(timeBin).get(route.getRouteId());
					foundRouteAndUtility.add(new Tuple<>(route.getRoute(), utility));
					totalUtility += Math.exp(utility);
				}
			}
		}else {
			throw new RuntimeException("Not implemented!");
		}
		//Do the modal split
		if(totalUtility > 0) {
			double randomNumber = Math.random();
			for(Tuple<Route, Double> routeAndUtility: foundRouteAndUtility) {
				randomNumber -= Math.exp(routeAndUtility.getSecond()) / totalUtility;
				if(randomNumber<=0) {
					return routeAndUtility.getFirst();
				}
			}
			throw new RuntimeException("Should not reach there!");
		}else {
			return null;
		}
	}
	
	/**
	 * 	 * Given an OD pair, and respective start and end link IDs, find the appropriate transit route.
	 * Return null if there is no route in this OD pair
	 * @param odPair
	 * @param timeBin
	 * @return
	 */
	private List<Leg> findTransitAppropriateRoute(AnalyticalModelODpair odPair, Coord fromCoord, Coord toCoord, String timeBin) {
		List<Tuple<List<Leg>, Double>> foundRouteAndUtility = Lists.newArrayList();
		double totalUtility = 0.0;
		for(AnalyticalModelTransitRoute route: odPair.getTrRoutes()) {
			ITransitRoute actualRoute = (ITransitRoute) route;
			if(NetworkUtils.getEuclideanDistance(actualRoute.getOriginCoord(), fromCoord) <=500 &&
					NetworkUtils.getEuclideanDistance(actualRoute.getDestinationCoord(), toCoord)<=500) {
				double utility = odPair.getTrRouteUtility(timeBin).get(route.getTrRouteId());
				foundRouteAndUtility.add(new Tuple<>(route.getLegListRoute(this.timeBeans.get(timeBin).getFirst()), 
						utility));
				totalUtility += Math.exp(utility);
			}
		}
		//Do the modal split
		if(totalUtility > 0) {
			double randomNumber = Math.random();
			for(Tuple<List<Leg>, Double> routeAndUtility: foundRouteAndUtility) {
				randomNumber -= Math.exp(routeAndUtility.getSecond()) / totalUtility;
				if(randomNumber<=0) {
					return routeAndUtility.getFirst();
				}
			}
			throw new RuntimeException("Should not reach there!");
		}else {
			return null;
		}
	}
	
	private static void offsetPlanTime(Plan plan, double offSet) {
		for(PlanElement pe: plan.getPlanElements()) {
			if(pe instanceof Activity) {
				if(((Activity) pe).getStartTime() <= -offSet) {
					continue; //Don't do the offset for it may become negative time.
				}
				((Activity) pe).setStartTime(((Activity) pe).getStartTime() + offSet);
				((Activity) pe).setEndTime(((Activity) pe).getEndTime() + offSet);
			}
			if(pe instanceof Leg) {
				((Leg) pe).setDepartureTime(((Leg) pe).getDepartureTime()+offSet);
			}
		}
	}
	
	/**
	 * This is a method written by Enoch to assign the current routes available to match MATSim population
	 * It assumes only one plan is there.
	 * @param population
	 */
	public void assignRoutesToMATSimPopulation(Population population, double ptRatio, boolean offsetTime) {
		if(ptRatio < 0 || ptRatio > 1) {
			throw new IllegalArgumentException("The pt ratio cannot be that small!");
		}
		
		AtomicInteger assignedCount = new AtomicInteger(0);
		AtomicInteger ptAssignedCount = new AtomicInteger(0);
		AtomicInteger processedCount = new AtomicInteger(0);
		ConcurrentHashMultiset<Id<Person>> offsetedPerson = ConcurrentHashMultiset.create();
		ConcurrentHashMultiset<Id<Person>> processingPerson = ConcurrentHashMultiset.create();
		//What we have:
		for(AnalyticalModelODpair odPair: odPairSet.values()) {
			List<Id<Person>> personIdsConcerning = odPair.getPersonIds();
			personIdsConcerning.parallelStream().forEach(personId->{
				while(processingPerson.contains(personId)) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
						throw new RuntimeException("It is being interrupted!");
					}
				}
				
				processingPerson.add(personId);
				if(processedCount.incrementAndGet()%100000==0) {
					logger.info("Assigning route to "+processedCount.intValue()+"-th trip");
				}
				Plan p = population.getPersons().get(personId).getSelectedPlan();
				//Coord lastCoord = null; //Last coordinate of activity
				Id<Link> lastLinkId = null; //Last linkId of activity
				Leg lastLeg = null; //Last leg
				String lastLegtimeBin = null; //Last time bin of leg.
				Activity lastActivity = null;
				
				boolean ignoreNextAct = false;
				
				if(offsetTime && !offsetedPerson.contains(personId)) {
					offsetedPerson.add(personId);
					double timeVariation = Math.random() * 1800 - 3600; //Do a random time variation
					offsetPlanTime(p, timeVariation);
				}
				
				int i = 0;
				while(i < p.getPlanElements().size()) {
					PlanElement pe = p.getPlanElements().get(i);
					if(pe instanceof Activity) {
						Activity thisAct = (Activity) pe;
						if(thisAct.getType().equals(PtConstants.TRANSIT_ACTIVITY_TYPE)) {
							ignoreNextAct = true; //This trip is already assigned a route.
							i++;
							continue; //Ignore this activity if it is a transit exchange activity.
						}
						Id<Link> linkId =  thisAct.getLinkId();
						if(!ignoreNextAct && lastLinkId != null) { //Not first iteration and the trip is not assigned activity
							//Try to assign the route for last Leg
							if(lastLeg.getMode().equals("car")) {
								NetworkRoute routeFound = (NetworkRoute) findAppropriateRoute(odPair, lastLinkId, linkId, lastLegtimeBin);
								if(routeFound != null) {
									routeFound = RouteUtils.createLinkNetworkRouteImpl(routeFound.getStartLinkId(), routeFound.getLinkIds(), 
											routeFound.getEndLinkId()); //We copy a route
									lastLeg.setRoute(routeFound);
									assignedCount.incrementAndGet();
								}
							}else if(lastLeg.getMode().equals("pt")) { //Remove and replace the route.
								List<Leg> transitRouteFound = findTransitAppropriateRoute(odPair, lastActivity.getCoord(), 
										thisAct.getCoord(), lastLegtimeBin);
								if(transitRouteFound!= null) {
									p.getPlanElements().remove(i-1);
									p.getPlanElements().addAll(i-1, fillWithActivities(transitRouteFound, FacilitiesUtils.wrapActivity(lastActivity), 
											FacilitiesUtils.wrapActivity(thisAct), lastLeg.getDepartureTime(), null));
									i+= ( (transitRouteFound.size()-1) *2);
									ptAssignedCount.incrementAndGet();
								}
							}
						}else {
							ignoreNextAct = false;
						}
						//lastCoord = coord;
						lastLinkId = linkId;
						lastLegtimeBin = odPair.getTimeBean(thisAct.getEndTime());
						lastActivity = (Activity) pe;
					}
					if(pe instanceof Leg) {
						lastLeg = (Leg) pe;
					}
					i++;
				}
				processingPerson.remove(personId);
			});
		}
		//Step 2:
		logger.info("Number of trip assigned route : "+assignedCount);
		logger.info("Number of pt trip assigned route : "+ptAssignedCount);
	}
	
	private List<PlanElement> fillWithActivities(
			final List<Leg> baseTrip,
			final Facility fromFacility,
			final Facility toFacility, double departureTime, Person person) {
		List<PlanElement> trip = new ArrayList<>();
		Coord nextCoord = null;
		int i = 0;
		for (Leg leg : baseTrip) {
			if (i == 0) {
				// (access leg)
				Facility firstToFacility;
				if (baseTrip.size() > 1) { // at least one pt leg available
					ExperimentalTransitRoute tRoute = (ExperimentalTransitRoute) baseTrip.get(1).getRoute();
					firstToFacility = this.ts.getFacilities().get(tRoute.getAccessStopId());
				} else {
					firstToFacility = toFacility;
				}
				// (*)
				Route route = createWalkRoute(fromFacility, departureTime, person, leg.getTravelTime(), firstToFacility);
				leg.setRoute(route);
			} else {
				if (leg.getRoute() instanceof ExperimentalTransitRoute) {
					ExperimentalTransitRoute tRoute = (ExperimentalTransitRoute) leg.getRoute();
					tRoute.setTravelTime(leg.getTravelTime());
					tRoute.setDistance(RouteUtils.calcDistance(tRoute, this.ts, this.network));
					Activity act = PopulationUtils.createActivityFromCoordAndLinkId(PtConstants.TRANSIT_ACTIVITY_TYPE, this.ts.getFacilities().get(tRoute.getAccessStopId()).getCoord(), tRoute.getStartLinkId());
					act.setMaximumDuration(0.0);
					trip.add(act);
					nextCoord = this.ts.getFacilities().get(tRoute.getEgressStopId()).getCoord();
				} else { 
					// it is not an instance of an ExperimentalTransitRoute so it must be a (transit) walk leg.

					// walk legs don't have a coord, use the coord from the last egress point.  yyyy But I don't understand why in one case we take "nextCoord", while in the
					// other case we retrieve the facility from the previous route.

					if (i == baseTrip.size() - 1) {
						// if this is the last leg, we don't believe the leg from the TransitRouter.  Why?

						ExperimentalTransitRoute tRoute = (ExperimentalTransitRoute) baseTrip.get(baseTrip.size() - 2).getRoute();
						Facility lastFromFacility = this.ts.getFacilities().get(tRoute.getEgressStopId());
						
						Route route = createWalkRoute(lastFromFacility, departureTime, person, leg.getTravelTime(), toFacility);
						leg.setRoute(route);
					}
					Activity act = PopulationUtils.createActivityFromCoordAndLinkId(PtConstants.TRANSIT_ACTIVITY_TYPE, nextCoord, leg.getRoute().getStartLinkId());
					act.setMaximumDuration(0.0);
					trip.add(act);
				}
			}
			trip.add(leg);
			i++;
		}
		return trip;
	}
	
	private Route createWalkRoute(final Facility fromFacility, double departureTime, Person person, double travelTime, Facility firstToFacility) {
		// yyyy I extracted this method to make a bit more transparent that it is used twice.  But I don't know why it is done in this way
		// (take distance from newly computed walk leg, but take travelTime from elsewhere).  Possibly, the problem is that the TransitRouter 
		// historically just does not compute the distances.  kai, may'17
		
		Route route = RouteUtils.createGenericRouteImpl(fromFacility.getLinkId(), firstToFacility.getLinkId());
		final List<? extends PlanElement> walkRoute = walkRouter.calcRoute(fromFacility, firstToFacility, departureTime, person);
		route.setDistance(((Leg) walkRoute.get(0)).getRoute().getDistance());
		route.setTravelTime(((Leg) walkRoute.get(0)).getTravelTime());
		return route;
	}
}
