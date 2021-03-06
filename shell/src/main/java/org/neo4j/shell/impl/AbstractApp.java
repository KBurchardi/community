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
package org.neo4j.shell.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.shell.App;
import org.neo4j.shell.AppShellServer;
import org.neo4j.shell.OptionDefinition;
import org.neo4j.shell.Output;
import org.neo4j.shell.Session;
import org.neo4j.shell.ShellException;
import org.neo4j.shell.util.json.JSONArray;
import org.neo4j.shell.util.json.JSONException;
import org.neo4j.shell.util.json.JSONObject;

/**
 * Common abstract implementation of an {@link App}.
 */
public abstract class AbstractApp implements App
{
	private Map<String, OptionDefinition> optionDefinitions =
		new HashMap<String, OptionDefinition>();
	private AppShellServer server;
	
	public String getName()
	{
		return this.getClass().getSimpleName().toLowerCase();
	}

	public OptionDefinition getOptionDefinition( String option )
	{
		return this.optionDefinitions.get( option );
	}

	protected void addOptionDefinition( String option,
	        OptionDefinition definition )
	{
		this.optionDefinitions.put( option, definition );
	}
	
	public String[] getAvailableOptions()
	{
		String[] result = this.optionDefinitions.keySet().toArray(
			new String[ this.optionDefinitions.size() ] );
		Arrays.sort( result );
		return result;
	}

	public void setServer( AppShellServer server )
	{
		this.server = server;
	}
	
	public AppShellServer getServer()
	{
		return this.server;
	}
	
	public String getDescription()
	{
		return null;
	}
	
	public String getDescription( String option )
	{
		OptionDefinition definition = this.optionDefinitions.get( option );
		return definition == null ? null : definition.getDescription();
	}
	
	public void shutdown()
	{
	    // Default behaviour is to do nothing
	}
	
	public List<String> completionCandidates( String partOfLine, Session session )
	{
	    return Collections.emptyList();
	}

	protected String multiply( String string, int count ) throws RemoteException
	{
	    StringBuilder builder = new StringBuilder();
		for ( int i = 0; i < count; i++ )
		{
			builder.append( string );
		}
		return builder.toString();
	}

	protected static Serializable safeGet( Session session, String key )
	{
	    return session.get( key );
	}
	
	protected static void safeSet( Session session, String key,
		Serializable value )
	{
	    session.set( key, value );
	}

	protected static Serializable safeRemove( Session session, String key )
	{
	    return session.remove( key );
	}
	
	protected static Map<String, Object> parseFilter( String filterString,
	    Output out ) throws RemoteException, ShellException
	{
	    if ( filterString == null )
	    {
	        return new HashMap<String, Object>();
	    }
	    
	    Map<String, Object> map = null;
	    String signsOfJSON = ":";
	    int numberOfSigns = 0;
	    for ( int i = 0; i < signsOfJSON.length(); i++ )
	    {
	        if ( filterString.contains(
	            String.valueOf( signsOfJSON.charAt( i ) ) ) )
	        {
	            numberOfSigns++;
	        }
	    }
	    
	    if ( numberOfSigns >= 1 )
	    {
            String jsonString = filterString;
            if ( !jsonString.startsWith( "{" ) )
            {
                jsonString = "{" + jsonString;
            }
            if ( !jsonString.endsWith( "}" ) )
            {
                jsonString += "}";
            }
            try
            {
                map = parseJSONMap( jsonString );
            }
            catch ( JSONException e )
            {
                out.println( "parser: \"" + filterString + "\" hasn't got " +
                	"correct JSON formatting: " + e.getMessage() );
                throw ShellException.wrapCause( e );
            }
	    }
	    else
	    {
	        map = new HashMap<String, Object>();
	        map.put( filterString, null );
	    }
	    return map;
	}

    protected static Map<String, Object> parseJSONMap( String jsonString )
        throws JSONException
	{
        JSONObject object = new JSONObject( jsonString );
        Map<String, Object> result = new HashMap<String, Object>();
        for ( String name : JSONObject.getNames( object ) )
        {
            Object value = object.get( name );
            if ( value != null && value instanceof String &&
                ( ( String ) value ).length() == 0 )
            {
                value = null;
            }
            result.put( name, value );
        }
        return result;
	}
    
    protected static Object[] parseArray( String string )
    {
        try
        {
            JSONArray array = new JSONArray( string );
            Object[] result = new Object[ array.length() ];
            for ( int i = 0; i < result.length; i++ )
            {
                result[ i ] = array.get( i );
            }
            return result;
        }
        catch ( JSONException e )
        {
            throw new IllegalArgumentException( e.getMessage(), e );
        }
    }
    
    public static void safeClose( Closeable object )
    {
        if ( object != null )
        {
            try
            {
                object.close();
            }
            catch ( IOException e )
            {
                // I'd guess it's OK
            }
        }
    }
}
