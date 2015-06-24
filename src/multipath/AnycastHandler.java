package multipath;

import java.util.ArrayList;

import net.es.oscars.api.soap.gen.v06.PathInfo;


public class AnycastHandler {

	MultipathOSCARSClient multipathClient;
	
	public AnycastHandler(MultipathOSCARSClient multipathClient)
	{
		this.multipathClient = multipathClient;
	}
	
	public String handleAnycastRequest(String description, String sourceURN, boolean isSrcTagged, String srcVLAN, String destURN, boolean isDstTagged, String destVLAN, Integer bandwidth, String pathSetupMode, long startTimestamp, long endTimestamp, Integer mpNumPaths)
	{
		String currentGRI = "";
		/**
		 * Determine number of anycast destinations in set
		 */
		int anycastDestCount = 1;
		for(int i = 0; i < destURN.length(); i++)
		{
			if(destURN.charAt(i) == ',')
				anycastDestCount++;
		}
		
		/**
		 * Variables for determining how many requests are successful / number of successful paths in each request
		 */
		boolean[] successfulRequests = new boolean[anycastDestCount];
		int numSuccessfulRequests = 0;
		boolean flexible = true;
		int[] numSuccessfulPathsPerRequest = new int[anycastDestCount];
		
		/**
		 * Only do this if there is more than one destination in the anycast set
		 */
		if(anycastDestCount > 1)
		{
			ArrayList<ArrayList<SubrequestTuple>> anycastRequests = new ArrayList<ArrayList<SubrequestTuple>>();
		
			/**
			 * Perform a MP reservation for each destination in the Anycast Destination Set, take metrics (hops), then cancel the reservation
			 */
			for(int currentDestPos = 0; currentDestPos < anycastDestCount; currentDestPos++)
			{
				currentGRI = multipathClient.createMPReservation(description, sourceURN, isSrcTagged, srcVLAN, destURN, isDstTagged, destVLAN, bandwidth, pathSetupMode, startTimestamp, endTimestamp, mpNumPaths);
				
				ArrayList<SubrequestTuple> queryResults = multipathClient.getLastMPQuery();
				
				/**
				 * Determine the number of successful paths in this request
				 */
				for(int v = 0; v < queryResults.size();v++)
				{
					if(!queryResults.get(v).getAllDetails().get(0).getStatus().equals("FAILED"))
					{
						numSuccessfulPathsPerRequest[currentDestPos]++;
					}
				}
				
				/**
				 * Only do this if the correct number of paths have been successfully routed, or if flexibility is allowed
				 */
				if(numSuccessfulPathsPerRequest[currentDestPos] == mpNumPaths || (numSuccessfulPathsPerRequest[currentDestPos] > 0 && flexible))
				{
					successfulRequests[currentDestPos] = true;
					numSuccessfulRequests++;
					ArrayList<PathInfo> tempList = new ArrayList<PathInfo>();
					for(int y = 0; y < numSuccessfulPathsPerRequest[currentDestPos]; y++)
					{
						tempList.add(queryResults.get(y).getAllDetails().get(0).getReservedConstraint().getPathInfo());
					}
					anycastRequests.add(currentDestPos, queryResults);
				}
				else
				{
					successfulRequests[currentDestPos] = false;
				}
				//System.out.println("The old destURN is " + destURN);
				destURN = removeDestination(currentGRI, destURN);
				//System.out.println("The new destURN is " + destURN);
				multipathClient.cancelMPReservation(currentGRI);
			}
		
			/**
			 * Compare performed MP requests, select "best" based on hop count (sum across all paths in that MP reservation) and number of successful paths
			 */
			int[] hopTotalsPerMPRequest = new int[anycastDestCount];
			int bestRequest = 0;

			if(numSuccessfulRequests > 0)
			{
				/**
				 * Get Hop counts
				 */
				for(int z= 0;z < anycastDestCount;z++)
				{
						if(successfulRequests[z])
						{
							for(int u = 0; u < numSuccessfulPathsPerRequest[z]; u++)
							{
								hopTotalsPerMPRequest[z] += anycastRequests.get(z).get(u).getAllDetails().get(0).getReservedConstraint().getPathInfo().getPath().getHop().size();
							}
							//System.out.println("Hops: " + hopTotalsPerMPRequest[z] + " in " + numSuccessfulPathsPerRequest[z] + " Paths to " + parseDestination(multipathClient.convertPathToString(anycastRequests.get(z).get(0).getGroupGRI())));
						}
				}
				/**
				 * Find best request --
				 * Criteria: 
				 * 	(1) Highest Number of Paths
				 * 	(2) Lowest Number of Hops
				 */
				for(int h = 1; h < anycastDestCount; h++)
				{
					if(numSuccessfulPathsPerRequest[h] > numSuccessfulPathsPerRequest[bestRequest])
					{
						bestRequest = h;
					}
					else if(hopTotalsPerMPRequest[h] < hopTotalsPerMPRequest[bestRequest] && numSuccessfulPathsPerRequest[h] == numSuccessfulPathsPerRequest[bestRequest])
					{
						bestRequest = h;
					}
				}
			
				/**
				 * Create a final MP reservation for the "best" destination
				 */

				String bestRequestPath = multipathClient.convertPathToString(anycastRequests.get(bestRequest).get(0).getGroupGRI());
				destURN = parseDestination(bestRequestPath);
				System.out.println("The best Destination: " + destURN);
				currentGRI = multipathClient.createMPReservation(description, sourceURN, isSrcTagged, srcVLAN, destURN, isDstTagged, destVLAN, bandwidth, pathSetupMode, startTimestamp, endTimestamp, mpNumPaths);
			}
			return currentGRI;
		}
		else
		{
			return multipathClient.createMPReservation(description, sourceURN, isSrcTagged, srcVLAN, destURN, isDstTagged, destVLAN, bandwidth, pathSetupMode, startTimestamp, endTimestamp, mpNumPaths);
		}
	}
	
		/**
		 * Returns the destination URN from a path
		 * @param path
		 * @return
		 */
	private String parseDestination(String path)
	{
		int lastSemi = path.length()-1;
		//System.out.println("The path: " + path);
		int secondLastSemi = path.lastIndexOf(";", lastSemi - 1);
		return path.substring(secondLastSemi + 1, path.length()-1);
	}
	
	/**
	 * Removes the Destination from the Anycast Destination Set
	 *  Unicast --> urn:ogf:network:domain=es.net:node=DENV:port=port-4:link=link1
		Anycast --> urn:ogf:network:domain=es.net:node=anycast(DENV-4,SUNN-5,PNWG-4,ELPA-3):port=anycast:link=link1
	 */
	private String removeDestination(String currentGRI, String currentDestURN)
	{
		String thisPath = multipathClient.convertPathToString(currentGRI);
		String routedDestUrn = parseDestination(thisPath);

		String currentNode = routedDestUrn.substring(routedDestUrn.indexOf("node="), routedDestUrn.indexOf(":port="));
		currentNode = currentNode.substring(5);
		
		String currentPort = routedDestUrn.substring(routedDestUrn.indexOf("port="), routedDestUrn.indexOf(":link="));
		currentPort = currentPort.substring(10);
		
		String searchDestUrn = currentNode + '-' + currentPort;

		currentDestURN = currentDestURN.replace(searchDestUrn + ',', "");
		currentDestURN = currentDestURN.replace(searchDestUrn, "");
		
		return currentDestURN;
	}
}

