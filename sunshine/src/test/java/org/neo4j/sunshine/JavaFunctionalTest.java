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
package org.neo4j.sunshine;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.neo4j.kernel.ImpermanentGraphDatabase;
import org.neo4j.sunshine.commands.Query;

/**
 * Created by Andres Taylor
 * Date: 5/30/11
 * Time: 16:28
 */
public class JavaFunctionalTest
{
    @Test
    public void testHelloWorldFromJava() throws Exception
    {
        SunshineParser parser = new SunshineParser();
        ExecutionEngine engine = new ExecutionEngine(new ImpermanentGraphDatabase());
        Query query = parser.parse("start n = (0) return n");
        Projection result = engine.execute(query);
        assertTrue(result.toString().contains("Node[0]"));
    }
}
