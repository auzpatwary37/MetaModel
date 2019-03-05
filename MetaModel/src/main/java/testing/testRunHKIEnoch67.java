package testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.vehicles.VehicleType;
import org.xml.sax.SAXException;

import dynamicTransitRouter.TransitRouterFareDynamicImpl;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModelSubPop;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;

public class testRunHKIEnoch67 {
public static void main(String[] args) throws IOException, SAXException, ParserConfigurationException {
	Config config = ConfigUtils.createConfig();
	ConfigUtils.loadConfig(config, "data/config_clean.xml");
	
	String PersonChangeWithCar_NAME = "person_TCSwithCar";
	String PersonChangeWithoutCar_NAME = "person_TCSwithoutCar";
	
	String PersonFixed_NAME = "trip_TCS";
	String GVChange_NAME = "person_GV";
	String GVFixed_NAME = "trip_GV";
	
	ArrayList<String>subPopNames=new ArrayList<>();
	subPopNames.add(PersonChangeWithCar_NAME);
	subPopNames.add(PersonChangeWithoutCar_NAME);
	subPopNames.add(PersonFixed_NAME);
	subPopNames.add(GVChange_NAME);
	subPopNames.add(GVFixed_NAME);
	
	Config configGV = ConfigUtils.createConfig();
	ConfigUtils.loadConfig(configGV, "data/config_Ashraf.xml");
	for (ActivityParams act: configGV.planCalcScore().getActivityParams()) {
		if(act.getActivityType().contains("Usual place of work")) {
			act.setMinimalDuration(3600 * 2);
		}
		if(config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).getActivityParams(act.getActivityType())==null) {
			config.planCalcScore().getScoringParameters(PersonChangeWithCar_NAME).addActivityParams(act);
			config.planCalcScore().getScoringParameters(PersonChangeWithoutCar_NAME).addActivityParams(act);
			config.planCalcScore().getScoringParameters(PersonFixed_NAME).addActivityParams(act);
			config.planCalcScore().getScoringParameters(GVChange_NAME).addActivityParams(act);
			config.planCalcScore().getScoringParameters(GVFixed_NAME).addActivityParams(act);
		}
	}
	
	config.controler().setFirstIteration(0);
	config.controler().setLastIteration(400);
	config.strategy().setFractionOfIterationsToDisableInnovation(0.9);
	config.controler().setOverwriteFileSetting(OverwriteFileSetting.failIfDirectoryExists);
	config.controler().setWriteEventsInterval(25);
	config.controler().setWritePlansInterval(25);
	config.planCalcScore().setWriteExperiencedPlans(false);
	config.removeModule("roadpricing");
//    EmissionsConfigGroup ecg = new EmissionsConfigGroup() ;
//    config.addModule(ecg);
	config.removeModule("emissions");

	TransitRouterFareDynamicImpl.distanceFactor = 0.034;
	config.controler().setOutputDirectory(
			"outputHKISCap1.3Feb2/");
	//Updated: The new transit router is applied
	//config.plans().setInputFile("outputHKISCap1.3Updated/output_plans.xml.gz");
	config.plans().setInputFile("data/populationHKI.xml");
	config.plans().setInputPersonAttributeFile("data/personAttributesHKI.xml");
	config.plans().setSubpopulationAttributeName("SUBPOP_ATTRIB_NAME"); /* This is the default anyway. */
	config.vehicles().setVehiclesFile("data/VehiclesHKI.xml");
	config.qsim().setNumberOfThreads(7);
	config.qsim().setStorageCapFactor(1.3);
	config.qsim().setFlowCapFactor(1.1);
	
