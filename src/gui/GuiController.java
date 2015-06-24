package gui;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneDomainContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneLinkContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneNodeContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlanePathContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlanePortContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneTopologyContent;

import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.api.soap.gen.v06.ReservedConstraintType;
import net.es.oscars.common.soap.gen.MessagePropertiesType;
import net.es.oscars.common.soap.gen.OSCARSFaultMessage;
import net.es.oscars.common.soap.gen.OSCARSFaultReport;
import net.es.oscars.topoBridge.soap.gen.GetTopologyRequestType;
import net.es.oscars.topoBridge.soap.gen.GetTopologyResponseType;
import net.es.oscars.utils.clients.TopoBridgeClient;
import net.es.oscars.utils.soap.OSCARSServiceException;

import multipath.*;
import config.*;

/*****************************************************************************************************************************************
* This class acts as the behavior controller for the MultipathUI GUI.
* It serves as the middle-man between the end-user interface and MultipathOSCARSClient, which handles all the computations and submits
* requests to OSCARS for Unicast/Multipath reservations.
* 
* Many of the methods herein are called from parallel threads created by the GUI.
* 
* This class handles:
* 	- Generating the lists of Source/Destination nodes
* 	- Generating the lists of MP-GRIs/GRIs
* 	- Issuing calls to MultipathOSCARSClient for creating, querying, canceling, and grouping reservations.
* 	- Formats query result output into a readable version to be displayed in the GUI's console.  
* @author Jeremy
/*****************************************************************************************************************************************/
public class GuiController 
{
	private ArrayList<String> allShortMPGris = new ArrayList<String>();	// All existing short-format MP-GRIs
	private ArrayList<String> allLongMPGris = new ArrayList<String>();	// All existing long-format MP-GRIs
	private ArrayList<String> allUnicastGris = new ArrayList<String>();	// All existing unicast GRIs (obtained from OSCARS)
	private ArrayList<SubrequestTuple> allQueryResults;		// List of results from issuing a query to MultipathOSCARSClient, contains ResDetails and OSCARSFaultMessages
	private ArrayList<String> topologyNodes;				// List of all URNs in the network domain
		
	MultipathOSCARSClient multipathClient;			// Handles calls to OSCARS for Unicast/Multipath requests
	String domain;						// Default topology (GUI currently only supports single-domain reservations)
	
	/*******************************************************************************************************
	* Constructor 
	* - Connect to OSCARS via MultipathOSCARSClient
	* - Generate Topology node list by connecting to the TopBridge OSCARS WebService
	*******************************************************************************************************/
	public GuiController()
	{
		System.out.println("Initializing connection to OSCARS...");
		
		String oscarsURL = Configuration.oscarsURL;			// Where is the instance of OSCARS this client will use?
		String topoBridgeURL = Configuration.topoBridgeURL;	// Where is the instance of TopologyBridge WS this client will use?
		
		// Connect to OSCARS -- enable Multipath functionality //
		multipathClient = new MultipathOSCARSClient(oscarsURL);
		
		// Obtain the topology domain from Configuration.java //
		domain = Configuration.topologyDomain;
		
		// Obtain the topology from the TopoBridge WS //
		topologyNodes = getOSCARSTopology(topoBridgeURL, domain);
		
		Collections.sort(topologyNodes);
	}
	
	/*******************************************************************************************************
	* Returns the list of all nodes in the topology as an array of Objects (since that's what the GUI
	* lists expect). this is just a getter, the list of nodes is precomputed only once in the constructor.
	* 
	* @return A complete list of Topology nodes in 'node : port : link' format. 
	*******************************************************************************************************/
	public Object[] getTopologyNodes()
	{
		Collections.sort(topologyNodes);
		
		return topologyNodes.toArray();
	}
	
