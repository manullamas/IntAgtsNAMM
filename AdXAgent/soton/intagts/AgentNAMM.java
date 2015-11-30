package soton.intagts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import se.sics.isl.transport.Transportable;
import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;
import tau.tac.adx.ads.properties.AdType;
import tau.tac.adx.demand.CampaignStats;
import tau.tac.adx.devices.Device;
import tau.tac.adx.props.AdxBidBundle;
import tau.tac.adx.props.AdxQuery;
import tau.tac.adx.props.PublisherCatalog;
import tau.tac.adx.props.PublisherCatalogEntry;
import tau.tac.adx.report.adn.AdNetworkReport;
import tau.tac.adx.report.adn.MarketSegment;
import tau.tac.adx.report.demand.AdNetBidMessage;
import tau.tac.adx.report.demand.AdNetworkDailyNotification;
import tau.tac.adx.report.demand.CampaignOpportunityMessage;
import tau.tac.adx.report.demand.CampaignReport;
import tau.tac.adx.report.demand.CampaignReportKey;
import tau.tac.adx.report.demand.InitialCampaignMessage;
import tau.tac.adx.report.demand.campaign.auction.CampaignAuctionReport;
import tau.tac.adx.report.publisher.AdxPublisherReport;
import tau.tac.adx.report.publisher.AdxPublisherReportEntry;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BankStatus;

/**
 * 
 * @author Mariano Schain
 * Test plug-in
 * 
 */
public class AgentNAMM extends Agent {

	private final Logger log = Logger
			.getLogger(AgentNAMM.class.getName());

	/*
	 * Basic simulation information. An agent should receive the {@link
	 * StartInfo} at the beginning of the game or during recovery.
	 */
	@SuppressWarnings("unused")
	private StartInfo startInfo;

	/**
	 * Messages received:
	 * 
	 * We keep all the {@link CampaignReport campaign reports} delivered to the
	 * agent. We also keep the initialization messages {@link PublisherCatalog}
	 * and {@link InitialCampaignMessage} and the most recent messages and
	 * reports {@link CampaignOpportunityMessage}, {@link CampaignReport}, and
	 * {@link AdNetworkDailyNotification}.
	 */
	private final Queue<CampaignReport> campaignReports;
	private PublisherCatalog publisherCatalog;
	private InitialCampaignMessage initialCampaignMessage;
	private AdNetworkDailyNotification adNetworkDailyNotification;

	/*
	 * The addresses of server entities to which the agent should send the daily
	 * bids data
	 */
	private String demandAgentAddress;
	private String adxAgentAddress;


	/*
	 * we maintain a list of queries - each characterized by the web site (the
	 * publisher), the device type, the ad type, and the user market segment
	 */
	private AdxQuery[] queries;

	/**
	 * Information regarding the latest campaign opportunity announced
	 */
	private CampaignData pendingCampaign;

	/**
	 * We maintain a collection (mapped by the campaign id) of the campaigns won
	 * by our agent.
	 */
	private Map<Integer, CampaignData> myCampaigns;
	/*
	 * the bidBundle to be sent daily to the AdX
	 */
	private AdxBidBundle bidBundle;

	/*
	 * The current bid level for the user classification service
	 */
	double ucsBid;

	/*
	 * The targeted service level for the user classification service
	 */
	int ucsTargetLevel;

	/*
	 *  The bid campaign bid we send.
	 *  Note it is not reset each day on purpose so there is a default value in case we fail to calculate a bid in time.
	 */
	long cmpBidMillis;

	/*
	 * current day of simulation
	 */
	private int day;
	private String[] publisherNames;
	private CampaignData currCampaign;

	public AgentNAMM() {
		campaignReports = new LinkedList<CampaignReport>();
	}

