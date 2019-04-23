package ust.hk.praisehk.shortestpath;


import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.algorithms.NetworkInverter;
import org.matsim.core.network.algorithms.NetworkTurnInfoBuilderI;
import org.matsim.core.network.algorithms.NetworkExpandNode.TurnInfo;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LinkToLinkTravelTime;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;

public class L2lLeastCostCalculatorFactory {

	private final L2lNetworkLeastCostPathCalculator invertedNetworkRouteAlgo;

	public L2lLeastCostCalculatorFactory(Scenario scenario, Set<String> allowedModes, AnalyticalModel trafficModel) {
		Network network=scenario.getNetwork();
		LinkToLinkTravelTime l2ltravelTimes = new MetaModelTravelTimeAndDisutility(-50, 1, -50, trafficModel); // link to link travel time
		NetworkTurnInfoBuilderI turnInfoBuilder = new CustomNetworkTurnInfoBuilder(scenario,
				allowedModes); // Turn info of the network

		Map<Id<Link>, List<TurnInfo>> allowedInLinkTurnInfoMap = turnInfoBuilder.createAllowedTurnInfos();
		//Create an inverted network that considers the turn restriction
		Network invertedNetwork = new NetworkInverter(network, allowedInLinkTurnInfoMap).getInvertedNetwork();

		LeastCostPathCalculator routeAlgo = new DijkstraFactory().createPathCalculator(invertedNetwork,
				new TravelTimesInvertedNetworkProxyMetaModel(network, l2ltravelTimes),
				new TravelTimesInvertedNetworkProxyMetaModel(network, l2ltravelTimes));

		invertedNetworkRouteAlgo = new L2lNetworkLeastCostPathCalculator(network, invertedNetwork, routeAlgo);
	}

	public L2lNetworkLeastCostPathCalculator getRoutingAlgo() {
		return invertedNetworkRouteAlgo;
	}

	private static class TravelTimesInvertedNetworkProxyMetaModel implements TravelTime, TravelDisutility {
		private Network network;
		private LinkToLinkTravelTime linkToLinkTravelTime;

		private TravelTimesInvertedNetworkProxyMetaModel(Network network, LinkToLinkTravelTime l2ltt) {
			this.linkToLinkTravelTime = l2ltt;
			this.network = network;
		}

		/**
		 * In this case the link given as parameter is a link from the inverted network.
		 * 
		 * @see org.matsim.core.router.util.TravelTime#getLinkTravelTime(Link, double,
		 *      Person, Vehicle)
		 */
		@Override
		public double getLinkTravelTime(Link invLink, double time, Person person, Vehicle vehicle) {
			return 0.1; //To trick the Dijkstra algorithm
//			Link fromLink = network.getLinks().get(Id.create(invLink.getFromNode().getId(), Link.class));
//			Link toLink = network.getLinks().get(Id.create(invLink.getToNode().getId(), Link.class));
//			return linkToLinkTravelTime.getLinkToLinkTravelTime(fromLink, toLink, time);
		}

		// TODO: Validate the travel disutility
		@Override
		public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
			//It is actually the linkTolinkTravelTime * 20.
			Link fromLink = network.getLinks().get(Id.create(link.getFromNode().getId(), Link.class));
			Link toLink = network.getLinks().get(Id.create(link.getToNode().getId(), Link.class));
			double disutility = linkToLinkTravelTime.getLinkToLinkTravelTime(fromLink, toLink, time) * 20;
			if(disutility <= 0) {
				throw new RuntimeException("The disutility must be positive!");
			}else if(disutility > 1e15) {
				throw new RuntimeException("The disutility is too large! Check!");
			}
			return disutility;
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return 0;
		}
	}

}
