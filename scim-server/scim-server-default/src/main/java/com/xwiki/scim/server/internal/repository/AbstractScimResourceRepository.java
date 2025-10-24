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
package com.xwiki.scim.server.internal.repository;

import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import javax.annotation.Nullable;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.scim.core.repository.ETag;
import org.apache.directory.scim.core.repository.InvalidRepositoryException;
import org.apache.directory.scim.core.repository.PatchHandler;
import org.apache.directory.scim.core.repository.Repository;
import org.apache.directory.scim.core.repository.RepositoryRegistry;
import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.apache.directory.scim.spec.exception.ConflictResourceException;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.exception.ResourceNotFoundException;
import org.apache.directory.scim.spec.exception.ScimResourceInvalidException;
import org.apache.directory.scim.spec.filter.Filter;
import org.apache.directory.scim.spec.filter.FilterExpressions;
import org.apache.directory.scim.spec.filter.FilterResponse;
import org.apache.directory.scim.spec.filter.PageRequest;
import org.apache.directory.scim.spec.filter.SortRequest;
import org.apache.directory.scim.spec.filter.attribute.AttributeReference;
import org.apache.directory.scim.spec.patch.PatchOperation;
import org.apache.directory.scim.spec.resources.ScimGroup;
import org.apache.directory.scim.spec.resources.ScimResource;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.apache.directory.scim.spec.schema.Meta;
import org.slf4j.Logger;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.user.group.GroupException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.scim.server.internal.SCIMRestletJacksonContextResolver;
import com.xwiki.scim.server.internal.resources.GroupsResourceImpl;
import com.xwiki.scim.server.internal.resources.UsersResourceImpl;

import static com.xwiki.scim.server.internal.Constants.SCIM_CODE_SPACE;
import static com.xwiki.scim.server.internal.Constants.SCIM_CONF_REF;
import static com.xwiki.scim.server.internal.repository.ScimUserRepository.SELF_ID;

/**
 * Common base for the scim resource user and group repository implementations.
 * @param <T> the type of handled scim resource (ScimUser or ScimGroup)
 * @since 0.0.1
 * @version $Id$
 */
@SuppressWarnings({"ClassFanOutComplexity"})
abstract class AbstractScimResourceRepository<T extends ScimResource> implements Repository<T>, Initializable
{
    private static final String ID = "id";
    private static final String USERS = "users";
    private static final String SLASH = "/";
    private static final String EXTERNAL_ID = "externalId";
    private static final String REFERENCE = "reference";
    private static final String CLIENT = "client";
    private static final String BASE_HQL_QUERY =
        "select scimResource.name, scimResource_reference.value, scimResource_externalId.value, "
            + "scimResource_scimRepresentation.value "
            + "from "
            + "BaseObject scimResource, "
            + "StringProperty scimResource_reference, "
            + "StringProperty scimResource_externalId, "
            + "LargeStringProperty scimResource_scimRepresentation "
            + "where "
            + "    scimResource.className  = 'SCIM.Code.SCIMResourceClass' "
            + "and scimResource_externalId.id.name = 'externalId' "
            + "and scimResource_reference.id.name = 'reference' "
            + "and scimResource_scimRepresentation.id.name = 'scimRepresentation' "
            + "and scimResource.id = scimResource_reference.id.id "
            + "and scimResource.id = scimResource_scimRepresentation.id.id "
            + "and scimResource.id = scimResource_externalId.id.id "
            + "and scimResource.name ";

    private static final String BASE_MANAGED_RESOURCE_HQL =
        BASE_HQL_QUERY + "like concat('SCIM.Resources.', :client, '.%') and ";

    private static final String XWIKI = "XWiki";

    private static final EntityReference GLOBAL_RIGHTS_CLASS =
        new LocalDocumentReference(XWIKI, "XWikiGlobalRights");

    private static final LocalDocumentReference SCIM_RESOURCE_CLASS =
        new LocalDocumentReference(SCIM_CODE_SPACE, "SCIMResourceClass");

    @Inject
    protected Provider<XWikiContext> xcontextProvider;

    @Inject
    protected DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("local")
    protected EntityReferenceSerializer<String> localSerializer;

    @Inject
    protected QueryManager queryManager;

    @Inject
    private Logger logger;

