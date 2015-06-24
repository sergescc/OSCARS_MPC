package multipath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;

/***********************************************************************************************************************
* This class provides helper methods needed for various MultipathOSCARSClient methods to work appropriately.
* This class exists solely to provide a higher layer of modularity and keep MultipathOSCARSClient.java clean.
* 
* @author Jeremy
***********************************************************************************************************************/
public class HelperMiscellaneous 
{
	public static final String mpQueryOut = MultipathOSCARSClient.mpQueryOut;	// File containing subrequest statuses for queried MP reservations.
    public static final String mpLookupGRI = MultipathOSCARSClient.mpLookupGRI;	// File which acts as the MP-GRI lookup table
    public static final String mpTrackerGRI = MultipathOSCARSClient.mpTrackerGRI; // File containing tracker for next MP-GRI number
    
    
	/*********************************************************************************************************************************************************
	* Inspects the MP-GRI lookup table (file) to determine if the given groupGRI exists. 
	* IF the GRI exists, THEN the corresponding regular-format MP-GRI will be returned.
	* ELSE will return the unaltered groupGRI.
	* 
	* @param groupGRI
	* @return regular-format MP-GRI corresponding to input parameter groupGRI, or the original groupGRI if no such correspondence exists.
	*********************************************************************************************************************************************************/	
	protected String getRegularMPGri(String groupGRI)
	{
		String originalGRI = groupGRI;
		
		// MP-GRI simple-format:   "MP-ID"
		// MP-GRI regular-format: "MP-ID:_K_:<gri1>:<gri2>:<griK>" 	// K = Destination Set size
		// MP-GRI long-format:  "MP-ID_=_MP-ID:_K_:<gri1>:<gri2>:<griK>"
		
		if(groupGRI.startsWith("MP"))
		{   
			// MP-GRI is in short-format --> Open up mp_gri_lookup.txt and lookup the corresponding long-format MP-GRI. //
			if(!groupGRI.contains(":_")) 	
           	{
               	try
               	{
               		FileInputStream griStream = new FileInputStream(mpLookupGRI);
            		DataInputStream griIn = new DataInputStream(griStream);
            		BufferedReader griBr = new BufferedReader(new InputStreamReader(griIn));
            		String strGriLine;
 
            		while(true)
            		{
            			strGriLine = griBr.readLine();
            			
            			if(strGriLine == null)
            				break;
 
            			// If MP-GRI is valid, convert short-format to corresponding long-format MP-GRI. //
            			if(strGriLine.substring(0, strGriLine.indexOf("_=_")).equals(groupGRI))
            			{
            				groupGRI = strGriLine;;
            				break;
            			}
            		}
            		
            		griBr.close();
            		griIn.close();
            		griStream.close();
               	}
            	catch(Exception e)
            	{
            		System.err.println("Problem looking up MP-GRI in \'mp_gri_lookup.txt\'");
            		System.err.println(originalGRI + " is not a valid MP-GRI, cannot Query.");
            		System.exit(-1);
            	}
           	}
			
			// MP-GRI is in long-format --> convert to corresponding regular-format MP-GRI. //
			if(groupGRI.contains("_=_"))		
			{
				groupGRI = groupGRI.substring(groupGRI.indexOf("_=_")+3);
			}                     	
        }
		
		return groupGRI;		// If unicast, gri will be returned unaltered.
	}

	/*********************************************************************************************************************************************************
	* Truncates the given groupGRI from long-format and/or regular-format. 
	* 
	* @param groupGRI
	* @return short-format MP-GRI corresponding to input parameter groupGRI, or the original groupGRI if no such correspondence exists.
	*********************************************************************************************************************************************************/	
	protected String getShortMPGri(String groupGRI)
	{
		// MP-GRI simple-format:   "MP-ID"
		// MP-GRI regular-format: "MP-ID:_K_:<gri1>:<gri2>:<griK>" 	// K = Destination Set size
		// MP-GRI long-format:  "MP-ID_=_MP-ID:_K_:<gri1>:<gri2>:<griK>"

		// MP-GRI is in regular-format or long-format --> truncate to the corresponding short-format MP-GRI. //
		if(groupGRI.contains(":_")) 	
        {
           groupGRI = groupGRI.substring(0, groupGRI.indexOf(":_"));
        }
					
		// MP-GRI was originally in long-format --> convert to corresponding short-format MP-GRI. //
		if(groupGRI.contains("_=_"))		
		{
			groupGRI = groupGRI.substring(0, groupGRI.indexOf("_=_"));
		}                     	

		return groupGRI;		// If already in short-form, gri will be returned unaltered.
	}
	
