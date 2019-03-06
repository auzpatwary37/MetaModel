package ust.hk.praisehk.metamodelcalibration.analyticalModel;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;



public abstract class AnalyticalModelODpairs {
	
	//private Config config=ConfigUtils.createConfig();
	//private Scenario scenario;
	private Vehicles vehicles;
	protected final Network network;
	@Deprecated private Population population;
	private Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair> ODpairset=new HashMap<>();
	private Map<Id<AnalyticalModelODpair>,Double> ODdemand=new HashMap<>();
	//private Map<Id<AnalyticalModelODpair>,Double> ODdemandperhour=new HashMap<>();
	private final Map<String,Tuple<Double,Double>> timeBins; //There contains all time bins.
	
	public Vehicles getVehicles() {
		return vehicles;
	}
	
	/**
	 * Create analytical model OD pairs from scratch (Maybe it is not useful?)
	 * Notes: The scenario does not have vehicle included
	 * @param populationFileLocation
	 * @param networkFileLocation
	 * @param timeBean
	 */
	@Deprecated
	public AnalyticalModelODpairs(String populationFileLocation, String networkFileLocation,HashMap<String,Tuple<Double,Double>> timeBean){
		this.network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(this.network).readFile(networkFileLocation);
		
		//population = scenario.getPopulation();
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(populationFileLocation);
		this.population = scenario.getPopulation();
		//network=scenario.getNetwork();
		//population=scenario.getPopulation();
		this.timeBins=timeBean;
	}
	
