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
package org.neo4j.server.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.neo4j.server.rest.FunctionalTestHelper.CLIENT;

import java.io.IOException;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.kernel.impl.annotations.Documented;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.helpers.ServerHelper;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.test.TestData;

import com.sun.jersey.api.client.ClientResponse;

public class GetOnRootFunctionalTest
{

    private static NeoServerWithEmbeddedWebServer server;
    private static FunctionalTestHelper functionalTestHelper;

    @BeforeClass
    public static void setupServer() throws IOException
    {
        server = ServerHelper.createServer();
        functionalTestHelper = new FunctionalTestHelper( server );
    }

    @Before
    public void cleanTheDatabase()
    {
        ServerHelper.cleanTheDatabase( server );
    }

    @AfterClass
    public static void stopServer()
    {
        server.stop();
    }

    public @Rule
    TestData<DocsGenerator> gen = TestData.producedThrough( DocsGenerator.PRODUCER );

    /**
     * The service root is your starting point to discover the REST API.
     */
    @Documented
    @Test
    @TestData.Title( "Get service root" )
    public void assert200OkFromGet() throws Exception
    {
        gen.get()
                .expectedStatus( 200 )
                .get( functionalTestHelper.dataUri() );
    }

    @Test
    public void assertResponseHaveCorrectContentFromGet() throws Exception
    {
        ClientResponse response = CLIENT.resource( functionalTestHelper.dataUri() )
                .accept( MediaType.APPLICATION_JSON )
                .get( ClientResponse.class );
        String body = response.getEntity( String.class );
        Map<String, Object> map = JsonHelper.jsonToMap( body );
        assertEquals( functionalTestHelper.nodeUri(), map.get( "node" ) );
        assertNotNull( map.get( "reference_node" ) );
        assertNotNull( map.get( "node_index" ) );
        assertNotNull( map.get( "relationship_index" ) );
        assertNotNull( map.get( "extensions_info" ) );
        response.close();

        String referenceNodeUri = (String) map.get( "reference_node" );
        response = CLIENT.resource( referenceNodeUri )
                .accept( MediaType.APPLICATION_JSON )
                .get( ClientResponse.class );
        assertEquals( 200, response.getStatus() );
        response.close();
    }
}
