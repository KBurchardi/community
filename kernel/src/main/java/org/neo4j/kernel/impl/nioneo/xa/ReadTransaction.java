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
package org.neo4j.kernel.impl.nioneo.xa;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.transaction.xa.XAResource;

import org.neo4j.helpers.Pair;
import org.neo4j.kernel.impl.core.PropertyIndex;
import org.neo4j.kernel.impl.nioneo.store.InvalidRecordException;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.nioneo.store.NodeStore;
import org.neo4j.kernel.impl.nioneo.store.PropertyData;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexData;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyIndexStore;
import org.neo4j.kernel.impl.nioneo.store.PropertyRecord;
import org.neo4j.kernel.impl.nioneo.store.PropertyStore;
import org.neo4j.kernel.impl.nioneo.store.Record;
import org.neo4j.kernel.impl.nioneo.store.RelationshipRecord;
import org.neo4j.kernel.impl.nioneo.store.RelationshipStore;
import org.neo4j.kernel.impl.nioneo.store.RelationshipTypeData;
import org.neo4j.kernel.impl.persistence.NeoStoreTransaction;
import org.neo4j.kernel.impl.transaction.xaframework.XaConnection;
import org.neo4j.kernel.impl.util.ArrayMap;
import org.neo4j.kernel.impl.util.RelIdArray;
import org.neo4j.kernel.impl.util.RelIdArray.DirectionWrapper;

class ReadTransaction implements NeoStoreTransaction
{
    private final NeoStore neoStore;

    public ReadTransaction( NeoStore neoStore )
    {
        this.neoStore = neoStore;
    }

    private NodeStore getNodeStore()
    {
        return neoStore.getNodeStore();
    }

    private int getRelGrabSize()
    {
        return neoStore.getRelationshipGrabSize();
    }

    private RelationshipStore getRelationshipStore()
    {
        return neoStore.getRelationshipStore();
    }

    private PropertyStore getPropertyStore()
    {
        return neoStore.getPropertyStore();
    }

    public boolean nodeLoadLight( long nodeId )
    {
        return getNodeStore().loadLightNode( nodeId );
    }

    public RelationshipRecord relLoadLight( long id )
    {
        return getRelationshipStore().getLightRel( id );
    }

    public long getRelationshipChainPosition( long nodeId )
    {
        return getNodeStore().getRecord( nodeId ).getNextRel();
    }

    public Pair<Map<DirectionWrapper, Iterable<RelationshipRecord>>, Long> getMoreRelationships(
            long nodeId, long position )
    {
        return getMoreRelationships( nodeId, position, getRelGrabSize(), getRelationshipStore() );
    }
    
    static Pair<Map<DirectionWrapper, Iterable<RelationshipRecord>>, Long> getMoreRelationships(
            long nodeId, long position, int grabSize, RelationshipStore relStore )
    {
        // initialCapacity=grabSize saves the lists the trouble of resizing
        List<RelationshipRecord> out = new ArrayList<RelationshipRecord>();
        List<RelationshipRecord> in = new ArrayList<RelationshipRecord>();
        List<RelationshipRecord> loop = null;
        Map<DirectionWrapper, Iterable<RelationshipRecord>> result =
            new EnumMap<DirectionWrapper, Iterable<RelationshipRecord>>( DirectionWrapper.class );
        result.put( DirectionWrapper.OUTGOING, out );
        result.put( DirectionWrapper.INCOMING, in );
        for ( int i = 0; i < grabSize && 
            position != Record.NO_NEXT_RELATIONSHIP.intValue(); i++ )
        {
            RelationshipRecord relRecord = relStore.getChainRecord( position );
            if ( relRecord == null )
            {
                // return what we got so far
                return Pair.of( result, position );
            }
            long firstNode = relRecord.getFirstNode();
            long secondNode = relRecord.getSecondNode();
            if ( relRecord.inUse() )
            {
                if ( firstNode == secondNode )
                {
                    if ( loop == null )
                    {
                        // This is done lazily because loops are probably quite
                        // rarely encountered
                        loop = new ArrayList<RelationshipRecord>();
                        result.put( DirectionWrapper.BOTH, loop );
                    }
                    loop.add( relRecord );
                }
                else if ( firstNode == nodeId )
                {
                    out.add( relRecord );
                }
                else if ( secondNode == nodeId )
                {
                    in.add( relRecord );
                }
            }
            else
            {
                i--;
            }

            if ( firstNode == nodeId )
            {
                position = relRecord.getFirstNextRel();
            }
            else if ( secondNode == nodeId )
            {
                position = relRecord.getSecondNextRel();
            }
            else
            {
                throw new InvalidRecordException( "Node[" + nodeId + 
                    "] is neither firstNode[" + firstNode + 
                    "] nor secondNode[" + secondNode + "] for Relationship[" + relRecord.getId() + "]" );
            }
        }
        return Pair.of( result, position );
    }
    