	/**
	 * Constructor to create from network and population file
	 * @param network
	 * @param population
	 * @param timeBean
	 * @param scenario
	 */
	public AnalyticalModelODpairs(Network network, Map<String,Tuple<Double,Double>> timeBean, Scenario scenario){
		this.network=network;
		//this.population=population;
		this.timeBins=timeBean;
		this.vehicles=scenario.getVehicles();
	}
	
	
	@SuppressWarnings("unchecked")
	public void generateODpairset(Population population){
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		
		if(multiThread==true) {
			ArrayList<TripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(population.getPersons().values()), (int)(population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new TripsCreatorFromPlan(personList.get(i),this));
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(TripsCreatorFromPlan t:threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId : population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			double pcu=1;
			Vehicle v=this.vehicles.getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
			if(v!=null) {
				pcu=v.getType().getPcuEquivalents();
			}
			trip.setCarPCU(pcu);
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(network);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(trip.getOriginNode(),trip.getDestinationNode(),network,this.timeBins);
					odpair.addtrip(trip);
					ODpairset.put(trip.generateODpairId(network), odpair);
				}
			}else {
				if(!trip.getMode().equals("transit_walk")) {
					//throw new IllegalArgumentException("WAit");
				}
				tripsWithoutRoute++;
			}
		}
		System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}

	public void deleteCarRoutes(int RemainingRouteNo) {
		for(Id<AnalyticalModelODpair> odId:this.ODpairset.keySet()) {
			this.ODpairset.get(odId).deleteCarRoute(RemainingRouteNo);
		}
		//this.generateRouteandLinkIncidence(0);
	}
	
	/**
	 * This function generate routes for a initial given population
	 */
	public void generateODpairsetWithoutRoutes(Population population){
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		
		if(multiThread==true) {
			ArrayList<TripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(population.getPersons().values()), (int)(population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new TripsCreatorFromPlan(personList.get(i),this));
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(TripsCreatorFromPlan t:threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId:population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			double pcu=1;
			Vehicle v=this.vehicles.getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
			if(v!=null) {
				pcu=v.getType().getPcuEquivalents();
			}
			trip.setCarPCU(pcu);
//			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(network);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtripWithoutRoute(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(trip.getOriginNode(),trip.getDestinationNode(),network,this.timeBins);
					odpair.addtripWithoutRoute(trip);
					ODpairset.put(trip.generateODpairId(network), odpair);
				}
//			}else {
//				if(!trip.getMode().equals("transit_walk")) {
//					//throw new IllegalArgumentException("WAit");
//				}
//				tripsWithoutRoute++;
//			}
		}
		//System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}
	
	public HashMap <Id<AnalyticalModelODpair>,ActivityFacility> getOriginActivityFacilitie(){
		HashMap<Id<AnalyticalModelODpair>,ActivityFacility> Ofacilities=new HashMap<>();
		for (Id<AnalyticalModelODpair> odpairId:this.ODpairset.keySet()) {
			Ofacilities.put(odpairId, this.ODpairset.get(odpairId).getOriginFacility());
		}
		
		return Ofacilities;
	}
	public HashMap<Id<AnalyticalModelODpair>,ActivityFacility> getDestinationFacilitie(){
		HashMap<Id<AnalyticalModelODpair>,ActivityFacility> Dfacilities=new HashMap<>();
		for (Id<AnalyticalModelODpair> odpairId:this.ODpairset.keySet()) {
			Dfacilities.put(odpairId, this.ODpairset.get(odpairId).getDestinationFacility());
		}
		
		return Dfacilities;
	}
	public Map<Id<AnalyticalModelODpair>,Double> getDemand(String timeBeanId){
		for(Id<AnalyticalModelODpair> ODpairId:ODpairset.keySet()){
			this.ODdemand.put(ODpairId, ODpairset.get(ODpairId).getSpecificPeriodODDemand(timeBeanId));
		}
		return ODdemand;
	}


	public Network getNetwork() {
		return network;
	}

//	public Population getPopulation() {
//		return population;
//	}
	
	public Map<Id<Link>,ArrayList<AnalyticalModelRoute>> getLinkIncidence(String ODpairId){
		this.ODpairset.get(ODpairId).generateLinkIncidence();
		return this.ODpairset.get(ODpairId).getLinkIncidence();
	}
	public Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> getODpairset() {
		return ODpairset;
	}
	
	public void generateRouteandLinkIncidence(double routePercentage){
		for (Id<AnalyticalModelODpair> odpairId:ODpairset.keySet()){
			ODpairset.get(odpairId).generateRoutes(routePercentage);
			ODpairset.get(odpairId).generateTRRoutes(routePercentage);
			ODpairset.get(odpairId).generateLinkIncidence();
		}
	}
	
	
	public void resetDemand() {
		for(AnalyticalModelODpair odpair: this.ODpairset.values()) {
			odpair.resetDemand();
		}
		
	}
	protected abstract TripChain getNewTripChain(Plan plan);
	protected AnalyticalModelODpair getNewODPair(Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2) {
		return new AnalyticalModelODpair(oNode,dNode,network,timeBean2);
	}
	protected AnalyticalModelODpair getNewODPair(Node oNode,Node dNode, Network network,Map<String, Tuple<Double,Double>> timeBean2,String subPopName) {
		return new AnalyticalModelODpair(oNode,dNode,network,timeBean2,subPopName);
	}
	public Map<String, Tuple<Double,Double>> getTimeBean() {
		return timeBins;
	}
	
	public abstract Map<Id<TransitLink>, TransitLink> getTransitLinks(Map<String,Tuple<Double,Double>> timeBean,String timeBeanId);


	public void generateODpairsetSubPop(Network odNetwork, Population population){
		if(odNetwork==null) {
			odNetwork=this.network;
		}
		ArrayList<Trip> trips=new ArrayList<>();
		
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		if(multiThread==true) {
			ArrayList<TripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(population.getPersons().values()), (int)(population.getPersons().values().size()/16));
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new TripsCreatorFromPlan(personList.get(i),this));
				threadrun.get(i).setPersonsAttributes(population.getPersonAttributes());
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(TripsCreatorFromPlan t:threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId:population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				String s = (String) population.getPersonAttributes().getAttribute(personId.toString(), "SUBPOP_ATTRIB_NAME");
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
				}
				trips.addAll( tripchain.getTrips());
			}
		}
		double tripsWithoutRoute=0;
		for (Trip trip:trips){
			double pcu=1;
			Vehicle v=this.vehicles.getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
			if(v!=null) {
				pcu=v.getType().getPcuEquivalents();
			}
			trip.setCarPCU(pcu);
			if(trip.getRoute()!=null ||trip.getTrRoute()!=null) {
				Id<AnalyticalModelODpair> ODId=trip.generateODpairId(odNetwork);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtrip(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair(trip.getOriginNode(),trip.getDestinationNode(),odNetwork,this.timeBins,trip.getSubPopulationName());
					odpair.addtrip(trip);
					ODpairset.put(trip.generateODpairId(odNetwork), odpair);
				}
			}else {
				tripsWithoutRoute++;
			}
		}
		System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}
	
	public void generateODpairsetWithoutRoutesSubPop(Network odNetwork, Population population){
		ArrayList<Trip> trips=new ArrayList<>();
		
		if(odNetwork==null) {
			odNetwork=this.network;
		}
		/**
		 * Experimental Parallel
		 */
		boolean multiThread=true;
		
		if(multiThread) {
			List<TripsCreatorFromPlan> threadrun=new ArrayList<>();
			List<List<Person>> personList=Lists.partition(new ArrayList<Person>(population.getPersons().values()), 
					(int)(population.getPersons().values().size()/16)); //Divide the population by 16. Why 16?
			Thread[] threads=new Thread[personList.size()];
			for(int i=0;i<personList.size();i++) {
				threadrun.add(new TripsCreatorFromPlan(personList.get(i),this));
				threadrun.get(i).setPersonsAttributes(population.getPersonAttributes());
				threads[i]=new Thread(threadrun.get(i));
			}
			for(int i=0;i<personList.size();i++) {
				threads[i].start();
			}

			for(int i=0;i<personList.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for(TripsCreatorFromPlan t : threadrun) {
				trips.addAll((ArrayList<Trip>)t.getTrips());
			}
		}else {
			for (Id<Person> personId:population.getPersons().keySet()){
				TripChain tripchain=this.getNewTripChain(population.getPersons().get(personId).getSelectedPlan());
				String s=(String) population.getPersonAttributes().getAttribute(personId.toString(), "SUBPOP_ATTRIB_NAME");
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
				}
				trips.addAll( tripchain.getTrips());
			}
		}
		//double tripsWithoutRoute=0;
		for (Trip trip:trips){
			double pcu=1;
			Vehicle v=this.vehicles.getVehicles().get(Id.createVehicleId(trip.getPersonId().toString()));
			if(v!=null) {
				pcu=v.getType().getPcuEquivalents();
			}
			trip.setCarPCU(pcu);
			if(!trip.getStartLinkId().toString().equals(trip.getEndLinkId().toString())) { //We ignore all trips with same OD.
				Id<AnalyticalModelODpair> ODId = trip.generateODpairId(odNetwork);
				if (ODpairset.containsKey(ODId)){
					ODpairset.get(ODId).addtripWithoutRoute(trip);
				}else{
					AnalyticalModelODpair odpair=this.getNewODPair( trip.getOriginNode(), trip.getDestinationNode(), 
							network, this.timeBins, trip.getSubPopulationName());
					odpair.addtripWithoutRoute(trip);
					ODpairset.put(ODId, odpair);
				}
			}
//			}else {
//				if(!trip.getMode().equals("transit_walk")) {
//					//throw new IllegalArgumentException("WAit");
//				}
//				tripsWithoutRoute++;
//			}
		}
////		Map<Id<AnalyticalModelODpair>,AnalyticalModelODpair> sudoso=new HashMap<>(this.ODpairset);
////		int sameLinkOD=0;
////		for(AnalyticalModelODpair odPair:sudoso.values()) {
////			if(odPair.getStartLinkIds().size()==1 && odPair.getEndLinkIds().size()==1 && odPair.getStartLinkIds().keySet().toArray()[0].toString().equals(odPair.getEndLinkIds().keySet().toArray()[0].toString())) {
////				this.ODpairset.remove(odPair.getODpairId());
////				sameLinkOD++;
////				System.out.println();
////			}
////			
////		}
//		System.out.println("No of same link ODs = "+sameLinkOD);
		//System.out.println("no of trips withoutRoutes = "+tripsWithoutRoute);
	}
	
}



