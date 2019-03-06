package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpairs;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.InternalParamCalibratorFunction;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.shortestpath.L2lLeastCostCalculatorFactory;
import ust.hk.praisehk.shortestpath.SignalFlowReductionGenerator;


public class CNLSUEModel implements AnalyticalModel{
	/**
	 * This is a simple and upgraded version of the SUE. 
	 * With better Modularity.
	 * 
	 * 
	 * The model is very simplified. 
	 * As link performance still BPR will be used. 
	 * 
	 * TODO:Fixing the alpha and beta will require special thinking.
	 * One meta-model calibration style can be used to fix 
	 * 
	 */
	
		protected final Logger logger=Logger.getLogger(CNLSUEModel.class);
		private String fileLoc="traget/";
		public String getFileLoc() {
			return fileLoc;
		}

		public void setFileLoc(String fileLoc) {
			this.fileLoc = fileLoc;
		}

		protected Map<String,Double> consecutiveSUEErrorIncrease=new ConcurrentHashMap<>();
		private LinkedHashMap<String,Double> AnalyticalModelInternalParams=new LinkedHashMap<>();
		protected LinkedHashMap<String,Double> Params=new LinkedHashMap<>();
		private LinkedHashMap<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new LinkedHashMap<>();
		
		
		private double alphaMSA=1.9;//parameter for decreasing MSA step size
		private double gammaMSA=.1;//parameter for decreasing MSA step size
		
		//other Parameters for the Calibration Process
		private double tollerance=0.01;
		private double tolleranceLink=0.1;
		//user input
	
		protected Map<String, Tuple<Double,Double>> timeBeans;
		
		//MATSim Input
		protected Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
		protected Network originalNetwork;
		private TransitSchedule ts;
		protected Scenario scenario;
		@Deprecated private Population population;
		protected Map<String,FareCalculator> fareCalculator=new HashMap<>();
		
		//Used Containers
		protected Map<String,ArrayList<Double>> beta=new ConcurrentHashMap<>(); //This is related to weighted MSA of the SUE
		protected Map<String,ArrayList<Double>> error=new ConcurrentHashMap<>();
		protected Map<String,ArrayList<Double>> error1=new ConcurrentHashMap<>();//This is related to weighted MSA of the SUE
		
		//TimebeanId vs demands map
		protected Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> demand=new ConcurrentHashMap<>();//Holds ODpair based demand
		protected Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> originalDemand=new ConcurrentHashMap<>();//This will hold the original demand and will not be modified the demand on the other hand can be modified for incremental loading
		protected Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> carDemand=new ConcurrentHashMap<>(); 
		protected CNLODpairs odPairs;
		protected Map<String,Map<Id<TransitLink>,TransitLink>> transitLinks = new ConcurrentHashMap<>();
			