	/*********************************************************************************************************************************************************
	* Inspects the MP-GRI lookup table (file) to determine if the given groupGRI exists. 
	* IF the GRI exists, THEN the corresponding long-format MP-GRI will be returned.
	* ELSE will return a newly created long-format MP-GRI.
	* 
	* @param groupGRI
	* @return long-format MP-GRI corresponding to input parameter gri, or a new long-format MP-GRI if no such correspondence exists.
	*********************************************************************************************************************************************************/
	protected String getLongMPGri(String gri)
	{
		String shortGRI;
	
		// MP-GRI simple-format:   "MP-ID"
		// MP-GRI regular-format: "MP-ID:_K_:<gri1>:<gri2>:<griK>" 	// K = Destination Set size
		// MP-GRI long-format:  "MP-ID_=_MP-ID:_K_:<gri1>:<gri2>:<griK>"
		if(!gri.startsWith("MP"))
		{   
			return null;	// All group IDs must start with "MP"
		}
		else
		{
			try
			{
				gri = getShortMPGri(gri);
				shortGRI = gri;
								
		   		FileInputStream griStream = new FileInputStream(mpLookupGRI);
        		DataInputStream griIn = new DataInputStream(griStream);
        		BufferedReader griBr = new BufferedReader(new InputStreamReader(griIn));
        		String strGriLine;
	 
	            while(true)
	            {
	            	strGriLine = griBr.readLine();
	            	
	            	if(strGriLine == null)
	            		break;
	 
	            	// If short-format GRI is in lookup table, convert to corresponding long-format GRI //
	            	if(strGriLine.substring(0, strGriLine.indexOf("_=_")).equals(shortGRI))
	            	{
	            		gri = strGriLine;
	            		break;
	            	}
	            }
	            		
	            griBr.close();
	            griIn.close();
	            griStream.close();
	            
	            if(shortGRI.equals(gri))	// MP-GRI not in lookup table, make new long-form GRI
	            {
	            	gri += "_=_" + gri + ":_0_:";
	            }	            	            
	        }
        	catch(Exception e)
        	{
        		System.err.println("Problem looking up MP-GRI in \'mp_gri_lookup.txt\'");
        		e.printStackTrace();
        		System.exit(-1);
        	}
			
            return gri;
        }
	}

	
	/*********************************************************************************************************************************************************
	* Inspects the MP-GRI lookup tracker (file) to determine what the next default MP-GRI should be, and updates the file accordingly. 
	* IF the GRI exists, THEN the corresponding long-format MP-GRI will be returned.
	*********************************************************************************************************************************************************/
	protected Integer getMPGri(Integer thisMPGri)
	{		
		try
    	{
    		int thisGri;
    		
    		FileInputStream fstream = new FileInputStream(mpTrackerGRI);
    		DataInputStream in = new DataInputStream(fstream);
    		BufferedReader br = new BufferedReader(new InputStreamReader(in));
    		String strLine = br.readLine();
    		thisMPGri = new Integer(strLine.trim());
    		    		    		
    		thisGri = thisMPGri.intValue() + 1;   		// increment the GRI counter for the next request
    		    		
    		FileWriter fstream_out = new FileWriter(mpTrackerGRI);
        	BufferedWriter out = new BufferedWriter(fstream_out);
        	Integer nextMcGri = new Integer(thisGri);
        	out.write(nextMcGri.toString());
        	           	
    		in.close();
    		fstream.close();
        	out.close();
        	fstream_out.close();        	
    	}
    	catch(Exception e)	//mp_gri_tracker.txt doesn't exist
    	{
    		try
    		{
    			FileWriter fstream_out = new FileWriter(mpTrackerGRI);
            	BufferedWriter out = new BufferedWriter(fstream_out);
            	out.write("1");
            	out.close();
            	
            	thisMPGri = new Integer(0);
    		}
    		catch(Exception e2){e2.printStackTrace();}
    	}
		
		return thisMPGri + 1;
	}
	
}
