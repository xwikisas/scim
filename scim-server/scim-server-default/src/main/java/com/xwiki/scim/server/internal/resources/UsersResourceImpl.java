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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.apache.directory.scim.protocol.UserResource;
import org.apache.directory.scim.protocol.adapter.FilterWrapper;
import org.apache.directory.scim.protocol.data.PatchRequest;
import org.apache.directory.scim.protocol.data.SearchRequest;
import org.apache.directory.scim.protocol.exception.ScimException;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.filter.SortOrder;
import org.apache.directory.scim.spec.filter.attribute.AttributeReference;
import org.apache.directory.scim.spec.filter.attribute.AttributeReferenceListWrapper;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.annotation.Component;
import org.xwiki.rest.XWikiRestComponent;

import com.xwiki.scim.server.internal.repository.ScimGroupRepository;
import com.xwiki.scim.server.internal.repository.ScimUserRepository;

/**
 * REST API of the /scim/v2/Users endpoint.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("com.xwiki.scim.server.internal.resources.UsersResourceImpl")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Path(UsersResourceImpl.PATH)
public class UsersResourceImpl extends AbstractScimResourceImpl<ScimUser>
    implements XWikiRestComponent, UserResource
{
    /**
     * Path under /rest/.
     */
    public static final String PATH = "/scim/v2/Users";

    // Make sure the user repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimUserRepository userRepository;

    // Make sure the group repository is initialized and registered before hitting the REST endpoint
    @Inject
    private ScimGroupRepository groupRepository;

    @Override
    Class<ScimUser> getResourceClass()
    {
        return ScimUser.class;
    }

    @Override
    public Response getById(String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return super.getById(id, attributes, excludedAttributes);
    }

    @Override
    public Response query(AttributeReferenceListWrapper attributes, AttributeReferenceListWrapper excludedAttributes,
        FilterWrapper filter, AttributeReference sortBy, SortOrder sortOrder, Integer startIndex, Integer count)
        throws ScimException, ResourceException
    {
        return super.query(attributes, excludedAttributes, filter, sortBy, sortOrder, startIndex, count);
    }

    @Override
    public Response create(ScimUser resource, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return super.create(resource, attributes, excludedAttributes);
    }

    @Override
    public Response find(SearchRequest request) throws ScimException, ResourceException
    {
        return super.find(request);
    }

    @Override
    public Response update(ScimUser resource, String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return super.update(resource, id, attributes, excludedAttributes);
    }

    @Override
    public Response patch(PatchRequest patchRequest, String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return super.patch(patchRequest, id, attributes, excludedAttributes);
    }

    @Override
    public Response delete(String id) throws ScimException, ResourceException
    {
        return super.delete(id);
    }
}
