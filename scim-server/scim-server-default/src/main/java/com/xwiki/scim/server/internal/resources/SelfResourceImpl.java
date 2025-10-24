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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.directory.scim.protocol.SelfResource;
import org.apache.directory.scim.protocol.data.PatchRequest;
import org.apache.directory.scim.protocol.exception.ScimException;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.filter.attribute.AttributeReferenceListWrapper;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.rest.XWikiRestComponent;

import static com.xwiki.scim.server.internal.repository.ScimUserRepository.SELF_ID;

/**
 * REST API of the /scim/v2/Me endpoint.
 * I'm confused by SCImple's SelfResourceImpl and in particular the SelfIdResolver type. We reimplement this by
 * calling userResource directly, following
 * <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.11">Section 3.11 of rfc7644</a>
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("com.xwiki.scim.server.internal.resources.SelfResourceImpl")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
@Path(SelfResourceImpl.PATH)
public class SelfResourceImpl implements SelfResource, XWikiRestComponent
{
    static final String PATH = "/scim/v2/Me";

    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    @Context
    private HttpHeaders headers;

    @Inject
    @Named("com.xwiki.scim.server.internal.resources.UsersResourceImpl")
    private XWikiRestComponent userResource;

    private void init()
    {
        ((UsersResourceImpl) userResource).init(uriInfo, request, headers);
    }

    @Override
    public Response getSelf(AttributeReferenceListWrapper attributes, AttributeReferenceListWrapper excludedAttributes)
        throws ScimException, ResourceException
    {
        init();
        return ((UsersResourceImpl) userResource).getById(SELF_ID, attributes, excludedAttributes);
    }

    @Override
    public Response update(ScimUser resource, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        init();
        return ((UsersResourceImpl) userResource).update(resource, SELF_ID, attributes, excludedAttributes);
    }

    @Override
    public Response patch(PatchRequest patchRequest, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        init();
        return ((UsersResourceImpl) userResource).patch(patchRequest, SELF_ID, attributes, excludedAttributes);
    }

    @Override
    public Response delete() throws ScimException, ResourceException
    {
        init();
        return ((UsersResourceImpl) userResource).delete(SELF_ID);
    }
}
