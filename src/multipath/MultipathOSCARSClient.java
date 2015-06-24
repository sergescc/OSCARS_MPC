package multipath;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneLinkContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlanePathContent;

import net.es.oscars.api.soap.gen.v06.*;
import net.es.oscars.client.*;
import net.es.oscars.common.soap.gen.*;

import config.Configuration;

/** MULTIPATH SUMMARY **
 *  
 * Multipath reservations consist of a number of link-disjoint paths from the same source-port to the same destination-port.
 * A slightly modified version of OSCARS is responsible for computing the link-disjoint paths.
 * - User (front-end application) can specify the number of disjoint paths to form.
 * - Current implementation is best-effort. 
 * -- User specifies N link-disjoint paths. Path #(N-2) fails. Request is finished --> Only N-2 paths will be considered.
 * - Each subrequest's GRI is grouped into a Multipath GRI (MP-GRI). These disjoint reservations can then be treated as a group using the MP-GRI. 
 * -- Subrequests can still be handled as individual unicast reservations (depending on front-end application permissions).
 * - All subrequests are uniform: Every subrequest will have the same bandwidth, start-time, description, etc. upon creation.
 * 
 * This class contains the "CORE" methods necessary to perform both Unicast and Multipath operations.
 * Helper methods are implemented in classes named with "Helper<Operation>".
 **/						
public class MultipathOSCARSClient
{
    protected static final String mpQueryOut = Configuration.queryOutputFile;		// File containing subrequest statuses for queried MP reservations.
    protected static final String mpLookupGRI = Configuration.mpGriLookupFile;		// File which acts as the MP-GRI lookup table
    protected static final String mpTrackerGRI = Configuration.mpGriTrackerFile;	// File which provides persistent ID for next MP-GRI
    
	public boolean silentQuery = false;		// Used to suppress distracting query output when the user is not directly intending to query
	
	protected OSCARSClient oscarsClient;
	
	private HelperCreate createHelper = new HelperCreate();				// Provides access to createMPReservation() helper methods
	private HelperQuery queryHelper = new HelperQuery();				// Provides access to queryMPReservation() helper methods
	private HelperGroup groupHelper = new HelperGroup();				// Provides access to groupReservations() helper methods
	private HelperMiscellaneous miscHelper = new HelperMiscellaneous();	// Provides access to miscellaneous helper methods called by various methods in this class
	
	private boolean isPartOfMultipathPoll = false;	// MultipathPoll() needs to query unicast GRIs. This variable makes sure they are handled correctly.
	protected boolean readyToGetMetrics = false;	// Safety variable. Do not want to calculate metrics on MP requests before they are finished.
													// For example: Metrics are recorded in multipathPoll(), but this is called both before & after 
													//		minThreshold and maxCutoff have been evaluated and subrequests cancelled appropriately. 
													//		The value after the evaluation is the one that need to be recorded for accurate results.
	
	private ArrayList<SubrequestTuple> globalDesiredInfo = new ArrayList<SubrequestTuple>();
	
	public ArrayList<SubrequestTuple> getLastMPQuery()
	{
		return globalDesiredInfo;
	}
	
	/*********************************************************************************************************************************************************
	* Constructor - Establishes connection with OSCARS and handles WS-Security issues
	* 
	* @param oscarsURL, URL indicating where the running instance of OSCARS v0.6 resides. May be remote.
	**********************************************************************************************************************************************************/
	public MultipathOSCARSClient(String oscarsURL)
	{		
		@SuppressWarnings("unused")
		Configuration config  = new Configuration();		// Sets up the necessary security for connecting to OSCARS services
			    	
		try
        {
			oscarsClient = new OSCARSClient(oscarsURL);		// Connect to OSCARS
    		    		
    		System.out.println("OSCARS Connection successfully established!");
        }
        catch(OSCARSClientException ce) 
        {
        	System.err.println("OSCARSClientException thrown trying to initialize OSCARSClient");
        	ce.printStackTrace();
        }
    	catch(Exception e)
    	{
    		System.err.println("Exception thrown trying to initialize OSCARSClient");
    		e.printStackTrace();
    	}
	}
	