    @Inject
    private Provider<SchemaRegistry> schemaRegistryProvider;

    @Inject
    private Provider<RepositoryRegistry> repositoryRegistryProvider;

    @Inject
    private Provider<PatchHandler> patchHandlerProvider;

    @Override
    public void initialize() throws InitializationException
    {
        try {
            repositoryRegistryProvider.get().registerRepository(getResourceClass(), this);
        } catch (InvalidRepositoryException e) {
            throw new InitializationException("Failed to register this repository", e);
        }
    }

    /**
     * @return the requested resource, converted from its document to ScimClient or ScimGroup.
     * The id, externalId and meta fields should not be set by the implementation, this is taken care of by
     * the caller (#getScimResource(String, DocumentReference, ScimRecord))
     * @param scimClient the scim client requesting this resource
     * @param r the resource
     * @throws XWikiException if something wrong happens
     * @throws GroupException if something wrong happens
     */
    protected abstract T getScimResourceWithoutMeta(String scimClient, ScimRecord r)
        throws XWikiException, GroupException, QueryException, ResourceException;

    /**
     * @return the SCIM resource described by the given parameters by calling
     * #getScimResource(String, DocumentReference, XWikiDocument) to produce the resource object and setting the
     * meta, externalId and id field
     * @param scimClient the scim client requesting this resource
     * @param r the resource
     * @throws XWikiException if something wrong happens
     * @throws GroupException if something wrong happens
     */
    private T getScimResource(String scimClient, ScimRecord r)
        throws XWikiException, QueryException, ResourceException, GroupException
    {
        T scimResource = getScimResourceWithoutMeta(scimClient, r);
        if (scimResource == null) {
            return null;
        }

        Meta meta = new Meta();

        // The creation date of documents is set using new Date() (see com.xpn.xwiki.store.XWikiHibernateStore)
        // The date is thus in local time, using the system's current timezone.
        // To convert to LocalDateTime, we them must interpret the Date object as being in the current time zome,
        // assuming the time zone has never changed. We have no choice other than doing this assumption.
        XWikiContext context = xcontextProvider.get();
        XWikiDocument resourceDoc = context.getWiki().getDocument(r.resourceRef(), context);
        meta.setCreated(LocalDateTime.ofInstant(resourceDoc.getCreationDate().toInstant(), ZoneId.systemDefault()));
        meta.setLastModified(LocalDateTime.ofInstant(resourceDoc.getDate().toInstant(), ZoneId.systemDefault()));

        meta.setLocation(getLocation(isGroup(), r.getId()));
        meta.setVersion(resourceDoc.getVersion());
        meta.setResourceType(isGroup() ? ScimGroup.RESOURCE_NAME : ScimUser.RESOURCE_NAME);
        scimResource.setMeta(meta);
        scimResource.setId(r.getId());

        if (StringUtils.isEmpty(r.externalId())) {
            if (!SELF_ID.equals(r.getId())) {
                scimResource.setExternalId(r.externalId());
            }
        } else {
            scimResource.setExternalId(r.externalId());
        }

        return scimResource;
    }

    /**
     * @return the value of the location field as defined in
     *         <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-3.1">rfc7643</a>,
     *         which is basically the same as the ref parameter but with an absolute URL
     * @param isGroup whether the resource is a group
     * @param id the id for which to get the location field value
     */
    private String getLocation(boolean isGroup, String id)
    {
        String base = "";
        try {
            base = xcontextProvider.get().getURLFactory().getServerURL(xcontextProvider.get()).toExternalForm();
        } catch (MalformedURLException e) {
            logger.error("Failed to get XWiki's URL. The requested SCIM resource will have a relative location", e);
        }

        return base + getRef(isGroup, id);
    }

    /**
     * @return the ref field value as defined in
     *         <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-2.4">rfc7643</a>
     * @param isGroup whether the resource is a group
     * @param id the id for which to get the ref field value
     */
    String getRef(boolean isGroup, String id)
    {
        XWikiContext context = xcontextProvider.get();
        String webAppPath = context.getWiki().getWebAppPath(context);
        if (!webAppPath.startsWith(SLASH)) {
            webAppPath = SLASH + webAppPath;
        }
        if (!webAppPath.endsWith(SLASH)) {
            webAppPath += SLASH;
        }
        return webAppPath + "rest" + (isGroup ? GroupsResourceImpl.PATH : UsersResourceImpl.PATH) + '/'
            + id;
    }

