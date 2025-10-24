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
package com.xwiki.scim.server.internal.resources;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.apache.directory.scim.protocol.SchemaResource;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.phase.Initializable;
import org.xwiki.rest.XWikiRestComponent;

import com.xwiki.scim.server.internal.repository.ScimGroupRepository;
import com.xwiki.scim.server.internal.repository.ScimUserRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

/**
 * REST API of the /Schemas endpoint.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("com.xwiki.scim.server.internal.resources.SchemaResourceImpl")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Path(SchemaResourceImpl.PATH)
public class SchemaResourceImpl implements Initializable, SchemaResource, XWikiRestComponent
{
    static final String PATH = "/scim/v2/Schemas";

    @Inject
    private Provider<SchemaRegistry> schemaRegistryProvider;

    // Make sure the user repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimUserRepository userRepository;

    // Make sure the group repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimGroupRepository groupRepository;

    private org.apache.directory.scim.server.rest.SchemaResourceImpl impl;

    @Override
    public void initialize()
    {
        impl = new org.apache.directory.scim.server.rest.SchemaResourceImpl(
            schemaRegistryProvider.get()
        );
    }

    @Override
    public Response getAllSchemas(String filter, UriInfo uriInfo)
    {
        return impl.getAllSchemas(filter, uriInfo);
    }

    @Override
    public Response getSchema(String uri, UriInfo uriInfo)
    {
        return impl.getSchema(uri, uriInfo);
    }
}