	/*********************************************************************************************************************************************************
	* Queries a specific GRI to get its path information. Then that path information is converted to a String which is returned to the calling function.
	* The calling function can then use the path string as an OptionalConstraint for future reservations.
	* 
	* @param gri
	* @return String representation of this GRI's reserved path
	**********************************************************************************************************************************************************/	
	public String convertPathToString(String gri)
	{
		String thisPath = "";	
        SubrequestTuple completedReservation;
        
		isPartOfMultipathPoll = true;
		silentQuery = true;
		completedReservation = subrequestPoll(gri);
		silentQuery = false;
		isPartOfMultipathPoll = false;
		
		globalDesiredInfo.add(completedReservation);	// Copy to global list for easier access without re-querying
		        
        ResDetails oneReservationDetails = completedReservation.getAllDetails().get(0);
        ReservedConstraintType oneReservationConstraint = oneReservationDetails.getReservedConstraint();
        PathInfo oneReservationPathInfo = null;
        CtrlPlanePathContent oneReservationPath = null;
        List<CtrlPlaneHopContent> hops;
         
        if(oneReservationConstraint == null)
        {
        	return null;
        }
        	
        oneReservationPathInfo = oneReservationConstraint.getPathInfo();
        oneReservationPath = oneReservationPathInfo.getPath();
        	  	         
        if(oneReservationPath != null) 
        {
        	hops = oneReservationPath.getHop();
            
        	if(hops.size() > 0) 
        	{
        		for(CtrlPlaneHopContent ctrlHop : hops) 
        		{
        			CtrlPlaneLinkContent link = ctrlHop.getLink();
                     
        			thisPath += link.getId() + ";";
        		}
        	}
        }

        return thisPath;
	}
	
	
	/*********************************************************************************************************************************************************
	* Trivial method used to call the createMPReservation() method. This method is used to mimic the existing OSCARS API for unicast circuit
	*   reservations. This method simply sets the number of link-disjoint paths to 1 and calls the createMPReservation() function in this class.
	*********************************************************************************************************************************************************/
	public String createReservation(String description, String srcUrn, boolean isSrcTagged, String srcTag, String destUrn, boolean isDestTagged, String destTag, int bandwidth, String pathSetupMode, long startTimestamp, long endTimestamp)
	{
			return createMPReservation(description, srcUrn, isSrcTagged, srcTag, destUrn, isDestTagged, destTag, bandwidth, pathSetupMode, startTimestamp, endTimestamp, 1);
	}
	
	
	/*********************************************************************************************************************************************************
	* Creates a MP reservation to the multiple destinations specified in the destUrn parameter. 
	* MP reservation is a logical collection of multiple subrequests, one to each destination. 
	* These subrequests are submitted to OSCARS individually.
	* - Best-effort multipath reservation. Reserves disjoint paths between source and destination until numDisjoinPaths are reserved or reservation of a disjoint path fails.
	* @param description
	* @param srcUrn
	* @param isSrcTagged
	* @param srcTag, "any" is only Tag supported presently
	* @param destUrn, Multipath destination is in the form: "multipath(<domain1>[<node1>:<port1>:<link1>,<node2>:<port2>:<link2>, ...];<domain2>[<node1>:<port1>:<link1>, ...])"
	* @param isDestTagged
	* @param destTag, "any" is only Tag supported presently
	* @param bandwidth
	* @param pathSetupMode
	* @param startTimestamp
	* @param endTimestamp
	* @param numDisjointPaths, Multipath parameter: number of disjoint paths to establish (if possible) between source and destination.
	* @return GRI of created reservation
	**********************************************************************************************************************************************************/
	public String createMPReservation(String description, String srcUrn, boolean isSrcTagged, String srcTag, String destUrn, boolean isDestTagged, String destTag, int bandwidth, String pathSetupMode, long startTimestamp, long endTimestamp, int numDisjointPaths)
	{
		String griToReturn = "";
    	    	        
        ArrayList<ResCreateContent> allResCreateContents = new ArrayList<ResCreateContent>();
        ArrayList<CreateReply> allCreateReplies = new ArrayList<CreateReply>();
        globalDesiredInfo = new ArrayList<SubrequestTuple>();
        FileWriter griStream;
   		BufferedWriter outGri;
   		Integer thisMPGri = new Integer(0);
   		int requestNum = 0;
   		
   		String mpOutput = "";
    	String workingGriMP = "";  
    	String shortGriMP = "";
    	
    	String resourceShortageNotice = "";
    	
    	int disjointPaths = numDisjointPaths;
  		
    	try
    	{
	   		// Open up the persistent mp_gri_tracker.txt to find out what the unique GRI for this MP request should be //
	   		if(numDisjointPaths > 1)
	   		{
	   			thisMPGri = miscHelper.getMPGri(thisMPGri);
	   				        		        	
	     		List<OptionalConstraintType> allPreviousPaths = new ArrayList<OptionalConstraintType>();
		   		
		    	//Submit createReservation request to OSCARS for EACH subrequest
		   		while(disjointPaths > 0)
		   		{
		   			ResCreateContent oneResCreateContent = createHelper.constructResCreateContent(description, srcUrn, isSrcTagged, srcTag, destUrn, isDestTagged, destTag, bandwidth, pathSetupMode, startTimestamp, endTimestamp);
		   			allResCreateContents.add(oneResCreateContent);
		   			
		   			// Do this unless it is the first path reserved
		   			if(requestNum > 0)
		   			{                                
		        		for(int opt = 0; opt < allPreviousPaths.size(); opt++)
		        		{
		        			oneResCreateContent.getOptionalConstraint().add(allPreviousPaths.get(opt));
		        		}
		   			}
		   			
		   			CreateReply oneCreateResponse;
					oneCreateResponse = oscarsClient.createReservation(oneResCreateContent);

		   			allCreateReplies.add(oneCreateResponse);
		
		   	        workingGriMP = workingGriMP + oneCreateResponse.getGlobalReservationId() + ":";		// Update the MP-GRI with the subrequest GRI
		   	        
					
		   	        requestNum++;
		   	        disjointPaths--;
		   			
		   	        // Take the current path and convert it to a String of the hops, and then add the path String to the list of OptionalConstraints //
		   	        System.out.println("Polling Multipath subrequest (" + requestNum + " of " + numDisjointPaths + ") for status. Please wait a moment...");

		   	        String thisPath = convertPathToString(oneCreateResponse.getGlobalReservationId());
		   	        		   	        
		   	        if(thisPath == null)
		   	        {
		   	        	resourceShortageNotice = "\n** Only " + (requestNum-1) + " of the desired " + numDisjointPaths + " disjoint paths could be reserved!\n";
		   	        	resourceShortageNotice += "** This Multipath GRI consists of " + requestNum + " unicast subrequest GRIs."; 
		   	        			
		   	        	System.out.println(resourceShortageNotice);
		   	        	break;
		   	        }
		   	        
		   	        /**MULTIPATH + ANYCAST (June 5th, 2013)--- Take the Destination URN from the last path routed, set all following destinations equal to this***/
		   	        /**Function: First request goes in as Anycast, then once the destination is chosen, route all additional paths to that same destination (unicast)**/
		   	        /**Should have no effect on a Multipath Unicast Request**/
					if(requestNum == 1)
					{
						int lastSemi = thisPath.length()-1;
						int secondLastSemi = thisPath.lastIndexOf(";", lastSemi - 1);
						destUrn = thisPath.substring(secondLastSemi + 1, thisPath.length()-1);
					}		
					/***MULTIPATH + ANYCAST***/
					
		   	        OptionalConstraintType previousPath = new OptionalConstraintType();
		   	        previousPath.setCategory("BASIC_MULTIPATH_SERVICE");
		   	        OptionalConstraintValue optValue = new OptionalConstraintValue();
		   	        optValue.setStringValue(thisPath);
		   	        previousPath.setValue(optValue);
		 			allPreviousPaths.add(previousPath);
		   	        // //
		   		}
		        
		   		mpOutput = "\n[createMultipathReservation]  complete.\nWorking MP-Gri = ";
	        	workingGriMP = "MP-" + (thisMPGri.intValue()) + ":_" + requestNum + "_:";
	        	shortGriMP = "MP-" + (thisMPGri.intValue());

	        	for(int res = 0; res < allResCreateContents.size(); res++)
	        	{
	        		CreateReply oneReply = allCreateReplies.get(res);
	        		System.out.println("\n[createReservation]  " + (res+1) + " of " + requestNum + "\nGRI = " + oneReply.getGlobalReservationId() + "\nstatus=" + oneReply.getStatus());
	        		
	        		workingGriMP += oneReply.getGlobalReservationId() + ":";
	        	}
	        		
	        	workingGriMP = workingGriMP.substring(0, workingGriMP.length()-1);	// Handles trailing colon in MP-GRI string //
	        	mpOutput = mpOutput + shortGriMP;
	        	mpOutput = mpOutput + resourceShortageNotice;
	        	System.out.println(mpOutput);   

		   		try
	        	{
	        		//Add entry into MP-GRI lookup table for this request.
	        		if(workingGriMP.contains("MP-0:")) 		//Clear the GRI lookup file if the MP-GRIs have been reset. Prevents duplicate IDs in lookup table.
	        		{
	        			griStream = new FileWriter(mpLookupGRI);
	        		}
	        		else
	        		{
	        			griStream = new FileWriter(mpLookupGRI, true); // Otherwise append to existing lookup file
	        		}
	        		
	        		outGri = new BufferedWriter(griStream);
	        		outGri.write(shortGriMP + "_=_" + workingGriMP + "\n");		//Maps short GRI to working GRI
	        		        		
	        		outGri.close();
	        		griStream.close();
	        	}
	        	catch(Exception e)
	        	{
	        		System.err.println("Error Writing MP-GRI to \'" + mpLookupGRI + "\'.");
	        		e.printStackTrace();
	        	}
		        		    			    		
	    		griToReturn = shortGriMP;

	   		}
	   		// Traditional Unicast //
	    	else	
	    	{
	    		ResCreateContent createRequest = createHelper.constructResCreateContent(description, srcUrn, isSrcTagged, srcTag, destUrn, isDestTagged, destTag, bandwidth, pathSetupMode, startTimestamp, endTimestamp);
	    		
	    		CreateReply createResponse = oscarsClient.createReservation(createRequest);		// Submit createReservation() request to OSCARS for unicast reservation
	   		
	   			System.out.println("\n[createReservation]  gri= " + createResponse.getGlobalReservationId() + "\ntransactionId=" + createResponse.getMessageProperties().getGlobalTransactionId() + "\nstatus=" + createResponse.getStatus());
	   			
	   			griToReturn = createResponse.getGlobalReservationId();
	   		} 

	    }
    	catch (OSCARSFaultMessage e1) {e1.printStackTrace();} 
    	catch (OSCARSClientException e1) {e1.printStackTrace();}
		   		
   		return griToReturn;
	}
	