		@Deprecated protected Population lastPopulation;
	
	
		//All the parameters name
		//They are kept public to make it easily accessible as they are final they can not be modified
		public static final String BPRalphaName="BPRalpha";
		public static final String BPRbetaName="BPRbeta";
		public static final String LinkMiuName="LinkMiu";
		public static final String ModeMiuName="ModeMiu";
		public static final String TransferalphaName="Transferalpha";
		public static final String TransferbetaName="Transferbeta";

		
	public CNLSUEModel(Map<String, Tuple<Double, Double>> timeBean) {
		this.timeBeans=timeBean;
		this.defaultParameterInitiation(null);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.demand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.carDemand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			this.beta.put(timeBeanId, new ArrayList<Double>());
			this.error.put(timeBeanId, new ArrayList<Double>());
			this.error1.put(timeBeanId, new ArrayList<Double>());
			
		}
		logger.info("Analytical model created successfully.");
		
	}
	
	public CNLSUEModel(Config config,Map<String, Tuple<Double, Double>> timeBean) {
		this.timeBeans=timeBean;
		this.defaultParameterInitiation(config);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.demand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.carDemand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			this.beta.put(timeBeanId, new ArrayList<Double>());
			this.error.put(timeBeanId, new ArrayList<Double>());
			this.error1.put(timeBeanId, new ArrayList<Double>());
			
		}
		logger.info("Analytical model created successfully.");
		
	}
	
	/**
	 * This method loads default values to all the parameters 
	 * Including the internal parameters
	 */
	private void defaultParameterInitiation(Config config){
		//Loads the Internal default parameters 
		
		this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 0.008);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.ModeMiuName, 0.01);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRalphaName, 0.15);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.BPRbetaName, 4.);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferalphaName, 0.5);
		this.AnalyticalModelInternalParams.put(CNLSUEModel.TransferbetaName, 1.);
		this.loadAnalyticalModelInternalPamamsLimit();
		
		//Loads the External default Parameters
		if(config==null) {
			config=ConfigUtils.createConfig();
		}
		

		this.Params.put(CNLSUEModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
		this.Params.put(CNLSUEModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
		this.Params.put(CNLSUEModel.MarginalUtilityofTravelptName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.MarginalUtilityOfDistancePtName, config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfDistance());
		this.Params.put(CNLSUEModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
		this.Params.put(CNLSUEModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
		this.Params.put(CNLSUEModel.MarginalUtilityOfWalkingName, config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
		this.Params.put(CNLSUEModel.DistanceBasedMoneyCostWalkName, config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
		this.Params.put(CNLSUEModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
		this.Params.put(CNLSUEModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
		this.Params.put(CNLSUEModel.MarginalUtilityofPerformName, config.planCalcScore().getPerforming_utils_hr());
		this.Params.put(CNLSUEModel.CapacityMultiplierName, 1.0);
	}
	
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		for(String s:params.keySet()) {
			this.Params.put(s, params.get(s));
		}
	}
	
	
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.LinkMiuName, new Tuple<Double,Double>(0.0075,0.25));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.ModeMiuName, new Tuple<Double,Double>(0.01,0.5));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRalphaName, new Tuple<Double,Double>(0.10,0.20));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.BPRbetaName, new Tuple<Double,Double>(3.,5.));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferalphaName, new Tuple<Double,Double>(0.25,0.75));
		this.AnalyticalModelParamsLimit.put(CNLSUEModel.TransferbetaName, new Tuple<Double,Double>(0.75,1.5));
	}
	
	
		
	/**
	 * This method overlays transit vehicles on the road network
	 * @param network
	 * @param Schedule
	 */
	public void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles,double fromTime, double toTime) {
		for(TransitLine tl:schedule.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				ArrayList<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
				for(Departure d:tr.getDepartures().values()) {
					if(d.getDepartureTime()>fromTime && d.getDepartureTime()<=toTime) {
						for(Id<Link> linkId:links) {
							((CNLLink)network.getLinks().get(linkId)).addLinkTransitVolume(vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents());
							
							}
					}
				}
			}
		}
		logger.info("Completed transit vehicle overlay.");
	}
	
	protected void printDemandTotalAndAgentTripStat() {
		int agentTrip=0;
		//int matsimTrip=0;
		int agentDemand=0;
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()) {
			agentTrip+=odPair.getAgentCounter();
			for(String s:odPair.getTimeBean().keySet()) {
				agentDemand+=odPair.getDemand().get(s);
			}
			
		}
		logger.info("Demand total = "+agentDemand);
		logger.info("Total Agent Trips = "+agentTrip);
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		//this.setLastPopulation(population);
		//System.out.println("");
		this.odPairs = new CNLODpairs(network, transitSchedule, scenario,this.timeBeans);
		this.getOdPairs().generateODpairset(population);
		this.getOdPairs().generateRouteandLinkIncidence(0.);
		this.originalNetwork=network;
		for(String s:this.timeBeans.keySet()) {
			this.networks.put(s, new CNLNetwork(network));
			this.performTransitVehicleOverlay(this.networks.get(s),
					transitSchedule,scenario.getTransitVehicles(),this.timeBeans.get(s).getFirst(),
					this.timeBeans.get(s).getSecond());
			this.transitLinks.put(s,this.getOdPairs().getTransitLinks(this.timeBeans,s));
		}
		this.fareCalculator=fareCalculator;
		
		
		this.carDemand.size();
		
		this.setTs(transitSchedule);
		for(String timeBeanId:this.timeBeans.keySet()) { //For every time bean
			this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
			Map<Id<AnalyticalModelODpair>, Double> odDemandMap = this.getOdPairs().getDemand(timeBeanId); //TODO: A little bit strange
			this.originalDemand.put(timeBeanId, new HashMap<>(odDemandMap)); //Add a hashMap for the time bean
			for(Id<AnalyticalModelODpair> odId : odDemandMap.keySet()) {
				double totalDemand = odDemandMap.get(odId);
				if(this.odPairs.getODpairset().get(odId).getTrRoutes().isEmpty()) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}else {
					this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
			
				}
			}
			logger.info("Startig from 0.5 auto and transit ratio");
			if(this.demand.get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		
		printDemandTotalAndAgentTripStat();
	}
	
	public void generateRoutesAndODWithoutRoute(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		//this.setLastPopulation(population);
		//System.out.println("");
		this.scenario=scenario;
		this.odPairs = new CNLODpairs(scenario,this.timeBeans);
		this.getOdPairs().generateODpairsetWithoutRoutes(population);
		this.originalNetwork=network;
		SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
		//this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String s:this.timeBeans.keySet()) {
			
			this.networks.put(s, new CNLNetwork(network));
			this.networks.get(s).updateGCRatio(sg);
			this.performTransitVehicleOverlay(this.networks.get(s),
					transitSchedule,scenario.getTransitVehicles(),this.timeBeans.get(s).getFirst(),
					this.timeBeans.get(s).getSecond());
			this.transitLinks.put(s,this.getOdPairs().getTransitLinks(this.timeBeans,s));
		}
		this.generateRoute();
		this.odPairs.generateRouteandLinkIncidence(0);
		this.fareCalculator=fareCalculator;
		
		this.carDemand.size();
		
		this.setTs(transitSchedule);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
			this.originalDemand.put(timeBeanId, new HashMap<>(this.getOdPairs().getDemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.originalDemand.get(timeBeanId).keySet()) {
				double totalDemand=this.originalDemand.get(timeBeanId).get(odId);
				if(this.odPairs.getODpairset().get(odId).getTrRoutes().isEmpty()) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}else {
					this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
			
				}
			}
			logger.info("Startig from 0.5 auto and transit ratio");
			if(this.originalDemand.get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		printDemandTotalAndAgentTripStat();
	}
	
	/**
	 * This method has three part.
	 * 
	 * The parameter inputed must be in ParamName-Value format.
	 * The paramter name should include only the parameters that are present int the default Param
	 * 
	 * 1. Modal Split.
	 * 2. SUE assignment.
	 * 3. SUE Transit Assignment. 
	 */
	
	@Override
	public Map<String,Map<Id<Link>, Double>> perFormSUE(LinkedHashMap<String, Double> params) {
		if(!(this.Params.keySet()).containsAll(params.keySet())) {
			logger.error("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
			throw new IllegalArgumentException("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
		}
		
		return this.perFormSUE(params, this.AnalyticalModelInternalParams);
	}
	
	/**
	 * This variation is for enoch
	 * This will not perform sue only, rather genrate routeset close to matsim
	 * The function will deploy a shortest path algorithm based on the output of the previous algorithm
	 * THe pt mode ratio is between 0 to 1 
	 */
	public AnalyticalModelODpairs generateMATSimRoutes(double defaultPtModeRatio, int numberOfIterations, int numOfRoutes) {
		LinkedHashMap<String,Double> params=new LinkedHashMap<>(this.Params);
		LinkedHashMap<String,Double> inputParams=new LinkedHashMap<>(params);
		LinkedHashMap<String,Double> inputAnaParams=new LinkedHashMap<>(this.AnalyticalModelInternalParams);
		Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
		//this loop is for generating routeset
		for(int j=0; j < numberOfIterations;j++) {
			
			//Creating different threads for different time beans
			double percentage=1;
			if(j<10) {
				percentage=Math.sqrt((20*(j+1)-(j+1)*(j+1))/100.);
			}
			this.loadDemand(percentage, 1-defaultPtModeRatio);
			for(String timeBeans : this.timeBeans.keySet()) {
				this.applyModalSplit(inputParams, inputAnaParams, timeBeans, defaultPtModeRatio);
			}
			Thread[] threads=new Thread[this.timeBeans.size()];
			int i=0;
			for(String timeBeanId:this.timeBeans.keySet()) {
				threads[i]=new Thread(new SUERunnableEnoch(this,timeBeanId,inputParams,inputAnaParams,defaultPtModeRatio),timeBeanId);
				i++;
				outputLinkFlow.put(timeBeanId, new HashMap<Id<Link>, Double>());
			}
			//Starting the Threads
			for(i=0;i<this.timeBeans.size();i++) {
				threads[i].start();
			}
	
			//joining the threads
			for(i=0;i<this.timeBeans.size();i++) {
				try {
					threads[i].join();
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			for(String timeBeanId:this.timeBeans.keySet()) {
				for(Id<Link> linkId:this.networks.get(timeBeanId).getLinks().keySet()) {
					outputLinkFlow.get(timeBeanId).put(linkId, 
							((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume());
				}
			
			}
			if(j < numberOfIterations - 1) { //Not in the last iteration.
				this.odPairs.deleteCarRoutes(numOfRoutes); //Delete some obsolete routes.
				this.generateRoute(); //Create a new route
				this.odPairs.generateRouteandLinkIncidence(0); //Reset the route sets
			}
		}
		return this.odPairs;
	}
	
	/**
	 * This is the same method and does the same task as perform SUE, but takes the internal Parameters as an input too.
	 * This will be used for the internal parameters calibration internally
	 * @param params
	 * @return
	 */
	@Override
	public Map<String,Map<Id<Link>, Double>> perFormSUE(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams) {
		this.resetCarDemand();
		
		LinkedHashMap<String,Double> inputParams=new LinkedHashMap<>(params);
		LinkedHashMap<String,Double> inputAnaParams=new LinkedHashMap<>(anaParams);
		//Loading missing parameters from the default values		
		Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
		
		//Checking and updating for the parameters 
		for(Entry<String,Double> e:this.Params.entrySet()) {
			if(!params.containsKey(e.getKey())) {
				params.put(e.getKey(), e.getValue());
			}
		}
		
		//Checking and updating for the analytical model parameters
		for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
			if(!anaParams.containsKey(e.getKey())) {
				anaParams.put(e.getKey(), e.getValue());
			}
		}
		
		//Creating different threads for different time beans
		Thread[] threads=new Thread[this.timeBeans.size()];
		int i=0;
		for(String timeBeanId:this.timeBeans.keySet()) {
			threads[i]=new Thread(new SUERunnable(this,timeBeanId,params,anaParams),timeBeanId);
			i++;
			outputLinkFlow.put(timeBeanId, new HashMap<Id<Link>, Double>());
		}
		//Starting the Threads
		for(i=0;i<this.timeBeans.size();i++) {
			threads[i].start();
		}
		
		//joining the threads
		for(i=0;i<this.timeBeans.size();i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		
		//Collecting the Link Flows
		for(String timeBeanId:this.timeBeans.keySet()) {
			for(Id<Link> linkId:this.networks.get(timeBeanId).getLinks().keySet()) {
				outputLinkFlow.get(timeBeanId).put(linkId, 
						((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume());
			}
		}
		//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
		return outputLinkFlow;
	}
	
	/**
	 * Given an OD pair, and respective start and end link IDs, find the appropriate route.
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
	 * This is a method written by Enoch to assign the current routes available to match MATSim population
	 * It assumes only one plan is there.
	 * @param population
	 */
	public void assignRoutesToMATSimPopulation(Population population) {
		int assignedCount = 0;
		//What we have:
		Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> odPairSet = this.odPairs.getODpairset(); //Set of OD pair
		
		for(AnalyticalModelODpair odPair: odPairSet.values()) {
			List<Id<Person>> personIdsConcerning = odPair.getPersonIds();
			for(Id<Person> personId: personIdsConcerning) {
				Plan p = population.getPersons().get(personId).getSelectedPlan();
				Coord lastCoord = null; //Last coordinate of activity
				Id<Link> lastLinkId = null; //Last linkId of activity
				Leg lastLeg = null; //Last leg
				String lastLegtimeBin = null; //Last time bin of leg.
				for(PlanElement pe: p.getPlanElements()) {
					if(pe instanceof Activity) {
						Coord coord = ((Activity) pe).getCoord();
						Id<Link> linkId =  ((Activity) pe).getLinkId();
						//Try to assign the route for last Leg
						if(lastCoord != null) {
							NetworkRoute routeFound = (NetworkRoute) findAppropriateRoute(odPair, lastLinkId, linkId, lastLegtimeBin);
							if(routeFound != null && lastLeg.getMode().equals("car")) {
								routeFound = RouteUtils.createLinkNetworkRouteImpl(routeFound.getStartLinkId(), routeFound.getLinkIds(), 
										routeFound.getEndLinkId()); //We copy a route
								lastLeg.setRoute(routeFound);
								assignedCount++;
							}
						}
						lastCoord = coord;
						lastLinkId = linkId;
						lastLegtimeBin =  odPair.getTimeBean(((Activity) pe).getEndTime());
					}
					if(pe instanceof Leg) {
						lastLeg = (Leg) pe;
					}
				}
			}
		}
		//Step 2:
		logger.info("Number of trip assigned route : "+assignedCount);
	}
	
	
	public Map<String, FareCalculator> getFareCalculator() {
		return fareCalculator;
	}

	public void setFareCalculator(Map<String, FareCalculator> farecalc) {
		this.fareCalculator = farecalc;
	}
	public void setMSAAlpha(double alpha) {
		this.alphaMSA = alpha;
	}
	public void setMSAGamma(double gamma) {
		this.gammaMSA = gamma;
	}
	public void setTollerance(double tollerance) {
		this.tollerance = tollerance;
	}
	/**
	 * This method resets all the car demand 
	 */
	private void resetCarDemand() {
		for(String timeId:this.timeBeans.keySet()) {
			this.carDemand.put(timeId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			for(Id<AnalyticalModelODpair> o : this.demand.get(timeId).keySet()) {
				this.carDemand.get(timeId).put(o, this.demand.get(timeId).get(o)*0.5);
			}

		}
	}
	/**
	 * This method does single OD network loading of only car demand.
	 * 
	 * @param ODpairId
	 * @param anaParams 
	 * @return Return the link flows
	 */
	
	protected HashMap<Id<Link>,Double> networkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId, String timeBeanId, double iteration,
			LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		
		AnalyticalModelODpair odpair=this.getOdPairs().getODpairset().get(ODpairId);
		List<AnalyticalModelRoute> routes=odpair.getRoutes();
		HashMap<Id<AnalyticalModelRoute>,Double> routeFlows=new HashMap<>();
		double totalUtility=0;
		
		//Calculating route utility for all car routes inside one OD pair.
		HashMap<Id<AnalyticalModelRoute>,Double> oldUtility=new HashMap<>();
		HashMap<Id<AnalyticalModelRoute>,Double> newUtility=new HashMap<>();
		for(AnalyticalModelRoute route : routes){
			double utility=0;
			
			if(iteration>1) { //We only calculate the utility for all
				utility = route.calcRouteUtility(params, anaParams,this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId));
				newUtility.put(route.getRouteId(), utility);
				oldUtility.put(route.getRouteId(), odpair.getRouteUtility(timeBeanId).get(route.getRouteId()));
			}else {
				utility = 0; //TODO: See if it is appropriate
			}
			//oldUtility.put(r.getRouteId(),this.odPairs.getODpairset().get(ODpairId).getRouteUtility(timeBeanId).get(r.getRouteId()));
			odpair.updateRouteUtility(route.getRouteId(), utility, timeBeanId);
			
			//This Check is to make sure the exp(utility) do not go to infinity.
			if(utility>300||utility<-300) {
				logger.error("utility is either too small or too large. Increase or decrease the link miu accordingly. The utility is "+utility+" for route "+route.getRouteId());
				throw new IllegalArgumentException("stop!!!");
			}
			totalUtility+=Math.exp(utility);
		}
//		if(routes.size()>1 && counter>2 ) {
//			//&& (error.get(timeBeanId).get((int)(counter-2))>error.get(timeBeanId).get((int)(counter-3)))
//			System.out.println("Testing!!!");
//			for(CNLRoute r:routes) {
//				double diff=(newUtility.get(r.getRouteId())-oldUtility.get(r.getRouteId()));
//				if(Math.pow(diff,2)>0.00002){
//					
//					System.out.println(diff);
//				}
//			}
//		}
		//If total utility is zero, then there should not be any route. For testing purpose, can be removed later 
		if(totalUtility==0) {
			logger.error("utility is zero. Please check.");
			throw new IllegalArgumentException("Stop!!!!");
		}
		
		
		//This is the route flow split
		double totalODdemand = this.carDemand.get(timeBeanId).get(ODpairId);
		double totalFlow = 0;
		for(AnalyticalModelRoute route : routes){
			double utility = Math.exp(odpair.getRouteUtility(timeBeanId).get(route.getRouteId()));
			double flow = utility / totalUtility * totalODdemand;
			//For testing purpose, can be removed later
			if(flow==Double.NaN||flow==Double.POSITIVE_INFINITY) {
				logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
				throw new IllegalArgumentException("Wait!!!!Error!!!!");
			}
			routeFlows.put(route.getRouteId(),flow);
			totalFlow+=flow;
		}
		assert(totalODdemand==totalFlow);
		
		//Store the link flows
		HashMap<Id<Link>,Double> linkFlows=new HashMap<>();
		for(Id<Link> linkId : odpair.getLinkIncidence().keySet()){ //For each linkId
			double linkflow=0;
			for(AnalyticalModelRoute route : odpair.getLinkIncidence().get(linkId)){
				linkflow+=routeFlows.get(route.getRouteId()); //Add the flow
			}
			linkFlows.put(linkId, linkflow);
			if(linkflow==Double.NaN) {
				throw new IllegalArgumentException("link Flow is null !!!!");
			}
		}
		return linkFlows;
	}
	
	
	/**
	 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
	 * @param ODpairId
	 * @param timeBeanId
	 * @param anaParams 
	 * @return
	 */
	protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		List<AnalyticalModelTransitRoute> routes=this.getOdPairs().getODpairset().get(ODpairId).getTrRoutes(this.timeBeans,timeBeanId);
		
		HashMap<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
		HashMap<Id<TransitLink>,Double> linkFlows=new HashMap<>();
		
		
		
		double totalUtility=0;
		if(routes!=null && routes.size()!=0) {
		for(AnalyticalModelTransitRoute r:routes){
			double u=0;
			if(counter>1) {
				u=r.calcRouteUtility(params, anaParams,
					this.networks.get(timeBeanId),this.fareCalculator,this.timeBeans.get(timeBeanId));
				
				if(u==Double.NaN) {
					logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
					throw new IllegalArgumentException("Utility is NAN!!!");
				}
			}else {
				u=0;
			}
			if(u>300) {
				logger.warn("STOP!!!Utility is too large >300");
			}
			this.getOdPairs().getODpairset().get(ODpairId).updateTrRouteUtility(r.getTrRouteId(), u,timeBeanId);
			totalUtility+=Math.exp(u);
		}
		if(totalUtility==0) {
			logger.warn("STopp!!!! Total utility in the OD pair is zero. This can happen if there is no transit route in that OD pair.");
		}
		for(AnalyticalModelTransitRoute r:routes){
			double totalDemand=this.demand.get(timeBeanId).get(ODpairId);
			double carDemand=this.getCarDemand().get(timeBeanId).get(ODpairId);
			double q=(totalDemand-carDemand);
			if(q<0) {
				throw new IllegalArgumentException("Stop!!! transit demand is negative!!!");
			}
			double utility=this.getOdPairs().getODpairset().get(ODpairId).getTrRouteUtility(timeBeanId).
					get(r.getTrRouteId());
			double flow=q*Math.exp(utility)/totalUtility;
			if(Double.isNaN(flow)||flow==Double.POSITIVE_INFINITY||flow==Double.NEGATIVE_INFINITY) {
				logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
				throw new IllegalArgumentException("Error!!!!");
			}
			routeFlows.put(r.getTrRouteId(),flow);
					
		}

		}
		
		Set<Id<TransitLink>>linksets=getOdPairs().getODpairset().get(ODpairId).getTrLinkIncidence().keySet();
		for(Id<TransitLink> linkId:linksets){
			if(this.transitLinks.get(timeBeanId).containsKey(linkId)) {
			double linkflow=0;
			ArrayList<AnalyticalModelTransitRoute>incidence=getOdPairs().getODpairset().get(ODpairId).getTrLinkIncidence().get(linkId);
			for(AnalyticalModelTransitRoute r:incidence){
				ArrayList<AnalyticalModelTransitRoute> routesFromOd=this.getOdPairs().getODpairset().get(ODpairId).getTrRoutes(this.timeBeans,timeBeanId);
				
				if(CNLSUEModel.routeContain(routesFromOd, r)) {
				linkflow+=routeFlows.get(r.getTrRouteId());
				}
				if(Double.isNaN(linkflow)) {
					logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
					throw new IllegalArgumentException("Stop!!!");
				}
			}
			linkFlows.put(linkId,linkflow);
			}
		}
		return linkFlows;
	}
	
	
	private static boolean routeContain(ArrayList<AnalyticalModelTransitRoute> routeList,AnalyticalModelTransitRoute route) {
		
		for(AnalyticalModelTransitRoute r:routeList) {
			if(r.getTrRouteId().equals(route.getTrRouteId())) {
				return true;
			}
		}
		return false;
	}
	/**
	 * This method should do the network loading for car
	 * @param anaParams 
	 * @return
	 */
	protected HashMap<Id<Link>,Double> performCarNetworkLoading(String timeBeanId, double iteration, LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		HashMap<Id<Link>,Double> linkVolume=new HashMap<>();
		for(Id<AnalyticalModelODpair> odpairId:this.getOdPairs().getODpairset().keySet()){
			
			if(this.getOdPairs().getODpairset().get(odpairId).getRoutes()!=null && this.getCarDemand().get(timeBeanId).get(odpairId)!=0) {
				HashMap<Id<Link>,Double> oDlinkFlowVolume = this.networkLoadingCarSingleOD(odpairId, timeBeanId, iteration, params, anaParams);
				for(Id<Link>linkId : oDlinkFlowVolume.keySet()){
					if(linkVolume.containsKey(linkId)){
						linkVolume.put(linkId, linkVolume.get(linkId) + oDlinkFlowVolume.get(linkId));
					}else{
						linkVolume.put(linkId, oDlinkFlowVolume.get(linkId));
					}
				}
			}
		}
		return linkVolume;
	}
	
	/**
	 * This method should do the network loading for transit
	 * @param params 
	 * @param anaParams 
	 * @return
	 */
	protected HashMap<Id<TransitLink>,Double> performTransitNetworkLoading(String timeBeanId,int counter, LinkedHashMap<String, Double> params, LinkedHashMap<String, Double> anaParams){
		HashMap<Id<TransitLink>,Double> linkVolume=new HashMap<>();
		for(Id<AnalyticalModelODpair> odpairId:this.getOdPairs().getODpairset().keySet()){
			double totalDemand=this.demand.get(timeBeanId).get(odpairId);
			double carDemand=this.getCarDemand().get(timeBeanId).get(odpairId);
			if((totalDemand-carDemand)!=0) {
				HashMap <Id<TransitLink>,Double> ODvolume=this.NetworkLoadingTransitSingleOD(odpairId,timeBeanId,counter,params,anaParams);
				for(Id<TransitLink> linkId:ODvolume.keySet()){
					if(linkVolume.containsKey(linkId)){
						linkVolume.put(linkId, linkVolume.get(linkId)+ODvolume.get(linkId));
					}else{
						linkVolume.put(linkId, ODvolume.get(linkId));
					}
				}
			}
		}
		//System.out.println(linkVolume.size());
		return linkVolume;
	}
	
	
	/**
	 * This method updates the linkCarVolume and linkTransitVolume obtained using MSA 
	 * @param linkVolume - Calculated link volume
	 * @param transitlinkVolume - Calculated transit volume
	 * @param counter - current counter in MSA loop
	 * @param timeBeanId - the specific time Bean Id for which the SUE is performed
	 */

	@SuppressWarnings("unchecked")
	protected boolean UpdateLinkVolume(HashMap<Id<Link>,Double> linkVolume,HashMap<Id<TransitLink>,Double> transitlinkVolume,int counter,String timeBeanId){
		double squareSum=0;
		double flowSum=0;
		double linkSum=0;
		if(counter==1) {
			this.beta.get(timeBeanId).clear();
			//this.error.clear();
			this.beta.get(timeBeanId).add(1.);
		}else {
			if(error.get(timeBeanId).get(counter-1)<error.get(timeBeanId).get(counter-2)) {
				beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.gammaMSA);
			}else {
				this.consecutiveSUEErrorIncrease.put(timeBeanId, this.consecutiveSUEErrorIncrease.get(timeBeanId)+1);
				beta.get(timeBeanId).add(beta.get(timeBeanId).get(counter-2)+this.alphaMSA);
				
			}
		}
		
		for(Id<Link> linkId:linkVolume.keySet()){
			double newVolume=linkVolume.get(linkId);
			double oldVolume=((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkCarVolume();
			flowSum+=oldVolume;
			double update;
			double counterPart=1/beta.get(timeBeanId).get(counter-1);
			//counterPart=1./counter;
			update=counterPart*(newVolume-oldVolume);
			if(oldVolume!=0) {
				if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
					linkSum+=1;
				}
			}
			squareSum+=update*update;
			((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).addLinkCarVolume(update);
		}
		for(Id<TransitLink> trlinkId:transitlinkVolume.keySet()){
			//System.out.println("testing");
			double newVolume=transitlinkVolume.get(trlinkId);
			TransitLink trl=this.transitLinks.get(timeBeanId).get(trlinkId);
			double oldVolume=trl.getPassangerCount();
			double update;
			double counterPart=1/beta.get(timeBeanId).get(counter-1);
			
			update=counterPart*(newVolume-oldVolume);
			if(oldVolume!=0) {
				if(Math.abs(update)/oldVolume*100>this.tolleranceLink) {
					linkSum+=1;
				}
				
			}
			squareSum+=update*update;
			this.transitLinks.get(timeBeanId).get(trlinkId).addPassanger(update,this.networks.get(timeBeanId));
		
		}
		squareSum=Math.sqrt(squareSum);
		if(counter==1) {
			this.error1.get(timeBeanId).clear();
		}
		error1.get(timeBeanId).add(squareSum);
		
		if(squareSum<this.getTollerance()) {
			return true;
			
		}else {
			return false;
		}
	}
	
	/**
	 * This method will check for the convergence and also create the error term required for MSA
	 * @param linkVolume
	 * @param tollerance
	 * @return
	 */
	protected boolean CheckConvergence(HashMap<Id<Link>,Double> linkVolume,HashMap<Id<TransitLink>,Double> transitlinkVolume, double tollerance,String timeBeanId,int counter){
		
		double squareSum=0;
		double sum=0;
		double error=0;
		for(Id<Link> linkid:linkVolume.keySet()){
			if(linkVolume.get(linkid)==0) {
				error=0;
			}else {
				double currentVolume=((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkid)).getLinkCarVolume();
				double newVolume=linkVolume.get(linkid);
				error=Math.pow((currentVolume-newVolume),2);
				if(error==Double.POSITIVE_INFINITY||error==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("Error is infinity!!!");
				}
				if(error/newVolume*100>tollerance) {					
					sum+=1;
				}
			}
			if(error==Double.NaN){
				throw new IllegalArgumentException("error is NAN!!!! CHECK");
			}
			
			squareSum+=error;
			if(squareSum==Double.POSITIVE_INFINITY||squareSum==Double.NEGATIVE_INFINITY) {
				throw new IllegalArgumentException("error is infinity!!!");
			}
		}
		for(Id<TransitLink> transitlinkid:transitlinkVolume.keySet()){
			if(transitlinkVolume.get(transitlinkid)==0) {
				error=0;
			}else {
				double currentVolume=this.transitLinks.get(timeBeanId).get(transitlinkid).getPassangerCount();
				double newVolume=transitlinkVolume.get(transitlinkid);
				error=Math.pow((currentVolume-newVolume),2);
				if(error/newVolume*100>tollerance) {

					sum+=1;
				}
			}
			if(error==Double.NaN||error==Double.NEGATIVE_INFINITY) {
				throw new IllegalArgumentException("Stop!!! There is something wrong!!!");
			}
			squareSum+=error;
		}
		if(squareSum==Double.NaN) {
			System.out.println("WAIT!!!!Problem!!!!!");
		}
		squareSum=Math.sqrt(squareSum);
		if(counter==1) {
			this.error.get(timeBeanId).clear();
		}
		this.error.get(timeBeanId).add(squareSum);
		logger.info("ERROR amount for "+timeBeanId+" = "+squareSum);
		//System.out.println("in timeBean Id "+timeBeanId+" No of link not converged = "+sum);
		
//		try {
//			//CNLSUEModel.writeData(timeBeanId+","+counter+","+squareSum+","+sum, this.fileLoc+"ErrorData"+timeBeanId+".csv");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		if (squareSum<=this.getTollerance()||sum==0){
			return true;
		}else{
			return false;
		}
		
	}
	/**
	 * This method perform modal Split
	 * @param params
	 * @param anaParams
	 * @param timeBeanId
	 */
	protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,String timeBeanId) {
		double modeMiu=anaParams.get(CNLSUEModel.ModeMiuName);
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()){
			double demand=this.demand.get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) { 
				double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
				double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);
				
				if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
						Math.exp(transitUtility*modeMiu)==Double.POSITIVE_INFINITY) {
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.0);
					
				}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
						||Math.exp(carUtility*modeMiu)==Double.POSITIVE_INFINITY) {
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), this.demand.get(timeBeanId).get(odPair.getODpairId()));
				}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.);
				}else {
					double carProportion=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu));
					//System.out.println("Car Proportion = "+carProportion);
					Double cardemand=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu))*this.demand.get(timeBeanId).get(odPair.getODpairId());
					if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
						logger.error("Car Demand is invalid");
						throw new IllegalArgumentException("car demand is invalid");
					}
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}
			}
		}
	}
	
	/**
	 * This function is also just for Enoch's new usage
	 * @param timeBeanId
	 * @param defaultptModeshare
	 */
	protected void applyModalSplit(LinkedHashMap<String,Double> params, LinkedHashMap<String,Double> anaParams, String timeBeanId, double defaultptModeshare) {
		if(defaultptModeshare>=0 && defaultptModeshare<=100 ) {
			if(defaultptModeshare > 1) {
				defaultptModeshare = defaultptModeshare/100;
			}
		}else {
			throw new IllegalArgumentException("default pt mode ratio cannot be larger than 100");
		}
		
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()){
			double demand=this.demand.get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) {
				//double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
				if(!odPair.getODpairId().toString().contains("GV")) {
					Double cardemand=demand*(1-defaultptModeshare);
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}else {
					Double cardemand=demand;
					this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}
			}
		}
		
	}
	
	@Override
	public Map<Integer, Measurements> calibrateInternalParams(Map<Integer,Measurements> simMeasurements, 
			Map<Integer,LinkedHashMap<String,Double>>params, LinkedHashMap<String,Double> initialParam, int currentParamNo) {
		double[] x=new double[initialParam.size()];

		int j=0;
		for (double d:initialParam.values()) {
			x[j]=1;
			j++;
		}

		InternalParamCalibratorFunction iFunction=new InternalParamCalibratorFunction(simMeasurements,params,this,initialParam,currentParamNo);
		
		//Call the optimization subroutine
		CobylaExitStatus result = Cobyla.findMinimum(iFunction,x.length, x.length*2,
				x,20.,.05 ,3, 100);
		int i=0;
		for(String s:initialParam.keySet()) {
			this.AnalyticalModelInternalParams.put(s, initialParam.get(s)*(x[i]/100+1));
			i++;
		}
		return iFunction.getUpdatedAnaCount();
	}
	
	
	/**
	 * This function will save the original demand and load incrementally based on the input percentage given
	 * Percentage <=100
	 * initial car demand will control the default car demand ratio (recommended 50%)
	 * Will not affect if no transit route available 
	 */
	private void loadDemand(Double percentage, double initialCarDemandPercentage) {
		if(percentage>1) {
			throw new IllegalArgumentException("percentage cannot be greater than 1");
		}
		//this.originalDemand=new ConcurrentHashMap<>(this.demand); //Seems that it loads the originalDemand from the current demand
		for(String timeBean:this.demand.keySet()) {
			for(Id<AnalyticalModelODpair> odPairId:this.originalDemand.get(timeBean).keySet()) {
				double totalDemand = this.originalDemand.get(timeBean).get(odPairId)*percentage;
				if(this.getOdPairs().getODpairset().get(odPairId).getTrRoutes().isEmpty()) { //The case of no transit
					this.demand.get(timeBean).put(odPairId, totalDemand);
					this.carDemand.get(timeBean).put(odPairId, totalDemand);
				}else { //If there is transit
					this.demand.get(timeBean).put(odPairId, totalDemand);
					this.carDemand.get(timeBean).put(odPairId, totalDemand * initialCarDemandPercentage/100);
				}
				if(odPairId.toString().contains("GV")) { //If it is GV, just put all to the demand
					this.demand.get(timeBean).put(odPairId, totalDemand);
					this.carDemand.get(timeBean).put(odPairId, totalDemand);
				}
			}
		}
		
	}
	
	/**
	 * This method performs a Traffic Assignment of a single time Bean
	 * @param params: calibration Parameters
	 * @param anaParams: Analytical model Parameters
	 * @param timeBeanId
	 */
	public void singleTimeBeanTA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		HashMap<Id<TransitLink>, Double> linkTransitVolume;
		HashMap<Id<Link>,Double> linkCarVolume;
		boolean shouldStop=false;
		
		for(int i=1;i<500;i++) {
			//for(this.car)
			//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
		
			linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
			linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
			shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
			this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
			if(i==1 && shouldStop==true) {
				boolean demandEmpty=true;
				for(AnalyticalModelODpair od:this.getOdPairs().getODpairset().values()) {
					if(od.getDemand().get(timeBeanId)!=0) {
						demandEmpty=false;
						break;
					}
				}
				if(!demandEmpty) {
					System.out.println("The model cannot converge on first iteration!!!");
				}
			}
			if(shouldStop) {break;}
			this.performModalSplit(params, anaParams, timeBeanId);
			
		}
		
		
	}
	
