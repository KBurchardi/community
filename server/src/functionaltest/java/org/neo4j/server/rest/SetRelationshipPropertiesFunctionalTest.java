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
import static org.neo4j.server.rest.FunctionalTestHelper.CLIENT;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
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
import org.neo4j.server.rest.domain.GraphDbHelper;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.server.rest.domain.JsonParseException;
import org.neo4j.test.TestData;

import com.sun.jersey.api.client.ClientResponse;

public class SetRelationshipPropertiesFunctionalTest
{

    private URI propertiesUri;
    private URI badUri;

    private static NeoServerWithEmbeddedWebServer server;
    private static FunctionalTestHelper functionalTestHelper;

    @BeforeClass
    public static void setupServer() throws IOException
    {
        server = ServerHelper.createServer();
        functionalTestHelper = new FunctionalTestHelper( server );
    }

    @Before
    public void setupTheDatabase() throws Exception
    {
        ServerHelper.cleanTheDatabase( server );
        long relationshipId = new GraphDbHelper( server.getDatabase() ).createRelationship( "KNOWS" );
        propertiesUri = new URI( functionalTestHelper.relationshipPropertiesUri( relationshipId ) );
        badUri = new URI( functionalTestHelper.relationshipPropertiesUri( relationshipId + 1 * 99999 ) );
    }

    @AfterClass
    public static void stopServer()
    {
        server.stop();
    }

    public @Rule
    TestData<DocsGenerator> gen = TestData.producedThrough( DocsGenerator.PRODUCER );

    /**
     * Update relationship properties.
     */
    @Documented
    @Test
    public void shouldReturn204WhenPropertiesAreUpdated() throws JsonParseException
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put( "jim", "tobias" );
        gen.get()
                .payload( JsonHelper.createJsonFrom( map ) )
                .expectedStatus( 204 )
                .put( propertiesUri.toString() );
        ClientResponse response = updatePropertiesOnServer( map );
        assertEquals( 204, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn400WhenSendinIncompatibleJsonProperties() throws JsonParseException
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put( "jim", new HashMap<String, Object>() );
        ClientResponse response = updatePropertiesOnServer( map );
        assertEquals( 400, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn400WhenSendingCorruptJsonProperties()
    {
        ClientResponse response = CLIENT.resource( propertiesUri )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( "this:::Is::notJSON}" )
                .put( ClientResponse.class );
        assertEquals( 400, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn404WhenPropertiesSentToANodeWhichDoesNotExist() throws JsonParseException
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put( "jim", "tobias" );

        ClientResponse response = CLIENT.resource( badUri )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( JsonHelper.createJsonFrom( map ) )
                .put( ClientResponse.class );
        assertEquals( 404, response.getStatus() );
        response.close();
    }

    private ClientResponse updatePropertiesOnServer( final Map<String, Object> map ) throws JsonParseException
    {
        return CLIENT.resource( propertiesUri )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( JsonHelper.createJsonFrom( map ) )
                .put( ClientResponse.class );
    }

    private URI getPropertyUri( final String key ) throws Exception
    {
        return new URI( propertiesUri.toString() + "/" + key );
    }

    @Test
    public void shouldReturn204WhenPropertyIsSet() throws Exception
    {
        ClientResponse response = setPropertyOnServer( "foo", "bar" );
        assertEquals( 204, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn400WhenSendinIncompatibleJsonProperty() throws Exception
    {
        ClientResponse response = setPropertyOnServer( "jim", new HashMap<String, Object>() );
        assertEquals( 400, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn400WhenSendingCorruptJsonProperty() throws Exception
    {
        ClientResponse response = CLIENT.resource( getPropertyUri( "foo" ) )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( "this:::Is::notJSON}" )
                .put( ClientResponse.class );
        assertEquals( 400, response.getStatus() );
        response.close();
    }

    @Test
    public void shouldReturn404WhenPropertySentToANodeWhichDoesNotExist() throws Exception
    {
        ClientResponse response = CLIENT.resource( badUri.toString() + "/foo" )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( JsonHelper.createJsonFrom( "bar" ) )
                .put( ClientResponse.class );
        assertEquals( 404, response.getStatus() );
        response.close();
    }

    private ClientResponse setPropertyOnServer( final String key, final Object value ) throws Exception
    {
        return CLIENT.resource( getPropertyUri( key ) )
                .type( MediaType.APPLICATION_JSON )
                .accept( MediaType.APPLICATION_JSON )
                .entity( JsonHelper.createJsonFrom( value ) )
                .put( ClientResponse.class );
    }
}