class TripsCreatorFromPlan implements Runnable {
	private List<Person> Persons;
	AnalyticalModelODpairs odPairs;
	private ObjectAttributes personsAttributes=null;
	private ArrayList<Trip> trips=new ArrayList<>();
	public TripsCreatorFromPlan(List<Person> persons,AnalyticalModelODpairs odPairs) {
		this.Persons=persons;
		this.odPairs=odPairs;
	}
	
	public ObjectAttributes getPersonsAttributes() {
		return personsAttributes;
	}

	public void setPersonsAttributes(ObjectAttributes personsAttributes) {
		this.personsAttributes = personsAttributes;
	}
	
	@Override
	public void run() {
		if(personsAttributes==null) { //if there is no subpopulation
			for(Person p:this.Persons) {
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				trips.addAll( tripchain.getTrips());
			}
		}else {
			for(Person p:this.Persons) { //if there is subpopulation
				TripChain tripchain=this.odPairs.getNewTripChain(p.getSelectedPlan());
				String s=(String) this.personsAttributes.getAttribute(p.getId().toString(), "SUBPOP_ATTRIB_NAME");
				double pcu=this.odPairs.getVehicles().getVehicles().get(Id.createVehicleId(p.getId().toString())).getType().getPcuEquivalents();
				for(Trip t:(ArrayList<Trip>)tripchain.getTrips()) {
					t.setSubPopulationName(s);
					t.setCarPCU(pcu);
				}
				//TODO Get the actual mode default at that trp
				trips.addAll(tripchain.getTrips());
			}
			
		}
	}
	
	public ArrayList<Trip> getTrips(){
		return this.trips;
	}
	
	
}