/**
 * This function is for enoch's testing
 * @param params
 * @param anaParams
 * @param timeBeanId
 * @param defaultModeRatio: pt mode ratio in between 0 to 1
 */
	public void singleTimeBeanTA(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId,double defaultModeRatio) {
		HashMap<Id<TransitLink>, Double> linkTransitVolume;
		HashMap<Id<Link>,Double> linkCarVolume;
		boolean shouldStop=false;
		
		for(int i=1;i<500;i++) {
			
			//for(this.car)
			//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
			linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams); //Load the car network, and calculate utilities if it is not first iteration
			linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
			shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
			this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
			if(i==1 && shouldStop==true) {
				boolean demandEmpty=true;
				for(AnalyticalModelODpair od:this.getOdPairs().getODpairset().values()) {
					if(od.getDemand().get(timeBeanId)!=0) {
						demandEmpty=false;
						break;
					}
				}
				if(!demandEmpty) {
					System.out.println("The model cannot converge on first iteration!!!");
				}
			}
			if(shouldStop) {break;}
			this.applyModalSplit(params, anaParams, timeBeanId, defaultModeRatio);
			
		}
		
		
	}
	
	@Deprecated
	public void singleTimeBeanTAModeOut(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		HashMap<Integer,HashMap<Id<TransitLink>, Double>> linkTransitVolumeIteration;
		HashMap<Integer,HashMap<Id<Link>,Double>> linkCarVolumeIteration;
		
		
		HashMap<Id<TransitLink>, Double> linkTransitVolume;
		HashMap<Id<Link>,Double> linkCarVolume;
		
		boolean shouldStop=false;
		for(int j=0;j<1;j++) {
			for(int i=1;i<500;i++) {
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
				linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
				shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.tolleranceLink, timeBeanId,i);
				this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				if(shouldStop) {break;}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			this.performModalSplit(params, anaParams, timeBeanId);
		}
		
	}
	@Deprecated
	public void singleTimeBeanTAOut(LinkedHashMap<String, Double> params,LinkedHashMap<String,Double> anaParams,String timeBeanId) {
		HashMap<Id<TransitLink>, Double> linkTransitVolume=new HashMap<>();
		HashMap<Id<Link>,Double> linkCarVolume=new HashMap<>();
		boolean shouldStop=false;
		for(int j=0;j<1;j++) {
			
			for(int i=1;i<5000;i++) {
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
				this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
				shouldStop=this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				
				if(shouldStop) {
					break;
					}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			for(int i=1;i<1;i++) {
				linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
				shouldStop=this.CheckConvergence(linkCarVolume, linkTransitVolume, this.getTollerance(), timeBeanId,i);
				this.UpdateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				if(shouldStop) {break;}
			}
			this.performModalSplit(params, anaParams, timeBeanId);
		}
		
	}
	
	
	

	@Override
	public void clearLinkCarandTransitVolume() {
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.networks.get(timeBeanId).clearLinkVolumesfull();
			this.networks.get(timeBeanId).clearLinkTransitPassangerVolume();
			for(TransitLink trlink : this.transitLinks.get(timeBeanId).values()) {
				trlink.resetLink();
			}
		}
	}

	public LinkedHashMap<String, Double> getParams() {
		return Params;
	}

	public LinkedHashMap<String, Double> getAnalyticalModelInternalParams() {
		return AnalyticalModelInternalParams;
	}
	
	
	public static void writeData(String s , String fileLoc) throws IOException {
		File file=new File(fileLoc);
		FileWriter fw=new FileWriter(file,true);
		fw.append("\n");
		fw.append(s);
		
		fw.flush();
		fw.close();
	}

	public LinkedHashMap<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit() {
		return AnalyticalModelParamsLimit;
	}

	public Map<String, Tuple<Double, Double>> getTimeBeans() {
		return timeBeans;
	}
	
	@Override @Deprecated
	public Population getLastPopulation() {
		return lastPopulation;
	}

	@Deprecated
	public void setLastPopulation(Population lastPopulation) {
		this.lastPopulation = lastPopulation;
	}

	public AnalyticalModelODpairs getOdPairs() {
		return odPairs;
	}

	public Map <String,AnalyticalModelNetwork> getNetworks() {
		return networks;
	}

	
	@Deprecated
	public Map<String,Map<Id<TransitLink>,TransitLink>> getTransitLinks() {
		return transitLinks;
	}



	public TransitSchedule getTs() {
		return ts;
	}

	public void setTs(TransitSchedule ts) {
		this.ts = ts;
	}

