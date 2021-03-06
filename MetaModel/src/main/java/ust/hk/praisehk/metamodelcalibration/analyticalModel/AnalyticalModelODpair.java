package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.vehicles.VehicleType;
import com.google.inject.Inject;

/**
 * This is a self sufficient implementation of OD pair class.
 * Basically this class is a container which holds information for each OD pair
 * @author h
 *
 */

public class AnalyticalModelODpair {
		
	private double agentCARCounter=0;
	private double agentTrCounter=0;
	//private double ExpectedMaximumCarUtility;
	private double ExpectedMaximumTransitUtility;
	private List<Id<Person>> personIdList = new ArrayList<>(); //TODO: Remove it.
	
	private final Node onode;
	private final Node dnode;
	private final Coord ocoord;
	private final Coord dcoord;
	private double expansionFactor;
	private Map<String,Double> demand=new HashMap<>();
	//private Map <Id<VehicleType>,VehicleType> vt;
	private Coord[] c;
	private final Id<AnalyticalModelODpair> ODpairId;
	private LinkedHashMap<Id<AnalyticalModelRoute>, Integer> routeset=new LinkedHashMap<>(); //I can't see why it should be a Map instead of List. Enoch.
	private Map<Id<AnalyticalModelRoute>,AnalyticalModelRoute> RoutesWithDescription=new HashMap<>();
	private ArrayList<AnalyticalModelRoute> finalRoutes;
	private Map<Id<Link>,ArrayList<AnalyticalModelRoute>> linkIncidence=null;
	private Map<Id<TransitLink>,ArrayList<AnalyticalModelTransitRoute>> trLinkIncidence=null;
	private double routePercentage=5.0;
	private double originParkingCharge=0;
	private double destinationParkingCharge=0;
	private ActivityFacility OriginFacility;
	private ActivityFacility DestinationFacility;
	private Map<Id<AnalyticalModelTransitRoute>,AnalyticalModelTransitRoute> Transitroutes=new HashMap<>();
	private Map<Id<AnalyticalModelTransitRoute>, Integer> transitRouteCounter=new HashMap<>();
	private ArrayList<AnalyticalModelTransitRoute> finalTrRoutes;
	private Map<String,HashMap<Id<AnalyticalModelRoute>,Double>> routeUtility=new ConcurrentHashMap<>();
	private Map<String,HashMap<Id<AnalyticalModelTransitRoute>, Double>> TrRouteUtility=new ConcurrentHashMap<>();
	private final Map<String, Tuple<Double,Double>>timeBean;
	private Map<String, ArrayList<AnalyticalModelTransitRoute>> timeBasedTransitRoutes=new HashMap<>();
	private String subPopulation;
	//private double PCU=1;
	private int minRoute=5;
	private LinkedHashMap<Tuple<Id<Link>,Id<Link>>,Integer> startAndEndLinkIds=new LinkedHashMap<>();

	private int totalLeg = 0;
	//private List<Id<Person>> personIdsConcerned = new ArrayList<>();
	//TODO:Shift Node Based Coordinates to FacilityBased Coordinates
	
	/**
	 * This will give the modal split from MATSim
	 * @return
	 */
	public double getCarModalSplit() {
		return (double)this.agentCARCounter/(this.agentTrCounter+this.agentCARCounter);
	}
	
	/**
	 * This will give the origin Activity Facility
	 * Will be used in future expansion.
	 * No Setters for the Activity Facilities as they will be taken in the constructor
	 * @return
	 */
	public ActivityFacility getOriginFacility() {
		OriginFacility=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory().createActivityFacility(
				Id.create(this.onode.getId().toString(), ActivityFacility.class),this.ocoord);
		return OriginFacility;
	}


	/**
	 * This will give the Destination Activity Facility
	 * Will be used in future expansion.
	 * @return
	 */
	public ActivityFacility getDestinationFacility() {
		DestinationFacility=ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory().createActivityFacility(
				Id.create(this.dnode.getId().toString(), ActivityFacility.class),this.dcoord);
		return DestinationFacility;
	}