    /**
     * @return the id from a pair of (value, ref) parameters coming from Scimple objects having a getValue()
     *         and a getRef() methods
     * @param value the result of the getValue() method
     * @param ref the result of the getRef() method
     * @throws ResourceException if the conversion fails
     */
    protected static String getIdFromValueAndRef(String value, String ref) throws ResourceException
    {
        String id = value;
        if (StringUtils.isEmpty(id)) {
            if (StringUtils.isEmpty(ref)) {
                throw new ResourceException(500, "The user or group is missing an id");
            }
            id = convertRefToId(ref);
            if (StringUtils.isEmpty(id)) {
                throw new ResourceException(500, "Could not convert the ref to an id");
            }
        }
        return id;
    }

    /**
     * @return the id contained in this SCIM $ref value (that is, whatever is after the last slash).
     * @param ref the value of a $ref field, as defined in
     *            <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-2.4">rfc7643</a>
     */
    private static String convertRefToId(String ref)
    {
        if (StringUtils.isEmpty(ref)) {
            return null;
        }

        int slash = ref.lastIndexOf('/');
        if (slash == -1) {
            return null;
        }

        return ref.substring(slash + 1);
    }

    /**
     * @return whether the current implementation of this abstract class handles groups
     */
    private boolean isGroup()
    {
        return getResourceClass() == ScimGroup.class;
    }

    @Override
    public T get(String id, Set<AttributeReference> includedAttributes, Set<AttributeReference> excludedAttributes)
        throws ResourceException
    {
        String scimClient = getScimClientId();

        try {
            T scimResource = null;
            ScimRecord r = getResource(scimClient, id, isGroup(), true);
            if (r != null) {
                scimResource = getScimResource(scimClient, r);
            }
            if (scimResource == null) {
                throw new ResourceNotFoundException(id);
            }
            return scimResource;
        } catch (XWikiException | GroupException | QueryException e) {
            throw new ScimResourceInvalidException("Could not get user or group", e);
        }
    }

