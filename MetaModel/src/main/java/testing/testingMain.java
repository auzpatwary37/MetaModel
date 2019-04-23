package testing;

import java.io.IOException;
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
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.xml.sax.SAXException;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import dynamicTransitRouter.transfer.BusMinibusTransferDiscount;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;

public class testingMain {
	public static void main(String[] args) throws SAXException, IOException, ParserConfigurationException {
		Config config=ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/toyScenarioLargeData/configToyLargeMod.xml");
		config.transit().setTransitScheduleFile("data/transitSchedule.xml");
		config.transit().setVehiclesFile("data/transitVehicles.xml");
		config.network().setInputFile("data/network.xml");
		config.controler().setLastIteration(50);
		config.controler().setOutputDirectory("toyScenarioLarge/output"+1);
		config.transit().setUseTransit(true);
		config.plansCalcRoute().setInsertingAccessEgressWalk(false);
		config.qsim().setUsePersonIdForMissingVehicleId(true);
		
		config.parallelEventHandling().setNumberOfThreads(7);
		config.controler().setWritePlansInterval(50);
		config.qsim().setStartTime(0.0);
		config.qsim().setEndTime(28*3600);
	
		config.controler().setWriteEventsInterval(20);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());	
		Controler controler = new Controler(scenario);
//		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
//		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		Map<String,Tuple<Double,Double>> timeBean=new HashMap<>();
		for(int i=3;i<=29;i++) {
			timeBean.put(Integer.toString(i), new Tuple<Double,Double>((i-1)*3600.,i*3600.));
		}
		
		//saxParser.parse("data/busFare.xml", busFareGetter);
		Map<String,FareCalculator>fareCalculator= new HashMap<>();
		fareCalculator.put("bus", new UniformFareCalculator(5));
		fareCalculator.put("minibus",new UniformFareCalculator(7));
		fareCalculator.put("tram", new UniformFareCalculator(2.3));
		fareCalculator.put("ship", new UniformFareCalculator(3));
		fareCalculator.put("train",new MTRFareCalculator("fare/mtr_lines_fares.csv", scenario.getTransitSchedule()));
		fareCalculator.put("train",new LRFareCalculator("fare/light_rail_fares.csv"));
		
		CNLSUEModel anaModel=new CNLSUEModel(config,timeBean);
		TransferDiscountCalculator tdc = new BusMinibusTransferDiscount("fare/GMB.csv");
		anaModel.generateRoutesAndODWithoutRoute(scenario.getPopulation(), scenario.getNetwork(), scenario.getLanes(), 
				scenario.getTransitSchedule(), scenario, fareCalculator, tdc, 0.7, false);
		anaModel.generateMATSimRoutes(0.7, 30, 10);
		anaModel.assignRoutesToMATSimPopulation(scenario.getPopulation(), 0.7, false);
		System.out.println("wait!!!!");
				
	}
}