	@Inject
	/**
	 * Constructor
	 * TODO: Shift towards Facility based constructor
	 * @param onode
	 * @param dnode
	 * @param network
	 */
	public AnalyticalModelODpair(Node onode,Node dnode, Network network,Map<String, Tuple<Double, Double>> timeBean2){
		this.ocoord=onode.getCoord();
		this.dcoord=dnode.getCoord();
		this.onode=onode;
		this.dnode=dnode;
		this.expansionFactor=1;
		for(String s:timeBean2.keySet()){this.demand.put(s,0.);}
		ODpairId=Id.create(onode.getId().toString()+"_"+dnode.getId().toString(), AnalyticalModelODpair.class);
		this.timeBean=timeBean2;
		for(String timeBeanId:this.timeBean.keySet()) {
			this.routeUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
		}
		
	}
	
	public AnalyticalModelODpair(Node onode,Node dnode, Network network,Map<String, Tuple<Double, Double>> timeBean2,String subPopulation){
		this.ocoord=onode.getCoord();
		this.dcoord=dnode.getCoord();
		this.onode=onode;
		this.dnode=dnode;
		this.expansionFactor=1;
		for(String s:timeBean2.keySet()){this.demand.put(s,0.);}
		ODpairId=Id.create(onode.getId().toString()+"_"+dnode.getId().toString()+"_"+subPopulation, AnalyticalModelODpair.class);
		this.timeBean=timeBean2;
		for(String timeBeanId:this.timeBean.keySet()) {
			this.routeUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelRoute>, Double>());
			this.TrRouteUtility.put(timeBeanId, new HashMap<Id<AnalyticalModelTransitRoute>, Double>());
		}
		this.subPopulation=subPopulation;
	}
	
	
	
	public String getSubPopulation() {
		return subPopulation;
	}

	/**
	 * Return specific timeBeanId OD demand
	 * @param timeBeanId
	 * @return
	 */
	public double getSpecificPeriodODDemand(String timeBeanId){
		return this.demand.get(timeBeanId);
	}
	
	/**
	 * Get Origin Node
	 * @return
	 */
	public Node getOriginNode() {
		return onode;
	}


	/**
	 * Gives destination Ndoe
	 * @return
	 */
	public Node getDestinationNode() {
		return dnode;
	}


	/**
	 * Give od pair id
	 * @return
	 */
	public Id<AnalyticalModelODpair> getODpairId() {
		return ODpairId;
	}



	public Coord[] getODCoord(){
		c[0]=ocoord;
		c[1]=dcoord;
		return c;
	}
	public void resetDemand() {
		for(String i:this.demand.keySet()) {
			this.demand.put(i,0.);
		}
		this.agentCARCounter=0;
		this.agentTrCounter=0;
		//this.ExpectedMaximumCarUtility=0;
		this.ExpectedMaximumTransitUtility=0;
		this.routeUtility.clear();
		this.TrRouteUtility.clear();
	}
	
	

	/**
	 * 
	 * adding trips generated from a population file
	 * @param trip 
	 */

	public void addtrip(Trip trip){
		String timeId=null;
		Integer i=0;
		double lastTime=0;
		String lastTimeBean=null;
		for(String t:this.timeBean.keySet()) {
			if(timeBean.get(t).getSecond()>lastTime) {
				lastTime=timeBean.get(t).getSecond();
				lastTimeBean=t;
			}
			if(trip.getStartTime()>=this.timeBean.get(t).getFirst() && trip.getStartTime()<this.timeBean.get(t).getSecond()) {
				timeId=t;
			}	
		}
	
		if(timeId==null) {
			
			timeId=lastTimeBean;
		}
		
		if(trip.getRoute()!=null){
			demand.put(timeId, demand.get(timeId)+trip.getCarPCU());
			this.agentCARCounter+=trip.getCarPCU();
			if(!routeset.containsKey(trip.getRoute().getRouteId())){//A new route 
				routeset.put(trip.getRoute().getRouteId(),1);
				this.RoutesWithDescription.put(trip.getRoute().getRouteId(),trip.getRoute());
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
				//this.no_of_occurance.put(trip.getRouteId(), 1);

			}else{ //not a new route
				this.routeset.put(trip.getRoute().getRouteId(), routeset.get(trip.getRoute().getRouteId())+1);
				//this.RoutesWithDescription.get(trip.getRoute().getRouteDescription()).addPerson(trip.getPersonId());
			}
		}else if(trip.getTrRoute()!=null) {
//			if(demand.get(timeId)==null) {
//				System.out.println();
//			}
			demand.put(timeId, demand.get(timeId)+1);
			this.agentTrCounter++;
			if(!this.Transitroutes.containsKey(trip.getTrRoute().getTrRouteId())) {
				this.Transitroutes.put(trip.getTrRoute().getTrRouteId(),trip.getTrRoute());
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(), 1);
			}else {
				this.transitRouteCounter.put(trip.getTrRoute().getTrRouteId(),this.transitRouteCounter.get(trip.getTrRoute().getTrRouteId())+ 1);
			}
		}else {
			this.personIdList.add(trip.getPersonId());
		}
		
		
	}
	
	public void addCarRoute(AnalyticalModelRoute r) {
		this.agentCARCounter++;
		if(!routeset.containsKey(r.getRouteId())){//A new route 
			routeset.put(r.getRouteId(),1);
			this.RoutesWithDescription.put(r.getRouteId(),r);
			
		}else{ //not a new route, This should not happen in this case or should it???
			this.routeset.put(r.getRouteId(), routeset.get(r.getRouteId())+1);
		}
		
	}
	
	/**
	 * !!!WARNING!!!
	 * This do not work. Here for future improvement while adding pt routeset generation
	 */
	public void addTransitRoute() {
		
	}
	
	public String getTimeBean(double tripStartTime) {
		double lastTime=0;
		String lastTimeBean=null;
		for(String t : this.timeBean.keySet()) {
			//This part is for finding the last timeBin
			if(timeBean.get(t).getSecond()>lastTime) {
				lastTime=timeBean.get(t).getSecond();
				lastTimeBean=t;
			}
			if(tripStartTime>=this.timeBean.get(t).getFirst() && tripStartTime<this.timeBean.get(t).getSecond()) {
				return t; //This is for finding the middle timeBins
			}	
		}
		return lastTimeBean;
	}
	
	/**
	 * This is a convenient function to add trip without having a route.
	 * Only visible in package
	 * @param trip
	 * @param carOnly A boolean indicate whether only the car trip is considered or not.
	 */
	void addtripWithoutRoute(Trip trip, boolean carOnly){
		if( !carOnly || trip.getMode().equals("car")) { //The operation is only for car trips is carOnly==True
			personIdList.add( trip.getPersonId() ); //Store the person ID.
			String timeId = getTimeBean(trip.getStartTime());
			demand.put(timeId, demand.get(timeId)+trip.getCarPCU());
			if(this.startAndEndLinkIds.containsKey(trip.getStartAndEndLinkId())) {
				this.startAndEndLinkIds.put(trip.getStartAndEndLinkId(),this.startAndEndLinkIds.get(trip.getStartAndEndLinkId())+1);
			}else {
				this.startAndEndLinkIds.put(trip.getStartAndEndLinkId(),1);
			}
			totalLeg++; //Number of trip is increased by 1.
			
			//TODO: Where is agentCarCounter and agentTrCounter?
		}
	}
	
	/**
	 * This will return the full route Set
	 * @return
	 */
	public LinkedHashMap<Id<AnalyticalModelRoute>, Integer> getRouteset() {
		return routeset;
	}

	public Tuple<Id<Link>,Id<Link>> getStartAndEndLinkIds(){
		int[] index=new int[this.startAndEndLinkIds.size()];
		double[] prob=new double[this.startAndEndLinkIds.size()];
		int i=0;
		
		for(Tuple<Id<Link>,Id<Link>> linkIds:this.startAndEndLinkIds.keySet()) {
			index[i]=i;
			prob[i]=this.startAndEndLinkIds.get(linkIds)*1./this.totalLeg;
			i++;
		}
		EnumeratedIntegerDistribution dist=new EnumeratedIntegerDistribution(index,prob);
		int ind=dist.sample();
		i=0;
		Tuple<Id<Link>,Id<Link>> returnId=null;
		for(Tuple<Id<Link>,Id<Link>> linkId:this.startAndEndLinkIds.keySet()) {
			if(i==ind) {
				returnId= linkId;
			}
			i++;
		}
		return returnId;
	}
	
	
	
	
	/**
	 * For future expansion 
	 * @return
	 */
	public double getExpansionFactor() {
		return expansionFactor;
	}
	
	/**
	 * For future expansion 
	 * @return
	 */
	public void setExpansionFactor(double expansionFactor) {
		this.expansionFactor = expansionFactor;
	}

	/**
	 * Gives the demand ArrayList<double demand> index are the hour
	 * @return
	 */
	public Map<String,Double> getDemand() {
		return demand;
	}
	
	
	public void generateRoutes(double routePercentage) {
		if(this.agentCARCounter!=0) {
			this.routePercentage=routePercentage;
			this.finalRoutes=new ArrayList<>();
			for(Entry<Id<AnalyticalModelRoute>, Integer> e:routeset.entrySet()) {
				if(((double)e.getValue()/(double)this.agentCARCounter*100)>routePercentage) {
					this.finalRoutes.add(this.RoutesWithDescription.get(e.getKey()));
					for(String timeBeanId:this.timeBean.keySet()) {
						this.routeUtility.get(timeBeanId).put(e.getKey(), 0.0);
					}
				}
			}
			if(finalRoutes.size()<=this.minRoute && finalRoutes.size()<=this.routeset.size()) {
				ArrayList<Integer> tripCount=new ArrayList<Integer>(this.routeset.values());
				Collections.sort(tripCount);
				Collections.reverse(tripCount);
				if(routeset.size()<=this.minRoute) {
					for(AnalyticalModelRoute r:this.RoutesWithDescription.values()) {
						if(!finalRoutes.contains(r)) {
							this.finalRoutes.add(r);
						}
					}
				}else {
					for(java.util.Map.Entry<Id<AnalyticalModelRoute>, Integer> e:routeset.entrySet()) {
						if(e.getValue()>tripCount.get(this.minRoute-1)) {
							this.finalRoutes.add(this.RoutesWithDescription.get(e.getKey()));
							for(String timeBeanId:this.timeBean.keySet()) {
								this.routeUtility.get(timeBeanId).put(e.getKey(), 0.0);
							}
						}
					}
					
				}
			}

		}
	}
	
	
	
	public void generateTRRoutes(double routePercentage) {
		for(String timeBean:this.timeBean.keySet()) {
			this.TrRouteUtility.put(timeBean,new HashMap<Id<AnalyticalModelTransitRoute>,Double>());
		}
		if(this.agentTrCounter!=0) {
			finalTrRoutes=new ArrayList<>();
			for(Entry<Id<AnalyticalModelTransitRoute>, Integer> e:this.transitRouteCounter.entrySet()) {
				if(((double)e.getValue()/(double)this.agentTrCounter*100)>routePercentage||this.transitRouteCounter.size()<=this.minRoute) {
					this.finalTrRoutes.add(this.Transitroutes.get(e.getKey()));
					for(String timeBeanId:this.timeBean.keySet()) {
						AnalyticalModelTransitRoute tr=this.Transitroutes.get(e.getKey());
						tr.calcCapacityHeadway(this.timeBean, timeBeanId);
//						if((double)tr.getRouteCapacity().get(timeBeanId)!=0) {
//							this.TrRouteUtility.get(timeBeanId).put(tr.getTrRouteId(), 0.0);
//						}
					}
				}
			}
			if(finalTrRoutes.size()<=this.minRoute && finalTrRoutes.size()<=this.transitRouteCounter.size()) {
				ArrayList<Integer> tripCount=new ArrayList<Integer>(this.transitRouteCounter.values());
				Collections.sort(tripCount);
				Collections.reverse(tripCount);
				if(transitRouteCounter.size()<=this.minRoute) {
					this.finalTrRoutes.addAll(this.Transitroutes.values());
				}else {
					for(Entry<Id<AnalyticalModelTransitRoute>, Integer> e:this.transitRouteCounter.entrySet()) {
						if(e.getValue()>tripCount.get(this.minRoute-1)) {
							this.finalTrRoutes.add(this.Transitroutes.get(e.getKey()));
							for(String timeBeanId:this.timeBean.keySet()) {
								AnalyticalModelTransitRoute tr=this.Transitroutes.get(e.getKey());
								tr.calcCapacityHeadway(this.timeBean, timeBeanId);
							}
						}
					}
					
				}
			}
			
		}
		
		
	}
	
	public void updateRouteUtility(Id<AnalyticalModelRoute> routeId, double utility,String timeBeanId) {
		this.routeUtility.get(timeBeanId).put(routeId, utility);
	}
	public void updateTrRouteUtility(Id<AnalyticalModelTransitRoute> id, double utility,String timeBeanId) {
		this.TrRouteUtility.get(timeBeanId).put(id, utility);
	}
	
	public HashMap<Id<AnalyticalModelRoute>, Double> getRouteUtility(String timeBeanId) {
		return routeUtility.get(timeBeanId);
	}

	public HashMap<Id<AnalyticalModelTransitRoute>, Double> getTrRouteUtility(String timeBeanId) {
		return TrRouteUtility.get(timeBeanId);
	}
	
	/**
	 * This function will be used to delete some routes. This requires recreating the final routeset 
	 * @param remaingingRouteNo
	 */
	public void deleteCarRoute(int remaingingRouteNo) {
		if(this.routeset.size()<=remaingingRouteNo) {
			return;
		}
		boolean demandZero=true;
		for(Double demandValue:this.demand.values()) { //Check if there isn't any demand
			if (demandValue > 0) {
				demandZero=false;
				break;
			}
		}
		if(demandZero) return; //We do nothing when the utility is zero, as it is no demand, not necessary to do anything
		//System.out.println();
		//Calculate the log-sum
		Map<Id<AnalyticalModelRoute>,Double> logSumRouteUtility=new HashMap<>();
		for(HashMap<Id<AnalyticalModelRoute>,Double> utilityMap : this.routeUtility.values()) { //Sum the utilities across every route.
			for(Id<AnalyticalModelRoute> routeId : utilityMap.keySet()) {
				if(utilityMap.get(routeId)!=0) {
					if(logSumRouteUtility.containsKey(routeId)) {
						logSumRouteUtility.put(routeId,logSumRouteUtility.get(routeId)+Math.exp(utilityMap.get(routeId)));
					}else {
						logSumRouteUtility.put(routeId,Math.exp(utilityMap.get(routeId)));
					}
				}
				
			}
		}
		for(Id<AnalyticalModelRoute>routeId:logSumRouteUtility.keySet()) {
			logSumRouteUtility.put(routeId,Math.log(logSumRouteUtility.get(routeId)));
		}
		List<Double> orderedRouteUtility = new ArrayList<Double>(logSumRouteUtility.values());
		if(orderedRouteUtility.isEmpty()) {
			return; //I don't know why, but all utilities are 0.
		}
		Collections.sort(orderedRouteUtility);
		Collections.reverse(orderedRouteUtility); //Sort the log sum route utility
		double criticalUtility = orderedRouteUtility.get(remaingingRouteNo);
		for(Id<AnalyticalModelRoute> routeId : logSumRouteUtility.keySet()) {
			if(logSumRouteUtility.get(routeId) < criticalUtility) {
				this.routeset.remove(routeId); //Remove both route set and description
				this.RoutesWithDescription.remove(routeId);
			}
		}
		
	}

	public double getExpectedMaximumCarUtility(LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, String timeBeanId) {
		if(this.routeUtility.get(timeBeanId).size()==0) {
			return Double.NEGATIVE_INFINITY;
		}
		double logsum=0;
		for(double utility:this.routeUtility.get(timeBeanId).values()) {
			logsum+=Math.exp(utility);
		}
		return 1 * Math.log(logsum) / anaParams.get("LinkMiu");
	}

	public double getExpectedMaximumTransitUtility(LinkedHashMap<String,Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		if(this.TrRouteUtility.get(timeBeanId).size()==0) {
			return Double.NEGATIVE_INFINITY;
		}
		double logsum=0;
		for(double utility:this.TrRouteUtility.get(timeBeanId).values()) {
			logsum+=Math.exp(utility);
			
		}
		this.ExpectedMaximumTransitUtility=1*Math.log(logsum);
		return ExpectedMaximumTransitUtility/anaParams.get("LinkMiu");
	}

	/**
	 * This is another very important function. equivalent of  Delta matrix production
	 */
	public void generateLinkIncidence(){
		if (finalRoutes==null){
			this.generateRoutes(this.routePercentage);
			
		}

		this.linkIncidence=new HashMap<>();
		this.trLinkIncidence=new HashMap<>();
		if(this.finalRoutes!=null) {
			for(AnalyticalModelRoute route:finalRoutes){
				ArrayList<Id<Link>>linkIds=route.getLinkIds();
				for(Id<Link> linkId: linkIds){
					if(this.linkIncidence.containsKey(linkId)){
						this.linkIncidence.get(linkId).add(route);
					}else{
						ArrayList<AnalyticalModelRoute> routeList=new ArrayList<> ();
						routeList.add(route);
						this.linkIncidence.put(linkId, routeList);
					}
				}
			}
		}
		
		if(finalTrRoutes==null) {
			this.generateTRRoutes(routePercentage);
		}
		if(this.finalTrRoutes!=null) {
			for(AnalyticalModelTransitRoute route:finalTrRoutes){
				ArrayList<Id<TransitLink>>linkIds=route.getTrLinkIds();
				for(Id<TransitLink> trlinkId: linkIds){
					if(this.trLinkIncidence.containsKey(trlinkId)){
						this.trLinkIncidence.get(trlinkId).add(route);
					}else{
						ArrayList<AnalyticalModelTransitRoute> routeList=new ArrayList<> ();
						routeList.add(route);
						this.trLinkIncidence.put(trlinkId, routeList);
					}
				}
			}	
		}
		//System.out.println();
		
		if((this.finalRoutes!=null ||this.finalTrRoutes!=null)&&(this.routeset.size()!=0 && this.finalRoutes.size()==0)||(this.transitRouteCounter.size()!=0 && this.finalTrRoutes.size()==0)) {
			throw new IllegalArgumentException("Stop!!! No Routes Were Created!!!");
		}
	}
	
	
