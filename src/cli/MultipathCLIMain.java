package cli;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.PropertyConfigurator;

import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.utils.config.ConfigHelper;

import multipath.AnycastHandler;
import multipath.MultipathOSCARSClient;
import multipath.SubrequestTuple;
import config.Configuration;

/**
 * @author Jeremy, some portions trimmed down and re-purposed from OSCARS api test file IDCTest.java
 * 
 * This class acts as an interface to the end-user who wishes to make use of the MultipathOSCARSClient.
 * It has been designed to reflect much of the behavior of IDCTest.java.
 * - For example, it is intended to be called with arguments passed in. Those arguments are parsed to determine which
 *   Multipath/Unicast operation is desired and what parameters will be passed to that operation.
 * - Supported operations: createMPReservation, queryMPReservation, cancelMPReservation, modifyMPReservation, setupMPPath, teardownMPPath, groupReservations, listReservations
 */
public class MultipathCLIMain 
{
	private static MultipathOSCARSClient multipathClient;
	
	public static void main(String args[])
	{
		PropertyConfigurator.configure("lib/log4j.properties");	// Eliminate Logger warnings.
		
		String oscarsURL = Configuration.oscarsURL;	//Where is the instance of OSCARS this client will use?
		
		System.out.println("Connecting to OSCARS...");
		multipathClient = new MultipathOSCARSClient(oscarsURL);
		System.out.println("===========");
				
		parseArguments(args);	// Calls appropriate method

		System.out.println("\n=== Simulation complete. ===");		
	}
	
	public MultipathCLIMain()
	{
		String oscarsURL = Configuration.oscarsURL;	//Where is the instance of OSCARS this client will use?
		
		System.out.println("Connecting to OSCARS...");
		multipathClient = new MultipathOSCARSClient(oscarsURL);
		System.out.println("===========");
	}
	
	/* Invoke queryMPReservation call in MultipathOSCARSClient */
	private static void invokeQueryMPReservation(String gri)
	{
		multipathClient.queryMPReservation(gri);
	}

	/* Invoke cancelMPReservation call in MultipathOSCARSClient */	
	private static void invokeCancelMPReservation(String gri)
	{
		multipathClient.cancelMPReservation(gri);
	}

	/* Invoke setupMPPath call in MultipathOSCARSClient */
	private static void invokeSetupMPPath(String gri)
	{
		multipathClient.setupMPPath(gri);
	}

	/* Invoke teardownMPPath call in MultipathOSCARSClient */	
	private static void invokeTeardownMPPath(String gri)
	{
		multipathClient.teardownMPPath(gri);
	}

	/* Invoke groupMPReservations ADD call in MultipathOSCARSClient. 
	 * numDisjoint is the number of new link-disjoint paths to add to gri */	
	private static void invokeGroupAddReservations(String gri, String numDisjoint)
	{
		ArrayList<String> griList = new ArrayList<String>();
		griList.add(gri);
		
		int disjointMax = new Integer(numDisjoint).intValue();
		
		System.out.println("NUMBER OF DISJOINT = " + disjointMax);
		
		multipathClient.groupReservations(griList, true, disjointMax);
	}
	
	/* Invoke groupMPReservations SUB call in MultipathOSCARSClient. 
	 * gris contains the group MP-GRI and the member GRIs to remove from that group */	
	private static void invokeGroupSubReservations(ArrayList<String> theGRIs)
	{		
		multipathClient.groupReservations(theGRIs, false, 0);
	}
	
	/* Invoke modifyMPReservation call in MultipathOSCARSClient.
	 * MultipathOSCARSClient currently only supports modification of description, bandwidth, startTime, and endTime
	 * Start & End times will be in 'YYYY-MM-DD HH:MM' format, and must be converted to OSCARS-readable long values */
	private static void invokeModifyMPReservation(String gri, String description, String band, String start, String end) 
	{
		Integer bw = new Integer(band);
		int bandwidth = bw.intValue();
		
		long startTime = -1;
		long endTime = -1;
		
		if(!start.equals("") && !end.equals(""))
		{
			long[] times = parseTimes(start, end);
			startTime = times[0];
			endTime = times[1];
		}
		
		multipathClient.modifyMPReservation(gri, description, bandwidth, startTime, endTime);
	}

