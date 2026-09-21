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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.scim.spec.exception.ResourceException;
import org.apache.directory.scim.spec.exception.ScimResourceInvalidException;
import org.apache.directory.scim.spec.extension.EnterpriseExtension;
import org.apache.directory.scim.spec.phonenumber.PhoneNumberParseException;
import org.apache.directory.scim.spec.resources.Address;
import org.apache.directory.scim.spec.resources.Email;
import org.apache.directory.scim.spec.resources.Im;
import org.apache.directory.scim.spec.resources.Name;
import org.apache.directory.scim.spec.resources.PhoneNumber;
import org.apache.directory.scim.spec.resources.Photo;
import org.apache.directory.scim.spec.resources.ScimExtension;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.apache.directory.scim.spec.resources.UserGroup;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xwiki.scim.server.internal.SCIMRestletJacksonContextResolver;

/**
 * Default implementation of ScimUserRepository.
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
@SuppressWarnings({"ClassFanOutComplexity"})
public class DefaultScimUserRepository extends AbstractScimResourceRepository<ScimUser> implements ScimUserRepository
{
    private static final String TITLE = "title";
    private static final String LAST_NAME = "last_name";
    private static final String FIRST_NAME = "first_name";
    private static final String ACTIVE = "active";
    private static final String DEFAULT_LANGAGE = "default_language";
    private static final String EMAIL = "email";
    private static final String IMTYPE = "imtype";
    private static final String IMACCOUNT = "imaccount";
    private static final String PASSWORD = "password";
    private static final String ADDRESS = "address";
    private static final String FAILED_TO_ADD_USER_TO_GROUP = "Failed to add user [{}] to group [{}]";
    private static final String TIMEZONE = "timezone";
    private static final String AVATAR = "avatar";
    private static final String CITY = "city";
    private static final String COUNTRY = "country";
    private static final String PHONE = "phone";

    @Inject
    private Logger logger;

    @Inject
    private GroupManager groupManager;

    @Override
    public Class<ScimUser> getResourceClass()
    {
        return ScimUser.class;
    }

    @Override
    protected ScimUser getScimResourceWithoutMeta(String scimClient, ScimRecord r)
        throws GroupException, XWikiException, QueryException, ResourceException
    {
        XWikiContext context = xcontextProvider.get();
        XWikiDocument userDoc = context.getWiki().getDocument(r.resourceRef(), context);
        ScimUser user = getScimUserFromDocument(r);
        user.setActive(userDoc.getIntValue(ACTIVE) == 1);

        if (StringUtils.isEmpty(user.getUserName())) {
            // If present, we don't set the username of the resource, we keep the one originally sent by the client
            user.setUserName(r.resourceRef().getName());
        }

        fetchAddress(userDoc, user);
        fetchEmail(userDoc, user);
        fetchIM(userDoc, user);
        fetchPhone(userDoc, user);
        fetchName(userDoc, user);
        fetchAvatar(userDoc, user);
        fetchGroups(r, user);

        String timezone = userDoc.getStringValue(TIMEZONE);
        if (StringUtils.isNotEmpty(timezone)) {
            user.setTimezone(timezone);
        }

        String defaultLanguage = userDoc.getStringValue(DEFAULT_LANGAGE);
        if (StringUtils.isNotEmpty(defaultLanguage)) {
            String preferredLanguage = user.getPreferredLanguage();
            String normalizedPreferredLanguage = StringUtils.defaultString(defaultLanguage).toLowerCase(Locale.ROOT);
            if (!normalizedPreferredLanguage.equals(getSingleLanguageRange(preferredLanguage))) {
                // We don't update the preferred language stored in the SCIM object if the translation we do from SCIM
                // to XWiki gives what's stored from SCIM. This allows returning something less surprising to the SCIM
                // client (the created user that's returned has a preferredLanguage field that matches what was sent. It
                // should also make filtering more consistent should a SCIM client filter users according to this field)
                user.setPreferredLanguage(defaultLanguage);
            }
        }

        String title = StringUtils.defaultString(userDoc.getStringValue(TITLE));
        if (StringUtils.isNotEmpty(title)) {
            user.setTitle(title);
        }

        return user;
    }

    private ScimUser getScimUserFromDocument(ScimRecord r)
    {
        String scimRepresentation = r.scimRepresentation();
        if (StringUtils.isEmpty(scimRepresentation)) {
            return new ScimUser();
        }

        ObjectMapper objectMapper = SCIMRestletJacksonContextResolver.getContextStatic();
        try {
            return objectMapper.readValue(scimRepresentation, ScimUser.class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse the saved SCIM resource representation in [{}]", r.referenceRef());
        }

        return new ScimUser();
    }

    private void fetchAvatar(XWikiDocument userDoc, ScimUser user)
    {
        String avatar = userDoc.getStringValue(AVATAR);
        if (StringUtils.isNotEmpty(avatar)) {
            XWikiContext context = xcontextProvider.get();
            XWiki wiki = context.getWiki();
            Photo scimAvatar = new Photo();
            String avatarUrl = wiki.getURL(userDoc.getDocumentReference(), avatar, context);
            scimAvatar.setValue(avatarUrl);
            scimAvatar.setPrimary(true);
            user.setPhotos(List.of(scimAvatar));
        }
    }

    private void fetchGroups(ScimRecord r, ScimUser user)
        throws GroupException, ResourceException, QueryException
    {
        XWikiContext context = xcontextProvider.get();
        Collection<DocumentReference> groups = groupManager.getGroups(r.resourceRef(), context.getMainXWiki(), true);
        List<UserGroup> scimGroups = new ArrayList<>(groups.size());
        Map<String, ScimRecord> scimManagedGroups = getRecordsByRefs(groups, getScimClientId());
        for (ScimRecord group : scimManagedGroups.values()) {
            UserGroup userGroup = new UserGroup();
            String id = group.getId();
            userGroup.setValue(id);
            userGroup.setRef(getRef(true, id));
            userGroup.setDisplay(getGroupDisplayName(group.resourceRef()));
            scimGroups.add(userGroup);
        }
        user.setGroups(scimGroups);
    }

    private void fetchPhone(XWikiDocument userDoc, ScimUser user)
    {
        String phone = userDoc.getStringValue(PHONE);

        if (StringUtils.isEmpty(phone)) {
            user.setPhoneNumbers(Collections.emptyList());
        } else {
            PhoneNumber phoneNumber = getPrimaryOrFirstPhone(user);
            if (phoneNumber == null) {
                PhoneNumber scimPhone = new PhoneNumber();
                setPhoneValue(scimPhone, phone);
                user.setPhoneNumbers(List.of(scimPhone));
            } else {
                setPhoneValue(phoneNumber, phone);
            }
        }
    }

    private PhoneNumber getPrimaryOrFirstPhone(ScimUser user)
    {
        Optional<PhoneNumber> phoneNumber = user.getPrimaryPhoneNumber();
        if (phoneNumber.isPresent()) {
            return phoneNumber.get();
        }
        List<PhoneNumber> phoneNumbers = user.getPhoneNumbers();
        if (phoneNumbers != null && !phoneNumbers.isEmpty()) {
            return phoneNumbers.get(0);
        }
        return null;
    }

    private void setPhoneValue(PhoneNumber scimPhone, String phone)
    {
        try {
            scimPhone.setValue(phone);
        } catch (PhoneNumberParseException e) {
            throw new ScimResourceInvalidException("Failed to set the phone number of this resource", e);
        }
    }

    private static void fetchIM(XWikiDocument userDoc, ScimUser user)
    {
        String imType = userDoc.getStringValue(IMTYPE);
        String imAccount = userDoc.getStringValue(IMACCOUNT);

        if (StringUtils.isEmpty(imType) || StringUtils.isEmpty(imAccount)) {
            user.setIms(Collections.emptyList());
        } else {
            List<Im> ims = user.getIms();
            Im im = getFirstOrPrimaryIm(ims);
            if (im == null) {
                im = new Im();
                im.setType(imType);
                im.setValue(imAccount);
                user.setIms(List.of(im));
            } else {
                im.setType(imType);
                im.setValue(imAccount);
            }
        }
    }

    private static Im getFirstOrPrimaryIm(List<Im> ims)
    {
        Optional<Im> imOpt = ims == null
            ? Optional.empty()
            : ims.stream().filter(i -> Boolean.TRUE.equals(i.getPrimary())).findFirst();

        return imOpt.orElseGet(() -> ims != null && !ims.isEmpty()
            ? ims.get(0)
            : null);
    }

    private static void fetchAddress(XWikiDocument userDoc, ScimUser user)
    {
        String address = userDoc.getStringValue(ADDRESS);
        Optional<Address> addressOpt = user.getPrimaryAddress();
        if (StringUtils.isEmpty(address)) {
            addressOpt.ifPresent(
                v -> user.setAddresses(
                    user.getAddresses()
                        .stream()
                        .filter(a -> a.equals(v))
                        .toList()
                )
            );
        } else {
            Address scimAddress = addressOpt.orElseGet(Address::new);
            scimAddress.setFormatted(address);
            String country = userDoc.getStringValue(COUNTRY);
            scimAddress.setCountry(country);
            String city = userDoc.getStringValue(CITY);
            scimAddress.setLocality(city);
            scimAddress.setPrimary(true);
            if (addressOpt.isEmpty()) {
                List<Address> addresses = user.getAddresses();
                if (addresses == null) {
                    addresses = List.of(scimAddress);
                } else {
                    addresses = new ArrayList<>(addresses);
                    addresses.add(scimAddress);
                }
                user.setAddresses(addresses);
            }
        }
    }

    private static void fetchEmail(XWikiDocument userDoc, ScimUser user)
    {
        String emailString = userDoc.getStringValue(EMAIL);
        if (StringUtils.isEmpty(emailString)) {
            user.setEmails(Collections.emptyList());
        } else {
            Email email = getPrimaryOrFirstEmail(user);
            if (email == null) {
                email = new Email();
                email.setValue(emailString);
                user.setEmails(List.of(email));
            } else {
                email.setValue(emailString);
            }
        }
    }

    private static Email getPrimaryOrFirstEmail(ScimUser user)
    {
        Optional<Email> email = user.getPrimaryEmailAddress();
        if (email.isPresent()) {
            return email.get();
        }

        List<Email> emails = user.getEmails();
        if (emails != null && !emails.isEmpty()) {
            return emails.get(0);
        }

        return null;
    }

    private static void fetchName(XWikiDocument userDoc, ScimUser user)
    {
        String firstName = StringUtils.defaultString(userDoc.getStringValue(FIRST_NAME));
        String lastName = StringUtils.defaultString(userDoc.getStringValue(LAST_NAME));
        updateName(user, firstName, lastName);
        user.setDisplayName(getDisplayName(userDoc, firstName, lastName));
    }

    private static void updateName(ScimUser user, String firstName, String lastName)
    {
        Name name = user.getName();

        if (StringUtils.isNotEmpty(firstName)) {
            if (name == null) {
                name = new Name();
            }
            name.setGivenName(firstName);
        }

        if (StringUtils.isNotEmpty(lastName)) {
            if (name == null) {
                name = new Name();
            }
            name.setFamilyName(lastName);
        }

        user.setName(name);
    }

    static String getDisplayName(XWikiDocument userDoc, String firstName, String lastName)
    {
        String displayName = userDoc.getTitle();
        if (!displayName.isEmpty()) {
            return displayName;
        }

        String first = firstName == null
            ? StringUtils.defaultString(userDoc.getStringValue(FIRST_NAME))
            : firstName;
        String last = lastName == null
            ? StringUtils.defaultString(userDoc.getStringValue(LAST_NAME))
            : lastName;

        return String.join(" ", first, last).trim();
    }

    @Override
    protected XWikiDocument createOrUpdate(ScimUser scimResource, ScimRecord r) throws ResourceException
    {
        String scimClient = getScimClientId();
        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();

        DocumentReference userRef = r.resourceRef();
        XWikiDocument resourceDoc;
        DocumentReference userClass;

        try {
            resourceDoc = wiki.getDocument(userRef, context).clone();
            userClass = wiki.getUserClass(context).getDocumentReference();
        } catch (XWikiException e) {
            throw new ResourceException(500, "Failed to get the user's document or class", e);
        }

        BaseObject userObj = resourceDoc.getXObject(userClass, true, context);

        updateName(scimResource, userObj, resourceDoc);
        updateActive(scimResource, userObj);
        updatePassword(scimResource, userObj);
        updatePhone(scimResource, userObj);
        updateLanguage(scimResource, userObj);
        updateEmail(scimResource, userObj);
        updateIM(scimResource, userObj);
        updateAddress(scimResource, userObj);
        userObj.setStringValue(TITLE, StringUtils.defaultString(scimResource.getTitle()));
        userObj.setStringValue(TIMEZONE, StringUtils.defaultString(scimResource.getTimezone()));

        Set<String> groups = getGroupFullNames(scimClient, scimResource);
        addUserToGroups(scimClient, localSerializer.serialize(userRef), groups);
        maybeAddOIDCObject(resourceDoc, scimResource);
        return resourceDoc;
    }

    private void maybeAddOIDCObject(XWikiDocument resourceDoc, ScimUser scimUser)
    {
        if (!conf.isOIDCEnabled()) {
            return;
        }

        Map<String, String> variables = getFormatVariables(scimUser);

        BaseObject oidcObj = resourceDoc.getXObject(OIDC_USER_CLASS_REF, true, xcontextProvider.get());

        String issuer = conf.getOIDCIssuer();
        String subject = userFormatterFactory.create(variables).format(conf.getOIDCSubject());
        if (subject.isEmpty()) {
            subject = usernameCleaner.clean(scimUser.getUserName());
        }
        oidcObj.setStringValue("issuer", issuer);
        oidcObj.setStringValue("subject", subject);
    }

    @Override
    protected Map<String, String> getFormatVariables(ScimUser scimResource)
    {
        return Map.of(
            "externalId", StringUtils.defaultString(scimResource.getExternalId()),
            "userName", StringUtils.defaultString(scimResource.getUserName()),
            "userType", StringUtils.defaultString(scimResource.getUserType()),
            "displayName", StringUtils.defaultString(scimResource.getDisplayName()),
            "nickName", StringUtils.defaultString(scimResource.getNickName()),
            TITLE, StringUtils.defaultString(scimResource.getTitle()),
            EMAIL, StringUtils.defaultString(scimResource.getPrimaryEmailAddress().orElse(new Email()).getValue()),
            PHONE, StringUtils.defaultString(scimResource.getPrimaryPhoneNumber().orElse(new PhoneNumber()).getValue())
        );
    }

    @Override
    protected String getReferenceNameFormat()
    {
        return conf.getUserNameFormat();
    }

    private static void updatePassword(ScimUser scimResource, BaseObject userObj)
    {
        String password = scimResource.getPassword();
        if (StringUtils.isNotEmpty(password)) {
            userObj.setStringValue(PASSWORD, password);
        }
    }

    private static void updateActive(ScimUser scimResource, BaseObject userObj)
    {
        Boolean active = scimResource.getActive();
        userObj.setIntValue(ACTIVE, (active == null || active) ? 1 : 0);
    }

    private void updateAddress(ScimUser scimResource, BaseObject userObj)
    {
        boolean updated = false;
        Optional<Address> primaryAddress = scimResource.getPrimaryAddress();
        if (primaryAddress.isPresent()) {
            setAddress(userObj, primaryAddress.get());
            updated = true;
        } else {
            List<Address> addresses = scimResource.getAddresses();
            if (CollectionUtils.isNotEmpty(addresses)) {
                setAddress(userObj, addresses.get(0));
                updated = true;
            }
        }

        if (!updated) {
            userObj.setStringValue(CITY, "");
            userObj.setStringValue(COUNTRY, "");
            userObj.setStringValue(ADDRESS, "");
        }
    }

    private static void updateIM(ScimUser scimResource, BaseObject userObj)
    {
        List<Im> ims = scimResource.getIms();
        if (ims == null || ims.isEmpty()) {
            userObj.setStringValue(IMTYPE, "");
            userObj.setStringValue(IMACCOUNT, "");
        } else {
            Im im = getPrimaryOrFirstIm(ims);
            userObj.setStringValue(IMTYPE, im.getType());
            userObj.setStringValue(IMACCOUNT, im.getValue());
        }
    }

    private static void updateEmail(ScimUser scimResource, BaseObject userObj)
    {
        boolean updated = false;
        Optional<Email> primaryEmailAddress = scimResource.getPrimaryEmailAddress();
        if (primaryEmailAddress.isPresent()) {
            Email email = primaryEmailAddress.get();
            userObj.setStringValue(EMAIL, email.getValue());
            updated = true;
        } else {
            List<Email> emails = scimResource.getEmails();
            if (CollectionUtils.isNotEmpty(emails)) {
                userObj.setStringValue(EMAIL, emails.get(0).getValue());
                updated = true;
            }
        }

        if (!updated) {
            userObj.setStringValue(EMAIL, "");
        }
    }

    private void updateLanguage(ScimUser scimResource, BaseObject userObj)
    {
        boolean updated = false;
        String preferredLanguage = scimResource.getPreferredLanguage();
        if (StringUtils.isEmpty(preferredLanguage)) {
            String locale = scimResource.getLocale();
            if (StringUtils.isNotEmpty(locale)) {
                userObj.setStringValue(DEFAULT_LANGAGE, getSingleLanguageRange(locale));
                updated = true;
            }
        } else {
            userObj.setStringValue(DEFAULT_LANGAGE, getSingleLanguageRange(preferredLanguage));
            updated = true;
        }

        if (!updated) {
            userObj.setStringValue(DEFAULT_LANGAGE, "");
        }
    }

    private static void updatePhone(ScimUser scimResource, BaseObject userObj)
    {
        boolean updated = false;
        Optional<PhoneNumber> phoneNumber = scimResource.getPrimaryPhoneNumber();
        if (phoneNumber.isPresent()) {
            PhoneNumber p = phoneNumber.get();
            userObj.setStringValue(PHONE, Objects.toString(p.getValue(), p.getNumber()));
            updated = true;
        } else {
            List<PhoneNumber> phoneNumbers = scimResource.getPhoneNumbers();
            if (CollectionUtils.isNotEmpty(phoneNumbers)) {
                PhoneNumber p = phoneNumbers.get(0);
                userObj.setStringValue(PHONE, Objects.toString(p.getValue(), p.getNumber()));
                updated = true;
            }
        }
        if (!updated) {
            userObj.setStringValue(PHONE, "");
        }
    }

    @SuppressWarnings("NPathComplexity")
    private void setAddress(BaseObject userObj, Address address)
    {
        String locality = address.getLocality();
        String country = address.getCountry();

        if (StringUtils.isNotEmpty(locality)) {
            userObj.setStringValue(CITY, locality);
        }

        if (StringUtils.isNotEmpty(country)) {
            userObj.setStringValue(COUNTRY, country);
        }

        String formatted = address.getFormatted();
        if (StringUtils.isNotEmpty(formatted)) {
            userObj.setLargeStringValue(ADDRESS, formatted);
            return;
        }

        formatted = address.getStreetAddress();
        if (StringUtils.isEmpty(formatted)) {
            return;
        }

        formatted += "\n";

        if (StringUtils.isNotEmpty(locality)) {
            formatted += locality + ", ";
        }

        String region = address.getRegion();
        if (StringUtils.isNotEmpty(region)) {
            formatted += region + ' ';
        }

        String postalCode = address.getPostalCode();
        if (StringUtils.isNotEmpty(postalCode)) {
            formatted += postalCode + ' ';
        }

        if (StringUtils.isNotEmpty(country)) {
            formatted += country;
        }

        userObj.setLargeStringValue(ADDRESS, formatted.trim());
    }

    private String getSingleLanguageRange(String locale)
    {
        List<Locale.LanguageRange> ranges;

        try {
            ranges = Locale.LanguageRange.parse(locale);
        } catch (IllegalArgumentException e) {
            logger.warn("Could not parse language range from SCIM client: [{}]", locale);
            return "";
        }

        if (ranges.isEmpty()) {
            return "";
        }

        // Note that getRange returns lowercased strings
        return ranges.get(0).getRange();
    }

    private static Im getPrimaryOrFirstIm(List<Im> ims)
    {
        for (Im im : ims) {
            if (Boolean.TRUE.equals(im.getPrimary())) {
                return im;
            }
        }
        return ims.get(0);
    }

    @Override
    protected String getDefaultReferenceName(ScimUser scimResource)
    {
        return scimResource.getUserName();
    }

    private static void updateName(ScimUser scimResource, BaseObject userObj, XWikiDocument userDoc)
    {
        String displayName = "";

        Name name = scimResource.getName();
        if (name == null) {
            userObj.setStringValue(FIRST_NAME, "");
            userObj.setStringValue(LAST_NAME, "");
        } else {
            displayName = StringUtils.defaultString(name.getHonorificPrefix());
            String firstName = name.getGivenName();
            if (StringUtils.isEmpty(firstName)) {
                userObj.setStringValue(FIRST_NAME, "");
            } else {
                userObj.setStringValue(FIRST_NAME, firstName);
                displayName += ' ' + firstName;
            }

            String middleName = name.getMiddleName();
            if (StringUtils.isNotEmpty(middleName)) {
                displayName += ' ' + middleName;
            }

            String familyName = name.getFamilyName();
            if (StringUtils.isEmpty(familyName)) {
                userObj.setStringValue(LAST_NAME, "");
            } else {
                userObj.setStringValue(LAST_NAME, familyName);
                displayName += ' ' + familyName;
            }

            displayName = (displayName + ' ' + StringUtils.defaultString(name.getHonorificSuffix())).trim();
        }

        userDoc.setTitle(StringUtils.defaultIfBlank(scimResource.getDisplayName(), displayName));
    }

    private void addUserToGroups(String scimClient, String userFullName, Set<String> groups) throws ResourceException
    {
        if (groups == null) {
            return;
        }

        XWikiContext context = xcontextProvider.get();
        XWiki wiki = context.getWiki();
        DocumentReference groupClass;
        try {
            groupClass = wiki.getGroupClass(context).getDocumentReference();
        } catch (XWikiException e) {
            throw new ResourceException(500, "Failed to get the group class", e);
        }
        Collection<XWikiDocument> groupDocsToSave = new ArrayList<>(groups.size());
        for (String groupId : groups) {
            try {
                ScimRecord r = getRecord(scimClient, groupId, true, false);
                if (r != null) {
                    XWikiDocument groupDoc = wiki.getDocument(r.resourceRef(), context).clone();
                    int n = groupDoc.createXObject(groupClass, context);
                    BaseObject groupObj = groupDoc.getXObject(groupClass, n);
                    groupObj.set("member", userFullName, context);
                    groupDocsToSave.add(groupDoc);
                }
            } catch (XWikiException e) {
                logger.error(FAILED_TO_ADD_USER_TO_GROUP, userFullName, groupId, e);
            }
        }

        for (XWikiDocument groupDoc : groupDocsToSave) {
            try {
                wiki.saveDocument(groupDoc, "Added member from SCIM", context);
            } catch (XWikiException e) {
                logger.error(FAILED_TO_ADD_USER_TO_GROUP, userFullName, groupDoc.getDocumentReference(), e);
            }
        }
    }

    private Set<String> getGroupFullNames(String scimClient, ScimUser scimResource)
        throws ResourceException
    {
        Set<String> groups = null;
        List<UserGroup> scimGroups = scimResource.getGroups();
        if (scimGroups != null) {
            groups = new HashSet<>(scimGroups.size());
            for (UserGroup group : scimGroups) {
                String id = getIdFromValueAndRef(group.getValue(), group.getRef());
                checkScimClientAccess(scimClient, id, true);
                groups.add(id);
            }
        }
        return groups;
    }

    @Override
    public List<Class<? extends ScimExtension>> getExtensionList()
    {
        return List.of(EnterpriseExtension.class);
    }
}
