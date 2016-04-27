package com.sial.SST_Loader;
import java.util.Set;
import com.j256.simplejmx.client.JmxClient;
import javax.management.ObjectName;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.*;
import com.datastax.driver.core.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;

///////////////////////////////////////////////////////////////////////////////////////////////////////////
///
/// to compile use >gradle build in project root directory
///
///////////////////////////////////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////////////////////////////////////
//  EXAMPLE
// ----------------------- SOURCE MACHINE ---------------------------
// /data/dsc-cassandra-2.1.2/bin/nodetool -h stlpochdp06 snapshot enotebook -t 2015_07_07
// python ENOTEBOOK_get_database_snapshot.py enotebook enotebook /home/vleontie/cassandra_tables/snapshots 2015_07_07
// transfer folder to destination macine, then, on destination machine:
// ----------------------- DESTINATION MACHINE ------------------------
// cqlsh>drop keyspace enotebook
// java -jar /home/vleontie/code/java/sst_loader/build/libs/sst_loader.jar /home/vleontie/data/cassandra_tables/snapshots/2015_07_07/enotebook
///////////////////////////////////////////////////////////////////////////////////////////////////////////////


public class App
{
    public static void main( String[] args )
    {
        if(args.length == 0)
        {
            System.out.println("USAGE: java -jar ./build/libs/sst_loader.jar <path to root sanpshot directory>");
            System.out.println("EXAMLE: java -jar ./build/libs/sst_loader.jar /home/vleontie/data/cassandra_tables/snapshots/snapshot_2015_05_15/enotebook");
            System.exit(0);
        }
        String source_dir = args[0];
        String cmd_file_location=source_dir+"/commands.cql";
    	Pattern create_keyspace = Pattern.compile(".*CREATE KEYSPACE.*");
    	Pattern create_table = Pattern.compile(".*CREATE TABLE.*");
    	String ws = "\\s+";
    	BufferedReader br = null;
        String ll = null;
        Cluster  cluster = Cluster.builder().addContactPoint("localhost").build();
    	Session session1 = cluster.connect("system");
    	ArrayList<String> table_names = new ArrayList<String>();
        try
        {
        	StringBuffer all_statements = new StringBuffer();
            br = new BufferedReader(
            //new FileReader("/home/vleontiev/data/cassandra_bkp/enotebook_bkp_2015_05_04/commands.cql"));
            new FileReader(cmd_file_location));
            while ((ll = br.readLine()) != null)
            {
            	all_statements.append(ll);
            }
            String[] statements = all_statements.toString().split(";");
            for(String st : statements)
            {
            	Matcher create_keyspace_matcher = create_keyspace.matcher(st);
            	Matcher create_table_matcher = create_table.matcher(st);
            	if(create_keyspace_matcher.matches())
      			{
                	System.out.println(" ++++++++++++++ CREATING KEYSPACE +++++++++++++++++");
                	session1.execute(st);
            	}
            	else if(create_table_matcher.matches())
            	{
            		String table_name=st.split("\n")[0].split(ws)[2];
            		System.out.println("CREATING TABLE "+ table_name+" ...");
            		table_names.add(table_name.split("\\.")[1]);
                	session1.execute(st);
            	}
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        cluster.close();
        try {
        	JmxClient client = new JmxClient("localhost", 7199);
        	try {
        		for(String table_name : table_names)
        		{
        			System.out.println("LOADING TABLE "+ table_name+" ...");
        			client.invokeOperation(new ObjectName("org.apache.cassandra.db:type=StorageService"),  "bulkLoad", String.format("%s/%s",  source_dir, table_name));
        		}
        	}
        	catch(java.lang.Exception e)  {
        		e.printStackTrace();
        	}
        }
        catch(javax.management.JMException jmxe) {
        	jmxe.printStackTrace();
        }
    }
}