    /**
     * @return the SCIM client id making the current request, after checking that the current client is indeed a SCIM
     *         client, by checking that the current XWiki user is listed in the SCIM application admin section.
     * @throws ResourceException if the client isn't an allowed SCIM client.
     */
    protected String getScimClientId() throws ResourceException
    {
        XWikiContext context = xcontextProvider.get();
        DocumentReference userReference = context.getUserReference();
        try {
            boolean found = false;
            if (userReference != null) {
                XWikiDocument confDoc = context.getWiki().getDocument(SCIM_CONF_REF, context);
                // we use a UsersClass, which is a large string...
                String[] users = ObjectUtils.defaultIfNull(
                    confDoc.getStringValue(USERS), "").split(",");
                for (String user : users) {
                    DocumentReference userRef = resolver.resolve(user, EntityType.DOCUMENT);
                    if (userReference.equals(userRef)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new ResourceException(401, "You are not authorized to access this resource. "
                    + "Your user needs to be listed in the SCIM Configuration object.");
            }
        } catch (XWikiException e) {
            throw new ResourceException(500, "Could not determine if you are authorized to access this resource. ",
                e);

        }
        return userReference.getName();
    }

    /**
     * @return the display name of the group.
     * @param groupRef the group for which to return the display name
     */
    protected String getGroupDisplayName(DocumentReference groupRef)
    {
        return groupRef.getName();
    }

    @Override
    public void delete(String id) throws ResourceException
    {
        String scimClient = getScimClientId();

        ScimRecord r = getResource(scimClient, id, isGroup(), true);
        if (r == null) {
            throw new ResourceNotFoundException(id);
        }

        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();

        try {
            wiki.deleteDocument(wiki.getDocument(r.referenceRef(), context), context);
            wiki.deleteDocument(wiki.getDocument(r.resourceRef(), context), context);
        } catch (XWikiException e) {
            throw new ResourceException(500, "Could not delete the resource", e);
        }
    }

    @Override
    public FilterResponse<T> find(Filter filter, PageRequest pageRequest, SortRequest sortRequest,
        Set<AttributeReference> includedAttributes, Set<AttributeReference> excludedAttributes) throws ResourceException
    {
        List<T> resources;
        try {
            resources = getAll();
        } catch (QueryException | XWikiException | GroupException e) {
            throw new ScimResourceInvalidException("Could not find users or groups", e);
        }

        // Taken from the InMemoryUserService example.
        long count = pageRequest.getCount() != null ? pageRequest.getCount() : resources.size();
        // SCIM is 1-based indexed
        long startIndex = pageRequest.getStartIndex() != null
            ? pageRequest.getStartIndex() - 1
            : 0;

        List<T> result = resources.stream()
            .skip(startIndex)
            .limit(count)
            .filter(FilterExpressions.inMemory(filter,
                schemaRegistryProvider.get().getSchema(
                    isGroup()
                        ? ScimGroup.SCHEMA_URI
                        : ScimUser.SCHEMA_URI
                )))
            .toList();

        return new FilterResponse<>(result, pageRequest, result.size());
    }

    @Override
    public T create(T scimResource, Set<AttributeReference> includedAttributes,
        Set<AttributeReference> excludedAttributes) throws ResourceException
    {
        ScimRecord r = newResource(scimResource);
        return createOrUpdateAndSave(scimResource, r.getId(), r, includedAttributes, excludedAttributes);
    }

    /**
     * @param scimResource the resource to create
     * @param r the resource to update
     * @throws ResourceException if something wrong happens
     */
    abstract XWikiDocument createOrUpdate(T scimResource, ScimRecord r) throws ResourceException;

    private T createOrUpdateAndSave(T scimResource, String id, ScimRecord r,
        Set<AttributeReference> includedAttributes, Set<AttributeReference> excludedAttributes) throws ResourceException
    {
        ScimRecord resource = r == null
            ? getResource(getScimClientId(), id, isGroup(), false)
            : r;
        String name = getName(scimResource);
        DocumentReference dRef = resource.resourceRef();
        if (!dRef.getName().equals(name)) {
            throw new ResourceException(501, "Renaming resources is not supported");
        }
        try {
            XWikiDocument resourceDoc = createOrUpdate(scimResource, resource);
            XWikiContext context = xcontextProvider.get();
            context.getWiki().saveDocument(resourceDoc, "Created or updated from SCIM", context);
            updateReferenceDoc(scimResource, resource);
        } catch (XWikiException e) {
            throw new ResourceException(500, "Failed to save resource document", e);
        }

        return get(id, includedAttributes, excludedAttributes);
    }

    private void updateReferenceDoc(T scimResource, ScimRecord r) throws XWikiException
    {
        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();
        XWikiDocument referenceDoc = wiki.getDocument(r.referenceRef(), context).clone();
        BaseObject referenceObj = referenceDoc.getXObject(SCIM_RESOURCE_CLASS, true, context);
        referenceObj.setStringValue(EXTERNAL_ID, scimResource.getExternalId());
        referenceObj.setStringValue(REFERENCE, localSerializer.serialize(r.resourceRef()));
        saveScimRepresentation(scimResource, referenceObj);
        wiki.saveDocument(referenceDoc, context);
    }

    private void saveScimRepresentation(T scimResource, BaseObject scimResourceObj)
    {
        try {
            scimResourceObj.setLargeStringValue("scimRepresentation", getScimRepresentation(scimResource));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize the json resource", e);
        }
    }

    private static <T extends ScimResource> String getScimRepresentation(T scimResource) throws JsonProcessingException
    {
        return SCIMRestletJacksonContextResolver.getContextStatic().writeValueAsString(scimResource);
    }

    @Override
    public T patch(String id, @Nullable Set<ETag> etags, List<PatchOperation> patchOperations,
        Set<AttributeReference> includedAttributes, Set<AttributeReference> excludedAttributes) throws ResourceException
    {
        // inspired of the InMemoryGroupService.java example implementation
        PatchHandler patchHandler = patchHandlerProvider.get();
        T original = get(id, includedAttributes, excludedAttributes);
        // PatchHandler#apply already takes care of null checks
        T updated = patchHandler.apply(original, patchOperations);
        return createOrUpdateAndSave(updated, id, null, includedAttributes, excludedAttributes);
    }

    @Override
    public T update(String id, @Nullable Set<ETag> etags, T resource, Set<AttributeReference> includedAttributes,
        Set<AttributeReference> excludedAttributes) throws ResourceException
    {
        return createOrUpdateAndSave(resource, id, null, includedAttributes, excludedAttributes);
    }

    /**
     * @return the users or the groups (depending on what T is) for the given client. This method will
     *         allocate external ids as needed for resources that don't yet have an external id associated to this
     *         client.
     * @throws QueryException if something wrong happens
     * @throws XWikiException if something wrong happens
     * @throws GroupException if something wrong happens
     */
    private List<T> getAll()
        throws QueryException, XWikiException, GroupException, ResourceException
    {
        String scimClient = getScimClientId();
        List<ScimRecord> res = getQueryResults(scimClient, null, isGroup());
        ArrayList<T> resources = new ArrayList<>(res.size());
        for (ScimRecord r : res) {
            T scimResource = getScimResource(scimClient, r);
            resources.add(scimResource);
        }

        return resources;
    }

    /**
     * @return List of resources
     * @param scimClient the scim client for which to get the resources
     * @param requestedId the SCIM id of the resource to get. If empty, all resources of the requested type are returned
     * @param group whether the resource to get is a group
     */
    private List<ScimRecord> getQueryResults(String scimClient, String requestedId, boolean group)
        throws QueryException
    {
        String q = (
            BASE_HQL_QUERY + (requestedId == null
                ? "like concat('SCIM.Resources.', :client, '.', :type, '.%')"
                : "= concat('SCIM.Resources.', :client, '.', :type, '.', :id)")
            + " order by scimResource_reference.value "
        );

        Query query = queryManager.createQuery(q, Query.HQL)
            .setWiki(xcontextProvider.get().getMainXWiki())
            .bindValue(CLIENT, scimClient)
            .bindValue("type", group ? ScimGroup.RESOURCE_NAME : ScimUser.RESOURCE_NAME);

        if (requestedId != null) {
            query = query.bindValue(ID, requestedId);
        }

        return query.execute()
            .stream()
            .map(entry -> ScimRecord.fromDBEntry((Object[]) entry, resolver))
            .toList();
    }

    protected abstract String getName(T scimResource);

    /**
     * Create a new document for this scim resource, making sure not to overwrite any existing XWiki user or group.
     * @param scimResource the scim resource (ScimUser or ScimGroup) for which a new document is needed
     * @throws ResourceException if the resource already exists
     */
    protected ScimRecord newResource(T scimResource) throws ResourceException
    {
        String scimClient = getScimClientId();
        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();
        String main = context.getMainXWiki();
        DocumentReference resourceRef = new DocumentReference(main, XWIKI, getName(scimResource));
        try {
            XWikiDocument resourceDoc = wiki.getDocument(resourceRef, context);
            if (!resourceDoc.isNew()) {
                throw new ConflictResourceException("This resource already exists");
            }
            List<String> resourceSpace = List.of("SCIM", "Resources", scimClient);
            ensureResourceSpaceProtection(resourceSpace);

            List<String> resourceTypeSpace = new ArrayList<>(resourceSpace);
            resourceTypeSpace.add(scimResource.getResourceType());
            String id = UUID.randomUUID().toString();
            DocumentReference referenceRef = new DocumentReference(main, resourceTypeSpace, id);
            return new ScimRecord(referenceRef, resourceRef, scimResource.getExternalId(),
                getScimRepresentation(scimResource));
        } catch (XWikiException | JsonProcessingException e) {
            throw new ScimResourceInvalidException("Failed to create this resource's document", e);
        }
    }

    private void ensureResourceSpaceProtection(List<String> resourceSpace) throws XWikiException
    {
        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();
        String main = context.getMainXWiki();

        DocumentReference webPreferencesRef = new DocumentReference(main, resourceSpace, "WebPreferences");
        if (!wiki.exists(webPreferencesRef, context)) {
            XWikiDocument webPreferenceDoc = wiki.getDocument(webPreferencesRef, context).clone();
            BaseObject rightObj = webPreferenceDoc.getXObject(GLOBAL_RIGHTS_CLASS, true, context);
            String scimClient = resourceSpace.get(resourceSpace.size() - 1);
            rightObj.setStringValue(USERS, XWIKI + '.' + scimClient);
            rightObj.setIntValue("allow", 1);
            rightObj.setStringValue("levels", "view");
            wiki.saveDocument(webPreferenceDoc, context);
        }
    }

    protected ScimRecord getResource(String scimClient, String id, boolean isGroup, boolean allowNull)
        throws ResourceNotFoundException
    {
        ScimRecord r;
        try {
            r = getResourceRef(scimClient, id, isGroup);
        } catch (QueryException e) {
            throw new ResourceNotFoundException(id, e);
        }

        if (r == null) {
            if (allowNull) {
                return null;
            }
            throw new ResourceNotFoundException(id);
        }

        if (r.isGroup() != isGroup) {
            throw new ScimResourceInvalidException("Bad resource type");
        }
        return r;
    }

    ScimRecord getResourceRef(String scimClient, String id, boolean group) throws QueryException
    {
        if (SELF_ID.equals(id)) {
            return ScimRecord.getSelf(xcontextProvider.get());
        }

        List<ScimRecord> queryResults = getQueryResults(scimClient, id, group);
        if (queryResults.isEmpty()) {
            return null;
        }

        return queryResults.get(0);
    }

    protected void checkScimClientAccess(String scimClient, String id, boolean isGroup) throws ResourceException
    {
        getResource(scimClient, id, isGroup, false);
    }

    protected record ScimRecord(EntityReference referenceRef, DocumentReference resourceRef,
                                String externalId, String scimRepresentation)
    {
        public static ScimRecord fromDBEntry(Object[] entry, DocumentReferenceResolver<String> resolver)
        {
            String referenceRef = (String) entry[0];
            String resourceRef = (String) entry[1];
            String externalId = (String) entry[2];
            String scimRepresentation = (String) entry[3];

            return new ScimRecord(
                resolver.resolve(referenceRef, EntityType.DOCUMENT),
                resolver.resolve(resourceRef, EntityType.DOCUMENT),
                externalId,
                scimRepresentation
            );
        }

        public static ScimRecord getSelf(XWikiContext context)
        {
            return new ScimRecord(null, context.getUserReference(), "", "");
        }

        public String getId()
        {
            if (referenceRef() == null) {
                return SELF_ID;
            }
            return referenceRef().getName();
        }

        public boolean isGroup()
        {
            if (referenceRef() == null) {
                return false;
            }
            return ScimGroup.RESOURCE_NAME.equals(referenceRef().getParent().getName());
        }
    }

    protected List<String> getManagedResourcesByIds(List<String> ids, String scimClient) throws QueryException
    {
        String q = (
            BASE_MANAGED_RESOURCE_HQL + "substring("
                + "     scimResource.name,"
                + "     length(scimResource.name) - (locate('.', reverse(scimResource.name) + '.') - 2)"
                + ") in :ids"
        );

        return queryManager.createQuery(q, Query.HQL)
            .setWiki(xcontextProvider.get().getMainXWiki())
            .bindValue(CLIENT, scimClient)
            .bindValue("ids", ids)
            .execute()
            .stream()
            .map(entry -> (String) ((Object[]) entry)[1])
            .toList();
    }

    protected Map<String, ScimRecord> getManagedResourcesByRefs(Collection<DocumentReference> refs, String scimClient)
        throws QueryException
    {
        String q = BASE_MANAGED_RESOURCE_HQL + "scimResource_reference.value in :fullNames";

        return queryManager.createQuery(q, Query.HQL)
            .setWiki(xcontextProvider.get().getMainXWiki())
            .bindValue(CLIENT,  scimClient)
            .bindValue("fullNames", refs.stream().map(localSerializer::serialize).toList())
            .execute()
            .stream().map(entry -> Map.entry(
                (String) ((Object[]) entry)[1],
                ScimRecord.fromDBEntry((Object[]) entry, resolver)
            )).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected Map<String, ScimRecord> getManagedResourcesByFullNames(Collection<String> fullNames, String scimClient)
        throws QueryException
    {
        return getManagedResourcesByRefs(fullNames.stream().map(resolver::resolve).toList(), scimClient);
    }
}