	/*******************************************************************************************************
	* Updates the destination node list for proper display on the GUI.
	* When the user selects a source node for a new reservation, the list of destinations is updated to
	* exclude that source so that the user isn't allowed to specify a destination which is the same as the 
	* source.
	* 
	* @param selectedSource, The source that the user has already selected for the new reservation
	* @return An updated list of Destination nodes. The GUI list treats the model as an array of Objects.
	*******************************************************************************************************/
	@SuppressWarnings("unchecked")
	public Object[] updateDestinationNodeList(String selectedSource)
	{		
		// No source selected, let the destination list include all nodes in the topology //
		if(selectedSource.equals(""))
		{
			return topologyNodes.toArray();
		}
		// User has selected a source, remove it from destination list and return the updated list //
		else		
		{			
			ArrayList<String> destinations = (ArrayList<String>)topologyNodes.clone();
			destinations.remove(selectedSource);
			
			Collections.sort(destinations);
			
			return destinations.toArray();
		}
	}
	
	
	/*******************************************************************************************************
	* Updates the source node list for proper display on the GUI.
	* When the user selects one or more destination nodes for a new reservation, the list of source is updated 
	* to exclude them so that the user isn't allowed to specify a destination which is the same as the 
	* source.
	* 
	* @param selectedDestinations, List of destination nodes already selected by the user.
	* @return An updated list of source nodes. The GUI list treats the model as an array of Objects.
	*******************************************************************************************************/
	@SuppressWarnings("unchecked")
	public Object[] updateSourceNodeList(String[] selectedDestinations)
	{			
		// Nothing to update //
		if(selectedDestinations == null)
			return null;
		
		// No destination selected, let the source list include all nodes in the topology //
		if(selectedDestinations[0].equals(""))
		{
			return topologyNodes.toArray();
		}
		// User has selected a destination(s), remove it/them from source list //
		else	
		{			
			ArrayList<String> sources = (ArrayList<String>)topologyNodes.clone();
			
			for(String oneDestination : selectedDestinations)
				sources.remove(oneDestination);
			
			Collections.sort(sources);
			return sources.toArray();
		}
	}
	
	/*******************************************************************************************************
	* This is the behavior invoked by clicking on the "Cancel Reservation" button on the GUI.
 	* Forwards the cancellation request to MultipathOSCARSClient
	* 
	* @param griToCancel
	*******************************************************************************************************/
	protected void cancelExistingReservation(String griToCancel)
	{
		multipathClient.cancelMPReservation(griToCancel);
	}
	