	@Override
	protected void messageReceived(Message message) {
		try {
			Transportable content = message.getContent();

			// Dumps all recieved messages to log
			log.fine(message.getContent().getClass().toString());
			this.log.log(Level.ALL, message.getContent().getClass().toString());

			if (content instanceof InitialCampaignMessage) {
				handleInitialCampaignMessage((InitialCampaignMessage) content);
			} else if (content instanceof CampaignOpportunityMessage) {
				handleICampaignOpportunityMessage((CampaignOpportunityMessage) content);
			} else if (content instanceof CampaignReport) {
				handleCampaignReport((CampaignReport) content);
			} else if (content instanceof AdNetworkDailyNotification) {
				handleAdNetworkDailyNotification((AdNetworkDailyNotification) content);
			} else if (content instanceof AdxPublisherReport) {
				handleAdxPublisherReport((AdxPublisherReport) content);
			} else if (content instanceof SimulationStatus) {
				handleSimulationStatus((SimulationStatus) content);
			} else if (content instanceof PublisherCatalog) {
				handlePublisherCatalog((PublisherCatalog) content);
			} else if (content instanceof AdNetworkReport) {
				handleAdNetworkReport((AdNetworkReport) content);
			} else if (content instanceof StartInfo) {
				handleStartInfo((StartInfo) content);
			} else if (content instanceof BankStatus) {
				handleBankStatus((BankStatus) content);
			} else if(content instanceof CampaignAuctionReport) {
				hadnleCampaignAuctionReport((CampaignAuctionReport) content);
			}
			else {
				System.out.println("UNKNOWN Message Received: " + content);
			}

		} catch (NullPointerException e) {
			this.log.log(Level.SEVERE,
					"Exception thrown while trying to parse message." + e);
			return;
		}
	}

	private void hadnleCampaignAuctionReport(CampaignAuctionReport content) {
		// ingoring
	}

	private void handleBankStatus(BankStatus content) {
		System.out.println("Day " + day + " :" + content.toString());
	}

	/**
	 * Processes the start information.
	 *
	 * @param startInfo
	 *            the start information.
	 */
	protected void handleStartInfo(StartInfo startInfo) {
		this.startInfo = startInfo;
	}

	/**
	 * Process the reported set of publishers
	 *
	 * @param publisherCatalog
	 */
	private void handlePublisherCatalog(PublisherCatalog publisherCatalog) {
		this.publisherCatalog = publisherCatalog;
		generateAdxQuerySpace();
		getPublishersNames();

	}

	/**
	 * On day 0, a campaign (the "initial campaign") is allocated to each
	 * competing agent. The campaign starts on day 1. The address of the
	 * server's AdxAgent (to which bid bundles are sent) and DemandAgent (to
	 * which bids regarding campaign opportunities may be sent in subsequent
	 * days) are also reported in the initial campaign message
	 */
	private void handleInitialCampaignMessage(
			InitialCampaignMessage campaignMessage) {
		System.out.println(campaignMessage.toString());

		day = 0;

		initialCampaignMessage = campaignMessage;
		demandAgentAddress = campaignMessage.getDemandAgentAddress();
		adxAgentAddress = campaignMessage.getAdxAgentAddress();

		CampaignData campaignData = new CampaignData(initialCampaignMessage);
		campaignData.setBudget(initialCampaignMessage.getBudgetMillis()/1000.0);
		currCampaign = campaignData;
		genCampaignQueries(currCampaign);

		/*
		 * The initial campaign is already allocated to our agent so we add it
		 * to our allocated-campaigns list.
		 */
		System.out.println("Day " + day + ": Allocated campaign - " + campaignData);
		myCampaigns.put(initialCampaignMessage.getId(), campaignData);
	}