//	public Map<String,Double> getConsecutiveSUEErrorIncrease() {
//		return consecutiveSUEErrorIncrease;
//	}
	
//	public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getDemand() {
//		return demand;
//	}

	public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getCarDemand() {
		return carDemand;
	}

	public double getTollerance() {
		return tollerance;
	}

	@Override
	public double getLinkTravelTime(Id<Link> linkId, double time) {
		String timeBean=null;
		for(String s:this.timeBeans.keySet()) {
			if(time==0) {time=1;}
			if(time>29*3600) {time=29*3600;}
			if(time>this.timeBeans.get(s).getFirst() && time<=this.timeBeans.get(s).getSecond()) {
				timeBean=s;
			}
		}
		return ((AnalyticalModelLink)this.networks.get(timeBean).getLinks().get(linkId)).getLinkTravelTime(this.timeBeans.get(timeBean), this.Params, this.AnalyticalModelInternalParams);
	}

	@Override
	public double getAverageLinkTravelTime(Id<Link> linkId) {
		double travelTime=0;
		for(String s:this.timeBeans.keySet()) {
			travelTime+= ((AnalyticalModelLink)this.networks.get(s).getLinks().get(linkId)).getLinkTravelTime(this.timeBeans.get(s), this.Params, this.AnalyticalModelInternalParams);
		}
		return travelTime/this.timeBeans.size();
	}

	/**
	 * This will for now generate the car paths
	 * The transit path generation will be added later as well
	 * This will signal the ODpairs to generate routes. This can be done to specific od pairs in future
	 * 
	 */
	public void generateRoute() {
		L2lLeastCostCalculatorFactory shortestPathCalculatorFactory=new L2lLeastCostCalculatorFactory(scenario, Sets.newHashSet(TransportMode.car), this);
		Boolean mthread=true;
		if(mthread==false) {
		
		for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()) {
				
				//double randStartTime=this.timeBeans.get(timeBean).getFirst()+Math.random()*(this.timeBeans.get(timeBean).getSecond()-this.timeBeans.get(timeBean).getFirst());
				Link startLink=null;
				Link endLink=null;
				
				Tuple<Id<Link>,Id<Link>> linkTuple=odPair.getStartAndEndLinkIds();
				if(linkTuple!=null) {
					startLink=this.originalNetwork.getLinks().get(linkTuple.getFirst());
					endLink=this.originalNetwork.getLinks().get(linkTuple.getSecond());
				}else {
					startLink=NetworkUtils.getNearestLink(this.scenario.getNetwork(), odPair.getOriginNode().getCoord());
					endLink=NetworkUtils.getNearestLink(this.scenario.getNetwork(), odPair.getDestinationNode().getCoord());
				}
				Path path = shortestPathCalculatorFactory.getRoutingAlgo().calcLeastCostPath(startLink, endLink, 0, null, null);
				//System.out.println("");
				odPair.addCarRoute(new CNLRoute(path,startLink,endLink));
			

		}
		}else {
			int processor=Runtime.getRuntime().availableProcessors();
			RouteGenerationRunnable[] routeThreads=new RouteGenerationRunnable[processor];
			for(int i=0; i<routeThreads.length;i++) {
				routeThreads[i]=new RouteGenerationRunnable(this.scenario,this);
				
			}
			int odPerThread=this.odPairs.getODpairset().size()/processor+1;
			int j=0;
			int i=0;
			//Distribute the OD pairs
			for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
				routeThreads[i].addOdPair(od);
				j++;
				if(j>(i+1)*odPerThread-1) {
					i=i+1;
				}
					
				
			}
			
			Thread[] threads=new Thread[processor];
			for(i=0; i<routeThreads.length;i++) {
				threads[i]=new Thread(routeThreads[i]);
			}
			
			for(i=0; i<routeThreads.length;i++) {
				threads[i].start();
			}
			try {
			for(i=0; i<routeThreads.length;i++) {
				threads[i].join();
			}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}