	/*********************************************************************************************************************************************************
	* Controller method to perform a query. 
	* The method queryMPRes does the actual query work. 
	* This method exists just to give the end-user some uniformity in invoking the methods. 
	*   
	* @param gri
	* @return A list of SubrequestTuples. These can take on a number of forms, but they are wrappers to encapsulate data from requests.
	* 	- Currently set to return a list of ResDetails and OSCARSFaultReports for Multipath
	* 	- Currently set to return a single ResDetails and a OSCARSFaultReport for Unicast
	*********************************************************************************************************************************************************/
	public ArrayList<SubrequestTuple> queryMPReservation(String gri)
	{
		ArrayList<SubrequestTuple> desiredQueryInfo = new ArrayList<SubrequestTuple>();
							
		// Start with a fresh Query output file for every request //
		File queryFile = new File(mpQueryOut);
		if(queryFile.exists())
			queryFile.delete();
		
		queryMPRes(gri, desiredQueryInfo);
	/*	
		System.out.println("WHATS IN IT?: " + desiredQueryInfo.get(0).getGroupGRI());
		System.out.println("SOURCE SIZE = " + desiredQueryInfo.size());
		//globalDesiredInfo = new ArrayList<SubrequestTuple>(desiredQueryInfo.size());
		globalDesiredInfo = (ArrayList<SubrequestTuple>)(desiredQueryInfo.clone());
		
		System.out.println("DEST SIZE = " + globalDesiredInfo.size());
		Collections.copy(globalDesiredInfo, desiredQueryInfo);
	*/							
		return desiredQueryInfo;
	}
		