	/*******************************************************************************************************
	* This behavior is invoked when the user clicks the "Create Reservation" button on the GUI.
	* Updates the source node list for proper display on the GUI.
	* Parameters are parsed to identify the necessary values to pass to MultipathOSCARSClient to create a 
	* new reservation.
	* 
	* @param srcURN, In 'node : port : link' format.
	* @param dstURN, In 'node : port : link' format.
	* @param startTimeString, In 'YYYY-MM-DD HH:mm' format. 
	* @param endTimeString, In 'YYYY-MM-DD HH:mm' format.
	* @param bandwidth
	* @param numDisjointPaths
	* @return The GRI assigned to this reservation by OSCARS and MultipathOSCARSClient (for Multipath).
	*******************************************************************************************************/
	public String createNewReservation(String srcURN, String dstURN, String startTimeString, String endTimeString, int bandwidth, int numDisjointPaths)
	{
		String[] piecesOfURN = new String[3];
		String sourceString = "";
		String destinationString = "";
		long startTimestamp = 0;
		long endTimestamp = 0;
		long[] startEnd = new long[2];	
		String griFromOSCARS = "";
		
		// Convert source node to OSCARS-readable format //
		piecesOfURN = srcURN.split(" : ");
		sourceString = "urn:ogf:network:domain=" + domain + ":node=" + piecesOfURN[0] + ":port=" + piecesOfURN[1] + ":link=" + piecesOfURN[2];
		
		// Convert destination node to OSCARS-readable format //
		piecesOfURN = dstURN.split(" : ");
		destinationString = "urn:ogf:network:domain=" + domain + ":node=" + piecesOfURN[0] + ":port=" + piecesOfURN[1] + ":link=" + piecesOfURN[2]; 
		
		// Convert start/end times into OSCARS-readable long format //
		startEnd = parseTimes(startTimeString, endTimeString);
		startTimestamp = startEnd[0];
		endTimestamp = startEnd[1];
		
		// Submit the createMPReservation() request to OSCARSMultipathClient and get the assigned GRI back //
		if(destinationString.contains("node=anycast"))
		{
			AnycastHandler handler = new AnycastHandler(multipathClient);
			griFromOSCARS = handler.handleAnycastRequest("Reservation via MultipathUI", sourceString, true, "any", destinationString, true, "any", bandwidth, "timer-automatic", startTimestamp, endTimestamp, numDisjointPaths);
		}
		else
		{
			griFromOSCARS = multipathClient.createMPReservation("Reservation via MultipathUI", sourceString, true, "any", destinationString, true, "any", bandwidth, "timer-automatic", startTimestamp, endTimestamp, numDisjointPaths);
		}
		
		// Update MP-GRI/GRI lists to include the new reservation //
		this.populateUnicastList();
		this.refreshMPGriLists();
		
		return griFromOSCARS;
	}
	
	
	/*******************************************************************************************************
	* Convert readable date string-format to long-format compatible with OSCARS.
	* 
	* @param start_time, String in 'YYY-MM-DD HH:mm' format
	* @param end_time, String in 'YYY-MM-DD HH:mm' format
	* @return 2-element array containing corresponding long-format start/end times
	*******************************************************************************************************/
	private static long[] parseTimes(String start_time, String end_time) 
	{     
        Long startTime = 0L;
        Long endTime = 0L;
        
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        
        // Convert start time into Long format for OSCARS to read //
        if (start_time == null || start_time.equals("now") || start_time.equals("")) 
        {
            startTime = System.currentTimeMillis()/1000;
        } 
        else 
        {
            try 
            {
                startTime = date.parse(start_time.trim()).getTime()/1000;
            } 
            catch (java.text.ParseException ex) 
            {
                System.err.println("Error parsing start date: "+ ex.getMessage());
                System.exit(-1);
            }
        }

        // Convert end time into Long format for OSCARS to read //
        if (end_time == null || end_time.equals("")) 
        {
            System.err.println("Error: No end time specified.");
            System.exit(-1);
        } 
        else if (end_time.startsWith("+"))		// Offset from start time 
        {
            String[] hm = end_time.substring(1).split("\\:");
            
            if (hm.length != 3) 
            {
            	System.err.println("Error parsing end date.");
                System.exit(-1);
            } 
            
            try 
            {
                Integer seconds = Integer.valueOf(hm[0])*3600*24; 	// days
                seconds += Integer.valueOf(hm[1])*3600; 			// hours
                seconds += Integer.valueOf(hm[2])*60; 				// minutes
                
                if (seconds < 60) 
                {
                	System.err.println("Duration must be > 60 sec");System.exit(-1);
                }
                
                endTime = startTime + seconds;
            } 
            catch (NumberFormatException ex) 
            {
            	System.err.println("Error parsing end date format: "+ex.getMessage());
            	System.exit(-1);
            }
        } 
        else 	// regular end-time specification
        {
            try 
            {
                endTime = date.parse(end_time.trim()).getTime()/1000;
            } 
            catch (java.text.ParseException ex) 
            {
                System.err.println("Error parsing emd date: "+ex.getMessage());
                System.exit(-1);
            }
        }
        
        long[] startEnd = {startTime.longValue(), endTime.longValue()};
        
        return startEnd;
	}

	
	/*******************************************************************************************************
	* Gets all URNs from the OSCARS topology corresponding to the global String variable 'domain'.
	* NOTE: In order for this method to work, the TopoBridge config files must be updated to broadcast the
	* TopoBridge WebService on a specific IP, rather than just 'localhost'.
	* 
	* @param topoBridge_url, Where can the TopoBridge WS be found? Ex: http://localhost:9019/topoBridge
	* @param topologyID, Which topology are we looking to get the URNs from?
	* @return list of all URNs in the specified topology in 'node : port : link' format
	*******************************************************************************************************/
    public ArrayList<String> getOSCARSTopology(String topoBridge_url, String topologyID) 
    {
    	//Configuration clientConfig = new Configuration();
    	TopoBridgeClient topoBridge = null;
    	GetTopologyRequestType topologyRequest = new GetTopologyRequestType();
    	MessagePropertiesType mt = new MessagePropertiesType();
    	
    	ArrayList<String> endPoints = new ArrayList<String>();
    	
    	/**
         * GetTopologyResponseType					-->	List<CtrlPlaneTopologyContent>
         * - CtrlPlaneTopologyContent				--> List<CtrlPlaneDomainContent>
         * -- CtrlPlaneDomainContent				--> List<CtrlPlaneNodeContent>
         * --- CtrlPlaneNodeContent					--> List<CtrlPlanePortContent>
         * ---- CtrlPlanePortContent				--> List<CtrlPlaneLinkContent>, Port ID, Capacity, Granularity, Minimum Reservable Capacity, Maximum Reservable Capacity 
         * ----- CtrlPlaneLinkContent				--> Link ID, Remote Link ID, Capacity, Granularity, Minimum Reservable Capacity, Maximum Reservable Capacity, VLAN Range
         **/
        GetTopologyResponseType topologyResponse;					//This is what comes back from OSCARS
        List<CtrlPlaneTopologyContent> allTopologies;
        List<CtrlPlaneDomainContent> allDomains;
        List<CtrlPlaneNodeContent> allNodesInDomain;
        List<CtrlPlanePortContent> allPortsOnNode;
        List<CtrlPlaneLinkContent> allLinksOnPort;

        try
        {
			// Connect to the TopoBridge WS to submit a getTopology() request //
		  	topoBridge = TopoBridgeClient.getClient(topoBridge_url);
		
		  	topologyRequest = new GetTopologyRequestType();
		  	topologyRequest.getDomainId().add(topologyID);
	
		  	// This is necessary to prevent Null-Pointer Exception //
		  	mt.setGlobalTransactionId("made-up");
		  	topologyRequest.setMessageProperties(mt);
		                
		  	// Submit getTopology request to OSCARS and get response back //
	    	topologyResponse = topoBridge.getPortType().getTopology(topologyRequest);	
	        
	        allTopologies = topologyResponse.getTopology();
	        allDomains = allTopologies.get(0).getDomain();
	
	        // Popluate the list of all URNS in format 'node : port : link' so it is readable on GUI //
	        for (CtrlPlaneDomainContent oneDomain : allDomains) 
	        {	            
	            allNodesInDomain = oneDomain.getNode();
	            
	            for (CtrlPlaneNodeContent oneNode : allNodesInDomain) 
	            {                
	                allPortsOnNode = oneNode.getPort();
	                
	                for (CtrlPlanePortContent onePort : allPortsOnNode) 
	                {
	                	allLinksOnPort = onePort.getLink();
	                	
	                	for(CtrlPlaneLinkContent oneLink : allLinksOnPort)
	                	{
	                		String[] partsOfURN = oneLink.getId().split(":");
	                		String nodeID = partsOfURN[4].substring(partsOfURN[4].indexOf("node=") + 5);
	                		String portID = partsOfURN[5].substring(partsOfURN[5].indexOf("port=") + 5);
	                		String linkID = partsOfURN[6].substring(partsOfURN[6].indexOf("link=") + 5);
	                		
	                		endPoints.add(nodeID + " : " + portID + " : " + linkID);
	                	}
	                }
	            }
	        }
        }
        catch (OSCARSFaultMessage fm) 
        {
            fm.printStackTrace();
            System.err.println("Error: OSCARSFaultMessage [" + fm.getMessage() + "]");
        }
		catch (OSCARSServiceException se) 
		{
			se.printStackTrace();
            System.err.println("Error: OSCARSClientException [" + se.getMessage() + "]");
		}
    	catch(MalformedURLException mue)
    	{ 
    		mue.printStackTrace(); 
    	}
        catch (Exception e) 
        {
            e.printStackTrace();
            System.err.println("Error: Exception [" + e.getMessage() + "]");
        } 
        
        return endPoints;
    }
    
