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

import java.util.concurrent.Callable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.directory.scim.core.repository.RepositoryRegistry;
import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.apache.directory.scim.protocol.BaseResourceTypeResource;
import org.apache.directory.scim.protocol.Constants;
import org.apache.directory.scim.protocol.adapter.FilterWrapper;
import org.apache.directory.scim.protocol.data.PatchRequest;
import org.apache.directory.scim.protocol.data.SearchRequest;
import org.apache.directory.scim.protocol.exception.ScimException;
import org.apache.directory.scim.server.rest.BaseResourceTypeResourceImpl;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.filter.SortOrder;
import org.apache.directory.scim.spec.filter.attribute.AttributeReference;
import org.apache.directory.scim.spec.filter.attribute.AttributeReferenceListWrapper;
import org.apache.directory.scim.spec.resources.ScimResource;
import org.slf4j.Logger;
import org.xwiki.component.phase.InitializationException;

import static com.xwiki.scim.server.internal.Utils.setField;

/**
 * Proxy to BaseResourceTypeResourceImpl.
 * Unfortunately, we can't just extend BaseResourceTypeResourceImpl because of its constructor expecting the schema
 * and repository registries. We need to parrot each method of the BaseResourceTypeResource interface. What's worse
 * is that this interface declares all its members as default, which makes it error-prone to extend without missing
 * some methods. Although this will result in a 500 not implemented error in the worst case.
 * All this is caused by directory-scimple expecting CDI to work, and XWiki not supporting CDI.
 * @param <T> the type of handled resource (ScimUser or ScimGroup)
 * @since 0.0.1
 * @version $Id$
 */
abstract class AbstractScimResourceImpl<T extends ScimResource> implements BaseResourceTypeResource<T>
{
    @Context
    private UriInfo uriInfo;

    @Context
    private Request request;

    @Context
    private HttpHeaders headers;

    @Inject
    private Provider<SchemaRegistry> schemaRegistryProvider;

    @Inject
    private Provider<RepositoryRegistry> repositoryRegistryProvider;

    @Inject
    private Logger logger;

    private BaseResourceTypeResourceImpl<T> impl;

    private void init()
    {
        if (impl == null) {
            init(uriInfo, request, headers);
        }
    }

    void init(UriInfo uriInfo, Request request, HttpHeaders headers)
    {
        try {
            impl = new BaseResourceTypeResourceImpl<>(
                schemaRegistryProvider.get(),
                repositoryRegistryProvider.get(),
                getResourceClass()
            ) { };
            setContextFields(uriInfo, request, headers);
        } catch (InitializationException e) {
            throw new WebApplicationException("Failed to initialize the resource endpoint", e);
        }
    }

    void setContextFields(UriInfo uriInfo, Request request, HttpHeaders headers) throws InitializationException
    {
        // We need to set those fields using reflection, the BaseResourceTypeResourceImpl class expects CDI to work ,
        // but we don't have this.
        setField(impl, "uriInfo", uriInfo);
        setField(impl, "request", request);
        setField(impl, "headers", headers);
    }

    abstract Class<T> getResourceClass();

    private Response call(Callable<Response> f) throws ScimException, ResourceException
    {
        init();
        try {
            return f.call();
        } catch (ScimException | ResourceException e) {
            throw e;
        } catch (Exception e) {
            Response response = Response.status(500, e.getMessage()).type(Constants.SCIM_CONTENT_TYPE).build();
            logger.error(e.getMessage(), e);
            return response;
        }
    }

    @Override
    public Response getById(String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return call(() -> impl.getById(id, attributes, excludedAttributes));
    }

    @Override
    public Response query(AttributeReferenceListWrapper attributes, AttributeReferenceListWrapper excludedAttributes,
        FilterWrapper filter, AttributeReference sortBy, SortOrder sortOrder, Integer startIndex, Integer count)
        throws ScimException, ResourceException
    {
        return call(() -> impl.query(attributes, excludedAttributes, filter, sortBy, sortOrder, startIndex, count));
    }

    @Override
    public Response create(T resource, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return call(() -> impl.create(resource, attributes, excludedAttributes));
    }

    @Override
    public Response find(SearchRequest request) throws ScimException, ResourceException
    {
        return call(() -> impl.find(request));
    }

    @Override
    public Response update(T resource, String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return call(() -> impl.update(resource, id, attributes, excludedAttributes));
    }

    @Override
    public Response patch(PatchRequest patchRequest, String id, AttributeReferenceListWrapper attributes,
        AttributeReferenceListWrapper excludedAttributes) throws ScimException, ResourceException
    {
        return call(() -> impl.patch(patchRequest, id, attributes, excludedAttributes));
    }

    @Override
    public Response delete(String id) throws ScimException, ResourceException
    {
        return call(() -> impl.delete(id));
    }
}
