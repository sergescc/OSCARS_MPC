package config;

import net.es.oscars.client.OSCARSClientConfig;
import net.es.oscars.client.OSCARSClientException;

/***********************************************************************************************
* This class provides a one-stop shop for setting up filepaths needed to connect to OSCARS.
* Static variables of Configuration can (and should) be accessed by any classes in 
* /MultipathClient/src/ with a main() method. This class is also needed by MultipathOSCARSClient
* itself.
* 
* The user may have to modify this file depending on their OSCARS configurations and keystore
* setups.
* 
* @author Jeremy
***********************************************************************************************/
public class Configuration 
{
	public final static String oscarsURL = "http://localhost:9001/OSCARS";
	public final static String topoBridgeURL = "http://localhost:9019/topoBridge";
			
	public final static String keystoreClient = "certs/client.jks";
	public final static String keystoreClientUser = "mykey";
	public final static String keystoreClientPasswd	= "changeit";
	
	public final static String keystoreSSL = "certs/client.jks";
	public final static String keystoreSSLPasswd	= "changeit";
	
	// Default OSCARS topology to use in GUI src/destination displays //
	public final static String topologyDomain = "es.net";
	
	// There is really no reason to change these variables //
	public final static String queryOutputFile = "mp_query_out.txt";
	public final static String mpGriTrackerFile = "mp_gri_tracker.txt";
	public final static String mpGriLookupFile = "mp_gri_lookup.txt";
	
	/**
	 * Constructor - Only needs to be called in MultipathOSCARSClient.		
	 */
	public Configuration()
	{
		try 
		{
			// Setup keystores to handle security-related AAA //
			OSCARSClientConfig.setClientKeystore(keystoreClientUser, keystoreClient, keystoreClientPasswd);
			OSCARSClientConfig.setSSLKeyStore(keystoreSSL, keystoreSSLPasswd);
		} 
		catch (OSCARSClientException ce) 
		{
			System.err.println("OSCARSClientException thrown trying to initialize OSCARSClient");
			ce.printStackTrace();
		}
		catch (Exception e) 
		{
			System.err.println("Exception thrown trying to initialize OSCARSClient");
			e.printStackTrace();
		}
	}
}