	/**
	 * On day n ( > 0) a campaign opportunity is announced to the competing
	 * agents. The campaign starts on day n + 2 or later and the agents may send
	 * (on day n) related bids (attempting to win the campaign). The allocation
	 * (the winner) is announced to the competing agents during day n + 1.
	 */
	private void handleICampaignOpportunityMessage(
			CampaignOpportunityMessage com) {

		day = com.getDay();

		pendingCampaign = new CampaignData(com);
		System.out.println("Day " + day + ": Campaign opportunity - " + pendingCampaign);

		/*
		*  ALUN: Decide which of the 4 campaign strategies to use
		*/
		long cmpimps = com.getReachImps();
		int startDays = 5;

		// Starting strategy for first few days
		if (day <= startDays) {
			cmpBidMillis = campaignStartingStrategy();
		}
		// Quality recovery when quality is too low
		else if (adNetworkDailyNotification.getQualityScore() < 1) { // Condition for Quality strategy
			cmpBidMillis = campaignQualityRecoveryStrategy();
		}
		else cmpBidMillis = campaignProfitStrategy();
		// If bid is too high, just bid the maximum value.
		if (cmpBidMillis >= 0.8*cmpimps) {
			cmpBidMillis = cmpimps; //TODO: quality is factored into reserve price
			System.out.println("Day " + day + ": Campaign - Bid " + cmpBidMillis + " too high");
		}

		/*
		 * The campaign requires com.getReachImps() impressions. The competing
		 * Ad Networks bid for the total campaign Budget (that is, the ad
		 * network that offers the lowest budget gets the campaign allocated).
		 * The advertiser is willing to pay the AdNetwork at most 1$ CPM,
		 * therefore the total number of impressions may be treated as a reserve
		 * (upper bound) price for the auction.
		 */


		System.out.println("Day " + day + ": Campaign - Total budget bid (millis): " + cmpBidMillis);

		/*
		 * Adjust ucs bid s.t. target level is achieved. Note: The bid for the
		 * user classification service is piggybacked
		 */
		// TODO: UCS bid calculation here
		Random random = new Random();
		if (adNetworkDailyNotification != null) {
			double ucsLevel = adNetworkDailyNotification.getServiceLevel();
			ucsBid = 0.1 + random.nextDouble()/10.0;
			System.out.println("Day " + day + ": ucs level reported: " + ucsLevel);
		} else {
			System.out.println("Day " + day + ": Initial ucs bid is " + ucsBid);
		}

		/* Note: Campaign bid is in millis */
		AdNetBidMessage bids = new AdNetBidMessage(ucsBid, pendingCampaign.id, cmpBidMillis);
		sendMessage(demandAgentAddress, bids);
	}


	 /**
	 * On day n ( > 0), the result of the UserClassificationService and Campaign
	 * auctions (for which the competing agents sent bids during day n -1) are
	 * reported. The reported Campaign starts in day n+1 or later and the user
	 * classification service level is applicable starting from day n+1.
	 */
	private void handleAdNetworkDailyNotification(
			AdNetworkDailyNotification notificationMessage) {

		adNetworkDailyNotification = notificationMessage;

		System.out.println("Day " + day + ": Daily notification for campaign "
				+ adNetworkDailyNotification.getCampaignId());

		String campaignAllocatedTo = " allocated to "
				+ notificationMessage.getWinner();

		if ((pendingCampaign.id == adNetworkDailyNotification.getCampaignId())
				&& (notificationMessage.getCostMillis() != 0)) {

			/* add campaign to list of won campaigns */
			pendingCampaign.setBudget(notificationMessage.getCostMillis()/1000.0);
			pendingCampaign.setBid(cmpBidMillis);
			pendingCampaign.impressionTarget = setImpressionTargets();
			currCampaign = pendingCampaign;
			genCampaignQueries(currCampaign);
			myCampaigns.put(pendingCampaign.id, pendingCampaign);

			campaignAllocatedTo = " WON at cost (Millis)"
					+ notificationMessage.getCostMillis();
		}

		System.out.println("Day " + day + ": " + campaignAllocatedTo
				+ ". UCS Level set to " + notificationMessage.getServiceLevel()
				+ " at price " + notificationMessage.getPrice()
				+ " Quality Score is: " + notificationMessage.getQualityScore());
	}

	/**
	 * The SimulationStatus message received on day n indicates that the
	 * calculation time is up and the agent is requested to send its bid bundle
	 * to the AdX.
	 */
	private void handleSimulationStatus(SimulationStatus simulationStatus) {
		System.out.println("Day " + day + " : Simulation Status Received");
		sendBidAndAds();
		System.out.println("Day " + day + " ended. Starting next day");
		++day;
	}

