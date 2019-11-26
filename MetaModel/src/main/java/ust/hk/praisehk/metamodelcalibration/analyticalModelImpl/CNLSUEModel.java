package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.lanes.Lanes;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicles;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import de.xypron.jcobyla.Cobyla;
import de.xypron.jcobyla.CobylaExitStatus;
import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.AllPTTransferDiscount;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import transitCalculatorsWithFare.FareTransitRouterConfig;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpairs;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.InternalParamCalibratorFunction;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementDataContainer;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;
import ust.hk.praisehk.metamodelcalibration.transit.CNLPTRecordHandler;
import ust.hk.praisehk.metamodelcalibration.transit.ITransitRoute;
import ust.hk.praisehk.metamodelcalibration.transit.TransitNetworkHR;
import ust.hk.praisehk.shortestpath.L2lLeastCostCalculatorFactory;
import ust.hk.praisehk.shortestpath.SignalFlowReductionGenerator;
import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;


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
	protected Map<String,Double> AnalyticalModelInternalParams=new HashMap<>();
	protected Map<String,Double> Params=new HashMap<>();
	private Map<String,Tuple<Double,Double>> AnalyticalModelParamsLimit=new HashMap<>();
	
	
	private double alphaMSA=1.9;//parameter for decreasing MSA step size
	private double gammaMSA=.1;//parameter for decreasing MSA step size
	
	//other Parameters for the Calibration Process
	private double tolerance = 100;
	private double toleranceRatio = 0.01;
	private double toleranceLink = 5;
	//user input

	protected Map<String, Tuple<Double,Double>> timeBeans;
	
	//MATSim Input
	protected Map<String, AnalyticalModelNetwork> networks=new ConcurrentHashMap<>();
	protected Map<String, TransitNetworkHR> transitNetworks = new ConcurrentHashMap<>();
	protected Network originalNetwork;
	protected TransitSchedule ts;
	protected Scenario scenario;
	protected FareTransitRouterConfig transitConfig;
	
	@Deprecated private Population population;
	protected Map<String,FareCalculator> fareCalculator=new HashMap<>();
	protected TransferDiscountCalculator tdc;
	
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
	protected MeasurementDataContainer workingMdc; //fare collected for each timebean
		
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
		
		this.AnalyticalModelInternalParams.put(CNLSUEModel.LinkMiuName, 0.004);
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
	
	public void setDefaultParameters(Map<String,Double> params) {
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
	public void performTransitVehicleOverlay(AnalyticalModelNetwork network, TransitSchedule schedule,Vehicles vehicles, double fromTime, double toTime) {
		for(TransitLine tl:schedule.getTransitLines().values()) {
			for(TransitRoute tr:tl.getRoutes().values()) {
				List<Id<Link>> links=new ArrayList<>(tr.getRoute().getLinkIds());
				for(Departure d:tr.getDepartures().values()) {
					if(d.getDepartureTime()>fromTime && d.getDepartureTime()<=toTime) {
						for(int i = 0; i < links.size(); i++) {
							Id<Link> linkId = links.get(i);
							CNLLink link = (CNLLink) network.getLinks().get(linkId);
							double pcuVolume = vehicles.getVehicles().get(d.getVehicleId()).getType().getPcuEquivalents();
							link.addLinkTransitVolume( pcuVolume );
							if(link instanceof CNLLinkToLink && i < links.size()-1) {
								((CNLLinkToLink) link).addLinkToLinkTransitVolume(links.get(i+1), pcuVolume); //Also add the linkToLink thing.
							}
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
		for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()) {
			agentTrip+=odPair.getAgentCounter();
			for(String s:odPair.getTimeBean().keySet()) {
				agentDemand+=odPair.getDemand().get(s);
			}
			
		}
		logger.info("Demand total = "+agentDemand);
		logger.info("Total Agent Trips = "+agentTrip);
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network, TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		//this.setLastPopulation(population);
		//System.out.println("");
		this.odPairs = new CNLODpairs(network, transitSchedule, scenario,this.timeBeans);
		this.odPairs.generateODpairset(population);
		this.odPairs.generateRouteandLinkIncidence(0.);
		this.originalNetwork=network;
		SignalFlowReductionGenerator sg = new SignalFlowReductionGenerator(scenario);
		for(String s:this.timeBeans.keySet()) {
			CNLNetwork analyticalNetwork = new CNLNetwork(network, scenario.getLanes(), scenario.getConfig().qsim().getFlowCapFactor());
			analyticalNetwork.updateGCRatio(sg);
			this.networks.put(s, analyticalNetwork);
			this.performTransitVehicleOverlay(this.networks.get(s),
					transitSchedule,scenario.getTransitVehicles(),this.timeBeans.get(s).getFirst(),
					this.timeBeans.get(s).getSecond());
			this.transitLinks.put(s,this.odPairs.getTransitLinks(this.timeBeans,s));
		}
		this.fareCalculator=fareCalculator;
		
		this.ts = transitSchedule;
		for(String timeBeanId:this.timeBeans.keySet()) { //For every time bean
			this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
			Map<Id<AnalyticalModelODpair>, Double> odDemandMap = this.odPairs.getDemand(timeBeanId); //TODO: A little bit strange
			this.originalDemand.put(timeBeanId, new HashMap<>(odDemandMap)); //Add a hashMap for the time bean
			this.demand.put(timeBeanId, new HashMap<>(odDemandMap)); //XXX: Not sure should I do or not.
			for(Id<AnalyticalModelODpair> odId : odDemandMap.keySet()) {
				double totalDemand = odDemandMap.get(odId);
				if(this.odPairs.getODpairset().get(odId).getTrRoutes().isEmpty()) {
					this.carDemand.get(timeBeanId).put(odId, totalDemand);
				}else {
					this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
			
				}
			}
			logger.info("Startig from 0.5 auto and transit ratio");
			if(this.demand.get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		
		printDemandTotalAndAgentTripStat();
	}
	
	@Deprecated //This function should not be used, only the one with subpopulations should be used actually.
	public void generateRoutesAndODWithoutRoute(Population population,Network network,Lanes lanes, TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator, TransferDiscountCalculator tdc, 
			double transitRatio, boolean carOnly) {
		throw new IllegalArgumentException("This function is not maintained! Check if you want to use it.");
		/*
		//this.setLastPopulation(population);
		//System.out.println("");
		this.scenario=scenario;
		this.odPairs = new CNLODpairs(scenario,this.timeBeans);
		this.odPairs.generateODpairsetWithoutRoutes(null, population, true);
		this.originalNetwork=network;
		SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
		//this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String s:this.timeBeans.keySet()) {
			
			this.networks.put(s, new CNLNetwork(network, null));
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
		*/
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
	public Map<String,Map<Id<Link>, Double>> perFormSUE(Map<String, Double> params, 
			MeasurementDataContainer mdc) {
		if(!(this.Params.keySet()).containsAll(params.keySet())) {
			logger.error("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
			throw new IllegalArgumentException("The parameters key do not match with the default parameter keys. Invalid Parameter!! Did you send the wrong parameter format?");
		}		
		return this.perFormSUE(params, this.AnalyticalModelInternalParams, mdc);
	}
	
	/**
	 * This variation is for enoch
	 * This will not perform sue only, rather genrate routeset close to matsim
	 * The function will deploy a shortest path algorithm based on the output of the previous algorithm
	 * THe pt mode ratio is between 0 to 1 (The pt ratio may not be needed)
	 */
	public AnalyticalModelODpairs generateMATSimRoutes(double defaultPtModeRatio, int numberOfIterations, 
			int numOfRoutes) {
		Map<String,Double> params=new HashMap<>(this.Params);
		Map<String,Double> inputParams=new HashMap<>(params);
		Map<String,Double> inputAnaParams=new HashMap<>(this.AnalyticalModelInternalParams);
		Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
		//this loop is for generating routeset
		for(int j=0; j < numberOfIterations;j++) {
			//Creating different threads for different time beans
			double percentage = (j>=10)? 1: Math.sqrt((20*(j+1)-(j+1)*(j+1))/100.); //Incremental loading
			this.loadDemand(percentage, 1-defaultPtModeRatio);
			for(String timeBeans : this.timeBeans.keySet()) {
				this.applyModalSplit(inputParams, inputAnaParams, timeBeans, defaultPtModeRatio);
			}
			Thread[] threads=new Thread[this.timeBeans.size()];
			int i=0;
			for(String timeBeanId:this.timeBeans.keySet()) {
				threads[i]=new Thread(new SUERunnableEnoch(this, timeBeanId, inputParams,inputAnaParams,
						defaultPtModeRatio),timeBeanId);
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
			
			//Get the output link flows.
			for(String timeBeanId:this.timeBeans.keySet()) {
				for(Id<Link> linkId:this.networks.get(timeBeanId).getLinks().keySet()) {
					outputLinkFlow.get(timeBeanId).put(linkId, 
							((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkAADTVolume());
				}
			
			}
			//Modify the linkFlow of other links
			for(String timeBinId: outputLinkFlow.keySet()) {
				String prevTimeBean = findPrevTimeBean(timeBinId);
				if(prevTimeBean!=null) {
					for(Id<Link> linkId:this.networks.get(timeBinId).getLinks().keySet()) {
						CNLLink currLink = ((CNLLink)this.networks.get(timeBinId).getLinks().get(linkId));
						
						double preFlow = outputLinkFlow.get(prevTimeBean).get(linkId);
						//double prevCap = currLink.getMaximumFlowCapacity(timeBeans.get(prevTimeBean), inputParams);
						currLink.setLinkCarConstantVolume(Math.max(0, 0.1 * preFlow));
					}
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
	 * Given a time bin, find the previous time bin
	 * @param timeBinInput
	 * @return
	 */
	private String findPrevTimeBean(String timeBinInput) {
		double startTime = this.timeBeans.get(timeBinInput).getFirst();
		for(String timeBin: this.timeBeans.keySet()) {
			double endTime = this.timeBeans.get(timeBin).getSecond();
			if(endTime == startTime) {
				return timeBin;
			}
		}
		return null;
	}
	
	/**
	 * Reset the fare for every analyticalModelTransitRoute.
	 */
	private void resetFare() {
		for(AnalyticalModelODpair odpair: odPairs.getODpairset().values()) {
			for(String timeBeanId: this.timeBeans.keySet()) {
				List<AnalyticalModelTransitRoute> trRoutes = odpair.getTrRoutes(this.timeBeans, timeBeanId, 1);
				if(trRoutes != null)
					for(AnalyticalModelTransitRoute trRoute: trRoutes) {
						CNLTransitRoute route = (CNLTransitRoute) trRoute;
						route.resetFare();
					}
			}
		}
	}
	
	/**
	 * This is the same method and does the same task as perform SUE, but takes the internal Parameters as an input too.
	 * This will be used for the internal parameters calibration internally
	 * @param params
	 * @return
	 */
	@Override
	public Map<String,Map<Id<Link>, Double>> perFormSUE(Map<String, Double> params, 
			Map<String,Double> anaParams, MeasurementDataContainer mdc) {
		if(params.containsKey("All MTRBusTransferDiscount")){
			((AllPTTransferDiscount) this.tdc).setTransferDiscountAmount(params.get("All BusBusTransferDiscount"), 
					params.get("All MTRBusTransferDiscount")); //Update the discount fare amount
		}else if(params.containsKey("All SelectedFareDiscount")) {
			if(params.containsKey("All SelectedFareDiscount2")) {
				((AllPTTransferDiscount) this.tdc).updateFixedStopDiscount(Lists.newArrayList(
						params.get("All SelectedFareDiscount"), params.get("All SelectedFareDiscount2")));
			}else {
				((AllPTTransferDiscount) this.tdc).updateFixedStopDiscount(
						Lists.newArrayList(params.get("All SelectedFareDiscount")));
			}
		}
		this.resetCarDemand();
		this.resetFare();
		this.workingMdc = mdc; //Reset the fare collected
		mdc.clear(); //It shouldn't be necessary, but do for safety.
		
		Map<String,Double> inputParams=new HashMap<>(params);
		Map<String,Double> inputAnaParams=new HashMap<>(anaParams);
		//Loading missing parameters from the default values		
		Map<String,Map<Id<Link>,Double>> outputLinkFlow=new HashMap<>();
		
		//Checking and updating for the parameters 
		for(Entry<String,Double> e:this.Params.entrySet()) {
			if(!params.containsKey(e.getKey())) {
				inputParams.put(e.getKey(), e.getValue()); //Set the default parameters
			}
		}
		
		//Checking and updating for the analytical model parameters
		for(Entry<String,Double> e:this.AnalyticalModelInternalParams.entrySet()) {
			if(!anaParams.containsKey(e.getKey())) {
				inputAnaParams.put(e.getKey(), e.getValue());
			}
		}
		
		//Creating different threads for different time beans
		Thread[] threads=new Thread[this.timeBeans.size()];
		int i=0;
		for(String timeBeanId:this.timeBeans.keySet()) {
			threads[i]=new Thread(new SUERunnable(this,timeBeanId,inputParams,inputAnaParams),timeBeanId);
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
			
			this.transitNetworks.get(timeBeanId);
		}
		//new OdInfoWriter("toyScenario/ODInfo/odInfo",this.timeBeans).writeOdInfo(this.getOdPairs(), getDemand(), getCarDemand(), inputParams, inputAnaParams);
		return outputLinkFlow;
	}
	
	public Map<Id<AnalyticalModelODpair>, AnalyticalModelODpair> getODPairset(){
		return this.odPairs.getODpairset();
	}
	
	public void setMSAAlpha(double alpha) {
		this.alphaMSA = alpha;
	}
	public void setMSAGamma(double gamma) {
		this.gammaMSA = gamma;
	}
	public void setTollerance(double tollerance) {
		this.tolerance = tollerance;
	}
	/**
	 * This method resets all the car demand 
	 * Notice that the originalDemand is using here, as it is a fixed one.
	 */
	private void resetCarDemand() {
		for(String timeId:this.timeBeans.keySet()) {
			this.carDemand.put(timeId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			for(Id<AnalyticalModelODpair> o : this.originalDemand.get(timeId).keySet()) {
				this.carDemand.get(timeId).put(o, this.originalDemand.get(timeId).get(o)*0.5);
			}

		}
	}
	/**
	 * This method does single OD network loading of only car demand.
	 * 
	 * @param ODpairId
	 * @param internalParams 
	 * @return Return the link flows
	 */
	
	protected Map<Id<Link>, Map<Id<Link>,Double>> networkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId, String timeBeanId, double iteration,
			Map<String, Double> params2, Map<String, Double> internalParams){
		
		AnalyticalModelODpair odpair=this.odPairs.getODpairset().get(ODpairId);
		List<AnalyticalModelRoute> routes=odpair.getRoutes();
		double totalUtility=0;
		
		//Calculating route utility for all car routes inside one OD pair.
		//HashMap<Id<AnalyticalModelRoute>,Double> oldUtility=new HashMap<>();
		//HashMap<Id<AnalyticalModelRoute>,Double> newUtility=new HashMap<>();
		for(AnalyticalModelRoute route : routes){
			double utility=0;
			
			if(iteration>1) { //We only calculate the utility for all
				utility = route.calcRouteUtility(params2, internalParams,this.networks.get(timeBeanId),this.timeBeans.get(timeBeanId));
				//newUtility.put(route.getRouteId(), utility);
				//oldUtility.put(route.getRouteId(), odpair.getRouteUtility(timeBeanId).get(route.getRouteId()));
			}else {
				utility = 0;
			}
			if(utility < -300) {
				utility = -300;
				logger.warn("The utility is too small, the route would not be chosen");
			}
			if(Double.isNaN(utility)) {
				throw new RuntimeException("The utility is Nan!");
			}			
			//oldUtility.put(r.getRouteId(),this.odPairs.getODpairset().get(ODpairId).getRouteUtility(timeBeanId).get(r.getRouteId()));
			odpair.updateRouteUtility(route.getRouteId(), utility, timeBeanId);
			
			//This Check is to make sure the exp(utility) do not go to infinity.
			if(utility>300) {
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
			throw new IllegalArgumentException("The total utility is zero, cannot perform UE.");
		}
		
		
		//This is the route flow split
		HashMap<Id<AnalyticalModelRoute>,Double> routeFlows=new HashMap<>(); //A map to store the route flow.
		Map<Id<Link>, Map<Id<Link>, Double>> linkToLinkPCUFlow = new HashMap<>();
		double totalODcarDemand = this.carDemand.get(timeBeanId).get(ODpairId);
		double totalFlow = 0;
		for(AnalyticalModelRoute route : routes){
			double utility = Math.exp(odpair.getRouteUtility(timeBeanId).get(route.getRouteId()));
			double flow = utility / totalUtility * totalODcarDemand;
			//For testing purpose, can be removed later
			if(Double.isNaN(flow)||flow==Double.POSITIVE_INFINITY) {
				logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
				throw new IllegalArgumentException("Wait!!!!Error!!!!");
			}
			routeFlows.put(route.getRouteId(),flow);
			totalFlow+=flow;
			
			//Store the linkToLinkFlows here.
			for(int i = 0; i < route.getLinkIds().size(); i++) {
				Id<Link> thislinkId = route.getLinkIds().get(i);
				Map<Id<Link>, Double> toLinkFlow = linkToLinkPCUFlow.containsKey(thislinkId)?linkToLinkPCUFlow.get(thislinkId):new HashMap<>();
				if(i < route.getLinkIds().size()-1) {
					Id<Link> nextLinkId = route.getLinkIds().get(i+1);
					if( toLinkFlow.containsKey(nextLinkId) ) {
						toLinkFlow.put(nextLinkId, toLinkFlow.get(nextLinkId) + flow);
					}else {
						toLinkFlow.put(nextLinkId, flow);
					}
				}
				linkToLinkPCUFlow.put(thislinkId, toLinkFlow);
			}
			
		}
		assert(totalODcarDemand==totalFlow);
		
		//Store the link flows
		HashMap<Id<Link>,Double> linkFlows = new HashMap<>();
		for(Id<Link> linkId : odpair.getLinkIncidence().keySet()){ //For each linkId
			double linkflow=0;
			for(AnalyticalModelRoute route : odpair.getLinkIncidence().get(linkId)){
				linkflow+=routeFlows.get(route.getRouteId()); //Add the flow
			}
			linkFlows.put(linkId, linkflow);
			if(Double.isNaN(linkflow)) {
				throw new IllegalArgumentException("link Flow is null !!!!");
			}
		}
		return linkToLinkPCUFlow;
	}
	
	
	/**
	 * This method does transit sue assignment on the transit network on (Total demand-Car Demand)
	 * It will also update the fare collected.
	 * @param ODpairId
	 * @param timeBeanId
	 * @param internalParams 
	 * @return
	 */
	protected HashMap<Id<TransitLink>,Double> networkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId, String timeBeanId,
			int iteration, Map<String, Double> params2, Map<String, Double> internalParams){
		List<AnalyticalModelTransitRoute> routes=this.odPairs.getODpairset().get(ODpairId).getTrRoutes(this.timeBeans,timeBeanId, iteration);
		double totalUtility = 0;
		HashMap<Id<AnalyticalModelTransitRoute>,Double> routeFlows=new HashMap<>();
		if(routes!=null && routes.size()!=0) {
			for(AnalyticalModelTransitRoute r:routes){
				double u;
				if(iteration>1) {
					u=r.calcRouteUtility(params2, internalParams,
						this.networks.get(timeBeanId), this.fareCalculator, this.tdc, 
						this.timeBeans.get(timeBeanId));
					
					if(Double.isNaN(u)) {
						logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
						throw new IllegalArgumentException("Utility is NAN!!!");
					}
				}else {
					u=0;
				}
				if(u > 300) {
					logger.warn("STOP!!!Utility is too large >300");
					throw new IllegalArgumentException("Utility is too large!");
				}else if(u < -300) {
					u = -300;
					logger.warn("Warning!!!Utility is too small < -300");
				}
				this.odPairs.getODpairset().get(ODpairId).updateTrRouteUtility(r.getTrRouteId(), u, timeBeanId);
				totalUtility+=Math.exp(u);
			}
			if(totalUtility==0) {
				logger.warn("STopp!!!! Total utility in the OD pair is zero. This can happen if there is no transit route in that OD pair.");
				throw new IllegalArgumentException("Utility is too small!");
			}
			for(AnalyticalModelTransitRoute r:routes){ //Assign the flow and the fare
				double totalDemand=this.demand.get(timeBeanId).get(ODpairId);
				double carDemand=this.carDemand.get(timeBeanId).get(ODpairId);
				if( Double.isNaN(carDemand) ) {
					carDemand = 0;
				}
				double transitDemand=(totalDemand-carDemand);
				if(transitDemand<0) {
					throw new IllegalArgumentException("Stop!!! transit demand is negative!!!");
				}
				double utility=this.odPairs.getODpairset().get(ODpairId).getTrRouteUtility(timeBeanId).
						get(r.getTrRouteId());
				double flow=transitDemand*Math.exp(utility)/totalUtility;
				if(Double.isNaN(flow)||flow==Double.POSITIVE_INFINITY||flow==Double.NEGATIVE_INFINITY) {
					logger.error("The flow is NAN. This can happen for a number of reasons. Mostly is total utility of all the routes in a OD pair is zero");
					throw new IllegalArgumentException("Error!!!!");
				}
				routeFlows.put(r.getTrRouteId(),flow);
				r.getFare(ts, this.fareCalculator, this.tdc);
				this.workingMdc.addBusFareReceived(timeBeanId, flow * ((CNLTransitRoute) r).getBusFareCollected());
				this.workingMdc.addMTRFareReceived(timeBeanId, flow * ((CNLTransitRoute) r).getMTRFareCollected());
			}
		}
		
		//This part is added by Enoch in order to update the linkflow well
		HashMap<Id<TransitLink>,Double> linkFlows=new HashMap<>();
		if(routes!=null && !routes.isEmpty() && routes.get(0) instanceof ITransitRoute) {
			for(Id<AnalyticalModelTransitRoute> routeId: routeFlows.keySet()) {
				ITransitRoute transitRoute = null;
				for(AnalyticalModelTransitRoute route: routes) { //Find the transit route
					if(route.getTrRouteId().equals(routeId)) {
						transitRoute = (ITransitRoute) route;
						break;
					}
				}
				for(Id<TransitLink> transitLinkId: transitRoute.getTrLinkIds()) {
					if(linkFlows.containsKey(transitLinkId)) {
						linkFlows.put(transitLinkId, linkFlows.get(transitLinkId) + routeFlows.get(routeId));
					}else {
						linkFlows.put(transitLinkId, routeFlows.get(routeId));
					}
				}
			}
			return linkFlows;
		}
		
		//This part below was working, so I kept it here for the metamodel calibration
		Set<Id<TransitLink>>linksets=odPairs.getODpairset().get(ODpairId).getTrLinkIncidence().keySet();
		for(Id<TransitLink> linkId:linksets){
			if(this.transitLinks.get(timeBeanId).containsKey(linkId)) {
				double linkflow=0;
				ArrayList<AnalyticalModelTransitRoute>incidence=odPairs.getODpairset().get(ODpairId).getTrLinkIncidence().get(linkId);
				for(AnalyticalModelTransitRoute r:incidence){
					List<AnalyticalModelTransitRoute> routesFromOd=this.odPairs.getODpairset().get(ODpairId).getTrRoutes(this.timeBeans,timeBeanId, iteration);
					
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
	
	
	private static boolean routeContain(List<AnalyticalModelTransitRoute> routeList,AnalyticalModelTransitRoute route) {
		
		for(AnalyticalModelTransitRoute r:routeList) {
			if(r.getTrRouteId().equals(route.getTrRouteId())) {
				return true;
			}
		}
		return false;
	}
	/**
	 * This method should do the network loading for car
	 * @param internalParams 
	 * @return
	 */
	protected HashMap<Id<Link>,Double> performCarNetworkLoading(String timeBeanId, double iteration, Map<String, Double> params2, Map<String, Double> internalParams){
		HashMap<Id<Link>,Double> linkVolume=new HashMap<>();
		for(Id<AnalyticalModelODpair> odpairId:this.odPairs.getODpairset().keySet()){
			if(this.odPairs.getODpairset().get(odpairId).getRoutes()!=null && this.carDemand.get(timeBeanId).get(odpairId)!=0) {
				Map<Id<Link>,Map<Id<Link>,Double>> oDlinkFlowVolume = this.networkLoadingCarSingleOD(odpairId, timeBeanId, iteration, params2, internalParams);
				for(Id<Link> linkId : oDlinkFlowVolume.keySet()){
					if(linkVolume.containsKey(linkId)){
						linkVolume.put(linkId, linkVolume.get(linkId) + oDlinkFlowVolume.get(linkId).values().stream().mapToDouble(Double::doubleValue).sum());
					}else{
						linkVolume.put(linkId, oDlinkFlowVolume.get(linkId).values().stream().mapToDouble(Double::doubleValue).sum());
					}
				}
			}
		}
		return linkVolume;
	}

	/**
	 * This method should do the linkToLink network loading for car
	 * @param anaParams 
	 * @return
	 */
	protected Map<Id<Link>, Map<Id<Link>,Double>> performParallelLinkToLinkCarNetworkLoading(String timeBeanId, double iteration, 
			Map<String,Double> params, Map<String, Double> anaParams){
		double time = System.nanoTime();
		Map<Id<Link>, Map<Id<Link>,Double>> linkToLinkVolume=new ConcurrentHashMap<>();
		this.odPairs.getODpairset().keySet().parallelStream().forEach(odpairId->{
			if(this.odPairs.getODpairset().get(odpairId).getRoutes()!=null && this.carDemand.get(timeBeanId).get(odpairId)!=0) {
				Map<Id<Link>,Map<Id<Link>,Double>> oDlinkFlowVolume = this.networkLoadingCarSingleOD(odpairId, timeBeanId, iteration, params, anaParams);
				for(Id<Link> fromLinkId : oDlinkFlowVolume.keySet()){
					Map<Id<Link>, Double> toLinkFlow = linkToLinkVolume.containsKey(fromLinkId)?linkToLinkVolume.get(fromLinkId):new HashMap<>();
					for(Id<Link> toLinkId: oDlinkFlowVolume.get(fromLinkId).keySet()) {
						if( toLinkFlow.containsKey(toLinkId) ) {
							toLinkFlow.put(toLinkId, toLinkFlow.get(toLinkId) + oDlinkFlowVolume.get(fromLinkId).get(toLinkId));
						}else {
							toLinkFlow.put(toLinkId, oDlinkFlowVolume.get(fromLinkId).get(toLinkId));
						}
					}
					linkToLinkVolume.put(fromLinkId, toLinkFlow);
				}
			}
		});
		//System.out.println("The time used for car network loading is "+ (System.nanoTime() - time)/1e9);
		return linkToLinkVolume;
	}
	
	/**
	 * This method should do the linkToLink network loading for car
	 * @param anaParams 
	 * @return
	 */
	protected Map<Id<Link>, Map<Id<Link>,Double>> performLinkToLinkCarNetworkLoading(String timeBeanId, double iteration, 
			Map<String,Double> params, Map<String, Double> anaParams){
		double time = System.nanoTime();
		Map<Id<Link>, Map<Id<Link>,Double>> linkToLinkVolume=new HashMap<>();
		for(Id<AnalyticalModelODpair> odpairId:this.odPairs.getODpairset().keySet()){
			if(this.odPairs.getODpairset().get(odpairId).getRoutes()!=null && this.carDemand.get(timeBeanId).get(odpairId)!=0) {
				Map<Id<Link>,Map<Id<Link>,Double>> oDlinkFlowVolume = this.networkLoadingCarSingleOD(odpairId, timeBeanId, iteration, params, anaParams);
				for(Id<Link> fromLinkId : oDlinkFlowVolume.keySet()){
					Map<Id<Link>, Double> toLinkFlow = linkToLinkVolume.containsKey(fromLinkId)?linkToLinkVolume.get(fromLinkId):new HashMap<>();
					for(Id<Link> toLinkId: oDlinkFlowVolume.get(fromLinkId).keySet()) {
						if( toLinkFlow.containsKey(toLinkId) ) {
							toLinkFlow.put(toLinkId, toLinkFlow.get(toLinkId) + oDlinkFlowVolume.get(fromLinkId).get(toLinkId));
						}else {
							toLinkFlow.put(toLinkId, oDlinkFlowVolume.get(fromLinkId).get(toLinkId));
						}
					}
					linkToLinkVolume.put(fromLinkId, toLinkFlow);
				}
			}
		}
		//System.out.println("The time used for car network loading is "+ (System.nanoTime() - time)/1e9);
		return linkToLinkVolume;
	}
	
	protected HashMap<Id<TransitLink>,Double> performParallelTransitNetworkLoading(String timeBeanId, int counter, 
			Map<String, Double> params, Map<String, Double> anaParams){
		double time = System.nanoTime();
		ConcurrentHashMap<Id<TransitLink>,Double> transitLinkVolume=new ConcurrentHashMap<>();
		this.odPairs.getODpairset().keySet().parallelStream().forEach(odpairId->{
			double totalDemand=this.demand.get(timeBeanId).get(odpairId);
			double carDemand=this.carDemand.get(timeBeanId).get(odpairId);
			if((totalDemand-carDemand)!=0) { //Load only if there is something not car demand.
				HashMap<Id<TransitLink>,Double> transitLinkvolumes = this.networkLoadingTransitSingleOD(odpairId,timeBeanId,counter,params,anaParams);
				for(Id<TransitLink> linkId:transitLinkvolumes.keySet()){
					if(transitLinkVolume.containsKey(linkId)){
						transitLinkVolume.put(linkId, transitLinkVolume.get(linkId)+transitLinkvolumes.get(linkId));
					}else{
						transitLinkVolume.put(linkId, transitLinkvolumes.get(linkId));
					}
				}
			}
		});
		//System.out.println("The time used for parllel transit network loading is "+ (System.nanoTime() - time)/1e9);
		//System.out.println(linkVolume.size());
		return new HashMap<>(transitLinkVolume);
	}
	
	/**
	 * This method should do the network loading for transit
	 * @param params2 
	 * @param internalParams 
	 * @return
	 */
	protected HashMap<Id<TransitLink>,Double> performTransitNetworkLoading(String timeBeanId, int counter, Map<String, Double> params2, 
			Map<String, Double> internalParams){
		double time = System.nanoTime();
		HashMap<Id<TransitLink>,Double> transitLinkVolume=new HashMap<>();
		for(Id<AnalyticalModelODpair> odpairId:this.odPairs.getODpairset().keySet()){
			double totalDemand=this.demand.get(timeBeanId).get(odpairId);
			double carDemand=this.carDemand.get(timeBeanId).get(odpairId);
			if((totalDemand-carDemand)!=0) { //Load only if there is something not car demand.
				HashMap<Id<TransitLink>,Double> transitLinkvolumes = this.networkLoadingTransitSingleOD(odpairId,timeBeanId,counter,params2,internalParams);
				for(Id<TransitLink> linkId:transitLinkvolumes.keySet()){
					if(transitLinkVolume.containsKey(linkId)){
						transitLinkVolume.put(linkId, transitLinkVolume.get(linkId)+transitLinkvolumes.get(linkId));
					}else{
						transitLinkVolume.put(linkId, transitLinkvolumes.get(linkId));
					}
				}
			}
		}
		//System.out.println("The time used for transit network loading is "+ (System.nanoTime() - time)/1e9);
		//System.out.println(linkVolume.size());
		return transitLinkVolume;
	}
	
	
	/**
	 * This method updates the linkCarVolume and linkTransitVolume obtained using MSA 
	 * @param linkToLinkCarVolume - Calculated link volume
	 * @param transitlinkVolume - Calculated transit volume
	 * @param counter - current counter in MSA loop
	 * @param timeBeanId - the specific time Bean Id for which the SUE is performed
	 */
	
	@SuppressWarnings("unchecked")
	protected boolean updateLinkToLinkVolume(Map<Id<Link>, Map<Id<Link>, Double>> linkToLinkCarVolume, 
			HashMap<Id<TransitLink>,Double> transitlinkVolume, int counter, String timeBeanId){
		boolean success = updateLinkVolume(convertl2lToLink(linkToLinkCarVolume), transitlinkVolume, counter, timeBeanId);
		//TODO: Add a update linkToLink thing here.
		double counterPart = 1/beta.get(timeBeanId).get(counter-1);
		for(Id<Link> fromLinkId : linkToLinkCarVolume.keySet()){
			for(Id<Link> toLinkId: linkToLinkCarVolume.get(fromLinkId).keySet()) {
				double newVolume = linkToLinkCarVolume.get(fromLinkId).get(toLinkId);
				CNLLinkToLink linkFound = ((CNLLinkToLink) this.networks.get(timeBeanId).getLinks().get(fromLinkId));
				double oldVolume= linkFound.getLinkCarVolume(toLinkId);
				double update;
				//counterPart=1./counter;
				update=counterPart*(newVolume-oldVolume);
				linkFound.addLinkToLinkCarVolume(toLinkId, update);
			}
		}
		return success;
	}
	
	protected void updateTransitVolume(HashMap<Id<TransitLink>,Double> transitlinkVolume, int counter, String timeBeanId) {
		//Update transit link volume.
		double counterPart = 1/beta.get(timeBeanId).get(counter-1);
		if(!this.transitNetworks.isEmpty()) {
			for(Id<TransitLink> transitLinkId: transitlinkVolume.keySet()) {
				double newVolume = transitlinkVolume.get(transitLinkId);
				TransitLink linkFound = (TransitLink) this.transitNetworks.get(timeBeanId).getLink(transitLinkId);
				double oldVolume= linkFound.getPassangerCount();
				double update = counterPart*(newVolume-oldVolume);
				linkFound.addPassanger(update, null);
			}
		}else {
			for(Id<TransitLink> transitLinkId: transitlinkVolume.keySet()) {
				double newVolume = transitlinkVolume.get(transitLinkId);
				TransitLink linkFound = (TransitLink) this.transitLinks.get(timeBeanId).get(transitLinkId);
				double oldVolume= linkFound.getPassangerCount();
				double update = counterPart*(newVolume-oldVolume);
				linkFound.addPassanger(update, this.networks.get(timeBeanId));
			}
		}
	}
	
	private void updateBeta(int counter, String timeBeanId){
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
	}
	
	private boolean updateLinkVolume(Map<Id<Link>, Double> linkCarVolume,
			Map<Id<TransitLink>, Double> transitlinkVolume, int counter, String timeBeanId) {
		// TODO Auto-generated method stub
		double squareSum=0;
		double flowSum=0;
		double linkSum=0;
		
		for(Id<Link> linkId:linkCarVolume.keySet()){
			double newVolume=linkCarVolume.get(linkId);
			double oldVolume=((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).getLinkCarVolume();
			flowSum+=oldVolume;
			double update;
			double counterPart=1/beta.get(timeBeanId).get(counter-1);
			//counterPart=1./counter;
			update=counterPart*(newVolume-oldVolume);
			if(oldVolume!=0) {
				if(Math.abs(update)/oldVolume*100>this.toleranceLink) {
					linkSum+=1;
				}
			}
			squareSum+=update*update;
			((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkId)).addLinkCarVolume(update);
		}
		squareSum=Math.sqrt(squareSum);
		if(counter==1) {
			this.error1.get(timeBeanId).clear();
		}
		error1.get(timeBeanId).add(squareSum);
		
		if(squareSum<this.tolerance) {
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
	protected boolean checkConvergence(HashMap<Id<Link>,Double> linkVolume, 
			HashMap<Id<TransitLink>,Double> transitlinkVolume, String timeBeanId,int counter){
		double squareSum=0;
		boolean someLinkDeviatedTooMuch = false;
		for(Id<Link> linkid:linkVolume.keySet()){
			if(linkVolume.get(linkid)!=0){ //We only calculate for links with flow.
				double currentVolume =((AnalyticalModelLink) this.networks.get(timeBeanId).getLinks().get(linkid)).getLinkCarVolume();
				double newVolume=linkVolume.get(linkid);
				double error = Math.pow((currentVolume-newVolume),2);
				if(error==Double.POSITIVE_INFINITY||error==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("Error is infinity!!!");
				}else if(Double.isNaN(error)) {
					throw new IllegalArgumentException("error is NAN!!!! CHECK");
				}
				if( error/newVolume > toleranceRatio && Math.abs(currentVolume - newVolume) < toleranceLink) {					
					someLinkDeviatedTooMuch = true;
				}
				squareSum += error;
			}
//			if(squareSum==Double.POSITIVE_INFINITY || squareSum==Double.NEGATIVE_INFINITY) {
//				throw new IllegalArgumentException("error is infinity!!!");
//			}
		}
		for(Id<TransitLink> transitlinkid:transitlinkVolume.keySet()){
			if(transitlinkVolume.get(transitlinkid)!=0) { //We only calculate for links with flow.
				double currentVolume = this.transitLinks.get(timeBeanId).get(transitlinkid).getPassangerCount();
				double newVolume=transitlinkVolume.get(transitlinkid);
				double error = Math.pow((currentVolume-newVolume),2);
				if(Double.isNaN(error)||error==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("Stop!!! There is something wrong!!!");
				}
				if( error/newVolume > toleranceRatio && Math.abs(currentVolume - newVolume) < toleranceLink) {
					someLinkDeviatedTooMuch = true;
				}
				squareSum += error;
			}
		}
		
		if(counter==1) {
			this.error.get(timeBeanId).clear();
		}
		squareSum = Math.sqrt(squareSum);
		this.error.get(timeBeanId).add(squareSum);
		logger.info("ERROR amount for "+timeBeanId+" = "+squareSum);
		//System.out.println("in timeBean Id "+timeBeanId+" No of link not converged = "+squareSum);
		
//		try {
//			//CNLSUEModel.writeData(timeBeanId+","+counter+","+squareSum+","+sum, this.fileLoc+"ErrorData"+timeBeanId+".csv");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		List<Double> errorList = this.error.get(timeBeanId); //It is for storing the error.
		if (squareSum <= this.tolerance || !someLinkDeviatedTooMuch || 
				(errorList.size() > 2 && Math.abs(errorList.get(errorList.size()-1) - errorList.get(errorList.size()-2)) < 1)){
			return true;
		}else{
			return false;
		}
		
	}
	/**
	 * This method perform modal Split
	 * @param params2
	 * @param internalParams
	 * @param timeBeanId
	 */
	protected void performModalSplit(Map<String, Double> params2,Map<String, Double> internalParams,String timeBeanId) {
		double modeMiu=internalParams.get(CNLSUEModel.ModeMiuName);
		for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()){
			double demand=this.demand.get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) { 
				double carUtility=odPair.getExpectedMaximumCarUtility(params2, internalParams, timeBeanId);
				double transitUtility=odPair.getExpectedMaximumTransitUtility(params2, internalParams, timeBeanId);
				
				if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
						Math.exp(transitUtility*modeMiu)==Double.POSITIVE_INFINITY) {
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.0);
					
				}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
						||Math.exp(carUtility*modeMiu)==Double.POSITIVE_INFINITY) {
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(), this.demand.get(timeBeanId).get(odPair.getODpairId()));
				}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.);
				}else {
					double carProportion=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu));
					//System.out.println("Car Proportion = "+carProportion);
					Double cardemand=Math.exp(carUtility*modeMiu)/(Math.exp(carUtility*modeMiu)+Math.exp(transitUtility*modeMiu))*this.demand.get(timeBeanId).get(odPair.getODpairId());
					if(Double.isNaN(cardemand)||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
						logger.error("Car Demand is invalid");
						throw new IllegalArgumentException("car demand is invalid");
					}
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}
			}
		}
	}
	
	/**
	 * This function is also just for Enoch's new usage
	 * @param timeBeanId
	 * @param defaultptModeshare
	 */
	protected void applyModalSplit(Map<String,Double> params, Map<String,Double> anaParams, String timeBeanId, double defaultptModeshare) {
		if(defaultptModeshare<0 || defaultptModeshare>1 ) {
			throw new IllegalArgumentException("default pt mode ratio cannot be larger than 100");
		}
		
		for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()){
			double demand=this.demand.get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) {
				//double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
				if(!odPair.getODpairId().toString().contains("GV")) {
					Double cardemand=demand*(1-defaultptModeshare);
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}else {
					Double cardemand=demand;
					this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
				}
			}
		}
		
	}
	
	@Override
	public Map<Integer, Measurements> calibrateInternalParams(Map<Integer,Measurements> simMeasurements, 
			Map<Integer,Map<String,Double>>params, Map<String,Double> initialParam, int currentParamNo) {
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
				
				if(this.odPairs.getODpairset().get(odPairId).getTrRoutes().isEmpty() || //No transit route
						odPairId.toString().contains("GV")) {  //Subpopulation of GV
					this.demand.get(timeBean).put(odPairId, totalDemand);
					this.carDemand.get(timeBean).put(odPairId, totalDemand);
				}else { //If there is transit
					this.demand.get(timeBean).put(odPairId, totalDemand);
					this.carDemand.get(timeBean).put(odPairId, totalDemand * initialCarDemandPercentage);
				}
			}
		}
		
	}
	
	private static HashMap<Id<Link>, Double> convertl2lToLink(Map<Id<Link>, Map<Id<Link>, Double>> linkToLinkCarVolume){
		HashMap<Id<Link>, Double> linkCarVolume = new HashMap<>();
		for(Id<Link> linkId : linkToLinkCarVolume.keySet()){
			if(linkCarVolume.containsKey(linkId)){
				linkCarVolume.put(linkId, linkCarVolume.get(linkId) + linkToLinkCarVolume.get(linkId).values().stream().mapToDouble(Double::doubleValue).sum());
			}else{
				linkCarVolume.put(linkId, linkToLinkCarVolume.get(linkId).values().stream().mapToDouble(Double::doubleValue).sum());
			}
		}
		return linkCarVolume;
	}
	
	private void clearNanFlows() {
		for(AnalyticalModelNetwork network: networks.values()) {
			network.clearLinkNANVolumes();
		}
	}
	
	/**
	 * This method performs a Traffic Assignment of a single time Bean
	 * @param params: calibration Parameters
	 * @param anaParams: Analytical model Parameters
	 * @param timeBeanId
	 */
	public void singleTimeBeanTA(Map<String, Double> params2,Map<String, Double> internalParams,String timeBeanId) {
		boolean shouldStop=false;
		boolean carConverged = false;
		HashMap<Id<TransitLink>, Double> linkTransitVolume = new HashMap<>();
		Map<Id<Link>, Map<Id<Link>, Double>> linkToLinkCarVolume = new HashMap<>();
		for(int i=1;i<500;i++) {
			//for(this.car)
			//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
			this.networks.get(timeBeanId).clearLinkNANVolumes();
			this.workingMdc.resetTransitFares(timeBeanId); //Reset the fares as the iteration updated
			
			
			if(!carConverged) {
				linkToLinkCarVolume = performParallelLinkToLinkCarNetworkLoading(timeBeanId,i,params2,internalParams);
				linkTransitVolume.clear();
				shouldStop = this.checkConvergence(convertl2lToLink(linkToLinkCarVolume), linkTransitVolume, timeBeanId,i);
				this.updateBeta(i, timeBeanId);
				this.updateLinkToLinkVolume(linkToLinkCarVolume, linkTransitVolume, i, timeBeanId);
			
			}else {
				this.updateBeta(i, timeBeanId);
				shouldStop = true;
			}
			linkTransitVolume = this.performTransitNetworkLoading(timeBeanId,i,params2,internalParams);
			if(shouldStop) {
				carConverged = true;
				shouldStop = this.checkConvergence(convertl2lToLink(linkToLinkCarVolume), linkTransitVolume, timeBeanId,i);
			}
			updateTransitVolume(linkTransitVolume, i, timeBeanId);

//			HashMap<Id<Link>,Double> linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params2,internalParams); //Load the car network, and calculate utilities if it is not first iteration
//			linkTransitVolume = this.performTransitNetworkLoading(timeBeanId,i,params2,internalParams);
//			shouldStop=this.checkConvergence(linkCarVolume, linkTransitVolume, timeBeanId,i);
//			this.updateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
			
			if(i==1 && shouldStop==true) {
				boolean demandEmpty=true;
				for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
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
			this.performModalSplit(params2, internalParams, timeBeanId);
		}
	}


/**
 * This function is for enoch's testing
 * @param params2
 * @param internalParams
 * @param timeBeanId
 * @param defaultModeRatio: pt mode ratio in between 0 to 1
 * @throws ExecutionException 
 * @throws InterruptedException 
 */
	public void singleTimeBeanTA(Map<String, Double> params2,Map<String, Double> internalParams,String timeBeanId,double defaultModeRatio) throws InterruptedException, ExecutionException {
		boolean shouldStop=false;
		boolean carConverged = false;
		HashMap<Id<TransitLink>, Double> linkTransitVolume = new HashMap<>();
		Map<Id<Link>, Map<Id<Link>, Double>> linkToLinkCarVolume = new HashMap<>();
		for(int i=1;i<500;i++) {
//			int[] iter = {i};
//		    CompletableFuture<Map<Id<Link>, Map<Id<Link>, Double>>> carLoad = 
//		    		CompletableFuture.supplyAsync(() -> performParallelLinkToLinkCarNetworkLoading(timeBeanId,iter[0],params,anaParams));
//		    CompletableFuture<HashMap<Id<TransitLink>, Double>> transitLoad = 
//		    		CompletableFuture.supplyAsync(() -> performTransitNetworkLoading(timeBeanId,iter[0],params,anaParams));
//		    CompletableFuture<HashMap<Id<TransitLink>, Double>> transitLoad2 = 
//		    		CompletableFuture.supplyAsync(() -> performParallelTransitNetworkLoading(timeBeanId,iter[0],params,anaParams));
			
			if(!carConverged) {
				linkToLinkCarVolume = performParallelLinkToLinkCarNetworkLoading(timeBeanId,i,params2,internalParams);
				//HashMap<Id<TransitLink>, Double> linkTransitVolume = transitLoad2.get();
				linkTransitVolume.clear(); //Empty the transit link volume so that the error would not be counted.
				shouldStop = this.checkConvergence(convertl2lToLink(linkToLinkCarVolume), linkTransitVolume, timeBeanId,i);
				this.updateLinkToLinkVolume(linkToLinkCarVolume, linkTransitVolume, i, timeBeanId);
			}
			linkTransitVolume = performParallelTransitNetworkLoading(timeBeanId,i,params2,internalParams);
			if(shouldStop) {
				carConverged = true;
				shouldStop = this.checkConvergence(convertl2lToLink(linkToLinkCarVolume), linkTransitVolume, timeBeanId,i);
			}
			updateTransitVolume(linkTransitVolume, i, timeBeanId);

			//for(this.car)
			//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
			if(i==1 && shouldStop==true) {
				boolean demandEmpty=true;
				for(AnalyticalModelODpair od:this.odPairs.getODpairset().values()) {
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
			this.applyModalSplit(params2, internalParams, timeBeanId, defaultModeRatio);
		}
		
		
	}
	
	@Deprecated
	public void singleTimeBeanTAModeOut(Map<String, Double> params,Map<String,Double> anaParams,String timeBeanId) {
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
				shouldStop=this.checkConvergence(linkCarVolume, linkTransitVolume, timeBeanId,i);
				this.updateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				if(shouldStop) {break;}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			this.performModalSplit(params, anaParams, timeBeanId);
		}
		
	}
	@Deprecated
	public void singleTimeBeanTAOut(Map<String, Double> params,Map<String,Double> anaParams,String timeBeanId) {
		HashMap<Id<TransitLink>, Double> linkTransitVolume=new HashMap<>();
		HashMap<Id<Link>,Double> linkCarVolume=new HashMap<>();
		boolean shouldStop=false;
		for(int j=0;j<1;j++) {
			
			for(int i=1;i<5000;i++) {
				//for(this.car)
				//ConcurrentHashMap<String,HashMap<Id<CNLODpair>,Double>>demand=this.Demand;
				linkCarVolume=this.performCarNetworkLoading(timeBeanId,i,params,anaParams);
				this.checkConvergence(linkCarVolume, linkTransitVolume, timeBeanId,i);
				shouldStop=this.updateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
				
				if(shouldStop) {
					break;
					}
				//this.performModalSplit(params, anaParams, timeBeanId);

			}
			for(int i=1;i<1;i++) {
				linkTransitVolume=this.performTransitNetworkLoading(timeBeanId,i,params,anaParams);
				shouldStop=this.checkConvergence(linkCarVolume, linkTransitVolume, timeBeanId,i);
				this.updateLinkVolume(linkCarVolume, linkTransitVolume, i, timeBeanId);
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

//	public LinkedHashMap<String, Double> getParams() {
//		return Params;
//	}

	public Map<String, Double> getAnalyticalModelInternalParams() {
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

	public Map<String, Tuple<Double, Double>> getAnalyticalModelParamsLimit() {
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

//	public AnalyticalModelODpairs getOdPairs() {
//		return odPairs;
//	}

//	public Map <String,AnalyticalModelNetwork> getNetworks() {
//		return networks;
//	}

	
	@Deprecated
	public Map<String,Map<Id<TransitLink>,TransitLink>> getTransitLinks() {
		return transitLinks;
	}

//	public TransitSchedule getTs() {
//		return ts;
//	}

//	public void setTs(TransitSchedule ts) {
//		this.ts = ts;
//	}

//	public Map<String,Double> getConsecutiveSUEErrorIncrease() {
//		return consecutiveSUEErrorIncrease;
//	}
	
//	public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getDemand() {
//		return demand;
//	}

//	public Map<String,HashMap<Id<AnalyticalModelODpair>,Double>> getCarDemand() {
//		return carDemand;
//	}

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
		throw new RuntimeException("Should be replaced by the averageLinkTravelTime with time!");
		//I would rather remove this function to avoid confusion. Enoch Mar 2019.
		
//		double travelTime=0;
//		for(String s:this.timeBeans.keySet()) {
//			travelTime+= ((AnalyticalModelLink)this.networks.get(s).getLinks().get(linkId)).getLinkTravelTime(this.timeBeans.get(s), this.Params, this.AnalyticalModelInternalParams);
//		}
//		return travelTime/this.timeBeans.size();
	}
	
	public static List<String> timeBinsWithDemand(Map<String, Double> timeBinToDemandMap) {
		List<String> timeBinFound = new ArrayList<>();
		for(String timebin: timeBinToDemandMap.keySet()) {
			double odPairDemand = timeBinToDemandMap.get(timebin);
			if(odPairDemand>0) {
				timeBinFound.add(timebin);
			}
		}
		return timeBinFound;
	}

	/**
	 * This will for now generate the car paths
	 * The transit path generation will be added later as well
	 * This will signal the ODpairs to generate routes. This can be done to specific od pairs in future
	 * 
	 */
	protected void generateRoute() {
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
				
				//Get a random time bin
				for(String timeBin: timeBinsWithDemand(odPair.getDemand())) {				
					Path path = shortestPathCalculatorFactory.getRoutingAlgo().calcLeastCostPath(startLink, endLink, 
							this.timeBeans.get(timeBin).getFirst(), null, null);
					odPair.addCarRoute(new CNLRoute(path,startLink,endLink));
				}
			}
		}else {
			int processor=Runtime.getRuntime().availableProcessors();
			RouteGenerationRunnable[] routeThreads=new RouteGenerationRunnable[processor];
			for(int i=0; i<routeThreads.length;i++) {
				routeThreads[i]=new RouteGenerationRunnable(this.scenario,this, this.transitNetworks, 
						this.transitConfig, this.fareCalculator, this.tdc);
				
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
				e.printStackTrace();
			}
		}
	}

	/**
	 * This function is for getting the average link travel time in the same timeBin
	 */
	@Override
	public double getAverageLinkTravelTime(Id<Link> linkId, double time) {
		double linkTime = 0.0;
		for(String s : this.timeBeans.keySet()) {
			Tuple<Double, Double> times = this.timeBeans.get(s);
			linkTime = ((AnalyticalModelLink)this.networks.get(s).getLinks().get(linkId)).getLinkTravelTime(times, this.Params, this.AnalyticalModelInternalParams);
			if(times.getFirst()<=time && time<times.getSecond())
				return linkTime;
		}
		return linkTime * 1000; //Return the final link time if their time is not found.
		//throw new IllegalArgumentException("Error in get the appropriate time bin with time "+time+" !!");
	}
}


class RouteGenerationRunnable implements Runnable{
	private HashMap<Id<AnalyticalModelODpair>,AnalyticalModelODpair> odPairs=new HashMap<>();
	private Network originalNetwork;
	private L2lLeastCostCalculatorFactory shortestPathCalculatorFactory;
	//private final MultiNodeAStarEucliean transitPathCalculator;
	private final TransitRouterFareDynamicImpl transitRouter;
	private final double beelineWalkingSpeed;
	private final double utilityTravelTimeWalk;
	private final TransitSchedule ts;
	private final Map<String, Tuple<Double,Double>> timeBeans;
	private final Map<String, TransitNetworkHR> transitNetworks;
	private static final AtomicInteger count = new AtomicInteger();
	private final Logger logger = Logger.getLogger(RouteGenerationRunnable.class);
	
	public RouteGenerationRunnable(Scenario scenario, AnalyticalModel model, Map<String, TransitNetworkHR> transitNetworks, 
			FareTransitRouterConfig transitConfig, Map<String, FareCalculator> fareCalculators, 
			TransferDiscountCalculator tdc) {
		this.originalNetwork = scenario.getNetwork();
		this.shortestPathCalculatorFactory = new L2lLeastCostCalculatorFactory(scenario, 
				Sets.newHashSet(TransportMode.car), model);
		
		//Create transit network
		CNLPTRecordHandler ptHandler = new CNLPTRecordHandler(scenario, transitNetworks, model);
		TransitRouterFareDynamicImpl.distanceFactor = 0.034;
		this.transitRouter = new TransitRouterFareDynamicImpl(scenario, ptHandler, ptHandler, ptHandler,
				fareCalculators, tdc);
		this.ts = scenario.getTransitSchedule();
		this.transitNetworks = transitNetworks;
		this.timeBeans = model.getTimeBeans();
		this.utilityTravelTimeWalk = transitConfig.getMarginalUtilityOfTravelTimeWalk_utl_s();
		this.beelineWalkingSpeed = transitConfig.getBeelineWalkSpeed();
	}
	
	public void addOdPair(AnalyticalModelODpair od) {
		count.set(0); //Reset the route generation.
		this.odPairs.put(od.getODpairId(), od);
	}
	
	/**
	 * This is a convenient function to convert the path obtained from the TransitRouterFareDynamicImpl
	 * to the transitNetwork using here.
	 * @param originalPath
	 * @return
	 */
	private Path convertPathFromRouterToModel(Path originalPath, String timebin) {
		List<Node> newNodes = new ArrayList<>();
		List<Link> newLinks = new ArrayList<>();
		if(originalPath.nodes!=null)
		for(Node node: originalPath.nodes) {
			newNodes.add(this.transitNetworks.get(timebin).getNodes().get(node.getId()));
		}
		for(Link link: originalPath.links) {
			newLinks.add(this.transitNetworks.get(timebin).getLinks().get(link.getId()));
		}
		return new Path(newNodes, newLinks, originalPath.travelTime, originalPath.travelCost);
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
			
			for(String timeBin: CNLSUEModel.timeBinsWithDemand(od.getDemand())) {	
				Path path = shortestPathCalculatorFactory.getRoutingAlgo().calcLeastCostPath(startLink.getId(), endLink.getId(), 
						this.timeBeans.get(timeBin).getFirst(), null, null);
				od.addCarRoute(new CNLRoute(path,startLink,endLink));
				
				Coord originCoord = od.getOriginNode().getCoord();
				Coord destCoord = od.getDestinationNode().getCoord();
				Path transitPath = this.transitRouter.calcPathRoute(originCoord, destCoord, this.timeBeans.get(timeBin).getFirst(), null);
				if(transitPath != null) { //It is null if there is a transit node, but no routes are found (probably out of service)
					od.addTransitRoute(this.transitRouter, originCoord, destCoord, convertPathFromRouterToModel(transitPath, timeBin));
				}else { //Give it a direct walk, if the route is not found.
					double directWalkTime = CoordUtils.calcEuclideanDistance(originCoord, destCoord) 
							/ beelineWalkingSpeed;
					double directWalkCost = directWalkTime * ( 0 - utilityTravelTimeWalk);
					transitPath = new Path(new ArrayList<Node>(), new ArrayList<Link>(), directWalkTime, directWalkCost);
					od.addTransitRoute(this.transitRouter, originCoord, destCoord, convertPathFromRouterToModel(transitPath, timeBin));
					
				}
			}
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
	private Map<String,Double> params;
	private Map<String, Double> internalParams;
	private final String timeBeanId;
	public SUERunnable(CNLSUEModel CNLMod,String timeBeanId,Map<String,Double> params,Map<String,Double>IntParams) {
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
	private Map<String,Double> params;
	private Map<String, Double> internalParams;
	private final String timeBeanId;
	private double defaultPtModeSplit;
	public SUERunnableEnoch(CNLSUEModel CNLMod,String timeBeanId,Map<String,Double> params,Map<String,Double>IntParams,double defaultPtModeSplit) {
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
		try {
			this.Model.singleTimeBeanTA(params, internalParams, timeBeanId, this.defaultPtModeSplit);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("");
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("");
		}
		//this.Model.singleTimeBeanTAModeOut(params, internalParams, timeBeanId);
	}
	
}

