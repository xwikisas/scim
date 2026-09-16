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
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.directory.scim.core.repository.InvalidRepositoryException;
import org.apache.directory.scim.core.repository.PatchHandler;
import org.apache.directory.scim.core.repository.Repository;
import org.apache.directory.scim.core.repository.RepositoryRegistry;
import org.apache.directory.scim.core.repository.ScimRequestContext;
import org.apache.directory.scim.core.schema.SchemaRegistry;
import org.apache.directory.scim.spec.exception.ConflictResourceException;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.exception.ResourceNotFoundException;
import org.apache.directory.scim.spec.exception.ScimResourceInvalidException;
import org.apache.directory.scim.spec.filter.Filter;
import org.apache.directory.scim.spec.filter.FilterExpressions;
import org.apache.directory.scim.spec.filter.FilterResponse;
import org.apache.directory.scim.spec.filter.SortExpressions;
import org.apache.directory.scim.spec.patch.PatchOperation;
import org.apache.directory.scim.spec.resources.ScimGroup;
import org.apache.directory.scim.spec.resources.ScimResource;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.apache.directory.scim.spec.schema.Meta;
import org.apache.directory.scim.spec.schema.Schema;
import org.slf4j.Logger;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.contrib.usercommon.formatter.UserFormatter;
import org.xwiki.contrib.usercommon.formatter.UserFormatterFactory;
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
import com.xwiki.scim.server.internal.ScimConfiguration;
import com.xwiki.scim.server.internal.SCIMRestletJacksonContextResolver;
import com.xwiki.scim.server.internal.resources.GroupsResourceImpl;
import com.xwiki.scim.server.internal.resources.UsersResourceImpl;

