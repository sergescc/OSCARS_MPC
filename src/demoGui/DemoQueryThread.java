package demoGui;

import java.awt.Color;
import java.util.ArrayList;

import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* This class performs a GRI query, which is the most time-consuming task for the GUI, particularly once MP-groups become large and consist 
* of several smaller groups. The console will display a message indicating that the query is occurring, and once it has finished, the results
* of the query will be printed to the console.
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class DemoQueryThread extends SwingWorker<Void, Integer>
{
	DemoUI callingMPGui;			// The actual GUI object
	ArrayList<String> allQueryOutput;	// Results of the query, formatted as specified in GuiController's query-related methods
	String griToQuery;					// GRI to submit to OSCARS for querying. May be be a Multipath or Unicast GRI
	
	/**
	* Constructor
	* 
	* @param gri, GRI to submit to OSCARS for querying. May be be a Multipath or Unicast GRI
	* @param theGui, The actual GUI object
	**/
	public DemoQueryThread(String gri, DemoUI theGui)
	{
		callingMPGui = theGui;
		griToQuery = gri;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	**/
	protected Void doInBackground() 
	{
		DemoGuiController multipathUIController = callingMPGui.mpUIController;		// The GUI's behavior controller
						
		// This might be triggered by a RefreshThread object //
		if(griToQuery.equals(""))
			return null;
				
		// Perform the actual query and get output results back //
		allQueryOutput = multipathUIController.queryReservations(griToQuery);
			    
	    printResultsToConsole();	// Set colors on console to display output readably
		
	    return null; 
	}
	
	protected ArrayList<String> getAllQueryOutput(){
		return allQueryOutput;
	}

	/**
	* Format the output console and display the query results.
	**/
	private void printResultsToConsole()
	{
		JTextPane console = callingMPGui.outputConsole;			// GUI console for displaying messages/query output
		StyledDocument doc = console.getStyledDocument();		
		Style style = console.addStyle("I'm a Style", null);
		
		console.setText("");
		
		try 
        {
			// The GuiController object returns Lists of Strings of length n | n % 3 = 0 //
			// Every 1st index is either blank or an MP-GRI //
			// Every 2nd index is the Unicast subrequest GRI query output for the MP-GRI in every first index //
			// Every 3rd index is the list of OSCARSFaultMessages (Errors) attached to the GRI in every second index // 
			for(int q = 0; q < allQueryOutput.size(); q += 3)
			{	    	        
	        	StyleConstants.setForeground(style, Color.BLUE);	// Blue Font
	        	doc.insertString(doc.getLength(), allQueryOutput.get(q), style); 	// Multipath GRIs
	        		        	
	        	StyleConstants.setForeground(style, Color.BLACK);	// Black Font
	        	doc.insertString(doc.getLength(), allQueryOutput.get(q+1), style); 	// Unicast output

	        	StyleConstants.setForeground(style, Color.RED);		// Red Font
	        	doc.insertString(doc.getLength(), allQueryOutput.get(q+2), style);	// Errors
	        	        
	        }			
				
			console.setCaretPosition(0);						//Scroll back to top of output console
			StyleConstants.setForeground(style, Color.BLACK);
			doc.insertString(doc.getLength(), "", style);		// Should reset font color to Black for output from other methods
        }
        catch (BadLocationException e){}
	}
	
}