	/* Invoke listUnicastByStatus call in MultipathOSCARSClient, and prints output appropriately */	
	private static void invokeListUnicast(ArrayList<String> statusesToList)
	{
		List<ResDetails> listResults = multipathClient.listUnicastByStatus(statusesToList);
		
		for(ResDetails oneListItem : listResults)
		{
			System.out.println("\nGRI: " + oneListItem.getGlobalReservationId());
			System.out.println("Status: " + oneListItem.getStatus());
		}
	}
	
	/* Invoke listGroupMembers call in MultipathOSCARSClient, and prints output appropriately */	
	private static void invokeListGroups(ArrayList<String> groupGRIs)
	{
		ArrayList<SubrequestTuple> allGroups = multipathClient.listGroupMembers(groupGRIs);
		
		System.out.println("\n~~ Listing Groups ~~\n   --------------\n");
		
		for(SubrequestTuple oneGroup : allGroups)
        {
        	System.out.println("Group GRI: " + oneGroup.getGroupGRI() + "\n=================");
        	
        	ArrayList<ResDetails> allMemberDetails = oneGroup.getAllDetails();
        	
        	for(ResDetails oneMemberDetails : allMemberDetails)
        	{
        		String gri = oneMemberDetails.getGlobalReservationId();
        		String status = oneMemberDetails.getStatus();
        		
        		if(status.equals("GROUP DOES NOT EXIST!"))
        		{
        			System.out.println("- " + status);
        			continue;
        		}
        		
        		System.out.println("GRI: " + gri);
        		
        		if(status.equals("MP-QUERIED"))
        			System.out.println("Status: MP-GROUP");
        		else
        			System.out.println("Status: " + status);
        	}
        	
        	System.out.println("=================\n");
        }
	}
	
	/* Invoke createMPReservation call in MultipathOSCARSClient.
	 * Argument is a YAML file, containing parameters to populate the createResContent object in OSCARS.
	 *  - This YAML file must first be read, and arguments parsed, before the createMPReservation operation can be invoked. */
	private static String invokeCreateMPReservation(String yamlFile)
	{
		String gri = "";
		ArrayList<Object> createArgs = configureParamFile(yamlFile);
        
		String sourceURN = (String)createArgs.get(0);
		String destURN = (String)createArgs.get(1);
		Integer bandwidth = (Integer)createArgs.get(2);
		String description = (String)createArgs.get(3);
		String startTime = (String)createArgs.get(4);
		String endTime = (String)createArgs.get(5);
		String pathSetupMode = (String)createArgs.get(6);
		String srcVLAN = (String)createArgs.get(7);
		String destVLAN = (String)createArgs.get(8);
		Integer mpNumPaths = (Integer)createArgs.get(9);
		
		boolean isSrcTagged = true;
		boolean isDstTagged = true;
				
		long[] startAndEndTimes = parseTimes(startTime, endTime);
		long startTimestamp = startAndEndTimes[0];
		long endTimestamp = startAndEndTimes[1];
		
		if(destURN.contains("node=anycast"))
		{
			AnycastHandler handler = new AnycastHandler(multipathClient);
			gri = handler.handleAnycastRequest(description, sourceURN, isSrcTagged, srcVLAN, destURN, isDstTagged, destVLAN, bandwidth, pathSetupMode, startTimestamp, endTimestamp, mpNumPaths);
		}
		else
		{
			gri = multipathClient.createMPReservation(description, sourceURN, isSrcTagged, srcVLAN, destURN, isDstTagged, destVLAN, bandwidth, pathSetupMode, startTimestamp, endTimestamp, mpNumPaths);
		}
		return gri;
		
	}
	