//CAUTION: This method can be used only after using the method getRoutes(int no_of_Routes)
	//as the variable finalRoutes is generated in that method which is the basic output of this method.

	public double getRoutePercentage() {
		return routePercentage;
	}

	public void setRoutePercentage(double routePercentage) {
		this.routePercentage = routePercentage;
	}
	
	public List<Id<Person>> getPersonIds(){
		return personIdList;
	}

	
	/**
	 * This returns extracted most used car routes
	 * @return
	 */

	public ArrayList<AnalyticalModelRoute> getRoutes() {
		return finalRoutes;
	}
	
	/**
	 * This method returns the most used transit routes
	 * clones the routes
	 * @return
	 */
	public ArrayList<AnalyticalModelTransitRoute> getTrRoutes(Map<String, Tuple<Double, Double>> timeBean2,String timeBeanId) {
		if(this.timeBasedTransitRoutes.size()==0) {
			this.generateTimeBasedTransitRoutes(timeBean2);
		}
		return this.timeBasedTransitRoutes.get(timeBeanId);
	}
	public ArrayList<AnalyticalModelTransitRoute> getTrRoutes(){
		if(this.finalTrRoutes==null) {
			return new ArrayList<AnalyticalModelTransitRoute>();
		}
		return this.finalTrRoutes;
	}
	

	public Map<Id<Link>,ArrayList<AnalyticalModelRoute>> getLinkIncidence(){
		if(this.linkIncidence==null) {
			this.generateLinkIncidence();
		}
		return linkIncidence;
		
	}
	
	public Map<Id<TransitLink>,ArrayList<AnalyticalModelTransitRoute>> getTrLinkIncidence(){
		if(this.trLinkIncidence==null) {
			this.generateLinkIncidence();
		}
		
		return trLinkIncidence;
		
	}
	
	
	
	public double getOriginParkingCharge() {
		return originParkingCharge;
	}

	public void setOriginParkingCharge(double originParkingCharge) {
		this.originParkingCharge = originParkingCharge;
	}

	public double getDestinationParkingCharge() {
		return destinationParkingCharge;
	}

	public void setDestinationParkingCharge(double destinationParkingCharge) {
		this.destinationParkingCharge = destinationParkingCharge;
	}

	public double getParkingCharge(){
		return (this.originParkingCharge+this.destinationParkingCharge)/2;
	}

	public Map<String, Tuple<Double,Double>> getTimeBean() {
		return timeBean;
	}

	public void generateTimeBasedTransitRoutes(Map<String, Tuple<Double, Double>> timeBean2) {
		if(this.finalTrRoutes!=null) {
			for(String timeBeanId:timeBean2.keySet()) {
			ArrayList<AnalyticalModelTransitRoute> timeBasedTrRoutes=new ArrayList<>();
			for(AnalyticalModelTransitRoute tr:this.finalTrRoutes) {
				AnalyticalModelTransitRoute trnew=tr.cloneRoute();
				trnew.calcCapacityHeadway(timeBean2, timeBeanId);
				if((Double)tr.getRouteCapacity().get(timeBeanId)!=0) {
					timeBasedTrRoutes.add(trnew);
				}
				
			}
			this.timeBasedTransitRoutes.put(timeBeanId,timeBasedTrRoutes);
		}
	}
	}
	
	public double getTotalTrip() {
		return this.totalLeg;
	}

	public double getAgentCounter() {
		return agentCARCounter+this.agentTrCounter;
	}
}
