package gui;

/*****************************************************************************************************************************************
* This class is kind of awesome. Modifies the typical alphabetical sorting approach of a Collection of Strings for the GRIs.
* Using the traditional String comparator, The GRI 'es.net-100' would appear before 'es.net-2' in a sorted list.
* The list would in fact look something like this: 'es.net-1', 'es.net-10', es.net-'100', 'es.net-101', ... , 'es.net-11', ...  
* 
* This class will modify the comparison to first check the alphabetical ordering, and then modify the comparison result based upon the
* relative lengths of the String arguments being compared.
* The new list will look something like this: 'es.net-1', 'es.net-2', 'es.net-3', ... , 'es.net-10', 'es.net-11', ... 'es.net-100', ...
*  
* @author Jeremy
/*****************************************************************************************************************************************/
public class GriComparator implements java.util.Comparator<String> 
{
    public GriComparator()
    {
    	super();
    }
    
    /**
     * Perform the String comparison: based on length and alphabetical precedence
     */
	public int compare(String s1, String s2) 
	{
		int sizablyLess = 0;
		int alphabeticallyLess = s1.compareTo(s2);				// Alphabetical Comparison
		
		if(s1.length() != s2.length())
		{
			if(alphabeticallyLess < 0 && s2.contains(s1))					// Ex: 10 vs 100 
				sizablyLess = alphabeticallyLess;
			else if(alphabeticallyLess > 0 && s1.contains(s2))				// Ex: 100 vs 10
				sizablyLess = alphabeticallyLess;
			else if(alphabeticallyLess < 0 && s2.length() < s1.length())	// Ex: 100 vs 20
				sizablyLess = alphabeticallyLess * (-1);
			else if(alphabeticallyLess > 0 && s1.length() < s2.length())	// Ex: 20 vs 100
				sizablyLess = alphabeticallyLess * (-1);
			else
				sizablyLess = alphabeticallyLess;
		}
		else	// Same length, the alphabetical sorting has taken care of everythign already
		{
			sizablyLess = alphabeticallyLess;
		}
		

		return sizablyLess; 
	}
}