class RouteGenerationRunnable implements Runnable{
	private HashMap<Id<AnalyticalModelODpair>,AnalyticalModelODpair> odPairs=new HashMap<>();
	private Network originalNetwork;
	private L2lLeastCostCalculatorFactory shortestPathCalculatorFactory;
	private static final AtomicInteger count = new AtomicInteger();
	private final Logger logger = Logger.getLogger(RouteGenerationRunnable.class);
	
	public RouteGenerationRunnable(Scenario scenario,AnalyticalModel model) {
		this.originalNetwork=scenario.getNetwork();
		this.shortestPathCalculatorFactory=new L2lLeastCostCalculatorFactory(scenario, Sets.newHashSet(TransportMode.car), model);
	}
	
	public void addOdPair(AnalyticalModelODpair od) {
		count.set(0); //Reset the route generation.
		this.odPairs.put(od.getODpairId(), od);
	}
	@Override
	public void run() {
		for(AnalyticalModelODpair od:this.odPairs.values()) {
			count.getAndIncrement();
			if( count.get() % 40000==0) {
				logger.info("Generating route for # "+count.get()+" OD pairs.");
			}
			Link startLink=null;
			Link endLink=null;
			
			Tuple<Id<Link>,Id<Link>> linkTuple=od.getStartAndEndLinkIds();
				if(linkTuple!=null) {
					startLink=this.originalNetwork.getLinks().get(linkTuple.getFirst());
					endLink=this.originalNetwork.getLinks().get(linkTuple.getSecond());
				}else {
					startLink=NetworkUtils.getNearestLink(this.originalNetwork, od.getOriginNode().getCoord());
					endLink=NetworkUtils.getNearestLink(this.originalNetwork, od.getDestinationNode().getCoord());
				}
				
				
		
			Path path = shortestPathCalculatorFactory.getRoutingAlgo().calcLeastCostPath(startLink, endLink, (int)(24*3600*Math.random()), null, null);
			//System.out.println("");
			od.addCarRoute(new CNLRoute(path,startLink,endLink));
		}
	}
}



