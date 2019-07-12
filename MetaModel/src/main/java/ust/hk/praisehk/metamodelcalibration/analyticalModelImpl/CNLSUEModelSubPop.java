package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lanes;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import transitCalculatorsWithFare.FareTransitRouterConfig;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.measurements.MeasurementDataContainer;
import ust.hk.praisehk.metamodelcalibration.transit.TransitNetworkHR;
import ust.hk.praisehk.shortestpath.SignalFlowReductionGenerator;


/**
 * This is a multi-subPopulation implementation of the AnalyticalModel CNLSUEModel
 * 
 * @author h
 *
 */
public class CNLSUEModelSubPop extends CNLSUEModel{

	private ArrayList<String> subPopulationName=new ArrayList<>();
	private ParamReader pReader=new ParamReader("input/subPopParamAndLimit.csv");
	private boolean containLinkToLink = false; //Default as false.
	
	public CNLSUEModelSubPop(Map<String, Tuple<Double, Double>> timeBean,ArrayList<String> subPopName) {
		super(timeBean);
		this.subPopulationName=subPopName;
		super.setDefaultParameters(pReader.getDefaultParam());
	}
	
	public CNLSUEModelSubPop(Map<String, Tuple<Double, Double>> timeBean,ParamReader preader) {
		super(timeBean);
		this.subPopulationName=pReader.getSubPopulationName();
		this.pReader=preader;
		super.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
		this.setTollerance(0.1);
	}
	
	public CNLSUEModelSubPop(Config config,Map<String, Tuple<Double, Double>> timeBean,ArrayList<String>subPopNames) {
		super(timeBean);
		this.subPopulationName=subPopNames;
		this.defaultParameterInitiation(config);
		for(String timeBeanId:timeBean.keySet()) {
			this.demand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.carDemand.put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.transitLinks.put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			super.beta.put(timeBeanId, new ArrayList<Double>());
			this.error.put(timeBeanId, new ArrayList<Double>());
			this.error1.put(timeBeanId, new ArrayList<Double>());
			
		}
		logger.info("Analytical model created successfully.");
		
	}
	
	public void setTransferDiscountCalculator(TransferDiscountCalculator tdc) {
		this.tdc = tdc;
	}
	
