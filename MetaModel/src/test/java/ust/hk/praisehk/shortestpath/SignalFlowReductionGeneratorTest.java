package ust.hk.praisehk.shortestpath;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.signals.data.SignalsData;
import org.matsim.contrib.signals.data.SignalsDataLoader;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

class SignalFlowReductionGeneratorTest {

	@Test
	void test() {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.loadConfig(config, "data/config_clean.xml");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.addScenarioElement(SignalsData.ELEMENT_NAME, new SignalsDataLoader(config).loadSignalsData());
		SignalFlowReductionGenerator generator = new SignalFlowReductionGenerator(scenario);
		
		assertEquals(0.425, generator.getGCratio(scenario.getNetwork().getLinks().get(Id.createLinkId("101736_101537")), null), 1e-5);
		assertEquals(1, generator.getGCratio(scenario.getNetwork().getLinks().get(Id.createLinkId("201138_101561")), null), 1e-5);
		
		assertEquals(0.44711538, generator.getGCratio(scenario.getNetwork().getLinks().get(Id.createLinkId("22528_101476")), null), 1e-5);	
		assertEquals(0.163461538, generator.getGCratio(scenario.getNetwork().getLinks().get(Id.createLinkId("22528_101476")), Id.createLinkId("101476_101477")), 1e-5);
	}

}
