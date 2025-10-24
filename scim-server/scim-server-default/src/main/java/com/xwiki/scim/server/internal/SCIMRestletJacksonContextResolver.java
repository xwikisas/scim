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
package com.xwiki.scim.server.internal;

import jakarta.inject.Singleton;
import javax.ws.rs.Produces;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.apache.directory.scim.core.json.ObjectMapperFactory;
import org.apache.directory.scim.protocol.Constants;
import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiRestComponent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xwiki.scim.server.internal.providers.SchemaRegistryProvider;

/**
 * Use directory-scimple provided ObjectMapper for SCIM requests.
 * Inspired of org.xwiki.rest.jersey.internal.RestletJacksonContextResolver
 * @version $Id$
 * @since 0.0.1
 */
@Provider
@Produces(Constants.SCIM_CONTENT_TYPE)
@Component
@Singleton
public class SCIMRestletJacksonContextResolver implements ContextResolver<ObjectMapper>, XWikiRestComponent
{
    /**
     * Creates a new instance.
     */
    public SCIMRestletJacksonContextResolver()
    {
        // nothing
    }

    @Override
    public ObjectMapper getContext(Class<?> objectType)
    {
        return getContextStatic();
    }

    /**
     * @return an object mapper used for serializing and deserializing scim resources
     * This static method works around the fact that SCIMRestletJacksonContextResolver cannot be injected in
     * resource repositories.
     */
    public static ObjectMapper getContextStatic()
    {
        return ObjectMapperFactory.createObjectMapper(SchemaRegistryProvider.getStatic());
    }
}