    /*******************************************************************************************************
    * Get ALL Unicast GRIs from OSCARS and put them into the allUnicastGris ArrayList.   
    *******************************************************************************************************/
    private void populateUnicastList()
    {
    	ArrayList<String> allStatuses = new ArrayList<String>();
    	List<ResDetails> allReservationDetails;								
    	
    	allUnicastGris = new ArrayList<String>();
    	
    	// Include ALL available OSCARS statuses in the list request //
    	allStatuses.add("ALL");
    	
    	// Invoke the list request in MultipathOSCARSClient and get Details back on every exsiting request //
    	allReservationDetails = multipathClient.listUnicastByStatus(allStatuses);
    	
    	// Put the GRIs of all Unicast reservations into the global list //
    	for(ResDetails oneReservation : allReservationDetails)
    	{
    		allUnicastGris.add(oneReservation.getGlobalReservationId());
    	}    	
    }
    
    /*******************************************************************************************************
    * Get ALL Unicast GRIs from OSCARS and return the to the user as a sorted list of Objects,
    * since thats what the GUI lists expect.
    * 
    * @return List of all Unicast GRIs as an array of Objects
    *******************************************************************************************************/
    protected Object[] getAllUnicastGRIs()
    {
    	GriComparator griComparator = new GriComparator();
    	
    	this.populateUnicastList();	// Invoke the list operation
    	
    	// Sort them alphabetically and by length so that es.net-2 comes after es.net-1 not es.net-199 //
    	Collections.sort(allUnicastGris, griComparator);	
    	    	
    	return allUnicastGris.toArray();
    }
    
    
    /*******************************************************************************************************
    * Update the global list of MP-GRIs to include everything currently in the "mp_gri_lookup.txt" file 
    *******************************************************************************************************/
    protected void refreshMPGriLists()
    {
    	File mpGriLookup = new File("mp_gri_lookup.txt");
    	FileInputStream lookupStream;
		DataInputStream lookupIn;
		BufferedReader lookupReader;
		
    	if(mpGriLookup.exists())
    	{
    		try
    		{
    			String oneLongMPGri = "";
    			lookupStream = new FileInputStream(mpGriLookup);
    			lookupIn = new DataInputStream(lookupStream);
    			lookupReader = new BufferedReader(new InputStreamReader(lookupIn));
    			
    			allLongMPGris = new ArrayList<String>();		// Clear the list
    			allShortMPGris = new ArrayList<String>();		// Clear the list
    			
    			while(true)
    			{
    				oneLongMPGri = lookupReader.readLine();		// Get all GRIs listed in the lookup table file
    				
    				if(oneLongMPGri == null)
    					break;
    				
    				allLongMPGris.add(oneLongMPGri);
    				allShortMPGris.add(oneLongMPGri.substring(0, oneLongMPGri.indexOf("_=_"))); // Truncate the GRIs to be readable and short
    			}
    			
    			lookupReader.close();
    			lookupIn.close();
    			lookupStream.close();
    		}
    		catch(Exception e)
    		{
    			System.err.println("Error: Exception [" + e.getMessage() + "]");
    			System.exit(-1);
    		}
    	}
    		
    }
    