	/**
	 * Miguel
	 * 
	 */
	protected void sendBidAndAds() {

		bidBundle = new AdxBidBundle();

		/*
		 *  TODO: CREATE OUR BID BUNDLE HERE
		 */

		int dayBiddingFor = day + 1;

		/* A fixed random bid, for all queries of the campaign */
		/*
		 * Note: bidding per 1000 imps (CPM) - no more than average budget
		 * revenue per imp
		 */

		double rbid = 10000.0;

		/*
		 * add bid entries w.r.t. each active campaign with remaining contractedcmpBidMillis
		 * impressions.
		 *
		 * for now, a single entry per active campaign is added for queries of
		 * matching target segment.
		 */

		if ((dayBiddingFor >= currCampaign.dayStart)
				&& (dayBiddingFor <= currCampaign.dayEnd)
				&& (currCampaign.impsTogo() > 0)) {

			int entCount = 0;

			for (AdxQuery query : currCampaign.campaignQueries) {
				if (currCampaign.impsTogo() - entCount > 0) {    // TODO: Consider overachieving campaigns when quality < 1 
					/*
					 * among matching entries with the same campaign id, the AdX
					 * randomly chooses an entry according to the designated
					 * weight. by setting a constant weight 1, we create a
					 * uniform probability over active campaigns(irrelevant because we are bidding only on one campaign)
					 */
					if (query.getDevice() == Device.pc) {
						if (query.getAdType() == AdType.text) {
							entCount++;
						} else {
							entCount += currCampaign.videoCoef;
						}
					} else {
						if (query.getAdType() == AdType.text) {
							entCount+=currCampaign.mobileCoef;
						} else {
							entCount += currCampaign.videoCoef + currCampaign.mobileCoef;
						}

					}
					bidBundle.addQuery(query, rbid, new Ad(null),
							currCampaign.id, 1);
				}
			}

			double impressionLimit = currCampaign.impsTogo();
			double budgetLimit = currCampaign.budget;
			bidBundle.setCampaignDailyLimit(currCampaign.id,
					(int) impressionLimit, budgetLimit);

			System.out.println("Day " + day + " #####BIDBUNDLE#####: Updated " + entCount
					+ " Bid Bundle entries for Campaign id " + currCampaign.id);
			log.log(Level.ALL, "##### Bid Bundle #####; currCampaign: " + currCampaign.id + "; " + currCampaign.budget);
		}

		if (bidBundle != null) {
			System.out.println("Day " + day + ": Sending BidBundle");
			sendMessage(adxAgentAddress, bidBundle);
		}
	}

	/**
	 * Campaigns performance w.r.t. each allocated campaign
	 */
	private void handleCampaignReport(CampaignReport campaignReport) {

		campaignReports.add(campaignReport);

		/*
		 * for each campaign, the accumulated statistics from day 1 up to day
		 * n-1 are reported
		 */
		for (CampaignReportKey campaignKey : campaignReport.keys()) {
			int cmpId = campaignKey.getCampaignId();
			CampaignStats cstats = campaignReport.getCampaignReportEntry(
					campaignKey).getCampaignStats();
			myCampaigns.get(cmpId).setStats(cstats);

			System.out.println("Day " + day + ": Updating campaign " + cmpId + " stats: "
					+ cstats.getTargetedImps() + " tgtImps "
					+ cstats.getOtherImps() + " nonTgtImps. Cost of imps is "
					+ cstats.getCost());
		}
	}

	/**
	 * Users and Publishers statistics: popularity and ad type orientation
	 */
	private void handleAdxPublisherReport(AdxPublisherReport adxPublisherReport) {
		System.out.println("Publishers Report: ");
		for (PublisherCatalogEntry publisherKey : adxPublisherReport.keys()) {
			AdxPublisherReportEntry entry = adxPublisherReport
					.getEntry(publisherKey);
			System.out.println(entry.toString());
		}
	}

	/**
	 *
	 * @param //AdNetworkReport
	 */
	private void handleAdNetworkReport(AdNetworkReport adnetReport) {

		System.out.println("Day " + day + " : AdNetworkReport");
		/*
		 * for (AdNetworkKey adnetKey : adnetReport.keys()) {
		 *
		 * double rnd = Math.random(); if (rnd > 0.95) { AdNetworkReportEntry
		 * entry = adnetReport .getAdNetworkReportEntry(adnetKey);
		 * System.out.println(adnetKey + " " + entry); } }
		 */
	}

	@Override
	protected void simulationSetup() {

		day = 0;
		bidBundle = new AdxBidBundle();

		/* initial bid between 0.1 and 0.2 */
		ucsBid = 0.2;

		myCampaigns = new HashMap<Integer, CampaignData>();
		log.fine("AdNet " + getName() + " simulationSetup");
	}

	@Override
	protected void simulationFinished() {
		campaignReports.clear();
		bidBundle = null;
	}

