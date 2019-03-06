package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpairs;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TripChain;



public class CNLODpairs extends AnalyticalModelODpairs{

	private final Scenario scenario;
	private final TransitSchedule ts;
	
	public CNLODpairs(Scenario scenario, Map<String, Tuple<Double, Double>> timeBeans) {
		super(scenario.getNetwork(), timeBeans, scenario);
		this.scenario = scenario;
		this.ts = scenario.getTransitSchedule();
	}
	
	@Deprecated
	public CNLODpairs(Network network, TransitSchedule ts, Scenario scenario,Map<String, Tuple<Double, Double>> timeBeans) {
		super(network, timeBeans,scenario); //This is nothing, just put the variables
		this.scenario=scenario;
		this.ts = ts;
	}
	
	public CNLODpairs(String networkFileLoc,String populationFileLoc,TransitSchedule ts, Scenario scenario,HashMap<String,Tuple<Double,Double>> timeBean) {
		super(populationFileLoc,networkFileLoc,timeBean);
		this.scenario=scenario;
		this.ts=ts;
	
	}

	@Override
	protected TripChain getNewTripChain(Plan plan) {
		return new CNLTripChain(plan,this.ts,this.scenario);
		
	}

	
	@Override
	public Map<Id<TransitLink>, TransitLink> getTransitLinks(Map<String,Tuple<Double,Double>> timeBean,String timeBeanId){
		Map<Id<TransitLink>,TransitLink> transitLinks=new HashMap<>();
		for(AnalyticalModelODpair odPair:this.getODpairset().values()) {
			if(odPair.getTrRoutes(timeBean,timeBeanId)!=null && odPair.getTrRoutes(timeBean,timeBeanId).size()!=0) {
				for(AnalyticalModelTransitRoute tr:odPair.getTrRoutes(timeBean,timeBeanId)) {
					transitLinks.putAll(((CNLTransitRoute)tr).getTransitLinks(timeBean,timeBeanId));
				}
			}
		}
		return transitLinks;
	}

	
	
}