    /*******************************************************************************************************
    * Getter method to obtain the list of MP-GRIs.
    * 
    * @return List of all MP-GRIs as an array of Objects
    *******************************************************************************************************/
    protected Object[] getMPGRIs()
    {
    	return allShortMPGris.toArray();
    }
    
    /*******************************************************************************************************
     * Getter method to obtain the list of MP-GRIs.
     * 
     * @return List of all MP-GRIs as an ArrayList of Strings
     *******************************************************************************************************/
    protected ArrayList<String> getMPGRIsAsStrings()
    {
    	return allShortMPGris;
    }
    
    
    /*******************************************************************************************************
    * Gets all subrequest GRIs associated with the given MP-group GRI, for display on the GUI.
    * 
    * @param mpGRI, Group GRI to fetch the subrequest GRIs for
    * @return The list of associated subrequest GRIs as an array of Objects
    *******************************************************************************************************/
    protected Object[] getGroupedGRIs(String mpGRI)
    {
    	ArrayList<String> subrequestGriList = new ArrayList<String>();
    	GriComparator griComparator = new GriComparator();
    	
    	// Find long-format mpGRI in the global list //
    	int whereToLook = allShortMPGris.indexOf(mpGRI);	
    	String longGRI = allLongMPGris.get(whereToLook);
    	
    	// Break up long-format MP-GRI to get its subrequests //
    	String[] parsedMPGri = longGRI.split(":");		
    	    	
    	for(int g = 2; g < parsedMPGri.length; g++)
    		subrequestGriList.add(parsedMPGri[g]);
    	
    	// Sort subrequest GRIs alphabetically and by length so that es.net-2 comes after es.net-1 not es.net-199 //
    	Collections.sort(subrequestGriList, griComparator);
    	    	
    	return subrequestGriList.toArray();
    }
    
    /*******************************************************************************************************
	* This is the behavior invoked by clicking on the "Add to Group" button on the GUI.
 	* Invokes the groupReservations() ADD operation in MultipathOSCARSClient, which builds a new, or appends to 
 	* an existing MP-GRI in 'mp_gri_lookup.txt'.
 	* - Will update the Multipath GRI list on the GUI to reflect the addition.
	*	
    * @param griGroup, Destination group MP-GRI.
    * @return MP_GRI created or altered by this call.
    *******************************************************************************************************/
    protected String addToGroup(String griGroup)
    {
    	ArrayList<String> gris = new ArrayList<String>();
    	gris.add(griGroup);
    	
    	// Invoke the Add to Group call in MultipathOSCARSClient //
    	String mpGriToReturn = multipathClient.groupReservations(gris, true, 1);
    	    	    	
    	this.refreshMPGriLists();
    	    	
    	if(mpGriToReturn.equals(""))	// Couldn't successfully clone the subrequest
    		return "IMPOSSIBLE";
    	
    	return mpGriToReturn.substring(0, mpGriToReturn.indexOf("_=_"));
    }
    