	private void defaultParameterInitiation(Config config){
		super.Params.clear();
		if(this.subPopulationName.size()!=0) {
			for(String subPop:this.subPopulationName) {
				if(!subPop.contains("GV")) {
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMarginalUtilityOfDistance());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofMoneyName,config.planCalcScore().getOrCreateScoringParameters(subPop).getMarginalUtilityOfMoney());
					super.Params.put(subPop+" "+AnalyticalModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMonetaryDistanceRate());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofTravelptName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityOfDistancePtName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("pt").getMonetaryDistanceRate());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofWaitingName,config.planCalcScore().getOrCreateScoringParameters(subPop).getMarginalUtlOfWaitingPt_utils_hr());
					super.Params.put(subPop+" "+AnalyticalModel.UtilityOfLineSwitchName,config.planCalcScore().getOrCreateScoringParameters(subPop).getUtilityOfLineSwitch());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityOfWalkingName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
					super.Params.put(subPop+" "+AnalyticalModel.DistanceBasedMoneyCostWalkName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("walk").getMonetaryDistanceRate());
					super.Params.put(subPop+" "+AnalyticalModel.ModeConstantPtname,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("pt").getConstant());
					super.Params.put(subPop+" "+AnalyticalModel.ModeConstantCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getConstant());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofPerformName,config.planCalcScore().getOrCreateScoringParameters(subPop).getPerforming_utils_hr());
				
				}else {
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMarginalUtilityOfDistance());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofMoneyName,config.planCalcScore().getOrCreateScoringParameters(subPop).getMarginalUtilityOfMoney());
					super.Params.put(subPop+" "+AnalyticalModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("car").getMonetaryDistanceRate());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityOfWalkingName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
					super.Params.put(subPop+" "+AnalyticalModel.DistanceBasedMoneyCostWalkName,config.planCalcScore().getOrCreateScoringParameters(subPop).getOrCreateModeParams("walk").getMonetaryDistanceRate());
					super.Params.put(subPop+" "+AnalyticalModel.MarginalUtilityofPerformName,config.planCalcScore().getOrCreateScoringParameters(subPop).getPerforming_utils_hr());
				}
			}
			}else {
				super.Params.put(AnalyticalModel.MarginalUtilityofTravelCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfTraveling());
				super.Params.put(AnalyticalModel.MarginalUtilityofDistanceCarName,config.planCalcScore().getOrCreateModeParams("car").getMarginalUtilityOfDistance());
				super.Params.put(AnalyticalModel.MarginalUtilityofMoneyName,config.planCalcScore().getMarginalUtilityOfMoney());
				super.Params.put(AnalyticalModel.DistanceBasedMoneyCostCarName,config.planCalcScore().getOrCreateModeParams("car").getMonetaryDistanceRate());
				super.Params.put(AnalyticalModel.MarginalUtilityofTravelptName,config.planCalcScore().getOrCreateModeParams("pt").getMarginalUtilityOfTraveling());
				super.Params.put(AnalyticalModel.MarginalUtilityOfDistancePtName,config.planCalcScore().getOrCreateModeParams("pt").getMonetaryDistanceRate());
				super.Params.put(AnalyticalModel.MarginalUtilityofWaitingName,config.planCalcScore().getMarginalUtlOfWaitingPt_utils_hr());
				super.Params.put(AnalyticalModel.UtilityOfLineSwitchName,config.planCalcScore().getUtilityOfLineSwitch());
				super.Params.put(AnalyticalModel.MarginalUtilityOfWalkingName,config.planCalcScore().getOrCreateModeParams("walk").getMarginalUtilityOfTraveling());
				super.Params.put(AnalyticalModel.DistanceBasedMoneyCostWalkName,config.planCalcScore().getOrCreateModeParams("walk").getMonetaryDistanceRate());
				super.Params.put(AnalyticalModel.ModeConstantPtname,config.planCalcScore().getOrCreateModeParams("pt").getConstant());
				super.Params.put(AnalyticalModel.ModeConstantCarName,config.planCalcScore().getOrCreateModeParams("car").getConstant());
				super.Params.put(AnalyticalModel.MarginalUtilityofPerformName,config.planCalcScore().getPerforming_utils_hr());
			}
		super.Params.put("All "+AnalyticalModel.CapacityMultiplierName, config.qsim().getFlowCapFactor());
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario, Map<String,FareCalculator> fareCalculator) {
		this.lastPopulation = population;
		//System.out.println("");
		super.originalNetwork=network;
		this.odPairs = new CNLODpairs(network, transitSchedule, scenario, this.getTimeBeans());
		Config odConfig=ConfigUtils.createConfig();
		odConfig.network().setInputFile("data/odNetwork.xml");
		Network odNetwork=ScenarioUtils.loadScenario(odConfig).getNetwork();
		this.odPairs.generateODpairsetSubPop(null, population);//This network has priority over the constructor network. This allows to use a od pair specific network 
		this.odPairs.generateRouteandLinkIncidence(0.);
		for(String s:this.getTimeBeans().keySet()) {
			this.networks.put(s, new CNLNetwork(network, null));
			this.performTransitVehicleOverlay(this.networks.get(s),
					transitSchedule,scenario.getTransitVehicles(),this.getTimeBeans().get(s).getFirst(),
					this.getTimeBeans().get(s).getSecond());
			this.transitLinks.put(s,this.odPairs.getTransitLinks(this.getTimeBeans(),s));
		}
		this.fareCalculator = fareCalculator;
		
		this.ts = transitSchedule;
		for(String timeBeanId:this.getTimeBeans().keySet()) {
			this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
			this.originalDemand.put(timeBeanId, new HashMap<>(this.odPairs.getDemand(timeBeanId)));
			this.demand.put(timeBeanId, new HashMap<>(this.odPairs.getDemand(timeBeanId))); //The demand is also the same, fixed
			for(Id<AnalyticalModelODpair> odId:this.originalDemand.get(timeBeanId).keySet()) {
				double totalDemand=this.originalDemand.get(timeBeanId).get(odId);
				this.carDemand.get(timeBeanId).put(odId, 0.5*totalDemand);
				if(this.odPairs.getODpairset().get(odId).getSubPopulation().contains("GV")) {
					this.carDemand.get(timeBeanId).put(odId, totalDemand); //Load all demand to car for GV
				}
				//System.out.println();
			}
			
		}
		printDemandTotalAndAgentTripStat();
	}
	
	public static LinkedHashMap<String,Double>generateSubPopSpecificParam(Map<String, Double> params,String subPopName){
		LinkedHashMap<String,Double> specificParam=new LinkedHashMap<>();
		for(String s:params.keySet()) {
			if(s.contains(subPopName)) {
				specificParam.put(s.split(" ")[1],params.get(s));
			}
			if(s.contains("All")) {
				specificParam.put(s,params.get(s));
			}
		}
		return specificParam;
	}
	
	/**
	 * This function is a key function to generate the networks for the route creations.
	 */
	@Override
	public void generateRoutesAndODWithoutRoute(Population population,Network network, Lanes lanes, TransitSchedule transitSchedule,
			Scenario scenario, Map<String,FareCalculator> fareCalculator, TransferDiscountCalculator tdc, 
			double transitRatio, boolean carOnly) {
		
		if(lanes != null) containLinkToLink = true;
		this.scenario = scenario;
		this.odPairs = new CNLODpairs(scenario, this.timeBeans); //Create ODpairs
		super.originalNetwork=network;
		
		this.transitConfig = new FareTransitRouterConfig(scenario.getConfig().planCalcScore(),
				scenario.getConfig().plansCalcRoute(), scenario.getConfig().transitRouter(),
				scenario.getConfig().vspExperimental()); //transit config
		//trial
		Network net = NetworkUtils.createNetwork();
		new MatsimNetworkReader(net).readFile("data/odNetTPUSB.xml");
		this.odPairs.generateODpairsetWithoutRoutesSubPop(net, population, true, carOnly); //Load the network and make the OD pairs
		
		double totalTrip = 0;
		for(AnalyticalModelODpair odPair: this.odPairs.getODpairset().values()) {
			totalTrip += odPair.getTotalTrip();
		}
		logger.info("Total trip considering = "+totalTrip);
		
		SignalFlowReductionGenerator sg = new SignalFlowReductionGenerator(scenario);
		//this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String timeBin:this.timeBeans.keySet()) { ///Create network for each time bin
			CNLNetwork analyticalNetwork = new CNLNetwork(network, lanes);
			analyticalNetwork.updateGCRatio(sg);
			this.networks.put(timeBin, analyticalNetwork);
			TransitNetworkHR transitNetwork = TransitNetworkHR.createFromSchedule(scenario.getNetwork(), 
					scenario.getTransitSchedule(), scenario.getTransitVehicles(), 
					this.transitConfig.getBeelineWalkConnectionDistance(), this.timeBeans.get(timeBin));
			this.transitNetworks.put(timeBin, transitNetwork);
			this.transitLinks.put(timeBin, transitNetwork.getTransitLinkMap());
			this.performTransitVehicleOverlay(analyticalNetwork, transitSchedule, scenario.getTransitVehicles(),
					this.timeBeans.get(timeBin).getFirst(), this.timeBeans.get(timeBin).getSecond());
			//this.transitLinks.put(timeBin,this.odPairs.getTransitLinks(this.timeBeans,timeBin));
		}
		this.fareCalculator = fareCalculator;
		this.tdc = tdc;
		this.generateRoute(); //Generate the very first route
		this.odPairs.generateRouteandLinkIncidence(0);
		
		this.ts = transitSchedule;
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.consecutiveSUEErrorIncrease.put(timeBeanId, 0.);
			this.originalDemand.put(timeBeanId, new HashMap<>( this.odPairs.getDemand(timeBeanId) ));
			for(Id<AnalyticalModelODpair> odId : this.originalDemand.get(timeBeanId).keySet()) {
				double totalDemand=this.originalDemand.get(timeBeanId).get(odId);
				AnalyticalModelODpair odPair = this.odPairs.getODpairset().get(odId);
//				if(sk==null) {
//					System.out.println();
//				}
				if(odPair.getSubPopulation().contains("GV") ||  //If it is GV subpopulation
						odPair.getTrRoutes()==null || odPair.getTrRoutes().isEmpty()) { //If there are no transit routes
					this.carDemand.get(timeBeanId).put(odId, totalDemand); //Put all demand to car as it is all car.
				}else {
					this.carDemand.get(timeBeanId).put(odId, (1-transitRatio) * totalDemand); //Still put 0.5 if there are transit route
				}
			}
			
			logger.info("Starting from 0.5 auto and transit ratio");
			if(this.originalDemand.get(timeBeanId).size()!=this.carDemand.get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		printDemandTotalAndAgentTripStat();
	}
	
	@Override
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.getAnalyticalModelParamsLimit().put("LinkMiu", new Tuple<Double,Double>(0.004,0.008));
		this.getAnalyticalModelParamsLimit().put("ModeMiu", new Tuple<Double,Double>(0.01,0.1));
		this.getAnalyticalModelParamsLimit().put("BPRalpha", new Tuple<Double,Double>(0.10,0.20));
		this.getAnalyticalModelParamsLimit().put("BPRbeta", new Tuple<Double,Double>(3.,5.));
		this.getAnalyticalModelParamsLimit().put("Transferalpha", new Tuple<Double,Double>(0.25,0.75));
		this.getAnalyticalModelParamsLimit().put("Transferbeta", new Tuple<Double,Double>(0.75,1.5));
	}
	
	@Override
	protected Map<Id<Link>, Map<Id<Link>, Double>> networkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,Map<String,Double> params, Map<String, Double> anaParams){
		String s=this.odPairs.getODpairset().get(ODpairId).getSubPopulation();
		Map<String,Double> newParam = new HashMap<>(params);
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.networkLoadingCarSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected HashMap<Id<TransitLink>,Double> networkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,Map<String,Double> params, Map<String, Double> anaParams){
		String s= this.odPairs.getODpairset().get(ODpairId).getSubPopulation();
		Map<String,Double> newParam = new HashMap<>(params);
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.networkLoadingTransitSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected void performModalSplit(Map<String,Double>params,Map<String,Double>anaParams,String timeBeanId) {
		for(AnalyticalModelODpair odPair:this.odPairs.getODpairset().values()){
			
			//For GV car proportion is always 1
			if(odPair.getSubPopulation().contains("GV")) {
				double carDemand=this.demand.get(timeBeanId).get(odPair.getODpairId());
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			// if a phantom trip, car and pt proportion is decided from the simulation and will not be changed
			}else if(odPair.getSubPopulation().contains("trip")) {
				double carDemand=this.demand.get(timeBeanId).get(odPair.getODpairId())*odPair.getCarModalSplit();
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			}
			double demand=this.demand.get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) { 
			double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
			double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);
			
			if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
					Math.exp(transitUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.0);
				
			}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
					||Math.exp(carUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(), this.demand.get(timeBeanId).get(odPair.getODpairId()));
			}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(), 0.);
			}else {
				double carProportion=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")));
				//System.out.println("Car Proportion = "+carProportion);
				Double cardemand=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")))*this.demand.get(timeBeanId).get(odPair.getODpairId());
				if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("car demand is invalid");
				}
				this.carDemand.get(timeBeanId).put(odPair.getODpairId(),cardemand);
			}
		}
		}
	}
	
	public double getAverageLinkToLinkTravelTime(Id<Link> fromLinkId, Id<Link> toLinkId, double time) {
		if(containLinkToLink) {
			double linkTime = 0.0;
			for(String s : this.timeBeans.keySet()) {
				Tuple<Double, Double> times = this.timeBeans.get(s);
				if(times.getFirst() <= time && time <= times.getSecond()) {
					linkTime = ((CNLLinkToLink)this.networks.get(s).getLinks().get(fromLinkId)).getLinkToLinkTravelTime(toLinkId, times, this.Params, 
						this.AnalyticalModelInternalParams);
					return linkTime;
				}
			}
			return linkTime * 1000; //Return the final link time if their time is not found.
		}else {
			return getAverageLinkTravelTime(fromLinkId, time);
		}
	}
	
	@Override
	public Map<String,Map<Id<Link>, Double>> perFormSUE(Map<String, Double> noparams,
			Map<String,Double> anaParams, MeasurementDataContainer mdc) {
		LinkedHashMap<String,Double>params=this.pReader.ScaleUp(noparams);
		return super.perFormSUE(params, anaParams, mdc);
	}
	
	@Override
	public void setDefaultParameters(Map<String,Double> params) {
		super.setDefaultParameters(this.pReader.ScaleUp(params));
	}
	
}
