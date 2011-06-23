/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.ReadOnlyIndex;
import org.neo4j.graphdb.index.AutoIndexer;
import org.neo4j.kernel.Config;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.impl.util.FileUtils;
import org.neo4j.metatest.TestGraphDescription;
import org.neo4j.test.GraphDescription;
import org.neo4j.test.GraphDescription.Graph;
import org.neo4j.test.GraphHolder;
import org.neo4j.test.TargetDirectory;
import org.neo4j.test.TestData;

public class AutoIndexerExampleTests implements GraphHolder
{
    private static final TargetDirectory target = TargetDirectory.forTest( TestGraphDescription.class );

    private static EmbeddedGraphDatabase graphdb;
    public @Rule
    TestData<Map<String, Node>> data = TestData.producedThrough( GraphDescription.createGraphFor( this, true ) );

    private String getStoreDir( String testName ) throws IOException
    {
        File base = new File( "target", "example-auto-index" );
        FileUtils.deleteRecursively( base );
        return new File( base.getAbsolutePath(), testName ).getAbsolutePath();
    }

    @Test
    public void testConfig() throws Exception
    {
        // START SNIPPET: ConfigAutoIndexer

        /*
         * Creating the configuration, adding nodeProp1 and nodeProp2 as
         * auto indexed properties for Nodes and relProp1 and relProp2 as
         * auto indexed properties for Relationships. Only those will be
         * indexed. We also have to enable auto indexing for both these
         * primitives explicitly.
         */
        Map<String, String> config = new HashMap<String, String>();
        config.put( Config.NODE_KEYS_INDEXABLE, "nodeProp1, nodeProp2" );
        config.put( Config.RELATIONSHIP_KEYS_INDEXABLE, "relProp1, relProp2" );
        config.put( Config.NODE_AUTO_INDEXING, "true" );
        config.put( Config.RELATIONSHIP_AUTO_INDEXING, "true" );

        EmbeddedGraphDatabase graphDb = new EmbeddedGraphDatabase(
                getStoreDir( "testConfig" ), config );

        Transaction tx = graphDb.beginTx();
        Node node1 = null, node2 = null;
        Relationship rel = null;
        try
        {
            // Create the primitives
            node1 = graphDb.createNode();
            node2 = graphDb.createNode();
            rel = node1.createRelationshipTo( node2,
                    DynamicRelationshipType.withName( "DYNAMIC" ) );

            // Add indexable and non-indexable properties
            node1.setProperty( "nodeProp1", "nodeProp1Value" );
            node2.setProperty( "nodeProp2", "nodeProp2Value" );
            node1.setProperty( "nonIndexed", "nodeProp2NonIndexedValue" );
            rel.setProperty( "relProp1", "relProp1Value" );
            rel.setProperty( "relPropNonIndexed", "relPropValueNonIndexed" );

            // Make things persistent
            tx.success();
        }
        catch ( Exception e )
        {
            tx.failure();
        }
        finally
        {
            tx.finish();
        }
        // END SNIPPET: ConfigAutoIndexer

        // START SNIPPET: APIReadAutoIndex
        // Get the Node auto index
        ReadOnlyIndex<Node> autoNodeIndex = graphDb.index().getNodeAutoIndexer().getAutoIndex();
        // node1 and node2 both had auto indexed properties, get them
        assertEquals( node1,
                autoNodeIndex.get( "nodeProp1", "nodeProp1Value" ).getSingle() );
        assertEquals( node2,
                autoNodeIndex.get( "nodeProp2", "nodeProp2Value" ).getSingle() );
        // node2 also had a property that should be ignored.
        assertFalse( autoNodeIndex.get( "nonIndexed",
                "nodeProp2NonIndexedValue" ).hasNext() );

        // Get the relationship auto index
        ReadOnlyIndex<Relationship> autoRelIndex = graphDb.index().getRelationshipAutoIndexer().getAutoIndex();
        // One property was set for auto indexing
        assertEquals( rel,
                autoRelIndex.get( "relProp1", "relProp1Value" ).getSingle() );
        // The rest should be ignored
        assertFalse( autoRelIndex.get( "relPropNonIndexed",
                "relPropValueNonIndexed" ).hasNext() );
        // END SNIPPET: APIReadAutoIndex
        graphDb.shutdown();
    }