	/*********************************************************************************************************************************************************
	* Queries a reservation. 
	* IF parameter gri starts with substring "MP-", THEN request is queried as a group (Each sub-gri queried individually).
	* ELSE query behaves just as it would for unicast requests.
	* 
	* @param gri, unicast or MP GRI to query
	* @param queryInformation, A SubrequestTuple. This will be used by the end-user application to compile the desired info about the queried GRI. 
	**********************************************************************************************************************************************************/
	private boolean queryMPRes(String gri, ArrayList<SubrequestTuple> queryInformation)
	{
		String mpGRI = gri;
		String shortGRI = gri;
		boolean isMultipathRequest = false;
    	
    	try 
    	{
        	/**
    	    * QueryResReply						-->	List<OSCARSFaultReports>
    	    * - ResDetails						-->	GRI, Login, Description, Create Time, Status
    	    * -- ReservedConstraintType			--> Start Time, End Time, Bandwidth
    	    * --- PathInfo						-->	Path Setup Mode, Path Type
    	    * ---- CtrlPlanePathContent 		-->	List<Hops>
    	    * ----- CtrlPlaneHopContent			-->	Domain, Node, Port, Link
    	    * ---- Layer2Info					-->	Src Endpoint, Dst Endpoint
    	    * ----- VlanTag						-->	VLAN value, IsTagged?
    	    * - OSCARSFaultReports				--> Error Code, Error Type, Error Message
    	    **/
                    	
        	
            mpGRI = miscHelper.getRegularMPGri(gri);			// Convert GRI into expected regular-format
            shortGRI = miscHelper.getShortMPGri(mpGRI);		// Convert GRI into short-format for simpler output later in this method.	
                        
            if(!gri.equals(mpGRI) || mpGRI.contains("MP") || isPartOfMultipathPoll)
            {
            	isMultipathRequest = true;
            }
            
            // Multipath Query -- Treated by OSCARS as a set of individual unicast queries, but allows user to query all GRIs in an MP-Group together.
            if(isMultipathRequest)
            {
            	ArrayList<QueryResContent> allMPGRI = queryHelper.buildAllQueryResContents(mpGRI);	//Break up the MP-GRI into Query objects for each subrequest GRI to submit to OSCARS.
            	           
               	ArrayList<ResDetails> allResDetails = new ArrayList<ResDetails>();
               	ArrayList<List<OSCARSFaultReport>> allFaultReports = new ArrayList<List<OSCARSFaultReport>>();
               	ArrayList<Exception> allResExceptions = new ArrayList<Exception>();
                              	
               	int mpID = 0;
               	int numDests = allMPGRI.size();
               	for(QueryResContent oneMPGRI : allMPGRI)
               	{   
               		mpID++;
               		               		
                   	QueryResReply queryResponse = null;
                   	ResDetails details = null;
                    Exception queryException = null;
                   	List<OSCARSFaultReport> faultReports = null;
                   	
                   	try
                   	{
                   		queryResponse = oscarsClient.queryReservation(oneMPGRI);		// Call queryReservation() in OSCARS

                       	details = queryResponse.getReservationDetails();    // Actual ResDetails for this unicast subrequest                	              	
                       	queryException = new Exception("OK");				// Dummy Exception for this unicast subrequest
                       	faultReports = queryResponse.getErrorReport();		// Actual FaultReport list for this unicast subrequest
                   	}
                   	catch(Exception e)
                   	{
                   		details = new ResDetails();							// Dummy ResDetails for this unicast subrequest
                   		queryException = e;									// Actual Exception for this unicast subrequest
                   		faultReports = new ArrayList<OSCARSFaultReport>();	// Dummy FaultReport list for this unicast subrequest
                   		
                   		// In-case there is a BAD_GRI submitted by user, this will prevent NullPointerExceptions when printing out query summary
                   		details.setGlobalReservationId(oneMPGRI.getGlobalReservationId());
                   		details.setStatus("BAD_GRI");
                   	}
                   	finally
                   	{                  		                   	                  	
	                   	// Now add the results of this unicast subrequest to the lists.
	                   	allResExceptions.add(queryException);
	                   	allResDetails.add(details);
	                   	allFaultReports.add(faultReports);
                    }
                   	
                   	if(!silentQuery)
                   	{
	                   	if(queryResponse != null)
	                   		System.out.println("\n[queryReservation]  " + (mpID) + " of " + numDests + "\nGRI = " + details.getGlobalReservationId() + "\nStatus: " + details.getStatus().toString());
	                   	else
	              			System.out.println("\n[queryReservation]  " + (mpID) + " of " + numDests + "\nGRI = " + details.getGlobalReservationId() + "\nStatus: " + queryException.getMessage());
	                   	
	                   	if (faultReports != null && !faultReports.isEmpty()) 
                   		{
                   			System.err.println(queryHelper.printFaultDetails(faultReports));
                   		}
                   	}
               	} //end-for             	
               	
               	// cancelMPReservation() calls queryMPReservation() to ensure that the reservations to be cancelled is cancellable. This code writes subrequest statuses to mp_query_out.txt so cancleMPReservation() can read it.
               	queryHelper.writeAllStatusesToQueryOutputFile(shortGRI, allMPGRI, allResDetails, allResExceptions, allFaultReports);
               	
               	if(!silentQuery)
               	{
               		// Prints out relevant (according to the code author) info for the subrequests individually as an MP-group summary //
               		queryHelper.printMPResDetails(shortGRI, allMPGRI, allResDetails, allFaultReports);
               	}
               	
               	// A new SubrequestTuple will be created for each subrequest.
               	queryInformation.add(new SubrequestTuple(shortGRI, allResDetails, allFaultReports));	
            }//end-if(isMultipathRequest)
           	
            // Traditional Unicast query/ //   
            else
            {
        		QueryResContent queryRequest = new QueryResContent();
               	QueryResReply queryResponse;
               	ResDetails details;
               	List<OSCARSFaultReport> faultReports;

                queryRequest.setGlobalReservationId(gri);
               	
                queryResponse = oscarsClient.queryReservation(queryRequest);		// Call queryReservation() in OSCARS 
                
                details = queryResponse.getReservationDetails();
                              
                if(!silentQuery)
                	queryHelper.printResDetails(details);			// Prints out all information for this reservation
                                                               	               
               	faultReports = queryResponse.getErrorReport();

               	queryInformation.add(new SubrequestTuple(details, faultReports));	// Supply results of the query back to end-user, calling function
               	
               	if (faultReports != null && !faultReports.isEmpty()) 
               	{
               		System.err.println(queryHelper.printFaultDetails(faultReports));
               	}
            }
               
    	} // end-try
   		catch(OSCARSClientException ce) 
   		{
   			ce.printStackTrace();
   		}    	
        catch(OSCARSFaultMessage fm) 
        {
            fm.printStackTrace();
        }
   		catch(Exception e)
   		{
   			e.printStackTrace();
   		}
    	
    	return true;
	}

	
	/**********************************************************************************************************************************************************
	* Cancels an existing reservation. 
	* IF parameter gri starts with substring "MP", THEN request is cancelled as a group (Each sub-gri cancelled individually).
	* ELSE cancel behaves just as it would for unicast requests.
	*   
	* @param gri
	**********************************************************************************************************************************************************/
	public void cancelMPReservation(String gri)
	{	
		String mpGRI = gri;
		String shortGRI = gri;
		boolean isMultipathRequest = false;
        
		try
		{
	        mpGRI = miscHelper.getRegularMPGri(gri);
	        shortGRI = miscHelper.getShortMPGri(mpGRI);
	        
	        if(!gri.equals(mpGRI) || mpGRI.contains("MP"))
	        {
	        	isMultipathRequest = true;
	        }
	        
	        // Multipath Cancel -- Treated by OSCARS as a set of individual unicast cancels, but allows user to cancel all GRIs in an MP-Group together.
	        if(isMultipathRequest)
	        {
	        	int mpID = 0;
	        	int numDests = 0;
	                          	
	           	// Must query first to make sure subrequest can be safely cancelled, special behavior required for sub-groups
	        	silentQuery = true;		// Suppress query output
	        	queryMPReservation(gri);
	        	silentQuery = false;
	        	
	        	ArrayList<SubrequestTuple> allSubrequests = queryHelper.readQueryOutput();
	        	
	        	numDests = allSubrequests.size();
	        	for(SubrequestTuple oneSubrequest : allSubrequests)
	        	{   
	        		if(!silentQuery)
	        		{
	        			System.out.println("\n[cancelReservation]  " + (++mpID) + " of " + numDests);
	        			System.out.println("GRI = " + oneSubrequest.getGRI());
	        		}
	        		
	        		if(oneSubrequest.getStatus().equals("BAD_GRI"))
	        		{
	        			if(!silentQuery)
	        			{
	        				System.out.println("-- CANNOT CANCEL THIS SUBREQUEST");                       				
	        				System.out.println("---> Because: Current subrequest GRI is invalid!  Skipping...");
	        			}
	        			
	        			continue;
	        		}
	        		else
	        		{
	                	CancelResContent cancelRequest = new CancelResContent();
	                	CancelResReply cancelResponse;
	                	
	                	cancelRequest.setGlobalReservationId(oneSubrequest.getGRI());
	                	
	                	try
	                	{
	                		cancelResponse = oscarsClient.cancelReservation(cancelRequest);	// Submit cancelReservation() request to OSCARS
	                	}
	                	catch(OSCARSFaultMessage fm) 
	                    {
	                		if(!silentQuery)
	                			System.err.println("Error: " + fm.getMessage());
	                		
	                    	continue;
	                    }
	                	
	                	if(!silentQuery)
	                		System.out.println("Status = " + cancelResponse.getStatus());
	        		}
				} //End-For
	        	
	        	if(!silentQuery)
	        		System.out.println("\n[cancelMultipathPath] for gri = " + shortGRI + " complete.");
	        } //End-If(isMultipath)
	        
	        // Traditional unicast cancelReservation()
	        else
	        {
	        	/**++++++++++++++++++++++++++++++++++++++++++++++++++++++
	             * CancelResReply				-->	Status
	             **+++++++++++++++++++++++++++++++++++++++++++++++++++++*/
	        	
	        	CancelResContent cancelRequest = new CancelResContent();
	        	CancelResReply cancelResponse = null;
	        	
	        	cancelRequest.setGlobalReservationId(gri);
	        	
	        	try
	        	{
	        		cancelResponse = oscarsClient.cancelReservation(cancelRequest);	// Submit cancelReservation() request to OSCARS
	        		
	        		if(!silentQuery)
		        		System.out.println("[cancelReservation] gri = " + gri + ", status = " + cancelResponse.getStatus());
	        	}
	        	catch(OSCARSFaultMessage fm) 
                {
           			System.err.println("[cancelReservation] gri = " + gri + ", Error: " + fm.getMessage());
                }
	        }
		}
   		catch(OSCARSClientException ce) 
   		{
   			ce.printStackTrace();
   		}    	
   		catch(Exception e)
   		{
   			e.printStackTrace();
   		}            	
	}

