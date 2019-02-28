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
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
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
			this.getDemand().put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.getCarDemand().put(timeBeanId, new HashMap<Id<AnalyticalModelODpair>, Double>());
			this.getTransitLinks().put(timeBeanId, new HashMap<Id<TransitLink>, TransitLink>());
			super.beta.put(timeBeanId, new ArrayList<Double>());
			this.error.put(timeBeanId, new ArrayList<Double>());
			this.error1.put(timeBeanId, new ArrayList<Double>());
			
		}
		logger.info("Analytical model created successfully.");
		
	}
	
	private void defaultParameterInitiation(Config config){
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
	}
	
	@Override
	public void generateRoutesAndOD(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		this.setLastPopulation(population);
		//System.out.println("");
		this.setOdPairs(new CNLODpairs(network,population,transitSchedule,scenario,this.getTimeBeans()));
		Config odConfig=ConfigUtils.createConfig();
		odConfig.network().setInputFile("data/odNetwork.xml");
		Network odNetwork=ScenarioUtils.loadScenario(odConfig).getNetwork();
		this.getOdPairs().generateODpairsetSubPop(null);//This network has priority over the constructor network. This allows to use a od pair specific network 
		this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String s:this.getTimeBeans().keySet()) {
			this.getNetworks().put(s, new CNLNetwork(network));
			this.performTransitVehicleOverlay(this.getNetworks().get(s),
					transitSchedule,scenario.getTransitVehicles(),this.getTimeBeans().get(s).getFirst(),
					this.getTimeBeans().get(s).getSecond());
			this.getTransitLinks().put(s,this.getOdPairs().getTransitLinks(this.getTimeBeans(),s));
		}
		this.setFareCalculator(fareCalculator);
		
		
		
		
		this.setTs(transitSchedule);
		for(String timeBeanId:this.getTimeBeans().keySet()) {
			this.getConsecutiveSUEErrorIncrease().put(timeBeanId, 0.);
			this.getDemand().put(timeBeanId, new HashMap<>(this.getOdPairs().getdemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.getDemand().get(timeBeanId).keySet()) {
				double totalDemand=this.getDemand().get(timeBeanId).get(odId);
				this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
				if(this.getOdPairs().getODpairset().get(odId).getSubPopulation().contains("GV")) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}
				//System.out.println();
			}
			
		}
		
		int agentTrip=0;
		int matsimTrip=0;
		int agentDemand=0;
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()) {
			agentTrip+=odPair.getAgentCounter();
			for(String s:odPair.getTimeBean().keySet()) {
				agentDemand+=odPair.getDemand().get(s);
			}
			
		}
		System.out.println("Demand total = "+agentDemand);
		System.out.println("Total Agent Trips = "+agentTrip);
	
	}
	
	private LinkedHashMap<String,Double>generateSubPopSpecificParam(LinkedHashMap<String,Double>originalparams,String subPopName){
		LinkedHashMap<String,Double> specificParam=new LinkedHashMap<>();
		for(String s:originalparams.keySet()) {
			if(s.contains(subPopName)) {
				specificParam.put(s.split(" ")[1],originalparams.get(s));
			}
		}
		return specificParam;
	}
	
	
	@Override
	public void generateRoutesAndODWithoutRoute(Population population,Network network,TransitSchedule transitSchedule,
			Scenario scenario,Map<String,FareCalculator> fareCalculator) {
		this.setLastPopulation(population);
		//System.out.println("");
		this.scenario=scenario;
		this.setOdPairs(new CNLODpairs(network,population,transitSchedule,scenario,this.timeBeans));
		
		//trial
		Config odConfig=ConfigUtils.createConfig();
		odConfig.network().setInputFile("data/odNetTPUSB.xml");
		Scenario odScenario=ScenarioUtils.loadScenario(odConfig);
		Network net=odScenario.getNetwork();
		
		this.getOdPairs().generateODpairsetWithoutRoutesSubPop(net);
		
		SignalFlowReductionGenerator sg=new SignalFlowReductionGenerator(scenario);
		//this.getOdPairs().generateRouteandLinkIncidence(0.);
		for(String s:this.timeBeans.keySet()) {
			
			this.getNetworks().put(s, new CNLNetwork(network));
			this.getNetworks().get(s).updateGCRatio(sg);
			this.performTransitVehicleOverlay(this.getNetworks().get(s),
					transitSchedule,scenario.getTransitVehicles(),this.timeBeans.get(s).getFirst(),
					this.timeBeans.get(s).getSecond());
			this.getTransitLinks().put(s,this.getOdPairs().getTransitLinks(this.timeBeans,s));
		}
		this.generateRoute();
		this.getOdPairs().generateRouteandLinkIncidence(0);
		this.fareCalculator=fareCalculator;
		
		
		this.setTs(transitSchedule);
		for(String timeBeanId:this.timeBeans.keySet()) {
			this.getConsecutiveSUEErrorIncrease().put(timeBeanId, 0.);
			this.getDemand().put(timeBeanId, new HashMap<>(this.getOdPairs().getdemand(timeBeanId)));
			for(Id<AnalyticalModelODpair> odId:this.getDemand().get(timeBeanId).keySet()) {
				double totalDemand=this.getDemand().get(timeBeanId).get(odId);
				if(this.getOdPairs().getODpairset().get(odId).getTrRoutes().isEmpty()) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}else {
					this.getCarDemand().get(timeBeanId).put(odId, 0.5*totalDemand);
			
				}
				if(this.getOdPairs().getODpairset().get(odId).getSubPopulation().contains("GV")) {
					this.getCarDemand().get(timeBeanId).put(odId, totalDemand);
				}
			}
			logger.info("Startig from 0.5 auto and transit ratio");
			if(this.getDemand().get(timeBeanId).size()!=this.getCarDemand().get(timeBeanId).size()) {
				logger.error("carDemand and total demand do not have same no of OD pair. This should not happen. Please check");
			}
		}
		
		int agentTrip=0;
		int matsimTrip=0;
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
	protected void loadAnalyticalModelInternalPamamsLimit() {
		this.getAnalyticalModelParamsLimit().put("LinkMiu", new Tuple<Double,Double>(0.004,0.008));
		this.getAnalyticalModelParamsLimit().put("ModeMiu", new Tuple<Double,Double>(0.01,0.1));
		this.getAnalyticalModelParamsLimit().put("BPRalpha", new Tuple<Double,Double>(0.10,0.20));
		this.getAnalyticalModelParamsLimit().put("BPRbeta", new Tuple<Double,Double>(3.,5.));
		this.getAnalyticalModelParamsLimit().put("Transferalpha", new Tuple<Double,Double>(0.25,0.75));
		this.getAnalyticalModelParamsLimit().put("Transferbeta", new Tuple<Double,Double>(0.75,1.5));
	}
	
	@Override
	protected HashMap<Id<Link>,Double> NetworkLoadingCarSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,double counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=params;
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingCarSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected HashMap<Id<TransitLink>,Double> NetworkLoadingTransitSingleOD(Id<AnalyticalModelODpair> ODpairId,String timeBeanId,int counter,LinkedHashMap<String,Double> params, LinkedHashMap<String, Double> anaParams){
		String s=this.getOdPairs().getODpairset().get(ODpairId).getSubPopulation();
		LinkedHashMap<String,Double>newParam=params;
		if(s!=null) {
			newParam=this.generateSubPopSpecificParam(params, s);
		}
		return super.NetworkLoadingTransitSingleOD(ODpairId, timeBeanId, counter, newParam, anaParams);
	}
	
	@Override
	protected void performModalSplit(LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams,String timeBeanId) {
		for(AnalyticalModelODpair odPair:this.getOdPairs().getODpairset().values()){
			
			//For GV car proportion is always 1
			if(odPair.getSubPopulation().contains("GV")) {
				double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			// if a phantom trip, car and pt proportion is decided from the simulation and will not be changed
			}else if(odPair.getSubPopulation().contains("trip")) {
				double carDemand=this.getDemand().get(timeBeanId).get(odPair.getODpairId())*odPair.getCarModalSplit();
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),carDemand);
				continue;
			}
			double demand=this.getDemand().get(timeBeanId).get(odPair.getODpairId());
			if(demand!=0) { 
			double carUtility=odPair.getExpectedMaximumCarUtility(params, anaParams, timeBeanId);
			double transitUtility=odPair.getExpectedMaximumTransitUtility(params, anaParams, timeBeanId);
			
			if(carUtility==Double.NEGATIVE_INFINITY||transitUtility==Double.POSITIVE_INFINITY||
					Math.exp(transitUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.0);
				
			}else if(transitUtility==Double.NEGATIVE_INFINITY||carUtility==Double.POSITIVE_INFINITY
					||Math.exp(carUtility*anaParams.get("ModeMiu"))==Double.POSITIVE_INFINITY) {
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), this.getDemand().get(timeBeanId).get(odPair.getODpairId()));
			}else if(carUtility==Double.NEGATIVE_INFINITY && transitUtility==Double.NEGATIVE_INFINITY){
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(), 0.);
			}else {
				double carProportion=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")));
				//System.out.println("Car Proportion = "+carProportion);
				Double cardemand=Math.exp(carUtility*anaParams.get("ModeMiu"))/(Math.exp(carUtility*anaParams.get("ModeMiu"))+Math.exp(transitUtility*anaParams.get("ModeMiu")))*this.getDemand().get(timeBeanId).get(odPair.getODpairId());
				if(cardemand==Double.NaN||cardemand==Double.POSITIVE_INFINITY||cardemand==Double.NEGATIVE_INFINITY) {
					throw new IllegalArgumentException("car demand is invalid");
				}
				this.getCarDemand().get(timeBeanId).put(odPair.getODpairId(),cardemand);
			}
		}
		}
	}
	
	
	
	
	
	@Override
	public Map<String,Map<Id<Link>, Double>> perFormSUE(LinkedHashMap<String, Double> noparams,LinkedHashMap<String,Double> anaParams) {
		LinkedHashMap<String,Double>params=this.pReader.ScaleUp(noparams);
		return super.perFormSUE(params, anaParams);
	}
	
	@Override
	public void setDefaultParameters(LinkedHashMap<String,Double> params) {
		super.setDefaultParameters(this.pReader.ScaleUp(params));
	}
	
}
