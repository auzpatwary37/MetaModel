package ust.hk.praisehk.metamodelcalibration.matsimIntegration;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import transitCalculatorsWithFare.TransitFareHandler;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

public class AnaModelControlerListener implements StartupListener,BeforeMobsimListener, AfterMobsimListener,IterationEndsListener, ShutdownListener{
	private boolean generateOD=true;
	private Scenario scenario;
	@Inject
	private AnalyticalModel SueAssignment;
	@Inject
	private @Named("CalibrationCounts") Measurements calibrationMeasurements;
	private String fileLoc;
	@Inject
	private LinkCountEventHandler pcuVolumeCounter;
	private MeasurementsStorage storage;
	@Inject
	private @Named("CurrentParam") paramContainer currentParam;
	
	@Inject
	private TransitFareHandler transitFares;
	
	private int maxIter;
	private final Map<String, FareCalculator> farecalc;
	private double busFareCollected;
	private int AverageCountOverNoOfIteration=5;
	private boolean shouldAverageOverIteration=true;
	private Map<String, Map<Id<Link>, Double>> counts=null;
	
	@Inject	private EventsManager eventsManager;
	
	@Inject
	public AnaModelControlerListener(Scenario scenario,AnalyticalModel sueAssignment, 
			Map<String,FareCalculator> farecalc,@Named("fileLoc") String fileLoc,@Named("generateRoutesAndOD") boolean generateRoutesAndOD, MeasurementsStorage storage){
		this.SueAssignment=sueAssignment;
		this.farecalc=farecalc;
		this.scenario=scenario;
		this.fileLoc=fileLoc;
		this.generateOD=generateRoutesAndOD;
		this.storage=storage;
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		this.eventsManager.addHandler(pcuVolumeCounter);
		this.maxIter=event.getServices().getConfig().controler().getLastIteration();
		}
	
	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		this.pcuVolumeCounter.resetLinkCount();
	}
	

	public void notifyAfterMobsim(AfterMobsimEvent event) {
		if(this.shouldAverageOverIteration) {
			int counter=event.getIteration();
			if(counter>this.maxIter-5) { //If it is final iterations
				if(this.counts==null) {
					counts=new HashMap<>(this.pcuVolumeCounter.geenerateLinkCounts()); //Store a value
				}else {
					Map<String,Map<Id<Link>,Double>> newcounts=this.pcuVolumeCounter.geenerateLinkCounts();
					for(String s:this.counts.keySet()) {
						for(Id<Link> lId:this.counts.get(s).keySet()) {
							counts.get(s).put(lId, counts.get(s).get(lId)+newcounts.get(s).get(lId)); //or add it
						}
					}
					busFareCollected += transitFares.getBusFareCollected().doubleValue();
				}
			}
			if(counter==this.maxIter) {
				//finally divide it by the number of iterations
				for(String s:this.counts.keySet()) {
					for(Id<Link> lId:this.counts.get(s).keySet()) {
						counts.get(s).put(lId, counts.get(s).get(lId)/this.AverageCountOverNoOfIteration);
					}
				}
				busFareCollected /= this.AverageCountOverNoOfIteration;
				Measurements m=storage.getCalibrationMeasurements().clone();
				m.updateMeasurements(counts); //Store the counts to the measurement object.
				m.setBusProfit(busFareCollected);
				//new MeasurementsWriter(m).write();
				this.storage.storeMeasurements(this.currentParam.getParam(), m);
			}
		}else {
			int counter=event.getIteration();
			if(counter==this.maxIter) {
				Measurements m=storage.getCalibrationMeasurements().clone();
				m.updateMeasurements(this.pcuVolumeCounter.geenerateLinkCounts());
				m.setBusProfit(transitFares.getBusFareCollected().doubleValue());
				//m.writeCSVMeasurements(fileLoc);
				this.storage.storeMeasurements(this.currentParam.getParam(), m);
			}
		}
		
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		
		
	}

	

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		if(this.generateOD) {
			this.SueAssignment.generateRoutesAndOD(event.getServices().getScenario().getPopulation(),
					event.getServices().getScenario().getNetwork(),
					event.getServices().getScenario().getTransitSchedule(),
					event.getServices().getScenario(), this.farecalc);
		}
	}

	public int getAverageCountOverNoOfIteration() {
		return AverageCountOverNoOfIteration;
	}

	public void setAverageCountOverNoOfIteration(int averageCountOverNoOfIteration) {
		AverageCountOverNoOfIteration = averageCountOverNoOfIteration;
	}

	public boolean isShouldAverageOverIteration() {
		return shouldAverageOverIteration;
	}

	public void setShouldAverageOverIteration(boolean shouldAverageOverIteration) {
		this.shouldAverageOverIteration = shouldAverageOverIteration;
	}
	
	
	
}