	/*********************************************************************************************************************************************************
	* Constructs a ModifyResContent object by aggregating the given input parameters. 
	* - NOTE: Current implementation does NOT support Optional Constraints, and supports only Layer2 parameters.
	* 
	* @param gri
	* @param description
	* @param bandwidth
	* @param startTimestamp
	* @param endTimestamp
	* @return
	*********************************************************************************************************************************************************/
	private ModifyResContent constructModifyResContent(String gri, String description, int bandwidth, long startTimestamp, long endTimestamp)
	{		
		/**
		* ModifyResContent					-->	GRI, Description
		* - UserRequestConstraintType		-->	Start Time, End Time, Bandwidth
		* -- PathInfo						-->	Path Setup Mode, Path Type
		* --- CtrlPlanePathContent 			-->	List<Hops>
		* ---- CtrlPlaneHopContent			-->	Domain, Node, Port, Link
		* --- Layer2Info					-->	Src Endpoint, Dst Endpoint
		* ---- VlanTag						-->	VLAN value, IsTagged?
		**/
		ModifyResContent modify = new ModifyResContent();
				
		UserRequestConstraintType userConstraint = new UserRequestConstraintType();
			
		// Complete UserRequestConstraint population //
		if(startTimestamp != -1)	// User never entered Start Time
			userConstraint.setStartTime(startTimestamp);
			
		if(endTimestamp != -1)		// User never entered End Time
			userConstraint.setEndTime(endTimestamp);
			
		if(bandwidth != -1)			// User never entered bandwidth
			userConstraint.setBandwidth(bandwidth);
			
		// Complete ModifyResContent population //
		if(!description.equals(""))
			modify.setDescription(description);
		
		modify.setGlobalReservationId(gri);
	    modify.setUserRequestConstraint(userConstraint);
		
        return modify;
	}
	