	/**
	 * A user visit to a publisher's web-site results in an impression
	 * opportunity (a query) that is characterized by the the publisher, the
	 * market segment the user may belongs to, the device used (mobile or
	 * desktop) and the ad type (text or video).
	 *
	 * An array of all possible queries is generated here, based on the
	 * publisher names reported at game initialization in the publishers catalog
	 * message
	 */
	private void generateAdxQuerySpace() {
		if (publisherCatalog != null && queries == null) {
			Set<AdxQuery> querySet = new HashSet<AdxQuery>();

			/*
			 * for each web site (publisher) we generate all possible variations
			 * of device type, ad type, and user market segment
			 */
			for (PublisherCatalogEntry publisherCatalogEntry : publisherCatalog) {
				String publishersName = publisherCatalogEntry
						.getPublisherName();
				for (MarketSegment userSegment : MarketSegment.values()) {
					Set<MarketSegment> singleMarketSegment = new HashSet<MarketSegment>();
					singleMarketSegment.add(userSegment);

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.video));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.video));

				}

				/**
				 * An empty segments set is used to indicate the "UNKNOWN"
				 * segment such queries are matched when the UCS fails to
				 * recover the user's segments.
				 */
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.text));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.text));
			}
			queries = new AdxQuery[querySet.size()];
			querySet.toArray(queries);
		}
	}
	
	/*genarates an array of the publishers names
	 * */
	private void getPublishersNames() {
		if (null == publisherNames && publisherCatalog != null) {
			ArrayList<String> names = new ArrayList<String>();
			for (PublisherCatalogEntry pce : publisherCatalog) {
				names.add(pce.getPublisherName());
			}

			publisherNames = new String[names.size()];
			names.toArray(publisherNames);
		}
	}
	/*
	 * genarates the campaign queries relevant for the specific campaign, and assign them as the campaigns campaignQueries field 
	 */
	private void genCampaignQueries(CampaignData campaignData) {
		Set<AdxQuery> campaignQueriesSet = new HashSet<AdxQuery>();
		for (String PublisherName : publisherNames) {
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.mobile, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.mobile, AdType.video));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.video));
		}

		campaignData.campaignQueries = new AdxQuery[campaignQueriesSet.size()];
		campaignQueriesSet.toArray(campaignData.campaignQueries);
		System.out.println("!!!!!!!!!!!!!!!!!!!!!!"+Arrays.toString(campaignData.campaignQueries)+"!!!!!!!!!!!!!!!!");
	}

	private class CampaignData {
		/* campaign attributes as set by server */
		Long reachImps;
		long dayStart;
		long dayEnd;
		Set<MarketSegment> targetSegment;
		double videoCoef;
		double mobileCoef;
		int id;
		private AdxQuery[] campaignQueries;//array of queries relvent for the campaign.
		long cmpBidMillis;
		long impressionTarget;

		/* campaign info as reported */
		CampaignStats stats;
		double budget;

		public CampaignData(InitialCampaignMessage icm) {
			reachImps = icm.getReachImps();
			dayStart = icm.getDayStart();
			dayEnd = icm.getDayEnd();
			targetSegment = icm.getTargetSegment();
			videoCoef = icm.getVideoCoef();
			mobileCoef = icm.getMobileCoef();
			id = icm.getId();
			impressionTarget = icm.getReachImps(); // TODO calculate default value for impression target.

			stats = new CampaignStats(0, 0, 0);
			budget = 0.0;
		}

		public void setBudget(double d) {
			budget = d;
		}

		public void setBid(long b) {
			cmpBidMillis = b; //NAMM
		}

		public CampaignData(CampaignOpportunityMessage com) {
			dayStart = com.getDayStart();
			dayEnd = com.getDayEnd();
			id = com.getId();
			reachImps = com.getReachImps();
			targetSegment = com.getTargetSegment();
			mobileCoef = com.getMobileCoef();
			videoCoef = com.getVideoCoef();
			stats = new CampaignStats(0, 0, 0);
			budget = 0.0;
			cmpBidMillis = 0; //NAMM
			impressionTarget = com.getReachImps(); //TODO calculate default value for impression target.
		}

		@Override
		public String toString() {
			return "Campaign ID " + id + ": " + "day " + dayStart + " to "
					+ dayEnd + " " + targetSegment + ", reach: " + reachImps
					+ " coefs: (v=" + videoCoef + ", m=" + mobileCoef + ")";
		}

		int impsTogo() {
			return (int) Math.max(0, reachImps - stats.getTargetedImps());
		}

		void setStats(CampaignStats s) {
			stats.setValues(s);
		}

		public AdxQuery[] getCampaignQueries() {
			return campaignQueries;
		}

		public void setCampaignQueries(AdxQuery[] campaignQueries) {
			this.campaignQueries = campaignQueries;
		}

	}

	/**
	 *  User defined methods
	 */

	/*
	 * ALUN: different methods for each campaign strategy
     */

	/*
	 * Method for computing campaign bid to maximise profit
	 * Currently just bids randomly between min and max as before
	 * In progress: version will bid the average successful second price.
	 * Not great because it creates a system where we assign our value based on other agents value.
	 */
	private long campaignProfitStrategy() {
		Random random = new Random();
		double bid, bidFactor;
		double totalCostPerImp = 0.0;
		if (myCampaigns.size() > 1) {
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				if (entry.getValue().dayStart != 1) {
					totalCostPerImp += entry.getValue().budget / entry.getValue().reachImps;
					System.out.println("############## Budget: " + entry.getValue().budget + "reachImps: " + entry.getValue().reachImps);
				}
			}
			bidFactor = (random.nextInt(40)/100) + 0.8;
			bid = 1000 * pendingCampaign.reachImps * totalCostPerImp / (myCampaigns.size() -1) * bidFactor;
		}
		else bid = (double)random.nextInt(pendingCampaign.reachImps.intValue()); //Random bid initially

		System.out.println("Day :" + day + " Campaign - Base bid: " + bid);
		return (long)bid;
		// TODO: Build a system for choosing initial bids when data isn't available i.e. read/write from previous games.

	}

	/*
	 * Method for computing the quality recovery campaign bid strategy
	 * Multiply profit strategy by quality squared, first to turn our bid into an effective bid.
	 * Second to try and win more campaigns than our value assigns.
	 */
	private long campaignQualityRecoveryStrategy() {
		double bid =  campaignProfitStrategy() * Math.pow(adNetworkDailyNotification.getQualityScore(),2); //TODO: learn the power
		System.out.println("Day :" + day + " Campaign - Quality Recovery Strategy");
		// TODO: ferocity of quality recovery should be based on our ability to complete the campaigns and the number of campaigns we currently have.
		return (long)bid;
	}

	/*
	 * Method for computing the campaign bid for starting strategy
	 */
	private long campaignStartingStrategy() {
		double cmpBidMillis;
		long campaignLength = pendingCampaign.dayEnd - pendingCampaign.dayStart + 1;
		if(campaignLength == 10){ // long campaign
			cmpBidMillis = campaignProfitStrategy()*0.8;
			System.out.println("Day :" + day +  " Campaign - Long campaign Starting Strategy");
		}
		else if (campaignLength == 5){ // medium campaign
			cmpBidMillis = campaignProfitStrategy()*1.2;
			System.out.println("Day: " + day + " Campaign - Medium campaign Starting Strategy");
		}
		else { // short campaign
			cmpBidMillis = campaignProfitStrategy()*2;
			System.out.println("Day :" + day + " Short campaign Starting Strategy");
		}
		return ((long)cmpBidMillis);
	}

	/*
	 *  Method for computing impression targets to be used in the UCS and impression auctions
	 *  Later it will be useful to return a range of targets rather than a single target.
	 *  Loops through impression targets to work out the most cost efficient permutation.
	 *  Impression target minimises campaign cost + quality effect
	 */
	private long setImpressionTargets() {
		long target=0;
		// loop over a variety of possible impression targets
		for (double multiplier = 0.6; multiplier <= 2; multiplier+= 0.02){ // loop over range of impression targets
			double tempTarget = pendingCampaign.reachImps*multiplier;
			// Decide which impression target is most cost efficient
			long targetCost = campaignCost(pendingCampaign, target) + qualityEffect(pendingCampaign);
			long tempTargetCost = campaignCost(pendingCampaign, (long)tempTarget) + qualityEffect(pendingCampaign);
			if (tempTargetCost < targetCost) {
				target = (long)tempTarget;
			}
		}
		//if impression cost estimate doesn't work / not implemented then use default value. 
		if (campaignCost(pendingCampaign, target)==0) target = pendingCampaign.reachImps;
		System.out.println("Day " + day + ": Impression target = " + target);
		return(target);
	}

	/*
	 * Evaluates the effect of estimated quality change on future revenue.
	 */
	private long qualityEffect(CampaignData Campaign) {
		// Days remaining after campaign ends
		long daysRemaining = 60 - Campaign.dayEnd;
		// Average daily reach from past campaigns
		double pastReach = 0;
		for (Map.Entry<Integer, CampaignData> campaign : myCampaigns.entrySet()) {
			pastReach += campaign.getValue().reachImps;
		}
		double pastDailyReach = pastReach / myCampaigns.size();
		// Linearly reduces reliance on historic data --> dynamic data over time
		long reachRemaining = (long)((daysRemaining/60) * 50 + (1-daysRemaining/60)*pastDailyReach); //TODO learn the average reach per day
		// turn target into a quality change
		double a = 4.08577, b = 3.08577, lRate = 0.6;
		double qualityChange=lRate*(adNetworkDailyNotification.getQualityScore()+(2*lRate/a)*Math.atan(a*Campaign.impressionTarget/Campaign.reachImps-b)-Math.atan(-b));
		// Sum the effect of increased quality on the remaining reach.
		return (long)qualityChange * reachRemaining;
	}

	/*
	 * Estimates the total cost of running a campaign based on the sum UCS and impression estimation functions.
	 * Total cost = impression cost of campaign + ucs cost
	 */
	private long campaignCost(CampaignData Campaign, long targetImp) {
		long totalCost = 0;
		// loop over each day of the campaign
		for (day = (int)Campaign.dayStart; day <= (int)Campaign.dayEnd; day++) {
			// evaluate best UCS/impression cost combination estimation
			ucsTargetLevel = bestImpUcsCombination();
			// add the UCS cost to the Impression cost estimate and sum
			totalCost += impressionCostEstimate(targetImp, day, ucsTargetLevel) + ucsCostEstimate(ucsTargetLevel);
		}
		return totalCost;
	}

	/*
	 *  Manu: Impression cost estimate
	 *  This method takes an impression target as an input and evaluates the estimated cost to achieve that value given
	 *  the day and UCS target.
	 *  Again later it will be useful to be able to estimate costs for a range of impressions to choose a local optimum.
	 *  May include functionality to recall past actual costs / estimates.
	 *  USE: use this function for campaign bids by evaluating the best impression/UCS combination then summing over
	 *  all days of the prospective campaign to evaluate total cost to complete campaign.
	 */
	private long impressionCostEstimate(long impTarget, long day, int ucsTargetLevel) {
		// TODO;
		// You can now access impression targets from campaign data;
		// e.g. pendingCampaign.impressionTarget
		return 0;
	}

	/*
	 * Nicola: UCS cost estimate
	 * This function estimates the cost to achieve a specific ucs tier. Note that ucsTarget is the integer tier not the
	 * percentage of users classified. (1 = 100%, 2 = 90%, 3 = 81% ...).
	 * Expansion: Factor in changes to unknown and known impression costs.
	 */
	private long ucsCostEstimate(int ucsTargetLevel) {
		// TODO;
		return 0;
	}

	/*
	 * Nicola: Best UCS impression cost combination
	 * This function queries impression and UCS cost estimations over the entire range of UCS costs to evaluate the
	 * cheapest cost to achieve our impression target.
	 */
	private int bestImpUcsCombination(){
		// TODO Return desired UCS classification;
		return 0;
	}

	/*
	 * Nicola: UCS bid
	 * This method takes a UCS target and tries to evaluate the bid required to reach that target.
	 * Expansion: include a sense of risk aversion to this function. i.e. when it is more important to achieve a
	 * specific UCS level (like incomplete campaign due that day) we want to overbid.
	 * Will use a combination of machine learning techniques and recalling recorded values to produce estimate.
	 */
	private double ucsBidCalculator(int ucsTargetLevel){
		// TODO;
		return 0;
	}

	/*
	 * Miguel: This method calculates how to bid for unknown users.
	 */
	private double untargetedImpressionBidCalculator(double impressionTarget){
		// TODO;
		return 0;
	}

	/*
	 * Miguel: This method evaluates the bid for each impression query.
	 */
	private double ImpressionBidCalculator(double impressionTarget, AdxQuery query){
		// TODO;
		// You can now access impression targets from campaign data;
		// e.g. pendingCampaign.impressionTarget
		return 0;
	}
}