	/* Breaks down program arguments and passes them to the appropriate invoker methods to perform desired operation */
	public static String parseArguments(String[] args)
	{
		String result = "";
		if(args.length < 3)
			usage();
		else
		{
			String operation = args[0];
			String flag = args[1];
			String parameter = args[2];
			
			if(operation.equals("createMPReservation") && flag.equals("-pf"))
				result = invokeCreateMPReservation(parameter);
			else if(operation.equals("queryMPReservation") && flag.equals("-gri"))
				invokeQueryMPReservation(parameter);
			else if(operation.equals("cancelMPReservation") && flag.equals("-gri"))
				invokeCancelMPReservation(parameter);
			else if(operation.equals("setupMPPath") && flag.equals("-gri"))
				invokeSetupMPPath(parameter);
			else if(operation.equals("teardownMPPath") && flag.equals("-gri"))
				invokeTeardownMPPath(parameter);
			else if(operation.equals("groupReservations") && (flag.equals("-add")))
				invokeGroupAddReservations(parameter, args[3]);
			else if(operation.equals("groupReservations") && (flag.equals("-sub")))
			{							
				ArrayList<String> gris = new ArrayList<String>();
				
				for(int a = 2; a < args.length; a++)
				{
						gris.add(args[a]);
				}
				
				invokeGroupSubReservations(gris);
			}			
			else if(operation.equals("modifyMPReservation") && flag.equals("-gri"))
			{
				String gri = args[2];
				String description = new String();
				String bandwidth = new String();
				String newStart = new String();
				String newEnd = new String();
				
				if(args.length == 3 || args.length % 2 == 0 || args.length > 11)
					usage();
				
				System.out.println("INSIDE MODIFY");
				System.out.println("ARGS LENGTH = " + args.length);
				
				for(int a = 3; a < args.length - 1; a += 2)
				{
					String nextFlag = args[a];
					String nextParam = args[a+1];
					
					System.out.println("ARGUMENT [" + a + "] = " + nextFlag);
					System.out.println("ARGUMENT [" + (a+1) + "] = " + nextParam);
					
					if(nextFlag.equals("-d"))
						description = nextParam;
					else if(nextFlag.equals("-bw"))
						bandwidth = nextParam;
					else if(nextFlag.equals("-start"))	//YYYY:MM:DD:HH:mm
						newStart = nextParam;
					else if(nextFlag.equals("-end"))	//YYYY:MM:DD:HH:mm
						newEnd = nextParam;
				}
				
				if(!newStart.equals(""))
				{
					String[] partsOfTime = newStart.split(":");
					if(partsOfTime.length != 5)
						usage();
					
					// This program expects  YYYY-MM-DD HH:mm - Need to reformat //
					newStart = partsOfTime[0] + "-" + partsOfTime[1] + "-" + partsOfTime[2] + " " + partsOfTime[3] + ":" + partsOfTime[4]; 
				}
				if(!newStart.equals(""))
				{
					String[] partsOfTime = newEnd.split(":");
					if(partsOfTime.length != 5)
						usage();
					
					// This program expects  YYYY-MM-DD HH:mm - Need to reformat //
					newEnd = partsOfTime[0] + "-" + partsOfTime[1] + "-" + partsOfTime[2] + " " + partsOfTime[3] + ":" + partsOfTime[4]; 
				}
				
				if(bandwidth.equals(""))
					bandwidth = "-1";
				
				invokeModifyMPReservation(gri, description, bandwidth, newStart, newEnd);
			}
			else if(operation.equals("listReservations") && (flag.equals("-uni")))
			{
				ArrayList<String> statuses = new ArrayList<String>();
				
				for(int a = 2; a < args.length; a++)
				{
					statuses.add(args[a]);
				}
				
				invokeListUnicast(statuses);
			}
			else if(operation.equals("listReservations") && (flag.equals("-grp")))
			{
				ArrayList<String> groups = new ArrayList<String>();
				
				for(int a = 2; a < args.length; a++)
				{
					groups.add(args[a]);
				}
				
				invokeListGroups(groups);
			}
			else
			{
				usage();
			}
		}
		
		return result;
	}
	
	
	/* Convert readable date string-format to long-format compatible with OSCARS */	
	private static long[] parseTimes(String start_time, String end_time) 
	{     
        Long startTime = 0L;
        Long endTime = 0L;
        
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        
        // Convert start time into Long format for OSCARS to read //
        if (start_time == null || start_time.equals("now")) 
        {
        	startTime = System.currentTimeMillis()/1000;
        } 
        else if(start_time.equals(""))
        {
        	; // Do nothing -- Let MultipathOSCARSClient deal with this (for modify)
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
            ;	// Do nothing -- Let MultipathOSCARSClient deal with this
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
                Integer seconds = Integer.valueOf(hm[0])*3600*24; 	//days
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
	
	/* Parses the YAML file for invokeCreateMPReservation()
	 * - More values might be listed in the YAML file, but this method gets only those necessary for basic Multipath reservation creation.
	 * - Values parsed: 
     * - - Src
     * - - Dst
     * - - Bandwidth
     * - - Description
     * - - Start Time
     * - - End Time
     * - - Path Setup Mode
     * - - Path Type
     * - - Src VLAN
     * - - Dst VLAN
     * - - Number of Disjoint Paths
	 * Also performs error checks to ensure values are appropriately set */
    public static ArrayList<Object> configureParamFile(String paramFile) 
    {
    	File pFile;
    	
        if (paramFile == null) 
        	die("Error: Parameter File is NULL");
        
        pFile = new File(paramFile);
    	
        if(!pFile.exists())
        	die("Error: Parameter File does not exist!");

        Map<?, ?> config = ConfigHelper.getConfiguration(paramFile);
        Map<?, ?> store = (Map<?, ?>) config.get("create");

        String src = (String) store.get("src");
        String dst = (String) store.get("dst");
        Integer bandwidth = (Integer) store.get("bandwidth");
        String description = (String) store.get("description");
        String startTime = (String) store.get("start-time");
        String endTime = (String) store.get("end-time");
        String pathSetupMode = (String) store.get("path-setup-mode");
        String srcVlan = (String) store.get("srcvlan");
        String dstVlan = (String) store.get("dstvlan");
        Integer numPaths = (Integer) store.get("disjoint-paths");
        	    	    		        
        if(src == null || src.equals(""))
            die("Error parsing Parameter file: Source must be specified");

        if(dst == null || dst.equals(""))
            die("Error parsing Parameter file: Destination must be specified");
        
        if(bandwidth == null)
            die("Error parsing Parameter file: Bandwidth must be specified");
        
        if(description == null || description.equals("")) 
            die("Error parsing Parameter file: Description must be specified");
        
        if(startTime == null || startTime.equals(""))
        	die("Error parsing Parameter file: Start time must be specified");
        
        if(endTime == null || endTime.equals(""))
        	die("Error parsing Parameter file: End time must be specified");

        if(pathSetupMode == null || pathSetupMode.equals(""))
        	die("Error parsing Parameter file: Path Setup Mode must be specified");
        
        if(numPaths == null || numPaths.equals(""))	// Not all YAML files contain this field, default value = 0.
			numPaths = 0;

                
        if(srcVlan == null || srcVlan.equals(""))
        	srcVlan = "any";
        	
        if(dstVlan == null || dstVlan.equals(""))
        	dstVlan = "any";
                
        ArrayList<Object> YAML = new ArrayList<Object>();       
        YAML.add(src);
        YAML.add(dst);
        YAML.add(bandwidth);
        YAML.add(description);
        YAML.add(startTime);
        YAML.add(endTime);
        YAML.add(pathSetupMode);
        YAML.add(srcVlan);
        YAML.add(dstVlan);
        YAML.add(numPaths);
        
        return YAML; 
    }

    /* Groups action of printing error and exiting -- makes other methods cleaner */
	private static void die(String errorMessage)
	{
		System.err.println(errorMessage);
		System.exit(-1);
	}

	/* How the program should be used. Called if User enters bad parameters */
	private static void usage()
	{
		System.out.println("USAGE:");
		System.out.println("======");
		System.out.println("createRes\t-pf\t<parameter_file>");
		System.out.println("queryRes \t-gri\t<gri>");
		System.out.println("cancelRes\t-gri\t<gri>");
		System.out.println("modifyRes\t-gri\t<gri>\t-d\t<description>");
		System.out.println("         \t-gri\t<gri>\t-bw\t<bandwidth>");
		System.out.println("         \t-gri\t<gri>\t-start\t<start time (YYYY:MM:DD:HH:mm)>");
		System.out.println("         \t-gri\t<gri>\t-end\t<end time (YYYY:MM:DD:HH:mm)>");
		System.out.println("setupPath\t-gri\t<gri>");
		System.out.println("teardownPath\t-gri\t<gri>");
		System.out.println("groupRes \t-add\t<gri> <numNewDisjointPaths>");
		System.out.println("         \t-sub\t<griGroup> <griSrc1> <griSrc2> ... <griSrcK>");
		System.out.println("listRes  \t-uni\t<status1> <status2> <status3> ... <statusK> | <ALL>");
		System.out.println("         \t-grp\t<griGroup1> <griGroup2> <griGroup3> ... <griGroupK> | <ALL>");
		
		System.exit(-1);
	}
	
	
}
