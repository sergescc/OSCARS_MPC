package test;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.PropertyConfigurator;

import net.es.oscars.api.soap.gen.v06.ResDetails;

import multipath.MultipathOSCARSClient;

import config.Configuration;

/*********************************************************************************************************
* This class serves to give examples of how a a user-written program would interact with the 
* MultipathOSCARSClient interface and show what sorts of parameters are expected. All scenarios in this
* class pursue Unicast reservations ONLY.
* - Comment out various blocks of code to test separate pieces.
* 
* @author Jeremy
*********************************************************************************************************/
public class TestUnicast 
{
	public static void main(String[] args)
	{		
		PropertyConfigurator.configure("lib/log4j.properties");	// Eliminate Logger warnings.
		
	try{
			
		String oscarsURL = Configuration.oscarsURL;
		long myTime = (long)(System.currentTimeMillis() / 1000L);		// Current time in seconds, represented as a long value
		
		System.out.println("Connecting to OSCARS");
		MultipathOSCARSClient multipathClient = new MultipathOSCARSClient(oscarsURL);
		System.out.println("==============\n");	
		
		
		// TEST createMPReservation() - Timer-automatic //
        System.out.println("Creating new Reservation");
		String gri = multipathClient.createReservation("Reservation from TestUnicast", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=KANS:port=port-3:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		        
		// TEST queryMPReservation() //
		System.out.println("Querying Reservation");
		Thread.sleep(10000);	// Give the reservation time to be processed
		multipathClient.queryMPReservation(gri);
		System.out.println("Query finished!");
		System.out.println("==============\n");
		   
		
		// TEST cancelMPReservation() //
		System.out.println("Cancelling Reservation");
		multipathClient.cancelMPReservation(gri);
		System.out.println("Cancellation complete!");
		System.out.println("==============\n");
		
		/*		
		// TEST modifyMPReservation() //
		System.out.println("Creating preliminary reservation");
		String gri2 = multipathClient.createMPReservation("Reservation from TestUnicast", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=KANS:port=port-3:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		System.out.println("Querying preliminary request");
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Modifying preliminary request");
		multipathClient.modifyMPReservation(gri2, "New Description", 25, myTime+3700, myTime+3820);
		System.out.println("Modification done!");
		System.out.println("==============\n");
		
		System.out.println("Querying modified request");
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		*/
		/*
		// TEST setupMPPath() & teardown<CPath() //
		System.out.println("Creating preliminary reservation");
		String gri3 = multipathClient.createMPReservation("Reservation from TestUnicast", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=KANS:port=port-3:link=link1", true, "any", 25, "signal-xml", myTime+5, myTime+3600);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		System.out.println("Querying preliminary request");
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri3);			// RESERVED
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Setting up path for reservation"); 
		multipathClient.setupMPPath(gri3);					// INSETUP
		System.out.println("Path Setup done!");
		System.out.println("==============\n");
		
		System.out.println("Querying reservation"); 		
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri3);			// ACTIVE
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Tearing Down path for reservation"); 
		multipathClient.teardownMPPath(gri3);				// INTEARDOWN
		System.out.println("Path Teardown done!");
		System.out.println("==============");
		
		System.out.println("Querying reservation"); 		
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri3);			// RESERVED
		System.out.println("Query done!");
		System.out.println("==============\n");
		*/
		
		// TEST listUnicastByStatus() //
		System.out.println("Listing all Unicast with status = RESERVED, CANCELLED, ACTIVE");
		ArrayList<String> status = new ArrayList<String>();
		status.add("RESERVED");
		status.add("CANCELLED");
		status.add("ACTIVE");
		List<ResDetails> listResults = multipathClient.listUnicastByStatus(status);
		for(ResDetails oneListItem : listResults)
		{
			System.out.println("\nGRI: " + oneListItem.getGlobalReservationId());
			System.out.println("Status: " + oneListItem.getStatus());
		}
		System.out.println("Listing complete");
		System.out.println("================\n");
		//
		System.out.println("Now listing ALL Unicast!");
		status = new ArrayList<String>();
		status.add("ALL");
		listResults = multipathClient.listUnicastByStatus(status);
		for(ResDetails oneListItem : listResults)
		{
			System.out.println("\nGRI: " + oneListItem.getGlobalReservationId());
			System.out.println("Status: " + oneListItem.getStatus());
		}
		System.out.println("\nListing complete");
		System.out.println("================\n");
	}
	catch(Exception e){ e.printStackTrace(); }
	}
}
