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

import org.apache.directory.scim.core.repository.RepositoryRegistry;
import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.apache.directory.scim.protocol.BulkResource;
import org.apache.directory.scim.protocol.data.BulkRequest;
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
 * REST API of the /Bulk endpoint.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("com.xwiki.scim.server.internal.resources.BulkResourceImpl")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Path(BulkResourceImpl.PATH)
public class BulkResourceImpl implements Initializable, BulkResource, XWikiRestComponent
{
    static final String PATH = "/scim/v2/Bulk";

    @Inject
    private Provider<SchemaRegistry> schemaRegistryProvider;

    @Inject
    private Provider<RepositoryRegistry> repositoryRegistryProvider;

    // Make sure the user repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimUserRepository userRepository;

    // Make sure the group repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimGroupRepository groupRepository;

    private org.apache.directory.scim.server.rest.BulkResourceImpl impl;

    @Override
    public void initialize()
    {
        impl = new org.apache.directory.scim.server.rest.BulkResourceImpl(
            schemaRegistryProvider.get(),
            repositoryRegistryProvider.get()
        );
    }

    @Override
    public Response doBulk(BulkRequest bulkRequest, UriInfo uriInfo)
    {
        return impl.doBulk(bulkRequest, uriInfo);
    }
}