    /*******************************************************************************************************
    * This is the behavior invoked by clicking on the "Sub from Group" button on the GUI.
 	* Invokes the groupReservations() SUB operation in MultipathOSCARSClient, which removes the specified 
 	* subrequest GRI from an existing MP-GRI in 'mp_gri_lookup.txt'.
 	* - Will update the Multipath GRI list on the GUI to reflect the removal.
    * @param griGroup, Destination group MP-GRI.
    * @param griSrc, Source GRI to remove from Destination group.
    * @return MP_GRI altered by this call.
    *******************************************************************************************************/
    protected String subFromGroup(String griGroup, String griSrc)
    {
    	ArrayList<String> gris = new ArrayList<String>();
    	gris.add(griGroup);
    	gris.add(griSrc);
    	
    	// Invoke the Remove from Group call in MultipathOSCARSClient //
    	String mpGriToReturn = multipathClient.groupReservations(gris, false, 0);
    	
    	this.refreshMPGriLists();
    	
    	System.out.println("RETURN = " + mpGriToReturn);
    	
    	return mpGriToReturn;
    }
    
    /*******************************************************************************************************
    * This is the behavior invoked by selecting a GRI in either the Multipath/Unicast GRI list on the GUI.
    * Submits a queryMPReservation() call to MultipathOSCARSClient which returns a set of details and
    * associated error messages for each request/subrequest queried. 
    * - Results from query are formatted to be readable on the GUI output console.
    * 
    * @param griToQuery
    * @return The formatted, user-readable output representing the results of the query operation.
    *******************************************************************************************************/
    protected ArrayList<String> queryReservations(String griToQuery)
    {
    	ArrayList<String> consoleDisplay = new ArrayList<String>();
    	    	
    	int whereToLook = allShortMPGris.indexOf(griToQuery);
    	
    	if(whereToLook != -1)	// Multipath group GRI
    	{
    		String longGRI = allLongMPGris.get(whereToLook);
    		
    		System.out.println("LONG GRI = " + longGRI);
    	}
    	
    	// Submit query to MultipathClient and get a list of ResDetails and OSCARSFaultReports back //
    	multipathClient.silentQuery = true;
    	allQueryResults = multipathClient.queryMPReservation(griToQuery);
    	multipathClient.silentQuery = false;
    	
    	
    	if(griToQuery.startsWith("MP"))	// Multipath
    		consoleDisplay.addAll(generateMultipathQueryOutput(allQueryResults.get(allQueryResults.size()-1), griToQuery));
    	else							// Unicast
    		consoleDisplay = generateUnicastQueryOutput(allQueryResults.get(0));
    	
    	return consoleDisplay;
    }
    
    
    /*******************************************************************************************************
    * Constructs the ArrayList which becomes the output for Multipath reservation queries.
    * Generates output for each unicast subrequest.
    * The returned ArrayList has length which is a mulitple of 3.
    * - Every first entry is the MP-Group ID.
    * - Every second entry is a Unicast subrequest query result.
    * - Every third entry is a Unicast subrequest error report.
    * @param queryResults, All the results returned from issuing a Query call. Contains ResDetails and Errors.
    * @param groupID, The Multipath MP-GRI for the gruop to be output.
    * @return List of output for this group and all its subrequests.
    *******************************************************************************************************/
    private ArrayList<String> generateMultipathQueryOutput(SubrequestTuple queryResults, String groupID)
    {
    	ArrayList<String> consoleDisplay = new ArrayList<String>();
    	ArrayList<ResDetails> listOfDetails = queryResults.getAllDetails();
    	ArrayList<List<OSCARSFaultReport>> listOfErrors = queryResults.getAllErrors();
    	    	    	    	    	    	
        for(int d = 0; d < listOfDetails.size(); d++)
        {
        	ResDetails mpDetails = listOfDetails.get(d);				// Details of subrequest query
        	List<OSCARSFaultReport> mpErrors = listOfErrors.get(d);		// Errors of subrequest query
        			    		
            // Generate the output for a single Unicast GRI //
    		SubrequestTuple simplifiedTuple = new SubrequestTuple(mpDetails, mpErrors);
    		ArrayList<String> subrequestDisplay = generateUnicastQueryOutput(simplifiedTuple); 
    		
    		consoleDisplay.addAll(subrequestDisplay);
       	}// End-For loop through ResDetails
   	
  	    consoleDisplay.set(0, "< Multipath Group: " + groupID + ">\n"); // Update the output results for this entire group to include the group's MP-GRI	
    	return consoleDisplay;    	
    }
    