    static ArrayMap<Integer, PropertyData> loadProperties( PropertyStore propertyStore, long nextProp )
    {
        if ( nextProp == Record.NO_NEXT_PROPERTY.intValue() )
        {
            return null;
        }
        ArrayMap<Integer,PropertyData> propertyMap = 
            new ArrayMap<Integer,PropertyData>( 9, false, true );
        while ( nextProp != Record.NO_NEXT_PROPERTY.intValue() )
        {
            PropertyRecord propRecord = propertyStore.getLightRecord( nextProp );
            propertyMap.put( propRecord.getKeyIndexId(), propRecord.newPropertyData() );
            nextProp = propRecord.getNextProp();
        }
        return propertyMap;
    }
    
    public ArrayMap<Integer,PropertyData> relLoadProperties( long relId, boolean light )
    {
        RelationshipRecord relRecord = getRelationshipStore().getRecord( relId );
        if ( !relRecord.inUse() )
        {
            throw new InvalidRecordException( "Relationship[" + relId + 
                "] not in use" );
        }
        return loadProperties( getPropertyStore(), relRecord.getNextProp() );
    }

    public ArrayMap<Integer,PropertyData> nodeLoadProperties( long nodeId, boolean light )
    {
        return loadProperties( getPropertyStore(), getNodeStore().getRecord( nodeId ).getNextProp() );
    }

    // Duplicated code
    public Object propertyGetValueOrNull( PropertyRecord propertyRecord )
    {
        return propertyRecord.getType().getValue( propertyRecord, null );
    }

    public Object loadPropertyValue( long id )
    {
        PropertyRecord propertyRecord = getPropertyStore().getRecord( id );
        if ( propertyRecord.isLight() )
        {
            getPropertyStore().makeHeavy( propertyRecord );
        }
        return propertyRecord.getType().getValue( propertyRecord, getPropertyStore() );
    }

    public String loadIndex( int id )
    {
        PropertyIndexStore indexStore = getPropertyStore().getIndexStore();
        PropertyIndexRecord index = indexStore.getRecord( id );
        if ( index.isLight() )
        {
            indexStore.makeHeavy( index );
        }
        return indexStore.getStringFor( index );
    }

    public PropertyIndexData[] loadPropertyIndexes( int count )
    {
        PropertyIndexStore indexStore = getPropertyStore().getIndexStore();
        return indexStore.getPropertyIndexes( count );
    }

    public int getKeyIdForProperty( long propertyId )
    {
        PropertyRecord propRecord =
            getPropertyStore().getLightRecord( propertyId );
        return propRecord.getKeyIndexId();
    }

    @Override
    public void setXaConnection( XaConnection connection )
    {
    }

    @Override
    public XAResource getXAResource()
    {
        throw readOnlyException();
    }

    private IllegalStateException readOnlyException()
    {
        return new IllegalStateException(
                "This is a read only transaction, " +
                "this method should never be invoked" );
    }

    @Override
    public void destroy()
    {
        throw readOnlyException();
    }

    @Override
    public ArrayMap<Integer, PropertyData> nodeDelete( long nodeId )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData nodeAddProperty( long nodeId, PropertyIndex index, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData nodeChangeProperty( long nodeId, long propertyId, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public void nodeRemoveProperty( long nodeId, long propertyId )
    {
        throw readOnlyException();
    }

    @Override
    public void nodeCreate( long id )
    {
        throw readOnlyException();
    }

    @Override
    public void relationshipCreate( long id, int typeId, long startNodeId, long endNodeId )
    {
        throw readOnlyException();
    }

    @Override
    public ArrayMap<Integer, PropertyData> relDelete( long relId )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData relAddProperty( long relId, PropertyIndex index, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public PropertyData relChangeProperty( long relId, long propertyId, Object value )
    {
        throw readOnlyException();
    }

    @Override
    public void relRemoveProperty( long relId, long propertyId )
    {
        throw readOnlyException();
    }

    @Override
    public RelationshipTypeData[] loadRelationshipTypes()
    {
        RelationshipTypeData relTypeData[] =
            neoStore.getRelationshipTypeStore().getRelationshipTypes();
        RelationshipTypeData rawRelTypeData[] =
            new RelationshipTypeData[relTypeData.length];
        for ( int i = 0; i < relTypeData.length; i++ )
        {
            rawRelTypeData[i] = new RelationshipTypeData(
                relTypeData[i].getId(), relTypeData[i].getName() );
        }
        return rawRelTypeData;
    }

    @Override
    public void createPropertyIndex( String key, int id )
    {
        throw readOnlyException();
    }

    @Override
    public void createRelationshipType( int id, String name )
    {
        throw readOnlyException();
    }

    @Override
    public RelIdArray getCreatedNodes()
    {
        return RelIdArray.EMPTY;
    }

    @Override
    public boolean isNodeCreated( long nodeId )
    {
        return false;
    }

    @Override
    public boolean isRelationshipCreated( long relId )
    {
        return false;
    }
}