    @Test
    public void testAPI() throws Exception
    {
        // START SNIPPET: APIAutoIndexer

        // Start without any configuration
        EmbeddedGraphDatabase graphDb = new EmbeddedGraphDatabase(
                getStoreDir( "testAPI" ) );

        // Get the Node AutoIndexer, set nodeProp1 and nodeProp2 as auto
        // indexed.
        AutoIndexer<Node> nodeAutoIndexer = graphDb.index().getNodeAutoIndexer();
        nodeAutoIndexer.startAutoIndexingProperty( "nodeProp1" );
        nodeAutoIndexer.startAutoIndexingProperty( "nodeProp2" );

        // Get the Relationship AutoIndexer
        AutoIndexer<Relationship> relAutoIndexer = graphDb.index().getRelationshipAutoIndexer();
        relAutoIndexer.startAutoIndexingProperty( "relProp1" );

        // None of the AutoIndexers are enabled so far. Do that now
        nodeAutoIndexer.setEnabled( true );
        relAutoIndexer.setEnabled( true );

        // END SNIPPET: APIAutoIndexer

        Transaction tx = graphDb.beginTx();
        Node node1 = null, node2 = null;
        Relationship rel = null;
        try
        {
            // Create the primitives
            node1 = graphDb.createNode();
            node2 = graphDb.createNode();
            rel = node1.createRelationshipTo( node2,
                    DynamicRelationshipType.withName( "DYNAMIC" ) );

            // Add indexable and non-indexable properties
            node1.setProperty( "nodeProp1", "nodeProp1Value" );
            node2.setProperty( "nodeProp2", "nodeProp2Value" );
            node1.setProperty( "nonIndexed", "nodeProp2NonIndexedValue" );
            rel.setProperty( "relProp1", "relProp1Value1" );
            rel.setProperty( "relPropNonIndexed", "relProp1Value2" );

            // Make things persistent
            tx.success();
        }
        catch ( Exception e )
        {
            tx.failure();
        }
        finally
        {
            tx.finish();
        }

        // Get the Node auto index
        ReadOnlyIndex<Node> autoNodeIndex = nodeAutoIndexer.getAutoIndex();
        // node1 and node2 both had auto indexed properties, get them
        assertEquals( node1,
                autoNodeIndex.get( "nodeProp1", "nodeProp1Value" ).getSingle() );
        assertEquals( node2,
                autoNodeIndex.get( "nodeProp2", "nodeProp2Value" ).getSingle() );
        // node2 also had a property that should be ignored.
        assertFalse( autoNodeIndex.get( "nonIndexed",
                "nodeProp2NonIndexedValue" ).hasNext() );

        // Get the relationship auto index
        ReadOnlyIndex<Relationship> autoRelIndex = relAutoIndexer.getAutoIndex();
        // All properties ignored
        assertEquals( rel,
                autoRelIndex.get( "relProp1", "relProp1Value1" ).getSingle() );
        assertFalse( autoRelIndex.get( "relPropNonIndexed", "relProp1Value2" ).hasNext() );
        graphDb.shutdown();
    }