	/*********************************************************************************************************************************************************
	* Modifies an existing reservation. 
	* IF parameter gri starts with substring "MP", THEN request is treated as a group (Each sub-gri request modified individually).
	* ELSE modifyReservation behaves just as it would for unicast requests.
	* 
	* Some members of an MP-Group may not be modified uniformly (some may be in a state where modification isn't allowed for example, while others are not).
	* 
	* NOTE: This method was built to match the version in IDCTest.java which supports only changing the description, bandwidth, startTime and endTime. 
	* 
	* @param gri
	* @param description
	* @param bandwidth
	* @param startTimestamp
	* @param endTimestamp
	**********************************************************************************************************************************************************/
	public void modifyMPReservation(String gri, String description, int bandwidth, long startTimestamp, long endTimestamp)
	{	
		String mpGRI = gri;
		String shortGRI = gri;
		boolean isMultipathRequest = false;
    	
       	try
       	{
	        mpGRI = miscHelper.getRegularMPGri(gri);			// Convert GRI into expected regular-format
	        shortGRI = miscHelper.getShortMPGri(mpGRI);		// Convert GRI into short-format for simpler output later in this method.	
	                        
	        if(!gri.equals(mpGRI) || mpGRI.contains("MP"))
	        {
	        	isMultipathRequest = true;
	        }
	            
	        // Multipath modify -- Treated by OSCARS as a set of individual unicast requests, but allows user to modify all GRIs in an MP-Group together.
	        if(isMultipathRequest)
	        {
	        	int mpID = 0;
	        	int numDests = 0;
	                          	
	        	// Query MP-GRI first to make sure subrequests can be safely modified, special behavior necessary for sub-groups
	        	queryMPReservation(gri);
	        	ArrayList<SubrequestTuple> allSubrequests = queryHelper.readQueryOutput();
	        		        	
	        	numDests = allSubrequests.size();
	        	for(SubrequestTuple oneSubrequest : allSubrequests)
	        	{        			        		
	        		System.out.println("\n[modifyReservation]  " + (++mpID) + " of " + numDests);
	        		System.out.println("GRI = " + oneSubrequest.getGRI());
	        		
	        		if(oneSubrequest.getStatus().equals("BAD_GRI"))
	        		{
	        			System.out.println("-- CANNOT MODIFY THIS SUBREQUEST");                       				
	        			System.out.println("---> Because: Current subrequest GRI is invalid!  Skipping...");
	        			continue;
	        		}
	        		else
	        		{
	    	        	ModifyResContent modifyRequest = constructModifyResContent(oneSubrequest.getGRI(), description, bandwidth, startTimestamp, endTimestamp);
	                	ModifyResReply modifyResponse = null;
	                		                	
	                	try
	                	{
	                		modifyResponse = oscarsClient.modifyReservation(modifyRequest);	// Submit modifyReservation() request to OSCARS
	                	}
	                	catch(OSCARSFaultMessage fm) 
	                    {
	                    	System.err.println("Error: " + fm.getMessage());
	                    	continue;
	                    }
	                	
	                	System.out.println("Status = " + modifyResponse.getStatus());
	        		}
				} //End-For
	        	
	        	System.out.println("\n[modifyMultipathReservation] for gri = " + shortGRI + " complete.");
	        } //End-If(isMultipath)
	        
	        // Traditional Unicast modifyReservation()
	        else
	        {
	        	/**++++++++++++++++++++++++++++++++++++++++++++++++++++++
	             * ModifyResReply					-->	GRI, Status
	             **+++++++++++++++++++++++++++++++++++++++++++++++++++++*/
	        	ModifyResContent unicastModifyRequest = constructModifyResContent(gri, description, bandwidth, startTimestamp, endTimestamp);
	        	ModifyResReply unicastModifyResponse;
	        		        	
	        	unicastModifyResponse = oscarsClient.modifyReservation(unicastModifyRequest);	// Submit modifyReservation() request to OSCARS
	        	
	        	System.out.println("[modifyReservation] gri = " + gri + ", status = " + unicastModifyResponse.getStatus());
	        }
       	}
   		catch(OSCARSClientException ce) 
   		{
   			ce.printStackTrace();
   		}    	
        catch(OSCARSFaultMessage fm) 
        {
        	System.err.println("Error: " + fm.getMessage());
        }
   		catch(Exception e)
   		{
   			e.printStackTrace();
   		}
	}
	
	
	/*********************************************************************************************************************************************************
	* Creates the path(s) for a signalled reservation. 
	* IF parameter gri starts with substring "MP", THEN request is treated as a group (Each sub-gri path created individually).
	* ELSE createPath behaves just as it would for unicast requests.
	* 
	* Paths may only be setup if they are RESERVED && are reserved with pathSetupType = "signal-xml", and the current time is during the scheduled reservation.
	* 
	* @param gri, unicast or MP GRI to query
	**********************************************************************************************************************************************************/
	public void setupMPPath(String gri)
	{	
		String mpGRI = gri;
		String shortGRI = gri;
		boolean isMultipathRequest = false;
    	
       	try
       	{
	        mpGRI = miscHelper.getRegularMPGri(gri);			// Convert GRI into expected regular-format
	        shortGRI = miscHelper.getShortMPGri(mpGRI);		// Convert GRI into short-format for simpler output later in this method.	
	                        
	        if(!gri.equals(mpGRI) || mpGRI.contains("MP"))
	        {
	        	isMultipathRequest = true;
	        }
	            
	        // Multipath setupPath -- Treated by OSCARS as a set of individual unicast requests, but allows user to createPaths for all GRIs in an MP-Group together.
	        if(isMultipathRequest)
	        {
	        	int mpID = 0;
	        	int numDests = 0;
	                          	
	        	// Query MP-GRI first to make sure subrequests can be safely setup, special behavior necessary for sub-groups
	        	queryMPReservation(gri);
	        	ArrayList<SubrequestTuple> allSubrequests = queryHelper.readQueryOutput();
	        	
	        	numDests = allSubrequests.size();
	        	for(SubrequestTuple oneSubrequest : allSubrequests)
	        	{        		
	        		System.out.println("\n[setupPath]  " + (++mpID) + " of " + numDests);
	        		System.out.println("GRI = " + oneSubrequest.getGRI());
	        		
	        		// Do not process special cases //
	        		if(oneSubrequest.getStatus().equals("BAD_GRI"))
	        		{
	        			System.out.println("-- CANNOT CREATE PATH FOR THIS SUBREQUEST");                       				
	        			System.out.println("---> Because: Current subrequest GRI is invalid!  Skipping...");
	        			continue;
	        		}
	        		else
	        		{
	                	CreatePathContent setupRequest = new CreatePathContent();
	                	CreatePathResponseContent setupResponse = null;
	                	
	                	setupRequest.setGlobalReservationId(oneSubrequest.getGRI());
	                	
	                	try
	                	{
	                		setupResponse = oscarsClient.createPath(setupRequest);	// Submit createPath() request to OSCARS
	                	}
	                	catch(OSCARSFaultMessage fm) 
	                    {
	                    	System.err.println("Error: " + fm.getMessage());
	                    	continue;
	                    }
	                	
	                	System.out.println("Status = " + setupResponse.getStatus());
	        		}
				} //End-For
	        	
	        	System.out.println("\n[setupMultipathPath] for gri = " + shortGRI + " complete.");
	        } //End-If(isMultipath)
	        
	        // Traditional Unicast createPath()
	        else
	        {
	        	/**++++++++++++++++++++++++++++++++++++++++++++++++++++++
	             * CreatePathResponseContent			-->	GRI, Status
	             **+++++++++++++++++++++++++++++++++++++++++++++++++++++*/
	        	CreatePathContent unicastSetupRequest = new CreatePathContent();
	        	CreatePathResponseContent unicastSetupResponse;
	        	
	        	unicastSetupRequest.setGlobalReservationId(gri);
	        	
	        	unicastSetupResponse = oscarsClient.createPath(unicastSetupRequest);		// Submit createPath() request to OSCARS
	        	
	        	System.out.println("[setupPath] gri = " + gri + ", status = " + unicastSetupResponse.getStatus());
	        }
       	}
   		catch(OSCARSClientException ce) 
   		{
   			ce.printStackTrace();
   		}    	
        catch(OSCARSFaultMessage fm) 
        {
        	System.err.println("Error: " + fm.getMessage());
        }
   		catch(Exception e)
   		{
   			e.printStackTrace();
   		}
	}

	
	/*********************************************************************************************************************************************************
	* Tears down the path(s) for active, signalled reservations. 
	* IF parameter gri starts with substring "MP", THEN request is treated as a group (Each sub-gri path torn down individually).
	* ELSE teardownPath behaves just as it would for unicast requests.
	* 
	* Paths may only be torn down if they are ACTIVE && were reserved with pathSetupType = "signal-xml", and the current time is during the scheduled reservation.
	* 
	* @param gri, unicast or MP GRI to query
	**********************************************************************************************************************************************************/
	public void teardownMPPath(String gri)
	{		
		String mpGRI = gri;
		String shortGRI = gri;
		boolean isMultipathRequest = false;
    	
       	try
       	{
	        mpGRI = miscHelper.getRegularMPGri(gri);			// Convert GRI into expected regular-format
	        shortGRI = miscHelper.getShortMPGri(mpGRI);		// Convert GRI into short-format for simpler output later in this method.	
	                        
	        if(!gri.equals(mpGRI) || mpGRI.contains("MP"))
	        {
	        	isMultipathRequest = true;
	        }
	            
	        // Multipath teardownPath -- Treated by OSCARS as a set of individual unicast requests, but allows user to teardownPaths for all GRIs in an MP-Group together.
	        if(isMultipathRequest)
	        {
	        	int mpID = 0;
	        	int numDests = 0;
	                          	
	        	// Query MP-GRI first to make sure subrequests can be safely tornDown, special behavior necessary for sub-groups
	        	queryMPReservation(gri);
	        	ArrayList<SubrequestTuple> allSubrequests = queryHelper.readQueryOutput();
	        	
	        	numDests = allSubrequests.size();
	        	for(SubrequestTuple oneSubrequest : allSubrequests)
	        	{        		
	        		System.out.println("\n[teardownPath]  " + (++mpID) + " of " + numDests);
	        		System.out.println("GRI = " + oneSubrequest.getGRI());
	        		
	        		if(oneSubrequest.getStatus().equals("BAD_GRI"))
	        		{
	        			System.out.println("-- CANNOT TEARDOWN PATH FOR THIS SUBREQUEST");                       				
	        			System.out.println("---> Because: Current subrequest GRI is invalid!  Skipping...");
	        			continue;
	        		}
	        		else
	        		{
	                	TeardownPathContent teardownRequest = new TeardownPathContent();
	                	TeardownPathResponseContent teardownResponse = null;
	                	
	                	teardownRequest.setGlobalReservationId(oneSubrequest.getGRI());
	                	
	                	try
	                	{
	                		teardownResponse = oscarsClient.teardownPath(teardownRequest);	// Submit teardownPath() request to OSCARS
	                	}
	                	catch(OSCARSFaultMessage fm) 
	                    {
	                    	System.err.println("Error: " + fm.getMessage());
	                    	continue;
	                    }
	                	
	                	System.out.println("Status = " + teardownResponse.getStatus());
	        		}
				} //End-For
	        	
	        	System.out.println("\n[teardownMultipathPath] for gri = " + shortGRI + " complete.");
	        } //End-If(isMultipath)
	        
	        // Traditional Unicast teardownPath()
	        else
	        {
	        	/**++++++++++++++++++++++++++++++++++++++++++++++++++++++
	             * TeardownPathResponseContent			-->	GRI, Status
	             **+++++++++++++++++++++++++++++++++++++++++++++++++++++*/
	        	TeardownPathContent unicastTeardownRequest = new TeardownPathContent();
	        	TeardownPathResponseContent unicastTeardownResponse;
	        	
	        	unicastTeardownRequest.setGlobalReservationId(gri);
	        	
	        	unicastTeardownResponse = oscarsClient.teardownPath(unicastTeardownRequest);		// Submit teardownPath() request to OSCARS
	        	
	        	System.out.println("[teardownPath] gri = " + gri + ", status = " + unicastTeardownResponse.getStatus());
	        }
       	}
   		catch(OSCARSClientException ce) 
   		{
   			ce.printStackTrace();
   		}    	
        catch(OSCARSFaultMessage fm) 
        {
        	System.err.println("Error: " + fm.getMessage());
        }
   		catch(Exception e)
   		{
   			e.printStackTrace();
   		}
	}
	
	
	/*********************************************************************************************************************************************************
	* Controller function for reservation grouping operations. Groups may NOT consist of sub-groups.
	*  
	* @param groupGRI, The MP-GRI of group to which we want to add/remove a link-disjoint path.
	* @param numAdditionalDisjoint, The number of link-disjoint paths to add to the group specified by groupGRI. Only applicable if adding.
	*********************************************************************************************************************************************************/
	public String groupReservations(ArrayList<String> gris, boolean add, int numAdditionalDisjoint)
	{
		if(gris.size() == 0)
			return null;
		
		String groupGRI = gris.get(0);
				
		// If the lookup file doesn't already exist, create it //
		File lookupFile = new File(mpLookupGRI);
		if(!lookupFile.exists())
		{
			try
			{
				FileWriter lookupStream = new FileWriter(mpLookupGRI);
	       		BufferedWriter lookupWriter = new BufferedWriter(lookupStream);
	       		
	       		lookupWriter.write("");
	       		
	       		lookupWriter.close();
	       		lookupStream.close();
			}
			catch(IOException e){ e.printStackTrace(); }
		}
		
		if(!groupGRI.contains("MP-") && !add)
		{
			System.err.println("Remove operation cannot be performed on a unicast GRI.");
			return null;
		}
		else if(!add)	// Remove GRIs from MP-GRI group
		{
			return groupHelper.subFromGroup(gris);
		}
		else if(!groupGRI.contains("MP-") && add)	// Disjoint-clone unicast request
		{
			return groupHelper.duplicateUnicast(gris.get(0), this, numAdditionalDisjoint);
		}
		else			// Add another disjoint path to an existing group
		{
			return groupHelper.addToGroup(gris.get(0), this, numAdditionalDisjoint);
		}
		
	}