	config.global().setNumberOfThreads(27);
	config.parallelEventHandling().setNumberOfThreads(10);
	config.parallelEventHandling().setEstimatedNumberOfEvents((long) 1000000000);
//	
//	RunUtils.createStrategies(config, PersonChangeWithCar_NAME, 0.02, 0.02, 0.005, 0);
//	RunUtils.createStrategies(config, PersonChangeWithoutCar_NAME, 0.02, 0.02, 0.005, 0);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithCar_NAME, 
//			0.015, 150);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
//			0.015, 150);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithCar_NAME, 
//			0.02, 50);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithCar_NAME, 
//			0.02, 50);
//	
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithoutCar_NAME, 
//			0.015, 130);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
//			0.015, 130);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.toString(), PersonChangeWithoutCar_NAME, 
//			0.02, 50);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString(), PersonChangeWithoutCar_NAME, 
//			0.02, 50);
//	
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), PersonFixed_NAME, 
//			0.03, 170);
//	RunUtils.addStrategy(config, DefaultPlanStrategiesModule.DefaultStrategy.ReRoute.toString(), GVFixed_NAME, 
//			0.03, 170);
//	
//	RunUtils.createStrategies(config, PersonFixed_NAME, 0.02, 0.01, 0, 250);
//	RunUtils.createStrategies(config, GVChange_NAME, 0.015, 0.01, 0, 0);
//	RunUtils.createStrategies(config, GVFixed_NAME, 0.02, 0.01, 0, 250);

	//Create CadytsConfigGroup with defaultValue of everything
	
//	CadytsConfigGroup cadytsConfig=new CadytsConfigGroup();
//	cadytsConfig.setEndTime((int)config.qsim().getEndTime());
//	cadytsConfig.setFreezeIteration(Integer.MAX_VALUE);
//	cadytsConfig.setMinFlowStddev_vehPerHour(25);
//	cadytsConfig.setPreparatoryIterations(10);
//	cadytsConfig.setRegressionInertia(.95);
//	cadytsConfig.setStartTime(0);
//	cadytsConfig.setTimeBinSize(3600);
//	cadytsConfig.setUseBruteForce(false);
//	cadytsConfig.setWriteAnalysisFile(true);
//	cadytsConfig.setVarianceScale(1.0);
	
	//add the cadyts config 
	
	//config.addModule(cadytsConfig);
	
	//general Run Configuration
	config.counts().setInputFile("data/ATCCountsPeakHourLink.xml");
	
	Scenario scenario = ScenarioUtils.loadScenario(config);
	
//	Network ctsNet = NetworkUtils.createNetwork();
//	new MatsimNetworkReader(ctsNet).readFile("input/network_TD.xml");
//	AssignLinkToPlanActivity.defineMatchingTablePath("matching/");
//	AssignLinkToPlanActivity.run(scenario, ctsNet, 28, true);
//	PopulationWriter popWriter=new PopulationWriter(scenario.getPopulation());
//	popWriter.write("output/FinalHKITCSandGVTCS/populationHKI.xml");
	
	scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
//	RunUtils.scaleDownPopulation(scenario.getPopulation(), 0.15);
//	RunUtils.scaleDownPt(scenario.getTransitVehicles(), 0.15);
	for(VehicleType vt: scenario.getVehicles().getVehicleTypes().values()) {
		if(vt.getPcuEquivalents()==1) {
			vt.setPcuEquivalents(0.7);
		}
	}
	//Controler controler = new Controler(scenario);

	ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
	SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();

	saxParser.parse("data/busFare.xml", busFareGetter);
	
	Map<String,FareCalculator>fareCalculator= new HashMap<>();
	fareCalculator.put("bus", busFareGetter.get());
	fareCalculator.put("minibus",new UniformFareCalculator(7));
	fareCalculator.put("tram", new UniformFareCalculator(2.3));
	fareCalculator.put("ship", new UniformFareCalculator(3));
	fareCalculator.put("train",new MTRFareCalculator("fare/mtr_lines_fares.csv", scenario.getTransitSchedule()));
	fareCalculator.put("LR",new LRFareCalculator("fare/light_rail_fares.csv"));
	
	Map<String,Tuple<Double,Double>> timeBean=new HashMap<>();
	for(int i=3;i<=29;i++) {
		timeBean.put(Integer.toString(i), new Tuple<Double,Double>((i-1)*3600.,i*3600.));
		
	}
	
	
	//Object a=scenario.getPopulation().getPersonAttributes();
	CNLSUEModelSubPop anaModel=new CNLSUEModelSubPop(config,ParamReader.getDefaultTimeBean(),subPopNames);
	anaModel.generateRoutesAndODWithoutRoute(scenario.getPopulation(), scenario.getNetwork(), scenario.getTransitSchedule(), scenario, fareCalculator);
	anaModel.generateMATSimRoutes(0.7);
	System.out.println("wait!!!!");
	// Add the signal module to the controller
	//Signals.configure(controler);
}
}