/**
 * For multi-threaded SUE assignment in different time-bean
 * @author Ashraf
 *
 */
class SUERunnable implements Runnable{

	private CNLSUEModel Model;
	private LinkedHashMap<String,Double> params;
	private LinkedHashMap<String, Double> internalParams;
	private final String timeBeanId;
	public SUERunnable(CNLSUEModel CNLMod,String timeBeanId,LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>IntParams) {
		this.Model=CNLMod;
		this.timeBeanId=timeBeanId;
		this.params=params;
		this.internalParams=IntParams;
	}
	
	/**
	 * this method will do the single time bean assignment 
	 */
	
	@Override
	public void run() {
		this.Model.singleTimeBeanTA(params, internalParams, timeBeanId);
		//this.Model.singleTimeBeanTAModeOut(params, internalParams, timeBeanId);
	}
	
}

/**
 * This one is for enoch's testing purpose
 * For multi-threaded SUE assignment in different time-bean
 * @author Ashraf
 *
 */
class SUERunnableEnoch implements Runnable{

	private CNLSUEModel Model;
	private LinkedHashMap<String,Double> params;
	private LinkedHashMap<String, Double> internalParams;
	private final String timeBeanId;
	private double defaultPtModeSplit;
	public SUERunnableEnoch(CNLSUEModel CNLMod,String timeBeanId,LinkedHashMap<String,Double> params,LinkedHashMap<String,Double>IntParams,double defaultPtModeSplit) {
		this.Model=CNLMod;
		this.timeBeanId=timeBeanId;
		this.params=params;
		this.internalParams=IntParams;
		this.defaultPtModeSplit=defaultPtModeSplit;
	}
	
	/**
	 * this method will do the single time bean assignment 
	 */
	
	@Override
	public void run() {
		this.Model.singleTimeBeanTA(params, internalParams, timeBeanId, this.defaultPtModeSplit);
		//this.Model.singleTimeBeanTAModeOut(params, internalParams, timeBeanId);
	}
	
}