	/*********************************************************************************************************************************************************
	* Polls (Queries) a Multipath subrequest reservation set every 5 seconds until it reaches a final state.
	* - Final states: {ACTIVE, RESERVED, FINISHED, FAILED, CANCELLED, UNKNOWN}
	* - Successful states: {ACTIVE, RESERVED, FINISHED}
	* 
	* @param mpGRI
	* @return SubrequetTuple objects for the subrequest, containing GRI, and Status, etc.
	*********************************************************************************************************************************************************/
	private SubrequestTuple subrequestPoll(String mpGRI)
	{
		boolean firstPoll = true;
		
    	while(true)
		{
    		ArrayList<SubrequestTuple> queryResults = null;
    		String status = "";
    		
    		if(!firstPoll)
    		{
    			System.out.println(" - Still polling, please wait a moment...");
    		}
						
			try
			{
				Thread.sleep(5000);		//Poll for request status every 5 seconds.
			}
			catch(Exception e){ e.printStackTrace(); }

			silentQuery = true;	// Turn off unnecessary query output messages
			queryResults = queryMPReservation(mpGRI);	// Perform the query
			silentQuery = false;
						    									            	
        	status = queryResults.get(0).getAllDetails().get(0).getStatus();
        	System.out.println("STATUS = " + status);							            			
        	if(status.contains("ACTIVE") || status.contains("RESERVED") || status.contains("FINISHED"))
        	{
        		// This unicast request was successful. //
        		return queryResults.get(0);
        	}
        	else if(status.contains("FAILED") || status.contains("UNKNOWN") || status.contains("CANCELLED") || status.contains("BAD_GRI"))
        	{
        		// This unicast request was processed, but unsuccessful. //
        		return queryResults.get(0);
        	}
        	else
        	{
        		// This unicast request has not been processed yet, keep polling until it is.
        		;
        	}
        	
        	firstPoll = false;
        }
	}

	
	
