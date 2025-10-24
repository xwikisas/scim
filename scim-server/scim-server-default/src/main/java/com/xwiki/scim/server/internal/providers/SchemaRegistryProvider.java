/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.scim.server.internal.providers;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.xwiki.component.annotation.Component;

/**
 * SCIM SchemaRegistry provider.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
public class SchemaRegistryProvider implements Provider<SchemaRegistry>
{
    private static SchemaRegistry sr;

    @Override
    public SchemaRegistry get()
    {
        return getStatic();
    }

    /**
     * @return the SchemaRegistry object that scimple wants, especially when parsing JSON coming from REST calls.
     * This static method is a horrible hack. I hope you are not reading this code because you are struggling with an
     * impossible bug caused by this.
     * Early in SCIMRestletJacksonContextResolver (in integration tests anyway), the SchemaRegistry Provider seems
     * to return null for some reason, possibly because this component is not loaded yet or something like this.
     * Hence the ugly static method with the ugly static field work for sure.
     */
    public static SchemaRegistry getStatic()
    {
        if (sr == null) {
            sr = new SchemaRegistry();
        }
        return sr;
    }
}
