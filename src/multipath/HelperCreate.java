package multipath;

import net.es.oscars.api.soap.gen.v06.Layer2Info;
import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.ResCreateContent;
import net.es.oscars.api.soap.gen.v06.UserRequestConstraintType;
import net.es.oscars.api.soap.gen.v06.VlanTag;

/***********************************************************************************************************************
* This class provides helper methods needed for MultipathOSCARSClient method createMPReservation() to work appropriately.
* This class exists solely to provide a higher layer of modularity and keep MultipathOSCARSClient.java clean.
* 
* @author Jeremy
***********************************************************************************************************************/
public class HelperCreate 
{
	/*********************************************************************************************************************************************************
	* Constructs a ResCreateContent object by aggregating the given input parameters. 
	* - NOTE: Current implementation supports only Layer2 parameters.
	* 
	* @param description
	* @param srcUrn
	* @param isSrcTagged
	* @param srcTag
	* @param destUrn, Unicast destination format
	* @param isDestTagged
	* @param destTag
	* @param bandwidth
	* @param pathSetupMode
	* @param startTimestamp
	* @param endTimestamp
	* @return
	*********************************************************************************************************************************************************/
	protected ResCreateContent constructResCreateContent(String description, String srcUrn, boolean isSrcTagged, String srcTag, String destUrn, boolean isDestTagged, String destTag, int bandwidth, String pathSetupMode, long startTimestamp, long endTimestamp)
	{
    	/**
	    * ResCreateContent					-->	GRI, Description
	    * - UserRequestConstraintType		-->	Start Time, End Time, Bandwidth
	    * -- PathInfo						-->	Path Setup Mode, Path Type
	    * --- CtrlPlanePathContent 			-->	List<Hops>
	    * ---- CtrlPlaneHopContent			-->	Domain, Node, Port, Link
	    * --- Layer2Info					-->	Src Endpoint, Dst Endpoint
	    * ---- VlanTag						-->	VLAN value, IsTagged?
	    **/
        ResCreateContent createRequest = new ResCreateContent();
        UserRequestConstraintType userConstraint = new UserRequestConstraintType();
        PathInfo pathInfo = new PathInfo();
        Layer2Info layer2Info = new Layer2Info();
        VlanTag srcVtag = new VlanTag();
        VlanTag destVtag = new VlanTag();
        
        // Set src VLAN Parameters
        if (isSrcTagged) 
        {
            srcVtag.setTagged(true);
            srcVtag.setValue(srcTag);
        } 
        else 
        {
            srcVtag.setTagged(false);
            srcVtag.setValue("any");		// May update in future version
        }
        
        
        // Set dst VLAN Parameters
        if (isDestTagged) 
        {
            destVtag.setTagged(true);
            destVtag.setValue(destTag);
        } 
        else 
        {
            destVtag.setTagged(false);
            destVtag.setValue("any");		// May update in future version
        }
        
       
        // Complete Layer2Info population
        layer2Info.setSrcVtag(srcVtag);
        layer2Info.setDestVtag(destVtag);
        layer2Info.setSrcEndpoint(srcUrn);
        layer2Info.setDestEndpoint(destUrn); 
        
        // Complete PathInfo population
        pathInfo.setLayer2Info(layer2Info);       
        pathInfo.setPathSetupMode(pathSetupMode);
      
        // Complete UserRequestConstraint population
        userConstraint.setPathInfo(pathInfo);
        userConstraint.setStartTime(startTimestamp);
        userConstraint.setEndTime(endTimestamp);
        userConstraint.setBandwidth(bandwidth);
        
        // Complete ResCreateContent population
        createRequest.setUserRequestConstraint(userConstraint);
        createRequest.setDescription(description);
        
        return createRequest;
	}
}