	/*********************************************************************************************************************************************************
	* Polls (Queries) a Multipath reservation set every 5 seconds until all subrequests reach a final state.
	* - Final states: {ACTIVE, RESERVED, FINISHED, FAILED, CANCELLED, UNKNOWN}
	* 
	* Once all subrequests are in final states, it tracks the number of successful subrequests, and sets the global variable numSuccessfulCircuits with that value.
	* - Successful states: {ACTIVE, RESERVED, FINISHED}
	* 
	* @param mpGRI
	* @param mpNumDestinations, Size of Multipath request set
	* @return SubrequetTuple objects for each subrequest, containing GRI, and Status
	*********************************************************************************************************************************************************/
	@SuppressWarnings("unused")
	private SubrequestTuple[] multipathPoll(String mpGRI, int mpNumPaths)
	{
    	int finishedRequests;
    	
    	SubrequestTuple[] allRequests = new SubrequestTuple[mpNumPaths]; 
    	    	
    	while(true)
		{
			System.out.println("\n - Polling Multipath request for status. Please wait a moment...\n");
			
			try
			{
				Thread.sleep(5000);		//Poll for request status every 5 seconds.
			}
			catch(Exception e){ e.printStackTrace(); }

			silentQuery = true;	// Turn off unnecessary query output messages
			queryMPReservation(mpGRI);	// Perform the query
			silentQuery = false;
			
    		finishedRequests = 0;
    									            	
        	try
        	{
        		// Open the file containing output from query call above to get status of MP subrequests // 
        		FileInputStream fstream = new FileInputStream(mpQueryOut);
        		DataInputStream in = new DataInputStream(fstream);
        		BufferedReader br = new BufferedReader(new InputStreamReader(in));
        		String strLine;
        							            		
        		//Read first line in file
        		strLine = br.readLine();
        		        					            		                            
        		for(int mpID = 0; mpID < mpNumPaths; mpID++)
        		{        			
        			strLine = br.readLine();
        			
        			String token = "gri: ";	//token to parse string to get GRI of subrequest.
        			String subGRI = strLine.substring(strLine.indexOf(token)+token.length(), strLine.indexOf("status:")-2);
        			String subStatus = strLine.substring(strLine.indexOf("status: ")+8);
        			        			
        			SubrequestTuple oneSubrequest = new SubrequestTuple(subGRI, subStatus);
        			allRequests[mpID] = oneSubrequest;
        								            			
        			if(subStatus.contains("ACTIVE") || subStatus.contains("RESERVED") || subStatus.contains("FINISHED"))
        			{
        				finishedRequests++;
        				continue;
        			}
        			else if(subStatus.contains("FAILED") || subStatus.contains("UNKNOWN") || subStatus.contains("CANCELLED") || subStatus.contains("BAD_GRI"))
        			{
        				finishedRequests++;
        				continue;		  // This subrequest has been processed by Coordiator & PCE stack, check next subrequest.
        			}
        			else
        			{
        				break;			  // This subrequest has not been completely processed yet. Wait five seconds and poll again.
        			}
        		}
        	
        		br.close();
        		in.close();
        		fstream.close();
        	}
        	catch (Exception e){System.err.println("Error: " + e.getMessage());}
        
        	if(finishedRequests == mpNumPaths)
        		break;
		}
    	    	
    	return allRequests;
	}
	
	
	/*********************************************************************************************************************************************************
	* Allows end-user to obtain a list of all Unicast GRIs matching the statuses passed in in parameter statusesToList.
	* If all available statuses are passed in, the user will be able to obtain a list of ALL unicast GRIs in the system.
	*  
	* NOTE: This method isn't terribly efficient since two listReservations() calls must be sent to OSCARS.
	* - For some reason, trying to list FAILED statuses alongside CANCELLED statuses causes GRIs to be omitted from both lists.
	* - This method lists the FAILED GRIs separately from the rest and then combines them into one list to return. 
	* 
	* @param statusesToList, List of status Strings. All GRIs matching statuses in this parameter will be returned.
	* @return A list of all GRIs matching the passed in statuses.
	*********************************************************************************************************************************************************/
	public List<ResDetails> listUnicastByStatus(ArrayList<String> statusesToList)
	{
		boolean includesFAILED = false;					// Has the user also requested FAILED statuses?
		ListRequest listRequest = new ListRequest();
		ListReply listResponse = new ListReply();
		List<ResDetails> statusesToReturn = null;		// List of ResDetails for each GRI to be returned
		
		// Just make it easier for the user //
		if(statusesToList.contains("ALL"))
		{
			statusesToList = new ArrayList<String>();
			statusesToList.add(OSCARSClient.STATUS_ACCEPTED);
			statusesToList.add(OSCARSClient.STATUS_ACTIVE);
			statusesToList.add(OSCARSClient.STATUS_CANCELLED);
			statusesToList.add(OSCARSClient.STATUS_COMMITTED);
	    	statusesToList.add(OSCARSClient.STATUS_FAILED);
	    	statusesToList.add(OSCARSClient.STATUS_FINISHED);
	    	statusesToList.add(OSCARSClient.STATUS_INCANCEL);
	    	statusesToList.add(OSCARSClient.STATUS_INCOMMIT);
	    	statusesToList.add(OSCARSClient.STATUS_INMODIFY);
	    	statusesToList.add(OSCARSClient.STATUS_INPATHCALCULATION);
	    	statusesToList.add(OSCARSClient.STATUS_INSETUP);
	    	statusesToList.add(OSCARSClient.STATUS_INTEARDOWN);
	    	statusesToList.add(OSCARSClient.STATUS_OK);
	    	statusesToList.add(OSCARSClient.STATUS_PATHCALCULATED);
	    	statusesToList.add(OSCARSClient.STATUS_RESERVED);
	    	statusesToList.add("UNKNOWN");
		}
		
		List<String> existingStatusList = listRequest.getResStatus();
		
		// For some reason the results are not returned correctly if FAILED statuses are listed alongside CANCELLED. 
		// So this code omits the FAILED listing until all other Lists have been returned, and then lists FAILED separately.
		if(statusesToList.contains(OSCARSClient.STATUS_FAILED))
		{
			includesFAILED = true;
			statusesToList.remove(OSCARSClient.STATUS_FAILED);
		}
		
		for(String oneStatus : statusesToList)
		{
			existingStatusList.add(oneStatus);
		}
				
		try
		{
			listResponse = oscarsClient.listReservations(listRequest); 		// Invoke the listReservations call in OSCARS
			statusesToReturn = listResponse.getResDetails();
			
			// Now get the FAILED statuses too //
			if(includesFAILED)
			{
				listRequest = new ListRequest();
				listRequest.getResStatus().add(OSCARSClient.STATUS_FAILED);
				listResponse = oscarsClient.listReservations(listRequest);	// Invoke the listReservations call in OSCARS
				
				statusesToReturn.addAll(listResponse.getResDetails());		// Add all listed ResDetails to return list.
			}
			
		}
		catch(OSCARSFaultMessage fm) 
		{
			System.err.println("Error: " + fm.getMessage());
	    }
		catch(OSCARSClientException ce) 
		{
			System.err.println("Error: " + ce.getMessage());
	    }
		
		return statusesToReturn;
	}
	
	
	/*********************************************************************************************************************************************************
	* Allows end-user to obtain the subrequest members of all Multipath group GRIs matching those passed in as part of groupGRIs.
	* If all existing group GRIs are passed in, the user will be able to obtain a list of ALL groups and their subrequests in the system.
	*   
	* @param groupGRIs, List of groups to list. All GRIs matching strings in this parameter will be returned.
	* @return A list of all details about every listed group GRI.
	*********************************************************************************************************************************************************/
	public ArrayList<SubrequestTuple> listGroupMembers(ArrayList<String> groupGRIs)
	{
		ArrayList<SubrequestTuple> allGroupsToReturn = new ArrayList<SubrequestTuple>();
		ArrayList<String> allGroupsInSystem = new ArrayList<String>();
		
		// Open up the Multipath GRI lookup table and identify all existing groups //	
		try
    	{
			FileInputStream groupStream = new FileInputStream(mpLookupGRI);
			DataInputStream groupIn = new DataInputStream(groupStream);
			BufferedReader groupBR = new BufferedReader(new InputStreamReader(groupIn));
			String strLine;
    		
    	 	while(true)
	    	{
				strLine = groupBR.readLine();
					
				if(strLine == null)
					break;
					
				String oneGroupGRI = miscHelper.getShortMPGri(strLine);
				allGroupsInSystem.add(oneGroupGRI);							
			}
    	}
    	catch(IOException e)
    	{ 
    		e.printStackTrace(); 
    	}
			
		// Get results for ALL groups in the system -- Makes the listing easier for end-users //
		if(groupGRIs.contains("ALL"))
		{
			groupGRIs = allGroupsInSystem;
		}
					
		// Query the groups and add the results to the return list //
		for(String oneGroup : groupGRIs)	
		{	
			// If the group doesn't exist, let the user know //
			if(!allGroupsInSystem.contains(oneGroup))
			{
				ResDetails dummyDeet = new ResDetails();
				ArrayList<ResDetails> dummyDetails = new ArrayList<ResDetails>();
				
				dummyDeet.setStatus("GROUP DOES NOT EXIST!");
				dummyDetails.add(dummyDeet);
				
				SubrequestTuple nonExistantGroup = new SubrequestTuple(oneGroup, dummyDetails, new ArrayList<List<OSCARSFaultReport>>());
				allGroupsToReturn.add(nonExistantGroup);
				
				continue;
			}
			
			// Get details on all list members //
			silentQuery = true;
			ArrayList<SubrequestTuple> queryResults = queryMPReservation(oneGroup);
			silentQuery = false;
				
			allGroupsToReturn.add(queryResults.get(queryResults.size()-1));	// Do not include recursive lists 
		}
		
		return allGroupsToReturn;
	}
}