import static com.xwiki.scim.server.internal.Constants.SCIM_CODE_SPACE;
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
    protected static final LocalDocumentReference OIDC_USER_CLASS_REF =
        new LocalDocumentReference(List.of(XWiki.SYSTEM_SPACE, "OIDC"), "UserClass");

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

    protected UserFormatter usernameCleaner;

    @Inject
    protected ScimConfiguration conf;

    @Inject
    protected UserFormatterFactory userFormatterFactory;

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
        usernameCleaner = userFormatterFactory.create();
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

        meta.setLocation(getLocation(r.getId()));
        meta.setVersion(resourceDoc.getVersion());
        meta.setResourceType(isGroup() ? ScimGroup.RESOURCE_NAME : ScimUser.RESOURCE_NAME);
        scimResource.setMeta(meta);
        scimResource.setId(r.getId());

        if (!StringUtils.isEmpty(r.externalId())) {
            scimResource.setExternalId(r.externalId());
        }

        return scimResource;
    }

    /**
     * @return the value of the location field as defined in
     *         <a href="https://datatracker.ietf.org/doc/html/rfc7643#section-3.1">rfc7643</a>,
     *         which is basically the same as the ref parameter but with an absolute URL
     * @param id the id for which to get the location field value
     */
    private String getLocation(String id)
    {
        String base = "";
        try {
            base = xcontextProvider.get().getURLFactory().getServerURL(xcontextProvider.get()).toExternalForm();
        } catch (MalformedURLException e) {
            logger.error("Failed to get XWiki's URL. The requested SCIM resource will have a relative location", e);
        }

        return base + getRef(isGroup(), id);
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
    public T get(String id, ScimRequestContext requestContext)
        throws ResourceException
    {
        String scimClient = getScimClientId();

        try {
            T scimResource = null;
            ScimRecord r = getRecord(scimClient, id, isGroup(), true);
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
        boolean found = false;
        if (userReference != null) {
            for (String user : conf.getScimUsers()) {
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

        ScimRecord r = getRecord(scimClient, id, isGroup(), true);
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
    public FilterResponse<T> find(Filter filter, ScimRequestContext requestContext) throws ResourceException
    {
        List<T> resources;
        try {
            resources = getAll();
        } catch (QueryException | XWikiException | GroupException e) {
            throw new ScimResourceInvalidException("Could not find users or groups", e);
        }

        Schema schema = schemaRegistryProvider.get().getSchema(isGroup() ? ScimGroup.SCHEMA_URI : ScimUser.SCHEMA_URI);
        // Taken from the InMemoryUserService example.
        return resources.stream()
            .filter(FilterExpressions.inMemory(filter, schema))
            .sorted(SortExpressions.comparator(requestContext.getSortRequest(), schema))
            .collect(FilterResponse.paginate(requestContext.getPageRequestOrDefault()));
    }

    @Override
    public T create(T scimResource, ScimRequestContext requestContext) throws ResourceException
    {
        ScimRecord r = newResource(scimResource);
        return createOrUpdateAndSave(scimResource, r.getId(), r, requestContext);
    }

    /**
     * @param scimResource the resource to create
     * @param r the resource to update
     * @throws ResourceException if something wrong happens
     */
    abstract XWikiDocument createOrUpdate(T scimResource, ScimRecord r) throws ResourceException;

    private T createOrUpdateAndSave(T scimResource, String id, ScimRecord r, ScimRequestContext requestContext)
        throws ResourceException
    {
        ScimRecord resource = r == null
            ? getRecord(getScimClientId(), id, isGroup(), false)
            : r;
        try {
            DocumentReference newDocRef = getDocumentReference(scimResource);
            DocumentReference oldDocRef = resource.resourceRef();
            XWikiContext context = xcontextProvider.get();
            XWiki wiki = context.getWiki();
            if (!newDocRef.equals(oldDocRef) && wiki.exists(newDocRef, context)) {
                throw new ResourceException(400, "The target user already exists");
            }
            XWikiDocument resourceDoc = createOrUpdate(scimResource, resource);
            wiki.saveDocument(resourceDoc, "Created or updated from SCIM", context);
            if (!newDocRef.equals(oldDocRef) && wiki.exists(oldDocRef, context)) {
                wiki.renameDocument(oldDocRef, newDocRef, false, List.of(), List.of(), context);
                resource = new ScimRecord(
                        resource.referenceRef, newDocRef, resource.externalId, resource.scimRepresentation);
            }
            updateReferenceDoc(scimResource, resource);
        } catch (QueryException | XWikiException e) {
            throw new ResourceException(500, "Failed to save the resource document", e);
        }

        return get(id, requestContext);
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
    public T patch(String id, List<PatchOperation> patchOperations, ScimRequestContext requestContext)
        throws ResourceException
    {
        // inspired of the InMemoryGroupService.java example implementation
        PatchHandler patchHandler = patchHandlerProvider.get();
        T original = get(id, requestContext);
        // PatchHandler#apply already takes care of null checks
        T updated = patchHandler.apply(original, patchOperations);
        return createOrUpdateAndSave(updated, id, null, requestContext);
    }

    @Override
    public T update(String id, T resource, ScimRequestContext requestContext) throws ResourceException
    {
        return createOrUpdateAndSave(resource, id, null, requestContext);
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

    /**
     * @return the name to use to get the reference of a resource (which will be XWiki.name)
     * @param scimResource the considered resource
     */
    protected abstract String getDefaultReferenceName(T scimResource);

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
        try {
            DocumentReference resourceRef = getDocumentReference(scimResource);
            XWikiDocument resourceDoc = wiki.getDocument(resourceRef, context);
            if (isNewResourceConflicting(resourceDoc)) {
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
        } catch (XWikiException | JsonProcessingException | QueryException e) {
            throw new ScimResourceInvalidException("Failed to create this resource's document", e);
        }
    }

    abstract Map<String, String> getFormatVariables(T scimResource);

    abstract String getReferenceNameFormat();

    /**
     * @return the document reference for this SCIM resource. The document name is determined using the group or user
     * format defined in the configuration, or, by default, using the clean display name (for groups) or the clean
     * username (for users). If the OIDC interop is enabled, we try to find a matching user first using the subject
     * field of the OIDC user object.
     * @param scimResource the resource to get the document reference for
     * @throws QueryException if something wrong happens
     */
    private DocumentReference getDocumentReference(T scimResource) throws QueryException
    {
        String wiki = xcontextProvider.get().getMainXWiki();
        Map<String, String> formatVariables = getFormatVariables(scimResource);
        Pattern forbiddenPattern = conf.getForbiddenPattern();
        String forbiddenReplacement = conf.getForbiddenReplacement();
        UserFormatter formatter = userFormatterFactory.create(formatVariables, forbiddenPattern, forbiddenReplacement);
        String name = null;
        if (conf.isOIDCEnabled()) {
            String subject = formatter.format(conf.getOIDCSubject());
            List<String> res = queryManager.createQuery(
                "select oidcUserObj.name from BaseObject oidcUserObj, StringProperty subject where "
                    + "oidcUserObj.className = 'XWiki.OIDC.UserClass' and "
                    + "subject.id.id = oidcUserObj.id and "
                    + "subject.name = 'subject' and "
                    + "subject.value = :subject",
                Query.HQL).bindValue("subject", subject).setLimit(1).execute();
            if (!res.isEmpty()) {
                name = resolver.resolve(res.get(0)).getName();
            }
        }

        if (name == null) {
            name = formatter.format(getReferenceNameFormat());
        }

        if (name == null) {
            name = clean(getDefaultReferenceName(scimResource));
        }

        return new DocumentReference(wiki, XWIKI, name);
    }

    private String clean(String name)
    {
        usernameCleaner.setForbiddenPattern(conf.getForbiddenPattern());
        usernameCleaner.setForbiddenReplacement(conf.getForbiddenReplacement());
        return usernameCleaner.clean(name);
    }

    /**
     * @return whether the resource we are trying to create conflicts with an existing resource
     * @param resourceDoc the document of the resource
     * @throws QueryException if something wrong happens
     */
    private boolean isNewResourceConflicting(XWikiDocument resourceDoc) throws QueryException
    {
        // If the resource doesn't exist, there's no conflict
        if (resourceDoc.isNew()) {
            return false;
        }

        // if it exists and oidc is not enabled, it's always a conflict
        if (!conf.isOIDCEnabled()) {
            return true;
        }

        // it is not a conflict if the user has an oidc user class object: the user can have been created from OIDC.
        // For groups, we cannot perform this check
        if (!isGroup() && resourceDoc.getXObject(OIDC_USER_CLASS_REF) == null) {
            return true;
        }

        // There's conflict if some SCIM client is already managing this resource (including the current one)
        return !queryManager.createQuery(
            "select d.fullName from Document d, d.object(SCIM.Code.SCIMResourceClass) o where o.reference = :ref",
                Query.XWQL)
            .setWiki(xcontextProvider.get().getMainXWiki())
            .bindValue("ref", localSerializer.serialize(resourceDoc.getDocumentReference()))
            .setLimit(1)
            .execute()
            .isEmpty();
    }

    /**
     * Make sure the SCIM.Resources.{clientId} space is protected.
     * @param resourceSpace List.of("SCIM", "Resources", {clientId})
     * @throws XWikiException if something wrong happens
     */
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

    /**
     * @return the record of a resource, if found, otherwise null (if allowNull is true)
     * @param scimClient the SCIM client to use to find the resource
     * @param id the SCIM (internal) id of the resource to find
     * @param isGroup is the resource to find a group?
     * @param allowNull is it ok not to find any resource? (for instance because we are trying to create the resource)
     * @throws ResourceNotFoundException if allowNull is false and the resource was not found
     * @throws ScimResourceInvalidException if isGroup doesn't match what we found (not supposed to happen)
     */
    protected ScimRecord getRecord(String scimClient, String id, boolean isGroup, boolean allowNull)
        throws ResourceNotFoundException
    {
        // First, we try to find an existing resource with this id
        ScimRecord r;
        try {
            r = getResourceRecord(scimClient, id, isGroup);
        } catch (QueryException e) {
            throw new ResourceNotFoundException(id, e);
        }

        if (r == null) {
            if (allowNull) {
                // is it ok not to find this resource? (because we are creating it for instance)
                return null;
            }

            // we expected to find this resource
            throw new ResourceNotFoundException(id);
        }

        if (r.isGroup() != isGroup) {
            throw new ScimResourceInvalidException("Bad resource type");
        }
        return r;
    }

    /**
     * Return the record of an existing SCIM resource.
     * @param scimClient the SCIM client to use to find the resource
     * @param id the scim (internal) id of the resource to find
     * @param isGroup is the resource to find a group?
     * @return the record of the resource
     * @throws QueryException if something wrong happen
     */
    ScimRecord getResourceRecord(String scimClient, String id, boolean isGroup) throws QueryException
    {
        if (SELF_ID.equals(id)) {
            return ScimRecord.getSelf(xcontextProvider.get());
        }

        List<ScimRecord> queryResults = getQueryResults(scimClient, id, isGroup);
        if (queryResults.isEmpty()) {
            return null;
        }

        return queryResults.get(0);
    }

    /**
     * @param scimClient the SCIM client to use
     * @param id the SCIM id of the resource
     * @param isGroup whether the resource is a group
     * @throws ResourceException if this SCIM client is not allowed to access this resource (because it is managed by
     * some other SCIM client, or because the resource is not managed by SCIM)
     */
    protected void checkScimClientAccess(String scimClient, String id, boolean isGroup) throws ResourceException
    {
        getRecord(scimClient, id, isGroup, false);
    }

    /**
     * @param referenceRef the reference to the SCIM resource under SCIM.Resources.{clientId}.{type}.{id}
     * @param resourceRef the reference to the resource itself (under XWiki.{name})
     * @param externalId the SCIM external ID of the resource
     * @param scimRepresentation the serialized SCIM representation of the resource
     */
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
                StringUtils.isEmpty(externalId) ? null : externalId,
                scimRepresentation
            );
        }

        /**
         * @return the special SCIM record of self (of the SCIM client itself)
         * @param context the XWiki context
         */
        public static ScimRecord getSelf(XWikiContext context)
        {
            return new ScimRecord(null, context.getUserReference(), "", "");
        }

        /**
         * @return the SCIM id of the resource
         */
        public String getId()
        {
            if (referenceRef() == null) {
                return SELF_ID;
            }
            return referenceRef().getName();
        }

        /**
         * @return whether the resource is a group
         */
        public boolean isGroup()
        {
            if (referenceRef() == null) {
                return false;
            }
            return ScimGroup.RESOURCE_NAME.equals(referenceRef().getParent().getName());
        }
    }

    /**
     * @return the records of the given ids using the given SCIM client
     * @param ids the SCIM ids of the resources for which to get the records
     * @param scimClient the SCIM client to use
     * @throws QueryException if something wrong happens
     */
    protected List<String> getRecordsByIds(List<String> ids, String scimClient) throws QueryException
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

    /**
     * @return the records for the SCIM resources managed by the given SCIM client matching the given references
     * @param refs the document references of the resources to find
     * @param scimClient the SCIM client to use
     * @throws QueryException if something wrong happen
     */
    protected Map<String, ScimRecord> getRecordsByRefs(Collection<DocumentReference> refs, String scimClient)
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

    /**
     * @return the records for the SCIM resources managed by the given SCIM client matching the given full names
     * @param fullNames the XWiki full references of the resources to find
     * @param scimClient the SCIM client to use
     * @throws QueryException if something wrong happen
     */
    protected Map<String, ScimRecord> getRecordsByFullNames(Collection<String> fullNames, String scimClient)
        throws QueryException
    {
        // note: we resolve and then serialize the given full names to ensure we have local full names
        return getRecordsByRefs(fullNames.stream().map(resolver::resolve).toList(), scimClient);
    }
}