    /*******************************************************************************************************
    * Constructs the ArrayList which becomes the output for Unicast reservation queries.
    * The returned ArrayList has length which is a mulitple of 3.
    * - Every first entry is blank
    * - Every second entry is a Unicast request query result.
    * - Every third entry is a Unicast request error report. 
    * @param queryResults, The results returned from issuing a Query call. Contains ResDetails and Errors.
    * @return A list representing query output for this individual Unicast request.
    *******************************************************************************************************/
    private ArrayList<String> generateUnicastQueryOutput(SubrequestTuple queryResults)
    {	
    	ArrayList<String> consoleDisplay = new ArrayList<String>();
    	ResDetails unicastDetails = queryResults.getDetails();
		List<OSCARSFaultReport> unicastErrors = queryResults.getErrors();

		String uniGRI = unicastDetails.getGlobalReservationId();
		String uniStatus = unicastDetails.getStatus();
		int bandwidth = -1;
		long startTime = -1;
		long endTime = -1;
		Date readableStart = null;
		Date readableEnd = null;
		String allHops = "";
		String allErrorMessages = "";
		String allURNs = "";
		
		ReservedConstraintType uniResConst = unicastDetails.getReservedConstraint();
				
		// Dealing with a RESERVED/ACTIVE/FINISHED/CANCELLED Unicast reservation //
		if(uniResConst != null)
		{
			bandwidth = uniResConst.getBandwidth();
			startTime = uniResConst.getStartTime();
			endTime = uniResConst.getEndTime();
									
			readableStart = new Date(startTime*1000);
			readableEnd = new Date(endTime*1000);
			
			PathInfo pInfo = uniResConst.getPathInfo();
			CtrlPlanePathContent path = pInfo.getPath();
			List<CtrlPlaneHopContent> hops = path.getHop();
			
			// Format Hop output - Include only inter-nodal hops //
			for(int h = 0; h < hops.size() - 1; h++)
			{
				CtrlPlaneHopContent currHop = hops.get(h);
				CtrlPlaneHopContent nextHop = hops.get(h+1);
				
				String currLink = currHop.getLink().getId();
				String nextLink = nextHop.getLink().getId();
				
				String currNode = currLink.substring(currLink.indexOf("node=") + 5, currLink.indexOf(":port="));
				String nextNode = nextLink.substring(nextLink.indexOf("node=") + 5, nextLink.indexOf(":port="));
				
				if(h == 0)
					//allHops += " - " + currNode + " --> ";		// First hop
					allHops += " - " + currLink + " --> ";		// First hop

				if(!currNode.equals(nextNode))
					//	allHops += nextNode + " --> ";
					allHops += nextLink + " --> ";
			}
			
			if(allHops.endsWith(" --> "))
				allHops = allHops.substring(0, allHops.lastIndexOf(" --> "));
		}
		
		// Add all errors associated with this GRI to output list //
		if(unicastErrors != null)
		{
			for(OSCARSFaultReport oneError : unicastErrors)
			{
				allErrorMessages += " ERROR: " + oneError.getErrorMsg() + "\n";
			}
			allErrorMessages += "\n";
		}
		
		// Build the output String //
		String result = "[ " + uniGRI + " ]\n";
		result += uniStatus + "\n";

		if(bandwidth != -1)
		{
			result += "Bandwidth: " + bandwidth + " Mbps\n";
			result += "Start Time:" + readableStart + "\n";
			result += "End Time: " + readableEnd + "\n";
			result += "Hops in Path:  " + allHops + "\n";
			
			if(allErrorMessages.equals(""))
				result += "\n";
		}
		    			
		// Element 1 = Blank -- Will be filled if necessary by the generateMultipathQueryOutput() method 
		// Element 2 = Reservation Details
		// Element 3 = Error Details
		consoleDisplay.add("");
		consoleDisplay.add(result);
		consoleDisplay.add(allErrorMessages);
		
		return consoleDisplay;
    }
}