    @Test
    public void testMutations() throws Exception
    {
        // START SNIPPET: Mutations

        /*
         * Creating the configuration
         */
        Map<String, String> config = new HashMap<String, String>();
        config.put( Config.NODE_KEYS_INDEXABLE, "nodeProp1, nodeProp2" );
        config.put( Config.NODE_AUTO_INDEXING, "true" );

        EmbeddedGraphDatabase graphDb = new EmbeddedGraphDatabase(
                getStoreDir( "mutations" ), config );

        Transaction tx = graphDb.beginTx();
        Node node1 = null, node2 = null, node3 = null, node4 = null;
        try
        {
            // Create the primitives
            node1 = graphDb.createNode();
            node2 = graphDb.createNode();
            node3 = graphDb.createNode();
            node4 = graphDb.createNode();

            // Add indexable and non-indexable properties
            node1.setProperty( "nodeProp1", "nodeProp1Value" );
            node2.setProperty( "nodeProp2", "nodeProp2Value" );
            node3.setProperty( "nodeProp1", "nodeProp3Value" );
            node4.setProperty( "nodeProp2", "nodeProp4Value" );

            // Make things persistent
            tx.success();
        }
        catch ( Exception e )
        {
            tx.failure();
        }
        finally
        {
            tx.finish();
        }

        /*
         *  Here both nodes are indexed. To demonstrate removal, we stop
         *  autoindexing nodeProp1.
         */
        AutoIndexer<Node> nodeAutoIndexer = graphDb.index().getNodeAutoIndexer();
        nodeAutoIndexer.stopAutoIndexingProperty( "nodeProp1" );

        tx = graphDb.beginTx();
        try
        {
            /*
             * nodeProp1 is no longer auto indexed. It will be
             * removed regardless. Note that node3 will remain.
             */
            node1.setProperty( "nodeProp1", "nodeProp1Value2" );
            /*
             * node2 will be auto updated
             */
            node2.setProperty( "nodeProp2", "nodeProp2Value2" );
            /*
             * remove node4 property nodeProp2 from index.
             */
            node4.removeProperty( "nodeProp2" );
            // Make things persistent
            tx.success();
        }
        catch ( Exception e )
        {
            tx.failure();
        }
        finally
        {
            tx.finish();
        }

        // Verify
        ReadOnlyIndex<Node> nodeAutoIndex = nodeAutoIndexer.getAutoIndex();
        // node1 is completely gone
        assertFalse( nodeAutoIndex.get( "nodeProp1", "nodeProp1Value" ).hasNext() );
        assertFalse( nodeAutoIndex.get( "nodeProp1", "nodeProp1Value2" ).hasNext() );
        // node2 is updated
        assertFalse( nodeAutoIndex.get( "nodeProp2", "nodeProp2Value" ).hasNext() );
        assertEquals( node2,
                nodeAutoIndex.get( "nodeProp2", "nodeProp2Value2" ).getSingle() );
        /*
         * node3 is still there, despite its nodeProp1 property not being monitored
         * any more because it was not touched, in contrast with node1.
         */
        assertEquals( node3,
                nodeAutoIndex.get( "nodeProp1", "nodeProp3Value" ).getSingle() );
        // Finally, node4 is removed because the property was removed.
        assertFalse( nodeAutoIndex.get( "nodeProp2", "nodeProp4Value" ).hasNext() );
        // END SNIPPET: Mutations
    }

    @Test
    @Graph( autoIndexNodes = true,
            autoIndexRelationships = true,
            value = { "I know you"  })
    public void canCreateMoreInvolvedGraphWithPropertiesAndAutoIndex() throws Exception
    {
        GraphDatabaseService graphDatabase = data.get().values().iterator().next().getGraphDatabase();
        assertTrue( "node autoindex Nodes not enabled.", graphDatabase.index().getNodeAutoIndexer().isEnabled() );
        assertTrue( "node autoindex Rels not enabled.", graphDatabase.index().getRelationshipAutoIndexer().isEnabled() );
    }

    @BeforeClass
    public static void startDatabase()
    {
        graphdb = new EmbeddedGraphDatabase( target.graphDbDir( true ).getAbsolutePath() );
    }

    @AfterClass
    public static void stopDatabase()
    {
        if ( graphdb != null ) graphdb.shutdown();
        graphdb = null;
    }

    @Override
    public GraphDatabaseService graphdb()
    {
        return graphdb;
    }

}