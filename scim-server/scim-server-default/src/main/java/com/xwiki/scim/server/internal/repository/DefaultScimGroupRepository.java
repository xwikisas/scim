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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.resources.GroupMembership;
import org.apache.directory.scim.spec.resources.ScimGroup;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of ScimGroupRepository.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
public class DefaultScimGroupRepository extends AbstractScimResourceRepository<ScimGroup> implements ScimGroupRepository
{
    private static final String MEMBER = "member";

    @Inject
    private GroupManager groupManager;

    @Override
    public Class<ScimGroup> getResourceClass()
    {
        return ScimGroup.class;
    }

    @Override
    protected ScimGroup getScimResourceWithoutMeta(String scimClient, ScimRecord r)
        throws GroupException, QueryException
    {
        ScimGroup group = new ScimGroup();
        if (StringUtils.isEmpty(group.getDisplayName())) {
            // If present, we don't set the display name of the resource, we keep the one originally sent by the client
            group.setDisplayName(getGroupDisplayName(r.resourceRef()));
        }
        setMembers(scimClient, r.resourceRef(), group);
        return group;
    }

    private void setMembers(String scimClient, DocumentReference groupRef, ScimGroup group)
        throws GroupException, QueryException
    {
        Collection<DocumentReference> groupMembers = groupManager.getMembers(groupRef, false);
        Map<String, ScimRecord> groupMemberScimResources =
            getRecordsByRefs(groupMembers, scimClient);
        ArrayList<GroupMembership> members = new ArrayList<>(groupMembers.size());
        for (ScimRecord r : groupMemberScimResources.values()) {
            DocumentReference memberRef = r.resourceRef();
            GroupMembership groupMembership = new GroupMembership();
            boolean memberIsGroup = groupManager.isGroup(memberRef);
            groupMembership.setType(memberIsGroup ? ScimGroup.RESOURCE_NAME : ScimUser.RESOURCE_NAME);
            String id = r.getId();
            groupMembership.setRef(getRef(memberIsGroup, id));
            groupMembership.setValue(id);
            groupMembership.setDisplay(
                memberIsGroup
                    ? getGroupDisplayName(memberRef)
                    : memberRef.getName()
            );
            members.add(groupMembership);
        }
        group.setMembers(members);
    }

    @Override
    protected XWikiDocument createOrUpdate(ScimGroup scimResource, ScimRecord r) throws ResourceException
    {
        try {
            XWikiContext context = xcontextProvider.get();
            XWikiDocument resourceDoc = context.getWiki().getDocument(r.resourceRef(), context).clone();
            updateGroupMembers(scimResource, resourceDoc);
            return resourceDoc;
        } catch (XWikiException | QueryException e) {
            throw new ResourceException(500, "Could not save the group", e);
        }
    }

    @Override
    protected String getDefaultReferenceName(ScimGroup scimResource)
    {
        return scimResource.getDisplayName();
    }

    private void updateGroupMembers(ScimGroup scimResource, XWikiDocument resourceDoc)
        throws ResourceException, XWikiException, QueryException
    {
        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();
        DocumentReference groupClass = wiki.getGroupClass(context).getDocumentReference();
        List<BaseObject> existingMembers = resourceDoc.getXObjects(groupClass);
        String scimClient = getScimClientId();
        List<GroupMembership> scimMembers = scimResource.getMembers();
        if (scimMembers == null) {
            scimMembers = List.of();
        }

        List<String> members = existingMembers.stream()
            .filter(Objects::nonNull)
            .map(m -> m.getStringValue(MEMBER))
            .toList();
        Map<String, ScimRecord> managedMembers = getRecordsByFullNames(members, scimClient);
        LinkedMap<String, BaseObject> existingMembersByFullName = new LinkedMap<>(existingMembers.size());

        for (BaseObject existingMember : existingMembers) {
            if (existingMember != null) {
                String memberFullName = existingMember.getStringValue(MEMBER);
                if (managedMembers.containsKey(memberFullName)) {
                    // we don't mess with members that are not managed by this SCIM client
                    existingMembersByFullName.put(memberFullName, existingMember);
                }
            }
        }

        List<String> membersToAdd = new ArrayList<>(scimMembers.size());

        List<String> scimMemberIds = new ArrayList<>(scimMembers.size());
        for (GroupMembership membership : scimMembers) {
            scimMemberIds.add(getIdFromValueAndRef(membership.getValue(), membership.getRef()));
        }

        List<String> fullNames = getRecordsByIds(scimMemberIds, scimClient);
        for (String fullName : fullNames) {
            BaseObject memberObj = existingMembersByFullName.remove(fullName);
            if (memberObj == null) {
                membersToAdd.add(fullName);
            }
        }

        // The members remaining in existingMembersByFullName are those that were removed by the group update

        for (String member : membersToAdd) {
            // we add members to add, reusing objects of members to remove if available
            BaseObject memberObj = existingMembersByFullName.isEmpty()
                ? resourceDoc.newXObject(groupClass, context)
                : existingMembersByFullName.remove(0);

            memberObj.set(MEMBER, member, context);
        }

        // we remove objects of old members if we didn't reuse them for new members
        existingMembersByFullName.values().forEach(resourceDoc::removeXObject);

        // This member management is a bit contrived, this is because the API to manipulate a document's objects is
        // very finicky, especially when both adding and removing objects that have the same number
    }

    @Override
    protected Map<String, String> getFormatVariables(ScimGroup scimResource)
    {
        return Map.of(
            "externalId", StringUtils.defaultString(scimResource.getExternalId()),
            "displayName", StringUtils.defaultString(scimResource.getDisplayName())
        );
    }

    @Override
    protected String getReferenceNameFormat()
    {
        return conf.getGroupNameFormat();
    }
}
